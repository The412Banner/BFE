#!/usr/bin/env python3
"""Independent VectorDrawable -> SVG converter, used by check.py to prove the generated XML draws the same
as the SVG masters. Reads only the XML (never build.py's data). Covers what VectorDrawable supports:
groups (translate / scale / rotation / pivot), clip-path, solid and gradient (linear / radial / sweep is
refused) fills and strokes with their alphas, fill type, line caps / joins / miter limit.

Usage: vd2svg.py <in.xml> <out.svg>
"""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def col(v):
    """VectorDrawable colour (#RGB, #ARGB, #RRGGBB, #AARRGGBB) -> (#RRGGBB, alpha)."""
    v = v.strip()
    h = v[1:]
    if len(h) in (3, 4):
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        return "#" + h, 1.0
    if len(h) == 8:
        return "#" + h[2:], int(h[:2], 16) / 255.0
    raise ValueError(v)


def num(el, name, default):
    return float(el.get(A + name, default))


def conv(src, dst):
    root = ET.parse(src).getroot()
    assert root.tag == "vector", root.tag
    vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    defs, body = [], []
    gid = [0]

    def new_id(prefix):
        gid[0] += 1
        return "%s%d" % (prefix, gid[0])

    def gradient(el, attr):
        for sub in el:
            if sub.tag == "{http://schemas.android.com/aapt}attr" or sub.tag == "aapt:attr" or sub.tag.endswith("attr"):
                if sub.get("name") == "android:" + attr:
                    return sub.find("gradient")
        return None

    def paint(el, kind):
        """SVG attributes for VectorDrawable fill/stroke `kind`."""
        alpha = num(el, kind + "Alpha", "1")
        g = gradient(el, kind + "Color")
        if g is not None:
            i = new_id("g")
            items = g.findall("item")
            stops = []
            if items:
                for it in items:
                    c, a = col(it.get(A + "color"))
                    stops.append((float(it.get(A + "offset")), c, a))
            else:
                cols = [g.get(A + "startColor"), g.get(A + "centerColor"), g.get(A + "endColor")]
                offs = [0.0, 0.5, 1.0]
                for o, cv in zip(offs, cols):
                    if cv is not None:
                        c, a = col(cv)
                        stops.append((o, c, a))
            st = "".join('<stop offset="%g" stop-color="%s" stop-opacity="%.5f"/>' % s for s in stops)
            t = g.get(A + "type", "linear")
            if t == "linear":
                defs.append('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>' % (
                    i, g.get(A + "startX", "0"), g.get(A + "startY", "0"), g.get(A + "endX", "0"), g.get(A + "endY", "0"), st))
            elif t == "radial":
                defs.append('<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s</radialGradient>' % (
                    i, g.get(A + "centerX", "0"), g.get(A + "centerY", "0"), g.get(A + "gradientRadius"), st))
            else:
                raise ValueError("gradient type " + t)
            return '%s="url(#%s)" %s-opacity="%.5f"' % (kind, i, kind, alpha)
        v = el.get(A + kind + "Color")
        if not v:
            return '%s="none"' % kind
        c, a = col(v)
        return '%s="%s" %s-opacity="%.5f"' % (kind, c, kind, a * alpha)

    def walk(el, out):
        # A clip-path clips every later sibling in its group.
        pending_clip = None
        for c in el:
            if c.tag == "clip-path":
                i = new_id("c")
                defs.append('<clipPath id="%s"><path d="%s"/></clipPath>' % (i, c.get(A + "pathData")))
                inner = []
                pending_clip = (i, inner)
                out.append(('<g clip-path="url(#%s)">' % i, inner))
                out = inner
                continue
            if c.tag == "group":
                px, py = num(c, "pivotX", "0"), num(c, "pivotY", "0")
                tx, ty = num(c, "translateX", "0"), num(c, "translateY", "0")
                sx, sy = num(c, "scaleX", "1"), num(c, "scaleY", "1")
                r = num(c, "rotation", "0")
                inner = []
                walk(c, inner)
                out.append(('<g transform="translate(%r,%r) rotate(%r) scale(%r,%r) translate(%r,%r)">' % (
                    tx + px, ty + py, r, sx, sy, -px, -py), inner))
            elif c.tag == "path":
                a = ['d="%s"' % c.get(A + "pathData"), paint(c, "fill")]
                if c.get(A + "fillType") == "evenOdd":
                    a.append('fill-rule="evenodd"')
                s = paint(c, "stroke")
                if s != 'stroke="none"':
                    a.append(s)
                    a.append('stroke-width="%s"' % c.get(A + "strokeWidth", "0"))
                    a.append('stroke-linecap="%s"' % c.get(A + "strokeLineCap", "butt"))
                    a.append('stroke-linejoin="%s"' % c.get(A + "strokeLineJoin", "miter"))
                    a.append('stroke-miterlimit="%s"' % c.get(A + "strokeMiterLimit", "4"))
                out.append("<path " + " ".join(a) + "/>")
            else:
                raise ValueError("unexpected element " + c.tag)

    def render(items):
        s = ""
        for it in items:
            if isinstance(it, tuple):
                s += it[0] + render(it[1]) + "</g>"
            else:
                s += it
        return s

    walk(root, body)
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%s" height="%s" viewBox="0 0 %s %s"><defs>%s</defs>%s</svg>'
           % (vw, vh, vw, vh, "".join(defs), render(body)))
    with open(dst, "w") as f:
        f.write(svg)


if __name__ == "__main__":
    conv(sys.argv[1], sys.argv[2])
