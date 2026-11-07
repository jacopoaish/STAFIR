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
shot() { sleep "${2:-1}"; adb exec-out screencap -p </dev/null > "$OUT/$1.png"; echo "📸 $1"; }
raw_dump() { adb shell uiautomator dump /sdcard/ui.xml </dev/null >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" </dev/null >/dev/null 2>&1; }
dump() {
  raw_dump
  # L'emulatore a volte mostra "<app di sistema> isn't responding": lo chiudiamo (non riguarda Stafir).
  if grep -qE "responding|non risponde" "$OUT/ui.xml" 2>/dev/null; then
    local w
    w=$(python3 "$DIR/find_node.py" "$OUT/ui.xml" "Wait")
    [ -z "$w" ] && w=$(python3 "$DIR/find_node.py" "$OUT/ui.xml" "Attendi")
    if [ -n "$w" ]; then adb shell input tap $w </dev/null; echo "🔧 chiuso un avviso di sistema dell'emulatore"; sleep 2; raw_dump; fi
  fi
}
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
  local tries=${2:-12}
  for _ in $(seq 1 "$tries"); do
    local c
    c=$(find_node "$1")
    if [ -n "$c" ]; then adb shell input tap $c </dev/null; echo "👆 $1"; return 0; fi
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
    m = re.match(r"([\d.]+) risultat[oi]", n.get("text") or "")
    if m:
        print(m.group(0)); break
else:
    print("nessun conteggio")
PY
}
hide_keyboard() {
  if adb shell dumpsys input_method </dev/null | grep -q "mInputShown=true"; then adb shell input keyevent 4 </dev/null; sleep 1; fi
}
restart() { # riapre l'app da zero sulla schermata principale
  adb shell am start -S -W -n $PKG/.MainActivity </dev/null >/dev/null
  sleep 3
}
search() { # $1 = testo da cercare (spazi come %s)
  if [ -n "$(find_node "Cancella")" ]; then tap "Cancella"; sleep 1; fi
  tap "class:android.widget.EditText"
  adb shell input text "$1" </dev/null
  sleep 4
  hide_keyboard
  log "🔎 «${1//%s/ }» → $(results)"
  shot "$2" 1
}
media_count() {
  local i v
  i=$(adb shell content query --uri content://media/external/images/media --projection _id </dev/null 2>/dev/null | grep -c "Row:")
  v=$(adb shell content query --uri content://media/external/video/media --projection _id </dev/null 2>/dev/null | grep -c "Row:")
  echo "$i foto, $v video"
}

log "# Test sull'emulatore"
log ""
adb wait-for-device
# Niente finestre di errore di sistema dell'emulatore (es. launcher lento) sopra l'app
adb shell settings put global hide_error_dialogs 1 </dev/null
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS </dev/null >/dev/null 2>&1
sleep 10
log "Dispositivo: $(adb shell getprop ro.product.model) · Android $(adb shell getprop ro.build.version.release)"

# 1) Foto e video di prova (con data e GPS)
# (mapfile: i comandi adb dentro un ciclo "while read" consumerebbero l'elenco)
mapfile -t FIXTURES < "$DIR/fixtures/manifest.tsv"
for line in "${FIXTURES[@]}"; do
  name="${line%%$'\t'*}"
  folder="${line#*$'\t'}"
  [ -z "$name" ] && continue
  adb shell mkdir -p "/sdcard/$folder" </dev/null
  adb push "$DIR/fixtures/$name" "/sdcard/$folder/$name" </dev/null >/dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/$folder/$name" </dev/null >/dev/null 2>&1
done
for _ in $(seq 1 30); do
  [ "$(media_count)" = "18 foto, 1 video" ] && break
  sleep 2
done
log "Nella libreria multimediale: $(media_count) (attesi 18 foto, 1 video)"

# 2) Installazione e permessi (concessi in anticipo: il dialogo di sistema non è testabile qui)
adb install -r "$APK" || { log "❌ Installazione non riuscita"; exit 1; }
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO ACCESS_MEDIA_LOCATION POST_NOTIFICATIONS; do
  adb shell pm grant $PKG android.permission.$p 2>/dev/null
done
adb logcat -c
START=$(adb shell am start -W -n $PKG/.MainActivity | grep TotalTime | tr -dc '0-9')
log "⏱️ Avvio a freddo (emulatore): ${START} ms"

# 3) Procedura guidata (con i permessi già concessi parte dalla seconda schermata)
shot 01-avvio 3
if [ -n "$(find_node "Consenti accesso")" ]; then tap "Consenti accesso"; fi
wait_for "Cosa vuoi riconoscere" && shot 02-cosa-riconoscere
tap "Avanti"
wait_for "trovat" 40 && shot 03-archivio 2
tap "Avvia" 60
sleep 5
shot 04-home-analisi-in-corso
# attende la fine del riconoscimento (max 5 minuti)
for _ in $(seq 1 60); do
  [ -z "$(find_node "Riconoscimento in corso" --any)" ] && [ -z "$(find_node "Da riconoscere" --any)" ] && [ -z "$(find_node "Ricerca dei volti" --any)" ] && break
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

# 4b) Persone: i ritratti devono formare 3 persone; diamo un nome alla prima e la cerchiamo
restart
if tap "Tutte le persone"; then
  sleep 3
  shot 14a-persone 1
  dump
  PEOPLE=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
print(sum(1 for n in root.iter("node") if re.fullmatch(r"\d+ foto", n.get("text") or "")))
PY
)
  log "👤 Persone trovate: $PEOPLE (attese 3: Roosevelt 4 foto, Douglass 3, Lincoln 2)"
  if tap "Aggiungi un nome"; then
    sleep 3
    shot 14b-persona 1
    if tap "Aggiungi un nome"; then
      wait_for "Come si chiama"
      tap "class:android.widget.EditText"
      adb shell input text "Teodoro" </dev/null
      sleep 1
      hide_keyboard
      shot 14c-nome 1
      tap "Salva"
      sleep 4
      shot 14d-persona-con-nome 1
      [ -n "$(find_node "Teodoro" --any)" ] && log "✅ Nome «Teodoro» assegnato" || { log "❌ Il nome non compare"; FAIL=1; }
    fi
  fi
fi
restart
search "Teodoro" 14e-cerca-teodoro
if tap "IMG_202503"; then
  sleep 2
  if tap "Informazioni"; then
    sleep 2
    adb shell input swipe 540 1700 540 500 300 </dev/null
    shot 14f-info-volti 2
    if tap "Teodoro"; then shot 14g-volto 2; adb shell input keyevent 4 </dev/null; fi
  fi
fi
FACES_LOG=$(adb logcat -d </dev/null | grep -E "FaceRecognizer|FaceIndex" | tail -5)
[ -n "$FACES_LOG" ] && log "Registro volti: $FACES_LOG"

# 5) Visualizzatore e informazioni
restart
shot 15-griglia 1
if tap "IMG_20240502"; then
  shot 16-visualizzatore 3
  if tap "Informazioni"; then shot 17-informazioni 3; fi
fi

# 6) Selezione multipla (tieni premuto)
restart
c=$(find_node "IMG_20240610")
if [ -n "$c" ]; then
  adb shell input swipe $c $c 900 </dev/null; sleep 1; shot 18-selezione
  if tap "Seleziona tutto"; then
    sleep 2
    shot 18b-seleziona-tutto 1
    [ -n "$(find_node "19 selezionati" --any)" ] && log "✅ Seleziona tutto: 19 selezionati" || { log "❌ Seleziona tutto non ha selezionato i 19 file"; FAIL=1; }
    tap "Deseleziona tutto"
  fi
fi

# 7) Impostazioni, archivio, avanzate
restart
if tap "Impostazioni"; then
  shot 19-impostazioni 2
  if tap "Archivio e avanzamento"; then shot 20-archivio 2; adb shell input keyevent 4 </dev/null; sleep 1; fi
  adb shell input swipe 540 1800 540 500 300 </dev/null; sleep 1
  if tap "Avanzate"; then
    adb shell input swipe 540 1800 540 600 300 </dev/null; sleep 1
    shot 21-avanzate 1
  fi
fi

# 8) Video nel visualizzatore
restart
search "video" 22-video-risultati
if tap "VID_20230815"; then shot 23-lettore-video 4; fi

# 9) Apertura da un'altra app ("Apri con")
ID=$(adb shell content query --uri content://media/external/images/media --projection _id --where "_display_name=\'IMG_20230815_101500.jpg\'" </dev/null | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
if [ -n "$ID" ]; then
  adb shell am start -a android.intent.action.VIEW -t image/jpeg -d "content://media/external/images/media/$ID" -n $PKG/.MainActivity </dev/null >/dev/null
  shot 24-apri-con 3
fi

# 9b) Tanti file: intestazioni dei mesi, mese durante lo scorrimento, pulsanti «inizio/fine»
adb shell mkdir -p /sdcard/Pictures/Copie </dev/null
for i in $(seq 1 90); do
  adb shell cp /sdcard/DCIM/Camera/IMG_20240610_120000.jpg "/sdcard/Pictures/Copie/copia_$i.jpg" </dev/null
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/Copie/copia_$i.jpg" </dev/null >/dev/null 2>&1
done
sleep 5
restart
sleep 4
adb shell input swipe 540 1700 540 900 400 </dev/null
shot 24b-scorrimento-mese 0
if tap "Vai alla fine"; then
  sleep 2
  shot 24c-fine 0
  [ -n "$(find_node "Vai all'inizio" --any)" ] && log "✅ Pulsanti «Vai alla fine / all'inizio» presenti" || log "⚠️ Pulsante «Vai all'inizio» non visibile"
  tap "Vai all'inizio"
  sleep 2
  shot 24d-inizio 0
fi

# 10) Icona nell'elenco delle app del launcher
adb shell input keyevent 3 </dev/null; sleep 2
adb shell input swipe 540 1900 540 400 300 </dev/null; sleep 3
dump
shot 25-icona-launcher 1

# 11) Esito
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
