#!/usr/bin/env python3
"""
jmh_plot.py — Convert JMH benchmark CSV reports to SVG plots.

Usage:
    python3 jmh_plot.py results.csv                  # single file
    python3 jmh_plot.py results1.csv results2.csv    # multiple files
    python3 jmh_plot.py *.csv                        # glob

Output:
    One SVG file per benchmark *group* (same class / param axis),
    placed alongside the input file (or in --outdir if specified).

Options:
    --outdir DIR   Write all SVGs to DIR instead of next to the CSV
    --style dark   Use a dark background theme (default: light)
    --orient h     Horizontal bar chart (default: v = vertical / grouped bars)
    --no-errbar    Suppress error bars
"""

import argparse
import csv
import math
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("svg")           # no GUI needed
import matplotlib.pyplot as plt
import matplotlib.ticker as mticker
import numpy as np


# ── colour palettes ──────────────────────────────────────────────────────────

PALETTES = {
    "light": [
        "#4C72B0", "#DD8452", "#55A868", "#C44E52",
        "#8172B3", "#937860", "#DA8BC3", "#8C8C8C",
        "#CCB974", "#64B5CD",
    ],
    "dark": [
        "#5B8DB8", "#E8965A", "#6ABF7B", "#D9595C",
        "#9E8FCE", "#B09B7A", "#E49FD1", "#AAAAAA",
        "#D9C97E", "#7AC8DB",
    ],
}


# ── CSV parsing ───────────────────────────────────────────────────────────────

def _strip(v: str) -> str:
    return v.strip().strip('"')


def parse_jmh_csv(path: Path) -> list[dict]:
    """Parse a JMH-generated CSV file and return a list of row dicts."""
    rows = []
    with open(path, newline="", encoding="utf-8-sig") as fh:
        # JMH sometimes writes quoted headers; handle both styles
        reader = csv.DictReader(fh)
        # Normalise header names: strip whitespace and quotes
        reader.fieldnames = [_strip(f) for f in (reader.fieldnames or [])]
        for raw in reader:
            row = {_strip(k): _strip(v) for k, v in raw.items() if k}
            if not row:
                continue
            rows.append(row)
    return rows


def _short_name(benchmark: str) -> str:
    """Turn 'com.example.Foo.testMethod' → 'Foo.testMethod'."""
    parts = benchmark.rsplit(".", 2)
    return ".".join(parts[-2:]) if len(parts) >= 2 else benchmark


def _method_name(benchmark: str) -> str:
    """Return only the method part: 'com.example.Foo.testMethod' → 'testMethod'."""
    return benchmark.rsplit(".", 1)[-1]


def _group_key(benchmark: str) -> str:
    """Group benchmarks that share the same class."""
    parts = benchmark.rsplit(".", 1)
    return parts[0] if len(parts) == 2 else benchmark


# ── data organisation ─────────────────────────────────────────────────────────

def organise(rows: list[dict]):
    """
    Returns a dict:
        group_key → {
            "unit":  str,
            "param": str | None,          # param column name, e.g. "Param: size"
            "benchmarks": [str, ...],     # sorted unique method labels
            "param_values": [str, ...],   # sorted unique param values (or [""])
            "data": {bench: {param_val: (score, error)}}
        }
    """
    groups: dict[str, dict] = {}

    for row in rows:
        bench = row.get("Benchmark", "")
        score_str = row.get("Score", "")
        err_str = row.get("Score Error (99.9%)", row.get("Score Error", "0"))
        unit = row.get("Unit", "")

        try:
            score = float(score_str)
        except ValueError:
            continue
        try:
            error = float(err_str) if err_str and err_str != "NaN" else 0.0
        except ValueError:
            error = 0.0

        # Find any "Param: *" column
        param_col = next((k for k in row if k.startswith("Param:")), None)
        param_val = row[param_col].strip() if param_col else ""

        gk = _group_key(bench)
        if gk not in groups:
            groups[gk] = {
                "unit": unit,
                "param": param_col,
                "benchmarks": set(),
                "param_values": set(),
                "data": defaultdict(dict),
            }

        label = _method_name(bench)
        groups[gk]["benchmarks"].add(label)
        groups[gk]["param_values"].add(param_val)
        groups[gk]["data"][label][param_val] = (score, error)
        if not groups[gk]["unit"]:
            groups[gk]["unit"] = unit

    # Freeze sets → sorted lists
    for g in groups.values():
        g["benchmarks"] = sorted(g["benchmarks"])
        # Sort param_values numerically if possible
        pv = g["param_values"]
        try:
            g["param_values"] = sorted(pv, key=lambda x: float(x) if x else 0)
        except ValueError:
            g["param_values"] = sorted(pv)

    return groups


