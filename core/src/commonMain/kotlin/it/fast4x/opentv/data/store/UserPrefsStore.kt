package it.fast4x.opentv.data.store

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.AppSettings
import it.fast4x.opentv.data.model.PlaybackEngine
import it.fast4x.opentv.data.model.ProgrammeReminder
import it.fast4x.opentv.data.model.RecentEntry
import it.fast4x.opentv.data.model.SourceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import java.io.File
import java.util.UUID

/**
 * Persistenza delle preferenze utente: preferiti (per sorgente, feature
 * "preferiti per profilo"), cronologia "ultimi visti" e impostazioni (URL
 * playlist, URL EPG), basata su Jetpack DataStore.
 *
 * Versione multiplatform: il file dello store viene iniettato dal chiamante
 * (filesDir su Android, profilo utente su desktop) e l'API usata è
 * `datastore-preferences-core` con okio Path, identica su tutte le
 * piattaforme JVM. Il formato del file (`opentv_prefs.preferences_pb`) è lo
 * stesso dello store Android: migrando una copia dei dati si conservano
 * preferiti e cronologia.
 */
class UserPrefsStore(dataStoreFile: File) {

    // DataStore 1.2.x: createWithFile e' stata rimossa, la sostituzione e'
    // createWithPath (stessa firma: produceFile -> okio.Path).
    private val dataStore = PreferenceDataStoreFactory.createWithPath(
        produceFile = { dataStoreFile.absolutePath.toPath() },
    )

    private object Keys {
        // Bucket storico dei preferiti: dalla feature "preferiti per profilo"
        // rappresenta SOLO la sorgente automatica (nessun profilo attivo).
        // Nessuna migrazione dei dati esistenti: i preferiti creati prima
        // della feature restano quelli della lista automatica.
        val FAVORITES = stringSetPreferencesKey("fav_ids")

        // Preferiti per profilo (feature dedicata): un solo campo che contiene
        // un BUCKET per profilo, stessa codifica dei canali nascosti (scope ->
        // id canale). La chiave del bucket e' l'id del profilo attivo: i
        // preferiti sono una scelta legata alla lista in cui vive il canale,
        // come i nascosti.
        val FAVORITES_SCOPED = stringPreferencesKey("fav_scoped")
        val RECENTS = stringPreferencesKey("recents")
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val EPG_URL = stringPreferencesKey("epg_url")
        val PLAYBACK_ENGINE = stringPreferencesKey("playback_engine")

        // Sorgente playlist locale importata (URI SAF su Android, percorso
        // file su desktop): quando impostata ha priorità su PLAYLIST_URL
        // finché l'utente non salva un nuovo URL.
        val LOCAL_PLAYLIST_URI = stringPreferencesKey("local_playlist_uri")

        // Filtro per gruppo (genere) selezionato nella Home: persiste tra le
        // sessioni. Null/assente = nessun filtro (chip "Tutti").
        val SELECTED_GROUP = stringPreferencesKey("selected_group")

        // Paese scelto nei selettori di Home e Impostazioni (fix19):
        // preferenza dedicata, separata da PLAYLIST_URL (che resta per i soli
        // URL personali). Null/assente = modalità automatica (paese del
        // dispositivo). Così la scelta del paese non valorizza più il campo
        // URL delle Impostazioni ed è la stessa fonte di verità per entrambe
        // le schermate, in entrambe le direzioni.
        val COUNTRY_CODE = stringPreferencesKey("country_code")

        // Promemoria programmi ("avvisami quando inizia", feature reminder):
        // lista codificata di ProgrammeReminder (canale, inizio, titolo).
        val REMINDERS = stringPreferencesKey("reminders")

