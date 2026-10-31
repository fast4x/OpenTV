package it.fast4x.opentv.desktop

import it.fast4x.opentv.data.Config
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.callback.CallbackImagePainter
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JComponent

/**
 * Stato di riproduzione esposto alla UI desktop (parallelo di PlaybackStatus
 * del modulo Android, qui pilotato dagli eventi di libVLC).
 */
sealed interface PlayerStatus {
    data object Buffering : PlayerStatus
    data object Playing : PlayerStatus
    data object Paused : PlayerStatus

    /**
     * Riconnessione automatica in corso (feature 7): il flusso è caduto e si
     * sta riaprendo da soli — [attempt]-esimo tentativo su [maxAttempts].
     * A differenza di Error la superficie video resta in composizione: il
     * ritorno dell'immagine è immediato quando il flusso torna vivo.
     */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : PlayerStatus

    data class Error(val message: String) : PlayerStatus
}

/**
 * Istantanea delle statistiche del flusso in riproduzione (feature
 * "statistiche stream"): codec e geometria video/audio da MediaInfo,
 * bitrate e perdite da libvlc_media_stats_t. I campi opzionali sono null
 * quando il flusso non li dichiara (es. flussi radio senza video).
 * Il bitrate demux di libVLC arriva in kb/s: la UI lo formatta in Mb/s.
 */
data class StreamStats(
    val videoCodec: String?,
    val videoWidth: Int?,
    val videoHeight: Int?,
    val videoFps: Float?,
    val audioCodec: String?,
    val audioBitrateKbps: Int?,
    /** Bitrate di demux dichiarato da libVLC (kb/s). */
    val demuxBitrateKbps: Float,
    /** Fotogrammi video persi dall'avvio del media. */
    val picturesLost: Int,
    /** Buffer audio persi dall'avvio del media. */
    val audioBuffersLost: Int,
    /** Discontinuità di flusso segnalate dal demuxer (hiccup di rete/CDN). */
    val discontinuities: Int,
    /** Pacchetti corrotti segnalati dal demuxer. */
    val corrupted: Int,
)

/**
 * Modalità di visualizzazione del video (porting della funzione Android):
 * - FIT: video adattato all'area mantenendo le proporzioni (bande nere dove
 *   serve) — la resa del painter di default di vlcj (ScaledCallbackImagePainter);
 * - ZOOM: video ingrandito fino a COPRIRE l'area mantenendo le proporzioni
 *   (bordi tagliati) — utile per i canali 4:3 su schermo 16:9;
 * - FILL: video stirato fino a riempire ESATTAMENTE l'area (deformato) —
 *   utile per correggere flussi anamorfici mal segnalati.
 */
enum class VideoResizeMode { FIT, ZOOM, FILL }

/**
 * Painter che disegna il fotogramma video secondo la modalità proporzioni
 * scelta. È IL punto in cui le proporzioni vanno implementate su desktop:
 * con la superficie a callback di vlcj il video è decodificato in un
 * BufferedImage Java e disegnato da Swing (il modulo vmem di VLC non ha una
 * finestra video da gestire), quindi le regolazioni native del vout
 * (`setScale`, `setAspectRatio`) NON producono effetti visibili — era la
 * causa del pulsante proporzioni "muto". Il painter è l'unico responsabile
 * di come l'immagine occupa l'area disponibile.
 *
 * I tre comportamenti replicano le implementazioni stock di vlcj dove
 * esistono, per non cambiare la resa del modo predefinito:
 * - FIT: adattamento con rapporto mantenuto e bande nere dove serve —
 *   identico a ScaledCallbackImagePainter (painter di default di vlcj);
 * - ZOOM: copertura con rapporto mantenuto (il lato corto riempie, i bordi
 *   in eccesso restano fuori dal pannello e vengono tagliati);
 * - FILL: stiramento fino a riempire ESATTAMENTE l'area — identico a
 *   FilledCallbackImagePainter.
 *
 * L'istanza è unica per il player e vive quanto il componente video: il modo
 * corrente viene semplicemente rieseguito a ogni paint, quindi la scelta
 * sopravvive da sola a zapping e recovery (nessuna "riapplicazione" serve
 * quando VLC apre un nuovo stream).
 */
private class ResizeModeImagePainter(initial: VideoResizeMode) : CallbackImagePainter {

    /** Modo corrente; volatile perché scritto dal thread UI e letto dal paint EDT. */
    @Volatile
    var mode: VideoResizeMode = initial

