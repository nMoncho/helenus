# Importing Cassandra's CQL grammar

`CqlValidator` (in `core`) checks CQL syntax at compile time for the `cql"..."`
interpolator and `"...".toCQL`. Its lexer and parser are **derived from Apache
Cassandra's own ANTLR grammars**, rather than hand-maintained. This directory
holds the tooling that does the conversion so the import is repeatable when
Cassandra ships a new grammar.

## Why a conversion is needed

Cassandra's grammars are **ANTLR3** and are welded to Cassandra's Java AST: every
rule has a `returns [...]` type and embedded Java actions that build objects like
`SelectStatement.RawStatement`. They only compile with the whole `cassandra-all`
jar on the classpath.

Helenus needs none of that, only a **recognizer** (accept/reject a string, plus
token types). So `convert.py` mechanically strips all the Java and emits
recognizer-only **ANTLR4** grammars that plug into the existing `sbt-antlr4`
build with no runtime dependency on Cassandra.

```
upstream/Lexer.g   ─┐                  ┌─ core/src/main/antlr4/CqlLexer.g4
upstream/Parser.g  ─┼─ convert.py ────►┤
(pinned, see VERSION)                  └─ core/src/main/antlr4/CqlParser.g4
```

## Re-importing a newer Cassandra grammar

1. Download the new `Lexer.g` and `Parser.g` into `upstream/` and update
   `upstream/VERSION`:
   ```
   TAG=cassandra-5.0   # or the tag you are importing
   for f in Lexer Parser Cql; do
     curl -sSL "https://raw.githubusercontent.com/apache/cassandra/$TAG/src/antlr/$f.g" \
       -o "tools/antlr-import/upstream/$f.g"
   done
   ```
2. Run the converter:
   ```
   python3 tools/antlr-import/convert.py
   ```
   It **fails loudly** if an anchor it depends on is missing, that is the signal
   that a hand-reviewed part of the grammar changed shape (see below).
3. Regenerate + run the tests:
   ```
   sbt "core/testOnly net.nmoncho.helenus.internal.cql.*"
   sbt "core/testOnly net.nmoncho.helenus.internal.macros.*"
   ```
   `CqlConformanceSpec` and `CqlValidatorSpec` are the safety net: they assert a
   large corpus of valid CQL still parses. Triage any new rejections.

## What the converter does

**Mechanical (deterministic, ~80% of the work):**

- strips `@header` / `@members` / `@init` / `@after` action blocks
- strips every `returns [...]` clause and rule argument `rule[...]`
- strips inline `{ ...java... }` actions, `$x = ...` assignments and element
  labels (`x=`, `x+=`)
- strips ANTLR3 syntactic predicates `( ... )=>`
- rewrites the grammar headers and adds a `root` entry rule
- copies keyword and fragment rules **verbatim**, so a new CQL keyword in a
  future Cassandra release flows through automatically

**Judgment (hand-written in `convert.py`, revisit on a new version):**

- the operator/punctuation token block (`LPAREN`, `EQ`, `COLON`, ...): a split
  lexer/parser needs these named, whereas Cassandra's *combined* `Cql.g` gets
  them implicitly from the parser's inline `'('`, `'='`, `':'` literals
- the six lexer rules that carried actions/predicates, rewritten for ANTLR4:
  `STRING_LITERAL`, `QUOTED_NAME`, `FLOAT` (the `1..3` vs `1.5` disambiguation),
  `WS`, `COMMENT`, `MULTILINE_COMMENT`
- word-like parser literals (e.g. `'expr('`) auto-emitted as lexer tokens

## Intentional deviations from upstream

These are the places helenus knowingly diverges from Cassandra's grammar. They
live in `convert.py` as anchored patches that fail if upstream changes:

- **Bind markers are not accepted as bare `SELECT` selectors.** Cassandra's
  grammar accepts `SELECT ?` / `SELECT :name` syntactically (Cassandra rejects
  them at prepare time). helenus drops the two marker alternatives from
  `selectionLiteral` because the interpolator relies on a marker being invalid in
  a selector position to decide it must *inject* an identifier (a column name)
  there rather than *bind* a value, otherwise `cql"SELECT $col"` would bind
  `$col` as a value and silently build a broken query.

  A knock-on consequence (tracked as a gap, see `CqlValidator`'s scaladoc and
  `CqlValidatorSpec`): because selector function arguments resolve through the
  same `unaliasedSelector` rule, a bind marker used as a *function argument in the
  select list* (e.g. `SELECT similarity_cosine(v, ?) FROM t`) is also rejected.
  Only bind markers are affected there; column, constant, string and
  collection-literal arguments parse. Use `"...".toUnsafeCQL` when you need it.

## Files

| Path                            | Purpose                                                        |
|---------------------------------|----------------------------------------------------------------|
| `convert.py`                    | the converter                                                  |
| `upstream/{Lexer,Parser,Cql}.g` | pinned copies of Cassandra's grammars                          |
| `upstream/VERSION`              | the Cassandra repo + tag the copies come from                  |