# ── plotting ──────────────────────────────────────────────────────────────────

def _apply_theme(fig, ax, style: str):
    if style == "dark":
        fig.patch.set_facecolor("#1E1E2E")
        ax.set_facecolor("#2A2A3E")
        ax.tick_params(colors="#CCCCCC")
        ax.xaxis.label.set_color("#CCCCCC")
        ax.yaxis.label.set_color("#CCCCCC")
        ax.title.set_color("#EEEEEE")
        for spine in ax.spines.values():
            spine.set_edgecolor("#555577")
        ax.legend(facecolor="#2A2A3E", edgecolor="#555577",
                  labelcolor="#CCCCCC")
    else:
        fig.patch.set_facecolor("#FFFFFF")
        ax.set_facecolor("#F8F9FA")
        for spine in ax.spines.values():
            spine.set_edgecolor("#CCCCCC")


def plot_group(group_key: str, gdata: dict, outdir: Path,
               orient: str, no_errbar: bool, style: str) -> Path:
    """Render one benchmark group to an SVG file and return its path."""

    benchmarks = gdata["benchmarks"]
    param_values = gdata["param_values"]
    unit = gdata["unit"]
    data = gdata["data"]
    param_col = gdata["param"]

    n_benches = len(benchmarks)
    n_params = len(param_values)
    palette = PALETTES[style]

    # ── figure geometry ───────────────────────────────────────────────────────
    fig_w = max(7, n_params * n_benches * 0.55 + 3)
    fig_h = 5
    if orient == "h":
        fig_w, fig_h = fig_h, max(5, n_params * n_benches * 0.45 + 2)

    fig, ax = plt.subplots(figsize=(fig_w, fig_h))

    # ── data assembly ─────────────────────────────────────────────────────────
    # x-axis = param_values (or single blank), grouped bars per benchmark
    x = np.arange(n_params)
    total_width = 0.75
    bar_w = total_width / n_benches if n_benches > 1 else 0.5
    offsets = np.linspace(-(total_width - bar_w) / 2,
                          (total_width - bar_w) / 2,
                          n_benches)

    for i, bench in enumerate(benchmarks):
        scores = []
        errors = []
        for pv in param_values:
            val = data[bench].get(pv, (0.0, 0.0))
            scores.append(val[0])
            errors.append(val[1] if not no_errbar else 0.0)

        color = palette[i % len(palette)]
        err_kw = dict(ecolor="#555555" if style == "light" else "#AAAAAA",
                      capsize=3, linewidth=1)

        if orient == "v":
            ax.bar(x + offsets[i], scores, bar_w,
                   label=bench, color=color,
                   yerr=errors if not no_errbar else None,
                   error_kw=err_kw, zorder=3)
        else:  # horizontal
            ax.barh(x + offsets[i], scores, bar_w,
                    label=bench, color=color,
                    xerr=errors if not no_errbar else None,
                    error_kw=err_kw, zorder=3)

    # ── axes labels & ticks ───────────────────────────────────────────────────
    # X-axis labels: use numeric param values; fall back to raw string
    x_labels = param_values if param_values != [""] else [""]

    param_label = (param_col.replace("Param:", "").strip()
                   if param_col else "Benchmark")

    if orient == "v":
        ax.set_xticks(x)
        ax.set_xticklabels(x_labels, rotation=30 if n_params > 6 else 0,
                           ha="right" if n_params > 6 else "center")
        if param_values != [""]:
            ax.set_xlabel(param_label, fontsize=11)
        ax.set_ylabel(unit, fontsize=11)
        ax.yaxis.set_major_formatter(
            mticker.FuncFormatter(lambda v, _: _fmt_score(v)))
        ax.grid(axis="y", linestyle="--", alpha=0.5, zorder=0)
    else:
        ax.set_yticks(x)
        ax.set_yticklabels(x_labels)
        if param_values != [""]:
            ax.set_ylabel(param_label, fontsize=11)
        ax.set_xlabel(unit, fontsize=11)
        ax.xaxis.set_major_formatter(
            mticker.FuncFormatter(lambda v, _: _fmt_score(v)))
        ax.grid(axis="x", linestyle="--", alpha=0.5, zorder=0)

    # ── title ─────────────────────────────────────────────────────────────────
    short = group_key.rsplit(".", 1)[-1]  # last class segment
    ax.set_title(short, fontsize=13, fontweight="bold", pad=10)

    # ── legend ────────────────────────────────────────────────────────────────
    if n_benches > 1:
        ax.legend(title="Method", fontsize=9, title_fontsize=9,
                  loc="best", framealpha=0.85)

    _apply_theme(fig, ax, style)
    fig.tight_layout()

    # ── save ──────────────────────────────────────────────────────────────────
    safe = re.sub(r"[^\w.-]", "_", group_key)
    out_path = outdir / f"{safe}.svg"
    fig.savefig(out_path, format="svg", bbox_inches="tight")
    plt.close(fig)
    return out_path


