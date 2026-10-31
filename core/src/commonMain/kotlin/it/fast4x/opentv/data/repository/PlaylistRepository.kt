package it.fast4x.opentv.data.repository

import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.remote.ChannelEnricher
import it.fast4x.opentv.data.remote.HttpException
import it.fast4x.opentv.data.remote.M3UParser
import it.fast4x.opentv.data.remote.M3UWriter
import it.fast4x.opentv.data.remote.PlaylistFetcher
import it.fast4x.opentv.data.remote.ProfileCodec
import it.fast4x.opentv.data.remote.ProfileExportBundle
import it.fast4x.opentv.data.store.UserPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Repository dei canali: scarica la playlist M3U, la parsa, la salva su file
 * in [filesDir] (storage persistente, mai cancellato dal sistema) con un TTL
 * di refresh, e la espone come [StateFlow].
 *
 * In caso di rete assente ricade automaticamente sull'ultima copia valida,
 * anche se scaduta: l'app funziona sempre con l'ultima lista nota.
 *
 * Dopo l'emissione della lista può lanciare in background un passaggio di
 * [ChannelEnricher] (loghi/categorie mancanti delle playlist grezze): mai
 * bloccante, sostituisce lo stato solo se almeno un canale è cambiato.
 *
 * Multiplatform: la directory di lavoro ([filesDir]), la lettura della
 * sorgente locale ([localPlaylistReader]) e i messaggi utente ([strings])
 * vengono iniettati dalla piattaforma; la logica è identica ovunque.
 */
