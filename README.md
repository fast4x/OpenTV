# OpenTV

<div align="center">

**Player IPTV per Android e Android TV — Jetpack Compose**

Kotlin · Compose Multi-Form-Factor · Media3/ExoPlayer · MVVM

</div>

---

## Cos'è

OpenTV è un'applicazione Android nativa per guardare i canali TV via IPTV, con un
focus sui **canali italiani**. All'avvio recupera la playlist pubblica italiana
del progetto [iptv-org](https://iptv-org.github.io/), la mostra in un'interfaccia
in stile "Dark TV" (ispirata a Netflix e al launcher Leanback) e riproduce gli
stream con **Media3/ExoPlayer**.

L'app funziona su **telefono/tablet** e su **Android TV / TV box**: la stessa UI
Compose adatta focus, dimensioni e controlli al dispositivo rilevato
(D-pad vs touch).

> ⚠️ **Nota legale**: OpenTV è un *riproduttore* generico. Non fornisce,
> ospita né seleziona alcun contenuto: gli stream provengono da playlist
> pubbliche di terze parti. Utilizzala solo con canali la cui fruizione è
> legale nel tuo paese (es. canali free-to-air).

## Funzionalità

- **Canali per paese (sorgente automatica)** — all'avvio l'app rileva la
  nazione del dispositivo (rete mobile, poi SIM, poi impostazioni locali di
  sistema) e carica la playlist iptv-org curata di quel paese, con la guida
  TV dello stesso paese derivata in automatico e mirror grezzo come riserva.
  Ideale per usi internazionali: ogni installazione vede i canali locali.
  Dalla Home un **chip con bandierina** permette di cambiare nazione al
  volo (la scelta si applica subito ed è persistita: resta anche dopo il
  riavvio, mentre il default "Automatica" segue il paese del dispositivo);
  nelle impostazioni c'è il selettore completo (paesi con catalogo
  iptv-org, ordinati per nome nella lingua di sistema) oppure è possibile
  disattivare la sorgente automatica per usare un
  **URL personalizzato** (o importare una lista da file, che mantiene la
  priorità). Su desktop il paese segue il locale del sistema.
  Le bandierine sono emoji su Android e **PNG incorporate** su desktop
  (fonte flagcdn.com): Windows non dispone dei glifi emoji per le
  bandiere nel font di sistema e mostrerebbe la sigla al loro posto.
- **Recupero playlist IPTV** — download M3U/M3U8 con parser dedicato
  (`tvg-id`, `tvg-logo`, `group-title`), cache locale con TTL di 6 ore e
  **fallback offline** sull'ultima cache valida.
- **Riproduzione** — Media3/ExoPlayer con supporto HLS/DASH/MPEG-TS,
  redirect cross-protocol, riprove progressivo per i flussi ambigui,
  audio focus e gestione "becoming noisy" (pause automatiche quando si
  staccano le cuffie).
- **Preferiti per sorgente** — tap prolungato su una card (o stella nel
  player) e sezione dedicata; persistenza con Jetpack DataStore. **Ogni
  lista ha i suoi preferiti**: quelli della sorgente automatica (iptv-org,
  bucket storico) e quelli di ciascun profilo di sorgente sono elenchi
  indipendenti — passando da un profilo all'altro la riga Preferiti (col
  nome della lista attiva nel titolo) mostra solo i canali marcati su
  quella sorgente, senza mai mescolare o perdere gli altri.
- **Ultimi visti** — cronologia automatica dei canali guardati, in cima alla Home.
- **Ricerca live** — filtro per nome insensibile a maiuscole/accenti.
- **Filtro per gruppo (genere)** — riga di chip sotto la barra superiore con
  i `group-title` della playlist (es. News, Movies, Sports, Music): un tap
  mostra solo i canali del genere scelto, con il numero di canali per gruppo.
  Le categorie multiple ("Movies;Series") contano il canale in ogni genere;
  i canali senza gruppo finiscono in "Senza categoria". Combinabile con la
  ricerca; "Tutti" rimuove il filtro. **La scelta persiste** tra le sessioni
  (DataStore) e sulla card di ogni canale compare un badge col suo genere:
  su telefono e desktop il badge è **cliccabile** (un tap filtra quel genere
  e la riga di chip si allinea da sola al gruppo scelto), su Android TV resta
  decorativo per non aggiungere target di focus alla navigazione D-pad.
