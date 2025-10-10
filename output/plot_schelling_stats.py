#!/usr/bin/env python3
import sys
import os
import csv
import math
from typing import List, Tuple
import matplotlib.pyplot as plt

# ---- Color palette ----
COLOR_GLOBAL = "#111111"
COLOR_RACE = {"blue": "#0655BD", "green": "#2ca02c"}
COLOR_CLASSES = ["#249dbb", "#1411BB", "#8f0c0c"]  # C0, C1, C2 (orange, purple, gray)

# ---- Column indices according to the provided Java writer ----
# 0..44 inclusive
IDX = {
    "MoranI": 0,
    "MoranT": 1,
    "MoranClass": 2,
    "TotalMoves": 3,
    "GlobalHappy": 4,
    "NumAgents": 5,
    "ChangedMind": 6,
    # Tolerance descriptive stats (global / natives / migrants)
    "TolN": 7, "TolMean": 8, "TolVar": 9, "TolSD": 10, "TolKurt": 11, "TolSkew": 12,
    "NTolN": 13, "NTolMean": 14, "NTolVar": 15, "NTolSD": 16, "NTolKurt": 17, "NTolSkew": 18,
    "MTolN": 19, "MTolMean": 20, "MTolVar": 21, "MTolSD": 22, "MTolKurt": 23, "MTolSkew": 24,
    # By race (Blue=0, Green=1) count / H / T / A
    "R0_Count": 25, "R1_Count": 26,
    "R0_H": 27, "R1_H": 28,
    "R0_T": 29, "R1_T": 30,
    "R0_A": 31, "R1_A": 32,
    # By class (0,1,2) count / H / T / A
    "C0_Count": 33, "C1_Count": 34, "C2_Count": 35,
    "C0_H": 36, "C1_H": 37, "C2_H": 38,
    "C0_T": 39, "C1_T": 40, "C2_T": 41,
    "C0_A": 42, "C1_A": 43, "C2_A": 44,
    "AffBelow_All": 45,
    "AffBelow_Blue": 46,
    "AffBelow_Green": 47,
    "AffBelow_C0": 48,
    "AffBelow_C1": 49,
    "AffBelow_C2": 50,
}

MIN_COLS = max(IDX.values()) + 1

def to_float(x: str) -> float:
    try:
        return float(x)
    except Exception:
        return float("nan")

def pad_row(row: List[str], target: int) -> List[str]:
    if len(row) >= target:
        return row[:target]
    return row + [""] * (target - len(row))

def read_csv_series(path: str) -> dict:
    """Read CSV without headers, returning lists per key in IDX, padded if needed."""
    data = {k: [] for k in IDX.keys()}
    with open(path, "r", newline="") as f:
        reader = csv.reader(f)
        for row in reader:
            # skip empty rows
            if not row or all((c is None or str(c).strip() == "") for c in row):
                continue
            row = pad_row(row, MIN_COLS)
            for k, i in IDX.items():
                data[k].append(to_float(row[i]))
    return data

def ensure_dirs(base_out: str):
    for sub in ["global", "race", "class"]:
        os.makedirs(os.path.join(base_out, sub), exist_ok=True)

def savefig(path: str, title: str):
    plt.title(title)
    plt.tight_layout()
    plt.savefig(path, dpi=150)
    plt.close()

def weighted_mean(values: List[float], weights: List[float]) -> List[float]:
    assert len(values) == len(weights)
    out = []
    for v, w in zip(values, weights):
        if (w is None) or math.isnan(w) or w == 0 or v is None or math.isnan(v):
            out.append(float("nan"))
        else:
            out.append(v * w)
    # sum weights and values per time step is done outside (per-sample)
    return out

def compute_global_from_parts(means: List[Tuple[List[float], List[float]]]) -> List[float]:
    """
    Compute per-timestep weighted mean from multiple (series, counts) pairs.
    means: list of (mean_series, count_series)
    """
    if not means:
        return []
    n = len(means[0][0])
    global_series = []
    for t in range(n):
        num = 0.0
        den = 0.0
        for series, counts in means:
            v = series[t]; c = counts[t]
            if v is None or math.isnan(v) or c is None or math.isnan(c) or c <= 0:
                continue
            num += v * c
            den += c
        global_series.append((num / den) if den > 0 else float("nan"))
    return global_series

def plot_series(x, ys, labels, colors, ylabel, outpath, title, ylimits=None):
    plt.figure()
    for y, lab, col in zip(ys, labels, colors):
        plt.plot(x, y, label=lab, linewidth=1.8, color=col)
    plt.xlabel("Step")
    plt.ylabel(ylabel)
    if ylimits:
        plt.ylim(*ylimits)
    plt.legend()
    savefig(outpath, title)

