package it.fast4x.opentv.desktop

import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.GroupInfo
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.RecentEntry
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.model.channelGroupKeys
import it.fast4x.opentv.data.model.channelGroups
import it.fast4x.opentv.data.remote.StreamResolver
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.SourceResolver
import it.fast4x.opentv.data.repository.epgKey
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.store.UserPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

/**
 * Stati della UI desktop: stessi flussi e stessa logica degli ViewModel
 * Android (HomeViewModel e PlayerViewModel), senza androidx.lifecycle perché
 * il desktop usa scope Compose diretti.
 */

/** Stato aggregato per la schermata Home (specchio di HomeUiState Android). */
data class DesktopHomeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val channels: List<Channel> = emptyList(),
    val fromCache: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val favoriteChannels: List<Channel> = emptyList(),
    val recents: List<Channel> = emptyList(),
    val query: String = "",
    val filteredChannels: List<Channel> = emptyList(),
    val groupedChannels: List<Pair<Char, List<Channel>>> = emptyList(),
    val groups: List<GroupInfo> = emptyList(),
    val selectedGroup: String? = null,
    val groupChannels: List<Channel> = emptyList(),
    val epgNow: Map<String, Programme> = emptyMap(),
    /** Filtro rapido "solo preferiti" attivo (stato di sessione, non persistito). */
    val favoritesOnly: Boolean = false,
    /** Preferiti già intersecati con gruppo e ricerca, pronti per l'elenco. */
    val favoriteResults: List<Channel> = emptyList(),
    /**
     * Filtro "solo con guida TV" (feature dedicata): persistito nel
     * DataStore, gemello di HomeUiState Android.
     */
    val guideOnly: Boolean = false,
    /** Canali della sorgente attiva coperti dalla guida TV (per il chip). */
    val channelsWithGuide: Int = 0,
)

private data class HomeCoreState(
    val playlist: PlaylistState,
    val favorites: Set<String>,
    val recents: List<RecentEntry>,
    /** Canali nascosti della sorgente attiva (feature "nascondi canali"). */
    val hidden: Set<String>,
)

private data class HomeMetaState(
    val query: String,
    val selectedGroup: String?,
    val epg: Map<String, List<Programme>>,
    val favoritesOnly: Boolean,
    val guideOnly: Boolean,
)