    override fun prepare(g2: Graphics2D, component: JComponent) {
        // Stessa interpolazione dei painter stock di vlcj.
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    }

    override fun paint(g2: Graphics2D, component: JComponent, image: BufferedImage?) {
        val w = component.width
        val h = component.height
        // Sfondo sempre nero: copre le bande del modo Adatta e gli eventuali
        // bordi esposti da arrotondamenti nei modi Zoom/Riempi.
        g2.color = component.background
        g2.fillRect(0, 0, w, h)
        if (image == null || w <= 0 || h <= 0) return

        val imgW = image.width
        val imgH = image.height
        if (imgW <= 0 || imgH <= 0) return

        val sx = w.toDouble() / imgW
        val sy = h.toDouble() / imgH

        when (mode) {
            VideoResizeMode.FILL -> {
                // Riempimento esatto: scala indipendente su X e Y (distorsione).
                g2.scale(sx, sy)
                g2.drawImage(image, null, 0, 0)
            }

            VideoResizeMode.ZOOM -> {
                // Copertura: il rapporto PIÙ GRANDE porta il lato corto a
                // riempire l'area; l'altro lato sborda e viene tagliato dai
                // limiti del pannello.
                val sf = maxOf(sx, sy)
                g2.translate((w - imgW * sf) / 2.0, (h - imgH * sf) / 2.0)
                g2.scale(sf, sf)
                g2.drawImage(image, null, 0, 0)
            }

            VideoResizeMode.FIT -> {
                // Adattamento: il rapporto PIÙ PICCOLO tiene tutto il
                // fotogramma dentro l'area, con eventuali bande nere.
                val sf = minOf(sx, sy)
                g2.translate((w - imgW * sf) / 2.0, (h - imgH * sf) / 2.0)
                g2.scale(sf, sf)
                g2.drawImage(image, null, 0, 0)
            }
        }
    }
}

/**
 * Wrapper di libVLC via vlcj per il desktop.
 *
 * [CallbackMediaPlayerComponent] crea factory + player + superficie AWT
 * (un JPanel) che la UI Compose ospita dentro uno SwingPanel. Le opzioni
 * per-canale (`:http-user-agent`, `:http-referrer`, `:http-origin`) sono le
 * stesse direttive LibVLC già usate dalla patch RAI/Mediaset su Android.
 *
 * Richiede VLC (libVLC) installato sul sistema: la ricerca delle librerie è
 * gestita da [VlcLocator] (percorsi noti, installazioni per-utente e override
 * via OPENTV_VLC_PATH / -Dopentv.vlc.path). Se libVLC non è caricabile NON
 * viene lanciata un'eccezione (che farebbe crashare la UI con il popup del
 * runtime): [initError] contiene il messaggio d'aiuto e la UI mostra il suo
 * overlay di errore.
 *
 * Le proporzioni video (Adatta/Zoom/Riempi) vivono nel [ResizeModeImagePainter]
 * passato al componente: con la superficie a callback le regolazioni native
 * del vout (setScale/setAspectRatio) non hanno effetti sul display.
 */
class VlcPlayer(private val onStatus: (PlayerStatus) -> Unit) {

    /**
     * Painter delle proporzioni: dichiarato PRIMA di [impl] perché gli viene
     * passato in costruzione (in Kotlin l'ordine di inizializzazione delle
     * proprietà segue l'ordine di dichiarazione).
     */
    private val resizeModePainter = ResizeModeImagePainter(VideoResizeMode.FIT)

    // Creazione protetta: se JNA non riesce a caricare libvlc l'eccezione resta
    // qui dentro e diventa initError, invece di esplodere in composizione.
    private val impl: Result<CallbackMediaPlayerComponent> = runCatching {
        VlcLocator.prepareNativeLibraryPath()
        // Il painter delle proporzioni entra nel costruttore completo (il path
        // "ordinario" documentato da vlcj): senza di esso vlcj userebbe il suo
        // ScaledCallbackImagePainter, che sa fare solo il modo "Adatta".
        // Gli argomenti null + lockBuffers=true replicano esattamente i default
        // del costruttore vuoto (factory di default, nessuna strategia
        // fullscreen, rendering intrinseco gestito dal componente).
        CallbackMediaPlayerComponent(null, null, null, true, resizeModePainter, null, null, null).apply {
            // Sfondo scuro su tutta la superficie video: se il rendering va in
            // pausa (ridimensionamenti, transizioni layout) la zona mostra nero
            // coerente col tema invece del bianco di default AWT/Swing.
            background = java.awt.Color.BLACK
            runCatching { videoSurfaceComponent().background = java.awt.Color.BLACK }
        }
    }

