"""Independent VectorDrawable -> SVG converter, used to prove the shipped XML draws the same as the
SVG masters. Reads only the XML (never icons.py). Usage: vd2svg.py <in.xml> <out.svg>"""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def col(v):
    v = v.strip()
    if len(v) == 9:
        return "#" + v[3:], int(v[1:3], 16) / 255.0
    if len(v) == 7:
        return v, 1.0
    raise ValueError(v)


def conv(src, dst):
    root = ET.parse(src).getroot()
    assert root.tag == "vector", root.tag
    vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    defs, body = [], []
    gid = [0]

    def walk(el, out):
        for c in el:
            if c.tag == "group":
                tx, ty = c.get(A + "translateX", "0"), c.get(A + "translateY", "0")
                inner = []
                walk(c, inner)
                out.append(f'<g transform="translate({tx},{ty})">' + "".join(inner) + "</g>")
            elif c.tag == "path":
                a = [f'd="{c.get(A + "pathData")}"']
                fill = c.get(A + "fillColor")
                grad = None
                for sub in c:
                    if sub.get("name") == "android:fillColor":
                        grad = sub.find("gradient")
                if grad is not None:
                    gid[0] += 1
                    i = f"v{gid[0]}"
                    stops = ""
                    for it in grad.findall("item"):
                        cc, al = col(it.get(A + "color"))
                        stops += f'<stop offset="{it.get(A + "offset")}" stop-color="{cc}" stop-opacity="{al:.4f}"/>'
                    t = grad.get(A + "type")
                    if t == "linear":
                        defs.append(f'<linearGradient id="{i}" gradientUnits="userSpaceOnUse" x1="{grad.get(A + "startX")}" '
                                    f'y1="{grad.get(A + "startY")}" x2="{grad.get(A + "endX")}" y2="{grad.get(A + "endY")}">{stops}</linearGradient>')
                    elif t == "radial":
                        defs.append(f'<radialGradient id="{i}" gradientUnits="userSpaceOnUse" cx="{grad.get(A + "centerX")}" '
                                    f'cy="{grad.get(A + "centerY")}" r="{grad.get(A + "gradientRadius")}">{stops}</radialGradient>')
                    else:
                        raise ValueError(t)
                    a.append(f'fill="url(#{i})"')
                elif fill:
                    cc, al = col(fill)
                    al *= float(c.get(A + "fillAlpha", "1"))
                    a.append(f'fill="{cc}" fill-opacity="{al:.4f}"')
                else:
                    a.append('fill="none"')
                if c.get(A + "fillType") == "evenOdd":
                    a.append('fill-rule="evenodd"')
                st = c.get(A + "strokeColor")
                if st:
                    cc, al = col(st)
                    al *= float(c.get(A + "strokeAlpha", "1"))
                    a.append(f'stroke="{cc}" stroke-opacity="{al:.4f}" stroke-width="{c.get(A + "strokeWidth", "0")}" '
                             f'stroke-linecap="{c.get(A + "strokeLineCap", "butt")}" '
                             f'stroke-linejoin="{c.get(A + "strokeLineJoin", "miter")}"')
                out.append("<path " + " ".join(a) + "/>")
            else:
                raise ValueError("unexpected element " + c.tag)

    walk(root, body)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{vw}" height="{vh}" viewBox="0 0 {vw} {vh}">'
           f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>')
    with open(dst, "w") as f:
        f.write(svg)


if __name__ == "__main__":
    conv(sys.argv[1], sys.argv[2])
