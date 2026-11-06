#!/usr/bin/env python3
"""Scarica GeoNames (città >15.000 abitanti) e crea il file compatto usato dal geocoder offline.

Uscita: core/src/main/assets/geo/cities.tsv
Colonne: nome  lat  lon  codice_paese  regione
Dati GeoNames: licenza CC BY 4.0 (https://www.geonames.org).
"""
import io
import os
import sys
import urllib.request
import zipfile

BASE = "https://download.geonames.org/export/dump/"
OUT = os.path.join(os.path.dirname(__file__), "..", "core", "src", "main", "assets", "geo", "cities.tsv")


def fetch(name: str) -> bytes:
    with urllib.request.urlopen(BASE + name, timeout=120) as r:
        return r.read()


def main() -> int:
    admin1 = {}
    for line in fetch("admin1CodesASCII.txt").decode("utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 2:
            admin1[parts[0]] = parts[1]

    z = zipfile.ZipFile(io.BytesIO(fetch("cities15000.zip")))
    rows = []
    with z.open("cities15000.txt") as f:
        for raw in io.TextIOWrapper(f, encoding="utf-8"):
            p = raw.rstrip("\n").split("\t")
            if len(p) < 11:
                continue
            name, lat, lon, cc, a1 = p[1], p[4], p[5], p[8], p[10]
            region = admin1.get(f"{cc}.{a1}", "")
            rows.append(f"{name}\t{float(lat):.4f}\t{float(lon):.4f}\t{cc}\t{region}")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("\n".join(rows) + "\n")
    print(f"Scritte {len(rows)} città in {os.path.normpath(OUT)}")
    return 0 if len(rows) > 10000 else 1


if __name__ == "__main__":
    sys.exit(main())
