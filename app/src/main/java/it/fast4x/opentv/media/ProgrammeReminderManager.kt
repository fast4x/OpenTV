package it.fast4x.opentv.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.fast4x.opentv.MainActivity
import it.fast4x.opentv.R
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.ProgrammeReminder
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.repository.epgKey
import it.fast4x.opentv.data.store.UserPrefsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Notifica di sistema per i promemoria programmi (feature "reminder",
 * Android): canale di notifica dedicato (IMPORTANCE_DEFAULT, quindi con
 * suono/vibrazione secondo le impostazioni di sistema, a differenza della
 * notifica silenziosa di riproduzione) e tap che riapre l'app.
 *
 * Il permesso POST_NOTIFICATIONS (Android 13+) è richiesto dalla UI del
 * player per la notifica di riproduzione: se l'utente lo nega, anche i
 * promemoria restano muti (guardia esplicita, mai crash).
 */
object ProgrammeReminderNotifier {

    fun post(context: Context, channelName: String, programmeTitle: String, start: Long) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))
        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(channelName)
            .setContentText(context.getString(R.string.reminder_notif_now, time, programmeTitle))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching {
            // Id stabile per (canale, inizio): notifiche diverse non si
            // sovrascrivono a vicenda, il tap rimuove solo la sua.
            manager.notify(notificationIdOf(channelName, start), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun notificationIdOf(channelId: String, start: Long): Int =
        31 * channelId.hashCode() + start.hashCode()

    private const val CHANNEL_ID = "programme_reminders"
    private const val REMINDER_REQUEST_CODE = 5100
}

/**
 * Scheduler dei promemoria programmi (feature "reminder", Android): osserva
 * le voci armate nel DataStore e, quando un programma inizia, spara la
 * notifica e rimuove la voce (una sola volta per promemoria). Parte da
 * [it.fast4x.opentv.OpenTvApplication.onCreate] con lo scope dell'app:
 * l'avviso arriva anche con l'app in background (finché il processo vive —
 * coerente con il resto dell'app, che non usa servizi in primo piano).
 *
 * Le voci di programmi finiti da più di [STALE_GRACE_MS] (es. dispositivo
 * spento al momento dell'inizio) vengono pulite in silenzio, senza notifiche
 * arretrate.
 */
object ProgrammeReminderScheduler {

    @Volatile
    private var started = false

    fun start(
        appScope: CoroutineScope,
        context: Context,
        prefs: UserPrefsStore,
        playlistRepository: PlaylistRepository,
    ) {
        if (started) return
        started = true
        val tick = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        appScope.launch {
            combine(prefs.reminders, tick) { reminders, _ -> reminders }
                .collect { reminders ->
                    if (reminders.isEmpty()) return@collect
                    val now = System.currentTimeMillis()
                    val channels = (playlistRepository.state.value as? PlaylistState.Success)
                        ?.channels.orEmpty()
                    for (reminder in reminders) {
                        when {
                            // Programma finito da ore (dispositivo spento
                            // all'inizio): pulizia silenziosa, niente notifica
                            // fuori tempo.
                            reminder.start + STALE_GRACE_MS < now ->
                                prefs.removeReminder(reminder.channelId, reminder.start)

                            // È l'ora del programma: notifica + rimozione
                            // (impedisce il doppio avviso al tick successivo).
                            reminder.start <= now -> {
                                // Il promemoria memorizza l'id base EPG
                                // ("Rai1.it", chiave della guida); la playlist
                                // qualifica le varianti con suffissi
                                // ("Rai1.it@SD") — si risolve con lo stesso
                                // match epgKey usato da Home/player/guida.
                                val channelName = channels
                                    .firstOrNull { epgKey(it.id) == reminder.channelId }
                                    ?.name ?: reminder.channelId
                                ProgrammeReminderNotifier.post(
                                    context = context,
                                    channelName = channelName,
                                    programmeTitle = reminder.title.ifBlank {
                                        context.getString(R.string.notification_live)
                                    },
                                    start = reminder.start,
                                )
                                prefs.removeReminder(reminder.channelId, reminder.start)
                            }
                        }
                    }
                }
        }
    }

    private const val TICK_MS = 10_000L

    /** Finestra di tolleranza per la pulizia dei promemoria mai sparati. */
    private const val STALE_GRACE_MS = 2 * 60 * 60 * 1000L
}
