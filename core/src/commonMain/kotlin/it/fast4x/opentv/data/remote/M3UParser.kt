package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.model.Channel
import java.security.MessageDigest

/**
 * Parser per playlist in formato M3U/M3U8 (esteso con i metadati
 * `tvg-*` e `group-title` usati da IPTV).
 *
 * Formato gestito:
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-id="Rai1.it" tvg-logo="https://..." group-title="Italy",Rai 1
 * #EXTVLCOPT:http-user-agent=Mozilla/5.0 ...
 * https://indirizzo-dello-stream.m3u8
 * ```
 * Vengono inoltre accettate le direttive `#EXTGRP:` (categoria). Le direttive
 * `#EXTVLCOPT:http-user-agent` / `:http-referrer` (e i rispettivi attributi
 * `http-user-agent` / `http-referrer` nell'EXTINF, formato iptv-org recente)
 * NON vengono ignorate: diversi CDN rifiutano con 403 i client che non si
 * presentano con lo User-Agent (o il referrer) dichiarato nella playlist.
 * Tutte le altre righe di commento (es. `#KODIPROP`) restano ignorate.
 *
 * Guida TV incorporata (fix35): l'header `#EXTM3U` può dichiarare la guida
 * con gli attributi `x-tvg-url` / `url-tvg` / `tvg-url` (sinonimi usati dai
 * vari editor di playlist), anche come LISTA separata da virgole (FreeIPTV
 * ne dichiara oltre cento, una per paese/sorgente). [parseEpgUrls] la
 * estrae e [selectEpgUrls] sceglie i candidati migliori per i canali
 * effettivamente caricati, così la guida dichiarata dalla lista si carica
 * senza che l'utente debba configurare un URL a parte.
 */
object M3UParser {

    private val attrRegex = Regex("([A-Za-z0-9\\-_]+)=\"([^\"]*?)\"")
    private val extinfRegex = Regex("^\\s*#EXTINF:\\s*-?[\\d.]+(.*)$", RegexOption.IGNORE_CASE)

    /**
     * Marker `[Geo-blocked]` (iptv-org): il sorgente dichiara un blocco
     * geografico. Viene rimosso dal nome e trasformato in [Channel.geoBlocked].
     */
    private val geoBlockedRegex = Regex("\\[\\s*geo-?blocked\\s*\\]", RegexOption.IGNORE_CASE)
    private val multiSpaceRegex = Regex("\\s{2,}")

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()

        // fix36 — Dedup sulle STESSO chiavi usate come `key` dalle liste UI:
        // l'id (key = it.id nelle LazyColumn desktop) e l'URL (key = it.url
        // nella griglia Android). Le playlist elencano spesso sorgenti
        // alternative per lo stesso canale: stesso tvg-id, oppure stesso
        // stream ripetuto sotto nomi diversi SENZA tvg-id (in quel caso
        // entrambe le voci ricevevano lo stesso stableId dall'URL e la lista
        // canali desktop crashava con "Key was already used"). La prima
        // occorrenza vince (in genere la sorgente principale).
        val seenIds = HashSet<String>()
        val seenUrls = HashSet<String>()

