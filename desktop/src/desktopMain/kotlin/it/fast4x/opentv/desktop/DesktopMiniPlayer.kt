package it.fast4x.opentv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment

/**
 * Mini-player desktop (feature 11): tornando alla Home (o alla guida) mentre
 * si guarda un canale, il video prosegue in una finestretta UNDECORATED
 * sempre in primo piano nell'angolo in basso a destra — si guarda la partita
 * mentre si sfoglia la guida.
 *
 * PERCHE' un SECONDO player invece di spostare la superficie esistente: il
 * player pieno vive nella composizione della schermata Player (il suo stato
 * viene rilasciato all'uscita, [DesktopPlayerState.dispose]); spostare
 * fisicamente il componente AWT vlcj tra finestre non è supportato da Swing
 * (l'handle nativo verrebbe ricreato e il video ucciso). La scelta
 * pragmatica: il mini apre lo stesso canale con un proprio
 * [DesktopPlayerState] (stessa risoluzione relinker, stesse intestazioni,
 * stessa riconnessione automatica feature 7). Il cambio comporta una breve
 * riapertura del flusso (~1s), accettabile per la TV in diretta.
 *
 * UNA SOLA RIPRODUZIONE: il navigatore chiude il mini ogni volta che si apre
 * il player pieno ([DesktopNavigator.openPlayer]); il pulsante "mini" del
 * player chiude il player pieno e apre il mini. Mai due audio sovrapposti.
 */
class DesktopMiniPlayerController {

    /** True mentre la finestretta è aperta. */
    var active by mutableStateOf(false)
        private set

    /** Canale che la finestretta sta riproducendo (id playlist). */
    var channelId by mutableStateOf<String?>(null)
        private set

    /** Apre la finestretta sul canale indicato (sostituisce un eventuale mini già aperto). */
    fun enter(channelId: String) {
        this.channelId = channelId
        active = true
    }

    /** Chiude la finestretta (e arresta la sua riproduzione). */
    fun dismiss() {
        active = false
        channelId = null
    }
}

/**
 * Finestretta del mini-player: si compone a livello app (fuori dalle
 * schermate) e vive finché il controller è attivo. La striscia in alto è
 * trascinabile (stesso [WindowDragHandler] della title bar principale),
 * mostra il canale e i pulsanti espandi (riapre il player pieno) e chiudi.
 */
@Composable
fun DesktopMiniPlayerWindow(
    deps: DesktopDependencies,
    controller: DesktopMiniPlayerController,
    navigator: DesktopNavigator,
) {
    val channelId = controller.channelId ?: return
    if (!controller.active) return

    val windowState = rememberWindowState(width = 352.dp, height = 234.dp)

    Window(
        onCloseRequest = { controller.dismiss() },
        title = "OpenTV — mini",
        state = windowState,
        undecorated = true,
        resizable = false,
    ) {
        // Sempre in primo piano (è il senso del mini-player) e, al primo
        // montaggio, ancorata in basso a destra dell'area utile del desktop
        // (sopra la taskbar). runCatching: se la geometria dello schermo non
        // è disponibile la finestra resta dove il sistema la mette.
        DisposableEffect(Unit) {
            window.isAlwaysOnTop = true
            runCatching {
                val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .maximumWindowBounds
                window.setLocation(
                    bounds.x + bounds.width - window.width - MINI_MARGIN_PX,
                    bounds.y + bounds.height - window.height - MINI_MARGIN_PX,
                )
            }
            onDispose {
                window.isAlwaysOnTop = false
            }
        }

        val scope = rememberCoroutineScope()
        // Il suo [DesktopPlayerState]: risolve il canale dalla playlist,
        // applica relinker/intestazioni e pilotà libVLC con la riconnessione
        // automatica della feature 7. Allo smontaggio della finestra il
        // player viene rilasciato (niente processi VLC orfani).
        val state = remember(channelId) { DesktopPlayerState(deps, scope, channelId) }
        DisposableEffect(state) {
            onDispose { state.dispose() }
        }

        val channel by state.currentChannel.collectAsState()
        val status by state.playback.collectAsState()
        val isError = status is PlayerStatus.Error

        Column(Modifier.fillMaxSize().background(AppBackground)) {
            // ---- Striscia: trascinamento + titolo + espandi/chiudi ---------
            val dragHandler = remember { WindowDragHandler(window) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(AppSurface)
                    .pointerInput(dragHandler) {
                        detectTapGestures(
                            onPress = {
                                dragHandler.begin(windowState)
                                try {
                                    awaitRelease()
                                } finally {
                                    dragHandler.end()
                                }
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        // Espandi: chiude il mini (lo fa openPlayer) e riapre
                        // il player pieno sullo stesso canale.
                        val id = controller.channelId ?: return@IconButton
                        navigator.openPlayer(id)
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Apri nel player",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = channel?.name.orEmpty().ifEmpty { "OpenTV" },
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (status is PlayerStatus.Buffering || status is PlayerStatus.Reconnecting) {
                    CircularProgressIndicator(
                        color = AppPrimary,
                        modifier = Modifier.size(14.dp).padding(end = 6.dp),
                        strokeWidth = 2.dp,
                    )
                }
                IconButton(
                    onClick = { controller.dismiss() },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Chiudi mini-player",
                        tint = AppTextSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // ---- Video (o errore): stessa regola del player pieno ----------
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                // In errore la superficie esce dalla composizione: essendo un
                // componente AWT heavyweight coprirebbe il messaggio (vedi
                // player pieno). Il doppio click sul video... non è gestibile
                // qui (la superficie nativa assorbe i click): per tornare al
                // player pieno c'è il pulsante della striscia.
                if (!isError) {
                    state.videoComponent()?.let { video ->
                        androidx.compose.ui.awt.SwingPanel<java.awt.Container>(
                            background = Color.Black,
                            factory = { video },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                (status as? PlayerStatus.Error)?.let { error ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xD9070A10)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                null,
                                tint = AppError,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = error.message,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                androidx.compose.material3.TextButton(onClick = state::retry) {
                                    Text("Riprova", style = MaterialTheme.typography.labelMedium)
                                }
                                androidx.compose.material3.TextButton(onClick = { controller.dismiss() }) {
                                    Text("Chiudi", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MINI_MARGIN_PX = 24