        // Profili di sorgente (feature "profilazione della lista"): coppie
        // playlist+EPG create dall'utente nelle Impostazioni, codificate una
        // voce per riga (id\tnome\tplaylistUrl\tepgUrl). ACTIVE_PROFILE_ID e'
        // il profilo selezionato: assente/vuoto = sorgente automatica
        // (iptv-org, catena storica URL personale/paese/predefinita).
        val SOURCE_PROFILES = stringPreferencesKey("source_profiles")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        // Canali nascosti (feature "nascondi canali"): un solo campo che
        // contiene un BUCKET per sorgente. La chiave del bucket (scope) e'
        // l'id del profilo attivo, oppure DEFAULT_HIDDEN_SCOPE per la sorgente
        // automatica (iptv-org/paese/URL personale): nascondere un canale e'
        // una scelta legata alla lista in cui vive, non globale. La playlist
        // originale non viene mai toccata.
        val HIDDEN_CHANNELS = stringPreferencesKey("hidden_channels")

        // Filtro "solo con guida TV" (feature dedicata): quando attivo, la
        // Home mostra solo i canali per cui la guida ha almeno un programma.
        // Persiste tra le sessioni come il filtro per gruppo; se la guida non
        // copre nessun canale della sorgente il filtro resta inattivo
        // (altrimenti nasconderebbe l'intera lista).
        val GUIDE_ONLY = booleanPreferencesKey("guide_only")
    }

    /**
     * Preferiti della SORGENTE AUTOMATICA (bucket storico fav_ids): usato
     * direttamente quando nessun profilo e' attivo, cosi' i preferiti creati
     * prima della feature "preferiti per profilo" restano al loro posto
     * senza migrazioni.
     */
    val favorites: Flow<Set<String>> =
        dataStore.data.map { it[Keys.FAVORITES] ?: emptySet() }

    // Bucket dei preferiti per profilo: stessa tecnica dei canali nascosti.
    private val favoritesScoped: Flow<Map<String, Set<String>>> =
        dataStore.data.map { decodeFavoriteBuckets(it[Keys.FAVORITES_SCOPED].orEmpty()) }

    /**
     * Preferiti della SORGENTE ATTIVA (feature "preferiti per profilo"):
     * con un profilo attivo il set arriva dal bucket del profilo (fav_scoped),
     * senza profilo dal bucket storico fav_ids. Ogni lista ha il suo elenco:
     * i canali preferiti dell'iptv-org automatico non compaiono nel profilo
     * FreeIPTV e viceversa.
     */
    fun favoritesForActiveScope(): Flow<Set<String>> =
        combine(favorites, favoritesScoped, activeProfileId) { legacy, map, activeId ->
            if (activeId.isNullOrBlank()) legacy else map[activeId].orEmpty()
        }

    /** Cronologia dei canali visti, dalla più recente. */
    val recents: Flow<List<RecentEntry>> =
        dataStore.data.map { decodeRecents(it[Keys.RECENTS].orEmpty()) }

    /** Impostazioni generali. */
    val settings: Flow<AppSettings> = dataStore.data.map {
        AppSettings(
            playlistUrl = it[Keys.PLAYLIST_URL].orEmpty(),
            epgUrl = it[Keys.EPG_URL].orEmpty(),
            playbackEngine = PlaybackEngine.EXO,
            localPlaylistUri = it[Keys.LOCAL_PLAYLIST_URI].orEmpty(),
            // Normalizzato anche in lettura: un valore corrotto o non
            // standard vale come "non impostato" (modalità automatica).
            countryCode = Config.normalizeCountryCode(it[Keys.COUNTRY_CODE]),
        )
    }

    /** Gruppo (genere) selezionato nel filtro della Home; null = nessun filtro. */
    val selectedGroup: Flow<String?> =
        dataStore.data.map { it[Keys.SELECTED_GROUP] }

    /**
     * Filtro "solo con guida TV" della Home: true = mostra solo i canali
     * coperti dalla guida TV. Persiste tra le sessioni come selectedGroup.
     */
    val guideOnly: Flow<Boolean> =
        dataStore.data.map { it[Keys.GUIDE_ONLY] ?: false }

    /** Promemoria programmi attivi, in ordine di inizio. */
    val reminders: Flow<List<ProgrammeReminder>> =
        dataStore.data.map { decodeReminders(it[Keys.REMINDERS].orEmpty()) }

    /** Profili di sorgente salvati, in ordine di creazione. */
    val sourceProfiles: Flow<List<SourceProfile>> =
        dataStore.data.map { decodeSourceProfiles(it[Keys.SOURCE_PROFILES].orEmpty()) }

