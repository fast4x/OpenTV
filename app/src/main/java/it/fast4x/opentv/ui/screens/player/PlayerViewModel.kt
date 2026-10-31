package it.fast4x.opentv.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.PlaybackEngine
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.ProgrammeReminder
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.epgKey
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.remote.StreamProbe
import it.fast4x.opentv.data.remote.StreamResolver
import it.fast4x.opentv.data.remote.StreamTypes
import it.fast4x.opentv.data.store.UserPrefsStore
import it.fast4x.opentv.media.PlaybackCommandBus
import it.fast4x.opentv.media.PlaybackMediaBridge
import it.fast4x.opentv.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stato di riproduzione esposto alla UI. */
sealed interface PlaybackStatus {
    data object Buffering : PlaybackStatus
    data object Playing : PlaybackStatus
    data object Paused : PlaybackStatus

    /**
     * Riconnessione automatica in corso (feature 7): il flusso è caduto e si
     * sta riaprendo da soli — [attempt]-esimo tentativo su [maxAttempts].
     * A differenza di Error l'immagine resta a schermo (PlayerView con
     * shutter nero), così il ritorno del segnale è immediato.
     */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : PlaybackStatus

    data class Error(val message: String) : PlaybackStatus
}

/**
 * Traccia audio disponibile per il canale in riproduzione, pronta per il
 * foglio di selezione: i campi restano "grezzi" (etichetta del flusso, codice
 * lingua, canali) e la UI li compone con le stringhe localizzate.
 */
data class AudioTrackOption(
    /** Posizione nell'elenco; è la chiave passata a [PlayerViewModel.selectAudioTrack]. */
    val index: Int,
    /** Etichetta dichiarata dal flusso (es. "English"), se presente. */
    val label: String?,
    /** Codice lingua del flusso se noto (es. "ita"), altrimenti null. */
    val language: String?,
    /** Numero di canali audio (2 = stereo, 6 = 5.1...) o null se ignoto. */
    val channelCount: Int?,
    /** true se il flusso segnala audiodescrizione / descrizione del video. */
    val described: Boolean,
    /** true se questa traccia è quella attualmente riprodotta. */
    val selected: Boolean,
)

/**
 * Traccia sottotitoli disponibile per il canale in riproduzione (gemello di
 * [AudioTrackOption], feature "sottotitoli"): stessi criteri, campi
 * ridotti al necessario per le tracce di testo.
 */
data class TextTrackOption(
    /** Posizione nell'elenco; è la chiave passata a [PlayerViewModel.selectSubtitleTrack]. */
    val index: Int,
    /** Etichetta dichiarata dal flusso, se presente. */
    val label: String?,
    /** Codice lingua del flusso se noto (es. "ita"), altrimenti null. */
    val language: String?,
    /** true se questa traccia è quella attualmente riprodotta. */
    val selected: Boolean,
)

/**
 * ViewModel del player: possiede l'[ExoPlayer] (rilasciato in onCleared),
 * gestisce il caricamento del canale, gli errori e lo scambio canale
 * precedente/successivo senza rientrare nella Home.
 *
 * Strategia di riproduzione a due stadi:
 * 1. ExoPlayer (leggero, decoder hardware) — primo tentativo per ogni canale;
 * 2. in caso di errori di parsing: riprova una volta come flusso progressivo;
 * 3. in caso di errori di decodifica/codec/formato (o parsing persistente):
 *    passa automaticamente a VLC ([VlcEngine]), che decodifica via software
 *    e riesce a riprodurre flussi che i codec del dispositivo non supportano.
 *
 * Le intestazioni per-canali della playlist (`#EXTVLCOPT:http-user-agent` /
 * `:http-referrer`) vengono applicate a entrambi i motori: molti CDN
 * rifiutano con 403 client che non si presentano come da playlist.
 *
 * Se anche VLC fallisce, una sonda HTTP ([StreamProbe]) interroga il sorgente
 * e precisa all'utente la causa reale: geo-blocco (403/451), link morto
 * (404), segnaposto "video non disponibile", server in errore o rete.
 */
