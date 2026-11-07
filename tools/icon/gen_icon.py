#!/usr/bin/env python3
"""Icona di Stafir: bussola che punta a est, mini-scene fantasy a spirale, sfondo spazio profondo.

Genera i vector drawable Android (sfondo, primo piano, monocromatica) e un'anteprima HTML/SVG
con la stessa identica geometria. Uso: python3 gen_icon.py <cartella_uscita>
"""
import math, os, random, sys

C = 54.0

def f(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s

def P(d, fill=None, stroke=None, sw=0.0, alpha=1.0):
    return {"t": "p", "d": d, "fill": fill, "stroke": stroke, "sw": sw, "a": alpha}

def G(children, rot=None, tr=None, clip=None):
    return {"t": "g", "rot": rot, "tr": tr, "clip": clip, "ch": children}

def circle(cx, cy, r):
    return f"M{f(cx-r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0"

def ellipse(cx, cy, rx, ry):
    return f"M{f(cx-rx)},{f(cy)} a{f(rx)},{f(ry)} 0 1,0 {f(2*rx)},0 a{f(rx)},{f(ry)} 0 1,0 {f(-2*rx)},0"

def half_ellipse_front(cx, cy, rx, ry):  # metà inferiore (davanti)
    return f"M{f(cx-rx)},{f(cy)} a{f(rx)},{f(ry)} 0 0,0 {f(2*rx)},0"

def rrect(l, t, w, h, rx):
    r_, b = l + w, t + h
    return (f"M{f(l+rx)},{f(t)} L{f(r_-rx)},{f(t)} Q{f(r_)},{f(t)} {f(r_)},{f(t+rx)} L{f(r_)},{f(b-rx)} Q{f(r_)},{f(b)} {f(r_-rx)},{f(b)} "
            f"L{f(l+rx)},{f(b)} Q{f(l)},{f(b)} {f(l)},{f(b-rx)} L{f(l)},{f(t+rx)} Q{f(l)},{f(t)} {f(l+rx)},{f(t)} Z")

def star4(cx, cy, R, r):
    return (f"M{f(cx)},{f(cy-R)} Q{f(cx+r)},{f(cy-r)} {f(cx+R)},{f(cy)} Q{f(cx+r)},{f(cy+r)} {f(cx)},{f(cy+R)} "
            f"Q{f(cx-r)},{f(cy+r)} {f(cx-R)},{f(cy)} Q{f(cx-r)},{f(cy-r)} {f(cx)},{f(cy-R)} Z")

def dots(pts, color, r=0.18):
    return [P(circle(x, y, r), color) for x, y in pts]

# ---------------- mini-scene (coordinate locali 0..10 x 0..8) ----------------
W_, H_ = 10.0, 8.0

def bg(c): return P(f"M0,0 h10 v8 h-10 z", c)

SCENES = {
 "galassia": lambda: [bg("#1E1050"),
    P("M5,4 C6.6,2.5 8.8,3.2 8.4,4.9", None, "#F0ABFC", 0.6),
    P("M5,4 C3.4,5.5 1.2,4.8 1.6,3.1", None, "#A78BFA", 0.6),
    P("M5,4 C4.1,2.3 6.2,1.2 7.6,1.9", None, "#C4B5FD", 0.4, 0.8),
    P("M5,4 C5.9,5.7 3.8,6.8 2.4,6.1", None, "#F5D0FE", 0.4, 0.8),
    P(circle(5, 4, 0.95), "#FEF3C7")] + dots([(1.2, 1.2), (8.9, 1.1), (8.6, 6.9), (1.5, 6.8), (6.6, 6.5)], "#FFFFFF"),
 "buco_nero": lambda: [bg("#05060F"),
    P(ellipse(5, 4.1, 4.0, 1.25), None, "#FB923C", 0.75),
    P(circle(5, 4.1, 1.75), "#000000"),
    P(circle(5, 4.1, 1.95), None, "#FDE68A", 0.28),
    P(half_ellipse_front(5, 4.1, 4.0, 1.25), None, "#F97316", 0.85)] + dots([(1.3, 1.4), (8.8, 1.8), (2.2, 6.8), (8.2, 6.6)], "#FFFFFF"),
 "pianeta": lambda: [bg("#0B1A3A"),
    P(ellipse(5, 4.2, 3.9, 1.1), None, "#FDE68A", 0.4),
    P(circle(5, 4.2, 2.1), "#F59E0B"),
    P("M3.05,3.6 Q5,3.15 6.95,3.6 L6.98,4.25 Q5,3.8 3.02,4.25 Z", "#FCD34D"),
    P(half_ellipse_front(5, 4.2, 3.9, 1.1), None, "#FEF3C7", 0.45)] + dots([(1.2, 1.3), (8.7, 6.8), (8.9, 1.5), (1.6, 6.9)], "#FFFFFF"),
 "aurora": lambda: [bg("#03263A"),
    P("M0,3.4 C2,1.6 4,4.2 6,2.4 C7.4,1.2 8.8,2.6 10,2.0", None, "#34D399", 0.9, 0.9),
    P("M0,4.4 C2.2,3 4.2,5 6.2,3.6 C7.8,2.6 9,3.6 10,3.3", None, "#A78BFA", 0.6, 0.8),
    P("M0,8 L3,4.6 L4.6,6.1 L6.9,3.9 L10,8 Z", "#0F172A"),
    P("M6.9,3.9 L7.6,4.75 L6.95,4.5 L6.3,4.65 Z", "#E0F2FE"),
    P("M3,4.6 L3.55,5.25 L2.95,5.05 L2.45,5.2 Z", "#E0F2FE")] + dots([(1, 0.9), (4.6, 0.8), (9.1, 0.8)], "#FFFFFF"),
 "razzo": lambda: [bg("#111827"),
    P("M5,0.9 Q6.4,2.3 6.2,5.2 L3.8,5.2 Q3.6,2.3 5,0.9 Z", "#F8FAFC"),
    P(circle(5, 3.0, 0.6), "#38BDF8"),
    P("M3.8,4.2 L2.9,5.8 L3.8,5.3 Z", "#EF4444"),
    P("M6.2,4.2 L7.1,5.8 L6.2,5.3 Z", "#EF4444"),
    P("M4.2,5.3 Q5,7.6 5.8,5.3 Z", "#FB923C"),
    P("M4.6,5.3 Q5,6.6 5.4,5.3 Z", "#FDE047")] + dots([(1.3, 1.5), (8.6, 2.2), (1.8, 6.4), (8.2, 6.9), (2.5, 3.6)], "#FFFFFF"),
 "oceano": lambda: [bg("#7DD3FC"),
    P(circle(7.6, 2.0, 1.05), "#FDE047"),
    P("M0,4.4 h10 v3.6 h-10 z", "#0369A1"),
    P("M0,4.4 Q1.25,3.9 2.5,4.4 T5,4.4 T7.5,4.4 T10,4.4 L10,5 L0,5 Z", "#0EA5E9"),
    P("M3.2,6.9 Q3.6,5.6 3.4,4.9 Q2.6,4.6 2.2,4.2 Q3.1,4.3 3.6,4.7 Q4.1,4.3 5,4.2 Q4.4,4.6 3.8,4.9 Q3.7,5.6 4.1,6.9 Z", "#0C4A6E"),
    P("M5.6,6.3 Q6.6,5.9 7.6,6.3", None, "#E0F2FE", 0.3),
    P("M0.8,5.8 Q1.7,5.4 2.6,5.8", None, "#E0F2FE", 0.3)],
 "vulcano": lambda: [bg("#4C1D95"),
    P("M0,6 h10 v2 h-10 z", "#1C1917"),
    P("M1,8 L4,3.3 L6,3.3 L9,8 Z", "#292524"),
    P("M4,3.3 L6,3.3 L5.7,4.1 L5.3,3.7 L4.9,4.5 L4.5,3.8 L4.3,4.1 Z", "#F97316"),
    P("M4.9,4.4 Q4.6,5.6 5.1,6.8", None, "#FB923C", 0.35),
    P(circle(5.3, 2.4, 0.75), "#78716C", alpha=0.9),
    P(circle(6.2, 1.6, 0.6), "#A8A29E", alpha=0.8),
    P(circle(7.0, 0.95, 0.45), "#D6D3D1", alpha=0.7),
    P(circle(3.6, 2.6, 0.18), "#FDBA74"), P(circle(6.4, 2.8, 0.15), "#FDBA74")],
 "isola": lambda: [bg("#38BDF8"),
    P("M2.2,4.4 L7.8,4.4 L5.4,7.4 Q5,7.8 4.6,7.4 Z", "#92400E"),
    P("M2,4.4 Q5,3.6 8,4.4 L7.8,4.9 L2.2,4.9 Z", "#22C55E"),
    P("M6.6,4.6 L6.6,6.8", None, "#E0F2FE", 0.35),
    P("M4.1,4.2 L4.2,2.4", None, "#78350F", 0.35),
    P(circle(4.2, 2.0, 0.85), "#15803D"),
    P(ellipse(1.6, 1.6, 1.0, 0.4), "#FFFFFF", alpha=0.9),
    P(ellipse(8.4, 2.4, 0.9, 0.35), "#FFFFFF", alpha=0.9)],
 "castello": lambda: [bg("#F9A8D4"),
    P(circle(8.2, 1.6, 0.8), "#FEF9C3"),
    P("M3.2,6 L3.2,3.6 L4.4,3.6 L4.4,2.4 L5.6,2.4 L5.6,3.6 L6.8,3.6 L6.8,6 Z", "#6D28D9"),
    P("M2.4,6 L2.4,3.4 L3.4,3.4 L3.4,6 Z M6.6,6 L6.6,3.4 L7.6,3.4 L7.6,6 Z", "#7C3AED"),
    P("M2.2,3.5 L2.9,2.3 L3.6,3.5 Z M6.4,3.5 L7.1,2.3 L7.8,3.5 Z M4.2,2.5 L5,1.0 L5.8,2.5 Z", "#4C1D95"),
    P("M4.6,6 L4.6,5 Q5,4.6 5.4,5 L5.4,6 Z", "#FDE68A"),
    P(ellipse(2.4, 6.6, 2.4, 0.9), "#FFFFFF"),
    P(ellipse(5.4, 6.9, 2.6, 0.9), "#FFFFFF"),
    P(ellipse(8.3, 6.5, 2.0, 0.8), "#FFFFFF")],
 "dinosauri": lambda: [bg("#FDBA74"),
    P("M0,0 h10 v3 h-10 z", "#FB923C", alpha=0.6),
    P(circle(2.4, 3.2, 1.3), "#FEF08A"),
    P("M0,6.5 h10 v1.5 h-10 z", "#166534"),
    P("M2.6,6.6 Q3.1,5.7 3.6,5.3 Q4.6,4.6 6.2,4.7 Q6.9,4.6 7.1,4.0 L7.6,1.9 Q7.8,1.4 8.4,1.5 L8.9,1.75 L8.4,2.0 L8.0,2.1 L7.7,4.5 Q7.5,5.3 6.9,5.6 L7.0,6.6 L6.5,6.6 L6.4,5.7 L4.6,5.7 L4.5,6.6 L4.0,6.6 L4.0,5.6 Q3.4,5.9 2.6,6.6 Z", "#1E293B"),
    P("M0.6,6.6 Q0.9,6.0 1.4,5.9 L1.9,5.9 L2.2,5.4 L2.5,5.5 L2.3,6.0 Q2.4,6.4 2.1,6.6 L1.9,6.6 L1.8,6.3 L1.2,6.3 L1.1,6.6 Z", "#334155")],
}

ORDER = ["galassia", "buco_nero", "pianeta", "aurora", "razzo", "oceano", "vulcano", "isola", "castello", "dinosauri"]

N = len(ORDER)
TURNS = 1.22
R0, R1 = 17.8, 27.4
S0, S1 = 4.4, 10.2
TH0 = -math.pi * 0.62

def card_layout():
    out = []
    for i, name in enumerate(ORDER):
        t = i / (N - 1)
        th = TH0 + t * TURNS * 2 * math.pi
        r = R0 + t * (R1 - R0)
        w = S0 + t * (S1 - S0)
        out.append(dict(name=name, x=C + r * math.cos(th), y=C + r * math.sin(th), w=w, h=w * 0.8, rot=14 * math.sin(i * 1.9)))
    return out

def spiral_path():
    pts = []
    for k in range(91):
        t = -0.1 + k / 90 * 1.08
        th = TH0 + t * TURNS * 2 * math.pi
        r = R0 + t * (R1 - R0)
        pts.append((C + r * math.cos(th), C + r * math.sin(th)))
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)

