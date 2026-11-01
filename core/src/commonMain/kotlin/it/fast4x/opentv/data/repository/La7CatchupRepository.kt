package it.fast4x.opentv.data.repository

import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.remote.PlaylistFetcher
import it.fast4x.opentv.data.remote.RscFlight
import it.fast4x.opentv.data.remote.asArr
import it.fast4x.opentv.data.remote.asLong
import it.fast4x.opentv.data.remote.asObj
import it.fast4x.opentv.data.remote.asString
import it.fast4x.opentv.data.remote.parseJson
import it.fast4x.opentv.data.remote.JsonValue
import it.fast4x.opentv.data.remote.asBool
import it.fast4x.opentv.data.remote.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Un programma della guida "Rivedi" di La7 Play (catch-up TV).
 *
 * @param start       inizio trasmissione (epoch millis).
 * @param stop        fine prevista (epoch millis, start + durata EPG).
 * @param title       titolo in guida (es. "Omnibus ST.27", "Puntata del 1/11/2026").
 * @param description sinossi breve, se presente.
 * @param imageUrl    immagine del programma (scena/programma), se presente.
 * @param pathname    percorso della pagina puntata su la7play.it (es.
 *                    "/omnibus/puntate/puntata-del-1-11-2026-398406"): non null
 *                    SOLO per i programmi con replica on-demand disponibile.
 * @param drmProtected true se il contenuto è protetto (DRM Widevine /
 *                    geoblocco dichiarato in guida): la web app lo riproduce
 *                    con token e licenze che un player IPTV non può gestire,
 *                    quindi l'app lo mostra come non disponibile.
 */
data class CatchupProgramme(
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val pathname: String? = null,
    val drmProtected: Boolean = false,
) {
    /** True se il programma è riproducibile in app (replica presente, non protetta). */
    val playable: Boolean
        get() = pathname != null && !drmProtected
}

/**
 * Un giorno della programmazione "Rivedi".
 *
 * @param dateKey    chiave del sorgente nel formato "dd-MM-yyyy" (es. "01-11-2026").
 * @param startOfDay mezzanotte locale del giorno (epoch millis), base per le
 *                   etichette UI ("Oggi", "Ieri", nome del giorno).
 * @param programmes programmi del giorno in ordine di inizio.
 */
data class CatchupDay(
    val dateKey: String,
    val startOfDay: Long,
    val programmes: List<CatchupProgramme>,
)

/** Stream on-demand risolto per una puntata, pronto per il player. */
data class CatchupStream(
    /** URL HLS (master playlist) del programma, senza DRM né token. */
    val url: String,
    /** Durata dichiarata del media (secondi), se presente nella pagina puntata. */
    val durationSec: Long? = null,
    /** Titolo della puntata dichiarato dalla pagina (di solito "Puntata del …"). */
    val title: String? = null,
)

/**
 * Sessione catch-up attiva nel player: il programma scelto dal pannello
 * "Rivedi" e l'URL già risolto. Non null = il player sta riproducendo una
 * replica invece del live (l'UI mostra il badge Rivedi e "Torna al live").
 */
data class CatchupSession(
    val programme: CatchupProgramme,
    val streamUrl: String,
)

/**
 * Feature "Rivedi" (catch-up TV di La7 Play, https://www.la7play.it).
 *
 * Come funziona il sorgente (riscontro diretto sulle pagine, 11/2026):
 *
 * 1. GUIDA: la pagina `guida-tv-rivedi/<slug>` è una Next.js SSR il cui
 *    payload flight incorpora ~15 giorni di programmazione in formato
 *    XML→JSON (`@attributes.canale`, `giorno[].@attributes.data`,
 *    `giorno[].programma[]` con `ora`, `timestamp` (epoch s), `durata` (s),
 *    `titolo`, `sinossi`, immagini, `bundle` ("replica" = replica on-demand
 *    disponibile), `pathname` (pagina puntata), `drm`, `geolock`). Una sola
 *    richiesta copre la finestra dei 7 giorni: si filtrano i giorni passati
 *    + oggi e si scartano i futuri.
 *
 * 2. STREAM: la pagina puntata incorpora `videoData` con `uploadVideo.
 *    cf_remote_url` (percorsi MP4 delle rendizioni, separati da virgola) e
 *    il flag `drm`. L'URL HLS si costruisce come fa il player del sito:
 *    `{cdn}/local/hls/,{percorso1},{percorso2},.mp4.urlset/master.m3u8`
 *    dove i percorsi perdono l'estensione ".mp4" e ricevono il prefisso
 *    "/nk/" (il flag del sito `!cf_old_entry_id` ⇒ sempre prefisso per le
 *    puntate attuali). Il manifest risponde 200 senza token né Referer per
 *    i contenuti non DRM (verificato su TG La7, Omnibus, Otto e mezzo);
 *    i contenuti `drm: true` (film, alcuni documentari) servono Widevine +
 *    token della web app e vengono rifiutati (502): marcati "non disponibile".
 *
 * 3. CANALI: il Rivedi esiste per `la7` e `la7-cinema` (LA7d risponde 404).
 *    Il match del canale è sull'IDENTITÀ (id/tvg-name/nome normalizzati), non
 *    sul selettore paese: così il Rivedi funziona anche dentro i profili di
 *    sorgente personali che contengono La7.
 */
