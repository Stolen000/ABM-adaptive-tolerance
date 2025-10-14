#!/usr/bin/env python3
import pandas as pd
import matplotlib.pyplot as plt
import os
import numpy as np

INPUT_DIR = "."
OUTPUT_BASE = "analysis_results_full"

# Color palette
COLOR_GLOBAL = "#111111"
COLOR_RACE = {"blue": "#0655BD", "green": "#2ca02c"}
COLOR_CLASSES = ["#ff4500", "#ffa500", "#008000"]  # Poor, Mid, Rich

def ensure_dirs(base_out):
    for sub in ["global", "race", "class"]:
        os.makedirs(os.path.join(base_out, sub), exist_ok=True)

def weighted_average_series(series_list, weight_list):
    """Compute per-row weighted average"""
    arr_vals = np.array([s.values for s in series_list])
    arr_weights = np.array([w.values for w in weight_list])
    weighted_sum = np.nansum(arr_vals * arr_weights, axis=0)
    total_weight = np.nansum(arr_weights, axis=0)
    result = np.divide(weighted_sum, total_weight, out=np.full_like(weighted_sum, np.nan), where=total_weight!=0)
    return result

def plot_series(x, ys, labels, colors, ylabel, outpath, title, ylimits=None):
    plt.figure()
    for y, lab, col in zip(ys, labels, colors):
        plt.plot(x, y, label=lab, linewidth=1.8, color=col)
    plt.xlabel("Ticks")
    plt.ylabel(ylabel)
    if ylimits:
        plt.ylim(*ylimits)
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(outpath, dpi=150)
    plt.close()