def card(c, mono=None):
    k = c["w"] / W_
    x0, y0 = c["x"] - c["w"] / 2, c["y"] - c["h"] / 2
    frame = rrect(-0.45, -0.45, W_ + 0.9, H_ + 0.9, 1.5)
    clip = rrect(0, 0, W_, H_, 1.1)
    if mono:
        inner = [P(frame, mono)]
    else:
        inner = [P(frame, "#FFFFFF"), G(SCENES[c["name"]](), clip=clip)]
    return G([G(inner, tr=(x0, y0, k))], rot=(c["rot"], c["x"], c["y"]))

# ---------------- bussola (punta a EST) ----------------
def point(angle_deg, L, w, light, dark):
    a = math.radians(angle_deg)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    tx, ty = C + L * ux, C + L * uy
    s1 = (C + w * vx, C + w * vy)
    s2 = (C - w * vx, C - w * vy)
    return [P(f"M{f(C)},{f(C)} L{f(tx)},{f(ty)} L{f(s1[0])},{f(s1[1])} Z", light),
            P(f"M{f(C)},{f(C)} L{f(tx)},{f(ty)} L{f(s2[0])},{f(s2[1])} Z", dark)]

def compass(mono=None):
    m = lambda c: mono or c
    sh = []
    if not mono:
        sh.append(P(circle(C, C, 16), ("rad", C, C, 16, "#66FCD34D", "#00FCD34D")))
        sh.append(P(circle(C, C, 12.2), ("rad", C - 3, C - 3, 15, "#FF26336B", "#FF0B1026"), "#FFFCD34D", 1.3))
        sh.append(P(circle(C, C, 9.9), None, "#FDE68A", 0.3, 0.45))
    else:
        sh.append(P(circle(C, C, 12.2), None, mono, 1.3))
    for i in range(8):
        a = math.radians(i * 45)
        rr = 0.55 if i % 2 == 0 else 0.38
        sh.append(P(circle(C + 11.0 * math.cos(a), C + 11.0 * math.sin(a), rr), m("#FCD34D")))
    for ang in (45, 135, 225, 315):  # punte diagonali
        sh += point(ang, 6.6, 1.45, m("#C7D2FE"), m("#818CF8"))
    for ang in (-90, 90):  # nord e sud
        sh += point(ang, 8.6, 2.0, m("#FDE68A"), m("#D97706"))
    sh += point(180, 8.6, 2.0, m("#F1F5F9"), m("#94A3B8"))   # coda dell'ago (ovest)
    if not mono:
        sh.append(P(circle(C + 10.4, C, 2.2), ("rad", C + 10.4, C, 2.2, "#AAFB7185", "#00FB7185")))
    sh += point(0, 10.6, 2.3, m("#FB7185"), m("#BE123C"))    # ago: punta verso EST
    sh.append(P(circle(C, C, 1.55), m("#FCD34D")))
    if not mono:
        sh.append(P(circle(C, C, 0.6), "#0B1026"))
    return sh

