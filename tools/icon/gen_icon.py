import math, sys, os
C = 54.0
COLORS = ["#F472B6", "#FB923C", "#FACC15", "#4ADE80", "#2DD4BF", "#38BDF8", "#818CF8", "#C084FC", "#F87171", "#A3E635", "#22D3EE", "#E879F9"]
N = 11
TURNS = 1.3
R0, R1 = 15.0, 29.0
S0, S1 = 2.6, 8.4
TH0 = -math.pi / 2 + 0.3

def f(v): return f"{v:.2f}".rstrip('0').rstrip('.')

def cards():
    out = []
    for i in range(N):
        t = i / (N - 1)
        th = TH0 + t * TURNS * 2 * math.pi
        r = R0 + t * (R1 - R0)
        s = S0 + t * (S1 - S0)
        out.append(dict(x=C + r * math.cos(th), y=C + r * math.sin(th), w=s, h=s * 0.8, rot=16 * math.sin(i * 1.9), color=COLORS[i % len(COLORS)], big=s > 4.6))
    return out

def spiral_path():
    pts = []
    steps = 80
    for k in range(steps + 1):
        t = -0.08 + k / steps * 1.08
        th = TH0 + t * TURNS * 2 * math.pi
        r = R0 + t * (R1 - R0)
        pts.append((C + r * math.cos(th), C + r * math.sin(th)))
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)

def rect_path(x, y, w, h, rx):
    # rettangolo arrotondato centrato in (x,y), senza rotazione (la rotazione è nel gruppo)
    l, t, r_, b = x - w / 2, y - h / 2, x + w / 2, y + h / 2
    return (f"M{f(l+rx)},{f(t)} L{f(r_-rx)},{f(t)} Q{f(r_)},{f(t)} {f(r_)},{f(t+rx)} L{f(r_)},{f(b-rx)} Q{f(r_)},{f(b)} {f(r_-rx)},{f(b)} "
            f"L{f(l+rx)},{f(b)} Q{f(l)},{f(b)} {f(l)},{f(b-rx)} L{f(l)},{f(t+rx)} Q{f(l)},{f(t)} {f(l+rx)},{f(t)} Z")

def mountain_path(x, y, w, h):
    l, b = x - w / 2, y + h / 2
    return (f"M{f(l + w*0.12)},{f(b - h*0.14)} L{f(l + w*0.38)},{f(b - h*0.55)} L{f(l + w*0.55)},{f(b - h*0.33)} "
            f"L{f(l + w*0.68)},{f(b - h*0.45)} L{f(l + w*0.88)},{f(b - h*0.14)} Z")

def circle(cx, cy, r):
    return f"M{f(cx-r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2*r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2*r)},0"

def star(cx, cy, R, r):
    return (f"M{f(cx)},{f(cy-R)} Q{f(cx+r)},{f(cy-r)} {f(cx+R)},{f(cy)} Q{f(cx+r)},{f(cy+r)} {f(cx)},{f(cy+R)} "
            f"Q{f(cx-r)},{f(cy+r)} {f(cx-R)},{f(cy)} Q{f(cx-r)},{f(cy-r)} {f(cx)},{f(cy-R)} Z")

SPARKLES = [(79, 30, 2.6, "#FDE68A"), (27, 74, 2.0, "#FFFFFF"), (33, 33, 1.4, "#FDE68A")]

def shapes(mono=None):
    """Lista di forme: (path, fill, stroke, strokeWidth, alpha, rotation(deg,px,py) o None)"""
    W = lambda c: mono or c
    sh = []
    sh.append((spiral_path(), None, W("#FFFFFF"), 0.7, 0.35, None))
    for s in SPARKLES:
        sh.append((star(s[0], s[1], s[2], s[2] * 0.22), W(s[3]), None, 0, 0.95, None))
    for c in cards():
        rot = (c["rot"], c["x"], c["y"])
        sh.append((rect_path(c["x"], c["y"], c["w"] + 0.9, c["h"] + 0.9, 1.0), W("#FFFFFF"), None, 0, 1, rot))
        sh.append((rect_path(c["x"], c["y"], c["w"], c["h"], 0.7), W(c["color"]) if not mono else None, None, 0, 1, rot))
        if c["big"] and not mono:
            sh.append((mountain_path(c["x"], c["y"], c["w"], c["h"]), "#FFFFFF", None, 0, 0.9, rot))
    # bussola
    sh.append((circle(C, C, 11.5), W("#1E1B4B") if not mono else None, W("#FCD34D"), 2.0, 1, None))
    sh.append((star(C, C, 9.0, 2.0), W("#FDE68A"), None, 0, 1, None))
    # ago: metà nord corallo, metà sud bianca
    sh.append((f"M{f(C)},{f(C-9)} L{f(C+2.2)},{f(C)} L{f(C-2.2)},{f(C)} Z", W("#FB7185"), None, 0, 1, None))
    sh.append((f"M{f(C)},{f(C+9)} L{f(C+2.2)},{f(C)} L{f(C-2.2)},{f(C)} Z", W("#FFFFFF"), None, 0, 1, None))
    sh.append((circle(C, C, 1.3), W("#1E1B4B") if not mono else None, None, 0, 1, None))
    return sh