        var pendingName = ""
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null
        var pendingReferrer: String? = null
        var pendingGeoBlocked = false
        var hasPending = false

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val rest = extinfRegex.find(line)?.groupValues?.get(1).orEmpty()
                    var nameCandidate = rest
                    for (match in attrRegex.findAll(rest)) {
                        val key = match.groupValues[1].lowercase()
                        val value = match.groupValues[2]
                        when (key) {
                            "tvg-id" -> pendingTvgId = value.takeIf { it.isNotBlank() }
                            "tvg-name" -> pendingTvgName = value.takeIf { it.isNotBlank() }
                            "tvg-logo" -> pendingLogo = value.takeIf { it.isNotBlank() }
                            "group-title" -> pendingGroup = value.takeIf { it.isNotBlank() }
                            // iptv-org mette a volte le intestazioni direttamente
                            // come attributi dell'EXTINF (oltre che in EXTVLCOPT).
                            "http-user-agent" -> pendingUserAgent = value.takeIf { it.isNotBlank() }
                            "http-referrer" -> pendingReferrer = value.takeIf { it.isNotBlank() }
                        }
                    }
                    // Rimuove gli attributi: resta ",Nome canale" (la virgola finale
                    // separa i metadati dal nome; il nome può contenere altre virgole).
                    nameCandidate = attrRegex.replace(rest, "")
                    val parsedName = nameCandidate.substringAfter(',').trim()
                        .ifBlank { pendingTvgName.orEmpty() }
                    pendingGeoBlocked = geoBlockedRegex.containsMatchIn(parsedName)
                    pendingName = if (pendingGeoBlocked) {
                        // Il marker non serve nel nome visibile: diventa un badge.
                        parsedName.replace(geoBlockedRegex, "")
                            .replace(multiSpaceRegex, " ")
                            .trim()
                            .trimEnd('-', ',', '·')
                            .trim()
                    } else {
                        parsedName
                    }
                    hasPending = true
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    val group = line.substringAfter(':').trim()
                    if (group.isNotEmpty() && pendingGroup == null) pendingGroup = group
                }

                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    // Formato: #EXTVLCOPT:http-user-agent=... | :http-referrer=...
                    // Un solo substringAfter: il valore può contenere ':' (URL referrer).
                    val body = line.substringAfter(':').trim()
                    val key = body.substringBefore('=').trim().lowercase()
                    val value = body.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" ->
                                if (pendingUserAgent == null) pendingUserAgent = value
                            "http-referrer" ->
                                if (pendingReferrer == null) pendingReferrer = value
                        }
                    }
                }

                line.startsWith("#") -> Unit // altre direttive: ignorate

                else -> {
                    // La riga dopo #EXTINF contiene l'URL dello stream.
                    if (hasPending && line.startsWith("http", ignoreCase = true)) {
                        // Salta le sorgenti alternative dello stesso canale:
                        // id già visto (tvg-id ripetuto, oppure stesso URL ->
                        // stesso stableId) oppure URL già visto (stesso stream
                        // sotto nomi diversi: chiave doppia nella griglia
                        // Android che usa key = it.url).
                        val id = pendingTvgId ?: stableId(line)
                        if (seenIds.add(id) && seenUrls.add(line)) {
                            channels += Channel(
                                id = id,
                                name = pendingName.ifBlank { "Canale ${channels.size + 1}" },
                                url = line,
                                logoUrl = pendingLogo,
                                group = pendingGroup,
                                tvgName = pendingTvgName,
                                httpUserAgent = pendingUserAgent,
                                httpReferrer = pendingReferrer,
                                geoBlocked = pendingGeoBlocked,
                            )
                        }
                    }
                    hasPending = false
                    pendingName = ""
                    pendingTvgId = null
                    pendingTvgName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingUserAgent = null
                    pendingReferrer = null
                    pendingGeoBlocked = false
                }
            }
        }
        return channels
    }

    /**
     * Estrae le URL della guida TV dichiarate nell'header `#EXTM3U` (fix35).
     *
     * Attributi accettati (sinonimi di settore, case-insensitive):
     * `x-tvg-url`, `url-tvg`, `tvg-url`. Il valore può essere una lista
     * separata da virgole: vengono restituite in ordine di dichiarazione,
     * deduplicate e senza vuoti. Nessun'URL = lista senza guida incorporata
     * (la risoluzione della guida prosegue con la catena storica).
     */
    fun parseEpgUrls(content: String): List<String> {
        val header = content.lineSequence()
            .firstOrNull { it.trimStart().startsWith("#EXTM3U", ignoreCase = true) }
            ?: return emptyList()
        val urls = ArrayList<String>()
        for (match in attrRegex.findAll(header)) {
            if (match.groupValues[1].lowercase() in epgHeaderKeys) {
                for (part in match.groupValues[2].split(',')) {
                    val url = part.trim()
                    if (url.startsWith("http", ignoreCase = true)) urls += url
                }
            }
        }
        return urls.distinct()
    }

    /**
     * Sceglie le URL guida più adatte ai canali caricati (fix35).
     *
     * Le liste che dichiarano UNA sola guida non hanno alternativa: passa
     * com'è. Con più guide (FreeIPTV ne elenca oltre cento) non è possibile
     * scaricarle tutte: ogni candidato viene punteggiato in base a quanti
     * canali della lista hanno un suffisso nazionale del `tvg-id`
     * ("Rai1.it" -> "it") combaciante con i token del nome file della guida
     * ("epg_ripper_IT1.xml.gz" -> "IT" -> "it"). Vincono i punteggi migliori
     * (parità = ordine di dichiarazione), fino a [limit] candidate che il
     * caricatore fonderà. Se NESSUNA combacia (id senza convenzione
     * nazionale) resta la prima dichiarata: un tentativo è meglio di zero.
     */
    fun selectEpgUrls(
        urls: List<String>,
        channels: List<Channel>,
        limit: Int = 3,
    ): List<String> {
        if (urls.size <= 1) return urls
        val suffixCounts = HashMap<String, Int>()
        for (channel in channels) {
            val suffix = channel.id.substringBefore('@')
                .substringAfterLast('.', "")
                .lowercase()
            // Solo suffissi nazionali veri e propri ("it", "de", "va"...):
            // scartano sia i token troppo lunghi sia gli id sintetici hash.
            if (suffix.length in 2..3 && suffix.all { it in 'a'..'z' }) {
                suffixCounts.merge(suffix, 1, Int::plus)
            }
        }
        if (suffixCounts.isEmpty()) return urls.take(1)
        val scored = urls.mapIndexed { index, url ->
            Triple(index, url, scoreForChannelSuffixes(url, suffixCounts))
        }
        val best = scored.maxByOrNull { it.third } ?: return urls.take(1)
        if (best.third <= 0) return urls.take(1)
        // sortedByDescending è STABILE: a pari punteggio vince l'ordine
        // dell'header, così la selezione è deterministica.
        return scored.filter { it.third > 0 }
            .sortedByDescending { it.third }
            .take(limit)
            .map { it.second }
    }

    /** Punteggio di una URL guida = canali coperti dai suoi token nazionali. */
    private fun scoreForChannelSuffixes(url: String, suffixCounts: Map<String, Int>): Int {
        val fileName = url.substringAfterLast('/').substringBefore('.')
        var score = 0
        for (token in fileName.split(tokenSeparatorRegex)) {
            // Le guide "per paese" di solito numerano i file (IT1, US_LOCALS2,
            // BE2...): le cifre in coda non fanno parte del codice paese.
            val key = token.trimEnd { it.isDigit() }.lowercase()
            score += suffixCounts[key] ?: 0
        }
        return score
    }

    /**
     * Separatore dei token nei nomi file delle guide: trattini, underscore,
     * punti e qualunque non-alfanumerico ("epg_ripper_ALL_SOURCES1",
     * "viva-russia.ru").
     */
    private val tokenSeparatorRegex = Regex("[^A-Za-z0-9]+")

    /** Attributi header che dichiarano la guida TV (sinonimi, minuscoli). */
    private val epgHeaderKeys = setOf("x-tvg-url", "url-tvg", "tvg-url")

    /** Id deterministico derivato dall'URL (usato quando manca `tvg-id`). */
    fun stableId(url: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
