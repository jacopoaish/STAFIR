#!/usr/bin/env python3
"""Trova un elemento in un dump di uiautomator e stampa le coordinate del suo centro.

Uso: find_node.py ui.xml "testo"        (testo o descrizione, contiene, maiuscole ignorate)
     find_node.py ui.xml "class:android.widget.EditText"
     find_node.py ui.xml "testo" --any    (anche se disabilitato)
Stampa "x y" oppure niente.
"""
import re
import sys
import xml.etree.ElementTree as ET


def main():
    path, query = sys.argv[1], sys.argv[2]
    any_state = "--any" in sys.argv
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return
    q = query.lower()
    best = None
    for node in root.iter("node"):
        if query.startswith("class:"):
            ok = node.get("class") == query[6:]
        else:
            text = (node.get("text") or "").lower()
            desc = (node.get("content-desc") or "").lower()
            ok = q in text or q in desc
        if not ok:
            continue
        if not any_state and node.get("enabled") == "false":
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        if x2 <= x1 or y2 <= y1:
            continue
        best = ((x1 + x2) // 2, (y1 + y2) // 2)
        break
    if best:
        print(f"{best[0]} {best[1]}")


if __name__ == "__main__":
    main()
