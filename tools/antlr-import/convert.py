#!/usr/bin/env python3
# Copyright 2021 the original author or authors
#
# SPDX-License-Identifier: MIT
"""
Convert Apache Cassandra's ANTLR3 grammars (Lexer.g / Parser.g, which build
Cassandra's Java AST) into recognizer-only ANTLR4 grammars usable by helenus'
CqlValidator.

helenus only needs a *recognizer* (accept/reject + token types), never an AST,
so every embedded Java action, `returns [...]` clause, rule argument, and
syntactic predicate is stripped. The result plugs into the existing sbt-antlr4
build and keeps the split CqlLexer / CqlParser class layout.

    Upstream:  tools/antlr-import/upstream/{Lexer,Parser}.g   (pinned; see VERSION)
    Output:    core/src/main/antlr4/{CqlLexer,CqlParser}.g4

Design (see tools/antlr-import/README.md):
  * ~80% of the port is mechanical and done here deterministically.
  * ~20% needs judgment and lives in the clearly-marked HAND-WRITTEN section
    below (the 6 lexer rules that carried actions/predicates, plus the operator
    token block a split grammar requires). On a new Cassandra release, re-run
    this script: keyword/fragment additions flow through automatically; if an
    anchor is missing the script raises, telling you exactly what to revisit.

Run:  python3 tools/antlr-import/convert.py
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
UPSTREAM = os.path.join(HERE, "upstream")
OUT_DIR = os.path.join(REPO, "core", "src", "main", "antlr4")


class DriftError(RuntimeError):
    """Raised when an expected upstream anchor is missing (grammar changed shape)."""


# --------------------------------------------------------------------------- #
# Literal-aware scanning primitives
#
# At grammar top level, quoting is ANTLR single-quoted literals ('...') plus
# // and /* */ comments. Inside a Java action ({...}) quoting is Java ("..."
# and '...'). The matchers below respect the right rules for each context so
# braces/brackets inside literals never desync the span we remove.
# --------------------------------------------------------------------------- #

def _match_brace_java(s, i):
    """s[i] == '{'. Return index just past the matching '}', Java-aware."""
    assert s[i] == "{"
    depth = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == '"' or c == "'":
            q = c
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == q:
                    i += 1
                    break
                i += 1
            continue
        if c == "/" and i + 1 < n and s[i + 1] == "/":
            while i < n and s[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and s[i + 1] == "*":
            i += 2
            while i < n and not (s[i] == "*" and i + 1 < n and s[i + 1] == "/"):
                i += 1
            i += 2
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise DriftError("unbalanced '{' in embedded action")


def _match_bracket_antlr(s, i):
    """s[i] == '['. Return index just past the matching ']', ANTLR-literal-aware."""
    assert s[i] == "["
    depth = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == "'":
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c == "[":
            depth += 1
        elif c == "]":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise DriftError("unbalanced '[' in rule argument / returns clause")


def _match_paren_antlr(s, i):
    """s[i] == '('. Return index just past the matching ')', ANTLR-literal-aware."""
    assert s[i] == "("
    depth = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c == "'":
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise DriftError("unbalanced '(' while scanning for a syntactic predicate")


_WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def strip(body):
    """Single literal-aware pass over a rules body removing all embedded Java.

    Drops: @-action blocks (@init/@after/@members/...), `returns [...]`,
    rule arguments `name[...]`, inline actions `{...}`, and ANTLR3 syntactic
    predicates `(...)=>`. Copies ANTLR literals and comments verbatim.
    """
    out = []
    i = 0
    n = len(body)
    while i < n:
        c = body[i]

        # ANTLR single-quoted literal -> copy verbatim
        if c == "'":
            j = i + 1
            while j < n:
                if body[j] == "\\":
                    j += 2
                    continue
                if body[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(body[i:j])
            i = j
            continue

        # line comment -> copy verbatim
        if c == "/" and i + 1 < n and body[i + 1] == "/":
            j = body.find("\n", i)
            j = n if j == -1 else j
            out.append(body[i:j])
            i = j
            continue

        # block comment -> copy verbatim
        if c == "/" and i + 1 < n and body[i + 1] == "*":
            j = body.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(body[i:j])
            i = j
            continue

        # @-action block (@init { ... }, @after { ... }, @members { ... }, ...)
        if c == "@":
            m = re.match(r"@[A-Za-z_]\w*(::[A-Za-z_]\w*)?\s*\{", body[i:])
            if m:
                brace = i + m.end() - 1
                i = _match_brace_java(body, brace)
                continue

        # inline action { ... }
        if c == "{":
            i = _match_brace_java(body, i)
            continue

        # rule argument or returns bracket [ ... ]
        if c == "[":
            i = _match_bracket_antlr(body, i)
            continue

        # syntactic predicate ( ... )=>  -> drop the whole thing
        if c == "(":
            end = _match_paren_antlr(body, i)
            k = end
            while k < n and body[k] in " \t\r\n":
                k += 1
            if body[k:k + 2] == "=>":
                i = k + 2
                continue
            out.append("(")
            i += 1
            continue

        # identifier: handle `returns` keyword and element labels (name= / name+=)
        m = _WORD.match(body, i)
        if m:
            word = m.group(0)
            if word == "returns":
                i = m.end()
                continue
            # element label `x=rule` / `x+=rule`: useless once actions are gone,
            # and ANTLR4 rejects a label reused with a different rule type (which
            # Cassandra does freely), so drop the `name=` / `name+=` prefix.
            k = m.end()
            while k < len(body) and body[k] in " \t":
                k += 1
            if k < len(body) and body[k] == "+" and body[k + 1:k + 2] == "=":
                i = k + 2
                continue
            if k < len(body) and body[k] == "=" and body[k + 1:k + 2] not in ("=", ">"):
                i = k + 1
                continue
            out.append(word)
            i = m.end()
            continue

        out.append(c)
        i += 1

    return "".join(out)


def tidy(text):
    """Trailing whitespace + runs of blank lines cleanup."""
    text = "\n".join(line.rstrip() for line in text.split("\n"))
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


# --------------------------------------------------------------------------- #
# Parser conversion
# --------------------------------------------------------------------------- #

PARSER_HEADER = """\
// Generated by tools/antlr-import/convert.py from Apache Cassandra's Parser.g
// (see tools/antlr-import/upstream/VERSION). DO NOT EDIT BY HAND.
//
// Recognizer-only: every embedded Java action, `returns [...]`, rule argument
// and syntactic predicate from the upstream ANTLR3 grammar has been stripped.

