"""BFE icon set: one source of truth for the SVG masters and the Android VectorDrawables (ic_bfe_*.xml).

Every icon is a list of P (path) shapes on a 48x48 grid. Fills are solid colours or
userSpaceOnUse linear/radial gradients; strokes are solid. Nothing here needs a filter,
mask or clip, so the same data maps 1:1 onto VectorDrawable.
"""
import math

# ---------------------------------------------------------------- model


class Lin:
    def __init__(self, x1, y1, x2, y2, stops):
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.stops = [s if len(s) == 3 else (s[0], s[1], 1.0) for s in stops]


class Rad:
    def __init__(self, cx, cy, r, stops):
        self.cx, self.cy, self.r = cx, cy, r
        self.stops = [s if len(s) == 3 else (s[0], s[1], 1.0) for s in stops]


class P:
    def __init__(self, d, fill=None, fa=1.0, stroke=None, sw=0.0, sa=1.0,
                 cap="round", join="round", evenodd=False, tx=0.0, ty=0.0):
        self.d, self.fill, self.fa = d, fill, fa
        self.stroke, self.sw, self.sa = stroke, sw, sa
        self.cap, self.join, self.evenodd = cap, join, evenodd
        self.tx, self.ty = tx, ty


def n(v):
    s = ("%.3f" % v).rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rr(x, y, w, h, r):
    """Rounded rectangle."""
    return (f"M{n(x + r)},{n(y)} H{n(x + w - r)} A{n(r)},{n(r)} 0 0 1 {n(x + w)},{n(y + r)} "
            f"V{n(y + h - r)} A{n(r)},{n(r)} 0 0 1 {n(x + w - r)},{n(y + h)} H{n(x + r)} "
            f"A{n(r)},{n(r)} 0 0 1 {n(x)},{n(y + h - r)} V{n(y + r)} A{n(r)},{n(r)} 0 0 1 {n(x + r)},{n(y)} Z")


def rect(x, y, w, h):
    return f"M{n(x)},{n(y)} H{n(x + w)} V{n(y + h)} H{n(x)} Z"


def circ(cx, cy, r):
    return (f"M{n(cx - r)},{n(cy)} A{n(r)},{n(r)} 0 1 1 {n(cx + r)},{n(cy)} "
            f"A{n(r)},{n(r)} 0 1 1 {n(cx - r)},{n(cy)} Z")


def poly(*pts, close=True):
    s = "M" + " L".join(f"{n(x)},{n(y)}" for x, y in pts)
    return s + (" Z" if close else "")


def sector(cx, cy, r0, r1, a0, a1):
    """Annular sector between radii r0<r1, angles a0->a1 in degrees (screen coords, clockwise)."""
    def pt(r, a):
        t = math.radians(a)
        return cx + r * math.cos(t), cy + r * math.sin(t)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    x0, y0 = pt(r1, a0)
    x1, y1 = pt(r1, a1)
    x2, y2 = pt(r0, a1)
    x3, y3 = pt(r0, a0)
    return (f"M{n(x0)},{n(y0)} A{n(r1)},{n(r1)} 0 {large} 1 {n(x1)},{n(y1)} L{n(x2)},{n(y2)} "
            f"A{n(r0)},{n(r0)} 0 {large} 0 {n(x3)},{n(y3)} Z")


def V(top, bottom, y0, y1, x=0):
    """Vertical two-stop gradient."""
    return Lin(x, y0, x, y1, [(0, top), (1, bottom)])


WHITE = "#FFFFFF"
INK = "#000000"

# ---------------------------------------------------------------- folder

F_BACK = ("#DE9324", "#BF760F")      # back panel + tab (darker)
F_FRONT = ("#FFCD60", "#F3A83A")     # front flap
F_HILITE = "#FFE7A8"                 # top highlight line / emboss light
F_EMBLEM = "#9C5C07"                 # emblem ink (pressed into the flap)
F_EDGE = "#8A5208"                   # darker outline

