package it.fast4x.opentv.data.model

/**
 * Un canale IPTV estratto dalla playlist M3U.
 *
 * @param id       identificatore stabile: `tvg-id` dell'M3U se presente,
 *                 altrimenti hash SHA-1 dell'URL (per stabilità di preferiti/recenti).
 * @param name     nome visualizzato del canale.
 * @param url      URL dello stream (HLS, MPEG-TS, DASH...).
 * @param logoUrl  URL del logo del canale, se presente nei metadati.
 * @param group    categoria dichiarata nell'M3U (group-title).
 * @param tvgName  nome tecnico del canale (tvg-name), utile per matching EPG.
 * @param httpUserAgent User-Agent richiesto dal sorgente (direttiva
 *                 `#EXTVLCOPT:http-user-agent` o attributo `http-user-agent`):
 *                 alcuni CDN rispondono 403 a UA sconosciuti come "VLC/3.x".
 * @param httpReferrer  Referrer richiesto dal sorgente (`#EXTVLCOPT:http-referrer`
 *                 o attributo `http-referrer`).
 * @param geoBlocked true se il nome nella playlist contiene il marker
 *                 `[Geo-blocked]`: il sorgente dichiara di bloccare alcune
 *                 zone geografiche. Il marker viene rimosso dal nome
 *                 visualizzato e mostrato come badge nella card del canale.
 */
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val tvgName: String? = null,
    val httpUserAgent: String? = null,
    val httpReferrer: String? = null,
    val geoBlocked: Boolean = false,
)

/**
 * Chiave del pseudo-gruppo assegnato ai canali privi di group-title.
 */
const val UNCATEGORIZED_GROUP = ""

/**
 * Un gruppo di canali (genere) dichiarato nel group-title della playlist.
 *
 * @param key   nome del gruppo così come appare nella playlist ("News",
 *              "Movies", ...), oppure [UNCATEGORIZED_GROUP] per i canali
 *              senza gruppo.
 * @param count numero di canali appartenenti al gruppo.
 */
data class GroupInfo(
    val key: String,
    val count: Int,
)

/**
 * Chiavi di gruppo di un canale: il group-title M3U può dichiarare più
 * categorie separate da punto e virgola ("Movies;Series") e il canale deve
 * comparire in ognuna. Un canale senza gruppo viene mappato sulla chiave
 * speciale [UNCATEGORIZED_GROUP].
 */
fun channelGroupKeys(channel: Channel): List<String> {
    val tokens = channel.group
        ?.split(';')
        ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .orEmpty()
    return if (tokens.isEmpty()) listOf(UNCATEGORIZED_GROUP) else tokens
}

/**
 * Elenco dei gruppi presenti nella playlist con il numero di canali per
 * ciascuno, in ordine alfabetico (case-insensitive) con il pseudo-gruppo
 * dei canali senza categoria per ultimo.
 */
fun channelGroups(channels: List<Channel>): List<GroupInfo> {
    val counts = HashMap<String, Int>()
    for (channel in channels) {
        for (key in channelGroupKeys(channel)) counts.merge(key, 1, Int::plus)
    }
    return counts.entries
        .sortedWith(compareBy({ it.key.isEmpty() }, { it.key.lowercase() }))
        .map { (key, count) -> GroupInfo(key, count) }
}

/**
 * Un programma della guida TV (XMLTV).
 *
 * @param channelId  id del canale di riferimento (uguale a [Channel.id] o al tvg-id).
 * @param start      inizio del programma (epoch millis).
 * @param stop       fine del programma (epoch millis).
 * @param title      titolo del programma.
 * @param description descrizione opzionale.
 */
