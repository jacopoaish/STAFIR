# Stafir — Pics Revolution

Galleria intelligente per Android: gestisce, riconosce, classifica e cerca **tutte le foto e i video del telefono**, con ricerca in **italiano naturale** e intelligenza artificiale **che gira solo sul telefono**.

## Come provare l'app (Fase 2)

1. Dal telefono apri la pagina **Releases** di questo repository e scarica l'ultimo file `Stafir-fase2-buildN.apk`.
2. Tocca il file scaricato → consenti l'installazione da questa fonte se richiesto → **Installa**.
3. Ogni nuova build si installa sopra la precedente (stessa firma), senza perdere i dati.

## Cosa contiene la Fase 1

| Funzione | Stato |
|---|---|
| Procedura guidata in 3 schermate (accesso → cosa riconoscere → avvia) | ✅ |
| Conteggio dell'archivio (foto, video, spazio) | ✅ |
| Griglia veloce (miniature di sistema, pizzica per cambiare dimensione, barra di scorrimento rapido con data) | ✅ |
| Selezione multipla (tieni premuto), condividi, sposta nel cestino di sistema | ✅ |
| Visualizzatore a schermo intero (pizzica/doppio tocco per ingrandire), lettore video, informazioni, mappa, preferiti | ✅ |
| Può essere scelta come app predefinita per aprire foto e video | ✅ |
| Riconoscimento scene e oggetti (400+ categorie) su foto e 3 fotogrammi chiave per video | ✅ |
| Luoghi dal GPS **senza internet** (città, regione, paese) | ✅ |
| Provenienza (fotocamera, WhatsApp, Telegram, screenshot, download…) | ✅ |
| Ricerca in italiano naturale + dettatura vocale + filtri toccabili | ✅ |
| Analisi in background solo in carica, protezione dal calore, ripresa automatica, file nuovi rilevati da soli | ✅ |
| Guida HyperOS (avvio automatico, batteria) e rilevamento interruzioni | ✅ |
| Banco qualità: etichetti a mano alcune tue foto e misuri precisione, richiamo e tempi | ✅ |
| Riconoscimento comandi ("crea…", "sposta…"): capiti e spiegati, eseguiti nelle prossime fasi | 🟡 |

## Cosa aggiunge la Fase 2 – Volti e persone

| Funzione | Stato |
|---|---|
| Rilevamento volti su foto e fotogrammi chiave dei video (ML Kit Face Detection, sul telefono) | ✅ |
| Impronta del volto (FaceNet 128-d, TFLite, Apache 2.0) e raggruppamento automatico della stessa persona | ✅ |
| Schermata «Persone» con i volti; nome dato una volta vale per tutte le foto e i video | ✅ |
| Correzioni: «non è lui/lei», «è un'altra persona», unisci, nascondi; le correzioni non vengono annullate dall'analisi | ✅ |
| Ricerca per nome in italiano («Marco al mare nel 2023») e filtro «Persone» | ✅ |
| Le foto già analizzate in Fase 1 vengono ripassate solo per i volti (in carica) | ✅ |

## Architettura (moduli separati)

```
domain/  Kotlin puro, testabile senza telefono: parser italiano, vocabolario concetti, provenienza
core/    Database (Room + FTS), sincronizzazione con MediaStore, ricerca SQL, geocoder offline
ai/      Riconoscitori: ML Kit scene, volti (ML Kit + FaceNet); in futuro OCR, CLIP…
app/     Interfaccia (Jetpack Compose, Material 3), worker in background, impostazioni
```

- **Nessuna copia dei file**: l'app legge la stessa libreria della Galleria (MediaStore). Nel database ci sono solo metadati ed etichette.
- **Nessun permesso internet** nella Fase 1: niente può lasciare il telefono.
- **Non distruttivo**: cestino e preferiti passano dalle conferme di sistema di Android.

## Scelte tecniche e modelli

| Compito | Fase 1 | Prossime fasi |
|---|---|---|
| Scene/oggetti | Google ML Kit Image Labeling (incluso nell'APK, offline) | CLIP multilingue (es. MobileCLIP/SigLIP) su NPU Hexagon via LiteRT/QNN per la ricerca "per immagini" |
| Comprensione frasi | Analizzatore italiano a regole (< 1 ms) | Piccolo LLM locale (es. Gemma 1–2B quantizzato) per frasi complesse e comandi |
| Luoghi | GeoNames città > 15.000 ab. (CC BY 4.0), incluso | — |
| Volti | Fase 2: ML Kit Face Detection + FaceNet 128-d (TFLite, scaricato in compilazione e incluso nell'APK) | Modello più leggero su NPU |
| Testo/scontrini | — | Fase 3: OCR on-device |

## Sviluppo

- Compilazione automatica su GitHub Actions a ogni push:
  1. **test automatici** (parser italiano, vocabolario, metriche di qualità, database reale con Robolectric);
  2. **APK firmato** con R8 (uno per il telefono arm64, uno x86_64 per l'emulatore);
  3. **test sull'emulatore Android**: carica foto e video di prova (con data e GPS), percorre la procedura guidata,
     prova le ricerche in italiano, il visualizzatore, il lettore video e "Apri con", salva gli screenshot e
     controlla che l'app non vada mai in crash;
  4. solo se tutto passa, pubblica la **Release** con l'APK e le schermate.
- Test locali: `./gradlew :domain:test testDebugUnitTest`
- Foto di prova per l'emulatore: `tools/smoke/fixtures` (rigenerabili con `tools/smoke/make_fixtures.py`).
- Qualità del riconoscimento sulle foto vere: Impostazioni → Avanzate → **Banco qualità**.
- Dati geografici: `python3 tools/build_geodata.py` (eseguito automaticamente in CI).
- La chiave in `keystore/` è una chiave **di sviluppo** usata solo per permettere gli aggiornamenti dell'APK di prova.
