#!/usr/bin/env python3
"""Visual + numeric check of the generated launcher icons against their SVG masters.

For every icon in icons.json it renders (rsvg-convert):
  reference  the masters (background + foreground) shrunk by the same factor and clipped to a densely
             sampled superellipse with SVG's own anti-aliased clip — written here, not taken from build.py
  generated  res/drawable/ic_appicon_<id>_art.xml, turned back into SVG by vd2svg.py
and the same for the monochrome layer, then diffs each pair and writes contact sheets:
  <out>/sheet-art.png     per icon: master as a square mask shows it | reference | generated | diff x8 |
                          generated under a circle mask | under a rounded-square mask | mono master |
                          mono generated | themed look
  <out>/home-dark.png, <out>/home-light.png   every generated icon on a launcher-like grid
Exit status 1 if any pair differs by more than the tolerance away from the squircle's anti-aliased edge.

Needs: rsvg-convert on PATH, python3 -m pip install pillow numpy
Usage: check.py <out-dir>
"""
import json
import math
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
RES = os.path.join(REPO, "app", "src", "main", "res")
sys.path.insert(0, HERE)
import vd2svg  # noqa: E402

SVGNS = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVGNS)

# The squircle spec, restated independently of build.py.
N, W = 4.0, 60.0
SCALE = W / 72.0
PX = 216  # render size for the 108 canvas (2 px per unit)


def squircle_d(cx, cy, half, steps=1440):
    pts = []
    for i in range(steps):
        phi = 2 * math.pi * i / steps
        c, s = math.cos(phi), math.sin(phi)
        r = half / (abs(c) ** N + abs(s) ** N) ** (1 / N)
        pts.append("%.4f,%.4f" % (cx + r * c, cy + r * s))
    return "M" + " L".join(pts) + " Z"


def layer_body(path, prefix):
    root = ET.parse(path).getroot()
    text = "".join(ET.tostring(c, encoding="unicode") for c in root)
    text = re.sub(r'id="([^"]+)"', lambda m: 'id="%s%s"' % (prefix, m.group(1)), text)
    text = re.sub(r"url\(#([^)]+)\)", lambda m: "url(#%s%s)" % (prefix, m.group(1)), text)
    # ET writes children with an explicit default namespace; strip it so they nest in our <svg>.
    return text.replace(' xmlns="%s"' % SVGNS, "")


def svg_doc(body, defs=""):
    return '<svg xmlns="%s" width="108" height="108" viewBox="0 0 108 108"><defs>%s</defs>%s</svg>' % (SVGNS, defs, body)


def render(svg_text, png, px=PX):
    src = png[:-4] + ".svg"
    with open(src, "w") as f:
        f.write(svg_text)
    subprocess.run(["rsvg-convert", "-w", str(px), "-h", str(px), src, "-o", png], check=True)
    return Image.open(png).convert("RGBA")


def over(img, bg):
    base = Image.new("RGBA", img.size, bg)
    base.alpha_composite(img)
    return base


def diff_stats(a, b, edge_mask):
    """Over grey, ignoring the squircle's anti-aliased rim: the share of pixels off by more than 24 (of
    255), the mean difference, and the worst pixel with the rim included."""
    x = np.asarray(over(a, (128, 128, 128, 255)), dtype=np.int16)[..., :3]
    y = np.asarray(over(b, (128, 128, 128, 255)), dtype=np.int16)[..., :3]
    d = np.abs(x - y).max(axis=2)
    core = d[~edge_mask]
    return float((core > 24).mean()), float(core.mean()), int(d.max()), d


def rim_mask(px, half_units, center_units=54.0, width_px=2.0):
    """Pixels within width_px of the output squircle's edge."""
    yy, xx = np.mgrid[0:px, 0:px] + 0.5
    u = px / 108.0
    x = (xx / u - center_units) / half_units
    y = (yy / u - center_units) / half_units
    f = (np.abs(x) ** N + np.abs(y) ** N) ** (1 / N)  # 1 on the edge
    return np.abs(f - 1) * half_units * u < width_px


def mask_shape(img, shape):
    """What a launcher mask shows: the central 72 of the 108 canvas through `shape`."""
    px = img.size[0]
    lo, hi = px * 18 / 108, px * 90 / 108
    m = Image.new("L", img.size, 0)
    dr = ImageDraw.Draw(m)
    if shape == "circle":
        dr.ellipse([lo, lo, hi, hi], fill=255)
    else:
        dr.rounded_rectangle([lo, lo, hi, hi], radius=(hi - lo) * 0.18, fill=255)
    out = img.copy()
    out.putalpha(Image.fromarray(np.minimum(np.asarray(img.getchannel("A")), np.asarray(m))))
    return out.crop((int(lo), int(lo), int(hi), int(hi)))


