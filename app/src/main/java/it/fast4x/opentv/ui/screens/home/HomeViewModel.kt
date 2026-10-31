package it.fast4x.opentv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.GroupInfo
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.RecentEntry
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.model.channelGroupKeys
import it.fast4x.opentv.data.model.channelGroups
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.SourceResolver
import it.fast4x.opentv.data.repository.epgKey
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.store.UserPrefsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

/** Stato aggregato per la schermata Home. */
data class HomeUiState(
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
    /** Preferiti già intersecati con gruppo e ricerca, pronti per la griglia. */
    val favoriteResults: List<Channel> = emptyList(),
    /**
     * Filtro "solo con guida TV" (feature dedicata): persistito nel
     * DataStore, diversamente da favoritesOnly che resta di sessione.
     */
    val guideOnly: Boolean = false,
    /** Canali della sorgente attiva coperti dalla guida TV (per il chip). */
    val channelsWithGuide: Int = 0,
)

private data class CoreState(
    val playlist: PlaylistState,
    val favorites: Set<String>,
    val recents: List<RecentEntry>,
    /** Canali nascosti della sorgente attiva (feature "nascondi canali"). */
    val hidden: Set<String>,
)

private data class MetaState(
    val query: String,
    val selectedGroup: String?,
    val epg: Map<String, List<Programme>>,
    val favoritesOnly: Boolean,
    val guideOnly: Boolean,
)