object La7Catchup {

    /** Slug della pagina Rivedi per La7. */
    const val SLUG_LA7 = "la7"

    /** Slug della pagina Rivedi per La7 Cinema. */
    const val SLUG_CINEMA = "la7-cinema"

    /** URL della pagina guida "Rivedi" di un canale (slug del sorgente). */
    fun pageUrl(slug: String): String = "https://www.la7play.it/guida-tv-rivedi/$slug"

    /** URL assoluto della pagina puntata a partire dal pathname della guida. */
    fun puntataUrl(pathname: String): String = "https://www.la7play.it$pathname"

    /**
     * Slug "Rivedi" per un canale, o null se il canale non è coperto dal
     * servizio. Il confronto usa le tre identità del canale (id tecnico,
     * tvg-name, nome visibile), ognuna ridotta a sole lettere e cifre
     * minuscole dopo aver escluso la variante dopo '@' ("LA7.it@HD" →
     * "la7it", stessa convenzione di [epgKey]).
     *
     * Accettati (prefissi): "la7cinema*" → La7 Cinema; "la7*" → La7, con
     * ECCEZIONE esplicita di "la7d*" (LA7d è un canale diverso e il suo
     * Rivedi non esiste: la7play risponde 404).
     */
    fun slugFor(channel: Channel): String? {
        val candidates = sequenceOf(channel.id, channel.tvgName, channel.name)
            .filterNotNull()
            .mapNotNull(::normalizedCandidate)
        for (candidate in candidates) {
            when {
                candidate.startsWith("la7cinema") -> return SLUG_CINEMA
                candidate.startsWith("la7d") -> Unit // LA7d: canale diverso, no Rivedi
                candidate.startsWith("la7") -> return SLUG_LA7
            }
        }
        return null
    }

    /** Normalizza un'identità di canale: niente variante '@', solo [a-z0-9]. */
    private fun normalizedCandidate(raw: String): String? =
        raw.substringBefore('@')
            .lowercase(Locale.ROOT)
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .takeIf { it.isNotEmpty() }
}

/**
 * Repository del "Rivedi": scarica la guida per slug, la converte in
 * [CatchupDay] e risolve gli stream delle puntate. Cache in memoria con TTL
 * (la guida cambia solo a cavallo dei giorni; i percorsi puntata sono
 * stabili), nessuna persistenza: è contenuto effimero per definizione.
 *
 * Le richieste passano da [PlaylistFetcher.fetchText] come tutto il resto
 * dell'app (OkHttp, UA browser-like, gzip automatico): la7play.it accetta
 * quell'UA così com'è (verificato). Il PARSING (payload flight da centinaia
 * di KB + guida + pagine puntata) gira su Dispatchers.Default: i chiamanti
 * con scope legato alla UI (EDT del desktop, Main dell'app) non devono mai
 * eseguire elaborazioni pesanti sul loro thread.
 */
class La7CatchupRepository {

    /** Cache guida per slug: istante di fetch + giorni (già filtrati ai 7 Rivedi). */
    private val scheduleCache = HashMap<String, Pair<Long, List<CatchupDay>>>()

    /** Cache stream per pathname: i percorsi puntata sono stabili per tutta la finestra. */
    private val streamCache = HashMap<String, CatchupStream>()

    /** Svuota le cache (usato dai test e dal "Riprova" del pannello). */
    fun clearCache() {
        scheduleCache.clear()
        streamCache.clear()
    }

    /**
     * Programmazione "Rivedi" per lo slug [La7Catchup.pageUrl]: giorni dal
     * più recente (oggi incluso) al più vecchio, al massimo [RIVEDI_DAYS]
     * voci, programmi in ordine di orario. Solleva le eccezioni di rete
     * ([it.fast4x.opentv.data.remote.HttpException], IOException): il
     * chiamante mostra l'errore nel pannello con la voce "Riprova".
     */
    suspend fun schedule(slug: String): List<CatchupDay> {
        val now = System.currentTimeMillis()
        scheduleCache[slug]?.let { (fetchedAt, days) ->
            if (now - fetchedAt < SCHEDULE_TTL_MS) return days
        }
        val days = fetchSchedule(slug)
        scheduleCache[slug] = now to days
        return days
    }