BACK_D = ("M4,10.5 A2.5,2.5 0 0 1 6.5,8 H16.6 C17.55,8 18.4,8.42 19.0,9.15 L20.9,11.5 "
          "H41.5 A2.5,2.5 0 0 1 44,14 V38 A2.5,2.5 0 0 1 41.5,40.5 H6.5 A2.5,2.5 0 0 1 4,38 Z")
FRONT_D = ("M4,19.6 A2.6,2.6 0 0 1 6.6,17 H41.4 A2.6,2.6 0 0 1 44,19.6 V38 A2.5,2.5 0 0 1 41.5,40.5 "
           "H6.5 A2.5,2.5 0 0 1 4,38 Z")
# Open: the flap falls forward — a parallelogram leaning right, top edge lower.
FRONT_OPEN_D = ("M9.55,21.5 H44.85 C45.9,21.5 46.55,22.45 46.25,23.45 L42.3,38.75 "
                "C42.0,39.8 41.1,40.5 40.0,40.5 H6.0 C4.75,40.5 3.95,39.45 4.3,38.3 L8.25,22.55 "
                "C8.45,21.95 8.95,21.5 9.55,21.5 Z")


def folder_base(open_=False):
    s = []
    # soft contact shadow (only really visible on light backgrounds)
    s.append(P(BACK_D, INK, fa=0.10, ty=0.9))
    s.append(P(BACK_D, V(*F_BACK, 8, 40.5)))
    # inner shade along the top of the back panel, where the papers sit
    s.append(P(rr(5.2, 12.6, 37.6, 2.2, 1.1), "#9C5E09", fa=0.22))
    # papers: a grey sheet behind, a white sheet in front
    if open_:
        s.append(P("M10.6,11.6 H40.2 A1,1 0 0 1 41.2,12.6 V30 H9.6 V12.6 A1,1 0 0 1 10.6,11.6 Z",
                   V("#E9EDF2", "#D5DBE3", 11.6, 30)))
        s.append(P("M8.2,13.4 H37.4 A1,1 0 0 1 38.4,14.4 V30 H7.2 V14.4 A1,1 0 0 1 8.2,13.4 Z",
                   V(WHITE, "#EEF1F5", 13.4, 30)))
    else:
        s.append(P("M11.6,11.9 H40.2 A1,1 0 0 1 41.2,12.9 V24 H10.6 V12.9 A1,1 0 0 1 11.6,11.9 Z",
                   V("#E9EDF2", "#D7DDE5", 11.9, 24)))
        s.append(P("M8.4,13.6 H37.4 A1,1 0 0 1 38.4,14.6 V24 H7.4 V14.6 A1,1 0 0 1 8.4,13.6 Z",
                   V(WHITE, "#EEF1F5", 13.6, 24)))
    if open_:
        # shadow the falling flap casts into the folder
        s.append(P("M8.9,19.4 H45.5 L45.0,21.5 H8.4 Z",
                   Lin(0, 19.4, 0, 21.5, [(0, "#6B3F00", 0.0), (1, "#6B3F00", 0.30)])))
        s.append(P(FRONT_OPEN_D, V(*F_FRONT, 21.5, 40.5)))
        s.append(P("M9.7,22.35 H44.8 L44.62,23.05 H9.52 Z", F_HILITE, fa=0.9))
        s.append(P(FRONT_OPEN_D, None, stroke=F_EDGE, sw=0.7, sa=0.35))
    else:
        s.append(P("M6,15.0 H42 V17 H6 Z",
                   Lin(0, 15.0, 0, 17, [(0, "#6B3F00", 0.0), (1, "#6B3F00", 0.26)])))
        s.append(P(FRONT_D, V(*F_FRONT, 17, 40.5)))
        s.append(P(rr(5.6, 17.75, 36.8, 0.95, 0.475), F_HILITE, fa=0.95))
        s.append(P(FRONT_D, None, stroke=F_EDGE, sw=0.7, sa=0.35))
    s.append(P(BACK_D, None, stroke=F_EDGE, sw=0.7, sa=0.30))
    return s