parser grammar CqlParser;

options {
    tokenVocab = CqlLexer;
}

// Entry rule (mirrors Cassandra's `query` in Cql.g). CqlValidator calls root().
root
    : cqlStatement (';')* EOF
    ;
"""


# Intentional local deviations from the upstream grammar, applied to the raw
# ANTLR3 text before stripping. Each is an exact (old -> new) replacement and
# MUST match, or the run fails (so upstream drift is surfaced, not silently
# dropped). Keep this list short and documented; every entry is a place we
# knowingly diverge from Cassandra.
PARSER_RAW_PATCHES = [
    # Drop bind markers as bare SELECT selectors. Cassandra's grammar accepts
    # `SELECT ?` / `SELECT :name` syntactically, but Cassandra rejects them at
    # prepare time, and helenus' `cql"..."` interpolator relies on a marker being
    # invalid in a selector position to decide it must inject an identifier there
    # (a column name) rather than bind a value. Without this, `cql"SELECT $col"`
    # would bind `$col` as a value and silently produce a broken query.
    (
        "selectionLiteral returns [Term.Raw value]\n"
        "    : c=constant                     { $value = c; }\n"
        "    | K_NULL                         { $value = Constants.NULL_LITERAL; }\n"
        "    | ':' id=noncol_ident            { $value = newBindVariables(id); }\n"
        "    | QMARK                          { $value = newBindVariables(null); }\n"
        "    ;",
        "selectionLiteral returns [Term.Raw value]\n"
        "    : c=constant                     { $value = c; }\n"
        "    | K_NULL                         { $value = Constants.NULL_LITERAL; }\n"
        "    ;",
    ),
]


def apply_raw_patches(text, patches):
    for old, new in patches:
        if old not in text:
            raise DriftError(
                "raw patch anchor not found (upstream grammar changed shape):\n"
                + old[:120] + " ..."
            )
        text = text.replace(old, new, 1)
    return text


def convert_parser():
    src = read(os.path.join(UPSTREAM, "Parser.g"))

    # Drop the ANTLR3 header + @members (everything before the first real rule).
    marker = re.search(r"^cqlStatement\b", src, re.MULTILINE)
    if not marker:
        raise DriftError("Parser.g: could not find the `cqlStatement` entry rule")
    body = src[marker.start():]

    body = apply_raw_patches(body, PARSER_RAW_PATCHES)
    body = strip(body)

    # ANTLR4 rejects the redundant escapes Cassandra uses for '*' and '%'.
    body = body.replace(r"'\*'", "'*'").replace(r"'\%'", "'%'")

    return tidy(PARSER_HEADER + "\n" + body)


# --------------------------------------------------------------------------- #
# Lexer conversion
# --------------------------------------------------------------------------- #

LEXER_HEADER = """\
// Generated by tools/antlr-import/convert.py from Apache Cassandra's Lexer.g
// (see tools/antlr-import/upstream/VERSION). DO NOT EDIT BY HAND.
//
// Recognizer-only. Keyword and fragment rules are copied verbatim from upstream
// (so new CQL keywords flow through automatically). The operator/punctuation
// tokens below are ADDED: the upstream lexer omits them because Cassandra builds
// a *combined* grammar (Cql.g) where the parser's inline '(' , '=' , ':' ...
// literals become implicit tokens. A split lexer/parser needs them named.