class DesktopHomeState(
    private val deps: DesktopDependencies,
    val scope: CoroutineScope,
) {

    private val playlistRepository: PlaylistRepository = deps.playlistRepository
    private val prefs: UserPrefsStore = deps.prefs

    private val queryFlow = MutableStateFlow("")

    /**
     * Filtro rapido "solo preferiti" (chip stella): volutamente di sessione,
     * non persistito nel DataStore — è un commutatore momentaneo, diversamente
     * dal filtro per gruppo che invece sopravvive al riavvio. Stessa scelta
     * fatta sull'app Android (HomeViewModel).
     */
    private val favoritesOnlyFlow = MutableStateFlow(false)

    /**
     * Paese scelto nei selettori (preferenza dedicata, fix19): serve alla
     * Home per evidenziare la voce attiva nel selettore. Null = modalità
     * automatica (paese del dispositivo) o URL personale impostato nelle
     * Impostazioni.
     */
    val currentCountry: StateFlow<String?> = prefs.settings
        .map { it.countryCode }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            // Null = automatica: nessun paese implicito prima della prima
            // emissione, il selettore parte già su "Automatica".
            initialValue = null,
        )

    /**
     * Profili di sorgente (feature "profilazione della lista") e profilo
     * attivo: servono al menu sorgente della Home. Null = sorgente
     * automatica (iptv-org, catena storica URL personale/paese/predefinita).
     */
    val profiles: StateFlow<List<SourceProfile>> = prefs.sourceProfiles
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    val activeProfileId: StateFlow<String?> = prefs.activeProfileId
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val epgTick = flow {
        while (true) {
            emit(Unit)
            delay(EPG_REFRESH_MS)
        }
    }

    private val core = combine(
        playlistRepository.state,
        // Preferiti della SORGENTE ATTIVA (feature "preferiti per profilo"):
        // con un profilo attivo il set arriva dal bucket del profilo, senza
        // profilo dal bucket storico della lista automatica (specchio Android).
        prefs.favoritesForActiveScope(),
        prefs.recents,
        // Canali nascosti della sorgente attiva (feature "nascondi canali"):
        // filtro ALLA RADICE, tutte le viste ereditano (specchio Android).
        prefs.hiddenChannelsForActiveScope(),
    ) { playlist, favorites, recents, hidden ->
        HomeCoreState(playlist, favorites, recents, hidden)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeCoreState(PlaylistState.Loading, emptySet(), emptyList(), emptySet()),
    )

    private val meta = combine(
        queryFlow,
        // Il filtro per gruppo persiste in DataStore: selezionare un chip
        // lo salva e riaprendo l'app il filtro è ancora attivo.
        deps.prefs.selectedGroup,
        deps.epgRepository.programmes,
        favoritesOnlyFlow,
        // Anche il filtro "solo con guida TV" persiste (feature dedicata).
        deps.prefs.guideOnly,
    ) { query, group, epg, favoritesOnly, guideOnly ->
        HomeMetaState(query, group, epg, favoritesOnly, guideOnly)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeMetaState("", null, emptyMap(), false, false),
    )

    val uiState: StateFlow<DesktopHomeUiState> =
        combine(core, meta, epgTick) { coreState, metaState, _ ->
            val playlist = coreState.playlist
            // I nascosti spariscono alla fonte: tutte le viste partono da
            // questa lista già filtrata (feature "nascondi canali").
            val base = ((playlist as? PlaylistState.Success)?.channels ?: emptyList())
                .filter { it.id !in coreState.hidden }
            val now = System.currentTimeMillis()

            // Passata unica sulla lista base per la guida TV: per ogni canale
            // la chiave (base o alias per nome, fix35) si risolve UNA volta e
            // da li' escono sia il programma in onda sia l'insieme dei canali
            // coperti dalla guida, che alimenta il filtro "solo con guida TV"
            // e il conteggio del chip.
            val epgNow = HashMap<String, Programme>()
            val guideIds = HashSet<String>()
            val epgMap = metaState.epg
            if (epgMap.isNotEmpty()) {
                for (channel in base) {
                    val list = epgMap[epgKey(channel.id)]
                        ?: deps.epgRepository.aliasKeyFor(channel)?.let { epgMap[it] }
                    if (list.isNullOrEmpty()) continue
                    guideIds += channel.id
                    list.lastOrNull { it.start <= now && now < it.stop }?.let { epgNow[channel.id] = it }
                }
            }

            // Filtro "solo con guida TV": attivo SOLO se la guida copre almeno
            // un canale della sorgente (altrimenti nasconderebbe l'intera
            // lista). Filtrando alla radice, ogni vista eredita l'effetto.
            val channels = if (metaState.guideOnly && guideIds.isNotEmpty()) {
                base.filter { it.id in guideIds }
            } else {
                base
            }

            val recents = coreState.recents.mapNotNull { entry ->
                channels.firstOrNull { it.id == entry.channelId }
            }
            val favoriteChannels = channels
                .filter { it.id in coreState.favorites }
                .sortedBy { it.name.lowercase() }

            // fix32 — Il valore del CAMPO di ricerca deve restare fedele a ciò
            // che l'utente digita: prima la query trimmata tornava anche al
            // TextField controllato e lo spazio digitato in coda ("Italia ")
            // veniva rimosso dal flusso prima del frame successivo: non era
            // possibile scrivere canali con lo spazio nel nome ("Italia 1").
            // Il trim qui serve SOLO ai filtri; al campo torna la query RAW.
            val rawQuery = metaState.query
            val query = rawQuery.trim()
            val groups = channelGroups(channels)
            // Il gruppo persistito potrebbe non esistere più (playlist
            // cambiata): lo ignoriamo visualmente senza cancellarlo.
            val selectedGroup = metaState.selectedGroup
                ?.takeIf { sel -> groups.any { it.key == sel } }
            val normalizedQuery = normalize(query)
            val filtered = if (query.isEmpty()) {
                emptyList()
            } else {
                channels.filter { normalize(it.name).contains(normalizedQuery) }
            }

            // Canali del gruppo selezionato, con l'eventuale ricerca applicata:
            // filtro genere e ricerca si combinano (intersezione).
            val groupChannels = if (selectedGroup == null) {
                emptyList()
            } else {
                channels
                    .filter { channel ->
                        selectedGroup in channelGroupKeys(channel) &&
                            (query.isEmpty() || normalize(channel.name).contains(normalizedQuery))
                    }
                    .sortedBy { it.name.lowercase() }
            }

            // Filtro "solo preferiti": la lista dei preferiti (già ordinata per
            // nome) si interseca con gruppo e ricerca, così i tre filtri si
            // combinano come su Android.
            val favoriteResults = if (metaState.favoritesOnly) {
                favoriteChannels.filter { channel ->
                    (selectedGroup == null || selectedGroup in channelGroupKeys(channel)) &&
                        (query.isEmpty() || normalize(channel.name).contains(normalizedQuery))
                }
            } else {
                emptyList()
            }

            DesktopHomeUiState(
                loading = playlist is PlaylistState.Loading,
                error = (playlist as? PlaylistState.Error)?.message,
                channels = channels,
                fromCache = (playlist as? PlaylistState.Success)?.fromCache ?: false,
                favorites = coreState.favorites,
                favoriteChannels = favoriteChannels,
                recents = recents,
                query = rawQuery,
                filteredChannels = filtered,
                groupedChannels = groupByInitial(channels),
                groups = groups,
                selectedGroup = selectedGroup,
                groupChannels = groupChannels,
                epgNow = epgNow,
                favoritesOnly = metaState.favoritesOnly,
                favoriteResults = favoriteResults,
                guideOnly = metaState.guideOnly,
                channelsWithGuide = guideIds.size,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DesktopHomeUiState(),
        )

    init {
        scope.launch {
            // Migrazione preferenze pre-fix15 (stessa dell'app Android): i
            // vecchi default pre-compilati (campo playlist = URL Italia, campo
            // EPG = URL dei vecchi chip) diventano "campo vuoto = automatico"
            // PRIMA del primo refresh, così lista e guida partono allineate
            // e le Impostazioni mostrano i campi "Automatico".
            prefs.migrateLegacyDefaults()

            // Primo caricamento (o ricaricamento con cache scaduta) all'avvio.
            playlistRepository.refresh()
        }

        // Carica/ricarica la guida TV quando cambia l'URL effettivo: profilo
        // di sorgente attivo (feature "profilazione"), URL manuale impostato
        // dall'utente, paese scelto nei selettori, oppure automatico derivato
        // dalla playlist iptv-org (stessa risoluzione dell'app Android,
        // SourceResolver).
        // fix35: entra anche la guida INCORPORATA nella playlist (header
        // x-tvg-url), come su Android: attendere il primo esito del
        // caricamento playlist evita di scaricare la guida storica e, un
        // attimo dopo, quella incorporata.
        scope.launch {
            deps.playlistRepository.state.first { it !is PlaylistState.Loading }

            combine(
                prefs.settings,
                prefs.sourceProfiles,
                prefs.activeProfileId,
                deps.playlistRepository.embeddedEpgChain,
            ) { settings, profiles, activeId, embedded ->
                SourceResolver.resolveEpgChain(settings, profiles, activeId, embedded)
            }
                .distinctUntilChanged()
                .collectLatest { chain -> deps.epgRepository.load(chain) }
        }
    }

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    /**
     * Seleziona il filtro per gruppo (null = nessun filtro, tutti i canali).
     * La scelta viene persistita: riaprendo l'app il filtro è ancora attivo.
     */
    fun onGroupChange(group: String?) {
        scope.launch { deps.prefs.setSelectedGroup(group) }
    }

    /** Attiva/disattiva il filtro rapido "solo preferiti" (stato di sessione). */
    fun onFavoritesOnlyChange(enabled: Boolean) {
        favoritesOnlyFlow.value = enabled
    }

    /**
     * Attiva/disattiva il filtro "solo con guida TV": la scelta persiste
     * nel DataStore, riaprendo l'app il filtro è ancora com'era. Gemello
     * di HomeViewModel Android.
     */
    fun onGuideOnlyChange(enabled: Boolean) {
        scope.launch { deps.prefs.setGuideOnly(enabled) }
    }

    /**
     * Cambia il paese dei canali (playlist curata iptv-org per paese):
     * salva la scelta nella preferenza dedicata e forza il ricaricamento.
     * La guida TV segue da sola perché deriva dal flusso impostazioni (URL
     * EPG automatico per lo stesso paese).
     *
     * Fix19: la scelta NON viene più persistita come URL dentro playlistUrl
     * (che valorizzava a torto il campo URL delle Impostazioni) ma nella
     * preferenza dedicata [UserPrefsStore.setCountryCode], osservata anche
     * dal selettore delle Impostazioni: le due schermate restano quindi
     * allineate in entrambe le direzioni. Con [countryCode] null (voce
     * automatica) la preferenza si azzera e il repository carica la playlist
     * per paese del dispositivo (Italia se non supportata).
     */
    fun onCountryChange(countryCode: String?) {
        scope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setPlaylistUrl("")
            prefs.setCountryCode(countryCode)
            playlistRepository.refresh(force = true)
        }
    }

    /**
     * Cambia il profilo di sorgente attivo (feature "profilazione della
     * lista"; null = sorgente automatica iptv-org): persiste la scelta, esce
     * da un'eventuale playlist locale importata (che ha sempre priorità su
     * tutte le sorgenti) e forza il ricaricamento della lista. La guida TV
     * segue da sola: il flusso di init qui sopra risolve l'URL EPG anche dai
     * profili (SourceResolver.resolveEpg). Gemello di HomeViewModel Android.
     */
    fun onProfileChange(profileId: String?) {
        scope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setActiveProfileId(profileId)
            playlistRepository.refresh(force = true)
        }
    }

    fun toggleFavorite(channelId: String) {
        scope.launch { prefs.toggleFavorite(channelId) }
    }

    /**
     * Nasconde un canale (feature "nascondi canali"): lo store risolve da
     * solo lo scope (profilo attivo o sorgente automatica) e la lista si
     * aggiorna da sola via flusso. La UI chiede SEMPRE conferma prima.
     */
    fun hideChannel(channelId: String) {
        scope.launch { prefs.hideChannel(channelId) }
    }

    fun refresh() {
        scope.launch { playlistRepository.refresh(force = true) }
    }

    private fun groupByInitial(channels: List<Channel>): List<Pair<Char, List<Channel>>> =
        channels
            .groupBy { initialOf(it.name) }
            .toSortedMap()
            .map { (letter, list) -> letter to list }

    private fun initialOf(name: String): Char {
        val first = name.trim().firstOrNull() ?: return '#'
        return if (first.isLetter()) first.uppercaseChar() else '#'
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()

    private companion object {
        const val EPG_REFRESH_MS = 60_000L
    }
}