def emblem(parts, cy_shift=0.0):
    """parts: list of (d, mode, extra) where mode 'fill' | 'eo' | ('stroke', width).
    Draws a light emboss copy 0.7 lower, then the ink."""
    out = []
    for colour, alpha, dy in ((F_HILITE, 0.7, 0.7), (F_EMBLEM, 1.0, 0.0)):
        for d, mode in parts:
            if mode == "fill":
                out.append(P(d, colour, fa=alpha, ty=dy + cy_shift))
            elif mode == "eo":
                out.append(P(d, colour, fa=alpha, evenodd=True, ty=dy + cy_shift))
            else:
                out.append(P(d, None, stroke=colour, sw=mode[1], sa=alpha, ty=dy + cy_shift))
    return out


# Emblem glyphs, centred on the flap at (24, 29).
EMB = {
    "download": [
        ("M24,22.6 V31.0", ("stroke", 2.3)),
        ("M19.9,27.4 L24,31.5 L28.1,27.4", ("stroke", 2.3)),
        ("M18.6,35.0 H29.4", ("stroke", 2.3)),
    ],
    "pictures": [
        (rr(17.0, 23.0, 14, 11.6, 1.8) + " " + rr(18.8, 24.8, 10.4, 8.0, 0.6), "eo"),
        ("M19.4,32.0 L22.7,27.9 L25.0,30.6 L26.4,29.1 L28.6,32.0 Z", "fill"),
        (circ(26.6, 26.9, 1.15), "fill"),
    ],
    "music": [
        (circ(20.4, 33.3, 2.25) + " " + circ(28.4, 31.6, 2.25)
         + " M21.35,33.3 V24.9 L30.65,23.2 V31.6 H29.35 V26.4 L22.65,27.6 V33.3 Z", "fill"),
    ],
    "movies": [
        (circ(24, 29, 6.3) + " M22.1,25.6 L27.6,29 L22.1,32.4 Z", "eo"),
    ],
    "documents": [
        ("M18.3,24.2 H29.7", ("stroke", 1.9)),
        ("M18.3,27.6 H29.7", ("stroke", 1.9)),
        ("M18.3,31.0 H29.7", ("stroke", 1.9)),
        ("M18.3,34.4 H25.3", ("stroke", 1.9)),
    ],
    "android": [
        ("M15.9,34.6 A8.1,8.1 0 0 1 32.1,34.6 Z " + circ(20.6, 30.9, 1.1) + " " + circ(27.4, 30.9, 1.1), "eo"),
        ("M20.0,28.0 L18.25,25.0", ("stroke", 1.4)),
        ("M28.0,28.0 L29.75,25.0", ("stroke", 1.4)),
    ],
    "games": [
        ("M19.7,24.2 H28.3 C31.15,24.2 32.55,26.15 33.05,29.1 L33.65,32.75 C34.0,34.9 31.9,35.95 30.45,34.6 "
         "L28.1,32.4 H19.9 L17.55,34.6 C16.1,35.95 14.0,34.9 14.35,32.75 L14.95,29.1 "
         "C15.45,26.15 16.85,24.2 19.7,24.2 Z "
         "M18.85,26.4 H20.35 V27.75 H21.7 V29.25 H20.35 V30.6 H18.85 V29.25 H17.5 V27.75 H18.85 Z "
         + circ(28.0, 27.35, 0.95) + " " + circ(29.9, 29.25, 0.95), "eo"),
    ],
    "backups": [
        ("M24,22.6 L30.6,25.0 V29.2 C30.6,32.9 27.9,35.3 24,36.6 C20.1,35.3 17.4,32.9 17.4,29.2 V25.0 Z "
         "M21.0,29.1 L22.25,27.85 L23.35,28.95 L26.4,25.9 L27.65,27.15 L23.35,31.45 Z", "eo"),
    ],
}


def folder(kind=None, open_=False):
    s = folder_base(open_)
    if kind:
        s += emblem(EMB[kind])
    return s


# ---------------------------------------------------------------- file page

