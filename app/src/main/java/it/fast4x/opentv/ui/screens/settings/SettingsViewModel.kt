package it.fast4x.opentv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import it.fast4x.opentv.data.model.AppSettings
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.PlaybackEngine
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.EpgStatus
import it.fast4x.opentv.data.repository.SourceResolver
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.remote.ProfileCodec
import it.fast4x.opentv.data.remote.ProfileExportBundle
import it.fast4x.opentv.data.store.UserPrefsStore
import it.fast4x.opentv.util.readTextFromUri
import it.fast4x.opentv.util.takePersistableUriPermission
import it.fast4x.opentv.util.writeTextToUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appContext: Context,
    private val playlistRepository: PlaylistRepository,
    private val epgRepository: EpgRepository,
    private val prefs: UserPrefsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = prefs.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppSettings(),
    )

    val playlistState: StateFlow<PlaylistState> = playlistRepository.state
    val epgStatus: StateFlow<EpgStatus> = epgRepository.status

    /** Profili di sorgente salvati (feature "profilazione della lista"). */
    val sourceProfiles: StateFlow<List<SourceProfile>> = prefs.sourceProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    /** Id del profilo attivo; null = sorgente automatica (iptv-org). */
    val activeProfileId: StateFlow<String?> = prefs.activeProfileId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    /**
     * Canali nascosti della sorgente attiva (feature "nascondi canali"),
     * risolti in [Channel] dalla playlist caricata per mostrare il nome;
     * ordine alfabetico. Un id nascosto assente dalla playlist attuale resta
     * nella preferenza ma non è listato (è inerte finché il canale non torna).
     */
    val hiddenChannels: StateFlow<List<Channel>> = combine(
        prefs.hiddenChannelsForActiveScope(),
        playlistRepository.state,
    ) { hidden, playlist ->
        ((playlist as? PlaylistState.Success)?.channels ?: emptyList())
            .filter { it.id in hidden }
            .sortedBy { it.name.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    /** Ricarica lista + guida dopo una scrittura preferenze relativa alle sorgenti. */
    private suspend fun reloadSources() {
        playlistRepository.refresh(force = true)
        // Risoluzione unica con i profili (SourceResolver): con un profilo
        // attivo la guida resta quella del profilo anche quando l'utente
        // tocca i campi/paese della sorgente automatica.
        // fix35: catena completa — guida incorporata nella playlist (header
        // x-tvg-url, fresca perché refresh(force = true) è appena concluso)
        // + risoluzione storica come candidato di fusione.
        epgRepository.load(
            SourceResolver.resolveEpgChain(
                settings = prefs.settings.first(),
                profiles = prefs.sourceProfiles.first(),
                activeProfileId = prefs.activeProfileId.first(),
                embeddedFromPlaylist = playlistRepository.embeddedEpgChain.value,
            ),
        )
    }

    /** Salva gli URL della sorgente automatica e forza il ricaricamento. */
    fun save(playlistUrl: String, epgUrl: String) {
        viewModelScope.launch {
            // Scegliere un URL esplicitamente significa uscire dalla sorgente
            // locale importata: senza questa rimozione il file locale (che ha
            // priorità) continuerebbe a mascherare il nuovo URL.
            prefs.setLocalPlaylistUri(null)
            prefs.setPlaylistUrl(playlistUrl)
            // Fix19: un URL personale prende il posto del paese scelto nei
            // selettori (preferenza dedicata); con il campo vuoto il paese
            // scelto resta attivo e non viene sovrascritto dal salvataggio.
            if (playlistUrl.isNotBlank()) prefs.setCountryCode(null)
            prefs.setEpgUrl(epgUrl)
            reloadSources()
        }
    }

    /**
     * Ripristina la modalità automatica (playlist iptv-org del paese del
     * dispositivo, Italia se non disponibile) e ricarica: coerente con la
     * nota del campo URL, con la voce "Automatica" dei selettori e con la
     * migrazione delle preferenze legacy. Azzera anche il paese scelto nei
     * selettori (fix19): automatica significa proprio "nessun paese forzato".
     */
    fun resetPlaylistToDefault(onReset: (String) -> Unit) {
        viewModelScope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setPlaylistUrl("")
            prefs.setCountryCode(null)
            reloadSources()
            onReset("")
        }
    }

    /**
     * Cambia il paese della lista canali dal selettore delle Impostazioni
     * (fix19): applica subito la scelta nella preferenza dedicata — la stessa
     * osservata dalla Home, che resta così allineata — svuota il campo URL
     * (che significa solo "lista personale") e ricarica lista e guida TV.
     * Con [countryCode] null (voce automatica) la preferenza si azzera.
     */
    fun setCountry(countryCode: String?) {
        viewModelScope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setPlaylistUrl("")
            prefs.setCountryCode(countryCode)
            reloadSources()
        }
    }

    /**
     * Aggiunge un profilo di sorgente (feature "profilazione della lista"):
     * nome + URL playlist + URL guida opzionale. La playlist è obbligatoria
     * (un profilo senza playlist non è una sorgente: la validazione finale
     * sta nella schermata, la difesa in SourceResolver).
     */
    fun addProfile(name: String, playlistUrl: String, epgUrl: String) {
        viewModelScope.launch { prefs.addSourceProfile(name, playlistUrl, epgUrl) }
    }

    /** Salva le modifiche a un profilo esistente (nome/URL). */
    fun updateProfile(profile: SourceProfile) {
        viewModelScope.launch { prefs.updateSourceProfile(profile) }
    }

    /**
     * Rimuove un profilo; se era quello attivo la sorgente torna automatica
     * (lo store azzera la selezione) e la lista viene ricaricata.
     */
    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            prefs.removeSourceProfile(profileId)
            reloadSources()
        }
    }

    /**
     * Attiva un profilo di sorgente (null = automatica): esce dalla playlist
     * locale importata (che ha sempre priorità) e ricarica lista e guida TV
     * dalla nuova sorgente. Stesso comportamento del selettore della Home.
     */
    fun setActiveProfile(profileId: String?) {
        viewModelScope.launch {
            prefs.setLocalPlaylistUri(null)
            prefs.setActiveProfileId(profileId)
            reloadSources()
        }
    }

    /** Imposta il motore di riproduzione; si applica dal prossimo canale. */
    fun setEngine(engine: PlaybackEngine) {
        viewModelScope.launch { prefs.setPlaybackEngine(engine) }
    }

    /** Ripristina un canale nascosto (torna in tutti gli elenchi). */
    fun unhideChannel(channelId: String) {
        viewModelScope.launch { prefs.unhideChannel(channelId) }
    }

    /** Ripristina tutti i canali nascosti della sorgente attiva. */
    fun unhideAllChannels() {
        viewModelScope.launch { prefs.unhideAllChannels() }
    }

    /**
     * Piano di importazione (feature export/import a livello di profilo):
     * esito della lettura/classificazione del file scelto. Il file può essere
     * un pacchetto profilo OpenTV ([ProfileCodec], profilo + canali) oppure
     * una playlist M3U semplice ([bundle] null). Se il pacchetto ha il nome
     * di un profilo GIÀ presente ([nameCollision]) la schermata mostra il
     * dialog di conferma prima di chiamare [applyImport]; senza collisione
     * l'applicazione è immediata, coerente con l'import M3U storico.
     */
    data class ImportPlan(
        val uri: Uri,
        /** Pacchetto profilo decodificato; null = playlist M3U semplice. */
        val bundle: ProfileExportBundle?,
        /** Corpo M3U da passare al repository (per l'M3U semplice: il file). */
        val m3uText: String,
        val nameCollision: Boolean,
    )

    /**
     * Legge e classifica il file importato SENZA applicarlo: il risultato
     * guida la schermata nel decidere se chiedere conferma (profilo omonimo)
     * o procedere direttamente. La lettura dell'URI resta qui perché è
     * specifica di SAF (piattaforma Android).
     */
    fun planImport(uri: Uri, onPlan: (Result<ImportPlan>) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { readTextFromUri(appContext, uri) }
            if (text == null) {
                onPlan(Result.failure(java.io.IOException(fileErrorMessage())))
                return@launch
            }
            if (ProfileCodec.isBundle(text)) {
                val bundle = ProfileCodec.decode(text)
                if (bundle == null) {
                    onPlan(
                        Result.failure(
                            java.io.IOException(
                                appContext.getString(it.fast4x.opentv.R.string.settings_profile_invalid),
                            ),
                        ),
                    )
                    return@launch
                }
                val collision = prefs.sourceProfiles.first().any {
                    it.name.trim().equals(bundle.profile.name.trim(), ignoreCase = true)
                }
                onPlan(
                    Result.success(
                        ImportPlan(
                            uri = uri,
                            bundle = bundle,
                            m3uText = ProfileCodec.m3uBody(text),
                            nameCollision = collision,
                        ),
                    ),
                )
            } else {
                onPlan(
                    Result.success(
                        ImportPlan(uri = uri, bundle = null, m3uText = text, nameCollision = false),
                    ),
                )
            }
        }
    }

    /**
     * Applica il piano di importazione. Per un pacchetto profilo: il profilo
     * viene creato o sovrascritto per NOME (mantenendo l'id se omonimo, così
     * l'eventuale selezione attiva resta valida), viene attivato e i canali
     * del file diventano la lista caricata (sorgente locale, con la copia di
     * cache come fallback offline); per un M3U semplice vale l'import storico.
     * In coda la guida viene ricaricata con la catena appena cambiata
     * (profilo attivato + eventuale guida incorporata nel file).
     */
    fun applyImport(plan: ImportPlan, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val bundle = plan.bundle
            if (bundle != null) {
                val profile = prefs.upsertSourceProfileByName(
                    bundle.profile.name,
                    bundle.profile.playlistUrl,
                    bundle.profile.epgUrl,
                )
                prefs.setActiveProfileId(profile.id)
            }
            val result = playlistRepository.importPlaylist(plan.m3uText, sourceId = plan.uri.toString())
                .onSuccess {
                    // Rendi l'URI leggibile anche dopo il riavvio (quando il
                    // provider lo consente; il fallimento è accettabile, resta
                    // la copia di cache come fallback offline).
                    takePersistableUriPermission(appContext, plan.uri)
                }
            if (result.isSuccess) {
                epgRepository.load(
                    SourceResolver.resolveEpgChain(
                        settings = prefs.settings.first(),
                        profiles = prefs.sourceProfiles.first(),
                        activeProfileId = prefs.activeProfileId.first(),
                        embeddedFromPlaylist = playlistRepository.embeddedEpgChain.value,
                    ),
                )
            }
            onResult(result)
        }
    }

    /**
     * Esporta la lista canali corrente su un file scelto dall'utente (SAF):
     * il testo M3U è generato dal core, la scrittura resta qui.
     */
    fun exportPlaylistTo(uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = playlistRepository.exportPlaylistText().mapCatching { export ->
                val written = withContext(Dispatchers.IO) {
                    writeTextToUri(appContext, uri, export.m3uText)
                }
                if (!written) error(fileErrorMessage())
                export.channelCount
            }
            onResult(result)
        }
    }

    /**
     * Esporta un profilo con i canali della sua playlist su un file scelto
     * dall'utente (SAF): il pacchetto è prodotto dal core
     * ([PlaylistRepository.exportProfileBundle] + [ProfileCodec.encode]),
     * la scrittura resta qui.
     */
    fun exportProfileTo(profile: SourceProfile, uri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = playlistRepository.exportProfileBundle(profile).mapCatching { bundle ->
                val written = withContext(Dispatchers.IO) {
                    writeTextToUri(appContext, uri, ProfileCodec.encode(bundle))
                }
                if (!written) error(fileErrorMessage())
                bundle.channels.size
            }
            onResult(result)
        }
    }

    private fun fileErrorMessage(): String =
        appContext.getString(it.fast4x.opentv.R.string.settings_file_error)
}