/**
 * Voce del menu tracce audio (porting desktop della selezione Android):
 * [id] è l'identificativo VLC della traccia (da passare a libvlc_audio_set_track,
 * NON una posizione sequenziale) e [label] è la descrizione pronta per la UI.
 * libVLC fornisce già una descrizione per traccia (di solito "Track N - [lingua]"),
 * quindi rispetto ad Android servono meno campi: lingua, audiodescrizione e
 * numero di canali sono già dentro il testo, e l'evidenziazione dell'attiva
 * arriva da [currentAudioTrackId].
 */
data class DesktopAudioTrack(
    val id: Int,
    val label: String,
)

/**
 * Voce del menu sottotitoli (gemello di [DesktopAudioTrack], feature
 * "sottotitoli"): [id] è l'identificativo VLC della traccia SPU (da passare
 * a libvlc_video_set_spu, NON una posizione sequenziale); la voce
 * "Disattivati" non è qui dentro (la UI la aggiunge da sola, selezione
 * = [DesktopPlayerState.selectSubtitleTrack] con id -1).
 */
data class DesktopSubtitleTrack(
    val id: Int,
    val label: String,
)

/** Codici lingua tra parentesi quadre nelle descrizioni VLC, es. "Track 1 - [ita]". */
private val audioTrackLanguageRegex = Regex("\\[([A-Za-z]{2,3})]")

