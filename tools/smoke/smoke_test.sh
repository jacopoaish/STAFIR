#!/bin/bash
# Test sull'emulatore: installa l'APK, carica foto/video di prova, percorre la procedura guidata,
# prova la ricerca in italiano e il visualizzatore, salva gli screenshot e fallisce se l'app va in crash.
# Uso: smoke_test.sh <apk> <cartella_uscita>
set -u
APK="$1"
OUT="$2"
PKG=com.stafir.app
DIR=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
REPORT="$OUT/report.md"
: > "$REPORT"
FAIL=0

log() { echo "$@"; echo "$@" >> "$REPORT"; }
shot() { sleep "${2:-1}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "📸 $1"; }
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1; }
find_node() { dump; python3 "$DIR/find_node.py" "$OUT/ui.xml" "$@"; }
wait_for() { # attende che compaia un testo
  local tries=${2:-30}
  for _ in $(seq 1 "$tries"); do
    [ -n "$(find_node "$1" --any)" ] && return 0
    sleep 1
  done
  log "❌ Non compare: $1"; cp "$OUT/ui.xml" "$OUT/ui-missing-$(echo "$1" | tr -cd 'a-zA-Z0-9').xml"; return 1
}
tap() {
  local tries=${2:-20}
  for _ in $(seq 1 "$tries"); do
    local c
    c=$(find_node "$1")
    if [ -n "$c" ]; then adb shell input tap $c; echo "👆 $1"; return 0; fi
    sleep 1
  done
  log "❌ Non trovato da toccare: $1"; cp "$OUT/ui.xml" "$OUT/ui-missing-$(echo "$1" | tr -cd 'a-zA-Z0-9').xml"; FAIL=1; return 1
}
crashed() { adb logcat -d -b crash 2>/dev/null | grep -q "$PKG"; }
results() { # legge "N risultati" dallo schermo
  dump
  python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    print("?"); sys.exit()
for n in root.iter("node"):
    m = re.match(r"([\d.]+) risultati", n.get("text") or "")
    if m:
        print(m.group(1)); break
else:
    print("0?")
PY
}
search() { # $1 = testo da cercare (spazi come %s)
  if [ -n "$(find_node "Cancella")" ]; then tap "Cancella"; sleep 1; fi
  tap "class:android.widget.EditText"
  adb shell input text "$1"
  adb shell input keyevent 66
  sleep 4
  log "🔎 «${1//%s/ }» → $(results) risultati"
  shot "$2" 1
}

log "# Test sull'emulatore"
log ""
adb wait-for-device
log "Dispositivo: $(adb shell getprop ro.product.model) · Android $(adb shell getprop ro.build.version.release)"

# 1) Foto e video di prova (con data e GPS)
while IFS=$'\t' read -r name folder; do
  [ -z "$name" ] && continue
  adb shell mkdir -p "/sdcard/$folder"
  adb push "$DIR/fixtures/$name" "/sdcard/$folder/$name" >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/$folder/$name" >/dev/null 2>&1
done < "$DIR/fixtures/manifest.tsv"
sleep 8
log "File nella libreria multimediale: $(adb shell content query --uri content://media/external/file --projection _display_name --where "_display_name\ LIKE\ \'%IMG%\'\ OR\ _display_name\ LIKE\ \'%VID%\'" 2>/dev/null | grep -c Row)"

# 2) Installazione e permessi (concessi in anticipo: il dialogo di sistema non è testabile qui)
adb install -r "$APK" || { log "❌ Installazione non riuscita"; exit 1; }
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION POST_NOTIFICATIONS; do
  adb shell pm grant $PKG android.permission.$p 2>/dev/null
done
adb logcat -c
START=$(adb shell am start -W -n $PKG/.MainActivity | grep TotalTime | tr -dc '0-9')
log "⏱️ Avvio a freddo (emulatore): ${START} ms"

# 3) Procedura guidata
shot 01-benvenuto 3
tap "Consenti accesso"
wait_for "Cosa vuoi riconoscere" && shot 02-cosa-riconoscere
tap "Avanti"
wait_for "elementi trovati" 40 && shot 03-archivio 2
tap "Avvia" 60
sleep 5
shot 04-home-analisi-in-corso
# attende la fine del riconoscimento (max 3 minuti)
for _ in $(seq 1 36); do
  [ -z "$(find_node "Riconoscimento in corso" --any)" ] && [ -z "$(find_node "Da riconoscere" --any)" ] && break
  sleep 5
done
shot 05-home 2

# 4) Ricerca in italiano
search "mare" 06-cerca-mare
search "cani%sa%sRoma" 07-cerca-cani-roma
search "video" 08-cerca-video
search "Sicilia" 09-cerca-sicilia
search "documenti" 10-cerca-documenti
search "foto%sdi%swhatsapp" 11-cerca-whatsapp
search "estate%s2023" 12-cerca-estate-2023
search "Marco%sal%smare" 13-cerca-marco-mare
search "crea%sun%scarosello%sdel%smare" 14-comando

# 5) Visualizzatore e informazioni
if [ -n "$(find_node "Cancella")" ]; then tap "Cancella"; sleep 2; fi
tap "IMG_20240502" && shot 15-visualizzatore 3
tap "Informazioni" && shot 16-informazioni 3
adb shell input keyevent 4; sleep 1
adb shell input keyevent 4; sleep 2

# 6) Selezione multipla (tieni premuto)
c=$(find_node "IMG_20240610")
if [ -n "$c" ]; then adb shell input swipe $c $c 800; sleep 1; shot 17-selezione; adb shell input keyevent 4; fi

# 7) Impostazioni, archivio, guida batteria, banco qualità
tap "Impostazioni" && shot 18-impostazioni 2
tap "Archivio e avanzamento" && shot 19-archivio 2
adb shell input keyevent 4; sleep 1
tap "Avanzate" && shot 20-avanzate 2
adb shell input keyevent 4; sleep 1

# 8) Video nel visualizzatore
search "video" 21-video-risultati
tap "VID_20230815" && shot 22-lettore-video 4
adb shell input keyevent 4; sleep 1

# 9) Apertura da un'altra app ("Apri con")
ID=$(adb shell content query --uri content://media/external/images/media --projection _id --where "_display_name=\'IMG_20230815_101500.jpg\'" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
if [ -n "$ID" ]; then
  adb shell am start -a android.intent.action.VIEW -t image/jpeg -d "content://media/external/images/media/$ID" -n $PKG/.MainActivity >/dev/null
  shot 23-apri-con 3
fi

# 10) Esito
adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt" 2>/dev/null
grep -E "AnalysisWorker|StafirApp|OfflineGeocoder|AndroidRuntime" "$OUT/logcat.txt" | tail -50 > "$OUT/logcat-app.txt"
if crashed; then
  log ""
  log "❌ CRASH dell'app:"
  grep -A 30 "$PKG" "$OUT/crash.txt" | head -60 | tee -a "$REPORT"
  FAIL=1
fi
[ "$FAIL" = 0 ] && log "✅ Nessun crash, tutti i passi completati" || log "⚠️ Alcuni passi non riusciti (vedi sopra)"
exit $FAIL