PAGE_D = ("M11,4 H29.5 L40,14.5 V41 A3,3 0 0 1 37,44 H11 A3,3 0 0 1 8,41 V7 A3,3 0 0 1 11,4 Z")
FOLD_D = "M29.5,4 L40,14.5 H32.5 A3,3 0 0 1 29.5,11.5 Z"
BAND_TOP = 25.5
BAND_D = (f"M8,{BAND_TOP} H40 V41 A3,3 0 0 1 37,44 H11 A3,3 0 0 1 8,41 Z")


def band_bottom(y_left, mid, y_right):
    """Helper for scenery that runs to the band's rounded bottom edge."""
    return f"M8,{n(y_left)} {mid} L40,{n(y_right)} V41 A3,3 0 0 1 37,44 H11 A3,3 0 0 1 8,41 Z"


def page(band=None, glyph=(), above=()):
    s = [P(PAGE_D, INK, fa=0.12, ty=0.9),
         P(PAGE_D, V(WHITE, "#ECEFF3", 4, 44))]
    s += list(above)
    if band is not None:
        top, bottom = band
        s.append(P(BAND_D, V(top, bottom, BAND_TOP, 44)))
        s.append(P(rect(8, BAND_TOP, 32, 0.8), WHITE, fa=0.28))
    s += list(glyph)
    # fold: a little shadow on the page, then the turned-down corner
    s.append(P(FOLD_D, "#3A4350", fa=0.13, tx=-0.7, ty=0.9))
    s.append(P(FOLD_D, Lin(30.5, 13.6, 35.4, 8.7, [(0, "#C3CAD4"), (1, "#E8ECF1")])))
    s.append(P(FOLD_D, None, stroke="#4A5462", sw=0.6, sa=0.22))
    s.append(P(PAGE_D, None, stroke="#2B3440", sw=0.8, sa=0.36))
    return s


def w(d, fa=1.0, evenodd=False):
    return P(d, WHITE, fa=fa, evenodd=evenodd)


def ws(d, sw, fa=1.0, cap="round"):
    return P(d, None, stroke=WHITE, sw=sw, sa=fa, cap=cap)


C = {
    "archive": ("#A57DF8", "#7A4AE6"),
    "apk": ("#55DE97", "#22B266"),
    "exe": ("#4CC2F6", "#1495DB"),
    "package": ("#38CDBD", "#0E9686"),
    "video": ("#FF6E6E", "#DE3232"),
    "audio": ("#F684C6", "#D23488"),
    "document": ("#5F90F7", "#2D63DE"),
    "spreadsheet": ("#3CC47E", "#178A4E"),
    "presentation": ("#FFA25C", "#EB651D"),
    "text": ("#9DABBD", "#66758A"),
    "code": ("#8C93F7", "#5156D6"),
    "payload": ("#6A7685", "#3E4753"),
}

GLYPH = {}