/**
 * Prepara la descrizione VLC della traccia per la UI: descrizione vuota ->
 * "Traccia N" (N = posizione nella lista, come il fallback Android);
 * codici lingua tra parentesi quadre -> nome esteso se riconosciuto dalla
 * JVM ("Track 1 - Italiano"), altrimenti il testo resta com'è. I codici
 * riservati (qaa-qtz) non sono risolti dalla JVM come lingua e restano
 * originali, stessa difesa della versione Android.
 */
private fun prettyAudioTrackLabel(rawDescription: String?, position: Int): String {
    val text = rawDescription?.trim().orEmpty()
    if (text.isEmpty()) return "Traccia ${position + 1}"
    return audioTrackLanguageRegex.replace(text) { match ->
        val code = match.groupValues[1].lowercase()
        val display = runCatching { java.util.Locale(code).displayLanguage }.getOrNull().orEmpty()
        if (display.isNotBlank() && !display.equals(code, ignoreCase = true)) {
            display.replaceFirstChar { it.uppercase() }
        } else {
            match.value
        }
    }
}

/**
 * Stato del player desktop: risolve il canale (relinker RAI inclusa, come la
 * patch Android), applica le intestazioni per-network e pilota libVLC.
 */
class DesktopPlayerState(
    private val deps: DesktopDependencies,
    private val scope: CoroutineScope,
    initialChannelId: String,
) {

    private val _playback = MutableStateFlow<PlayerStatus>(PlayerStatus.Buffering)

    private val vlc = VlcPlayer { status ->
        if (_playback.value !is PlayerStatus.Error) _playback.value = status
        // Le tracce audio e i sottotitoli di libVLC sono leggibili solo a
        // stream avviato: a ogni evento "playing" (nuovo canale, retry,
        // recovery) riprogramma la lettura per il flusso appena aperto.
        if (status is PlayerStatus.Playing) scheduleTracksRefresh()
    }

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    val playback: StateFlow<PlayerStatus> = _playback.asStateFlow()

    private val epgTick = flow {
        while (true) {
            emit(Unit)
            delay(EPG_REFRESH_MS)
        }
    }

    /** Coppia (in onda adesso, a seguire) per il canale corrente. */
    val epg: StateFlow<Pair<Programme?, Programme?>> = combine(
        _currentChannel,
        deps.epgRepository.programmes,
        epgTick,
    ) { currentChannel, programmes, _ ->
        val id = currentChannel?.id
        if (id == null) {
            null to null
        } else {
            val now = System.currentTimeMillis()
            // Id base per il match con la guida ("Rai1.it@SD" -> "Rai1.it"),
            // con fallback per nome (fix35) per le guide con id diversi.
            val list = programmes[epgKey(id)]
                ?: currentChannel?.let { c -> deps.epgRepository.aliasKeyFor(c) }
                    ?.let { programmes[it] }
            val nowOn = list?.lastOrNull { it.start <= now && now < it.stop }
            val next = list?.firstOrNull { it.start >= now }
            nowOn to next
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null to null,
    )

    /**
     * Programmazione del canale corrente: tutti i programmi in memoria dalla
     * guida (finestra -1h → +24h) in ordine di inizio, per il pannello
     * "Programmazione" del player.
     */
    val schedule: StateFlow<List<Programme>> = combine(
        _currentChannel,
        deps.epgRepository.programmes,
        epgTick,
    ) { channel, programmes, _ ->
        channel?.let { c ->
            // Match per id base + fallback per nome (fix35).
            programmes[epgKey(c.id)]
                ?: deps.epgRepository.aliasKeyFor(c)?.let { programmes[it] }
        }.orEmpty()
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    // Preferiti della SORGENTE ATTIVA (feature "preferiti per profilo"):
    // la stella del player desktop marca/smarca nel bucket giusto.
    val favorites: StateFlow<Set<String>> = deps.prefs.favoritesForActiveScope().stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet(),
    )

    init {
        // Risolve il canale appena la playlist è disponibile.
        scope.launch {
            deps.playlistRepository.state.collect { state ->
                val success = state as? PlaylistState.Success ?: return@collect
                val channel = success.channels.firstOrNull { it.id == initialChannelId }
                if (channel != null && _currentChannel.value?.id != channel.id) {
                    playChannel(channel)
                }
            }
        }

        // Riconnessione automatica (feature 7): a ogni errore di riproduzione
        // riprovo ad aprire il canale con backoff crescente, senza lasciare
        // l'utente sulla schermata d'errore; il primo "playing" rimette a
        // zero i contatori. La UI mostra "Riconnessione…" e la superficie
        // video resta al suo posto (solo Error la smonta).
        scope.launch {
            playback.collect { status ->
                when (status) {
                    is PlayerStatus.Error -> scheduleReconnect()
                    is PlayerStatus.Playing -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        reconnectAttempts = 0
                    }

                    else -> Unit
                }
            }
        }
    }

    fun playChannel(channel: Channel) {
        // Nuova sessione di canale: annulla qualunque riconnessione pendente
        // (feature 7) e riparte con i contatori a zero.
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        _currentChannel.value = channel
        // La scelta della traccia riguarda il singolo canale (come su
        // Android): azzero liste ed evidenziazioni, la nuova lettura parte
        // dall'evento "playing" del flusso che si sta aprendo.
        _audioTracks.value = emptyList()
        _currentAudioTrackId.value = null
        _subtitleTracks.value = emptyList()
        _currentSubtitleTrackId.value = null
        // libVLC assente/non caricabile: errore pulito con le istruzioni di
        // installazione invece del popup di crash del runtime.
        val initError = vlc.initError
        if (initError != null) {
            _playback.value = PlayerStatus.Error(initError)
            return
        }
        _playback.value = PlayerStatus.Buffering
        scope.launch {
            deps.prefs.addRecent(channel.id)
            // Stessa strategia della patch RAI/Mediaset Android: risoluzione
            // del relinker + Referer/Origin per network.
            val resolvedUrl = StreamResolver.resolveStreamUrl(channel.url)
            val isMediaset = resolvedUrl.contains("mediaset") || resolvedUrl.contains("msf.cdn")
            val siteUrl = if (isMediaset) "https://mediaset.it" else "https://raiplay.it"
            vlc.play(
                url = resolvedUrl,
                channelUserAgent = channel.httpUserAgent,
                referrer = channel.httpReferrer ?: siteUrl,
            )
        }
    }

    fun togglePlayPause() {
        when (_playback.value) {
            is PlayerStatus.Playing -> vlc.pause()
            is PlayerStatus.Paused -> vlc.resume()
            // Durante Buffering/Reconnecting/Error non c'è nulla da
            // alternare: il flusso si riapre da solo (feature 7) o è fermo.
            else -> Unit
        }
    }

    /**
     * Volume relativo (tasti ↑/↓ o rotella del mouse sulla superficie video
     * del player): applica [delta] al livello corrente e ritorna il nuovo
     * valore per il suggerimento a schermo; null se VLC non è disponibile.
     */
    fun changeVolume(delta: Int): Int? = vlc.volumeDelta(delta)

    /** Passa al canale successivo (+1) o precedente (-1) della playlist. */
    fun switchChannel(delta: Int) {
        val state = deps.playlistRepository.state.value as? PlaylistState.Success ?: return
        val channels = state.channels
        if (channels.isEmpty()) return
        val current = _currentChannel.value ?: return
        val currentIndex = channels.indexOfFirst { it.id == current.id }
        val base = if (currentIndex < 0) 0 else currentIndex
        val nextIndex = ((base + delta) % channels.size + channels.size) % channels.size
        playChannel(channels[nextIndex])
    }

    fun toggleFavorite() {
        val id = _currentChannel.value?.id ?: return
        scope.launch { deps.prefs.toggleFavorite(id) }
    }

    /** Durata selezionata del timer di spegnimento (null = disattivo). */
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    /** Istante (epoch ms) in cui il timer di spegnimento si fermerà. */
    private val _sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepTimerEndsAt.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    /**
     * Timer di spegnimento (porting della funzione Android): alla scadenza
     * mette in pausa la riproduzione senza chiudere il player. [minutes] null
     * disarma il timer; una nuova chiamata sostituisce quella precedente. Il
     * timer sopravvive al cambio canale (zapping) perché riguarda la sessione
     * di visione, non il singolo canale.
     */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null) {
            _sleepTimerMinutes.value = null
            _sleepTimerEndsAt.value = null
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimerMinutes.value = minutes
        _sleepTimerEndsAt.value = endsAt
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            // Guardia: solo se la scadenza è ancora quella (nessun timer
            // più recente nel frattempo) si ferma la riproduzione.
            if (_sleepTimerEndsAt.value == endsAt) {
                vlc.pause()
                _sleepTimerMinutes.value = null
                _sleepTimerEndsAt.value = null
            }
        }
    }

    /**
     * Modalità proporzioni del video (stato esposto alla UI per il feedback;
     * l'applicazione effettiva vive in [VlcPlayer.setResizeMode]).
     */
    private val _videoResizeMode = MutableStateFlow(VideoResizeMode.FIT)
    val videoResizeMode: StateFlow<VideoResizeMode> = _videoResizeMode.asStateFlow()

    /**
     * Cicla le proporzioni Adatta → Zoom → Riempi (stesso ciclo del player
     * Android) e ritorna il nuovo modo, così la UI mostra il feedback.
     */
    fun cycleVideoResizeMode(): VideoResizeMode {
        val next = when (_videoResizeMode.value) {
            VideoResizeMode.FIT -> VideoResizeMode.ZOOM
            VideoResizeMode.ZOOM -> VideoResizeMode.FILL
            VideoResizeMode.FILL -> VideoResizeMode.FIT
        }
        _videoResizeMode.value = next
        vlc.setResizeMode(next)
        return next
    }

    // ------------------------------------------------------------------
    // Tracce audio (porting della selezione Android)
    // ------------------------------------------------------------------

    /** Tracce audio offerte dal flusso corrente (vuota = non disponibili). */
    private val _audioTracks = MutableStateFlow<List<DesktopAudioTrack>>(emptyList())
    val audioTracks: StateFlow<List<DesktopAudioTrack>> = _audioTracks.asStateFlow()

    /** Id VLC della traccia attiva (null = sconosciuta/disabilitata). */
    private val _currentAudioTrackId = MutableStateFlow<Int?>(null)
    val currentAudioTrackId: StateFlow<Int?> = _currentAudioTrackId.asStateFlow()

    private var tracksRefreshJob: kotlinx.coroutines.Job? = null

    /**
     * Rilettura delle tracce dopo l'evento "playing": libVLC le rende
     * disponibili solo con lo stream già avviato e il demuxer può annunciare
     * le ES un attimo dopo l'avvio, quindi si prova presto (700ms) e di
     * nuovo più tardi (2.5s) per i flussi lenti (HLS con playlist lunga).
     * Il job precedente è annullato: a zapping ravvicinati conta solo
     * l'ultimo flusso aperto. Audio e sottotitoli seguono lo stesso ritmo.
     */
    private fun scheduleTracksRefresh() {
        tracksRefreshJob?.cancel()
        tracksRefreshJob = scope.launch {
            delay(700)
            refreshAudioTracks()
            refreshSubtitleTracks()
            delay(2500)
            refreshAudioTracks()
            refreshSubtitleTracks()
        }
    }

    /**
     * Legge dal player le tracce audio del flusso corrente e aggiorna lista
     * ed evidenziazione. Chiamata anche "al volo" dalla UI all'apertura del
     * menu, così il contenuto riflette sempre lo stream in corso (uno
     * stream può cambiare le tracce a metà, e in pausa la lettura resta
     * valida perché il media è ancora caricato).
     */
    fun refreshAudioTracks() {
        _audioTracks.value = vlc.audioTrackDescriptions().mapIndexed { index, description ->
            DesktopAudioTrack(
                id = description.id(),
                label = prettyAudioTrackLabel(description.description(), index),
            )
        }
        _currentAudioTrackId.value = vlc.currentAudioTrackId()
    }

    /**
     * Seleziona la traccia audio per id VLC (porting di selectAudioTrack
     * Android). Su VLC non serve la voce "Automatica" di ExoPlayer: la
     * traccia predefinita è riscelta da VLC a ogni nuovo stream, quindi
     * cambiare canale riporta da solo la selezione automatica.
     */
    fun selectAudioTrack(id: Int) {
        // setTrack ritorna l'id effettivamente attivo dopo l'operazione:
        // se la selezione fallisce libVLC risponde con -1 e l'evidenziazione
        // resta onestamente su quella precedente.
        val applied = vlc.setAudioTrack(id) ?: return
        if (applied >= 0) _currentAudioTrackId.value = applied
    }

    // ------------------------------------------------------------------
    // Sottotitoli (gemello della selezione audio, feature "sottotitoli")
    // ------------------------------------------------------------------

    /** Tracce sottotitoli offerte dal flusso corrente (vuota = non disponibili). */
    private val _subtitleTracks = MutableStateFlow<List<DesktopSubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<DesktopSubtitleTrack>> = _subtitleTracks.asStateFlow()

    /** Id VLC della traccia attiva; null = disattivata/sconosciuta. */
    private val _currentSubtitleTrackId = MutableStateFlow<Int?>(null)
    val currentSubtitleTrackId: StateFlow<Int?> = _currentSubtitleTrackId.asStateFlow()

    /**
     * Legge dal player le tracce sottotitoli del flusso corrente e aggiorna
     * lista ed evidenziazione; stessa strategia dell'audio (lettura al volo
     * all'apertura del menu + refresh programmato a "playing").
     */
    fun refreshSubtitleTracks() {
        _subtitleTracks.value = vlc.subtitleTrackDescriptions().mapIndexed { index, description ->
            DesktopSubtitleTrack(
                id = description.id(),
                label = prettyAudioTrackLabel(description.description(), index),
            )
        }
        _currentSubtitleTrackId.value = vlc.currentSubtitleTrackId()
    }

    /**
     * Seleziona la traccia sottotitoli per id VLC, oppure la disattiva con
     * id -1 (voce "Disattivati" della UI). Come per l'audio, setTrack
     * ritorna l'id effettivamente attivo: -1 = disattivata (evidenziazione
     * torna sulla voce "Disattivati"), un altro valore diverso dalla
     * richiesta = la selezione non è andata a buon fine e resta quella
     * precedente.
     */
    fun selectSubtitleTrack(id: Int) {
        val applied = vlc.setSubtitleTrack(id) ?: return
        _currentSubtitleTrackId.value = applied.takeIf { it >= 0 }
    }

    // ------------------------------------------------------------------
    // Statistiche stream (feature "statistiche", desktop)
    // ------------------------------------------------------------------

    /**
     * Istantanea corrente delle statistiche del flusso (null = non ancora
     * disponibili: stream in apertura o canale radio). La UI la rilegge a
     * ritmo di ~1s solo mentre il pannello è visibile, così a player
     * coperto non costa nulla.
     */
    private val _streamStats = MutableStateFlow<StreamStats?>(null)
    val streamStats: StateFlow<StreamStats?> = _streamStats.asStateFlow()

    fun refreshStreamStats() {
        _streamStats.value = vlc.streamStats()
    }

    fun retry() {
        val channel = _currentChannel.value ?: return
        playChannel(channel)
    }

    // ------------------------------------------------------------------
    // Riconnessione automatica (feature 7)
    // ------------------------------------------------------------------

    private var reconnectAttempts = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null

    /**
     * Programma un tentativo di riapertura del canale dopo un errore.
     * I tentativi seguono un backoff crescente (1.5s, 4s, 8s): i blackout
     * dei CDN durano tipicamente qualche secondo, così la maggior parte
     * delle cadute si risolve da sola senza che l'utente tocchi nulla.
     * Esauriti i tentativi resta la schermata d'errore con "Riprova".
     */
    private fun scheduleReconnect() {
        // libVLC assente/non caricabile: l'errore d'installazione non si
        // cura ri-provando, la schermata con le istruzioni resta aperta.
        if (vlc.initError != null) return
        // Tentativi esauriti (oppure attesa già in corso, es. doppio evento
        // d'errore per lo stesso fallimento): niente programmazioni doppie.
        if (reconnectJob?.isActive == true) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        reconnectJob = scope.launch {
            _playback.value = PlayerStatus.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
            delay(RECONNECT_BACKOFF_MS[(attempt - 1).coerceIn(RECONNECT_BACKOFF_MS.indices)])
            replayCurrentChannel()
        }
    }

    /**
     * Riapre il canale corrente SENZA toccare i contatori della riconnessione
     * (a differenza di [playChannel], che li azzera perché è una nuova
     * sessione voluta dall'utente): è il tentativo automatico feature 7.
     * Nota: un flusso caduto non può restare in pausa, il riavvio riparte in
     * riproduzione (comportamento standard dei player TV).
     */
    private fun replayCurrentChannel() {
        val channel = _currentChannel.value ?: return
        val initError = vlc.initError
        if (initError != null) {
            _playback.value = PlayerStatus.Error(initError)
            return
        }
        _playback.value = PlayerStatus.Buffering
        scope.launch {
            // Stessa strategia di [playChannel] (relinker + intestazioni).
            val resolvedUrl = StreamResolver.resolveStreamUrl(channel.url)
            val isMediaset = resolvedUrl.contains("mediaset") || resolvedUrl.contains("msf.cdn")
            val siteUrl = if (isMediaset) "https://mediaset.it" else "https://raiplay.it"
            vlc.play(
                url = resolvedUrl,
                channelUserAgent = channel.httpUserAgent,
                referrer = channel.httpReferrer ?: siteUrl,
            )
        }
    }

    /**
     * Superficie video AWT (JPanel di vlcj) per lo SwingPanel della UI;
     * null se libVLC non è disponibile (in quel caso la UI mostra solo
     * l'overlay di errore con le istruzioni).
     */
    fun videoComponent(): java.awt.Container? = vlc.component

    /** Risveglia il rendering video dopo un ridimensionamento dell'area. */
    fun recoverVideoSurface() = vlc.recoverAfterResize()

    /**
     * Ponte nero sull'intera finestra (vedi SwingBlackout): da lanciare a
     * ogni transizione di layout (show/hide controlli, toggle fullscreen,
     * apertura player) per rendere nera la cancellazione nativa dei Canvas
     * heavyweight e riportare in pipeline il render della scena skiko.
     */
    fun blackoutWindow() = vlc.blackoutWindow()

    fun dispose() {
        reconnectJob?.cancel()
        vlc.stop()
        vlc.release()
    }

    private companion object {
        const val EPG_REFRESH_MS = 30_000L

        /** Tentativi massimi di riconnessione per canale (feature 7). */
        const val MAX_RECONNECT_ATTEMPTS = 3

        /** Backoff crescente dei tentativi: blackout brevi si sanano da soli. */
        val RECONNECT_BACKOFF_MS = listOf(1_500L, 4_000L, 8_000L)
    }
}
