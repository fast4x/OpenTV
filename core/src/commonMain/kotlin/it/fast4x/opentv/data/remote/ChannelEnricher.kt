package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Arricchisce i canali di playlist che non dichiarano loghi/categorie
 * (es. i file grezzi della cartella "streams" di iptv-org, privi di `tvg-logo` e
 * `group-title`, a differenza di quelli generati nella cartella "countries").
 *
 * Funzionamento: deriva il codice paese dai tvg-id (es. `Rai1.it` -> "it"),
 * scarica (con cache su disco di 24 ore) la playlist curata di quel paese e
 * costruisce un dizionario tvg-id -> (logo, categoria). Il dizionario viene
 * consultato con l'id completo ("Rai1.it@SD") e con l'id base ("Rai1.it"),
 * così i file grezzi e quelli curati si incrociano anche se usano varianti
 * di suffisso diverse.
 *
 * L'operazione non deve MAI bloccare la visualizzazione della lista: viene
 * chiamata dopo l'emissione dello stato [it.fast4x.opentv.data.model.PlaylistState.Success]
 * e sostituisce la lista solo se almeno un canale è stato effettivamente
 * arricchito. Ogni errore di rete è non fatale: si prosegue con la lista così com'è.
 */
object ChannelEnricher {

    private data class Artwork(val logoUrl: String?, val group: String?)

    /**
     * true se almeno un canale è senza logo o categoria E ha un tvg-id da cui
     * si può risalire a un paese: evita di lanciare coroutine e download
     * inutili (es. playlist già completa).
     */
    fun hasEnrichableChannels(channels: List<Channel>): Boolean =
        channels.any { (it.logoUrl == null || it.group == null) && countryOf(it.id) != null }

    /**
     * Restituisce la lista con loghi/categorie completati; se nessun canale è
     * cambiato ritorna esattamente la lista in ingresso (stessa istanza).
     *
     * @param cacheDir directory dove mettere le cache per-paese (filesDir su
     * Android, profilo utente su desktop).
     */
    suspend fun fillMissingArtwork(cacheDir: File, channels: List<Channel>): List<Channel> =
        withContext(Dispatchers.IO) {
            val missing = channels.filter { it.logoUrl == null || it.group == null }
            if (missing.isEmpty()) return@withContext channels

            val countries = missing.mapNotNull { countryOf(it.id) }
                .distinct()
                .take(Config.MAX_LOGO_SOURCES)
            if (countries.isEmpty()) return@withContext channels

            val dictionary = HashMap<String, Artwork>()
            for (country in countries) {
                val text = fetchCountryPlaylist(cacheDir, country) ?: continue
                mergeIntoDictionary(M3UParser.parse(text), dictionary)
            }
            if (dictionary.isEmpty()) return@withContext channels

            var changed = false
            val result = channels.map { channel ->
                if (channel.logoUrl != null && channel.group != null) {
                    channel
                } else {
                    val art = dictionary[channel.id] ?: dictionary[baseId(channel.id)]
                    if (art == null) {
                        channel
                    } else {
                        val logo = channel.logoUrl ?: art.logoUrl
                        val group = channel.group ?: art.group
                        if (logo == channel.logoUrl && group == channel.group) {
                            channel
                        } else {
                            changed = true
                            channel.copy(logoUrl = logo, group = group)
                        }
                    }
                }
            }
            if (changed) result else channels
        }

    /** Id senza suffisso variante: "Rai1.it@SD" -> "Rai1.it". */
    private fun baseId(id: String): String = id.substringBefore('@')

    /**
     * Codice paese dal tvg-id: l'ultima parte dopo il punto deve essere un TLD
     * di due lettere ("Rai1.it" -> "it", "Andromeda.us@Italy" -> "us").
     * Gli id sintetici (hash dell'URL, senza punto) non sono arricchibili.
     */
    private fun countryOf(id: String): String? {
        val tld = id.substringBefore('@').substringAfterLast('.', "")
        return if (tld.length == 2 && tld.all { it.isLetter() }) tld.lowercase() else null
    }

    /** Incrocia i canali curati nel dizionario: prima occorrenza per ogni chiave. */
    private fun mergeIntoDictionary(parsed: List<Channel>, dictionary: MutableMap<String, Artwork>) {
        for (channel in parsed) {
            val art = Artwork(channel.logoUrl, channel.group)
            dictionary.putIfAbsent(channel.id, art)
            val base = baseId(channel.id)
            if (base != channel.id) dictionary.putIfAbsent(base, art)
        }
    }

    /** Playlist curata del paese, con cache su disco (24 h, sopravvive al riavvio). */
    private suspend fun fetchCountryPlaylist(cacheDir: File, country: String): String? {
        val cache = File(cacheDir, "opentv_logos_$country.m3u")
        val fresh = cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < Config.LOGO_CACHE_TTL_MS
        if (fresh) {
            val cached = runCatching { cache.readText() }.getOrNull()
            if (cached != null) return cached
        }
        return try {
            val text = PlaylistFetcher.fetchText(Config.countryPlaylistUrl(country))
            runCatching { cache.writeText(text) } // il fallback non deve bloccare il flusso
            text
        } catch (_: Exception) {
            // Rete assente / paese senza playlist: si prosegue con la lista intatta.
            null
        }
    }
}
