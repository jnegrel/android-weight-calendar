#!/usr/bin/env python3
"""Convert the historical weight spreadsheet (one tab per year) into the app's CSV format.

Usage:  python3 tools/ods_to_csv.py Poids.ods weights.csv

Spreadsheet layout expected on every tab: column A = date, column B = weight (kg).
Other columns (the 7-day average) are ignored; the app computes its own.

Output columns:  date,weight_kg,estimated
  - empty days between two weights are filled by linear interpolation  -> estimated=1
  - runs of 3+ equal non-zero steps (hand-made interpolations, e.g.
    103.4, 103.55, 103.7, ...) are flagged as well                     -> estimated=1
    (for +-0.1 kg steps, which also happen naturally, 4+ steps are required)
Only needs the Python standard library.
"""
import sys
import zipfile
import datetime as dt
import xml.etree.ElementTree as ET

TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
T = lambda tag: f"{{{TABLE}}}{tag}"
O = lambda tag: f"{{{OFFICE}}}{tag}"


def read_weights(path):
    """Return {date: weight} for every row of every tab that has both."""
    root = ET.fromstring(zipfile.ZipFile(path).read("content.xml"))
    weights = {}
    for row in root.iter(T("table-row")):
        cells = []
        for cell in row:
            cells += [cell] * min(int(cell.get(T("number-columns-repeated"), 1)), 2)
            if len(cells) >= 2:
                break
        if len(cells) < 2:
            continue
        date, value = cells[0].get(O("date-value")), cells[1].get(O("value"))
        if date and value:
            weights[dt.date.fromisoformat(date[:10])] = float(value)
    return weights


def fill_gaps(weights):
    """Return a list of (date, weight, estimated) for every day from first to last."""
    days = sorted(weights)
    out = []
    for prev, nxt in zip(days, days[1:] + [None]):
        out.append([prev, weights[prev], False])
        if nxt is None:
            break
        gap = (nxt - prev).days
        for i in range(1, gap):  # straight line between the two measured days
            w = weights[prev] + (weights[nxt] - weights[prev]) * i / gap
            out.append([prev + dt.timedelta(days=i), w, True])
    return out


def flag_ramps(rows, min_steps=3):
    """Flag interior points of runs with >= min_steps identical non-zero steps
    (one more step required when the step is only 0.1 kg)."""
    steps = [round(b[1] - a[1], 6) for a, b in zip(rows, rows[1:])]
    i = 0
    while i < len(steps):
        j = i
        while j + 1 < len(steps) and steps[j + 1] == steps[i]:
            j += 1
        needed = min_steps + 1 if abs(steps[i]) == 0.1 else min_steps
        if steps[i] != 0 and j - i + 1 >= needed:
            for k in range(i + 1, j + 1):  # points strictly inside the run
                rows[k][2] = True
        i = j + 1


def main(src, dst):
    rows = fill_gaps(read_weights(src))
    measured_gaps = sum(r[2] for r in rows)
    flag_ramps(rows)
    with open(dst, "w") as f:
        f.write("date,weight_kg,estimated\n")
        for date, w, est in rows:
            f.write(f"{date.isoformat()},{w:.2f},{int(est)}\n")
    print(f"{len(rows)} days written to {dst}: {rows[0][0]} .. {rows[-1][0]}")
    print(f"  {measured_gaps} empty days interpolated, "
          f"{sum(r[2] for r in rows) - measured_gaps} hand-interpolated days detected")


if __name__ == "__main__":
    main(*sys.argv[1:3])
