#!/usr/bin/env python3
"""BFE launcher icons: SVG masters -> Android resources, with the squircle baked into the artwork.

Each icon is three 108x108 adaptive-icon layers in scripts/appicons/src/<src>/ (background.svg,
foreground.svg, monochrome.svg; paths + linear/radial gradients + clip paths + translucent fills/strokes,
no filters, letters as paths). For every icon in icons.json this writes:

  res/drawable/ic_appicon_<id>_art.xml   the adaptive FOREGROUND: background + foreground artwork,
                                         clipped to a squircle (superellipse, n = SQUIRCLE_N) that is
                                         SQUIRCLE_W wide on the 108 canvas, centred
  res/drawable/ic_appicon_<id>_mono.xml  the themed-icon (Android 13+) silhouette, the master's own size
  res/mipmap-anydpi-v26/ic_appicon_<id>.xml   adaptive-icon: transparent background + the two above

plus the Kotlin catalog (appicon/AppIconCatalog.kt) and the launcher activity-alias block in
AndroidManifest.xml (between the BEGIN/END app-icon markers).

Why baked: the owner's launcher has no icon-shape setting, so the squircle can't come from the launcher
mask. The background layer is transparent and the squircle sits inside the 72 dp viewport's circle, so
the icon reads as the same squircle under a circle, squircle, rounded-square or no mask at all.

How the clip is done: VectorDrawable <clip-path> is not anti-aliased on Android (jaggy edges), so this
never emits one. Every layer that crosses the squircle (or one of the master's own clip paths) is cut
geometrically with Skia PathOps and written as a plain filled path; strokes that cross are converted
to their outline first. Anything fully inside is written unchanged (strokes stay strokes). Transforms
stay as VectorDrawable groups, so gradients and non-uniform scales draw exactly as in the SVG.

Needs: python3 -m pip install skia-pathops fonttools        Run: python3 scripts/appicons/build.py
Then check the result: python3 scripts/appicons/check.py <out-dir>   (renders + contact sheets)
"""
import json
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

import pathops
from fontTools.pens.basePen import BasePen
from fontTools.svgLib.path import parse_path

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
MAIN = os.path.join(REPO, "app", "src", "main")
RES = os.path.join(MAIN, "res")
MANIFEST = os.path.join(MAIN, "AndroidManifest.xml")
CATALOG_KT = os.path.join(MAIN, "java", "com", "the412banner", "bfe", "appicon", "AppIconCatalog.kt")

PACKAGE = "com.the412banner.bfe"
CANVAS = 108.0
CENTER = 54.0
VIEWPORT = 72.0          # the part of a 108 adaptive layer a launcher mask can show
SQUIRCLE_N = 4.0         # superellipse exponent: 2 = circle, higher = squarer
SQUIRCLE_W = 60.0        # squircle width on the 108 canvas
# The 72-wide design (what a mask would show) is shrunk into the squircle, around the centre.
SCALE = SQUIRCLE_W / VIEWPORT
# Corner reach of the squircle from the centre: must stay inside the 72 viewport circle (radius 36)
# so a circular mask never nicks it.
assert SQUIRCLE_W / 2 * 2 ** (0.5 - 1 / SQUIRCLE_N) < VIEWPORT / 2

SVG = "{http://www.w3.org/2000/svg}"
EPS_AREA = 1e-3          # "fully inside" tolerance, in square local units

# ---------------------------------------------------------------------------------------------------
# Affine matrices, SVG order (a, b, c, d, e, f):  x' = a*x + c*y + e,  y' = b*x + d*y + f
# ---------------------------------------------------------------------------------------------------
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def mul(m, n):
    """m . n  (n is applied first)."""
    a, b, c, d, e, f = m
    A, B, C, D, E, F = n
    return (a * A + c * B, b * A + d * B, a * C + c * D, b * C + d * D, a * E + c * F + e, b * E + d * F + f)