def main():
    if len(sys.argv) < 2:
        print("Usage: python schelling_plots.py path/to/your_stats.csv [outdir=plots]")
        sys.exit(1)
    csv_path = sys.argv[1]
    outdir = sys.argv[2] if len(sys.argv) > 2 else "plots"
    ensure_dirs(outdir)

    D = read_csv_series(csv_path)
    steps = list(range(len(D["MoranI"])))

    # --- 1) Happiness: Global vs Race ---
    plot_series(
        steps,
        [D["GlobalHappy"], D["R0_H"], D["R1_H"]],
        ["Global", "Blue", "Green"],
        [COLOR_GLOBAL, COLOR_RACE["blue"], COLOR_RACE["green"]],
        "Happiness rate",
        os.path.join(outdir, "race", "happiness_global_vs_race.png"),
        "Happiness — Global vs Race",
        ylimits=(0, 1)
    )

    # --- 2) Happiness: Global vs Class ---
    # For a clean legend, label classes C0/C1/C2
    plot_series(
        steps,
        [D["GlobalHappy"], D["C0_H"], D["C1_H"], D["C2_H"]],
        ["Global", "Class 0", "Class 1", "Class 2"],
        [COLOR_GLOBAL] + COLOR_CLASSES,
        "Happiness rate",
        os.path.join(outdir, "class", "happiness_global_vs_class.png"),
        "Happiness — Global vs Class",
        ylimits=(0, 1)
    )

    # --- 3) Tolerance mean: Global vs Race ---
    plot_series(
        steps,
        [D["TolMean"], D["R0_T"], D["R1_T"]],
        ["Global (mean)", "Blue", "Green"],
        [COLOR_GLOBAL, COLOR_RACE["blue"], COLOR_RACE["green"]],
        "Tolerance (mean)",
        os.path.join(outdir, "race", "tolerance_global_vs_race.png"),
        "Tolerance Mean — Global vs Race",
        ylimits=(0, 100)
    )

    # --- 4) Tolerance mean: Global vs Class ---
    plot_series(
        steps,
        [D["TolMean"], D["C0_T"], D["C1_T"], D["C2_T"]],
        ["Global (mean)", "Class 0", "Class 1", "Class 2"],
        [COLOR_GLOBAL] + COLOR_CLASSES,
        "Tolerance (mean)",
        os.path.join(outdir, "class", "tolerance_global_vs_class.png"),
        "Tolerance Mean — Global vs Class",
        ylimits=(0, 100)
    )

    # --- 5) Counts: Total vs Race ---
    plot_series(
        steps,
        [D["NumAgents"], D["R0_Count"], D["R1_Count"]],
        ["Total agents", "Blue", "Green"],
        [COLOR_GLOBAL, COLOR_RACE["blue"], COLOR_RACE["green"]],
        "Population count",
        os.path.join(outdir, "race", "counts_total_vs_race.png"),
        "Population — Total vs Race"
    )

    # --- 6) Counts: Total vs Class ---
    plot_series(
        steps,
        [D["NumAgents"], D["C0_Count"], D["C1_Count"], D["C2_Count"]],
        ["Total agents", "Class 0", "Class 1", "Class 2"],
        [COLOR_GLOBAL] + COLOR_CLASSES,
        "Population count",
        os.path.join(outdir, "class", "counts_total_vs_class.png"),
        "Population — Total vs Class"
    )

    # --- 7) Affordability mean: Global (weighted) vs Race ---
    global_afford_from_race = compute_global_from_parts([
        (D["R0_A"], D["R0_Count"]),
        (D["R1_A"], D["R1_Count"]),
    ])
    plot_series(
        steps,
        [global_afford_from_race, D["R0_A"], D["R1_A"]],
        ["Global (weighted)", "Blue", "Green"],
        [COLOR_GLOBAL, COLOR_RACE["blue"], COLOR_RACE["green"]],
        "Affordability (mean)",
        os.path.join(outdir, "race", "affordability_global_vs_race.png"),
        "Affordability Mean — Global vs Race"
    )

    # --- 8) Affordability mean: Global (weighted) vs Class ---
    global_afford_from_class = compute_global_from_parts([
        (D["C0_A"], D["C0_Count"]),
        (D["C1_A"], D["C1_Count"]),
        (D["C2_A"], D["C2_Count"]),
    ])
    plot_series(
        steps,
        [global_afford_from_class, D["C0_A"], D["C1_A"], D["C2_A"]],
        ["Global (weighted)", "Class 0", "Class 1", "Class 2"],
        [COLOR_GLOBAL] + COLOR_CLASSES,
        "Affordability (mean)",
        os.path.join(outdir, "class", "affordability_global_vs_class.png"),
        "Affordability Mean — Global vs Class"
    )

        # --- NEW: Count of agents with affordability < 0.3 ---
    plot_series(
        steps,
        [D["AffBelow_All"], D["AffBelow_Blue"], D["AffBelow_Green"]],
        ["All", "Blue", "Green"],
        [COLOR_GLOBAL, COLOR_RACE["blue"], COLOR_RACE["green"]],
        "Agents below affordability threshold (count)",
        os.path.join(outdir, "race", "affordability_below_0_3_counts_race.png"),
        "Affordability < 0.3 — Counts (Global vs Race)",
        ylimits=(0, 1)
    )

    plot_series(
        steps,
        [D["AffBelow_All"], D["AffBelow_C0"], D["AffBelow_C1"], D["AffBelow_C2"]],
        ["All", "Class 0", "Class 1", "Class 2"],
        [COLOR_GLOBAL] + COLOR_CLASSES,
        "Agents below affordability threshold (count)",
        os.path.join(outdir, "class", "affordability_below_0_3_counts_class.png"),
        "Affordability < 0.3 — Counts (Global vs Class)",
        ylimits=(0, 1)
    )


    # --- 9) Moran's indices (all together, global only) ---
    plt.figure()
    plt.plot(steps, D["MoranI"], label="Moran's I (color)", linewidth=1.8, color="#333333")
    plt.plot(steps, D["MoranT"], label="Moran's T (tolerance)", linewidth=1.8, color="#555555")
    plt.plot(steps, D["MoranClass"], label="Moran's I (class)", linewidth=1.8, color="#999999")
    plt.xlabel("Step"); plt.ylabel("Spatial autocorrelation")
    plt.legend()
    savefig(os.path.join(outdir, "global", "morans_all.png"), "Moran metrics — Color / Tolerance / Class")

    print(f"Saved plots under: {outdir}")
    print("Subfolders:")
    print("  - plots/global  (global-only summaries)")
    print("  - plots/race    (Global vs Race overlays)")
    print("  - plots/class   (Global vs Class overlays)")


    

if __name__ == "__main__":
    main()