    /** Messaggio d'aiuto se libVLC non è disponibile sul sistema, altrimenti null. */
    val initError: String? = impl.exceptionOrNull()?.let { VlcLocator.installHint() }

    /** Superficie video AWT; null se libVLC non è disponibile. */
    val component: CallbackMediaPlayerComponent? = impl.getOrNull()

    private val mediaPlayer: MediaPlayer? = impl.getOrNull()?.mediaPlayer()

    /** Cambia la modalità proporzioni e la applica subito alla superficie. */
    fun setResizeMode(mode: VideoResizeMode) {
        resizeModePainter.mode = mode
        repaintSurface()
    }

    /**
     * Ridisegna subito la superficie video (sull'EDT Swing): il cambio di
     * modo è visibile all'istante anche a riproduzione ferma, senza attendere
     * il fotogramma successivo (che comunque ridipinge da solo mentre il
     * flusso va avanti).
     */
    private fun repaintSurface() {
        val surface = impl.getOrNull()?.videoSurfaceComponent() ?: return
        javax.swing.SwingUtilities.invokeLater {
            runCatching { surface.repaint() }
        }
    }

    init {
        mediaPlayer?.events()?.addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    // Nessuna riapplicazione delle proporzioni serve qui: il
                    // painter usa il modo corrente a OGNI paint, quindi la
                    // scelta sopravvive da sola al nuovo stream.
                    onStatus(PlayerStatus.Playing)
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    onStatus(PlayerStatus.Paused)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    onStatus(
                        PlayerStatus.Error(
                            "Il flusso non è riproducibile con VLC: controlla che il " +
                                "canale sia attivo e la rete raggiunga il server.",
                        ),
                    )
                }
            },
        )
    }

    /**
     * Avvia la riproduzione applicando le intestazioni per-canale della
     * playlist (#EXTVLCOPT). [defaultUserAgent] è l'UA "da browser" dell'app:
     * molti CDN rifiutano con 403 l'UA di prodotto di VLC.
     */
    fun play(url: String, channelUserAgent: String?, referrer: String?) {
        val player = mediaPlayer
        if (player == null) {
            onStatus(PlayerStatus.Error(initError ?: "libVLC non disponibile."))
            return
        }
        val userAgent = channelUserAgent ?: Config.DEFAULT_STREAM_USER_AGENT
        val options = buildList {
            add(":http-user-agent=$userAgent")
            if (!referrer.isNullOrBlank()) {
                add(":http-referrer=$referrer")
                add(":http-origin=$referrer")
            }
            add(":network-caching=3000")
        }
        player.media().play(url, *options.toTypedArray())
    }

    fun resume() {
        mediaPlayer?.controls()?.play()
    }

    fun pause() {
        mediaPlayer?.controls()?.pause()
    }

    /**
     * Volume relativo: applica [delta] al livello corrente (scala 0..125,
     * il massimo "comodo" di VLC: sopra 100 libVLC consente ancora di salire
     * ma l'audio comincia a distorcere) e ritorna il nuovo valore; null se
     * il player non è disponibile o l'operazione fallisce.
     *
     * Il valore di ritorno è RILETTO da libVLC dopo [setVolume], non calcolato
     * a mano: se il driver rifiuta o riscala la richiesta, il suggerimento
     * "Volume N%" della UI riflette il livello realmente applicato. Prima che
     * un media sia mai stato avviato volume() può rispondere -1: trattato
     * come 0 per non far partire la scala da un valore negativo.
     */
    fun volumeDelta(delta: Int): Int? {
        val player = mediaPlayer ?: return null
        return runCatching {
            val current = player.audio().volume().coerceAtLeast(0)
            val updated = (current + delta).coerceIn(0, 125)
            player.audio().setVolume(updated)
            // volume() risponde -1 se libVLC non ha (ancora) un livello
            // leggibile: in quel caso resta il valore calcolato.
            player.audio().volume().takeIf { it >= 0 } ?: updated
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Tracce audio (porting della selezione Android per canali multilingua)
    // ------------------------------------------------------------------

    /**
     * Descrizioni delle tracce audio del flusso corrente, così come le
     * espone libVLC (di solito "Track 1 - [ita]"). Attenzione: libVLC le
     * rende disponibili SOLO a stream avviato — finché il media non è in
     * riproduzione la lista è vuota (la UI quindi legge dall'evento
     * "playing", vedi [DesktopPlayerState]).
     *
     * L'id di ogni voce è il parametro corretto per [setAudioTrack]: NON
     * sono posizioni sequenziali e non va mai assunto 0..n-1 (documentato
     * anche su AudioApi.setTrack). La voce "Disable" (id -1) è esclusa a
     * monte: non ha senso offrire di spegnere l'audio dal menu tracce.
     */
    fun audioTrackDescriptions(): List<TrackDescription> {
        val player = mediaPlayer ?: return emptyList()
        return runCatching { player.audio().trackDescriptions() }
            .getOrNull()
            .orEmpty()
            .filter { it.id() >= 0 }
    }

    /**
     * Id della traccia audio attiva; null se libVLC non è disponibile o se
     * l'informazione non è leggibile. -1 (= nessuna traccia/disabilitata)
     * è riportato come null per uniformità con lo stato della UI.
     */
    fun currentAudioTrackId(): Int? {
        val player = mediaPlayer ?: return null
        return runCatching { player.audio().track() }.getOrNull()?.takeIf { it >= 0 }
    }

    /**
     * Seleziona la traccia audio per id (quello di [TrackDescription.id])
     * e ritorna l'id effettivamente attivo dopo l'operazione; null se il
     * player non è disponibile. Se la selezione fallisce, libVLC risponde
     * con -1 o con l'id vecchio: il chiamante userà il valore di ritorno
     * per aggiornare l'evidenziazione in modo onesto.
     */
    fun setAudioTrack(id: Int): Int? {
        val player = mediaPlayer ?: return null
        return runCatching { player.audio().setTrack(id) }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Tracce sottotitoli (gemello della selezione audio, feature "sottotitoli")
    // API vlcj 4.8.1: l'accessore di MediaPlayer e' subpictures() (NON
    // subtitles(), che NON esiste); SubpictureApi espone trackDescriptions(),
    // track() e setTrack(int) — vedi MediaPlayer.java:182.
    // ------------------------------------------------------------------

    /**
     * Descrizioni delle tracce SOTTOTITOLI del flusso corrente, così come
     * le espone libVLC (di solito "Track 1 - [ita]"). Come per l'audio,
     * libVLC le rende disponibili SOLO a stream avviato; il refresh segue
     * lo stesso percorso dell'audio (evento "playing", vedi
     * [DesktopPlayerState]). La voce "Disable" (id -1) è esclusa: l'UI
     * offre la voce "Disattivati" esplicita con [setSubtitleTrack](-1).
     */
    fun subtitleTrackDescriptions(): List<TrackDescription> {
        val player = mediaPlayer ?: return emptyList()
        return runCatching { player.subpictures().trackDescriptions() }
            .getOrNull()
            .orEmpty()
            .filter { it.id() >= 0 }
    }

    /**
     * Id della traccia sottotitoli attiva; null se nessuna/disabilitata
     * (libVLC risponde -1) o se l'informazione non è leggibile.
     */
    fun currentSubtitleTrackId(): Int? {
        val player = mediaPlayer ?: return null
        return runCatching { player.subpictures().track() }.getOrNull()?.takeIf { it >= 0 }
    }

    /**
     * Seleziona la traccia sottotitoli per id, oppure la disattiva con -1
     * (libvlc_video_set_spu: -1 = disable). Ritorna l'id effettivamente
     * attivo dopo l'operazione (−1 = disattivata); null se il player non
     * è disponibile.
     */
    fun setSubtitleTrack(id: Int): Int? {
        val player = mediaPlayer ?: return null
        return runCatching { player.subpictures().setTrack(id) }.getOrNull()
    }

    fun stop() {
        mediaPlayer?.controls()?.stop()
    }

    // ------------------------------------------------------------------
    // Statistiche stream (feature "statistiche", stile VLC → Ctrl+J)
    // ------------------------------------------------------------------

    /**
     * Statistiche di riproduzione del flusso corrente (lettura istantanea,
     * la UI la ripete ~1s): codec e risoluzione da MediaInfo, bitrate e
     * perdite da libvlc_media_stats_t. Null se nessun media è caricato
     * (player fermo, stream in apertura) o le statistiche non sono (ancora)
     * disponibili: il demuxer le popola dopo l'avvio della riproduzione.
     */
    fun streamStats(): StreamStats? {
        val player = mediaPlayer ?: return null
        return runCatching {
            val info = player.media().info() ?: return@runCatching null
            val video = info.videoTracks().firstOrNull()
            val audio = info.audioTracks().firstOrNull()
            val stats = info.statistics()
            val fps = video?.takeIf { it.frameRateBase() > 0 }
                ?.let { it.frameRate().toFloat() / it.frameRateBase() }
            StreamStats(
                videoCodec = video?.codecName(),
                videoWidth = video?.width()?.takeIf { it > 0 },
                videoHeight = video?.height()?.takeIf { it > 0 },
                videoFps = fps?.takeIf { it > 0f },
                audioCodec = audio?.codecName(),
                audioBitrateKbps = audio?.bitRate()?.takeIf { it > 0 }?.div(1000),
                demuxBitrateKbps = stats.demuxBitrate(),
                picturesLost = stats.picturesLost(),
                audioBuffersLost = stats.audioBuffersLost(),
                discontinuities = stats.demuxDiscontinuity(),
                corrupted = stats.demuxCorrupted(),
            )
        }.getOrNull()
    }

    /**
     * Risveglia il rendering dopo un ridimensionamento dell'area video
     * (resize finestra, ingresso/uscita fullscreen, apertura pannello
     * programmazione, arrivo EPG). Da fix28 lo show/hide dei controlli NON
     * ridimensiona piu' nulla (barre in slot a altezza fissa). Dopo una
     * transizione di layout la superficie a callback puo' restare
     * "congelata"/vuota finche' non viene sollecitata: qui forziamo la
     * superficie Swing a ridipingersi (il painter applica il modo proporzioni
     * corrente a ogni paint).
     */
    fun recoverAfterResize() {
        val comp = component ?: return
        // Le proporzioni non richiedono riapplicazioni: il painter usa il modo
        // corrente a ogni paint e il ridisegno qui sotto ripristina la resa col
        // modo scelto dopo il reflow del layout.
        javax.swing.SwingUtilities.invokeLater {
            runCatching {
                comp.revalidate()
                // Ponte nero COMPLETO della finestra (SwingBlackout): annerisce
                // holder interop, SkiaLayer, Canvas skiko e qualunque pannello
                // nato dopo l'avvio, e riporta in pipeline il render skiko
                // ridipingendo i parent dei Canvas. Copre piu' della vecchia
                // passeggiata sui soli antenati: il bianco nasce anche nel
                // Canvas skiko FRATELLO della catena video, non solo negli
                // antenati.
                SwingBlackout.apply(javax.swing.SwingUtilities.getWindowAncestor(comp))
                // paintImmediately aggira la coalescenza di RepaintManager:
                // ridipinge SUBITO la superficie anche se la dirty-region
                // calcolata dopo un reflow risultasse vuota.
                comp.paintImmediately(0, 0, comp.width, comp.height)
                comp.repaint()
                // Risveglio dell'intera catena finestra: validate + repaint
                // della finestra rimette in pipeline tutti i paint in sospeso,
                // sync scarica la coda di disegno sull'hardware.
                val win = javax.swing.SwingUtilities.getWindowAncestor(comp)
                win?.validate()
                win?.repaint()
                java.awt.Toolkit.getDefaultToolkit().sync()
            }
        }
    }

    /**
     * Ponte nero immediato sull'intera gerarchia AWT della finestra che ospita
     * il video (vedi [SwingBlackout]): da chiamare a ogni transizione di layout
     * che riformatta il mixing AWT (show/hide controlli, toggle fullscreen,
     * apertura player) PRIMA del recovery a 500ms. Annerisce qualunque area
     * che il nativo puo' cancellare col colore di background e ridisegna la
     * scena skiko. Idempotente ed economico; no-op se libVLC non e' disponibile.
     */
    fun blackoutWindow() {
        val cmp = component ?: return
        javax.swing.SwingUtilities.invokeLater {
            runCatching {
                SwingBlackout.apply(javax.swing.SwingUtilities.getWindowAncestor(cmp))
            }
        }
    }

    fun release() {
        val player = mediaPlayer
        val cmp = component
        if (player != null) runCatching { player.release() }
        if (cmp != null) runCatching { cmp.release() }
    }
}