def inverse(m):
    a, b, c, d, e, f = m
    det = a * d - b * c
    if abs(det) < 1e-12:
        raise ValueError("singular transform")
    ia, ib, ic, id_ = d / det, -b / det, -c / det, a / det
    return (ia, ib, ic, id_, -(ia * e + ic * f), -(ib * e + id_ * f))


def parse_transform(s):
    m = IDENTITY
    if not s:
        return m
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", s):
        v = [float(x) for x in re.split(r"[\s,]+", args.strip()) if x]
        if name == "translate":
            t = (1, 0, 0, 1, v[0], v[1] if len(v) > 1 else 0.0)
        elif name == "scale":
            t = (v[0], 0, 0, v[1] if len(v) > 1 else v[0], 0, 0)
        elif name == "rotate":
            r = math.radians(v[0])
            t = (math.cos(r), math.sin(r), -math.sin(r), math.cos(r), 0, 0)
            if len(v) == 3:
                t = mul((1, 0, 0, 1, v[1], v[2]), mul(t, (1, 0, 0, 1, -v[1], -v[2])))
        elif name == "matrix":
            t = tuple(v)
        else:
            raise ValueError("unsupported transform " + name)
        m = mul(m, t)
    return m


def is_identity(m):
    return all(abs(x - y) < 1e-9 for x, y in zip(m, IDENTITY))


def is_similarity(m):
    a, b, c, d, _, _ = m
    return abs(a - d) < 1e-9 and abs(b + c) < 1e-9


def decompose(m):
    """m = translate(e, f) . rotate(theta) . scale(sx, sy) — what a VectorDrawable <group> can say."""
    a, b, c, d, e, f = m
    if abs(a * c + b * d) > 1e-6:
        raise ValueError("skewed transform can't be a VectorDrawable group: %r" % (m,))
    sx = math.hypot(a, b)
    theta = math.atan2(b, a)
    sy = (a * d - b * c) / sx
    return e, f, math.degrees(theta), sx, sy


# ---------------------------------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------------------------------
def svg_path(d, evenodd=False):
    p = pathops.Path()
    parse_path(d, p.getPen())
    if evenodd:
        p.fillType = pathops.FillType.EVEN_ODD
    return p


def transformed(p, m):
    q = p.transform(*m)
    q.fillType = p.fillType
    return q


def squircle(cx, cy, half, n=SQUIRCLE_N, segments=32):
    """Superellipse |x/half|^n + |y/half|^n = 1 as cubic Beziers (Hermite fit; off the true curve by
    < 0.006 units at half = 36)."""
    def point(phi):
        c, s = math.cos(phi), math.sin(phi)
        r = half / (abs(c) ** n + abs(s) ** n) ** (1 / n)
        return r * c, r * s

    def tangent(x, y):
        # Perpendicular to the implicit gradient (n|x|^(n-1) sgn x, n|y|^(n-1) sgn y), counter-clockwise.
        gx = math.copysign(abs(x / half) ** (n - 1), x)
        gy = math.copysign(abs(y / half) ** (n - 1), y)
        ln = math.hypot(gx, gy)
        return -gy / ln, gx / ln

    p = pathops.Path()
    pts = [point(2 * math.pi * i / segments) for i in range(segments)]
    p.moveTo(cx + pts[0][0], cy + pts[0][1])
    for i in range(segments):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % segments]
        t0, t1 = tangent(x0, y0), tangent(x1, y1)
        # Handle length for a circular arc of the same turning angle (exact for a circle).
        chord = math.hypot(x1 - x0, y1 - y0)
        turn = math.acos(max(-1.0, min(1.0, t0[0] * t1[0] + t0[1] * t1[1])))
        h = chord / 3 if turn < 1e-6 else chord * (4 / 3) * math.tan(turn / 4) / (2 * math.sin(turn / 2))
        p.cubicTo(cx + x0 + t0[0] * h, cy + y0 + t0[1] * h, cx + x1 - t1[0] * h, cy + y1 - t1[1] * h, cx + x1, cy + y1)
    p.close()
    return p


