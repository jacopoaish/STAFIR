#!/usr/bin/env python3
"""Crea le icone Android (primo piano adattivo) dall'immagine scelta dall'utente.

L'area interna alla cornice luminosa diventa la zona visibile dell'icona (72 dp su 108);
il margine esterno (visibile solo nelle animazioni del launcher) è la stessa immagine sfocata.
Uso: python3 tools/icon/utente/genera_icona.py
"""
import os
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "res")
src = Image.open(os.path.join(HERE, "icona-originale.png")).convert("RGB")
W = src.width
inset = round(W * 90 / 1254)               # dentro la cornice luminosa
inner = src.crop((inset, inset, W - inset, W - inset))

BIG = 2048
vis = round(BIG * 72 / 108)                # zona visibile dell'icona adattiva
canvas = inner.resize((BIG, BIG), Image.LANCZOS).filter(ImageFilter.GaussianBlur(BIG * 0.03))
canvas = Image.eval(canvas, lambda v: int(v * 0.75))
art = inner.resize((vis, vis), Image.LANCZOS)
mask = Image.new("L", (vis, vis), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, vis - 1, vis - 1), radius=vis * 0.12, fill=255)
mask = mask.filter(ImageFilter.GaussianBlur(vis * 0.012))
off = (BIG - vis) // 2
canvas.paste(art, (off, off), mask)
canvas.save(os.path.join(HERE, "anteprima-primo-piano.png"))

for d, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    canvas.resize((px, px), Image.LANCZOS).save(os.path.join(RES, f"mipmap-{d}", "ic_launcher_foreground.png"), optimize=True)
print("ok")