class PlaylistRepository(
    private val prefs: UserPrefsStore,
    private val filesDir: File,
    private val localPlaylistReader: LocalPlaylistReader,
    private val strings: PlaylistStrings = ItalianPlaylistStrings,
) {

    private val mutex = Mutex()

    /** Scope proprio per l'arricchimento in background (fuori dal mutex). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PlaylistState>(PlaylistState.Loading)
    val state: StateFlow<PlaylistState> = _state.asStateFlow()

    /**
     * Guida TV INCORPORATA nella playlist attiva (fix35): i candidati URL
     * dichiarati dall'header `#EXTM3U x-tvg-url=` (o `url-tvg`/`tvg-url`),
     * selezionati da [M3UParser.selectEpgUrls] in base ai canali caricati e
     * ordinati per punteggio (il primo è il migliore). Viene aggiornata a
     * ogni caricamento riuscito della lista (remota, importata, cache) e
     * alimenta la risoluzione della guida tramite
     * [SourceResolver.resolveEpgChain]: una lista che dichiara la propria
     * guida la ottiene senza configurazione. Vuota = nessuna guida
     * dichiarata (o lista non ancora caricata).
     */
    private val _embeddedEpgChain = MutableStateFlow<List<String>>(emptyList())
    val embeddedEpgChain: StateFlow<List<String>> = _embeddedEpgChain.asStateFlow()

    /**
     * Scarica e carica la playlist.
     *
     * @param force se true ignora la cache fresca e riscarica (usato da "Aggiorna"
     *              e dopo la modifica dell'URL nelle impostazioni).
     */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val settings = prefs.settings.first()
        // Profili di sorgente (feature "profilazione della lista"): se un
        // profilo valido e' attivo la sua playlist BYPASSA la catena storica
        // (URL personale > paese > predefinita) e il fallback verso le liste
        // iptv-org predefinite non viene piu' inserito: una sorgente scelta
        // esplicitamente non deve mai degradare in silenzio su un'altra lista
        // (restano il fallback offline sulla cache e l'errore visibile).
        val activeProfile = SourceResolver.activeProfile(
            profiles = prefs.sourceProfiles.first(),
            activeProfileId = prefs.activeProfileId.first(),
        )
        val profileUrl = SourceResolver.playlistUrlOf(activeProfile)
        // Risoluzione della sorgente remota (fix19): URL personale > playlist
        // curata del paese scelto nei selettori (preferenza dedicata
        // countryCode) > predefinita per paese del dispositivo. Il campo URL
        // significa quindi solo "lista personale": la scelta del paese vive
        // in un'altra preferenza e non valorizza più il campo URL delle
        // Impostazioni.
        val manualUrl = settings.playlistUrl.trim()
        val selectedCountry = Config.normalizeCountryCode(settings.countryCode)
        val configuredUrl = when {
            profileUrl != null -> profileUrl
            manualUrl.isNotEmpty() -> manualUrl
            selectedCountry != null -> Config.countryPlaylistUrl(selectedCountry)
            else -> Config.DEFAULT_PLAYLIST_URL
        }
        // filesDir (e non cacheDir): la lista deve sopravvivere anche quando
        // lo storage del dispositivo è sotto pressione.
        val cache = File(filesDir, CACHE_FILE)
        val cacheFresh = cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < Config.PLAYLIST_CACHE_TTL_MS

        val alreadyLoaded = (_state.value as? PlaylistState.Success)?.channels.orEmpty()

        // Sorgente locale (playlist importata da file): ha priorità sull'URL
        // remoto. Con force=true ("Aggiorna") il file viene riletto dal disco;
        // senza force e con la lista già a schermo non si tocca nulla, come
        // per la cache remota. Se il file è illeggibile o vuoto (permesso
        // perso, file spostato, scheda SD rimossa) si prosegue con la catena
        // URL/cache standard: l'app mostra comunque una lista.
        val localUri = settings.localPlaylistUri.trim()
        if (localUri.isNotEmpty()) {
            if (!force && alreadyLoaded.isNotEmpty()) return@withLock
            val text = withContext(Dispatchers.IO) { localPlaylistReader.read(localUri) }
            if (text != null) {
                val channels = M3UParser.parse(text)
                if (channels.isNotEmpty()) {
                    _embeddedEpgChain.value = embeddedEpgChainOf(text, channels)
                    // Copia anche nel file cache: resta il fallback offline
                    // se il file sorgente dovesse diventare irraggiungibile.
                    withContext(Dispatchers.IO) { runCatching { cache.writeText(text) } }
                    emitAndEnrich(PlaylistState.Success(channels, fromCache = false))
                    return@withLock
                }
            }
        }

        // Cache fresca: usala se non c'è ancora nulla a schermo, altrimenti non toccare nulla.
        if (!force && cacheFresh) {
            if (alreadyLoaded.isEmpty() && cache.exists()) {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { cache.readText() }.getOrNull()
                }
                if (cached != null) parseAndEmit(cached, fromCache = true)
            }
            return@withLock
        }

        if (alreadyLoaded.isEmpty()) {
            _state.value = PlaylistState.Loading
        }

        // Catena di fallback: URL configurato -> sorgente ufficiale -> mirror.
        // Se un host è bloccato o irraggiungibile dal dispositivo (DNS, filtro
        // di rete, IPv6 mezzo rotto, ora di sistema errata), proviamo in
        // automatico il sorgente successivo prima di dichiarare l'errore.
        // Con un profilo attivo la catena e' SOLO il suo URL (vedi sopra).
        val candidates = if (profileUrl != null) {
            listOf(configuredUrl)
        } else {
            listOf(
                configuredUrl,
                Config.DEFAULT_PLAYLIST_URL,
                Config.FALLBACK_PLAYLIST_URL,
            ).distinct()
        }

        var lastError: Exception? = null
        // Tutto il blocco (rete + parse + cache su disco) gira su Dispatchers.IO:
        // refresh() viene invocato da viewModelScope (Main) e OkHttp execute() è
        // bloccante — senza questo switch si otterrebbe
        // NetworkOnMainThreadException.
        val fresh = withContext(Dispatchers.IO) {
            for (candidate in candidates) {
                try {
                    val text = PlaylistFetcher.fetchText(candidate)
                    val channels = M3UParser.parse(text)
                    if (channels.isEmpty()) {
                        lastError = IOException(strings.emptyPlaylist())
                        continue // sorgente degradato: prova il successivo
                    }
                    _embeddedEpgChain.value = embeddedEpgChainOf(text, channels)
                    runCatching { cache.writeText(text) } // la cache non deve bloccare il flusso
                    return@withContext channels
                } catch (error: Exception) {
                    lastError = error
                }
            }
            null
        }

        if (fresh != null) {
            emitAndEnrich(PlaylistState.Success(fresh, fromCache = false))
            return@withLock
        }

        val failure = lastError ?: IOException(strings.network())
        if (alreadyLoaded.isEmpty() && cache.exists()) {
            // Tutti i sorgenti falliti: prova l'ultima cache nota.
            val cached = withContext(Dispatchers.IO) {
                runCatching { cache.readText() }.getOrNull()
            }
            val cachedChannels = cached?.let { text ->
                runCatching { M3UParser.parse(text) }.getOrNull()
            }
            if (!cachedChannels.isNullOrEmpty()) {
                _embeddedEpgChain.value = embeddedEpgChainOf(cached, cachedChannels)
                emitAndEnrich(PlaylistState.Success(cachedChannels, fromCache = true))
            } else {
                _state.value = PlaylistState.Error(readableError(failure))
            }
        } else if (alreadyLoaded.isEmpty()) {
            _state.value = PlaylistState.Error(readableError(failure))
        }
        // Se la lista era già a schermo la manteniamo: l'utente continua a guardare la TV.
    }

    /** Ricerca un canale per id nello stato corrente (null se non presente). */
    fun channelById(id: String): Channel? =
        (_state.value as? PlaylistState.Success)?.channels?.firstOrNull { it.id == id }

    /**
     * Importa una playlist da file locale come sorgente attiva persistente.
     *
     * Il testo è già stato letto dalla piattaforma (SAF su Android, selettore
     * file su desktop); [sourceId] è l'identificatore opaco della sorgente
     * (URI SAF o percorso file) che viene persistito e ri-letto ad ogni
     * [refresh] tramite [localPlaylistReader]. "Salva" o "Ripristina
     * predefinito" lo rimuovono. Una copia del testo va anche nel file cache,
     * così il fallback offline continua a funzionare anche se il file
     * originale viene spostato o cancellato.
     *
     * @return il numero di canali importati, o un failure con un messaggio
     *         già pronto per l'utente.
     */
    suspend fun importPlaylist(text: String, sourceId: String): Result<Int> = mutex.withLock {
        val channels = M3UParser.parse(text)
        if (channels.isEmpty()) {
            return@withLock Result.failure(IOException(strings.emptyPlaylist()))
        }
        _embeddedEpgChain.value = embeddedEpgChainOf(text, channels)

        prefs.setLocalPlaylistUri(sourceId)

        withContext(Dispatchers.IO) {
            runCatching { File(filesDir, CACHE_FILE).writeText(text) }
        }
        emitAndEnrich(PlaylistState.Success(channels, fromCache = false))
        Result.success(channels.size)
    }

    /**
     * Prepara l'esportazione della lista canali attualmente caricata: il
     * testo M3U è prodotto da [M3UWriter] ed è riassorbibile con
     * [importPlaylist] senza perdita di metadati (tvg-id, loghi, gruppi,
     * intestazioni, badge geo-blocco). La scrittura su file è a carico della
     * piattaforma (SAF su Android, selettore file su desktop).
     *
     * @return il numero di canali e il testo M3U da scrivere, o un failure
     *         con un messaggio già pronto per l'utente.
     */
    suspend fun exportPlaylistText(): Result<PlaylistExport> = mutex.withLock {
        val channels = (_state.value as? PlaylistState.Success)?.channels.orEmpty()
        if (channels.isEmpty()) {
            Result.failure(IOException(strings.noChannels()))
        } else {
            // fix35: la guida incorporata (se presente) viaggia nel file
            // esportato: la re-importazione la ricarica senza riconfigurare.
            Result.success(
                PlaylistExport(
                    channelCount = channels.size,
                    m3uText = M3UWriter.build(channels, _embeddedEpgChain.value.firstOrNull()),
                ),
            )
        }
    }

    /**
     * Prepara il pacchetto "profilo + canali" (feature export/import a
     * livello di profilo): il profilo con i CANALI della SUA playlist, non
     * della lista attiva in generale.
     *
     * Se il profilo è quello attivo (e non c'è una playlist locale importata
     * che lo maschera) si riusa la lista già caricata, che è esattamente la
     * sua e arriva anche arricchita di loghi/categorie; altrimenti la playlist
     * del profilo viene scaricata al momento: il file esportato è così
     * sempre completo, anche per profili mai attivati in questa sessione.
     *
     * La scrittura su file resta alla piattaforma (SAF su Android, selettore
     * su desktop) tramite [ProfileCodec.encode].
     */
    suspend fun exportProfileBundle(profile: SourceProfile): Result<ProfileExportBundle> {
        val settings = prefs.settings.first()
        val activeId = prefs.activeProfileId.first()
        val usesActiveList = activeId == profile.id && settings.localPlaylistUri.isBlank()
        var channels: List<Channel> = emptyList()
        var embedded: String? = null
        if (usesActiveList) {
            channels = (_state.value as? PlaylistState.Success)?.channels.orEmpty()
            embedded = _embeddedEpgChain.value.firstOrNull()
        }
        if (channels.isEmpty()) {
            val url = profile.playlistUrl.trim()
            if (url.isEmpty()) {
                return Result.failure(IOException(strings.profileNoPlaylist()))
            }
            try {
                val text = withContext(Dispatchers.IO) { PlaylistFetcher.fetchText(url) }
                channels = M3UParser.parse(text)
                if (channels.isEmpty()) {
                    return Result.failure(IOException(strings.emptyPlaylist()))
                }
                embedded = runCatching {
                    M3UParser.selectEpgUrls(M3UParser.parseEpgUrls(text), channels).firstOrNull()
                }.getOrNull()
            } catch (error: Exception) {
                // Errori di rete/HTTP già in forma leggibile (readableError):
                // il messaggio finisce dritto nel feedback delle impostazioni.
                return Result.failure(IOException(readableError(error)))
            }
        }
        return Result.success(ProfileExportBundle(profile, channels, embedded))
    }

    private fun parseAndEmit(text: String, fromCache: Boolean) {
        val channels = M3UParser.parse(text)
        if (channels.isEmpty()) {
            _state.value = PlaylistState.Error(strings.emptyPlaylist())
        } else {
            _embeddedEpgChain.value = embeddedEpgChainOf(text, channels)
            emitAndEnrich(PlaylistState.Success(channels, fromCache))
        }
    }

    /**
     * Catena guida incorporata del testo playlist caricato (fix35): URL
     * dichiarate nell'header + selezione per punteggio sui canali appena
     * caricati. Con un errore di parsing non blocca il caricamento lista:
     * senza candidati la guida prosegue con la risoluzione storica.
     */
    private fun embeddedEpgChainOf(text: String, channels: List<Channel>): List<String> =
        runCatching {
            M3UParser.selectEpgUrls(M3UParser.parseEpgUrls(text), channels)
        }.getOrDefault(emptyList())

    /**
     * Emette lo stato e, se la lista ha canali arricchibili (senza logo o
     * categoria ma con tvg-id nazionale), lancia l'arricchimento in background.
     * L'aggiornamento viene applicato solo se la lista a schermo è ancora la
     * stessa (l'utente non ha nel frattempo cambiato URL o refreshato).
     */
    private fun emitAndEnrich(state: PlaylistState.Success) {
        _state.value = state
        if (!ChannelEnricher.hasEnrichableChannels(state.channels)) return
        scope.launch {
            val enriched = ChannelEnricher.fillMissingArtwork(filesDir, state.channels)
            if (enriched !== state.channels) {
                val current = _state.value
                if (current is PlaylistState.Success && current.channels === state.channels) {
                    _state.value = current.copy(channels = enriched)
                }
            }
        }
    }

    private fun readableError(error: Exception): String = when (error) {
        is HttpException ->
            strings.http(error.code)

        // Su molte TV box il fallimento TLS deriva da data/ora di sistema
        // errate o da filtri DNS: diamo un messaggio più utile del generico.
        is SSLHandshakeException ->
            strings.ssl()

        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException -> strings.network()

        is IOException -> error.message ?: strings.network()
        else -> error.message ?: error.javaClass.simpleName
    }

    private companion object {
        const val CACHE_FILE = "opentv_playlist.m3u"
    }
}

/** Risultato dell'esportazione: numero di canali + testo M3U pronto da scrivere. */
data class PlaylistExport(
    val channelCount: Int,
    val m3uText: String,
)