SPARKLES = [(80.5, 31.0, 2.4, "#FDE68A"), (30.0, 76.0, 1.9, "#FFFFFF"), (33.5, 33.0, 1.5, "#BAE6FD")]

def foreground(mono=None):
    sh = [P(spiral_path(), None, mono or "#FFFFFF", 0.6, 0.28)]
    for x, y, r, c in SPARKLES:
        sh.append(P(star4(x, y, r, r * 0.22), mono or c, alpha=0.95))
    for c in card_layout():
        sh.append(card(c, mono))
    sh += compass(mono)
    return sh

def background():
    rnd = random.Random(7)
    sh = [P("M0,0 h108 v108 h-108 z", ("rad", 54, 50, 62, "#FF151A3D", "#FF03040B")),
          P(circle(36, 76, 30), ("rad", 36, 76, 30, "#887C3AED", "#007C3AED")),
          P(circle(80, 34, 26), ("rad", 80, 34, 26, "#660EA5E9", "#000EA5E9")),
          P(circle(76, 84, 20), ("rad", 76, 84, 20, "#55DB2777", "#00DB2777"))]
    for _ in range(46):
        x, y = rnd.uniform(14, 94), rnd.uniform(14, 94)
        sh.append(P(circle(x, y, rnd.choice([0.22, 0.28, 0.35, 0.45])), "#FFFFFF", alpha=round(rnd.uniform(0.35, 0.95), 2)))
    return sh

