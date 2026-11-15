#!/usr/bin/env python3
"""Render the PCM test CSV as a two-panel PDM/PCM SVG diagram."""

import argparse
import csv
import html
from pathlib import Path


WIDTH = 1200
HEIGHT = 700
LEFT = 90
RIGHT = 30
TOP = 55
PANEL_HEIGHT = 240
GAP = 80


def scale_x(index: float, maximum: float) -> float:
    plot_width = WIDTH - LEFT - RIGHT
    return LEFT + index * plot_width / maximum


def line(x1, y1, x2, y2, color="#444", width=1, dash=None):
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" '
        f'y2="{y2:.2f}" stroke="{color}" stroke-width="{width}"{dash_attr}/>'
    )


def text(x, y, value, size=14, anchor="start", color="#222", weight="normal"):
    return (
        f'<text x="{x:.2f}" y="{y:.2f}" font-family="sans-serif" '
        f'font-size="{size}" text-anchor="{anchor}" fill="{color}" '
        f'font-weight="{weight}">{html.escape(str(value))}</text>'
    )


def load_rows(csv_path: Path):
    with csv_path.open(newline="") as stream:
        return list(csv.DictReader(stream))


def render(rows, output_path: Path):
    words = [row["pdm_word"] for row in rows]
    bits = [int(bit) for word in words for bit in word]
    pcm_points = [
        (int(row["sample"]), int(row["pcm"]))
        for row in rows
        if row["pcm_valid"].lower() == "true"
    ]

    if not bits:
        raise ValueError("CSV contains no PDM words")
    if not pcm_points:
        raise ValueError("CSV contains no valid PCM samples")

    x_max = max(len(bits) - 1, 1)
    sample_max = max(index for index, _ in pcm_points)
    sample_max = max(sample_max, 1)
    pcm_y_min, pcm_y_max = -128, 127

    pdm_top = TOP
    pcm_top = TOP + PANEL_HEIGHT + GAP
    pdm_mid = pdm_top + PANEL_HEIGHT / 2
    pcm_mid = pcm_top + PANEL_HEIGHT / 2

    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" '
        f'viewBox="0 0 {WIDTH} {HEIGHT}">',
        '<rect width="100%" height="100%" fill="white"/>',
        text(WIDTH / 2, 28, "PDM input and PCM output", 20, "middle", weight="bold"),
        text(LEFT, pdm_top - 18, "PDM bitstream", 16, weight="bold"),
        text(LEFT, pcm_top - 18, "PCM amplitude", 16, weight="bold"),
    ]

    # PDM panel
    svg.extend(
        [
            line(LEFT, pdm_mid, WIDTH - RIGHT, pdm_mid, "#999"),
            line(LEFT, pdm_top, LEFT, pdm_top + PANEL_HEIGHT, "#444"),
            line(LEFT, pdm_top + PANEL_HEIGHT, WIDTH - RIGHT, pdm_top + PANEL_HEIGHT, "#444"),
            text(LEFT - 12, pdm_top + 8, "1", 13, "end"),
            text(LEFT - 12, pdm_top + PANEL_HEIGHT - 2, "0", 13, "end"),
        ]
    )

    bit_width = (WIDTH - LEFT - RIGHT) / len(bits)
    high = pdm_top + 35
    low = pdm_top + PANEL_HEIGHT - 35
    previous_y = low
    for index, bit in enumerate(bits):
        x0 = LEFT + index * bit_width
        x1 = x0 + bit_width
        y = high if bit else low
        if index:
            svg.append(line(x0, previous_y, x0, y, "#286090", 1))
        svg.append(line(x0, y, x1, y, "#286090", 2))
        previous_y = y

    # PCM panel
    svg.extend(
        [
            line(LEFT, pcm_mid, WIDTH - RIGHT, pcm_mid, "#999"),
            line(LEFT, pcm_top, LEFT, pcm_top + PANEL_HEIGHT, "#444"),
            line(LEFT, pcm_top + PANEL_HEIGHT, WIDTH - RIGHT, pcm_top + PANEL_HEIGHT, "#444"),
            text(LEFT - 12, pcm_top + 8, "127", 13, "end"),
            text(LEFT - 12, pcm_mid + 5, "0", 13, "end"),
            text(LEFT - 12, pcm_top + PANEL_HEIGHT, "-128", 13, "end"),
        ]
    )

    def pcm_y(value):
        return pcm_top + (pcm_y_max - value) * PANEL_HEIGHT / (pcm_y_max - pcm_y_min)

    previous = None
    for sample_index, value in pcm_points:
        x = scale_x(sample_index, sample_max)
        y = pcm_y(value)
        if previous is not None:
            svg.append(line(previous[0], previous[1], x, y, "#c0392b", 2))
        svg.append(line(x, pcm_mid, x, y, "#c0392b", 1))
        svg.append(f'<circle cx="{x:.2f}" cy="{y:.2f}" r="4" fill="#c0392b"/>')
        svg.append(text(x, y - 10, value, 12, "middle", "#c0392b"))
        previous = (x, y)

    # Shared sample-index labels
    axis_y = pcm_top + PANEL_HEIGHT + 35
    label_step = max(1, len(pcm_points) // 8)
    for point_index, (sample_index, _) in enumerate(pcm_points):
        x = scale_x(sample_index, sample_max)
        svg.append(line(x, pcm_top + PANEL_HEIGHT, x, pcm_top + PANEL_HEIGHT + 6, "#444"))
        if point_index % label_step == 0 or point_index == len(pcm_points) - 1:
            svg.append(text(x, axis_y, sample_index, 12, "middle"))
    svg.append(text(WIDTH / 2, axis_y + 24, "PCM sample index", 13, "middle"))

    svg.append("</svg>")
    output_path.write_text("\n".join(svg), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path, help="PCM test CSV input")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="SVG output path (default: alongside CSV)",
    )
    args = parser.parse_args()

    output = args.output or args.csv.with_suffix(".svg")
    render(load_rows(args.csv), output)
    print(f"Wrote {output}")


if __name__ == "__main__":
    main()