- **EPG automatica (XMLTV)** — con playlist iptv-org carica da sola la guida
  per paese di iptv-epg.org (oppure URL manuale, anche `.gz`); le playlist
  che dichiarano la **propria guida nell'header** (`x-tvg-url`, anche come
  lista di URL) la ottengono senza configurare nulla — mostra
  *in onda adesso* e *a seguire* sulle card e nel player, più la
  **programmazione completa** del canale (finestra -1h → +24h, raggruppata
  per giorno) nel player Android (bottom sheet) e desktop (pannello laterale).
- **Guida TV a griglia** — schermata dedicata (icona TV in Home): canali sulle
  righe e programmi sulla linea del tempo orizzontale, finestra di 4 ore che
  parte dall'ora piena corrente, il programma **in onda** è evidenziato col
  bordo primario e una barra di avanzamento viva; un tap su qualunque
  programma (o canale) apre direttamente il player. Lo scroll orizzontale è
  condiviso e allineato fra tutte le righe e il righello orario, con il
  marker "adesso".
- **Riconnessione automatica dello stream** — se un flusso cade (hiccup CDN,
  timeout di rete) il player riprova da solo con backoff crescente (1.5s,
  4s, 8s — fino a 3 tentativi) mostrando "Riconnessione… (tentativo N di 3)"
  invece di lasciare la schermata d'errore; l'immagine resta a schermo e il
  ritorno del segnale è immediato. Su Android la diagnosi del sorgente
  (geo-blocco, link morto, segnaposto…) compare solo dopo l'ultimo tentativo.