BG = ("#1E1B4B", "#6D28D9", "#0F766E")

def svg(size, crop=True, mono=None, monobg=None):
    vb = "18 18 72 72" if crop else "0 0 108 108"
    out = [f'<svg width="{size}" height="{size}" viewBox="{vb}" xmlns="http://www.w3.org/2000/svg">']
    if monobg:
        out.append(f'<rect width="108" height="108" fill="{monobg}"/>')
    else:
        out.append(f'<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="18" y1="18" x2="90" y2="90">'
                   f'<stop offset="0" stop-color="{BG[0]}"/><stop offset="0.55" stop-color="{BG[1]}"/><stop offset="1" stop-color="{BG[2]}"/></linearGradient></defs>'
                   f'<rect width="108" height="108" fill="url(#bg)"/>')
    for p, fill, stroke, sw, a, rot in shapes(mono):
        attrs = f'd="{p}" fill="{fill or "none"}"'
        if stroke: attrs += f' stroke="{stroke}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"'
        if a != 1: attrs += f' opacity="{a}"'
        el = f'<path {attrs}/>'
        if rot: el = f'<g transform="rotate({f(rot[0])} {f(rot[1])} {f(rot[2])})">{el}</g>'
        out.append(el)
    out.append("</svg>")
    return "".join(out)

def argb(hexc, a=1.0):
    return "#%02X%s" % (round(a * 255), hexc.lstrip("#").upper())

def vector(mono=False):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generato da tools/icon/gen_icon.py: bussola con foto a spirale -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for p, fill, stroke, sw, a, rot in shapes("#FFFFFF" if mono else None):
        attrs = [f'android:pathData="{p}"']
        if fill: attrs.append(f'android:fillColor="{argb(fill)}"')
        if a != 1 and fill: attrs.append(f'android:fillAlpha="{a}"')
        if stroke:
            attrs += [f'android:strokeColor="{argb(stroke)}"', f'android:strokeWidth="{sw}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
            if a != 1: attrs.append(f'android:strokeAlpha="{a}"')
        el = "    <path " + " ".join(attrs) + " />"
        if rot:
            el = (f'    <group android:rotation="{f(rot[0])}" android:pivotX="{f(rot[1])}" android:pivotY="{f(rot[2])}">\n    '
                  + el + "\n    </group>")
        out.append(el)
    out.append("</vector>")
    return "\n".join(out) + "\n"

BACKGROUND_XML = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Sfondo: sfumatura notte fantasy (indaco → viola → verde acqua) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear"
                android:startX="18" android:startY="18" android:endX="90" android:endY="90"
                android:startColor="{argb(BG[0])}" android:centerColor="{argb(BG[1])}" android:endColor="{argb(BG[2])}" />
        </aapt:attr>
    </path>
</vector>
'''

if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "ic_launcher_foreground.xml"), "w").write(vector(False))
    open(os.path.join(out, "ic_launcher_monochrome.xml"), "w").write(vector(True))
    open(os.path.join(out, "ic_launcher_background.xml"), "w").write(BACKGROUND_XML)
    open(os.path.join(out, "preview.html"), "w").write(f"""<!doctype html><html><head><meta charset=utf-8><style>
body{{margin:0;font-family:Roboto,'Noto Sans',sans-serif;background:linear-gradient(160deg,#0f172a,#1e3a8a 55%,#0e7490);color:#fff;padding:26px}}
h1{{font-size:28px;margin:0 0 4px;text-align:center}} p{{opacity:.8;text-align:center;margin:0 0 22px}}
svg{{display:block}} .row{{display:flex;gap:34px;justify-content:center;align-items:flex-end}}
.item{{display:flex;flex-direction:column;align-items:center;gap:10px;font-size:15px}}
.sq{{border-radius:28%;overflow:hidden;box-shadow:0 10px 30px rgba(0,0,0,.35)}} .ci{{border-radius:50%;overflow:hidden}}
</style></head><body><h1>Nuova icona: Bussola dei ricordi</h1><p>Proporzioni reali sul telefono</p><div class=row>
<div class=item><div class=sq>{svg(260)}</div>HyperOS</div>
<div class=item><div class=ci>{svg(150)}</div>Rotonda</div>
<div class=item><div class=sq>{svg(64)}</div>Piccola</div>
<div class=item><div class=ci>{svg(150, mono='#C7D2FE', monobg='#1E1B4B')}</div>Icone a tema</div>
</div></body></html>""")