    /**
     * Risolve l'URL HLS on-demand di una puntata. Restituisce null quando la
     * replica non è riproducibile in app (contenuto DRM/protetto, pagina
     * senza media). I fallimenti di rete propagano l'eccezione.
     */
    suspend fun resolveStream(pathname: String): CatchupStream? {
        streamCache[pathname]?.let { return it }
        val html = PlaylistFetcher.fetchText(La7Catchup.puntataUrl(pathname))
        val stream = withContext(Dispatchers.Default) { resolveStreamFromHtml(html) }
            ?: return null
        streamCache[pathname] = stream
        return stream
    }

    // -------------------------------------------------------------------
    // Guida: pagina SSR -> payload flight -> oggetto guidatv -> giorni
    // -------------------------------------------------------------------

    private suspend fun fetchSchedule(slug: String): List<CatchupDay> {
        val html = PlaylistFetcher.fetchText(La7Catchup.pageUrl(slug))
        return withContext(Dispatchers.Default) {
            val payload = RscFlight.payloadOf(html)
            val guidatv = extractGuidatv(payload)
                ?: throw IllegalStateException("La pagina Rivedi non contiene la programmazione attesa")
            parseGuidatv(guidatv)
        }
    }

    /**
     * Trova l'oggetto guida nel payload: il dato arriva dalla conversione
     * XML→JSON del sorgente e si riconosce dallo schema `{"@attributes":{…},
     * "giorno":[…]}`. Il payload può contenere altri blocchi con
     * "@attributes" (dati di layout/SEO): viene accettato il primo candidato
     * che parsa con un array "giorno" non vuoto.
     */
    private fun extractGuidatv(payload: String): JsonValue? {
        for (position in RscFlight.occurrences(payload, "\"@attributes\"")) {
            // Il '{' che apre l'oggetto guida precede immediatamente "@attributes".
            val open = payload.lastIndexOf('{', position)
            if (open < 0) continue
            val text = RscFlight.objectAt(payload, open) ?: continue
            val parsed = runCatching { parseJson(text) }.getOrNull() ?: continue
            val days = parsed["giorno"]?.asArr().orEmpty()
            if (days.isNotEmpty()) return parsed
        }
        return null
    }

    /**
     * Converte l'oggetto guida in [CatchupDay]: mantiene solo i giorni del
     * Rivedi (da 6 giorni fa a oggi, inclusi), scarta i futuri (il sorgente
     * ne incorpora 7 avanti) e ordina dal più recente, programmi in ordine
     * di inizio.
     */
    private fun parseGuidatv(guidatv: JsonValue): List<CatchupDay> {
        val dayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ITALY).apply { isLenient = false }

        // Finestra Rivedi: [oggi-6 giorni 00:00, oggi 24:00) — i giorni
        // fuori (passati più vecchi o futuri) vengono scartati.
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        val windowStart = todayStart - (RIVEDI_DAYS - 1) * DAY_MS
        val windowEnd = todayStart + DAY_MS

