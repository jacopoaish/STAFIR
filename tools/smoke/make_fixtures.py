#!/usr/bin/env python3
"""Genera le foto/video di prova per il test sull'emulatore (con data e GPS nei metadati).

Le immagini sorgente sono immagini di esempio pubbliche (repository PyTorch Hub, TensorFlow Models,
OpenCV samples). Uso: python3 make_fixtures.py <cartella_sorgenti> <cartella_uscita>
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont
from PIL.TiffImagePlugin import IFDRational

SRC, OUT = sys.argv[1], sys.argv[2]

# file sorgente -> (nome sul telefono, cartella sul telefono, data, (lat, lon) o None)
PHOTOS = [
    ("tf2.jpg", "IMG_20230815_101500.jpg", "DCIM/Camera", "2023:08:15 10:15:00", (38.1938, 13.3275)),   # spiaggia, Mondello (Palermo)
    ("dog.jpg", "IMG_20240502_173000.jpg", "DCIM/Camera", "2024:05:02 17:30:00", (41.9028, 12.4964)),   # cane, Roma
    ("tf1.jpg", "IMG_20240610_120000.jpg", "DCIM/Camera", "2024:06:10 12:00:00", (45.4642, 9.1900)),    # cani, Milano
    ("building.jpg", "IMG_20220320_090000.jpg", "DCIM/Camera", "2022:03:20 09:00:00", (41.8986, 12.4769)),  # edificio, Roma
    ("home.jpg", "IMG_20210704_190000.jpg", "DCIM/Camera", "2021:07:04 19:00:00", (43.7696, 11.2558)),   # palazzo, Firenze
    ("messi5.jpg", "IMG_20191020_210000.jpg", "DCIM/Camera", "2019:10:20 21:00:00", (41.3809, 2.1228)),  # calcio, Barcellona
    ("fruits.jpg", "IMG-20240110-WA0003.jpg", "Pictures", "2024:01:10 13:00:00", None),                  # frutta, da WhatsApp
    ("baboon.jpg", "IMG_20180505_110000.jpg", "DCIM/Camera", "2018:05:05 11:00:00", None),              # animale
]


def dms(value):
    value = abs(value)
    d = int(value)
    m = int((value - d) * 60)
    s = round(((value - d) * 60 - m) * 60, 2)
    return (IFDRational(d), IFDRational(m), IFDRational(s))


def write_photo(src, name, date, gps, out_dir):
    im = Image.open(os.path.join(SRC, src)).convert("RGB")
    im.thumbnail((1280, 1280))
    exif = Image.Exif()
    exif[0x0110] = "Stafir Test"  # Model
    exif[0x0132] = date  # DateTime
    exif_ifd = exif.get_ifd(0x8769)
    exif_ifd[0x9003] = date  # DateTimeOriginal
    exif_ifd[0x9004] = date  # DateTimeDigitized
    if gps:
        lat, lon = gps
        g = exif.get_ifd(0x8825)
        g[0] = b"\x02\x02\x00\x00"
        g[1] = "N" if lat >= 0 else "S"
        g[2] = dms(lat)
        g[3] = "E" if lon >= 0 else "W"
        g[4] = dms(lon)
    im.save(os.path.join(out_dir, name), "JPEG", quality=85, exif=exif)


def write_receipt(out_dir):
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
    big = ImageFont.truetype(font_path, 44)
    mid = ImageFont.truetype(font_path, 34)
    im = Image.new("RGB", (720, 1280), (250, 250, 245))
    d = ImageDraw.Draw(im)
    lines = [
        (big, "TRATTORIA DA MARIO"), (mid, "Via del Corso 12 - Roma"), (mid, "P.IVA 01234567890"), (mid, ""),
        (mid, "DOCUMENTO COMMERCIALE"), (mid, "di vendita o prestazione"), (mid, ""),
        (mid, "2 x Carbonara      24,00"), (mid, "1 x Acqua           2,50"), (mid, "1 x Vino rosso     12,00"),
        (mid, "1 x Tiramisu        6,00"), (mid, "Coperto             4,00"), (mid, ""),
        (big, "TOTALE EUR   48,50"), (mid, ""), (mid, "Pagamento elettronico"), (mid, "12-04-2024  21:34"),
        (mid, "GRAZIE E ARRIVEDERCI"),
    ]
    y = 60
    for f, t in lines:
        d.text((40, y), t, fill=(20, 20, 20), font=f)
        y += 62
    exif = Image.Exif()
    exif.get_ifd(0x8769)[0x9003] = "2024:04:12 21:35:00"
    im.save(os.path.join(out_dir, "IMG_20240412_213500.jpg"), "JPEG", quality=85, exif=exif)


def write_video(out_dir):
    # 4 secondi dalla foto della spiaggia, con posizione (Mondello) e data nei metadati
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-i", os.path.join(SRC, "tf2.jpg"),
        "-vf", "scale=1280:-2,zoompan=z='min(zoom+0.0015,1.2)':d=100:s=1280x852,format=yuv420p",
        "-t", "4", "-r", "25", "-c:v", "libx264", "-preset", "veryfast", "-crf", "30",
        "-metadata", "location=+38.1938+013.3275/", "-metadata", "creation_time=2023-08-15T10:20:00Z",
        "-movflags", "+faststart", os.path.join(out_dir, "VID_20230815_102000.mp4"),
    ], check=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = []
    for src, name, folder, date, gps in PHOTOS:
        write_photo(src, name, date, gps, OUT)
        manifest.append(f"{name}\t{folder}")
    write_receipt(OUT)
    manifest.append("IMG_20240412_213500.jpg\tDCIM/Camera")
    write_video(OUT)
    manifest.append("VID_20230815_102000.mp4\tDCIM/Camera")
    with open(os.path.join(OUT, "manifest.tsv"), "w") as f:
        f.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    main()
