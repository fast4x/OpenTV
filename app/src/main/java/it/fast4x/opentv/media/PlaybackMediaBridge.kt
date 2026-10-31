package it.fast4x.opentv.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.session.MediaSession
import it.fast4x.opentv.MainActivity
import it.fast4x.opentv.R
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Ponte di riproduzione con il sistema (feature 9):
 *
 * - [MediaSession] sopra l'ExoPlayer del player: play/pausa da lockscreen e
 *   tasti media degli auricolari Bluetooth; i tasti "traccia successiva/
 *   precedente" vengono intercettati e convertiti in canale successivo/
 *   precedente.
 * - Notifica persistente con canale in onda, programma corrente e azioni
 *   (canale precedente, play/pausa, canale successivo); i pulsanti inviano
 *   broadcast a [PlaybackCommandReceiver], che li ripubblica sul bus dei
 *   comandi.
 * - Il ViewModel del player colleziona il bus e applica i comandi: un solo
 *   proprietario dello stato di riproduzione (niente service: la sessione
 *   vive quanto la schermata del player, come la riproduzione stessa).
 */
object PlaybackCommandBus {

    /** Azione broadcast del pulsante play/pausa della notifica. */
    const val ACTION_TOGGLE_PLAY = "it.fast4x.opentv.media.TOGGLE_PLAY"

    /** Azione broadcast del pulsante canale successivo della notifica. */
    const val ACTION_NEXT_CHANNEL = "it.fast4x.opentv.media.NEXT_CHANNEL"

    /** Azione broadcast del pulsante canale precedente della notifica. */
    const val ACTION_PREV_CHANNEL = "it.fast4x.opentv.media.PREV_CHANNEL"

    /** Comando: alterna play/pausa. */
    const val CMD_TOGGLE_PLAY = 1

    /** Comando: canale successivo. */
    const val CMD_NEXT_CHANNEL = 2

    /** Comando: canale precedente. */
    const val CMD_PREV_CHANNEL = 3

    /**
     * Comandi pronti per il collezionista del ViewModel. extraBufferCapacity
     * assorbe i doppi tocchi rapidi senza perdere eventi (tryEmit mai
     * bloccante; se il collezionista non esiste più il buffer si svuota).
     */
    val commands = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}

/**
 * Ricevitore dei pulsanti della notifica (dichiarato nel manifest con
 * exported = false): solo i PendingIntents espliciti creati da
 * [PlaybackMediaBridge] possono raggiungerlo. Converte l'azione in un
 * comando sul bus.
 */
class PlaybackCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val command = when (intent?.action) {
            PlaybackCommandBus.ACTION_TOGGLE_PLAY -> PlaybackCommandBus.CMD_TOGGLE_PLAY
            PlaybackCommandBus.ACTION_NEXT_CHANNEL -> PlaybackCommandBus.CMD_NEXT_CHANNEL
            PlaybackCommandBus.ACTION_PREV_CHANNEL -> PlaybackCommandBus.CMD_PREV_CHANNEL
            else -> return
        }
        PlaybackCommandBus.commands.tryEmit(command)
    }
}

/**
 * Sessione media + notifica di riproduzione. Crearla quando il player ha un
 * canale e aggiornarla a ogni cambiamento (canale, programma EPG, play/pausa);
 * [release] alla chiusura del player rimuove notifica e sessione.
 */
class PlaybackMediaBridge(
    private val context: Context,
    player: androidx.media3.exoplayer.ExoPlayer,
) {

    private val session: MediaSession = MediaSession.Builder(context, player)
        .setCallback(object : MediaSession.Callback {
            override fun onMediaButtonEvent(
                mediaSession: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent,
            ): Boolean {
                // "Traccia successiva/precedente" degli auricolari e dei
                // telecomandi -> canale successivo/precedente di OpenTV.
                val keyCode = keyEventOf(intent)?.keyCode
                return when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        PlaybackCommandBus.commands.tryEmit(PlaybackCommandBus.CMD_NEXT_CHANNEL)
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        PlaybackCommandBus.commands.tryEmit(PlaybackCommandBus.CMD_PREV_CHANNEL)
                        true
                    }

                    // Play/pausa e tutti gli altri tasti restano alla sessione
                    // (il player risponde da solo al tasto play/pausa).
                    else -> super.onMediaButtonEvent(mediaSession, controllerInfo, intent)
                }
            }
        })
        .build()

    /** Estrae il KeyEvent dal broadcast media button, compatibile API 24+. */
    private fun keyEventOf(intent: Intent): KeyEvent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    private var lastIsPlaying: Boolean? = null

    /**
     * Aggiorna la notifica (e di riflesso i controlli di sistema legati alla
     * sessione). Chiamata dal ViewModel a ogni cambiamento rilevante; no-op
     * se le notifiche sono disabilitate (permesso negato su Android 13+ o
     * canale spento dall'utente): la sessione continua comunque a funzionare.
     */
    fun update(channelName: String, programmeTitle: String?, isPlaying: Boolean) {
        lastIsPlaying = isPlaying
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, PlaybackCommandReceiver::class.java)
                .setAction(PlaybackCommandBus.ACTION_TOGGLE_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, PlaybackCommandReceiver::class.java)
                .setAction(PlaybackCommandBus.ACTION_NEXT_CHANNEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val prevIntent = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, PlaybackCommandReceiver::class.java)
                .setAction(PlaybackCommandBus.ACTION_PREV_CHANNEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = programmeTitle?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_live)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(channelName)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(0, context.getString(R.string.player_prev), prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                context.getString(R.string.player_play_pause),
                toggleIntent,
            )
            .addAction(0, context.getString(R.string.player_next), nextIntent)
            .build()

        try {
            // setOnlyAlertOnce rende gli aggiornamenti silenziosi: solo il
            // primo affioramento avvisa (e con IMPORTANCE_LOW nemmeno quello).
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permesso revocato a runtime: la notifica sparisce ma la
            // sessione media continua a gestire tasti e lockscreen.
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    /** Rimuove notifica e sessione (chiusura del player). */
    fun release() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        runCatching { session.release() }
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 4201
    }
}