        val days = ArrayList<CatchupDay>()
        for (dayValue in guidatv["giorno"]?.asArr().orEmpty()) {
            val dateKey = dayValue["@attributes"]?.get("data")?.asString() ?: continue
            val startOfDay = runCatching { dayFormat.parse(dateKey)?.time }.getOrNull() ?: continue
            if (startOfDay < windowStart || startOfDay >= windowEnd) continue

            val programmes = ArrayList<CatchupProgramme>()
            for (programmeValue in dayValue["programma"]?.asArr().orEmpty()) {
                programmes += programmeOrNull(programmeValue) ?: continue
            }
            if (programmes.isNotEmpty()) {
                days += CatchupDay(
                    dateKey = dateKey,
                    startOfDay = startOfDay,
                    programmes = programmes.sortedBy { it.start },
                )
            }
        }
        return days.sortedByDescending { it.startOfDay }
    }

    /** Converte una voce "programma" della guida; null se i minimi mancano. */
    private fun programmeOrNull(value: JsonValue): CatchupProgramme? {
        val startSec = value["timestamp"]?.asLong() ?: return null
        val start = startSec * 1000L
        val durationSec = value["durata"]?.asLong() ?: DEFAULT_PROGRAMME_DURATION_SEC
        val stop = start + durationSec.coerceAtLeast(60L) * 1000L

        val pathname = value["pathname"]?.asString()?.takeIf { it.isNotBlank() }
        val bundle = value["bundle"]?.asString().orEmpty()
        // "replica" = esiste la puntata on-demand; tutto il resto ( eventi
        // singoli, fasce senza replica) non ha pathname: doppia difesa.
        val hasReplica = bundle == BUNDLE_REPLICA && pathname != null

        val drm = value["drm"]?.asString().orEmpty() == "1" ||
            value["drm"]?.asBool() == true
        val geolock = value["geolock"]?.asString().orEmpty() == "1" ||
            value["geolock"]?.asBool() == true

        return CatchupProgramme(
            start = start,
            stop = stop,
            title = value["titolo"]?.asString()?.takeIf { it.isNotBlank() }
                ?: value["titolo_serie"]?.asString()
                ?: return null,
            description = value["sinossi"]?.asString()?.takeIf { it.isNotBlank() },
            imageUrl = value["immagine_programma"]?.asString()?.takeIf { it.isNotBlank() }
                ?: value["immagine_scena"]?.asString()?.takeIf { it.isNotBlank() },
            // Senza replica il pathname non viene esposto (niente tap che poi
            // fallirebbe a valle): l'UI marca la riga come non disponibile.
            pathname = if (hasReplica) pathname else null,
            drmProtected = drm || geolock,
        )
    }

    // -------------------------------------------------------------------
    // Stream: pagina puntata -> videoData -> URL HLS del player del sito
    // -------------------------------------------------------------------

    private fun resolveStreamFromHtml(html: String): CatchupStream? {
        val payload = RscFlight.payloadOf(html)
        val videoData = extractVideoData(payload) ?: return null

        // Contenuti protetti: la web app li riproduce con Widevine + token
        // (svc-01.la7play.it/DRM); in app non c'è percorso di riproduzione.
        if (videoData["drm"]?.asBool() == true) return null
        if (videoData["geolockItalia"]?.asBool() == true) return null

        val uploadVideo = videoData["uploadVideo"] ?: return null
        val remoteUrl = uploadVideo["cf_remote_url"]?.asString()?.takeIf { it.isNotBlank() }
            ?: return null
        // cf_old_entry_id non vuoto = media di vecchia generazione: il player
        // del sito in quel caso NON aggiunge il prefisso /nk/ (flag n=false).
        val forceNkPrefix = uploadVideo["cf_old_entry_id"]?.asString().isNullOrEmpty()

        val cdn = Regex("\"hostnameCdn\"\\s*:\\s*\"([^\"]+)\"")
            .find(payload)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CDN

        return CatchupStream(
            url = buildHlsUrl(cdn, remoteUrl, forceNkPrefix),
            durationSec = uploadVideo["cf_duration"]?.asLong(),
            title = videoData["title"]?.asString(),
        )
    }

    /**
     * Trova `videoData` nel payload: primo candidato che parsa come blocco
     * con `uploadVideo` (l'oggetto è referenziato più volte nel flight,
     * una sola definizione contiene i dati).
     */
    private fun extractVideoData(payload: String): JsonValue? {
        for (position in RscFlight.occurrences(payload, "\"videoData\":{")) {
            val open = position + "\"videoData\":{".length - 1
            val text = RscFlight.objectAt(payload, open) ?: continue
            val parsed = runCatching { parseJson(text) }.getOrNull() ?: continue
            if (parsed["uploadVideo"] != null) return parsed
        }
        return null
    }

    /**
     * Costruisce l'URL HLS come il player del sito (funzione `ee` del bundle):
     * `{cdn}/local/hls/,{p1},{p2},.mp4.urlset/master.m3u8` — estensioni ".mp4"
     * rimosse dai percorsi, prefisso "/nk/" assicurato quando richiesto.
     */
    private fun buildHlsUrl(cdnBase: String, remoteUrl: String, forceNkPrefix: Boolean): String {
        val paths = remoteUrl.split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .map { raw ->
                var path = raw.replace(".mp4", "")
                if (forceNkPrefix && !path.startsWith("/nk/")) {
                    path = if (path.startsWith("/")) "/nk$path" else "/nk/$path"
                }
                path
            }
        require(paths.isNotEmpty()) { "cf_remote_url vuoto" }
        return "${cdnBase.trimEnd('/')}/local/hls/,${paths.joinToString(",")},.mp4.urlset/master.m3u8"
    }

    private companion object {
        /** Giorni del Rivedi: oggi + i 6 precedenti (la finestra dichiarata dal sito). */
        const val RIVEDI_DAYS = 7

        /** Durata di un giorno in ms, per la finestra. */
        const val DAY_MS = 24L * 60 * 60 * 1000

        /** TTL della cache guida: dentro il giorno la programmazione non cambia. */
        const val SCHEDULE_TTL_MS = 30L * 60 * 1000

        /** Durata di riserva quando la guida non dichiara "durata" (30 min). */
        const val DEFAULT_PROGRAMME_DURATION_SEC = 1800L

        /** Valore "bundle" che dichiara la replica on-demand disponibile. */
        const val BUNDLE_REPLICA = "replica"

        /** CDN VOD predefinito (playerSettings.hostnameCdn delle pagine puntata). */
        const val DEFAULT_CDN = "https://awsvodpkg.iltrovatore.it"
    }
}