- **Comandi di sistema (Android)** — media session su ExoPlayer: play/pausa
  dal lockscreen e dai tasti degli auricolari Bluetooth, "traccia
  successiva/precedente" = canale successivo/precedente, notifica persistente
  con canale e programma in onda e pulsanti (precedente, play/pausa,
  successivo). Richiede il permesso notifiche su Android 13+ (richiesto
  all'apertura del player): negandolo sparisce solo la notifica, i comandi
  restano.
- **Precedente/Successivo** — cambio canale direttamente dal player, come sul
  telecomando.
- **Sottotitoli** — menu di selezione tracce sottotitoli gemello del menu
  tracce audio, su Android (bottom sheet, tracce di testo ExoPlayer) e
  desktop (menu a tendina, tracce SPU di libVLC): voce "Disattivati"
  sempre disponibile, traccia attiva evidenziata, icona CC nella barra che
  si accende quando i sottotitoli sono attivi. I flussi senza tracce di
  testo non mostrano il pulsante; al cambio canale la scelta torna automatica.
- **Promemoria programmi ("avvisami quando inizia")** — campanello su ogni
  programma futuro nella programmazione del canale (player Android e
  desktop): il promemoria persiste nel DataStore e, all'inizio del
  programma, arriva una notifica di sistema (canale dedicato su Android,
  fumetto della tray su desktop). Le voci dei programmi finiti da più di due
  ore (es. dispositivo spento) vengono pulite in silenzio; funziona anche
  con l'app in background (Android) o in Home/guida (desktop).
- **Zapping numerico (desktop)** — nel player digiti le cifre del canale
  ("103") e al volo si apre il canale 103 della playlist: le cifre
  compaiono in un chip nella barra, il canale parte alla scadenza del
  buffer (1,2s) o premendo Invio, il backspace corregge.
- **Statistiche stream (desktop)** — pannello stile VLC nel player (tasto I
  o pulsante ⓘ): codec e risoluzione video, fps, codec e bitrate audio,
  bitrate di demux e perdite segnalate da libVLC (fotogrammi, buffer
  audio, discontinuità, pacchetti corrotti): utile per capire perché un
  canale va a scatti. I dati vivono nella barra, mai sopra il video (la
  superficie è un componente nativo che coprirebbe ogni overlay).
- **Mini-player (desktop)** — il pulsante PiP del player sposta la visione
  in una finestretta sempre in primo piano nell'angolo in basso a destra:
  si guarda la partita mentre si sfoglia la guida o la Home. Striscia
  trascinabile con titolo, pulsante espandi (riapre il player pieno) e
  chiusura; aprendo un canale la finestretta si chiude da sola (una sola
  riproduzione attiva).
- **Android TV** — launcher Leanback, banner TV, navigazione D-pad con
  scala + glow sul focus, schermo sempre attivo in riproduzione.
- **Telefono** — landscape forzato + modalità immersiva a schermo intero.
- **Impostazioni** — URL playlist e URL EPG personalizzabili, ripristino
  predefinito, stato di caricamento in tempo reale.
- **Esporta/Importa lista** — backup della lista canali su file M3U e
  importazione da file locale tramite il selettore di sistema (SAF); la
  lista importata resta la sorgente attiva (con fallback offline) finché
  non si torna a un URL remoto.
- **Esporta/Importa profilo** — ogni profilo di sorgente si esporta in un
  unico pacchetto `.opentv` che contiene il profilo (nome, URL playlist,
  URL guida) e i canali della sua playlist, guida incorporata compresa;
  l'importazione è generica e riconosce il pacchetto dal contenuto: il
  profilo viene creato (o, con un dialog di conferma, sovrascritto se
  esiste già con lo stesso nome), attivato, e i suoi canali diventano la
  lista caricata. Le playlist importate non dichiarano più canali con
  identificatori o URL duplicati: le liste canali non possono più andare
  in crash per chiavi doppie (fix36).

## Screenshot

| Home (telefono) | Home (Android TV) | Player |
|---|---|---|
| _aggiungi screenshot_ | _aggiungi screenshot_ | _aggiungi screenshot_ |

## Stack tecnico

| Area | Scelta |
|---|---|
| Linguaggio | Kotlin 2.2 (Kotlin Multiplatform per il core) |
| UI Android | Jetpack Compose (BOM 2026.09) · Material 3 |
| UI desktop | Compose Multiplatform 1.9 (target JVM desktop) |
| Player Android | Media3 / ExoPlayer 1.11 (+ modulo HLS) |
| Player desktop | libVLC via vlcj 4.8 |
| Architettura | MVVM + Repository, Coroutines/Flow, DI manuale |
| Persistenza | Jetpack DataStore (preferences-core, multiplatform) |
| Rete | OkHttp 5.5 (condiviso) |
| Immagini | Coil 2.7 (Android) · loader skiko (desktop) |
| Navigazione | Navigation-Compose |
| Android | Min SDK 24 · Target/Compile SDK 36/37 · Gradle 9.8 · AGP 9.4 |

## Struttura del progetto

```
OpenTV/
├── core/                                 # Kotlin Multiplatform (Android + JVM desktop)
│   └── src/commonMain/kotlin/it/fast4x/opentv/
│       ├── Config.kt                     # costanti (URL default, TTL, timing)
│       ├── data/
│       │   ├── model/Models.kt           # Channel, Programme, PlaylistState, PlaybackEngine…
│       │   ├── remote/
│       │   │   ├── M3UParser.kt          # parser M3U/M3U8 + metadati tvg-*
│       │   │   ├── M3UWriter.kt          # export/import round-trip
│       │   │   ├── StreamResolver.kt     # risoluzione relinker RAI (patch RAI/Mediaset)
│       │   │   ├── StreamProbe.kt        # diagnosi sorgenti (geo-blocco, link morti…)
│       │   │   ├── StreamTypes.kt        # inferenza mimeType dall'URL
│       │   │   ├── XmltvParser.kt        # parser EPG XMLTV (XmlPull, Android+JVM)
│       │   │   ├── PlaylistFetcher.kt    # OkHttp + gzip + redirect
│       │   │   └── ChannelEnricher.kt    # loghi/categorie da playlist per paese
│       │   ├── repository/
│       │   │   ├── PlaylistRepository.kt # cache, fallback offline, import/export
│       │   │   ├── PlaylistStrings.kt    # messaggi utente (iniettabili/localizzabili)
│       │   │   ├── LocalPlaylistReader.kt# astrazione sorgente locale (SAF / file)
│       │   │   └── EpgRepository.kt      # guida TV in memoria (now / next)
│       │   └── store/UserPrefsStore.kt   # DataStore multiplatform: preferiti, recenti, settings
│       └── util/TimeUtils.kt
├── app/                                  # applicazione Android (telefono + Android TV)
│   └── src/main/java/it/fast4x/opentv/
│       ├── OpenTvApplication.kt          # DI: inietta in :core filesDir, SAF, stringhe
│       ├── MainActivity.kt               # entry point unico mobile + TV
│       ├── util/                         # PlaylistIo (SAF), DeviceUtils
│       └── ui/                           # theme, navigation, screens (home/player/settings)
├── desktop/                              # app desktop (Compose Multiplatform + vlcj)
│   └── src/desktopMain/kotlin/it/fast4x/opentv/desktop/
│       ├── Main.kt                       # finestra + DI desktop (~/.opentv)
│       ├── DesktopStates.kt              # stati Home/Player (stessa logica degli VM Android)
│       ├── VlcLocator.kt                 # individuazione librerie libVLC (percorsi/override)
│       ├── VlcPlayer.kt                  # libVLC: superficie video + opzioni per-canale
│       └── OpenTvDesktopApp.kt           # Home, Player, Impostazioni (import/export file)
├── build.gradle.kts · settings.gradle.kts · gradle/libs.versions.toml
└── gradlew (+ wrapper 9.8.1)
```

## Requisiti

- **Android Studio Ladybug o successivo** (consigliato)
- JDK 17+ (incluso in Android Studio)
- Min SDK 24 → Android 7.0+ (compatibile con la maggior parte dei TV box)
- Per il **desktop**: JDK 17+ e le librerie **libVLC** installate sul sistema
  (bastano quelle di [VLC Media Player](https://www.videolan.org); su Windows usare la versione a 64 bit)

## Compilare

### Con Android Studio

1. `File → Open…` e seleziona la cartella `OpenTV`.
2. Attendi la sincronizzazione Gradle (scarica automaticamente Gradle 8.9 e le dipendenze).
3. `Build → Make Project` oppure:
   ```bash
   ./gradlew assembleDebug
   ```

### Da riga di comando

```bash
# APK debug
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# APK release (minificato; firma con il tuo keystore)
./gradlew assembleRelease
```

### App desktop (Windows / Linux / macOS)

Il modulo `:desktop` è un'app Compose Multiplatform che riusa il core:

```bash
# Avvio in sviluppo (richiede VLC installato sul sistema per libVLC)
./gradlew :desktop:run

# Distribuzione: installer/launcher nativi (MSI/EXE su Windows, DEB su Linux)
./gradlew :desktop:packageDistributionForCurrentOS
# → desktop/build/compose/binaries/main/
```

**Scorciatoie da tastiera e mouse nel player desktop**:

| Tasto | Azione |
|-------|--------|
| `Spazio` | play / pausa |
| `←` / `→` | canale precedente / successivo |
| `↑` / `↓` | volume (indicatore temporaneo nella barra inferiore) |
| Rotella del mouse sul video | volume (un cric = ±10, indicatore temporaneo nella barra inferiore; con il puntatore fuori dal video la rotella continua a scorrere i contenuti) |
| `F` | schermo intero / ripristina finestra |
| `Esc` | esce dallo schermo intero (se attivo), altrimenti torna alla lista dei canali |

Lo **schermo intero** si attiva anche dal **pulsante in barra** del player
(icona che alterna entra/esci in base allo stato della finestra); uscendo
dal canale la finestra torna automaticamente in modalità normale.

Lo schermo intero è **davvero a bordo a bordo**: la finestra desktop è
senza decorazioni di sistema e la barra del titolo (riduci/ingrandisci/
chiudi, trascinabile, doppio click = ingrandisci) è disegnata dall'app
nello stesso stile scuro della UI. È implementato in modalità **borderless**
(i bounds della finestra vengono portati su quelli dello schermo, senza il
fullscreen "esclusivo" di AWT che su Windows interferisce con la composizione
del video): la resa è identica ma la transizione hide/show dei controlli è
la stessa della modalità finestra, senza sfarfallii o sfondi bianchi. Le
barre del player **spariscono dopo 4 secondi di inattività in ogni
modalità** (finestra e schermo intero); a schermo intero viene nascosto
anche il cursore; il primo movimento del mouse o un tasto qualsiasi le rende
di nuovo visibili. Le barre vivono in **slot a dimensione fissa** fuori
dall'area video: nasconderle è un effetto puramente grafico (dissolvenza),
la superficie video **non viene mai ridimensionata** né coperta (è la cura
dello "sfondo bianco" a schermo intero: il ridimensionamento ripetuto del
componente video nativo alla transizione era la causa del difetto). Le
zone lasciate libere dalle barre nascoste restano nere, coerenti col tema.
In modalità finestra la barra del titolo resta sempre disponibile per
trascinare e chiudere la finestra.

**Pacchetto con VLC incluso** (da distribuire a PC senza VLC installato):

```bash
# Windows: installer MSI + EXE con libVLC dentro (scarica WiX una volta)
.\gradlew.bat :desktop:packageDistributionForCurrentOS -Popentv.bundleVlc=true

# Alternativa senza WiX: cartella portabile da zippare
.\gradlew.bat :desktop:createDistributable -Popentv.bundleVlc=true
```

Il task `bundleVlc` copia l'installazione VLC locale (trovata via
`OPENTV_VLC_PATH` o in `C:\Program Files\VideoLAN\VLC`) dentro le risorse
Windows dell'immagine; a runtime viene trovata automaticamente in
`<app>/resources/vlc`. Attenzione: il pacchetto cresce di ~300 MB.

Lo stato desktop vive in `~/.opentv/` (cache playlist, DataStore con
preferiti/impostazioni — stesso formato dell'app Android). Il player usa
libVLC con le stesse intestazioni per-canale della patch RAI/Mediaset
(relinker risolto, Referer/Origin per network).

**Librerie VLC sul desktop**: vlcj non include libVLC, che viene cercata
nell'ordine seguente (il primo trovato vince):

1. proprietà di sistema `-Dopentv.vlc.path=<cartella di VLC>`
2. variabile d'ambiente `OPENTV_VLC_PATH`
3. bundle incluso nell'app pacchettizzata: `<cartella app>/vlc` oppure
   `<cartella app>/resources/vlc` (task Gradle `bundleVlc`)
4. percorsi di installazione noti (Windows: `Program Files` e installazioni
   "per utente" in `%LOCALAPPDATA%`; macOS: `/Applications/VLC.app`;
   Linux: librerie di sistema e VLC snap)

Se non viene trovata, la riproduzione mostra un messaggio con le istruzioni
invece di crashare: installa VLC (64 bit su Windows) oppure imposta
`OPENTV_VLC_PATH` alla cartella che contiene `libvlc.dll` / `libvlc.so` /
`libvlc.dylib` e riavvia.

> Il repository include il Gradle Wrapper (Gradle 9.8.1):
> non serve avere Gradle installato localmente.

### Installare su un dispositivo

```bash
# Telefono / tablet
adb install app/build/outputs/apk/debug/app-debug.apk

# Android TV (il device deve essere in modalità sviluppatore + debug USB)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Su Android TV l'app compare nel launcher (riga "Apps") grazie al filtro
`LEANBACK_LAUNCHER`; su telefono nell'elenco app normale.

## Configurazione

### Playlist canali

- **Predefinita**: `https://iptv-org.github.io/iptv/countries/it.m3u` (canali
  italiani della community iptv-org, aggiornata quotidianamente).
- **Personalizzata**: *Impostazioni → URL playlist (M3U)* per usare una tua
  playlist (URL diretto a file `.m3u`/`.m3u8`, anche serviti in gzip).

### Guida TV (EPG)

1. **Automatica (predefinita)**: con una playlist iptv-org (es. quella
   italiana di default) l'app carica da sola la guida **per paese** di
   [iptv-epg.org](https://iptv-epg.org) — il servizio community che pubblica
   gli EPG dell'ecosistema iptv-org (stessa toolchain del repo
   [iptv-org/epg](https://github.com/iptv-org/epg) e stesso database canali).
   Gli id canale coincidono con i `tvg-id` della playlist (es. `Rai1.it`);
   le varianti con suffisso qualità (`Rai1.it@SD`, `Rai1.it@HD`, `@IT`…)
   vengono ricondotte all'id base, quindi il match funziona comunque.
   Un solo download copre tutta la lista, senza configurare nulla.
2. **Incorporata nella playlist** (fix35): molte liste (es. FreeIPTV)
   dichiarano la propria guida nell'header `#EXTM3U x-tvg-url="…"` (o i
   sinonimi `url-tvg` / `tvg-url`), talvolta come **lista di URL separati da
   virgole** (FreeIPTV ne elenca oltre cento, una per paese). OpenTV la
   estrae, punteggia i candidati sul suffisso nazionale dei `tvg-id`
   ("Rai1.it" → "it" → `epg_ripper_IT1.xml.gz`) e carica fino a **3 guide
   fuse insieme** senza che l'utente debba impostare nulla. È usata quando
   non c'è una guida esplicita (profilo o campo manuale) e ha la precedenza
   sulla risoluzione automatica; l'export M3U la riscrive nell'header, così
   una lista esportata riporta con sé anche la guida.
3. *Impostazioni → URL guida TV XMLTV*: per una guida **manuale** (o
   self-hosted col tool iptv-org/epg: `npm run grab` → servila e incolla
   l'URL). L'URL manuale ha sempre priorità sull'automatico. Il file viene
   scaricato in streaming (anche `.xml.gz`), decompresso e filtrato su una
   finestra da **-1h a +24h** rispetto ad adesso.
4. I programmi vengono associati ai canali tramite `tvg-id` dell'M3U
   (= attributo `channel` dell'XMLTV, ignorando l'eventuale suffisso `@…`).
   Se la guida usa convenzioni di id diverse, il match prosegue per **nome
   normalizzato** (display-name della guida vs nome/tvg-name del canale:
   "Rai 1" = "RAI 1" = "Rai1") e per **id normalizzato** ("La5.it" =
   "La.5.it"): dove il match esiste, le card mostrano il programma in onda
   e il player mostra *adesso / a seguire*. Nel player il pulsante
   **Programmazione** apre la lista completa dei programmi della finestra
   **-1h → +24h** (bottom sheet su Android, pannello laterale su desktop),
   raggruppata per giorno con il programma in onda evidenziato e portato in
   vista. La guida copre i canali per cui esiste una fonte pubblica
   (emittenti nazionali e principali: Rai, Mediaset, Discovery, ecc.):
   molte TV locali/web non hanno EPG pubblicato e restano senza indicazione
   (con le guide incorporate il limite è del provider della guida stessa).
   Lo stato della guida (in caricamento / programmi in memoria / errori) è
   visibile nella sezione Impostazioni.

## Note Android TV

- Navigazione **D-pad** completa: frecce per muoversi tra card e righe,
  tasto centrale per aprire/interagire, `back` per uscire.
- Nel player **qualunque tasto del telecomando** (OK, frecce D-pad, tasti
  media) fa comparire i controlli, compreso il pulsante **preferito**; il
  focus parte da play/pausa e ogni pressione rinvia di 4 secondi l'auto-
  nascondimento. CH+/CH- (o PageUp/PageDown) passano direttamente al canale
  successivo/precedente, i tasti play/pausa del telecomando funzionano
  sempre, e `back` prima nasconde i controlli poi esce dal player.
- Banner TV (`tv_banner`) e icone adattive incluse; icone PNG legacy per API 24–25.

## Risoluzione problemi

| Problema | Causa probabile | Soluzione |
|---|---|---|
| "Rete non raggiungibile" | nessuna connessione / DNS | controlla la rete; in assenza di rete l'app usa l'ultima cache |
| Alcuni canali non partono | stream morto o codec non supportato dal device | passa al canale successivo; prova un altro URL playlist |
| Errore HTTP 403 | il server filtra lo User-Agent | l'app invia uno UA browser; alcuni server richiedono referer specifici |
| EPG vuoto | URL XMLTV errato, `tvg-id` non corrispondenti o canali senza guida pubblicata | usa una guida con gli stessi `tvg-id` della playlist (con iptv-org: quella automatica o i pulsanti per paese); le TV locali/web spesso non hanno EPG disponibile |
| Video senza audio in background | audio focus ceduto a un'altra app | comportamento atteso di Android |

## Roadmap

- [ ] Supporto playlist multiple con selettori
- [x] EPG completo a griglia (timeline) con XMLTV esteso — guida a griglia
      nella Home (feature 8)
- [x] Promemoria programmi ("avvisami quando inizia") con notifica di sistema
- [ ] Pannello "canali FTA consigliati" precompilato
- [ ] Catch-up / timeshift dove dichiarato dall'M3U (`catchup-*`)
- [ ] Widget e scorciatoie per l'ultimo canale visto

## Licenza

Rilasciato con licenza [MIT](LICENSE).
I contenuti riprodotti appartengono ai rispettivi proprietari; la playlist
di default è fornita dal progetto [iptv-org](https://github.com/iptv-org/iptv).