@UnstableApi
class PlayerViewModel(
    channelId: String,
    private val appContext: Context,
    private val playlistRepository: PlaylistRepository,
    private val prefs: UserPrefsStore,
    private val epgRepository: EpgRepository,
) : ViewModel() {

    private val _playback = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Buffering)
    val playback: StateFlow<PlaybackStatus> = _playback.asStateFlow()

    /** Motore di riproduzione attivo, esposto alla UI per scegliere la superficie. */
    enum class Engine { EXO }

    private val _engine = MutableStateFlow(Engine.EXO)
    val engine: StateFlow<Engine> = _engine.asStateFlow()

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    /**
     * Tracce audio del canale in riproduzione (più di una = canale multilingua,
     * il pulsante nel player appare solo in quel caso). Vuoto durante il
     * caricamento e per i flussi mono-traccia.
     */
    private val _audioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackOption>> = _audioTracks.asStateFlow()

    /**
     * Gruppi/indici delle tracce elencate in [_audioTracks], nello stesso
     * ordine: servono ad applicare l'override sulla traccia scelta (il foglio
     * passa solo l'indice della lista UI).
     */
    private var audioTrackCandidates: List<Pair<Tracks.Group, Int>> = emptyList()

    /**
     * Tracce sottotitoli del canale in riproduzione (feature "sottotitoli",
     * gemello del menu audio). Vuoto durante il caricamento e per i flussi
     * senza tracce di testo.
     */
    private val _subtitleTracks = MutableStateFlow<List<TextTrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<TextTrackOption>> = _subtitleTracks.asStateFlow()

    /** Gruppi/indici delle tracce di testo, stesso ordine della lista UI. */
    private var subtitleTrackCandidates: List<Pair<Tracks.Group, Int>> = emptyList()

    /**
     * Promemoria programmi armati (feature "reminder"): le chiavi servono
     * al foglio "Programmazione" per evidenziare i campanelli attivi; la
     * notifica la spara lo scheduler a livello app (OpenTvApplication).
     */
    val reminders: StateFlow<List<ProgrammeReminder>> = prefs.reminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    /** Arma/disarma il promemoria di un programma (campanello del foglio). */
    fun toggleReminder(programme: Programme) {
        viewModelScope.launch {
            val armed = ProgrammeReminder(
                channelId = programme.channelId,
                start = programme.start,
                title = programme.title,
            ).key() in reminders.value.map { it.key() }.toSet()
            if (armed) prefs.removeReminder(programme.channelId, programme.start)
            else prefs.addReminder(
                ProgrammeReminder(
                    channelId = programme.channelId,
                    start = programme.start,
                    title = programme.title,
                ),
            )
        }
    }

    /** Durata selezionata del timer di spegnimento (null = disattivo). */
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    /** Istante (epoch ms) in cui il timer di spegnimento si fermerà. */
    private val _sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepTimerEndsAt.asStateFlow()

    private var sleepTimerJob: Job? = null

    // Preferiti della SORGENTE ATTIVA (feature "preferiti per profilo"):
    // la stella del player marca/smarca nel bucket del profilo attivo (o nel
    // bucket storico della lista automatica, se nessun profilo e' attivo).
    val favorites: StateFlow<Set<String>> = prefs.favoritesForActiveScope().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptySet(),
    )

    private val epgTick = flow {
        while (true) {
            emit(Unit)
            delay(EPG_TICK_MS)
        }
    }

    /** Coppia (in onda adesso, a seguire) per il canale corrente. */
    val epg: StateFlow<Pair<Programme?, Programme?>> = combine(
        _currentChannel,
        epgRepository.programmes,
        epgTick,
    ) { currentChannel, programmes, _ ->
        if (currentChannel == null) {
            null to null
        } else {
            val now = System.currentTimeMillis()
            // Id base per il match con la guida ("Rai1.it@SD" -> "Rai1.it"),
            // con fallback per nome (fix35) per le guide con id diversi.
            val list = programmes[epgKey(currentChannel.id)]
                ?: epgRepository.aliasKeyFor(currentChannel)?.let { programmes[it] }
            val nowOn = list?.lastOrNull { it.start <= now && now < it.stop }
            val next = list?.firstOrNull { it.start >= now }
            nowOn to next
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null to null,
    )

    /**
     * Programmazione del canale corrente: tutti i programmi in memoria dalla
     * guida (finestra -1h → +24h) in ordine di inizio, per la vista
     * "Programmazione" del player.
     */
    val schedule: StateFlow<List<Programme>> = combine(
        _currentChannel,
        epgRepository.programmes,
        epgTick,
    ) { channel, programmes, _ ->
        channel?.let { c ->
            // Match per id base + fallback per nome (fix35).
            programmes[epgKey(c.id)]
                ?: epgRepository.aliasKeyFor(c)?.let { programmes[it] }
        }.orEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    /** Factory HTTP: applica User-Agent/Referer per-canale a ogni apertura media. */
    private val httpFactory = HeaderedHttpDataSourceFactory()

    /**
     * Sessione media + notifica di riproduzione (feature 9). Dichiata PRIMA
     * del blocco init perché lì viene creata: il costruttore Kotlin esegue
     * proprietà e init nell'ordine di dichiarazione.
     */
    private var mediaBridge: PlaybackMediaBridge? = null

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build()

    private var progressiveFallbackTried = false

    /** Preferenza utente per il motore di riproduzione (aggiornata in tempo reale). */
    private var enginePref: PlaybackEngine = PlaybackEngine.EXO

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateStatus(playbackState)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateStatus(player.playbackState)
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Nuovi media (o nuova variante HLS): ricostruisce gli elenchi
            // tracce audio e sottotitoli per i fogli di selezione.
            refreshAudioTracks(tracks)
            refreshSubtitleTracks(tracks)
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            // Parametri di selezione cambiati (override applicato o azzerato):
            // riallinea le evidenziazioni delle tracce attive nei fogli.
            refreshAudioTracks(player.currentTracks)
            refreshSubtitleTracks(player.currentTracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            val channel = _currentChannel.value
            if (channel == null) {
                _playback.value = PlaybackStatus.Error(describeError(error))
                return
            }

            // 1° soccorso (non in modalità "solo VLC"): errori di parsing ->
            // riprova come flusso progressivo (l'ipotesi HLS sull'URL senza
            // estensione era sbagliata).
            if (error.errorCode in PARSING_ERROR_CODES && !progressiveFallbackTried) {
                progressiveFallbackTried = true
                _playback.value = PlaybackStatus.Buffering
                loadMedia(channel, progressiveFallback = true)
                return
            }

            // 2° soccorso (feature 7): riconnessione automatica con backoff
            // crescente. I flussi IPTV cadono spesso per qualche secondo
            // (hiccup CDN, timeout di rete): prima di arrendersi con la
            // schermata d'errore si riprova da soli, con l'utente informato
            // dallo stato Reconnecting.
            if (_playback.value is PlaybackStatus.Reconnecting ||
                reconnectAttempts < MAX_RECONNECT_ATTEMPTS
            ) {
                scheduleReconnect()
                return
            }

            val message = describeError(error)
            _playback.value = PlaybackStatus.Error(message)
            // Diagnosi del sorgente: sostituisce il messaggio generico con la
            // causa reale (geo-blocco, link morto, segnaposto, rete).
            _currentChannel.value?.let { diagnoseFailure(it, message) }
        }
    }

    init {
        player.addListener(playerListener)
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        player.setHandleAudioBecomingNoisy(true)

        // Sessione media + notifica di riproduzione (feature 9): la sessione
        // dà play/pausa da lockscreen e tasti auricolari (next/prev -> cambio
        // canale), la notifica mostra canale + programma con i pulsanti.
        mediaBridge = PlaybackMediaBridge(appContext, player)

        viewModelScope.launch {
            combine(_currentChannel, epg, _playback) { channel, epgPair, status ->
                Triple(channel, epgPair, status)
            }.collect { (channel, epgPair, status) ->
                val bridge = mediaBridge ?: return@collect
                if (channel != null) {
                    bridge.update(
                        channelName = channel.name,
                        programmeTitle = epgPair.first?.title,
                        isPlaying = status is PlaybackStatus.Playing,
                    )
                }
            }
        }

        // Comandi dalla notifica e dagli auricolari (feature 9): un solo
        // proprietario dello stato (questo ViewModel), come per i tasti UI.
        viewModelScope.launch {
            PlaybackCommandBus.commands.collect { command ->
                when (command) {
                    PlaybackCommandBus.CMD_TOGGLE_PLAY -> togglePlayPause()
                    PlaybackCommandBus.CMD_NEXT_CHANNEL -> switchChannel(+1)
                    PlaybackCommandBus.CMD_PREV_CHANNEL -> switchChannel(-1)
                }
            }
        }


        // Segnala subito il canale tra gli "ultimi visti".
        viewModelScope.launch { prefs.addRecent(channelId) }

        // Tieni aggiornata la preferenza sul motore di riproduzione.
        viewModelScope.launch {
            prefs.settings.collect { enginePref = it.playbackEngine }
        }

        // Non appena la playlist è disponibile risolve il canale richiesto
        // (utile anche dopo il ripristino del processo). Prima del primo avvio
        // legge in modo sincrono la preferenza sul motore, così la scelta
        // "Sempre VLC" vale anche per il canale iniziale.
        viewModelScope.launch {
            playlistRepository.state.collect { state ->
                if (_currentChannel.value == null && state is PlaylistState.Success) {
                    state.channels.firstOrNull { it.id == channelId }?.let { channel ->
                        enginePref = prefs.settings.first().playbackEngine
                        playChannel(channel)
                    }
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
        progressiveFallbackTried = false
        _playback.value = PlaybackStatus.Buffering
        // La scelta della traccia audio riguarda il singolo canale: al cambio
        // si torna alla selezione automatica (l'override referenzia i gruppi
        // del media precedente e resterebbe come parametro sporco nei
        // TrackSelectionParameters). L'elenco UI si azzera finché il nuovo
        // stream non annuncia le sue tracce (onTracksChanged).
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            // Anche i sottotitoli tornano al comportamento automatico: un
            // "Disattivati" scelto sul canale precedente non deve restare
            // come parametro sporco (l'override referenzia i gruppi del media
            // precedente).
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        _audioTracks.value = emptyList()
        audioTrackCandidates = emptyList()
        _subtitleTracks.value = emptyList()
        subtitleTrackCandidates = emptyList()
        if (enginePref == PlaybackEngine.EXO) {
            _engine.value = Engine.EXO
            loadMedia(channel, progressiveFallback = false)
        }
        viewModelScope.launch { prefs.addRecent(channel.id) }
    }

    /**
     * Carica il canale sul player impostando esplicitamente il mimeType
     * dedotto dall'URL: senza estensione riconoscibile ExoPlayer tratterebbe
     * i relinker HLS come file generico (PARSING_CONTAINER_UNSUPPORTED).
     * Con [progressiveFallback] il mime viene omesso e si affida allo
     * sniffing degli extractor progressivi.
     */
    private fun loadMedia(channel: Channel, progressiveFallback: Boolean) {
        viewModelScope.launch {
            // Intestazioni richieste dalla playlist per questo canale (null = default).
            httpFactory.userAgent = channel.httpUserAgent
//            val refererUrl =
//                if (channel.url.contains("mediaset")) "https://mediaset.it" else "https://www.raiplay.it"


            // Chiamata silente al relinker per avere il link pulito e fresco
            val resolvedUrl = StreamResolver.resolveStreamUrl(channel.url)
            // 1. Riconoscimento del network
            val isMediaset = resolvedUrl.contains("mediaset") || resolvedUrl.contains("msf.cdn")
            val refererUrl = if (isMediaset) "https://mediaset.it" else "https://raiplay.it"
            val originUrl = if (isMediaset) "https://mediaset.it" else "https://raiplay.it"

            Log.d("PlayerViewModel", "loadMedia: url = ${channel.url}, resolvedUrl = $resolvedUrl")

            val userAgent = Config.DEFAULT_STREAM_USER_AGENT
            httpFactory.headers = channel.httpReferrer
                //?.let { mapOf("Referer" to it) }
                ?.let {
                    mapOf(
                        "Referer" to refererUrl,
                        "Origin" to refererUrl,
                        "User-Agent" to userAgent // Forzato anche come proprietà interna per evitare perdite nei redirect
                    )
                }
                ?: emptyMap()
            val mimeType = if (progressiveFallback) null else StreamTypes.guessMimeType(resolvedUrl)
            val mediaItem = MediaItem.Builder()
                .setUri(resolvedUrl)
                .setMimeType(mimeType)
                // Metadati per lockscreen e comandi di sistema (feature 9):
                // il titolo del media è il nome del canale.
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(channel.name)
                        .setArtist(appContext.getString(R.string.notification_live))
                        .build(),
                )
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun togglePlayPause() {
        if (_playback.value is PlaybackStatus.Error ||
            _playback.value is PlaybackStatus.Reconnecting
        ) return
        if (_engine.value == Engine.EXO) {
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun retry() {
        val channel = _currentChannel.value ?: return
        // Il "Riprova" manuale apre una nuova sessione: riarma anche il
        // meccanismo automatico (feature 7) con i contatori a zero.
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        _playback.value = PlaybackStatus.Buffering
        if (_engine.value == Engine.EXO) {
            player.prepare()
            player.play()
        }
    }

    // ------------------------------------------------------------------
    // Riconnessione automatica (feature 7)
    // ------------------------------------------------------------------

    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null

    /**
     * Programma un tentativo di riapertura del canale dopo un errore, con
     * backoff crescente (1.5s, 4s, 8s): la maggior parte delle cadute dei
     * flussi IPTV dura pochi secondi e si risana da sola. Durante l'attesa
     * lo stato è Reconnecting (la UI mostra "Riconnessione…" e l'immagine
     * resta a schermo); a fine attesa si riparte da Buffering e gli eventi
     * normali di ExoPlayer riprendono a pilotare lo stato.
     */
    private fun scheduleReconnect() {
        // Attesa già in corso (doppio evento d'errore per lo stesso
        // fallimento): non duplicare i tentativi né spostare il backoff.
        if (reconnectJob?.isActive == true) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        reconnectJob = viewModelScope.launch {
            _playback.value = PlaybackStatus.Reconnecting(attempt, MAX_RECONNECT_ATTEMPTS)
            delay(RECONNECT_BACKOFF_MS[(attempt - 1).coerceIn(RECONNECT_BACKOFF_MS.indices)])
            val channel = _currentChannel.value ?: return@launch
            _playback.value = PlaybackStatus.Buffering
            // Stessa apertura del primo tentativo (mime dedotto dall'URL):
            // la riconnessione gestisce cadute di rete, non cambi di formato.
            loadMedia(channel, progressiveFallback = false)
        }
    }

    /** Passa al canale successivo (+1) o precedente (-1) della playlist. */
    fun switchChannel(delta: Int) {
        val state = playlistRepository.state.value as? PlaylistState.Success ?: return
        val channels = state.channels
        if (channels.isEmpty()) return

        val currentIndex = channels.indexOfFirst { it.id == _currentChannel.value?.id }
        val base = if (currentIndex < 0) 0 else currentIndex
        val nextIndex = ((base + delta) % channels.size + channels.size) % channels.size
        playChannel(channels[nextIndex])
    }

    fun toggleFavorite() {
        val id = _currentChannel.value?.id ?: return
        viewModelScope.launch { prefs.toggleFavorite(id) }
    }

    /**
     * Ricostruisce l'elenco tracce audio dallo snapshot [Tracks] corrente:
     * solo tracce realmente riproducibili, con lo stato di selezione
     * aggiornato. Le tracce con audiodescrizione/descrizione del video
     * (roleFlags segnalati da HLS/DASH) vengono marcate per la UI.
     */
    private fun refreshAudioTracks(tracks: Tracks) {
        val options = mutableListOf<AudioTrackOption>()
        val candidates = mutableListOf<Pair<Tracks.Group, Int>>()
        tracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val described = (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0 ||
                    (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0
                candidates += group to i
                options += AudioTrackOption(
                    index = candidates.lastIndex,
                    label = format.label?.takeIf { it.isNotBlank() },
                    language = format.language?.takeIf { it.isNotBlank() },
                    channelCount = format.channelCount.takeIf { it > 0 },
                    described = described,
                    selected = group.isTrackSelected(i),
                )
            }
        }
        audioTrackCandidates = candidates
        _audioTracks.value = options
    }

    /**
     * Seleziona la traccia audio [index] dell'elenco esposto alla UI
     * (override puntuale su quel gruppo di tracce), oppure torna alla scelta
     * automatica di ExoPlayer con un indice negativo. La scelta resta valida
     * per tutto il canale e viene azzerata al prossimo cambio canale.
     */
    fun selectAudioTrack(index: Int) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (index < 0) {
            // "Automatica": rimuove gli override audio, ExoPlayer sceglie da sé.
            builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        } else {
            val candidate = audioTrackCandidates.getOrNull(index) ?: return
            builder.setOverrideForType(
                TrackSelectionOverride(candidate.first.mediaTrackGroup, candidate.second),
            )
        }
        player.trackSelectionParameters = builder.build()
        refreshAudioTracks(player.currentTracks)
    }

    /**
     * Ricostruisce l'elenco tracce sottotitoli dallo snapshot [Tracks]
     * corrente (feature "sottotitoli"): solo tracce di testo realmente
     * riproducibili, con lo stato di selezione aggiornato.
     */
    private fun refreshSubtitleTracks(tracks: Tracks) {
        val options = mutableListOf<TextTrackOption>()
        val candidates = mutableListOf<Pair<Tracks.Group, Int>>()
        tracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEach
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                candidates += group to i
                options += TextTrackOption(
                    index = candidates.lastIndex,
                    label = format.label?.takeIf { it.isNotBlank() },
                    language = format.language?.takeIf { it.isNotBlank() },
                    selected = group.isTrackSelected(i),
                )
            }
        }
        subtitleTrackCandidates = candidates
        _subtitleTracks.value = options
    }

    /**
     * Seleziona la traccia sottotitoli [index] dell'elenco esposto alla UI,
     * oppure disattiva i sottotitoli con un indice negativo (la voce
     * "Disattivati" del foglio): ExoPlayer disabilita l'intero tipo TEXT,
     * così nessun flusso riusa i renderer di testo finché non si riprende
     * una traccia (o non si cambia canale, che riporta tutto ad auto).
     */
    fun selectSubtitleTrack(index: Int) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (index < 0) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val candidate = subtitleTrackCandidates.getOrNull(index) ?: return
            builder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    TrackSelectionOverride(candidate.first.mediaTrackGroup, candidate.second),
                )
        }
        player.trackSelectionParameters = builder.build()
        refreshSubtitleTracks(player.currentTracks)
    }

    /**
     * Timer di spegnimento: alla scadenza mette in pausa la riproduzione
     * (senza chiudere il player, così il contenuto resta a portata di tap).
     * [minutes] null disarma il timer; una nuova chiamata sostituisce quella
     * precedente. Il timer sopravvive al cambio canale (zapping) perché riguarda
     * la sessione di visione, non il singolo canale.
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
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            // Guardia: solo se la scadenza è ancora quella (nessun timer
            // più recente nel frattempo) si ferma la riproduzione.
            if (_sleepTimerEndsAt.value == endsAt) {
                if (_engine.value == Engine.EXO) player.pause()
                _sleepTimerMinutes.value = null
                _sleepTimerEndsAt.value = null
            }
        }
    }

    private fun updateStatus(playbackState: Int) {
        // Dopo un errore restiamo in errore finché l'utente non preme "Riprova";
        // durante l'attesa di una riconnessione (feature 7) gli eventi IDLE di
        // ExoPlayer non devono sovrascrivere l'indicatore "Riconnessione…".
        if (_playback.value is PlaybackStatus.Error) return
        if (_playback.value is PlaybackStatus.Reconnecting) return
        _playback.value = when {
            playbackState == Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            playbackState == Player.STATE_READY && player.isPlaying -> PlaybackStatus.Playing
            playbackState == Player.STATE_READY -> PlaybackStatus.Paused
            else -> PlaybackStatus.Buffering
        }
    }

    private fun describeError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> "Flusso non raggiungibile: controlla la rete"

        PlaybackException.ERROR_CODE_TIMEOUT -> "Timeout durante la connessione al canale"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val code = httpStatusOf(error)
            if (code != null) {
                "Il server del canale ha risposto HTTP $code " +
                    "(possibile blocco geografico o link non più attivo)"
            } else {
                "Il server del canale ha risposto con un errore"
            }
        }

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        -> "Flusso in un formato non riconosciuto dal player"

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> "Formato stream non supportato dal dispositivo"

        else -> "Errore di riproduzione (${error.errorCodeName})"
    }

    /** Estrae il codice HTTP dalla catena di cause di [error], se presente. */
    private fun httpStatusOf(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
    }

    /**
     * Dopo il fallimento di entrambi i motori interroga il sorgente con la
     * [StreamProbe] e, se la schermata mostra ancora l'errore dello stesso
     * canale, sostituisce il messaggio generico con la causa reale.
     */
    private fun diagnoseFailure(channel: Channel, fallbackMessage: String) {
        viewModelScope.launch {
            val result = StreamProbe.probe(
                url = channel.url,
                userAgent = channel.httpUserAgent,
                headers = channel.httpReferrer?.let { mapOf("Referer" to it) } ?: emptyMap(),
            )
            val stillOnError = _playback.value is PlaybackStatus.Error
            val sameChannel = _currentChannel.value?.id == channel.id
            if (stillOnError && sameChannel) {
                _playback.value = PlaybackStatus.Error(
                    buildDiagnosisMessage(result, fallbackMessage),
                )
            }
        }
    }

    /** Traduce l'esito della sonda in un messaggio d'errore comprensibile. */
    private fun buildDiagnosisMessage(
        result: StreamProbe.Result,
        fallback: String,
    ): String = when {
        result.networkError ->
            "Il server del canale non risponde: flusso offline oppure la rete " +
                "blocca la connessione."

        StreamProbe.isPlaceholder(result) ->
            "Il canale al momento non trasmette: il sorgente restituisce un " +
                "segnaposto \"video non disponibile\" (tipico dei relinker Rai " +
                "quando il canale è spento o bloccato)."

        result.httpCode == 403 || result.httpCode == 451 ->
            "Il server ha rifiutato l'accesso (HTTP ${result.httpCode}): canale " +
                "bloccato nella tua zona o riservato ad alcuni operatori."

        result.httpCode == 404 || result.httpCode == 410 ->
            "Il canale non esiste più su questo server (HTTP ${result.httpCode}): " +
                "il link della playlist è morto."

        result.httpCode != null && result.httpCode!! >= 500 ->
            "Il server del canale è in errore (HTTP ${result.httpCode}): riprova più tardi."

        result.httpCode != null && result.httpCode in 300..399 ->
            "Il sorgente rimanda a un altro indirizzo che non risulta raggiungibile."

        result.httpCode == 200 ->
            "Il server risponde (HTTP 200) ma il flusso non è riproducibile: " +
                "contenuto non standard, protetto (DRM) o non più attivo."

        else -> fallback
    }

    override fun onCleared() {
        mediaBridge?.release()
        player.release()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EPG_TICK_MS = 30_000L

        /** Tentativi massimi di riconnessione per canale (feature 7). */
        const val MAX_RECONNECT_ATTEMPTS = 3

        /** Backoff crescente dei tentativi: blackout brevi si sanano da soli. */
        val RECONNECT_BACKOFF_MS = listOf(1_500L, 4_000L, 8_000L)

        /** Errori di parsing che giustificano il tentativo progressivo di riserva. */
        val PARSING_ERROR_CODES = intArrayOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )
    }
}

/**
 * Factory dei DataSource HTTP di ExoPlayer che legge User-Agent e intestazioni
 * per-canale ([Channel.httpUserAgent] / [Channel.httpReferrer], dalle
 * direttive `#EXTVLCOPT` della playlist) al momento della creazione del
 * DataSource: DefaultMediaSourceFactory ne crea uno a ogni apertura media,
 * quindi i valori impostati da [PlayerViewModel.loadMedia] prima di
 * `setMediaItem` vengono applicati al canale corrente.
 *
 * Lo User-Agent di default ("da browser") evita i 403 dei CDN verso UA di
 * prodotto; il canale può sovrascriverlo con il valore della playlist.
 */
private class HeaderedHttpDataSourceFactory : DataSource.Factory {

    /** User-Agent da usare per il canale in apertura (null = predefinito). */
    @Volatile
    var userAgent: String? = null

    /** Intestazioni extra (es. Referer) per il canale in apertura. */
    @Volatile
    var headers: Map<String, String> = emptyMap()

    @OptIn(UnstableApi::class)
    override fun createDataSource(): DataSource =
        DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: Config.DEFAULT_STREAM_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(headers)
            .createDataSource()
}