def process_csv(csv_path):
    print(f"📈 Processing {csv_path}")
    filename = os.path.splitext(os.path.basename(csv_path))[0]
    out_dir = os.path.join(OUTPUT_BASE, filename)
    ensure_dirs(out_dir)

    try:
        df = pd.read_csv(csv_path, on_bad_lines='skip', engine='python')
    except Exception as e:
        print(f"⚠️ Could not read {csv_path}: {e}")
        return

    if "tick" not in df.columns:
        df["tick"] = df.index * 10

    for col in df.columns:
        df[col] = pd.to_numeric(df[col], errors="coerce")

    df = df.dropna(axis=1, how='all')
    ticks = df["tick"]

    # --- Happiness ---
    race_cols = ["Blue_Happy", "Green_Happy"]
    class_cols = ["Class0_Happy", "Class1_Happy", "Class2_Happy"]
    if all(c in df.columns for c in race_cols):
        plot_series(
            ticks,
            [df["Blue_Happy"], df["Green_Happy"]],
            ["Blue", "Green"],
            [COLOR_RACE["blue"], COLOR_RACE["green"]],
            "Happiness Rate",
            os.path.join(out_dir, "race", "happiness.png"),
            "Happiness by Race",
            ylimits=(0, 1)
        )
    if all(c in df.columns for c in class_cols):
        total = df[class_cols].mean(axis=1)
        plot_series(
            ticks,
            df[class_cols].values.T.tolist() + [total.values],
            ["Poor (0)", "Mid (1)", "Rich (2)", "Total (avg)"],
            COLOR_CLASSES + [COLOR_GLOBAL],
            "Happiness Rate",
            os.path.join(out_dir, "class", "happiness.png"),
            "Happiness by Class",
            ylimits=(0, 1)
        )

    # --- Tolerance ---
    race_cols = ["Blue_Tolerance", "Green_Tolerance"]
    class_cols = ["Class0_Tolerance", "Class1_Tolerance", "Class2_Tolerance"]
    if all(c in df.columns for c in race_cols):
        plot_series(
            ticks,
            [df["Blue_Tolerance"], df["Green_Tolerance"]],
            ["Blue", "Green"],
            [COLOR_RACE["blue"], COLOR_RACE["green"]],
            "Tolerance Level",
            os.path.join(out_dir, "race", "tolerance.png"),
            "Tolerance by Race"
        )
    if all(c in df.columns for c in class_cols):
        total = df[class_cols].mean(axis=1)
        plot_series(
            ticks,
            df[class_cols].values.T.tolist() + [total.values],
            ["Poor (0)", "Mid (1)", "Rich (2)", "Total (avg)"],
            COLOR_CLASSES + [COLOR_GLOBAL],
            "Tolerance Level",
            os.path.join(out_dir, "class", "tolerance.png"),
            "Tolerance by Class"
        )

    # --- Affordability ---
    race_cols = ["AffBelow_Blue", "AffBelow_Green"]
    class_cols = ["AffBelow_Class0", "AffBelow_Class1", "AffBelow_Class2"]
    if all(c in df.columns for c in race_cols):
        plot_series(
            ticks,
            [df["AffBelow_Blue"], df["AffBelow_Green"]],
            ["Blue", "Green"],
            [COLOR_RACE["blue"], COLOR_RACE["green"]],
            "Share Below Threshold",
            os.path.join(out_dir, "race", "affordability.png"),
            "Affordability by Race"
        )
    if all(c in df.columns for c in class_cols):
        total = df[class_cols].mean(axis=1)
        plot_series(
            ticks,
            df[class_cols].values.T.tolist() + [total.values],
            ["Poor (0)", "Mid (1)", "Rich (2)", "Total (avg)"],
            COLOR_CLASSES + [COLOR_GLOBAL],
            "Share Below Threshold",
            os.path.join(out_dir, "class", "affordability.png"),
            "Affordability by Class"
        )

    # --- Failed Move Attempts ---
    race_cols = ["FailedMoveAttempts_Green", "FailedMoveAttempts_Blue"]
    class_cols = ["FailedMoveAttempts_Class0_Poor", "FailedMoveAttempts_Class1_Mid", "FailedMoveAttempts_Class2_Rich"]
    plt.figure()
    if all(c in df.columns for c in race_cols):
        plt.plot(df["tick"], df["FailedMoveAttempts_Green"], label="Green (race)", color="green", linestyle="--", alpha=0.6)
        plt.plot(df["tick"], df["FailedMoveAttempts_Blue"], label="Blue (race)", color="blue", linestyle="--", alpha=0.6)
    if all(c in df.columns for c in class_cols):
        plt.plot(df["tick"], df["FailedMoveAttempts_Class0_Poor"], label="Poor (class)", color="red")
        plt.plot(df["tick"], df["FailedMoveAttempts_Class1_Mid"], label="Mid (class)", color="orange")
        plt.plot(df["tick"], df["FailedMoveAttempts_Class2_Rich"], label="Rich (class)", color="green")
    plt.xlabel("Ticks")
    plt.ylabel("Failed Moves")
    plt.legend()
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, "global", "failed_moves.png"))
    plt.close()

     # --- Average Choices per Unhappy Agent (by race) ---
    race_cols = ["AvgChoicesPerUnhappy_Green", "AvgChoicesPerUnhappy_Blue"]
    if all(c in df.columns for c in race_cols):
        plt.figure()
        plt.plot(df["tick"], df["AvgChoicesPerUnhappy_Green"], label="Green", color=COLOR_RACE["green"], linewidth=2)
        plt.plot(df["tick"], df["AvgChoicesPerUnhappy_Blue"], label="Blue", color=COLOR_RACE["blue"], linewidth=2)
        avg_race = df[race_cols].mean(axis=1)
        plt.plot(df["tick"], avg_race, label="Average", color=COLOR_GLOBAL, linestyle=":", linewidth=2)
        plt.xlabel("Ticks")
        plt.ylabel("Avg Choices")
        #plt.yscale("log")
        plt.legend()
        plt.grid(True, alpha=0.3) #plt.grid(True, alpha=0.3, which="both")
        plt.tight_layout()
        plt.savefig(os.path.join(out_dir, "race", "avg_choices_per_unhappy.png"))
        plt.close()

    # --- Average Choices per Unhappy Agent (by class) ---
    class_cols = [
        "AvgChoicesPerUnhappy_Class0_Poor",
        "AvgChoicesPerUnhappy_Class1_Mid",
        "AvgChoicesPerUnhappy_Class2_Rich"
    ]
    if all(c in df.columns for c in class_cols):
        plt.figure()
        plt.plot(df["tick"], df[class_cols[0]], label="Poor", color=COLOR_CLASSES[0], linewidth=2)
        plt.plot(df["tick"], df[class_cols[1]], label="Mid", color=COLOR_CLASSES[1], linewidth=2)
        plt.plot(df["tick"], df[class_cols[2]], label="Rich", color=COLOR_CLASSES[2], linewidth=2)
        avg_class = df[class_cols].mean(axis=1)
        plt.plot(df["tick"], avg_class, label="Average (class)", color=COLOR_GLOBAL, linestyle=":", linewidth=2)
        plt.xlabel("Ticks")
        plt.ylabel("Avg Choices")
        #plt.yscale("log")
        plt.legend()
        plt.grid(True, alpha=0.3) #plt.grid(True, alpha=0.3, which="both")
        plt.tight_layout()
        plt.savefig(os.path.join(out_dir, "class", "avg_choices_per_unhappy.png"))
        plt.close()

   # --- Cluster statistics by race (variance only) ---
    for race, prefix in [("Green", "Cluster_Green"), ("Blue", "Cluster_Blue")]:
        var_col = f"{prefix}_Var"
        if var_col in df.columns:
            plt.figure()
            plt.plot(df["tick"], df[var_col], color=COLOR_RACE[race.lower()], linewidth=2)
            plt.xlabel("Ticks")
            plt.ylabel("Cluster Variance")
            plt.legend()
            plt.grid(True, alpha=0.3)
            plt.tight_layout()
            plt.savefig(os.path.join(out_dir, "race", f"{race.lower()}_cluster_variance.png"))
            plt.close()

    # --- Cluster statistics by class (variance only) ---
    for cls, prefix, color in zip(["Poor","Mid","Rich"],
                                  ["Cluster_Class0_Poor", "Cluster_Class1_Mid", "Cluster_Class2_Rich"],
                                  COLOR_CLASSES):
        var_col = f"{prefix}_Var"
        if var_col in df.columns:
            plt.figure()
            plt.plot(df["tick"], df[var_col], color=color, linewidth=2)
            plt.xlabel("Ticks")
            plt.ylabel("Cluster Variance")
            plt.legend()
            plt.grid(True, alpha=0.3)
            plt.tight_layout()
            plt.savefig(os.path.join(out_dir, "class", f"{cls.lower()}_cluster_variance.png"))
            plt.close()

    # --- Price correlations ---
    for corr in ["PriceClassCorr", "PriceRaceCorr"]:
        if corr in df.columns:
            plt.figure()
            plt.plot(df["tick"], df[corr], label=corr, color=COLOR_GLOBAL)
            plt.xlabel("Ticks")
            plt.ylabel("Correlation")
            plt.grid(True, alpha=0.3)
            plt.tight_layout()
            plt.savefig(os.path.join(out_dir, "global", f"{corr}.png"))
            plt.close()

    print(f"✅ Saved all plots to {out_dir}\n")

# --- Process all CSV files ---
os.makedirs(OUTPUT_BASE, exist_ok=True)
for csv_file in os.listdir(INPUT_DIR):
    if csv_file.endswith(".csv"):
        csv_path = os.path.join(INPUT_DIR, csv_file)
        process_csv(csv_path)
