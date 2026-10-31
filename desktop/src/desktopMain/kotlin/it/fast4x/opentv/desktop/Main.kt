package it.fast4x.opentv.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.ItalianPlaylistStrings
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.store.UserPrefsStore
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * Dipendenze condivise del desktop: stesse classi del core usate dall'app
 * Android, con le "colla" desktop (percorsi utente, lettura file locale).
 */
class DesktopDependencies(baseDir: File) {

    val prefs: UserPrefsStore = UserPrefsStore(File(baseDir, "opentv_prefs.preferences_pb"))

    val playlistRepository: PlaylistRepository = PlaylistRepository(
        prefs = prefs,
        filesDir = baseDir,
        // Su desktop l'id sorgente è il percorso del file importato.
        localPlaylistReader = { sourceId ->
            runCatching { File(sourceId).readText() }.getOrNull()
        },
        strings = ItalianPlaylistStrings,
    )

    val epgRepository: EpgRepository = EpgRepository()
}

fun main() {
    // I menu a tendina (timer di spegnimento, tracce audio, picker paesi)
    // devono apparire SOPRA il video. La superficie VLC è un componente
    // Swing/AWT heavyweight che copre SEMPRE il canvas Compose (il flag
    // d'integrazione "interop blending" che invertirebbe la situazione non
    // è attivo), e con il default di Compose Multiplatform le Popup sono
    // disegnate DENTRO il canvas stesso: il risultato è che i menu del
    // player si aprivano DIETRO il video, invisibili e non cliccabili.
    // Con "WINDOW" ogni popup diventa una finestra nativa separata
    // (JDialog posseduta dalla finestra principale), sempre in primo piano
    // rispetto a qualunque componente heavyweight.
    // Deve essere impostato PRIMA della prima finestra: Compose legge la
    // proprietà una volta sola (lazy) alla creazione del container. Un
    // eventuale valore esplicito dell'utente (-Dcompose.layers.type=...)
    // viene rispettato e non sovrascritto.
    if (System.getProperty("compose.layers.type") == null) {
        System.setProperty("compose.layers.type", "WINDOW")
    }

    // Prima cura dello "schermo bianco" (diagnosi completa in SwingBlackout):
    // i pannelli Swing creati IN SEGUITO da CMP/vlcj (gruppi interop, superfici
    // video) nascono con lo sfondo chiaro del Look&Feel; con il default
    // Panel nero nascono gia' neri e la cancellazione nativa dei Canvas
    // heavyweight cancella in nero e non in bianco. Deve precedere la
    // creazione della finestra.
    runCatching {
        javax.swing.UIManager.put("Panel.background", java.awt.Color.BLACK)
    }

    // Tutto lo stato dell'app desktop vive in ~/.opentv (playlist cache,
    // DataStore preferiti/impostazioni): analogo a filesDir su Android.
    val baseDir = File(System.getProperty("user.home"), ".opentv").apply { mkdirs() }
    val deps = DesktopDependencies(baseDir)

    // Prepara il percorso delle librerie VLC PRIMA del primo uso di vlcj:
    // VlcLocator cerca percorsi noti, installazioni per-utente e l'override
    // OPENTV_VLC_PATH / -Dopentv.vlc.path.
    if (VlcLocator.prepareNativeLibraryPath() == null) {
        println(
            "[OpenTV] libVLC non trovata nei percorsi standard: la riproduzione " +
                "richiede VLC installato oppure la variabile d'ambiente " +
                "OPENTV_VLC_PATH puntata alla cartella di VLC.",
        )
    }

    // Schermo intero borderless condiviso tra Main (title bar) e il player
    // (toggle tasto F/pulsante, cursore nascosto, auto-hide controlli): vedi
    // DesktopFullscreen.kt per perche' NON si usa WindowPlacement.Fullscreen
    // (attiva il fullscreen esclusivo AWT di Windows, causa della regressione
    // "sfondo bianco senza video" alla nascondita dei controlli).
    val fullscreen = DesktopFullscreenController()

    application {
        // Stato finestra per dimensione/posizione e per la title bar custom
        // (massimizza/ripristino in modalita' finestra).
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "OpenTV",
            state = windowState,
            // Finestra creata SENZA decorazioni native, fin dal primo istante:
            // in modalita' finestra disegniamo la nostra barra (OpenTvWindowTitleBar,
            // stessa identita' visiva dell'app) e a schermo intero non esiste
            // nessuna title bar di sistema da nascondere. Cosi' il fullscreen e'
            // davvero a bordo a bordo e non restano mai strisce bianche o residui
            // della barra nella parte alta della finestra (gli insets AWT restano
            // sempre zero, senza interventi sugli stili Win32 a finestra viva).
            undecorated = true,
        ) {
            // Massimizzazione dentro l'area di lavoro (senza questo vincolo una
            // finestra senza decorazioni maximizzata coprirebbe la taskbar).
            DisposableEffect(Unit) {
                runCatching {
                    window.maximizedBounds =
                        GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                }
                // Sfondo AWT della finestra NERO: se per un istante nessuna
                // superficie ridipinge (reflow hide/show dei controlli a
                // schermo intero) l'utente vede nero coerente col tema, non
                // il bianco di default AWT/Swing (era la "finestra bianca"
                // riportata in fullscreen quando i controlli spariscono).
                runCatching {
                    val black = java.awt.Color.BLACK
                    window.background = black
                    window.rootPane?.background = black
                    window.contentPane?.background = black
                }
                // Primo ponte nero sull'albero AWT gia' esistente (rootPane,
                // pannelli CMP, Canvas skiko): idempotente, viene rieseguito a
                // ogni recovery (vedi SwingBlackout).
                runCatching { SwingBlackout.apply(window) }
                onDispose { }
            }

            Column(Modifier.fillMaxSize()) {
                // Barra del titolo custom: solo fuori dallo schermo intero (a
                // schermo intero i controlli del player si nascondono da soli
                // e la finestra e' sempre in primo piano: vedi DesktopFullscreen).
                if (!fullscreen.active) {
                    OpenTvWindowTitleBar(windowState = windowState, onClose = ::exitApplication)
                }
                OpenTvDesktopTheme {
                    // `window` è il ComposeWindow reale: il player lo usa per il
                    // cursore nascosto quando i controlli si nascondono a schermo
                    // intero; il toggle schermo intero passa dal controller.
                    OpenTvDesktopApp(deps, fullscreen, window)
                }
            }
        }
    }
}