class HomeViewModel(
    private val playlistRepository: PlaylistRepository,
    private val prefs: UserPrefsStore,
    private val epgRepository: EpgRepository,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    /**
     * Filtro rapido "solo preferiti" (chip stella): volutamente di sessione,
     * non persistito nel DataStore — è un commutatore momentaneo, diversamente
     * dal filtro per gruppo che invece sopravvive al riavvio.
     */
    private val favoritesOnlyFlow = MutableStateFlow(false)

    /**
     * Paese scelto nei selettori (preferenza dedicata, fix19): evidenzia la
     * voce attiva nel selettore della Home. Null = modalità automatica (paese
     * del dispositivo) o URL personale impostato nel campo delle Impostazioni.
     */
    val currentCountry: StateFlow<String?> = prefs.settings
        .map { it.countryCode }
        .stateIn(
            scope = viewModelScope,
            // Eagerly come sul desktop: il selettore parte subito allineato
            // alle preferenze reali, senza flicker verso un paese non ancora
            // confermato dai dati.
            started = SharingStarted.Eagerly,
            // Null = automatica: nessun paese implicito prima della prima
            // emissione dal DataStore.
            initialValue = null,
        )

    /**
     * Profili di sorgente (feature "profilazione della lista") e profilo
     * attivo: servono al menu sorgente della Home. Null = sorgente
     * automatica (iptv-org, catena storica).
     */
    val sourceProfiles: StateFlow<List<SourceProfile>> = prefs.sourceProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    val activeProfileId: StateFlow<String?> = prefs.activeProfileId
        .stateIn(
            scope = viewModelScope,
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
        // profilo dal bucket storico della lista automatica. Entra nel core
        // cosi' ogni vista (riga Preferiti, chip stella, stelle delle card)
        // eredita lo scope giusto senza controlli sparsi.
        prefs.favoritesForActiveScope(),
        prefs.recents,
        // Canali nascosti della sorgente attiva (feature "nascondi canali"):
        // il set entra nel core cosi' il filtro avviene ALLA RADICE e ogni
        // vista (gruppi con i loro conteggi, ricerca, preferiti, recenti,
        // elenco alfabetico) ne eredita l'effetto.
        prefs.hiddenChannelsForActiveScope(),
    ) { playlist, favorites, recents, hidden ->
        CoreState(playlist, favorites, recents, hidden)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CoreState(PlaylistState.Loading, emptySet(), emptyList(), emptySet()),
    )

    private val meta = combine(
        queryFlow,
        // Il filtro per gruppo persiste in DataStore: selezionare un chip
        // lo salva e riaprendo l'app il filtro è ancora attivo.
        prefs.selectedGroup,
        epgRepository.programmes,
        favoritesOnlyFlow,
        // Anche il filtro "solo con guida TV" persiste (feature dedicata).
        prefs.guideOnly,
    ) { query, group, epg, favoritesOnly, guideOnly ->
        MetaState(query, group, epg, favoritesOnly, guideOnly)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = MetaState("", null, emptyMap(), false, false),
    )

    val uiState: StateFlow<HomeUiState> = combine(core, meta, epgTick) { coreState, metaState, _ ->
        val playlist = coreState.playlist
        // I nascosti spariscono alla fonte: tutte le viste qui sotto partono
        // da questa lista gia' filtrata (feature "nascondi canali").
        val base = ((playlist as? PlaylistState.Success)?.channels ?: emptyList())
            .filter { it.id !in coreState.hidden }
        val now = System.currentTimeMillis()

        // Passata unica sulla lista base per la guida TV: per ogni canale la
        // chiave (base o alias per nome, fix35) si risolve UNA volta e da li'
        // escono sia il programma in onda sia l'insieme dei canali coperti
        // dalla guida, che alimenta il filtro "solo con guida TV" e il
        // conteggio del chip.
        val epgNow = HashMap<String, Programme>()
        val guideIds = HashSet<String>()
        val epgMap = metaState.epg
        if (epgMap.isNotEmpty()) {
            for (channel in base) {
                val list = epgMap[epgKey(channel.id)]
                    ?: epgRepository.aliasKeyFor(channel)?.let { epgMap[it] }
                if (list.isNullOrEmpty()) continue
                guideIds += channel.id
                list.lastOrNull { it.start <= now && now < it.stop }?.let { epgNow[channel.id] = it }
            }
        }

        // Filtro "solo con guida TV": attivo SOLO se la guida copre almeno un
        // canale della sorgente (altrimenti nasconderebbe l'intera lista).
        // Filtrando alla radice, ogni vista (gruppi con i loro conteggi,
        // ricerca, preferiti, recenti, elenco alfabetico) eredita l'effetto.
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

        // fix32 — come su desktop: al campo di ricerca torna la query RAW,
        // il trim resta solo ai filtri (lo spazio digitato in coda non deve
        // sparire dal TextField controllato: "Italia 1").
        val rawQuery = metaState.query
        val query = rawQuery.trim()
        val groups = channelGroups(channels)
        // Il gruppo persistito potrebbe non esistere più (playlist cambiata):
        // in quel caso lo ignoriamo visualmente senza cancellarlo, così se
        // il gruppo torna disponibile il filtro si riattiva da solo.
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
        // combinano come si combinano già gruppo + ricerca.
        val favoriteResults = if (metaState.favoritesOnly) {
            favoriteChannels.filter { channel ->
                (selectedGroup == null || selectedGroup in channelGroupKeys(channel)) &&
                    (query.isEmpty() || normalize(channel.name).contains(normalizedQuery))
            }
        } else {
            emptyList()
        }

        HomeUiState(
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
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(),
    )

    init {
        // Primo caricamento (o ricaricamento con cache scaduta) all'avvio.
        viewModelScope.launch { playlistRepository.refresh() }

        // Carica/ricarica la guida TV quando cambia l'URL effettivo: profilo
        // di sorgente attivo (feature "profilazione"), URL manuale impostato
        // dall'utente, paese scelto nei selettori, oppure automatico derivato
        // dalla playlist iptv-org (guida per paese di iptv-epg.org, stessi id
        // dei tvg-id). La risoluzione unica sta in SourceResolver: con un
        // profilo attivo la catena storica è bypassata anche qui.
        // fix35: entra anche la guida INCORPORATA nella playlist (header
        // x-tvg-url), selezionata dal repository a ogni caricamento lista —
        // attendere il primo esito del caricamento playlist evita di
        // scaricare la guida storica e, un attimo dopo, quella incorporata:
        // la catena completa è nota prima del primo download guida.
        viewModelScope.launch {
            playlistRepository.state.first { it !is PlaylistState.Loading }

            combine(
                prefs.settings,
                prefs.sourceProfiles,
                prefs.activeProfileId,
                playlistRepository.embeddedEpgChain,
            ) { settings, profiles, activeId, embedded ->
                SourceResolver.resolveEpgChain(settings, profiles, activeId, embedded)
            }
                .distinctUntilChanged()
                .collectLatest { chain -> epgRepository.load(chain) }
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
        viewModelScope.launch { prefs.setSelectedGroup(group) }
    }

    /** Attiva/disattiva il filtro rapido "solo preferiti" (stato di sessione). */
    fun onFavoritesOnlyChange(enabled: Boolean) {
        favoritesOnlyFlow.value = enabled
    }

    /**
     * Attiva/disattiva il filtro "solo con guida TV": la scelta persiste
     * nel DataStore, riaprendo l'app il filtro è ancora com'era.
     */
    fun onGuideOnlyChange(enabled: Boolean) {
        viewModelScope.launch { prefs.setGuideOnly(enabled) }
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
     * allineate in entrambe le direzioni e il campo URL torna a significare
     * solo "lista personale". Con [countryCode] null (voce automatica) la
     * preferenza si azzera e il repository carica la playlist per paese del
     * dispositivo (Italia se non supportata).
     */
    fun onCountryChange(countryCode: String?) {
        viewModelScope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setPlaylistUrl("")
            prefs.setCountryCode(countryCode)
            playlistRepository.refresh(force = true)
        }
    }

    /**
     * Cambia il profilo di sorgente attivo (feature "profilazione della
     * lista"; null = sorgente automatica iptv-org): persiste la scelta,
     * esce da un'eventuale playlist locale importata (che ha sempre
     * priorità su tutte le sorgenti) e forza il ricaricamento della lista.
     * La guida TV segue da sola: il flusso di init qui sopra risolve l'URL
     * EPG anche dai profili (SourceResolver.resolveEpg).
     */
    fun onProfileChange(profileId: String?) {
        viewModelScope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setActiveProfileId(profileId)
            playlistRepository.refresh(force = true)
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { prefs.toggleFavorite(channelId) }
    }

    /**
     * Nasconde un canale (feature "nascondi canali"): la chiamata allo store
     * risolve da sola lo scope (profilo attivo o sorgente automatica) e la
     * lista si aggiorna da sola via flusso. La UI chiede SEMPRE conferma
     * prima di arrivare qui.
     */
    fun hideChannel(channelId: String) {
        viewModelScope.launch { prefs.hideChannel(channelId) }
    }

    fun refresh() {
        viewModelScope.launch { playlistRepository.refresh(force = true) }
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
        const val STOP_TIMEOUT_MS = 5_000L
        const val EPG_REFRESH_MS = 60_000L
    }
}