lexer grammar CqlLexer;
"""

# Operators & punctuation the parser references as inline literals. Names are
# referenced by CqlValidator, so keep them stable.
LEXER_OPERATORS = """\
// ===== Operators & punctuation (added; see header) =====
LPAREN      : '(';
RPAREN      : ')';
LBRACE      : '{';
RBRACE      : '}';
LBRACKET    : '[';
RBRACKET    : ']';
COMMA       : ',';
SEMICOLON   : ';';
COLON       : ':';
DOT         : '.';
EQ          : '=';
LT          : '<';
GT          : '>';
LTE         : '<=';
GTE         : '>=';
NEQ         : '!=';
PLUS        : '+';
MINUS       : '-';
STAR        : '*';
SLASH       : '/';
PERCENT     : '%';
PLUS_ASSIGN : '+=';
MINUS_ASSIGN: '-=';
"""

# The 6 rules that carried @init/@after actions or ANTLR3 predicates, rewritten
# for ANTLR4. Behaviour is preserved; the tricky one is FLOAT: upstream used a
# lexer that could emit multiple tokens plus a syntactic predicate to keep
# `1..3` lexing as INTEGER RANGE INTEGER rather than FLOAT('1.') DOT ... . The
# predicate `{_input.LA(2) != '.'}?` reproduces that without the multi-emit hack.
LEXER_REWRITES = """\
// ===== Rewritten for ANTLR4 (upstream used embedded actions / predicates) =====

STRING_LITERAL
    // pg-style dollar-quoted string, or a conventional single-quoted string
    : '$$' .*? '$$'
    | '\\'' ( ~'\\'' | '\\'' '\\'' )* '\\''
    ;

QUOTED_NAME
    : '"' ( ~'"' | '"' '"' )+ '"'
    ;

FLOAT
    // The LA(2) guard keeps `1..3` as INTEGER RANGE INTEGER (upstream did this
    // with a multi-token-emitting lexer + syntactic predicate).
    : INTEGER ( {_input.LA(2) != '.'}? '.' DIGIT* EXPONENT? | EXPONENT )
    ;

WS
    : [ \\t\\r\\n]+ -> channel(HIDDEN)
    ;

COMMENT
    : ('--' | '//') ~[\\n\\r]* -> channel(HIDDEN)
    ;

