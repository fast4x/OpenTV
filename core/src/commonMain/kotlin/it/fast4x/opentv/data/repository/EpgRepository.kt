package it.fast4x.opentv.data.repository

import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.remote.HttpException
import it.fast4x.opentv.data.remote.PlaylistFetcher
import it.fast4x.opentv.data.remote.XmltvParser
import it.fast4x.opentv.data.remote.XmltvText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.ZipException

/**
 * Chiave EPG di un canale: i tvg-id delle playlist iptv-org qualificano la
 * variante del flusso con un suffisso ("Rai1.it@SD", "Rai1.it@HD", "...@IT"),
 * mentre le guide XMLTV (iptv-epg.org e compagnie) usano l'id base del
 * canale ("Rai1.it"). Normalizzando su entrambi i lati il match funziona a
 * prescindere dal suffisso; idempotente su id già privi di "@".
 */
fun epgKey(channelId: String): String = channelId.substringBefore('@').trim()

/** Stato del caricamento della guida TV. */
sealed interface EpgStatus {
    data object Idle : EpgStatus
    data object Loading : EpgStatus
    data class Ready(val programmeCount: Int) : EpgStatus
    data class Error(val message: String) : EpgStatus
}

/**
 * Repository della guida TV (EPG base in formato XMLTV).
 *
 * Scarica il file (anche .gz), lo parsa mantenendo solo i programmi nella
 * finestra temporale utile [1h fa → +24h] e li espone in memoria per la
 * consultazione rapida di "in onda adesso", "a seguire" e della
 * programmazione del canale.
 *
 * fix35 — guide multiple: accetta una CATENA di candidati (tipicamente la
 * guida incorporata nella playlist + la guida storica) e fonde i programmi
 * di quelle che riesce a caricare: i canali che la guida primaria non copre
 * possono essere recuperati dalle successive. Costruisce anche un indice
 * dei display-name della guida, così i canali il cui id non combacia con
 * quello della guida vengono comunque abbinati per nome ([keyFor]).
 */
class EpgRepository {

    private val mutex = Mutex()

    private val _programmes = MutableStateFlow<Map<String, List<Programme>>>(emptyMap())
    val programmes: StateFlow<Map<String, List<Programme>>> = _programmes.asStateFlow()

    private val _status = MutableStateFlow<EpgStatus>(EpgStatus.Idle)
    val status: StateFlow<EpgStatus> = _status.asStateFlow()

    /**
     * Ultima catena caricata CON SUCCESSO: se load() viene richiamato con
     * la stessa catena mentre lo stato è già Ready, il download viene
     * saltato. Senza questa guardia ogni chiamante che riemette gli stessi
     * URL (flusso Home, tasto Salva delle Impostazioni, doppio avvio
     * concorrente Home+Impostazioni) ri-scaricherebbe decine di MB di
     * guida a ogni occasione; dopo un errore invece loadedKey non viene
     * aggiornato, così il tentativo successivo riparte davvero.
     * (fix35: da singolo URL a chiave dell'intera catena.)
     */
    private var loadedKey: String? = null

    /**
     * Indice di ricerca per nome (fix35): display-name normalizzato ->
     * chiave guida. Ricostruito a ogni caricamento riuscito dai
     * display-name dichiarati dalla guida ([XmltvParser.Result.channelAliases]).
     */
    private var nameIndex: Map<String, String> = emptyMap()

    /**
     * Indice per id NORMALIZZATO (fix35): le guide scrivono gli stessi id
     * con separatori propri ("La.5.it" vs "La5.it", "R.101.TV.it" vs
     * "R101TV.it"); normalizzando (solo lettere e cifre, suffisso
     * nazionale escluso) le due forme combaciano. Ricostruito a ogni
     * caricamento riuscito, come [nameIndex].
     */
    private var idIndex: Map<String, String> = emptyMap()

    /** Scarica e parsa la guida TV all'URL indicato (vuoto = nessuna guida). */
    suspend fun load(url: String) = load(listOf(url))

