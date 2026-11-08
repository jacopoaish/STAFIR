#!/usr/bin/env python3
"""Stampa nel log di CI gli screenshot ridotti (JPEG in base64), per poterli rivedere dal log.

Uso: print_shots.py <cartella>
"""
import base64
import glob
import io
import os
import sys

from PIL import Image

folder = sys.argv[1]
for path in sorted(glob.glob(os.path.join(folder, "*.png"))):
    try:
        im = Image.open(path).convert("RGB")
    except Exception as e:  # screenshot vuoto o illeggibile
        print(f"=====SHOT {os.path.basename(path)} ERRORE {e}=====")
        continue
    im.thumbnail((432, 960))
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=62)
    data = base64.b64encode(buf.getvalue()).decode()
    print(f"=====SHOT {os.path.basename(path)}=====")
    for i in range(0, len(data), 3000):
        print(data[i:i + 3000])
    print("=====END=====")
