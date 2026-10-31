package it.fast4x.opentv.desktop

import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.repository.epgKey
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Notifica di sistema desktop per i promemoria programmi (feature
 * "reminder"): SystemTray + TrayIcon.displayMessage, cioè il "toast" nativo
 * del sistema operativo (fumetto sull'area di notifica su Windows, banner
 * della tray su macOS/Linux dove la tray è disponibile). Nessuna dipendenza
 * extra: l'iconcina della tray è disegnata a mano (riempimento col primario
 * dell'app), non serve una risorsa.
 *
 * Se la tray di sistema non è supportata o non è ancora disponibile (alcuni
 * WM Linux, ambienti headless) le notifiche si disattivano in silenzio con
 * runCatching: i promemoria restano armati ma non avvisano, e l'app non
 * crasha mai per una notifica.
 */
object DesktopReminderNotifier {

    private var trayIcon: TrayIcon? = null

    private fun ensureTrayIcon(): TrayIcon? {
        trayIcon?.let { return it }
        if (!SystemTray.isSupported()) return null
        val icon = runCatching {
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB).apply {
                val g = createGraphics()
                g.color = Color(0x4D, 0x9F, 0xFF) // AppPrimary, senza dipendere dal tema Compose
                g.fillRect(0, 0, 16, 16)
                g.dispose()
            }
            TrayIcon(image, "OpenTV")
        }.getOrNull() ?: return null
        runCatching {
            icon.isImageAutoSize = true
            SystemTray.getSystemTray().add(icon)
        }.getOrElse { return null }
        trayIcon = icon
        return icon
    }

    /**
     * Mostra la notifica "inizia ora": titolo = nome del canale, testo =
     * titolo del programma con l'orario di inizio. I fumi di sistema sono
     * best-effort per natura: qualunque fallimento è ignorato.
     */
    fun notify(channelName: String, programmeTitle: String, start: Long) {
        val icon = ensureTrayIcon() ?: return
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(start))
        runCatching {
            icon.displayMessage(
                channelName,
                "Inizia ora ($time): $programmeTitle",
                TrayIcon.MessageType.INFO,
            )
        }
    }

    /** Rimuove l'icona dalla tray (chiamata alla chiusura dell'app, se serve). */
    fun dispose() {
        val icon = trayIcon ?: return
        trayIcon = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }
}

/**
 * Scheduler dei promemoria programmi (feature "reminder", desktop): osserva
 * le voci armate nel DataStore e, quando un programma inizia, spara la
 * notifica di sistema e rimuove la voce (una sola volta per promemoria).
 *
 * Vive a livello app — NON nel player: l'avviso deve arrivare anche mentre
 * si sfoglia la guida o la Home. Le voci di programmi finiti da più di
 * [STALE_GRACE_MS] (es. app spenta al momento dell'inizio) vengono pulite in
 * silenzio, senza notifiche arretrate.
 *
 * Il singleton con guardia [started] evita doppie schedulazioni se la
 * composizione venisse mai ricreata.
 */
object DesktopReminderScheduler {

    @Volatile
    private var started = false

    fun start(deps: DesktopDependencies) {
        if (started) return
        started = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tick = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        scope.launch {
            combine(deps.prefs.reminders, tick) { reminders, _ -> reminders }
                .collect { reminders ->
                    if (reminders.isEmpty()) return@collect
                    val now = System.currentTimeMillis()
                    val channels = (deps.playlistRepository.state.value as? PlaylistState.Success)
                        ?.channels.orEmpty()
                    for (reminder in reminders) {
                        when {
                            // Programma finito da ore (app spenta all'inizio):
                            // pulizia silenziosa, niente notifica fuori tempo.
                            reminder.start + STALE_GRACE_MS < now ->
                                deps.prefs.removeReminder(reminder.channelId, reminder.start)

                            // È l'ora del programma: notifica + rimozione
                            // (la rimozione impedisce il doppio avviso al tick
                            // successivo).
                            reminder.start <= now -> {
                                val channelName = channels
                                    .firstOrNull { epgKey(it.id) == reminder.channelId }
                                    ?.name ?: reminder.channelId
                                DesktopReminderNotifier.notify(
                                    channelName = channelName,
                                    programmeTitle = reminder.title.ifBlank { "Programma" },
                                    start = reminder.start,
                                )
                                deps.prefs.removeReminder(reminder.channelId, reminder.start)
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