def stroke_outline(p, width, cap, join):
    q = pathops.Path()
    q.addPath(p)
    caps = {"butt": pathops.LineCap.BUTT_CAP, "round": pathops.LineCap.ROUND_CAP, "square": pathops.LineCap.SQUARE_CAP}
    joins = {"miter": pathops.LineJoin.MITER_JOIN, "round": pathops.LineJoin.ROUND_JOIN, "bevel": pathops.LineJoin.BEVEL_JOIN}
    q.stroke(width, caps[cap], joins[join], 4.0)
    q.convertConicsToQuads()
    q.fillType = pathops.FillType.WINDING
    return q


def intersect(p, region):
    r = pathops.op(p, region, pathops.PathOp.INTERSECTION)
    r.convertConicsToQuads()
    return r


def inside(p, region):
    return pathops.op(p, region, pathops.PathOp.DIFFERENCE).area < EPS_AREA


def fmt(v):
    s = "%.3f" % v
    s = s.rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


class _DPen(BasePen):
    def __init__(self):
        super().__init__(None)
        self.out = []

    def _pt(self, p):
        return fmt(p[0]) + "," + fmt(p[1])

    def _moveTo(self, p):
        self.out.append("M" + self._pt(p))

    def _lineTo(self, p):
        self.out.append("L" + self._pt(p))

    def _curveToOne(self, p1, p2, p3):
        self.out.append("C" + " ".join(self._pt(q) for q in (p1, p2, p3)))

    def _qCurveToOne(self, p1, p2):
        self.out.append("Q" + " ".join(self._pt(q) for q in (p1, p2)))

    def _closePath(self):
        self.out.append("Z")

    def _endPath(self):
        pass


def path_data(p):
    pen = _DPen()
    p.draw(pen)
    return " ".join(pen.out)


# ---------------------------------------------------------------------------------------------------
# SVG master -> flat list of draw operations
# ---------------------------------------------------------------------------------------------------
class Paint:
    def __init__(self, color=None, gradient=None, alpha=1.0):
        self.color, self.gradient, self.alpha = color, gradient, alpha


class Leaf:
    """One SVG <path>: its geometry in its own (local) coordinates, the local->canvas matrix, the canvas
    clip it inherits (or None), and its fill / stroke."""

    def __init__(self, d, path, m, clip, fill, stroke, width, cap, join):
        self.d, self.path, self.m, self.clip = d, path, m, clip
        self.fill, self.stroke, self.width, self.cap, self.join = fill, stroke, width, cap, join


def color_hex(c):
    c = c.strip()
    if re.fullmatch(r"#[0-9a-fA-F]{3}", c):
        c = "#" + "".join(ch * 2 for ch in c[1:])
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", c):
        raise ValueError("unsupported colour " + c)
    return c.upper()


PATH_ATTRS = {"d", "fill", "fill-opacity", "fill-rule", "stroke", "stroke-width", "stroke-opacity",
              "stroke-linecap", "stroke-linejoin", "transform", "clip-path", "id"}
G_ATTRS = {"transform", "clip-path", "id"}


