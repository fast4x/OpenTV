package it.fast4x.opentv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.AWTEvent
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent

/**
 * Title bar "custom" della finestra desktop.
 *
 * La finestra viene creata SENZA decorazioni native (undecorated = true in
 * Main.kt): in modalita' finestra questa barra fornisce trascinamento,
 * riduzione, ingrandimento e chiusura, con lo stile Dark TV dell'app. A
 * schermo intero la barra non viene proprio disegnata (vedi Main.kt), quindi
 * non resta nessuna barra di sistema in alto e nessuna area da "togliere" a
 * finestra gia' creata: e' questo che rende il fullscreen davvero a bordo a
 * bordo su tutti i sistemi operativi.
 *
 * PERCHE' IL TRASCINAMENTO NON USA WindowDraggableArea (diagnosi del difetto
 * "tenendo premuto sulla barra la finestra non si sposta", verificata sui
 * sorgenti CMP 1.9.0 foundation-desktop): WindowDraggableArea riconosce il
 * drag con un `awaitFirstDown()` che accetta SOLO eventi down NON consumati,
 * mentre detectTapGestures (qui usato per il doppio click ingrandisci/
 * ripristina) consuma il down IMMEDIATAMENTE (TapGestureDetector.kt:
 * `val down = awaitFirstDown(); down.consume()`). Il gestore del doppio click
 * e' piu' profondo della Box trascinabile e sul pass Main i figli vedono gli
 * eventi PRIMA del padre: il down arrivava al riconoscitore del drag GIA'
 * consumato e il trascinamento non poteva mai partire. La cura e' avere UN
 * SOLO proprietario della pressione: detectTapGestures con onPress, che
 * all'inizio della pressione aggancia il trascinamento AWT (WindowDrag
 * Handler) e lo stacca al rilascio.
 */
@Composable
fun WindowScope.OpenTvWindowTitleBar(
    windowState: WindowState,
    onClose: () -> Unit,
) {
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    fun toggleMaximize() {
        windowState.placement =
            if (windowState.placement == WindowPlacement.Maximized)
                WindowPlacement.Floating
            else
                WindowPlacement.Maximized
    }

    val dragHandler = remember { WindowDragHandler(window) }

    // Un solo proprietario della pressione: il doppio click ingrandisce/
    // ripristina (comportamento abituale della title bar di sistema), la
    // pressione prolungata con movimento trascina la finestra. I pulsanti
    // restano cliccabili perche' il loro `clickable` consuma il down PRIMA
    // che la barra lo veda: una pressione sui pulsanti non avvia ne' il
    // trascinamento ne' il conteggio del doppio click.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(AppSurface)
            .pointerInput(dragHandler) {
                detectTapGestures(
                    onDoubleTap = { toggleMaximize() },
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
        Text(
            text = "OpenTV",
            modifier = Modifier.padding(start = 12.dp),
            color = AppPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        TitleBarButton(
            icon = Icons.Filled.Remove,
            description = "Riduci",
            onClick = { windowState.isMinimized = true },
        )
        TitleBarButton(
            icon = if (isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
            description = if (isMaximized) "Ripristina dimensione" else "Ingrandisci",
            onClick = { toggleMaximize() },
        )
        TitleBarButton(
            icon = Icons.Filled.Close,
            description = "Chiudi",
            onClick = onClose,
        )
    }
}

/**
 * Trascinamento della finestra a livello AWT (stesso approccio di
 * WindowDraggableArea in CMP 1.9.0, con una differenza: un listener GLOBALE
 * del Toolkit invece di listener aggiunti alla finestra. Gli eventi di drag
 * vengono consegnati al componente che ha ricevuto la pressione e non sempre
 * raggiungono i listener della finestra; un AWTEventListener invece vede
 * TUTTI gli eventi di sistema, a prescindere dal routing sui componenti).
 *
 * Il movimento usa le coordinate ASSOLUTE del puntatore sullo schermo
 * (MouseInfo.getPointerInfo): la destinazione e' sempre "posizione della
 * finestra all'inizio + delta del puntatore dall'inizio". Non soffre quindi
 * del ritardo/scatto che si avrebbe usando posizioni relative alla finestra,
 * che cambia posizione sotto il cursore proprio mentre si sposta.
 * Internal: è riusato anche dalla finestretta del mini-player (feature 11),
 * che ha una propria striscia trascinabile.
 */
internal class WindowDragHandler(private val window: Window) {

    private var windowLocationAtDragStart: Point? = null
    private var dragStartPoint: Point? = null

    private val listener = AWTEventListener { event ->
        if (event is MouseEvent) {
            when (event.id) {
                MouseEvent.MOUSE_DRAGGED -> onDrag()
                // Qualunque rilascio (anche di un altro bottone) termina il
                // trascinamento: evita di restare "agganciati" al puntatore.
                MouseEvent.MOUSE_RELEASED -> end()
            }
        }
    }

    /** Inizia a seguire il puntatore. No-op se la finestra e' massimizzata. */
    fun begin(windowState: WindowState) {
        if (windowState.placement != WindowPlacement.Floating) return
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        dragStartPoint = pointer
        windowLocationAtDragStart = window.location
        runCatching {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                listener,
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
            )
        }
    }

    /** Smette di seguire il puntatore (idempotente). */
    fun end() {
        dragStartPoint = null
        windowLocationAtDragStart = null
        runCatching {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    private fun onDrag() {
        val start = dragStartPoint ?: return
        val windowLocation = windowLocationAtDragStart ?: return
        val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
        window.setLocation(
            windowLocation.x + (pointer.x - start.x),
            windowLocation.y + (pointer.y - start.y),
        )
    }
}

@Composable
private fun TitleBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = AppTextSecondary,
            modifier = Modifier.size(15.dp),
        )
    }
}