def _fmt_score(v: float) -> str:
    """Human-friendly axis tick labels."""
    if v == 0:
        return "0"
    if abs(v) >= 1_000_000:
        return f"{v/1_000_000:.1f}M"
    if abs(v) >= 1_000:
        return f"{v/1_000:.1f}k"
    if abs(v) < 0.01:
        return f"{v:.2e}"
    return f"{v:.3g}"


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(
        description="Convert JMH benchmark CSVs to SVG plots.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("csv_files", nargs="+", metavar="FILE.csv",
                    help="One or more JMH result CSV files")
    ap.add_argument("--outdir", metavar="DIR", default=None,
                    help="Output directory for SVG files (default: same as CSV)")
    ap.add_argument("--style", choices=["light", "dark"], default="light",
                    help="Plot colour theme (default: light)")
    ap.add_argument("--orient", choices=["v", "h"], default="v",
                    help="Bar orientation: v=vertical, h=horizontal (default: v)")
    ap.add_argument("--no-errbar", action="store_true",
                    help="Suppress error bars")
    args = ap.parse_args()

    produced: list[Path] = []

    for csv_path_str in args.csv_files:
        csv_path = Path(csv_path_str)
        if not csv_path.exists():
            print(f"[WARN] File not found: {csv_path}", file=sys.stderr)
            continue

        outdir = Path(args.outdir) if args.outdir else csv_path.parent
        outdir.mkdir(parents=True, exist_ok=True)

        print(f"Parsing {csv_path} …")
        rows = parse_jmh_csv(csv_path)
        if not rows:
            print(f"  [WARN] No data rows found in {csv_path}", file=sys.stderr)
            continue

        groups = organise(rows)
        print(f"  Found {len(groups)} benchmark group(s): "
              f"{', '.join(g.rsplit('.', 1)[-1] for g in groups)}")

        for gk, gdata in groups.items():
            svg = plot_group(gk, gdata, outdir,
                             orient=args.orient,
                             no_errbar=args.no_errbar,
                             style=args.style)
            print(f"  → {svg}")
            produced.append(svg)

    if produced:
        print(f"\nDone. {len(produced)} SVG(s) written.")
        print("\nEmbed in Markdown with:")
        for p in produced:
            print(f"  ![{p.stem}]({p.name})")
    else:
        print("No output produced.", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