GLYPH["document"] = [
    ws("M16.5,29.9 H25.5", 2.3),
    ws("M16.5,33.6 H31.5", 1.6),
    ws("M16.5,36.8 H31.5", 1.6),
    ws("M16.5,40.0 H27.5", 1.6),
]
GLYPH["text"] = [
    ws("M16.5,29.8 H31.5", 1.6),
    ws("M16.5,33.0 H27.0", 1.6),
    ws("M16.5,36.2 H30.0", 1.6),
    ws("M16.5,39.4 H23.5", 1.6),
]
GLYPH["code"] = [
    ws("M20.0,30.4 L16.2,34.8 L20.0,39.2", 2.0),
    ws("M28.0,30.4 L31.8,34.8 L28.0,39.2", 2.0),
    ws("M25.5,29.6 L22.5,40.0", 1.9),
]
GLYPH["video"] = [
    P("M21.4,29.7 L30.3,34.8 L21.4,39.9 Z", WHITE, stroke=WHITE, sw=1.6),
]
GLYPH["audio"] = [
    w(circ(20.3, 38.7, 2.35) + " " + circ(28.7, 37.0, 2.35)
      + " M21.35,38.7 V30.3 L31.05,28.6 V37.0 H29.75 V31.8 L22.65,33.05 V38.7 Z"),
]
GLYPH["spreadsheet"] = [
    w(rr(16, 29, 16, 11.6, 1.6) + " "
      + " ".join(rr(x, y, 3.85, 2.85, 0.35)
                 for x in (17.25, 22.075, 26.9) for y in (32.55, 36.55)), evenodd=True),
]
GLYPH["presentation"] = [
    w(rr(15.6, 28.6, 16.8, 10.6, 1.4) + " "
      + rect(18.6, 33.8, 2.3, 3.4) + " " + rect(22.85, 31.4, 2.3, 5.8) + " " + rect(27.1, 32.6, 2.3, 4.6),
      evenodd=True),
    ws("M24,39.4 V41.4", 1.4, cap="butt"),
    ws("M21.2,41.6 H26.8", 1.4),
]
GLYPH["exe"] = [
    w(rr(16.2, 29.0, 15.6, 11.8, 1.8) + " " + rr(17.75, 32.55, 12.5, 6.7, 0.6) + " "
      + circ(19.15, 30.8, 0.55) + " " + circ(20.85, 30.8, 0.55) + " " + circ(22.55, 30.8, 0.55),
      evenodd=True),
]
GLYPH["package"] = [
    w("M24,28.4 L30.6,31.9 L24,35.4 L17.4,31.9 Z"),
    w("M17.4,31.9 L24,35.4 V42.2 L17.4,38.7 Z", fa=0.80),
    w("M24,35.4 L30.6,31.9 V38.7 L24,42.2 Z", fa=0.58),
    P("M20.7,30.15 L27.3,33.65", None, stroke="#0E9686", sw=0.9, sa=0.55, cap="butt"),
]
GLYPH["apk"] = [
    w("M16.6,39.0 A7.4,7.4 0 0 1 31.4,39.0 Z " + circ(20.95, 35.7, 1.0) + " " + circ(27.05, 35.7, 1.0),
      evenodd=True),
    ws("M20.7,33.4 L19.05,30.55", 1.3),
    ws("M27.3,33.4 L28.95,30.55", 1.3),
]
GLYPH["payload"] = [
    w(rr(19.0, 30.0, 10.0, 10.0, 1.4) + " " + rr(21.4, 32.4, 5.2, 5.2, 0.6), evenodd=True),
    w(" ".join(rect(x, 27.7, 1.2, 2.3) + " " + rect(x, 40.0, 1.2, 2.3) for x in (20.9, 23.4, 25.9))
      + " " + " ".join(rect(16.7, y, 2.3, 1.2) + " " + rect(29.0, y, 2.3, 1.2) for y in (31.9, 34.4, 36.9))),
]


def zipper_teeth(colour):
    out = []
    y = 4.7
    i = 0
    while y < 24.6:
        x = 21.5 if i % 2 == 0 else 24.0
        out.append(rr(x, y, 2.5, 1.55, 0.4))
        y += 1.75
        i += 1
    return P(" ".join(out), colour)


ARCHIVE_ABOVE = [zipper_teeth("#7A4AE6")]
GLYPH["archive"] = [
    w("M20.7,26.3 H27.3 L26.5,30.4 H21.5 Z"),
    w(rr(21.6, 29.8, 4.8, 9.6, 1.7) + " " + rr(23.0, 33.8, 2.0, 3.9, 0.85), evenodd=True),
]


def image_page():
    sky = ("#A9DBFF", "#6FB6F2")
    glyph = [
        P(circ(32.0, 30.4, 2.5), "#FFD23C"),
        P(band_bottom(38.8, "C11.6,34.6 15.6,32.4 19.6,32.4 C23.6,32.4 26.4,35.5 29.0,37.0", 37.0),
          V("#3DA351", "#2E8A41", 32.4, 44)),
        P(band_bottom(42.2, "C14.5,38.2 22.0,36.6 29.8,36.6 C34.0,36.6 37.4,37.4 40,38.4", 38.4),
          V("#6BD47E", "#3FB45A", 36.6, 44)),
    ]
    return page(band=sky, glyph=glyph)