    /**
     * Scarica e fonde le guide della catena di candidati (fix35).
     *
     * I candidati vengono provati in ordine: quello che fallisce (rete,
     * HTTP, file non-XMLTV) NON blocca gli altri — una guida su tre vale
     * più di zero. Solo se NESSUNA carica lo stato diventa Error, con il
     * messaggio del primo fallimento (il candidato primario, il più
     * significativo). Gli errori del singolo file in una catena parzialmente
     * riuscita si perdono volutamente: la guida parziale è già il miglior
     * risultato ottenibile, l'utente non deve vedersi un errore per un
     * candidato di riserva.
     */
    suspend fun load(urls: List<String>) = mutex.withLock {
        val candidates = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (candidates.isEmpty()) {
            _programmes.value = emptyMap()
            _status.value = EpgStatus.Idle
            loadedKey = null
            return@withLock
        }

        // Stessa catena già caricata con successo: niente ri-download.
        val chainKey = candidates.joinToString(separator = "\n")
        if (chainKey == loadedKey && _status.value is EpgStatus.Ready) return@withLock

        _status.value = EpgStatus.Loading
        try {
            // Parse in STREAMING dallo stream gzip del server: le guide per
            // paese di iptv-epg.org decomprimono in decine di MB e mantenerle
            // come String intera è sprecato (e rischioso su Android); il
            // parser a pull filtrava comunque la finestra temporale riga per
            // riga. La finestra utile resta [1h fa → +24h].
            val now = System.currentTimeMillis()
            val windowStart = now - Config.EPG_WINDOW_PAST_MS
            val windowEnd = now + Config.EPG_WINDOW_FUTURE_MS

            val merged = HashMap<String, MutableList<Programme>>()
            val names = HashMap<String, String>()
            val ids = HashMap<String, String>()
            var total = 0
            var loaded = 0
            var firstError: Throwable? = null

            for (trimmed in candidates) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        // fix34: openGuideStream gestisce gzip E archivi zip
                        // (anche annidati) con timeout dedicato; XmltvText
                        // .readerFor risolve BOM, encoding dichiarato ed entità
                        // HTML non-XML prima che il parser veda il testo. La
                        // pipeline resta in streaming: mai il file intero in
                        // memoria.
                        PlaylistFetcher.openGuideStream(trimmed).use { stream ->
                            XmltvParser.parse(
                                XmltvText.readerFor(stream),
                                windowStart,
                                windowEnd,
                            )
                        }
                    }

                    // fix34: una risposta 200 che non contiene NESSUN canale
                    // NESSUN programma non è una guida valida (spesso è una
                    // pagina HTML di errore/protezione o un XML di altro
                    // tipo): meglio un errore esplicito che una "guida attiva
                    // con 0 programmi" che non spiega perché "in onda adesso"
                    // resta vuoto. Un XMLTV vero ha almeno i <channel> (che
                    // non dipendono dalla finestra temporale).
                    // fix35: in una catena non è fatale — si passa al
                    // candidato successivo; se era l'unico, resta l'errore.
                    if (result.channelNames.isEmpty() && result.programmeCount == 0) {
                        if (firstError == null) firstError = IOException(NOT_A_GUIDE_MESSAGE)
                        continue
                    }

                    loaded++
                    total += result.programmeCount
                    // Ri-keying con [epgKey]: la guida dichiara id base
                    // ("Rai1.it"), ma per robustezza (file con suffissi
                    // variante o id duplicati) le liste di canali con la
                    // stessa chiave vengono unite; l'ordinamento finale è a
                    // valle, dopo la fusione di tutta la catena.
                    for ((id, list) in result.programmes) {
                        merged.getOrPut(epgKey(id)) { ArrayList() }.addAll(list)
                    }
                    // Indice dei nomi (fix35): ogni display-name della guida
                    // diventa una chiave di ricerca per i canali che non
                    // combaciano per id. Il primo nome visto vince: i
                    // display-name identici tra canali diversi sono rumore
                    // tipico delle guide aggregate.
                    for ((id, aliases) in result.channelAliases) {
                        val key = epgKey(id)
                        for (alias in aliases) {
                            val normalized = normalizeName(alias)
                            if (normalized.isNotEmpty()) names.putIfAbsent(normalized, key)
                        }
                        // Indice per id normalizzato (fix35): la forma senza
                        // separatori dell'id dichiarato dalla guida.
                        val normId = normalizeId(id)
                        if (normId.isNotEmpty()) ids.putIfAbsent(normId, key)
                    }
                } catch (error: Throwable) {
                    // fix34: messaggi per classe di errore — prima si mostrava
                    // la stringa grezza dell'eccezione ("HTTP 403", "Read
                    // timed out", "unexpected token...") incomprensibile per
                    // l'utente. Throwable per intercettare anche
                    // OutOfMemoryError: una guida enorme non deve far crashare
                    // l'app. fix35: il fallimento di UN candidato non abortisce
                    // la catena.
                    if (firstError == null) firstError = error
                }
            }

            if (loaded == 0) {
                _status.value = EpgStatus.Error(
                    friendlyEpgError(firstError ?: IOException(NOT_A_GUIDE_MESSAGE)),
                )
                return@withLock
            }

            _programmes.value = merged.mapValues { (_, list) -> list.sortedBy { it.start } }
            nameIndex = names
            idIndex = ids
            loadedKey = chainKey
            _status.value = EpgStatus.Ready(total)
        } catch (error: Throwable) {
            // Rete di sicurezza esterna (fuori dal ciclo candidati): nessun
            // percorso deve poter lasciare lo stato in Loading.
            _status.value = EpgStatus.Error(friendlyEpgError(error))
        }
    }

    /** Traduce le eccezioni tipiche del caricamento guida in messaggi chiari. */
    private fun friendlyEpgError(error: Throwable): String = when (error) {
        is HttpException -> when (error.code) {
            401, 403 -> "Il server della guida nega l'accesso (HTTP ${error.code}): " +
                "l'URL richiede autenticazione o blocca l'app."
            404 -> "Guida non trovata sul server (HTTP 404): l'URL è cambiato o non esiste."
            429 -> "Il server della guida sta limitando le richieste (HTTP 429): riprova più tardi."
            else -> "Il server della guida ha risposto HTTP ${error.code}."
        }

        is SocketTimeoutException ->
            "Connessione scaduta durante il download della guida: il server è troppo lento."
        is UnknownHostException ->
            "Host della guida non raggiungibile: controlla l'URL e la connessione."
        is ConnectException ->
            "Impossibile connettersi al server della guida."
        is ZipException ->
            "L'archivio della guida è corrotto o non supportato."
        is XmlPullParserException ->
            "Il file non è una guida XMLTV valida (struttura non riconosciuta)."
        is OutOfMemoryError ->
            "La guida è troppo grande per la memoria del dispositivo."
        is IOException ->
            error.message ?: "Errore di rete durante il download della guida."
        else -> error.message ?: error.javaClass.simpleName
    }

    /** Messaggio per i file 200 che non sono guide XMLTV (fix34). */
    private val NOT_A_GUIDE_MESSAGE =
        "Il file scaricato non contiene canali o programmi XMLTV " +
            "riconoscibili: controlla che l'URL punti a una guida TV."

    /** Rimuove la guida TV dalla memoria. */
    fun clear() {
        _programmes.value = emptyMap()
        _status.value = EpgStatus.Idle
        loadedKey = null
        nameIndex = emptyMap()
        idIndex = emptyMap()
    }

    /**
     * Chiave guida del canale (fix35): il suo id base se la guida lo copre,
     * altrimenti ricerca per NOME normalizzato tra i display-name dichiarati
     * dalla guida — recupera i canali che la guida conosce ma con un id
     * diverso da quello della playlist (le guide di terze parti usano
     * convenzioni di id tra loro incompatibili: "TV2000.va" può essere
     * "TV.2000.it" o solo "TV 2000"). Se nessun abbinamento, torna la
     * chiave per id (nessun programma, com'era).
     */
    fun keyFor(channel: Channel): String {
        val byId = epgKey(channel.id)
        if (_programmes.value.containsKey(byId)) return byId
        return aliasKeyFor(channel) ?: byId
    }

    /**
     * Solo l'abbinamento per NOME (fix35): chiave guida se un display-name
     * della guida combacia con il nome (o tvg-name) del canale, null se la
     * guida non lo conosce. Pensato per i chiamanti che stanno già leggendo
     * uno snapshot della mappa programmi (es. nelle combine della UI):
     * `map[epgKey(id)] ?: map[aliasKeyFor(channel)]` senza toccare lo stato
     * live del repository.
     */
    fun aliasKeyFor(channel: Channel): String? {
        nameIndex[normalizeName(channel.name)]?.let { return it }
        channel.tvgName?.let { tvg -> nameIndex[normalizeName(tvg)]?.let { return it } }
        return idIndex[normalizeId(channel.id)]
    }

    /** Programmi della guida per il canale (null = nessun programma noto). */
    fun programmesFor(channel: Channel): List<Programme>? =
        _programmes.value[keyFor(channel)]

    /**
     * Normalizzazione per l'abbinamento per nome (fix35): minuscole e SOLO
     * lettere e cifre — "Rai 1", "RAI 1" e "Rai1" diventano la stessa
     * chiave; le decorazioni delle playlist ("Ⓖ", "Ⓢ") e la punteggiatura
     * spariscono. Le lettere unicode restano (canali non latini).
     */
    private fun normalizeName(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Normalizzazione per ID (fix35): suffisso nazionale escluso ("La.5.it"
     * -> "La.5"), poi come [normalizeName] -> "la5". Il suffisso va tolto
     * PRIMA: "TV2000.va" e "TV.2000.it" differiscono proprio nel paese ma
     * designano lo stesso canale.
     */
    private fun normalizeId(id: String): String =
        normalizeName(id.substringBefore('@').replace(tldSuffixRegex, ""))

    /** Suffisso nazionale in coda all'id XMLTV (".it", ".DE"...). */
    private val tldSuffixRegex = Regex("\\.[a-z]{2,3}$", RegexOption.IGNORE_CASE)

    /** Programma in onda adesso sul canale (null se non disponibile). */
    fun nowOn(channelId: String, now: Long = System.currentTimeMillis()): Programme? =
        _programmes.value[epgKey(channelId)]?.lastOrNull { it.start <= now && now < it.stop }

    /** Prossimo programma in onda sul canale (null se non disponibile). */
    fun next(channelId: String, now: Long = System.currentTimeMillis()): Programme? =
        _programmes.value[epgKey(channelId)]?.firstOrNull { it.start >= now }
}
