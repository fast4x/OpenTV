package it.fast4x.opentv

import android.app.Application
import android.content.Context
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.store.UserPrefsStore
import it.fast4x.opentv.media.ProgrammeReminderScheduler
import it.fast4x.opentv.util.ContentResolverPlaylistReader
import it.fast4x.opentv.util.ResourcePlaylistStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application dell'app: inizializza le dipendenze condivise del modulo :core
 * (senza framework DI, per mantenere il progetto leggero).
 *
 * Il core è multiplatform: qui si iniettano le "colla" Android —
 * directory di lavoro (filesDir), DataStore su file, lettura della playlist
 * importata via ContentResolver (SAF) e messaggi localizzati.
 */
class OpenTvApplication : Application() {

    // Scope tutto dell'app per lavori che devono partire all'avvio senza
    // legarsi al ciclo di vita di una schermata (es. migrazione preferenze).
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var prefs: UserPrefsStore
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var epgRepository: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Stesso file che creava il delegate `preferencesDataStore` prima
        // della conversione (filesDir/datastore/…): preferiti, cronologia e
        // impostazioni esistenti restano validi senza migrazioni.
        val datastoreDir = File(filesDir, "datastore")
        datastoreDir.mkdirs()

        prefs = UserPrefsStore(
            dataStoreFile = File(datastoreDir, PREFS_FILE),
        )
        playlistRepository = PlaylistRepository(
            prefs = prefs,
            filesDir = filesDir,
            localPlaylistReader = ContentResolverPlaylistReader(this),
            strings = ResourcePlaylistStrings(this),
        )
        epgRepository = EpgRepository()

        // Migrazione preferenze pre-fix15 (campo playlist pre-compilato con
        // l'URL Italia, campo EPG con gli URL dei vecchi chip): i valori
        // generati dall'app diventano "campo vuoto = automatico" in background,
        // senza bloccare il primo avvio. Gli URL personali restano intatti.
        appScope.launch { prefs.migrateLegacyDefaults() }

        // Scheduler dei promemoria programmi (feature "reminder"): vive a
        // livello app, non nel player — l'avviso arriva anche con l'app in
        // background (finché il processo vive, coerente con il resto dell'app
        // che non usa servizi in primo piano).
        ProgrammeReminderScheduler.start(appScope, this, prefs, playlistRepository)
    }

    private companion object {
        const val PREFS_FILE = "opentv_prefs.preferences_pb"
    }
}

/** Accesso rapido alle dipendenze dell'app da qualunque contesto. */
val Context.openTvApp: OpenTvApplication
    get() = applicationContext as OpenTvApplication