def disc():
    cx, cy = 24, 24
    s = [P(circ(cx, cy, 20), INK, fa=0.14, ty=0.9),
         P(circ(cx, cy, 20) + " " + circ(cx, cy, 3.1),
           Lin(9, 9, 39, 39, [(0, "#F7F9FB"), (0.5, "#D4DAE1"), (1, "#A7B0BB")]), evenodd=True)]
    # iridescent sheen: two faint colour wedges + two bright wedges, opposite each other
    def sheen(centre, half, colour, alpha):
        # three nested wedges so the band fades out at its edges instead of ending hard
        for k in (1.0, 0.66, 0.33):
            s.append(P(sector(cx, cy, 8.2, 19.2, centre - half * k, centre + half * k), colour, fa=alpha / 3))
    sheen(212, 26, "#8FD3FF", 0.75)
    sheen(32, 26, "#FFB5DD", 0.70)
    sheen(250, 20, WHITE, 1.0)
    sheen(70, 20, WHITE, 0.9)
    # data edge + label-free hub
    s.append(P(circ(cx, cy, 19.25), None, stroke="#FFFFFF", sw=0.6, sa=0.55))
    s.append(P(circ(cx, cy, 8.2) + " " + circ(cx, cy, 3.1), "#C5CCD5", fa=0.65, evenodd=True))
    s.append(P(circ(cx, cy, 6.6) + " " + circ(cx, cy, 3.1),
               Lin(18, 18, 30, 30, [(0, "#F4F6F9"), (1, "#C9D0D9")]), evenodd=True))
    s.append(P(circ(cx, cy, 6.6), None, stroke="#7D8794", sw=0.55, sa=0.55))
    s.append(P(circ(cx, cy, 3.1), None, stroke="#56606D", sw=0.7, sa=0.75))
    s.append(P(circ(cx, cy, 20), None, stroke="#4F5966", sw=0.8, sa=0.6))
    return s


ICONS = {}
ICONS["ic_bfe_folder"] = folder()
ICONS["ic_bfe_folder_open"] = folder(open_=True)
for k in ("download", "pictures", "music", "movies", "documents", "android", "games", "backups"):
    ICONS[f"ic_bfe_folder_{k}"] = folder(k)
ICONS["ic_bfe_file"] = page()
ICONS["ic_bfe_file_archive"] = page(C["archive"], GLYPH["archive"], above=ARCHIVE_ABOVE)
ICONS["ic_bfe_file_disc"] = disc()
for k in ("apk", "exe", "package"):
    ICONS[f"ic_bfe_file_{k}"] = page(C[k], GLYPH[k])
ICONS["ic_bfe_file_image"] = image_page()
for k in ("video", "audio", "document", "spreadsheet", "presentation", "text", "code", "payload"):
    ICONS[f"ic_bfe_file_{k}"] = page(C[k], GLYPH[k])


# ---------------------------------------------------------------- writers

def _hex_alpha(colour, alpha):
    a = max(0, min(255, round(alpha * 255)))
    return "#%02X%s" % (a, colour[1:].upper())