MULTILINE_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;
"""

# Rules replaced by LEXER_REWRITES; deleted from the copied upstream body.
REPLACED_LEXER_RULES = [
    "STRING_LITERAL",
    "QUOTED_NAME",
    "FLOAT",
    "WS",
    "COMMENT",
    "MULTILINE_COMMENT",
]


def _delete_rule(text, name):
    # Rules deleted here contain no ';' literal in their body, so "up to the
    # first ';'" cleanly captures the whole (already action-stripped) rule.
    pattern = re.compile(r"(?ms)^" + re.escape(name) + r"\b.*?;[ \t]*\n?")
    new, count = pattern.subn("", text, count=1)
    if count == 0:
        raise DriftError("Lexer.g: expected rule `%s` not found" % name)
    return new


def alpha_literals(parser_text):
    """String literals the parser uses that contain a letter (e.g. 'expr(').

    In Cassandra's combined grammar these become implicit tokens; a split
    lexer/parser must define them explicitly. Punctuation/operator literals are
    excluded (they are covered by LEXER_OPERATORS). Comments are skipped.
    """
    found = []
    seen = set()
    i, n = 0, len(parser_text)
    while i < n:
        c = parser_text[i]
        if c == "/" and parser_text[i + 1:i + 2] == "/":
            j = parser_text.find("\n", i)
            i = n if j == -1 else j
            continue
        if c == "/" and parser_text[i + 1:i + 2] == "*":
            j = parser_text.find("*/", i + 2)
            i = n if j == -1 else j + 2
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if parser_text[j] == "\\":
                    j += 2
                    continue
                if parser_text[j] == "'":
                    break
                j += 1
            lit = parser_text[i + 1:j]
            if re.search(r"[A-Za-z]", lit) and lit not in seen:
                seen.add(lit)
                found.append(lit)
            i = j + 1
            continue
        i += 1
    return found


def literal_token_defs(literals):
    """Lexer rules for word-like parser literals; ANTLR binds them by content."""
    if not literals:
        return ""
    lines = ["// ===== Implicit tokens for word-like parser literals (added; see header) ====="]
    used = set()
    for lit in literals:
        base = "L_" + re.sub(r"[^A-Za-z0-9]", "", lit).upper()
        name = base
        k = 2
        while name in used:
            name = "%s_%d" % (base, k)
            k += 1
        used.add(name)
        lines.append("%-12s: '%s';" % (name, lit))
    return "\n".join(lines)


def convert_lexer(extra_literals=()):
    src = read(os.path.join(UPSTREAM, "Lexer.g"))

    # Drop the ANTLR3 header + @lexer::members (everything before the first
    # keyword rule).
    marker = re.search(r"^K_SELECT\b", src, re.MULTILINE)
    if not marker:
        raise DriftError("Lexer.g: could not find the `K_SELECT` keyword rule")
    body = src[marker.start():]

    body = strip(body)

    # ANTLR4 rejects the redundant backslash escapes Cassandra uses in literals
    # (e.g. EMPTY_QUOTED_NAME's '\"'); none of these characters need escaping
    # inside an ANTLR literal.
    for esc in ('\\"', "\\$", "\\%", "\\*"):
        body = body.replace(esc, esc[1])

    for name in REPLACED_LEXER_RULES:
        body = _delete_rule(body, name)

    parts = [
        LEXER_HEADER,
        "",
        LEXER_OPERATORS,
        "",
        literal_token_defs(list(extra_literals)),
        "",
        "// ===== Keywords & fragments (copied verbatim from upstream Lexer.g) =====",
        body.strip(),
        "",
        LEXER_REWRITES,
    ]
    return tidy("\n".join(p for p in parts if p != ""))


# --------------------------------------------------------------------------- #

def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    print("wrote %s (%d lines)" % (os.path.relpath(path, REPO), text.count("\n") + 1))


def main():
    for name in ("Lexer.g", "Parser.g"):
        if not os.path.exists(os.path.join(UPSTREAM, name)):
            sys.exit("missing upstream/%s -- download it from the pinned "
                     "Cassandra tag (see upstream/VERSION)" % name)

    parser = convert_parser()
    lexer = convert_lexer(extra_literals=alpha_literals(parser))

    write(os.path.join(OUT_DIR, "CqlLexer.g4"), lexer)
    write(os.path.join(OUT_DIR, "CqlParser.g4"), parser)
    print("done. Review the diff, then run: sbt core/Antlr4/antlr4Generate")


if __name__ == "__main__":
    main()
