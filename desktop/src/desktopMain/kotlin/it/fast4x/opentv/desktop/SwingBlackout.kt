package it.fast4x.opentv.desktop

import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * "Ponte nero totale" sull'albero AWT/Swing della finestra.
 *
 * PERCHE' ESISTE (diagnosi dello "schermo bianco" in fullscreen):
 *
 * Compose Desktop renderizza la scena dentro la finestra con questa catena
 * (verificata sui sorgenti CMP 1.9.0 / skiko 0.9.22.2):
 *
 *   ComposeWindow (JFrame)
 *    +- contentPane (JPanel)
 *        +- ComposeWindowPanel (JLayeredPane, sfondo LAF chiaro)
 *            +- contenitori scene (JLayeredPane)
 *            |   +- SkiaLayer (JPanel, sfondo LAF chiaro)
 *            |       +- HardwareLayer: java.awt.Canvas HEAVYWEIGHT  <-- la
 *            |          superficie GPU skiko: background di default CHIARO,
 *            |          paint() volutamente VUOTO, senza ignoreRepaint.
 *            +- SwingInteropViewGroup (JPanel OPACO, sfondo LAF chiaro) <--
 *                contenitore AWT del video (SwingPanel)
 *                +- CallbackMediaPlayerComponent (nero, vlcj)
 *
 * Il Canvas skiko e' un HWND nativo: quando la sua area viene esposta o la
 * sua regione di clipping viene rimodellata dal "mixing" AWT (che accade a
 * OGNI reflow hide/show dei controlli, perche' il pannello video lightweight
 * cresce/decresce sopra il Canvas), Windows cancella l'area col COLORE DI
 * BACKGROUND del Canvas = bianco/chiaro del Look&Feel. Il paint del Canvas e'
 * vuoto e skiko non ri-renderizza perche' la scena Compose non e' cambiata:
 * il BIANCO RESTA SCHERMATO finche' la scena non viene ridisegnata (es. al
 * movimento del mouse). Nessun fix sugli sfondi di finestra/pannelli poteva
 * eliminarlo: il bianco nasce DENTRO il Canvas, che e' un FRATELLO della
 * catena video, non un suo antenato.
 *
 * La cura e' tripla:
 *  1. SFONDO NERO ALLA NASCITA: UIManager "Panel.background" = nero (Main.kt,
 *     prima della creazione della finestra) -> qualunque JPanel CMP/skiko/vlcj
 *     nasce nero invece che col chiaro del LAF.
 *  2. PASSEGGIATA RICORSIVA su TUTTI i componenti della finestra (questa
 *     classe): annerisce anche Canvas, SkiaLayer, contenitori scene e gruppi
 *     interop creati DOPO l'avvio (l'albero cresce quando si apre il player).
 *  3. RE-RENDER FORZATO di skiko: dopo aver annerito un Canvas chiediamo un
 *     repaint al suo parent (SkiaLayer.paint -> tryRedrawImmediately), cosi'
 *     la scena viene ridisegnata subito sopra l'eventuale cancellazione.
 *
 * L'operazione e' idempotente ed economica (pochi componenti;setBackground
 * e' invocato solo se il colore e' davvero diverso) e puo' essere richiamata
 * a ogni resize/recovery senza effetti collaterali.
 *
 * NOTA (dalla cura dello "sfondo bianco" in fullscreen, dopo la diagnosi dei
 * sorgenti CMP 1.9.0/skiko): la causa principale era il FULLSCREEN ESCLUSIVO
 * di AWT attivato da WindowPlacement.Fullscreen (GraphicsDevice
 * .setFullScreenWindow), che rende inaffidabile il mixing lightweight/
 * heavyweight proprio quando la regione di clip del video si allarga (hide
 * dei controlli). La cura primaria e' ora l'uso del fullscreen "borderless"
 * (DesktopFullscreenController) che percorre lo stesso rendering della
 * modalita' finestra; questo ponte nero resta come difesa generica per le
 * cancellazioni native a ogni reflow (resize, riorganizzazioni layout).
 */
object SwingBlackout {

    private val black: Color = Color.BLACK

    /**
     * Annerisce [window] e TUTTA la gerarchia discendente (finestra, rootPane,
     * contentPane, pannelli CMP/skiko, Canvas hardware, gruppi interop, vlcj).
     * Da chiamare sull'EDT (se non si e' sicuri usare [applyLater]).
     */
    fun apply(window: Window?) {
        window ?: return
        runCatching {
            window.background = black
            walk(window)
        }
    }

    /** Variante comoda da qualunque thread: sposta l'esecuzione sull'EDT. */
    fun applyLater(window: Window?) {
        window ?: return
        java.awt.EventQueue.invokeLater { apply(window) }
    }

    private fun walk(component: Component) {
        runCatching {
            if (component.background != black) component.background = black
        }

        // Il Canvas heavyweight (HardwareLayer di skiko) viene cancellato dal
        // nativo col proprio background a ogni esposizione: col background
        // nero la cancellazione diventa nera (coerente col tema). Il repaint
        // del parent (SkiaLayer) rimette in pipeline un render completo della
        // scena, che ricopre l'area con il contenuto Compose reale.
        if (component is Canvas) {
            runCatching { component.parent?.repaint() }
        }

        if (component is Container) {
            for (child in component.components) walk(child)
        }
    }
}