def load_svg(path):
    root = ET.parse(path).getroot()
    if root.get("viewBox", "0 0 108 108").split() != ["0", "0", "108", "108"]:
        raise ValueError(path + ": viewBox must be 0 0 108 108")
    grads, clips = {}, {}
    for el in root.iter():
        tag = el.tag.replace(SVG, "")
        if tag in ("linearGradient", "radialGradient"):
            if el.get("gradientUnits") != "userSpaceOnUse" or el.get("gradientTransform") or el.get("href") \
                    or el.get("{http://www.w3.org/1999/xlink}href") or el.get("spreadMethod") or el.get("fx") or el.get("fy"):
                raise ValueError(path + ": unsupported gradient form " + el.get("id"))
            stops = []
            for s in el:
                off = float(s.get("offset", "0").rstrip("%")) / (100 if s.get("offset", "").endswith("%") else 1)
                stops.append((off, color_hex(s.get("stop-color", "#000000")), float(s.get("stop-opacity", "1"))))
            g = {"type": "linear" if tag == "linearGradient" else "radial", "stops": stops}
            for k in ("x1", "y1", "x2", "y2", "cx", "cy", "r"):
                if el.get(k) is not None:
                    g[k] = float(el.get(k))
            grads[el.get("id")] = g
        elif tag == "clipPath":
            if el.get("clipPathUnits", "userSpaceOnUse") != "userSpaceOnUse":
                raise ValueError(path + ": clipPathUnits")
            union = None
            for c in el:
                if c.tag.replace(SVG, "") != "path" or c.get("clip-rule"):
                    raise ValueError(path + ": unsupported clipPath child")
                p = transformed(svg_path(c.get("d")), parse_transform(c.get("transform")))
                union = p if union is None else pathops.op(union, p, pathops.PathOp.UNION)
            clips[el.get("id")] = transformed(union, parse_transform(el.get("transform")))

    def paint(el, kind):
        v = el.get(kind)
        if v is None:
            v = "#000000" if kind == "fill" else "none"
        if v == "none":
            return None
        alpha = float(el.get(kind + "-opacity", "1"))
        m = re.fullmatch(r"url\(#([^)]+)\)", v)
        if m:
            return Paint(gradient=grads[m.group(1)], alpha=alpha)
        return Paint(color=color_hex(v), alpha=alpha)

    leaves = []

    def walk(el, m, clip):
        for c in el:
            tag = c.tag.replace(SVG, "")
            if tag in ("defs", "linearGradient", "radialGradient", "clipPath", "title", "desc"):
                continue
            if tag not in ("g", "path"):
                raise ValueError("%s: unsupported element <%s>" % (path, tag))
            extra = set(c.attrib) - (G_ATTRS if tag == "g" else PATH_ATTRS)
            if extra:
                raise ValueError("%s: unsupported attributes %s on <%s>" % (path, sorted(extra), tag))
            cm = mul(m, parse_transform(c.get("transform")))
            cc = clip
            if c.get("clip-path"):
                cid = re.fullmatch(r"url\(#([^)]+)\)", c.get("clip-path")).group(1)
                cp = transformed(clips[cid], cm)
                cc = cp if cc is None else pathops.op(cc, cp, pathops.PathOp.INTERSECTION)
            if tag == "g":
                walk(c, cm, cc)
                continue
            d = c.get("d")
            evenodd = c.get("fill-rule") == "evenodd"
            leaves.append(Leaf(
                d, svg_path(d, evenodd), cm, cc, paint(c, "fill"), paint(c, "stroke"),
                float(c.get("stroke-width", "1")), c.get("stroke-linecap", "butt"), c.get("stroke-linejoin", "miter"),
            ))

    walk(root, IDENTITY, None)
    return leaves


# ---------------------------------------------------------------------------------------------------
# Draw operations -> VectorDrawable XML
# ---------------------------------------------------------------------------------------------------
class Op:
    """One VectorDrawable <path> inside the group for matrix m."""

    def __init__(self, m, d, evenodd, fill=None, stroke=None, width=None, cap=None, join=None):
        self.m, self.d, self.evenodd = m, d, evenodd
        self.fill, self.stroke, self.width, self.cap, self.join = fill, stroke, width, cap, join


