package it.fast4x.opentv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window

/**
 * Schermo intero "borderless" della finestra desktop.
 *
 * PERCHE' NON WindowPlacement.Fullscreen (diagnosi dello "sfondo bianco senza
 * video" in fullscreen alla nascondita dei controlli, verificata sui sorgenti
 * Compose Multiplatform 1.9.0 e skiko 0.9.22.2):
 *
 * In CMP 1.9.0 impostare WindowPlacement.Fullscreen NON massimizza la finestra:
 * passa per ComposeWindow -> ComposeContainer.fullscreen -> SkiaLayer ->
 * HardwareLayer -> skiko PlatformOperations.setFullscreen, che su Windows
 * attiva il FULLSCREEN ESCLUSIVO di AWT (GraphicsDevice.setFullScreenWindow).
 * In quella modalita' il sistema riscrive gli stili nativi della finestra
 * (WS_POPUP, bounds a schermo) e bypassa il compositing del desktop; il
 * "mixing" AWT tra componenti lightweight e il canvas heavyweight skiko
 * (da cui dipende la visibilita' del pannello video vlcj, che vive SOTTO il
 * canvas attraverso la regione di clip) non segue in modo affidabile i reflow
 * successivi: quando i controlli si nascondono la regione di clip del video si
 * allarga fino a coprire tutto lo schermo e l'area appena esposta non viene
 * ridipinta — resta lo sfondo bianco senza video finche' un evento non forza
 * un nuovo disegno. JetBrains stesso documenta il limite ("Changing fullscreen
 * probably will require recreate our layers" in ComposeContainer).
 *
 * LA CURA: non usare piu' il fullscreen esclusivo. La finestra e' gia' creata
 * senza decorazioni (undecorated = true in Main.kt), quindi lo schermo intero
 * si ottiene portando i SUOI bounds su quelli dello schermo corrente
 * ("borderless fullscreen", la tecnica dei player e dei giochi): la finestra
 * resta una normale top-level window, il DWM resta attivo e il percorso di
 * rendering/painting e' ESATTAMENTE quello della modalita' finestra, dove la
 * nascondita dei controlli funziona senza difetti. La resa visiva e' identica:
 * bordo a bordo, senza title bar (viva solo fuori dallo schermo intero) e
 * sopra la taskbar (isAlwaysOnTop durante lo schermo intero, come prima).
 *
 * [active] e' un mutableStateOf: le reazioni UI (title bar, cursore nascosto,
 * auto-hide controlli, ponte nero a ogni transizione) si agganciano qui, come
 * prima si agganciavano al placement della finestra.
 */
class DesktopFullscreenController {

    /** True mentre la finestra copre lo schermo intero. */
    var active by mutableStateOf(false)
        private set

    private var savedBounds: Rectangle? = null
    private var savedExtendedState: Int? = null
    private var savedAlwaysOnTop: Boolean? = null

    /** Entra/esce dallo schermo intero mantenendo ripristinabile la geometria. */
    fun toggle(window: Window) {
        if (active) exit(window) else enter(window)
    }

    fun enter(window: Window) {
        if (active) return
        val screen = runCatching {
            window.graphicsConfiguration.device.defaultConfiguration.bounds
        }.getOrNull() ?: return
        savedBounds = window.bounds
        savedExtendedState = (window as? Frame)?.extendedState
        savedAlwaysOnTop = window.isAlwaysOnTop
        // Primo sempre-in-primo-piano poi i bounds: nessun frame in cui la
        // taskbar spunta sopra la finestra durante la transizione.
        window.isAlwaysOnTop = true
        window.setBounds(screen)
        active = true
    }

    fun exit(window: Window) {
        if (!active) return
        runCatching {
            val state = savedExtendedState
            // Se si era massimizzati il ripristino giusto e' lo stato massimizzato:
            // un setBounds riporterebbe la finestra a stato normale.
            if (state != null && state and Frame.MAXIMIZED_BOTH != 0 && window is Frame) {
                window.extendedState = state
            } else {
                savedBounds?.let { window.bounds = it }
            }
        }
        window.isAlwaysOnTop = savedAlwaysOnTop ?: false
        savedBounds = null
        savedExtendedState = null
        savedAlwaysOnTop = null
        active = false
    }
}