def tint(img, rgb):
    a = img.getchannel("A")
    out = Image.new("RGBA", img.size, rgb + (255,))
    out.putalpha(a)
    return out


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "build", "appicons-check")
    os.makedirs(out, exist_ok=True)
    icons = json.load(open(os.path.join(HERE, "icons.json")))["icons"]
    clip = '<clipPath id="__sq"><path d="%s"/></clipPath>' % squircle_d(54, 54, W / 2)
    g = "matrix(%r 0 0 %r %r %r)" % (SCALE, SCALE, 54 * (1 - SCALE), 54 * (1 - SCALE))
    rim = rim_mask(PX, W / 2)
    font = ImageFont.load_default()
    rows, gen_icons, failed = [], [], []
    for ic in icons:
        src = os.path.join(HERE, "src", ic["src"])
        d = os.path.join(out, ic["id"])
        os.makedirs(d, exist_ok=True)
        body = layer_body(os.path.join(src, "background.svg"), "bg_") + layer_body(os.path.join(src, "foreground.svg"), "fg_")
        master = render(svg_doc(body), os.path.join(d, "master.png"))
        ref = render(svg_doc('<g clip-path="url(#__sq)"><g transform="%s">%s</g></g>' % (g, body), clip), os.path.join(d, "reference.png"))
        vd2svg.conv(os.path.join(RES, "drawable", "ic_appicon_%s_art.xml" % ic["id"]), os.path.join(d, "generated.svg"))
        gen = render(open(os.path.join(d, "generated.svg")).read(), os.path.join(d, "generated.png"))
        mono_res = ic.get("mono_res") or "ic_appicon_%s_mono" % ic["id"]
        mref = render(svg_doc(layer_body(os.path.join(src, "monochrome.svg"), "m_")), os.path.join(d, "mono-master.png"))
        vd2svg.conv(os.path.join(RES, "drawable", mono_res + ".xml"), os.path.join(d, "mono-generated.svg"))
        mgen = render(open(os.path.join(d, "mono-generated.svg")).read(), os.path.join(d, "mono-generated.png"))
        # Clip-path corners anti-alias a little differently in rsvg than the cut geometry does, so a
        # handful of pixels may differ; anything real shows up as a share of pixels or in the mean.
        bad, mean, rim_max, dmap = diff_stats(ref, gen, rim)
        m_bad, m_mean, _, _ = diff_stats(mref, mgen, np.zeros_like(rim))
        ok = bad <= 5e-4 and mean <= 0.5 and m_bad <= 5e-4 and m_mean <= 0.5
        if not ok:
            failed.append(ic["id"])
        print("%-8s %-20s art: off>24 %.4f%% mean %.3f (max incl. rim %3d)   mono: off>24 %.4f%% mean %.3f   %s" % (
            ic["id"], ic["name"], bad * 100, mean, rim_max, m_bad * 100, m_mean, "ok" if ok else "DIFFERS"))
        dimg = Image.fromarray(np.clip(dmap * 8, 0, 255).astype(np.uint8)).convert("RGBA")
        themed = Image.new("RGBA", mgen.size, (0, 0, 0, 0))
        circ = Image.new("RGBA", mgen.size, (0, 0, 0, 0))
        ImageDraw.Draw(circ).ellipse([PX * 18 / 108, PX * 18 / 108, PX * 90 / 108, PX * 90 / 108], fill=(214, 227, 255, 255))
        themed.alpha_composite(circ)
        themed.alpha_composite(tint(mgen, (16, 46, 98)))
        crop = lambda im: im.crop((PX * 18 // 108, PX * 18 // 108, PX * 90 // 108, PX * 90 // 108))
        cells = [crop(over(master, (40, 40, 40, 255))), crop(over(ref, (40, 40, 40, 255))), crop(over(gen, (40, 40, 40, 255))),
                 crop(dimg), over(mask_shape(gen, "circle"), (40, 40, 40, 255)), over(mask_shape(gen, "rrect"), (40, 40, 40, 255)),
                 crop(over(mref, (230, 230, 230, 255))), crop(over(mgen, (230, 230, 230, 255))), crop(over(themed, (40, 40, 40, 255)))]
        rows.append((ic, cells, ok))
        gen_icons.append((ic, gen))
    # Contact sheet.
    cw = PX * 72 // 108
    heads = ["master (square)", "reference", "generated", "diff x8", "circle mask", "rounded mask", "mono master", "mono gen", "themed"]
    lw = 150
    sheet = Image.new("RGB", (lw + len(heads) * (cw + 8) + 8, 24 + len(rows) * (cw + 8) + 8), (24, 24, 28))
    dr = ImageDraw.Draw(sheet)
    for i, h in enumerate(heads):
        dr.text((lw + 8 + i * (cw + 8), 6), h, fill=(220, 220, 220), font=font)
    for r, (ic, cells, ok) in enumerate(rows):
        y = 24 + r * (cw + 8)
        dr.text((8, y + cw // 2 - 12), ic["name"] if ic["code"] == ic["name"] else "%s %s" % (ic["code"], ic["name"]), fill=(230, 230, 230), font=font)
        dr.text((8, y + cw // 2 + 2), "ok" if ok else "DIFFERS", fill=(120, 220, 120) if ok else (255, 90, 90), font=font)
        for i, c in enumerate(cells):
            sheet.paste(c.convert("RGB"), (lw + 8 + i * (cw + 8), y))
    sheet.save(os.path.join(out, "sheet-art.png"))
    # Launcher-like home screens: the full 108 canvas at 132 px (squircle ~73 px), label below.
    for name, bg, ink in (("home-dark", (18, 22, 34), (235, 235, 240)), ("home-light", (222, 228, 236), (30, 30, 36))):
        cols, cell = 6, 150
        rows_n = math.ceil(len(gen_icons) / cols)
        home = Image.new("RGB", (cols * cell, rows_n * cell + 10), bg)
        hd = ImageDraw.Draw(home)
        for k, (ic, gen) in enumerate(gen_icons):
            x, y = (k % cols) * cell, (k // cols) * cell
            im = gen.resize((132, 132), Image.LANCZOS)
            home.paste(im, (x + 9, y - 8), im)
            label = ic["name"] if len(ic["name"]) <= 22 else ic["name"][:21] + "…"
            tw = hd.textlength(label, font=font)
            hd.text((x + (cell - tw) / 2, y + 112), label, fill=ink, font=font)
        home.save(os.path.join(out, name + ".png"))
    print("sheets: %s/sheet-art.png, home-dark.png, home-light.png" % out)
    if failed:
        print("DIFFERS: " + ", ".join(failed))
        sys.exit(1)


if __name__ == "__main__":
    main()