def ops_for(leaf, region):
    """Cut a leaf to `region` (canvas coordinates; None = no clip) and return its draw operations."""
    out = []
    local = None
    if region is not None:
        local = transformed(region, inverse(leaf.m))
    if leaf.fill is not None:
        if local is None or inside(leaf.path, local):
            out.append(Op(leaf.m, leaf.d, leaf.path.fillType == pathops.FillType.EVEN_ODD, fill=leaf.fill))
        else:
            cut = intersect(leaf.path, local)
            if cut.area >= EPS_AREA:
                out.append(Op(leaf.m, path_data(cut), cut.fillType == pathops.FillType.EVEN_ODD, fill=leaf.fill))
    if leaf.stroke is not None and leaf.width > 0:
        outline = stroke_outline(leaf.path, leaf.width, leaf.cap, leaf.join)
        if (local is None or inside(outline, local)) and is_similarity(leaf.m):
            out.append(Op(leaf.m, leaf.d, False, stroke=leaf.stroke, width=leaf.width, cap=leaf.cap, join=leaf.join))
        else:
            # Crosses the clip (or sits under a non-uniform scale): draw the stroke's outline as a fill.
            cut = outline if local is None else intersect(outline, local)
            if cut.area >= EPS_AREA:
                out.append(Op(leaf.m, path_data(cut), cut.fillType == pathops.FillType.EVEN_ODD, fill=leaf.stroke))
    return out


def argb(color, opacity):
    return "#%02X%s" % (round(max(0.0, min(1.0, opacity)) * 255), color[1:])


def gradient_xml(g, attr, ind, alpha=1.0):
    """A gradient fill/stroke. A layer's fill/stroke opacity is folded into the stops (exact: every stop's
    alpha is scaled alike), so a shader is never combined with fillAlpha/strokeAlpha."""
    a = ['android:type="%s"' % g["type"]]
    if g["type"] == "linear":
        a += ['android:startX="%s"' % fmt(g.get("x1", 0)), 'android:startY="%s"' % fmt(g.get("y1", 0)),
              'android:endX="%s"' % fmt(g.get("x2", 1)), 'android:endY="%s"' % fmt(g.get("y2", 0))]
    else:
        a += ['android:centerX="%s"' % fmt(g["cx"]), 'android:centerY="%s"' % fmt(g["cy"]),
              'android:gradientRadius="%s"' % fmt(g["r"])]
    lines = [ind + '<aapt:attr name="android:%s">' % attr, ind + "    <gradient " + " ".join(a) + ">"]
    for off, col, op in g["stops"]:
        lines.append(ind + '        <item android:offset="%s" android:color="%s" />' % (fmt(off), argb(col, op * alpha)))
    lines += [ind + "    </gradient>", ind + "</aapt:attr>"]
    return lines


def path_xml(op, ind):
    attrs = ['android:pathData="%s"' % op.d]
    children = []
    for kind, paint in (("fill", op.fill), ("stroke", op.stroke)):
        if paint is None:
            continue
        if paint.gradient is not None:
            children += gradient_xml(paint.gradient, kind + "Color", ind + "    ", paint.alpha)
        else:
            attrs.append('android:%sColor="%s"' % (kind, argb(paint.color, 1.0)))
            if paint.alpha != 1.0:
                attrs.append('android:%sAlpha="%s"' % (kind, fmt(paint.alpha)))
    if op.stroke is not None:
        attrs.append('android:strokeWidth="%s"' % fmt(op.width))
        if op.cap != "butt":
            attrs.append('android:strokeLineCap="%s"' % op.cap)
        if op.join != "miter":
            attrs.append('android:strokeLineJoin="%s"' % op.join)
    if op.evenodd:
        attrs.append('android:fillType="evenOdd"')
    head = ind + "<path\n" + "\n".join(ind + "    " + a for a in attrs)
    if not children:
        return [head + " />"]
    return [head + ">"] + children + [ind + "</path>"]


def group_open(m, ind):
    tx, ty, rot, sx, sy = decompose(m)
    a = []
    if abs(tx) > 1e-9 or abs(ty) > 1e-9:
        a += ['android:translateX="%s"' % fmt(tx), 'android:translateY="%s"' % fmt(ty)]
    if abs(rot) > 1e-9:
        a.append('android:rotation="%s"' % fmt(rot))
    if abs(sx - 1) > 1e-9 or abs(sy - 1) > 1e-9:
        a += ['android:scaleX="%s"' % ("%.6f" % sx).rstrip("0").rstrip("."), 'android:scaleY="%s"' % ("%.6f" % sy).rstrip("0").rstrip(".")]
    return ind + "<group " + " ".join(a) + ">"


