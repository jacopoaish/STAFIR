#!/usr/bin/env python3
"""Crea l'icona Android dall'immagine scelta dall'utente, senza il nero intorno.

Il riquadro arrotondato con la cornice luminosa diventa l'icona; fuori resta solo il bagliore
della cornice (trasparente altrove). Lo sfondo dell'icona adattiva è trasparente.
Uso: python3 tools/icon/utente/genera_icona.py
"""
import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "res")
src = Image.open(os.path.join(HERE, "icona-originale.png")).convert("RGB")
W, H = src.size
sx, sy = W / 1254, H / 1254
box = (41 * sx, 28 * sy, 1213 * sx, 1213 * sy)      # bordo esterno della cornice luminosa
radius = 262 * sx

# dentro il riquadro: tutto opaco; fuori: solo il bagliore (più luminoso = più opaco)
shape = Image.new("L", (W, H), 0)
ImageDraw.Draw(shape).rounded_rectangle(box, radius=radius, fill=255)
shape = shape.filter(ImageFilter.GaussianBlur(1.5 * sx))
lum = src.convert("L").point(lambda v: max(0, min(255, int((v - 22) * 2.4))))
near = Image.new("L", (W, H), 0)
pad = 26 * sx
ImageDraw.Draw(near).rounded_rectangle((box[0] - pad, box[1] - pad, box[2] + pad, box[3] + pad), radius=radius + pad, fill=255)
near = near.filter(ImageFilter.GaussianBlur(10 * sx))
glow = ImageChops.multiply(lum, near)
alpha = ImageChops.lighter(shape, glow)
icon = src.copy()
icon.putalpha(alpha)
cx0, cy0, cx1, cy1 = (int(box[0] - pad), int(box[1] - pad), int(box[2] + pad), int(box[3] + pad))
side = max(cx1 - cx0, cy1 - cy0)
icon = icon.crop((cx0, cy0, cx0 + side, cy0 + side))

# il riquadro (senza bagliore) occupa la zona visibile dell'icona adattiva: 72 dp su 108
BIG = 2048
inner = (box[2] - box[0]) / side                    # frazione del ritaglio occupata dal riquadro
target = BIG * 72 / 108 / inner
art = icon.resize((round(target), round(target)), Image.LANCZOS)
canvas = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
off = (BIG - art.width) // 2
canvas.alpha_composite(art, (off, off))
canvas.save(os.path.join(HERE, "anteprima-primo-piano.png"))
for d, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    canvas.resize((px, px), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{d}", "ic_launcher_foreground.png"), optimize=True)
print("ok")
