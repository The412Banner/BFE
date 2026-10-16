"""Regenerate BFE's icon drawables from the masters in bfe_icons.py.

    python3 scripts/icons/generate.py               # writes app/src/main/res/drawable/ic_bfe_*.xml
    python3 scripts/icons/generate.py --svg out/    # also writes the SVG masters (for previews)

Check a change by rendering the SVGs (e.g. rsvg-convert) on dark and light backgrounds at 20-72 px,
and by round-tripping the XML: `vd2svg.py <xml> <svg>` reads only the drawable and must draw the same.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bfe_icons  # noqa: E402

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", default=os.path.join(REPO, "app", "src", "main", "res", "drawable"))
    ap.add_argument("--svg", help="also write <name>.svg masters into this folder")
    a = ap.parse_args()
    if a.svg:
        os.makedirs(a.svg, exist_ok=True)
    for name, shapes in bfe_icons.ICONS.items():
        with open(os.path.join(a.res, name + ".xml"), "w") as f:
            f.write(bfe_icons.to_vd(shapes, name))
        if a.svg:
            with open(os.path.join(a.svg, name + ".svg"), "w") as f:
                f.write(bfe_icons.to_svg(shapes))
    print(f"wrote {len(bfe_icons.ICONS)} drawables to {a.res}")


if __name__ == "__main__":
    main()