# ---------------- SVG (anteprima) ----------------
_gid = [0]
def svg_el(s, defs):
    if s["t"] == "g":
        tf = []
        if s["rot"]: tf.append(f"rotate({f(s['rot'][0])} {f(s['rot'][1])} {f(s['rot'][2])})")
        if s["tr"]: tf.append(f"translate({f(s['tr'][0])} {f(s['tr'][1])}) scale({s['tr'][2]:.5f})")
        cp = ""
        if s["clip"]:
            _gid[0] += 1
            defs.append(f'<clipPath id="c{_gid[0]}"><path d="{s["clip"]}"/></clipPath>')
            cp = f' clip-path="url(#c{_gid[0]})"'
        inner = "".join(svg_el(c, defs) for c in s["ch"])
        if cp:
            inner = f"<g{cp}>{inner}</g>"
        return f'<g transform="{" ".join(tf)}">{inner}</g>' if tf else inner
    fill = s["fill"]
    if isinstance(fill, tuple):
        _gid[0] += 1
        _, cx, cy, r, c1, c2 = fill
        def stop(c, o):
            return f'<stop offset="{o}" stop-color="#{c[3:]}" stop-opacity="{int(c[1:3], 16) / 255:.3f}"/>'
        defs.append(f'<radialGradient id="r{_gid[0]}" gradientUnits="userSpaceOnUse" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}">{stop(c1, 0)}{stop(c2, 1)}</radialGradient>')
        fill = f"url(#r{_gid[0]})"
    a = f'd="{s["d"]}" fill="{fill or "none"}"'
    if s["stroke"]:
        a += f' stroke="{s["stroke"]}" stroke-width="{s["sw"]}" stroke-linecap="round" stroke-linejoin="round"'
    if s["a"] != 1:
        a += f' opacity="{s["a"]}"'
    return f"<path {a}/>"