def to_svg(shapes, size=48, bg=None, idp="g"):
    defs, body = [], []
    if bg:
        body.append(f'<rect width="48" height="48" fill="{bg}"/>')
    for i, s in enumerate(shapes):
        a = [f'd="{s.d}"']
        if isinstance(s.fill, (Lin, Rad)):
            gid = f"{idp}{i}"
            stops = "".join(
                f'<stop offset="{n(o)}" stop-color="{c}"' + (f' stop-opacity="{n(al * s.fa)}"' if al * s.fa < 1 else "") + "/>"
                for o, c, al in s.fill.stops)
            if isinstance(s.fill, Lin):
                g = s.fill
                defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{n(g.x1)}" y1="{n(g.y1)}" '
                            f'x2="{n(g.x2)}" y2="{n(g.y2)}">{stops}</linearGradient>')
            else:
                g = s.fill
                defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{n(g.cx)}" cy="{n(g.cy)}" '
                            f'r="{n(g.r)}">{stops}</radialGradient>')
            a.append(f'fill="url(#{gid})"')
        elif s.fill:
            a.append(f'fill="{s.fill}"')
            if s.fa < 1:
                a.append(f'fill-opacity="{n(s.fa)}"')
        else:
            a.append('fill="none"')
        if s.evenodd:
            a.append('fill-rule="evenodd"')
        if s.stroke:
            a.append(f'stroke="{s.stroke}" stroke-width="{n(s.sw)}" stroke-linecap="{s.cap}" stroke-linejoin="{s.join}"')
            if s.sa < 1:
                a.append(f'stroke-opacity="{n(s.sa)}"')
        el = "<path " + " ".join(a) + "/>"
        if s.tx or s.ty:
            el = f'<g transform="translate({n(s.tx)},{n(s.ty)})">{el}</g>'
        body.append(el)
    d = f"<defs>{''.join(defs)}</defs>" if defs else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 48 48">'
            f"{d}{''.join(body)}</svg>")


def to_vd(shapes, name):
    L = ['<?xml version="1.0" encoding="utf-8"?>',
         f"<!-- BFE's own artwork ({name}), 48x48 grid. Generated by scripts/icons/generate.py",
         "     from the masters in scripts/icons/bfe_icons.py: edit those, then regenerate. -->",
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         '    xmlns:aapt="http://schemas.android.com/aapt"',
         '    android:width="48dp"',
         '    android:height="48dp"',
         '    android:viewportWidth="48"',
         '    android:viewportHeight="48">']
    for s in shapes:
        ind = "    "
        if s.tx or s.ty:
            L.append(f'{ind}<group android:translateX="{n(s.tx)}" android:translateY="{n(s.ty)}">')
            ind += "    "
        attrs = [f'android:pathData="{s.d}"']
        grad = None
        if isinstance(s.fill, (Lin, Rad)):
            grad = s.fill
        elif s.fill:
            attrs.append(f'android:fillColor="{_hex_alpha(s.fill, 1.0)}"')
            if s.fa < 1:
                attrs.append(f'android:fillAlpha="{n(s.fa)}"')
        if s.evenodd:
            attrs.append('android:fillType="evenOdd"')
        if s.stroke:
            attrs.append(f'android:strokeColor="{_hex_alpha(s.stroke, 1.0)}"')
            attrs.append(f'android:strokeWidth="{n(s.sw)}"')
            attrs.append(f'android:strokeLineCap="{s.cap}"')
            attrs.append(f'android:strokeLineJoin="{s.join}"')
            if s.sa < 1:
                attrs.append(f'android:strokeAlpha="{n(s.sa)}"')
        sep = "\n" + ind + "    "
        if grad is None:
            L.append(f"{ind}<path" + sep + sep.join(attrs) + " />")
        else:
            L.append(f"{ind}<path" + sep + sep.join(attrs) + ">")
            L.append(f'{ind}    <aapt:attr name="android:fillColor">')
            if isinstance(grad, Lin):
                L.append(f'{ind}        <gradient android:type="linear" android:startX="{n(grad.x1)}" android:startY="{n(grad.y1)}" '
                         f'android:endX="{n(grad.x2)}" android:endY="{n(grad.y2)}">')
            else:
                L.append(f'{ind}        <gradient android:type="radial" android:centerX="{n(grad.cx)}" android:centerY="{n(grad.cy)}" '
                         f'android:gradientRadius="{n(grad.r)}">')
            for o, c, al in grad.stops:
                # fillAlpha is folded into the stop colours (never combined with a shader)
                L.append(f'{ind}            <item android:offset="{n(o)}" android:color="{_hex_alpha(c, al * s.fa)}" />')
            L.append(f"{ind}        </gradient>")
            L.append(f"{ind}    </aapt:attr>")
            L.append(f"{ind}</path>")
        if s.tx or s.ty:
            L.append("    </group>")
    L.append("</vector>")
    return "\n".join(L) + "\n"