data class Programme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String? = null,
) {
    fun isOnAir(now: Long = System.currentTimeMillis()): Boolean =
        start <= now && now < stop

    /**
     * Frazione di avanzamento del programma all'istante [now]: 0.0 all'inizio,
     * 1.0 alla fine, null se la durata dichiarata non è valida (stop <= start).
     * I valori fuori intervallo sono troncati entro 0..1: è la base della
     * barra di avanzamento nel player (feature 5) e non deve mai sforare.
     */
    fun progressAt(now: Long = System.currentTimeMillis()): Float? {
        if (stop <= start) return null
        val elapsed = (now - start).coerceIn(0L, stop - start)
        return elapsed.toFloat() / (stop - start)
    }
}

/**
 * Stato di caricamento della playlist dei canali.
 */
sealed interface PlaylistState {
    data object Loading : PlaylistState

    data class Success(
        val channels: List<Channel>,
        val fromCache: Boolean = false,
    ) : PlaylistState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : PlaylistState
}

/**
 * Voce della cronologia "ultimi visti".
 */
data class RecentEntry(
    val channelId: String,
    val timestamp: Long,
)

/**
 * Promemoria di un programma (feature "reminder"): l'utente ha chiesto di
 * essere avvisato quando inizia il programma [title] su [channelId], che
 * parte al timestamp [start] (epoch millis, coincide con Programme.start).
 * La coppia (channelId, start) identifica la voce: lo stesso titolo può
 * ripetersi nella griglia, l'istante di inizio no.
 */
data class ProgrammeReminder(
    val channelId: String,
    val start: Long,
    val title: String,
) {
    /** Chiave univoca della voce (canale + inizio). */
    fun key(): String = "$channelId@$start"
}

/**
 * Motore di riproduzione selezionabile dall'utente.
 *
 * - AUTO: ExoPlayer (decoder hardware) al primo tentativo, con riprove
 *   progressivo in caso di fallimento;
 * - EXO: solo ExoPlayer, nessun fallback;
 */
enum class PlaybackEngine { EXO }

/**
 * Impostazioni utente persistite.
 *
 * @param localPlaylistUri URI (SAF) della playlist importata da file locale.
 *        Quando non è vuoto ha priorità su [playlistUrl]: è il meccanismo con
 *        cui "Importa da file" rende persistente la scelta finché l'utente non
 *        salva un nuovo URL (o ripristina il predefinito).
 * @param countryCode paese scelto esplicitamente nei selettori di Home e
 *        Impostazioni (preferenza dedicata, fix19). Null = modalità
 *        automatica: il repository usa la playlist per paese del dispositivo.
 *        È indipendente da [playlistUrl] (che resta per i soli URL personali):
 *        così il campo URL delle Impostazioni non si valorizza più quando si
 *        sceglie un paese, e i selettori delle due schermate condividono la
 *        stessa fonte di verità in entrambe le direzioni.
 */
data class AppSettings(
    val playlistUrl: String = "",
    val epgUrl: String = "",
    val playbackEngine: PlaybackEngine = PlaybackEngine.EXO,
    val localPlaylistUri: String = "",
    val countryCode: String? = null,
)

/**
 * Profilo di sorgente (feature "profilazione della lista"): una coppia
 * autonoma playlist M3U + guida TV XMLTV opzionale, creata e gestita
 * dall'utente nelle Impostazioni e selezionabile dal menu sorgente di Home
 * e Impostazioni. Il profilo attivo BYPASSA la catena storica (URL
 * personale > paese scelto > predefinita iptv-org): playlist e guida
 * vengono prese solo da qui.
 *
 * @param id identificatore stabile generato alla creazione (persistito);
 * @param nome scelto dall'utente (es. "Lista di casa", "Sorgente parenti");
 * @param playlistUrl della lista M3U remota (obbligatoria: un profilo senza
 *        playlist è ignorato dalla risoluzione, difesa contro corruzioni);
 * @param epgUrl della guida XMLTV (opzionale: vuota = derivazione automatica
 *        dalla playlist iptv-org del profilo, come per la sorgente automatica).
 */
data class SourceProfile(
    val id: String,
    val name: String,
    val playlistUrl: String,
    val epgUrl: String = "",
)