    /**
     * Id del profilo di sorgente attivo; null/assente = sorgente automatica
     * (iptv-org, catena storica URL personale/paese/predefinita).
     */
    val activeProfileId: Flow<String?> =
        dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID]?.takeIf { id -> id.isNotBlank() } }

    /**
     * Canali nascosti (feature "nascondi canali"): mappa scope -> set di id
     * canale. Lo scope e' l'id del profilo di sorgente attivo oppure
     * [DEFAULT_HIDDEN_SCOPE] per la sorgente automatica.
     */
    val hiddenChannels: Flow<Map<String, Set<String>>> =
        dataStore.data.map { decodeHiddenChannels(it[Keys.HIDDEN_CHANNELS].orEmpty()) }

    /**
     * Set dei canali nascosti per la SORGENTE ATTIVA (profilo attivo o
     * automatica): flusso pronto per ViewModel e stati desktop, cosi' la
     * risoluzione dello scope vive in un solo posto.
     */
    fun hiddenChannelsForActiveScope(): Flow<Set<String>> =
        combine(hiddenChannels, activeProfileId) { map, activeId ->
            map[activeId ?: DEFAULT_HIDDEN_SCOPE].orEmpty()
        }

    /**
     * Marca/smarca un canale dai preferiti della sorgente attiva (feature
     * "preferiti per profilo"): lo scope e' risolto al momento della chiamata
     * (profilo attivo -> bucket dedicato, altrimenti bucket storico fav_ids),
     * come per i canali nascosti. Un bucket svuotato viene rimosso dal campo.
     */
    suspend fun toggleFavorite(channelId: String) {
        val scope = activeProfileId.first()
        dataStore.edit { prefs ->
            if (scope.isNullOrBlank()) {
                // Sorgente automatica: il bucket resta la chiave storica.
                val current = prefs[Keys.FAVORITES] ?: emptySet()
                prefs[Keys.FAVORITES] = if (channelId in current) {
                    current - channelId
                } else {
                    current + channelId
                }
            } else {
                // Profilo attivo: bucket dedicato fav_scoped.
                val map = decodeFavoriteBuckets(prefs[Keys.FAVORITES_SCOPED].orEmpty()).toMutableMap()
                val current = map[scope].orEmpty()
                val updated = if (channelId in current) {
                    current - channelId
                } else {
                    current + channelId
                }
                if (updated.isEmpty()) map.remove(scope) else map[scope] = updated
                if (map.isEmpty()) prefs.remove(Keys.FAVORITES_SCOPED)
                else prefs[Keys.FAVORITES_SCOPED] = encodeFavoriteBuckets(map)
            }
        }
    }

    suspend fun addRecent(channelId: String) {
        dataStore.edit { prefs ->
            val previous = decodeRecents(prefs[Keys.RECENTS].orEmpty())
                .filterNot { it.channelId == channelId }
            val updated = (
                listOf(RecentEntry(channelId, System.currentTimeMillis())) + previous
                ).take(MAX_RECENTS)
            prefs[Keys.RECENTS] = encodeRecents(updated)
        }
    }

    suspend fun setPlaylistUrl(url: String) {
        dataStore.edit { it[Keys.PLAYLIST_URL] = url.trim() }
    }

    /**
     * Imposta (o, con null, rimuove) la playlist locale come sorgente attiva.
     * La rimozione riporta la priorità all'URL remoto configurato.
     */
    suspend fun setLocalPlaylistUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.LOCAL_PLAYLIST_URI)
            else prefs[Keys.LOCAL_PLAYLIST_URI] = uri
        }
    }

    suspend fun setEpgUrl(url: String) {
        dataStore.edit { it[Keys.EPG_URL] = url.trim() }
    }

    /**
     * Imposta (o, con null, rimuove) il paese scelto nei selettori di Home
     * e Impostazioni: entrambe le schermate osservano lo stesso flusso
     * [settings], quindi la scelta fatta in una si riflette nell'altra.
     * Il codice viene normalizzato; un valore non valido equivale a null
     * (modalità automatica, paese del dispositivo).
     */
    suspend fun setCountryCode(code: String?) {
        dataStore.edit { prefs ->
            val normalized = Config.normalizeCountryCode(code)
            if (normalized == null) prefs.remove(Keys.COUNTRY_CODE)
            else prefs[Keys.COUNTRY_CODE] = normalized
        }
    }

    /** Imposta il motore di riproduzione (auto / exo / vlc). */
    suspend fun setPlaybackEngine(engine: PlaybackEngine) {
        dataStore.edit { it[Keys.PLAYBACK_ENGINE] = engine.name.lowercase() }
    }

    /**
     * Nasconde il canale nella sorgente attiva (feature "nascondi canali"):
     * sparisce da Home, ricerca, gruppi, preferiti, recenti e guida TV; la
     * playlist resta intatta. Operazione reversibile con [unhideChannel] e
     * [unhideAllChannels].
     */
    suspend fun hideChannel(channelId: String) {
        val scope = activeProfileId.first() ?: DEFAULT_HIDDEN_SCOPE
        dataStore.edit { prefs ->
            val map = decodeHiddenChannels(prefs[Keys.HIDDEN_CHANNELS].orEmpty()).toMutableMap()
            map[scope] = map[scope].orEmpty() + channelId
            prefs[Keys.HIDDEN_CHANNELS] = encodeHiddenChannels(map)
        }
    }

    /** Ripristina (toglie dai nascosti) un canale della sorgente attiva. */
    suspend fun unhideChannel(channelId: String) {
        val scope = activeProfileId.first() ?: DEFAULT_HIDDEN_SCOPE
        dataStore.edit { prefs ->
            val map = decodeHiddenChannels(prefs[Keys.HIDDEN_CHANNELS].orEmpty()).toMutableMap()
            val updated = map[scope].orEmpty() - channelId
            if (updated.isEmpty()) map.remove(scope) else map[scope] = updated
            if (map.isEmpty()) prefs.remove(Keys.HIDDEN_CHANNELS) else {
                prefs[Keys.HIDDEN_CHANNELS] = encodeHiddenChannels(map)
            }
        }
    }

    /** Ripristina TUTTI i canali nascosti della sorgente attiva. */
    suspend fun unhideAllChannels() {
        val scope = activeProfileId.first() ?: DEFAULT_HIDDEN_SCOPE
        dataStore.edit { prefs ->
            val map = decodeHiddenChannels(prefs[Keys.HIDDEN_CHANNELS].orEmpty()).toMutableMap()
            map.remove(scope)
            if (map.isEmpty()) prefs.remove(Keys.HIDDEN_CHANNELS) else {
                prefs[Keys.HIDDEN_CHANNELS] = encodeHiddenChannels(map)
            }
        }
    }

    /**
     * Persiste il filtro per gruppo della Home: con null rimuove la
     * preferenza (equivalente al chip "Tutti").
     */
    suspend fun setSelectedGroup(group: String?) {
        dataStore.edit { prefs ->
            if (group.isNullOrBlank()) prefs.remove(Keys.SELECTED_GROUP)
            else prefs[Keys.SELECTED_GROUP] = group
        }
    }

    /**
     * Persiste il filtro "solo con guida TV": con false rimuove la
     * preferenza (il valore d'assenza equivale al filtro spento).
     */
    suspend fun setGuideOnly(enabled: Boolean) {
        dataStore.edit { prefs ->
            if (enabled) prefs[Keys.GUIDE_ONLY] = true
            else prefs.remove(Keys.GUIDE_ONLY)
        }
    }

    /**
     * Aggiunge (o aggiorna) un promemoria programma. La stessa coppia
     * (canale, inizio) non viene mai duplicata; l'elenco resta ordinato per
     * inizio e limitato a [MAX_REMINDERS] voci (i più lontani nel tempo
     * scartando gli ultimi, dopo aver tenuto i più vicini).
     */
    suspend fun addReminder(reminder: ProgrammeReminder) {
        dataStore.edit { prefs ->
            val current = decodeReminders(prefs[Keys.REMINDERS].orEmpty())
                .filterNot { it.channelId == reminder.channelId && it.start == reminder.start }
            val updated = (current + reminder)
                .sortedBy { it.start }
                .take(MAX_REMINDERS)
            prefs[Keys.REMINDERS] = encodeReminders(updated)
        }
    }

    /** Rimuove il promemoria per (canale, inizio); idempotente. */
    suspend fun removeReminder(channelId: String, start: Long) {
        dataStore.edit { prefs ->
            val updated = decodeReminders(prefs[Keys.REMINDERS].orEmpty())
                .filterNot { it.channelId == channelId && it.start == start }
            if (updated.isEmpty()) prefs.remove(Keys.REMINDERS)
            else prefs[Keys.REMINDERS] = encodeReminders(updated)
        }
    }

    /**
     * Aggiunge un profilo di sorgente (feature "profilazione") e lo
     * restituisce con l'id appena generato: la UI lo usa per selezionarlo
     * subito come attivo se vuole.
     */
    suspend fun addSourceProfile(name: String, playlistUrl: String, epgUrl: String): SourceProfile {
        val profile = SourceProfile(
            id = newProfileId(),
            name = sanitizeProfileField(name).ifBlank { "Profilo" },
            playlistUrl = playlistUrl.trim(),
            epgUrl = epgUrl.trim(),
        )
        dataStore.edit { prefs ->
            val current = decodeSourceProfiles(prefs[Keys.SOURCE_PROFILES].orEmpty())
            prefs[Keys.SOURCE_PROFILES] = encodeSourceProfiles(current + profile)
        }
        return profile
    }

    /** Sostituisce il profilo con lo stesso id (nome/URL modificati). */
    suspend fun updateSourceProfile(profile: SourceProfile) {
        dataStore.edit { prefs ->
            val current = decodeSourceProfiles(prefs[Keys.SOURCE_PROFILES].orEmpty())
            val updated = current.filterNot { it.id == profile.id } + profile.copy(
                name = sanitizeProfileField(profile.name),
                playlistUrl = profile.playlistUrl.trim(),
                epgUrl = profile.epgUrl.trim(),
            )
            prefs[Keys.SOURCE_PROFILES] = encodeSourceProfiles(updated)
        }
    }

    /**
     * Crea o SOVRASCRIVE un profilo con il nome indicato (feature
     * export/import a livello di profilo): se esiste già un profilo con lo
     * stesso nome (confronto senza distinzione di maiuscole, dopo
     * sanitizzazione) ne mantiene l'ID e aggiorna nome/URL — così l'eventuale
     * selezione attiva resta valida e non saltella su un profilo nuovo;
     * altrimenti ne crea uno nuovo con id fresco. Ritorna il profilo salvato.
     */
    suspend fun upsertSourceProfileByName(
        name: String,
        playlistUrl: String,
        epgUrl: String,
    ): SourceProfile {
        var saved = SourceProfile(
            id = newProfileId(),
            name = sanitizeProfileField(name).ifBlank { "Profilo" },
            playlistUrl = playlistUrl.trim(),
            epgUrl = epgUrl.trim(),
        )
        dataStore.edit { prefs ->
            val current = decodeSourceProfiles(prefs[Keys.SOURCE_PROFILES].orEmpty())
            val existing = current.firstOrNull { it.name.equals(saved.name, ignoreCase = true) }
            if (existing != null) saved = saved.copy(id = existing.id)
            val updated = if (existing != null) {
                current.map { if (it.id == existing.id) saved else it }
            } else {
                current + saved
            }
            prefs[Keys.SOURCE_PROFILES] = encodeSourceProfiles(updated)
        }
        return saved
    }

    /**
     * Rimuove il profilo indicato; se era quello attivo la selezione torna
     * alla sorgente automatica (nessun profilo pendente fantasma).
     */
    suspend fun removeSourceProfile(profileId: String) {
        dataStore.edit { prefs ->
            val updated = decodeSourceProfiles(prefs[Keys.SOURCE_PROFILES].orEmpty())
                .filterNot { it.id == profileId }
            if (updated.isEmpty()) prefs.remove(Keys.SOURCE_PROFILES)
            else prefs[Keys.SOURCE_PROFILES] = encodeSourceProfiles(updated)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) prefs.remove(Keys.ACTIVE_PROFILE_ID)
        }
    }

    /**
     * Seleziona il profilo di sorgente attivo; con null torna la sorgente
     * automatica (iptv-org, catena storica). Non tocca la playlist locale
     * importata: esiste a monte per tutte le sorgenti ed e' la UI a deciderne
     * l'uscita quando la selezione cambia (come gia' per paese e URL).
     */
    suspend fun setActiveProfileId(profileId: String?) {
        dataStore.edit { prefs ->
            if (profileId.isNullOrBlank()) prefs.remove(Keys.ACTIVE_PROFILE_ID)
            else prefs[Keys.ACTIVE_PROFILE_ID] = profileId
        }
    }

    /**
     * Migrazione delle preferenze salvate PRIMA delle nuove semantiche
     * "campo vuoto = automatico" (fix15/16) e "paese in preferenza dedicata"
     * (fix19):
     *  - playlistUrl uguale al vecchio default pre-compilato (iptv-org
     *    Italia) -> viene svuotato: era scritto dall'app, non scelto
     *    dall'utente, e ora significa "automatico";
     *  - playlistUrl di un altro paese curato iptv-org riconosciuto come
     *    scritto dall'app dal selettore paese pre-fix19 (che persisteva la
     *    scelta dentro playlistUrl, valorizzando a torto il campo URL delle
     *    Impostazioni) -> la scelta viene spostata nella preferenza
     *    [COUNTRY_CODE] e il campo viene svuotato;
     *  - epgUrl generato dall'app (i vecchi chip guide rapide, riconosciuto
     *    da [Config.isAutoDerivableEpgUrl]) -> viene rimosso: la guida ora
     *    deriva da sola dalla playlist iptv-org.
     * Gli URL davvero personali (playlist e guida custom) restano intatti.
     * Idempotente: su preferenze già migrate non fa nulla.
     */
    suspend fun migrateLegacyDefaults() {
        val current = dataStore.data.first()
        val playlist = current[Keys.PLAYLIST_URL].orEmpty().trim()
        val legacyDefaultPlaylist = playlist == Config.DEFAULT_PLAYLIST_URL
        val legacyCountryPlaylist = Config.countryCodeOfPlaylistUrl(playlist)
            ?.takeIf { playlist != Config.DEFAULT_PLAYLIST_URL }
        val legacyEpg = current[Keys.EPG_URL]
            ?.let { Config.isAutoDerivableEpgUrl(it) } == true
        if (!legacyDefaultPlaylist && legacyCountryPlaylist == null && !legacyEpg) return

        dataStore.edit { prefs ->
            val url = prefs[Keys.PLAYLIST_URL]?.trim().orEmpty()
            when {
                // Default pre-compilato dalle versioni vecchie: automatico.
                url == Config.DEFAULT_PLAYLIST_URL -> prefs[Keys.PLAYLIST_URL] = ""
                // Paese scritto dal selettore pre-fix19: diventa la scelta
                // esplicita nella nuova preferenza, il campo si svuota.
                Config.countryCodeOfPlaylistUrl(url) != null -> {
                    Config.countryCodeOfPlaylistUrl(url)?.let { prefs[Keys.COUNTRY_CODE] = it }
                    prefs[Keys.PLAYLIST_URL] = ""
                }
            }
            if (prefs[Keys.EPG_URL]?.let { Config.isAutoDerivableEpgUrl(it) } == true) {
                prefs.remove(Keys.EPG_URL)
            }
        }
    }

    companion object {
        const val MAX_RECENTS = 15

        /** Promemoria programmi tenuti in memoria: più che sufficienti. */
        const val MAX_REMINDERS = 30

        /**
         * Scope dei canali nascosti per la sorgente automatica (nessun
         * profilo attivo): la chiave del bucket dentro hidden_channels.
         */
        const val DEFAULT_HIDDEN_SCOPE = "default"

        // Codifica compatta: una voce per riga, campi separati da tab.
        private fun encodeRecents(entries: List<RecentEntry>): String =
            entries.joinToString("\n") { "${it.channelId}\t${it.timestamp}" }

        private fun decodeRecents(raw: String): List<RecentEntry> =
            raw.split('\n')
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    val id = parts.getOrNull(0) ?: return@mapNotNull null
                    val ts = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                    RecentEntry(id, ts)
                }

        // Stessa codifica compatta dei recenti (righe + tab): canale, inizio,
        // titolo. Il titolo è sanitizzato (tab/a-capo -> spazio) in scrittura:
        // nella guida arriva dall'XMLTV e non è sotto il nostro controllo.
        private fun encodeReminders(entries: List<ProgrammeReminder>): String =
            entries.joinToString("\n") {
                "${it.channelId}\t${it.start}\t${it.title.replace(Regex("[\\t\\r\\n]"), " ")}"
            }

        private fun decodeReminders(raw: String): List<ProgrammeReminder> =
            raw.split('\n')
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 3)
                    val id = parts.getOrNull(0) ?: return@mapNotNull null
                    val start = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                    val title = parts.getOrNull(2).orEmpty().trim()
                    ProgrammeReminder(id, start, title)
                }

        // Codifica dei profili di sorgente: stessa tecnica compatta di recenti
        // e promemoria (righe + tab), quattro campi per voce. Nome e URL sono
        // sanitizzati in scrittura (tab/a-capo -> spazio) e le voci senza id
        // sono scartate in lettura: un profilo corrotto non blocca gli altri.
        private fun newProfileId(): String =
            UUID.randomUUID().toString().replace("-", "").take(12)

        private fun sanitizeProfileField(value: String): String =
            value.replace(Regex("[\\t\\r\\n]"), " ").trim()

        private fun encodeSourceProfiles(profiles: List<SourceProfile>): String =
            profiles.joinToString("\n") { p ->
                listOf(
                    p.id,
                    sanitizeProfileField(p.name),
                    sanitizeProfileField(p.playlistUrl),
                    sanitizeProfileField(p.epgUrl),
                ).joinToString("\t")
            }

        private fun decodeSourceProfiles(raw: String): List<SourceProfile> =
            raw.split('\n')
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t', limit = 4)
                    val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SourceProfile(
                        id = id,
                        name = parts.getOrNull(1).orEmpty().ifBlank { "Profilo" },
                        playlistUrl = parts.getOrNull(2).orEmpty().trim(),
                        epgUrl = parts.getOrNull(3).orEmpty().trim(),
                    )
                }

        // Codifica dei canali nascosti: una riga per sorgente, primo campo =
        // scope (id profilo o DEFAULT_HIDDEN_SCOPE), campi seguenti = id
        // canale. Gli id non dovrebbero contenere tab/a-capo (hash esadecimale
        // o tvg-id): sanitizzati in scrittura E in lettura; righe malformate
        // (senza scope) scartate, un bucket corrotto non blocca gli altri.
        private fun hiddenChannelIdOf(value: String): String =
            value.replace(Regex("[\\t\\r\\n]"), "")

        private fun encodeHiddenChannels(map: Map<String, Set<String>>): String =
            map.entries.joinToString("\n") { (scope, ids) ->
                (listOf(scope) + ids.map(::hiddenChannelIdOf)).joinToString("\t")
            }

        private fun decodeHiddenChannels(raw: String): Map<String, Set<String>> =
            raw.split('\n')
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    val scope = parts.getOrNull(0)?.takeIf(String::isNotBlank)
                        ?: return@mapNotNull null
                    val ids = parts.drop(1)
                        .map(::hiddenChannelIdOf)
                        .filter(String::isNotBlank)
                        .toSet()
                    if (ids.isEmpty()) null else scope to ids
                }
                .toMap()

        // I preferiti per profilo riusano la STESSA codifica a bucket dei
        // canali nascosti (scope \t id \t id...): due chiavi diverse nel
        // DataStore, un solo formato. Gli id canale sono hash esadecimali o
        // tvg-id, senza tab/a-capo, e la sanitizzazione del codec copre anche
        // questi; un bucket corrotto non blocca gli altri.
        private fun encodeFavoriteBuckets(map: Map<String, Set<String>>): String =
            encodeHiddenChannels(map)

        private fun decodeFavoriteBuckets(raw: String): Map<String, Set<String>> =
            decodeHiddenChannels(raw)
    }
}