def svg(size, mono=None, monobg=None):
    defs, body = [], []
    if monobg:
        body.append(f'<rect width="108" height="108" fill="{monobg}"/>')
    else:
        body += [svg_el(s, defs) for s in background()]
    body += [svg_el(s, defs) for s in foreground(mono)]
    return f'<svg width="{size}" height="{size}" viewBox="18 18 72 72" xmlns="http://www.w3.org/2000/svg"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>'

# ---------------- Vector drawable Android ----------------
def argb(c):
    if len(c) == 9: return c.upper()
    return "#FF" + c.lstrip("#").upper()

def vd_el(s, ind="    "):
    if s["t"] == "g":
        attrs = []
        if s["rot"]:
            attrs += [f'android:rotation="{f(s["rot"][0])}"', f'android:pivotX="{f(s["rot"][1])}"', f'android:pivotY="{f(s["rot"][2])}"']
        if s["tr"]:
            attrs += [f'android:translateX="{f(s["tr"][0])}"', f'android:translateY="{f(s["tr"][1])}"',
                      f'android:scaleX="{s["tr"][2]:.5f}"', f'android:scaleY="{s["tr"][2]:.5f}"']
        out = [f"{ind}<group {' '.join(attrs)}>" if attrs else f"{ind}<group>"]
        if s["clip"]:
            out.append(f'{ind}    <clip-path android:pathData="{s["clip"]}" />')
        out += [vd_el(c, ind + "    ") for c in s["ch"]]
        out.append(f"{ind}</group>")
        return "\n".join(out)
    attrs = [f'android:pathData="{s["d"]}"']
    grad = None
    if isinstance(s["fill"], tuple):
        _, cx, cy, r, c1, c2 = s["fill"]
        grad = (f'{ind}    <aapt:attr name="android:fillColor">\n'
                f'{ind}        <gradient android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" '
                f'android:gradientRadius="{f(r)}" android:startColor="{c1.upper()}" android:endColor="{c2.upper()}" />\n'
                f'{ind}    </aapt:attr>')
    elif s["fill"]:
        attrs.append(f'android:fillColor="{argb(s["fill"])}"')
        if s["a"] != 1: attrs.append(f'android:fillAlpha="{s["a"]}"')
    if s["stroke"]:
        attrs += [f'android:strokeColor="{argb(s["stroke"])}"', f'android:strokeWidth="{s["sw"]}"',
                  'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        if s["a"] != 1: attrs.append(f'android:strokeAlpha="{s["a"]}"')
    if grad:
        return f"{ind}<path {' '.join(attrs)}>\n{grad}\n{ind}</path>"
    return f"{ind}<path {' '.join(attrs)} />"

def vector(shapes, comment):
    head = ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} — generato da tools/icon/gen_icon.py, non modificare a mano -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n')
    return head + "\n".join(vd_el(s) for s in shapes) + "\n</vector>\n"

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "ic_launcher_background.xml"), "w").write(vector(background(), "Sfondo: spazio profondo con nebulose e stelle"))
    open(os.path.join(out, "ic_launcher_foreground.xml"), "w").write(vector(foreground(), "Bussola che punta a est con mini-scene fantasy a spirale"))
    open(os.path.join(out, "ic_launcher_monochrome.xml"), "w").write(vector(foreground("#FFFFFF"), "Versione monocromatica per le icone a tema"))
    big = svg(330)
    open(os.path.join(out, "preview.html"), "w").write(f"""<!doctype html><html><head><meta charset=utf-8><style>
body{{margin:0;font-family:Roboto,'Noto Sans',sans-serif;background:#0b1020;color:#fff;padding:24px}}
h1{{font-size:28px;margin:0 0 4px;text-align:center}} p{{opacity:.75;text-align:center;margin:0 0 20px}}
svg{{display:block}} .row{{display:flex;gap:30px;justify-content:center;align-items:flex-end}}
.item{{display:flex;flex-direction:column;align-items:center;gap:10px;font-size:15px}}
.sq{{border-radius:28%;overflow:hidden;box-shadow:0 10px 40px rgba(124,58,237,.35)}} .ci{{border-radius:50%;overflow:hidden}}
</style></head><body><h1>Bussola dei ricordi — versione spazio</h1><p>La bussola punta a est · mini mondi fantasy a spirale</p><div class=row>
<div class=item><div class=sq>{big}</div>HyperOS (grande)</div>
<div class=item><div class=ci>{svg(160)}</div>Rotonda</div>
<div class=item><div class=sq>{svg(72)}</div>Piccola</div>
<div class=item><div class=ci>{svg(160, mono='#C7D2FE', monobg='#1E1B4B')}</div>Icone a tema</div>
</div></body></html>""")