def vector_xml(ops, comment, outer=None):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- %s -->" % comment,
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    xmlns:aapt="http://schemas.android.com/aapt"',
             '    android:width="108dp"', '    android:height="108dp"',
             '    android:viewportWidth="108"', '    android:viewportHeight="108">']
    ind = "    "
    if outer is not None:
        lines.append(ind + outer)
        ind += "    "
    i = 0
    while i < len(ops):
        m = ops[i].m
        j = i
        while j < len(ops) and all(abs(x - y) < 1e-9 for x, y in zip(ops[j].m, m)):
            j += 1
        if is_identity(m):
            for op in ops[i:j]:
                lines += path_xml(op, ind)
        else:
            lines.append(group_open(m, ind))
            for op in ops[i:j]:
                lines += path_xml(op, ind + "    ")
            lines.append(ind + "</group>")
        i = j
    if outer is not None:
        lines.append("    </group>")
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def build_icon(icon):
    src = os.path.join(HERE, "src", icon["src"])
    # Squircle in master coordinates: the 72-wide viewport squircle; the outer group then shrinks the
    # whole picture by SCALE around the centre, so it lands SQUIRCLE_W wide.
    region = squircle(CENTER, CENTER, VIEWPORT / 2)
    ops = []
    for layer in ("background", "foreground"):
        for leaf in load_svg(os.path.join(src, layer + ".svg")):
            clip = region if leaf.clip is None else pathops.op(region, leaf.clip, pathops.PathOp.INTERSECTION)
            ops += ops_for(leaf, clip)
    outer = ('<group android:name="squircle" android:pivotX="54" android:pivotY="54" '
             'android:scaleX="%s" android:scaleY="%s">' % (("%.6f" % SCALE,) * 2))
    note = ("BFE app icon \"%s\" (%s): background + foreground artwork baked into a %g-wide squircle "
            "(n=%g). Generated by scripts/appicons/build.py from scripts/appicons/src/%s; edit the SVG "
            "masters, then regenerate." % (icon["name"], icon["code"], SQUIRCLE_W, SQUIRCLE_N, icon["src"]))
    art = vector_xml(ops, note, outer)
    mono_ops = []
    for leaf in load_svg(os.path.join(src, "monochrome.svg")):
        mono_ops += ops_for(leaf, leaf.clip)
    note = ("BFE app icon \"%s\" (%s): themed-icon (Android 13+) silhouette; the launcher tints it. "
            "Generated by scripts/appicons/build.py from scripts/appicons/src/%s/monochrome.svg." % (icon["name"], icon["code"], icon["src"]))
    mono = vector_xml(mono_ops, note)
    return art, mono


def adaptive_xml(icon, mono_res):
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- BFE app icon \"%s\" (%s). The squircle is part of the foreground artwork and the background is"
        " transparent, so it looks the same under any launcher mask. Generated by scripts/appicons/build.py. -->" % (icon["name"], icon["code"]),
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">',
        '    <background android:drawable="@android:color/transparent" />',
        '    <foreground android:drawable="@drawable/ic_appicon_%s_art" />' % icon["id"],
        '    <monochrome android:drawable="@drawable/%s" />' % mono_res,
        "</adaptive-icon>",
    ]) + "\n"


