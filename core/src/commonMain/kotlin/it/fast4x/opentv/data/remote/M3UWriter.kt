package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.model.Channel

/**
 * Genera il testo di una playlist M3U (estesa IPTV) a partire dalla lista
 * canali attualmente caricata: è il complementare di [M3UParser] e serve per
 * la funzione "Esporta lista" delle impostazioni (backup o condivisione).
 *
 * Il file prodotto è riassorbibile da OpenTV stesso (e da qualunque player
 * IPTV standard) senza perdita di informazioni:
 * - `tvg-id`, `tvg-name`, `tvg-logo`, `group-title` vengono riscritti come
 *   attributi dell'#EXTINF;
 * - la guida TV incorporata (fix35) viene riscritta nell'header come
 *   `x-tvg-url` (formato standard IPTV), così la lista esportata riporta
 *   con sé anche la guida e una re-importazione la ricarica da sola;
 * - le intestazioni per-canale ([Channel.httpUserAgent] / [Channel.httpReferrer])
 *   vengono riscritte come direttive `#EXTVLCOPT:`, il formato più compatibile;
 * - i canali marcati geo-bloccati riottengono il suffisso `[Geo-blocked]` nel
 *   nome, così un'eventuale re-importazione riattiva il badge.
 *
 * Nota sugli identificatori: quando la playlist originale non dichiarava un
 * `tvg-id`, [M3UParser] ne genera uno sintetico hashando l'URL. Quel valore
 * NON viene riscritto nel file (farebbe rumore in altri player): all'import
 * il parser lo rigenera comunque in modo deterministico dallo stesso URL,
 * quindi preferiti e "ultimi visti" sopravvivono al giro di andata e ritorno.
 */
object M3UWriter {

    // 16 caratteri esadecimali: l'output di M3UParser.stableId().
    private val syntheticIdRegex = Regex("^[0-9a-f]{16}$")

    fun build(channels: List<Channel>, epgUrl: String? = null): String = buildString {
        append("#EXTM3U")
        // Guida incorporata (fix35): solo se dichiarata dalla lista attiva.
        // Nel formato M3U esteso IPTV l'header è il posto canonico per la
        // guida (x-tvg-url); più URL separate da virgola sono ammesse, qui
        // viene riportata solo la primaria (quella effettivamente in uso).
        if (!epgUrl.isNullOrBlank()) {
            append(" x-tvg-url=\"").append(escape(epgUrl.trim())).append("\"")
        }
        append('\n')
        for (channel in channels) {
            append("#EXTINF:-1")
            if (!syntheticIdRegex.matches(channel.id)) {
                append(" tvg-id=\"").append(escape(channel.id)).append("\"")
            }
            channel.tvgName?.let { append(" tvg-name=\"").append(escape(it)).append("\"") }
            channel.logoUrl?.let { append(" tvg-logo=\"").append(escape(it)).append("\"") }
            channel.group?.let { append(" group-title=\"").append(escape(it)).append("\"") }
            // Il nome sta dopo la prima virgola (i separatori successivi ne
            // fanno parte: M3UParser usa substringAfter(',') una sola volta).
            append(',').append(channel.name)
            if (channel.geoBlocked) append(" [Geo-blocked]")
            append('\n')
            channel.httpUserAgent?.let {
                append("#EXTVLCOPT:http-user-agent=").append(it).append('\n')
            }
            channel.httpReferrer?.let {
                append("#EXTVLCOPT:http-referrer=").append(it).append('\n')
            }
            append(channel.url).append('\n')
        }
    }

    /** Gli attributi sono delimitati da virgolette: i doppi apici interni non sono ammessi. */
    private fun escape(value: String): String = value.replace("\"", "'")
}