def kt_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def catalog_kt(icons):
    lines = [
        "// Generated by scripts/appicons/build.py from scripts/appicons/icons.json. Do not edit by hand.",
        "package com.the412banner.bfe.appicon",
        "",
        "import com.the412banner.bfe.R",
        "",
        "/** Every launcher icon BFE ships, in picker order; see [AppIcons]. */",
        "internal val APP_ICON_CATALOG: List<AppIcon> = listOf(",
    ]
    for ic in icons:
        mono = "R.drawable." + (ic.get("mono_res") or "ic_appicon_%s_mono" % ic["id"])
        lines.append("    AppIcon(%s, %s, %s, AppIcon.Group.%s, \"%s.icon.%s\", R.drawable.ic_appicon_%s_art, %s, isDefault = %s)," % (
            kt_string(ic["id"]), kt_string(ic["name"]), kt_string(ic["code"]), ic["group"], PACKAGE, ic["alias"],
            ic["id"], mono, "true" if ic.get("default") else "false"))
    lines.append(")")
    return "\n".join(lines) + "\n"


BEGIN = "<!-- BEGIN app-icon aliases"
END = "<!-- END app-icon aliases -->"


def manifest_block(icons):
    ind = "        "
    out = [ind + BEGIN + ": generated by scripts/appicons/build.py from scripts/appicons/icons.json. -->",
           ind + "<!-- Settings → App icon. Each icon is its own launcher entry opening MainActivity; exactly one is",
           ind + "     enabled (AppIcons switches them; %s is the default). Never rename one: Android" % next(
               "icon." + i["alias"] for i in icons if i.get("default")),
           ind + "     remembers the chosen icon as that alias's enabled state, and it survives updates. -->"]
    for ic in icons:
        out += [
            ind + "<activity-alias",
            ind + '    android:name="%s.icon.%s"' % (PACKAGE, ic["alias"]),
            ind + '    android:targetActivity="%s.MainActivity"' % PACKAGE,
            ind + '    android:label="@string/app_name"',
            ind + '    android:icon="@mipmap/ic_appicon_%s"' % ic["id"],
            ind + '    android:roundIcon="@mipmap/ic_appicon_%s"' % ic["id"],
            ind + '    android:enabled="%s"' % ("true" if ic.get("default") else "false"),
            ind + '    android:exported="true">',
            ind + "    <intent-filter>",
            ind + '        <action android:name="android.intent.action.MAIN" />',
            ind + '        <category android:name="android.intent.category.LAUNCHER" />',
            ind + "    </intent-filter>",
            ind + "</activity-alias>",
        ]
    out.append(ind + END)
    return "\n".join(out)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    old = open(path).read() if os.path.exists(path) else None
    if old != text:
        with open(path, "w") as f:
            f.write(text)


def main():
    icons = json.load(open(os.path.join(HERE, "icons.json")))["icons"]
    if sum(1 for i in icons if i.get("default")) != 1:
        sys.exit("icons.json: exactly one icon must be the default")
    keep = set()
    for ic in icons:
        art, mono = build_icon(ic)
        name = "ic_appicon_%s" % ic["id"]
        write(os.path.join(RES, "drawable", name + "_art.xml"), art)
        keep.add(name + "_art.xml")
        if not ic.get("mono_res"):
            write(os.path.join(RES, "drawable", name + "_mono.xml"), mono)
            keep.add(name + "_mono.xml")
        write(os.path.join(RES, "mipmap-anydpi-v26", name + ".xml"), adaptive_xml(ic, ic.get("mono_res") or name + "_mono"))
        keep.add(name + ".xml")
        print("%-8s %-20s art %6d B  mono %5d B" % (ic["id"], ic["name"], len(art), len(mono)))
    # Drop outputs of icons no longer in the catalog.
    for sub in ("drawable", "mipmap-anydpi-v26"):
        for f in os.listdir(os.path.join(RES, sub)):
            if f.startswith("ic_appicon_") and f not in keep:
                os.remove(os.path.join(RES, sub, f))
    write(CATALOG_KT, catalog_kt(icons))
    text = open(MANIFEST).read()
    start, end = text.find(BEGIN), text.find(END)
    if start < 0 or end < 0:
        sys.exit("AndroidManifest.xml: app-icon alias markers not found")
    start = text.rfind("\n", 0, start) + 1
    write(MANIFEST, text[:start] + manifest_block(icons) + text[end + len(END):])


if __name__ == "__main__":
    main()
