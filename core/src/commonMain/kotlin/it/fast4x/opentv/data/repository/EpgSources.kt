package it.fast4x.opentv.data.repository

import it.fast4x.opentv.data.Config

/**
 * Risolve l'URL della guida TV (EPG) da caricare.
 *
 * Priorità:
 *  1. URL manuale impostato dall'utente nelle impostazioni (vince sempre,
 *     così una guida self-hosted col tool iptv-org/epg resta configurabile)
 *  2. automatico: paese scelto esplicitamente nei selettori (preferenza
 *     dedicata countryCode, fix19) -> guida per paese pubblicata da
 *     iptv-epg.org
 *  3. automatico: playlist dell'ecosistema iptv-org (dominio dedicato o repo
 *     GitHub) -> guida per paese pubblicata da iptv-epg.org, i cui id canale
 *     coincidono con i tvg-id della playlist (es. "Rai1.it")
 *  4. automatico: playlist non impostata -> guida del paese predefinito
 *     dell'app (Italia, come [Config.DEFAULT_PLAYLIST_URL])
 *
 * Perché NON una guida "per canale": iptv-epg.org pubblica file per PAESE
 * (non per canale) e un solo download copre l'intera lista, così "in onda
 * adesso" appare su tutte le card della Home oltre che nel player, senza
 * richieste moltiplicate durante lo zapping.
 */
object EpgSources {

    /** Codice paese predefinito quando la playlist non è impostata. */
    private const val DEFAULT_COUNTRY = "it"

    /** Domini/path dell'ecosistema iptv-org (playlist curate e mirror raw). */
    private val IPTV_ORG_HOST = Regex(
        pattern = "iptv-org\\.github\\.io|github(?:usercontent)?\\.com/iptv-org/",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    /** countries/it.m3u (curata) o streams/it.m3u (grezza), anche .m3u8. */
    private val COUNTRY_IN_PATH = Regex(
        pattern = "(?:countries|streams)/([a-z]{2})(?:\\.m3u8?)(?:[?#].*)?$",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    /**
     * URL guida per la playlist indicata; "" se non derivabile (playlist
     * personalizzata: l'utente imposterà un URL manuale).
     */
    fun autoUrlForPlaylist(playlistUrl: String): String {
        val url = playlistUrl.trim()
        if (url.isEmpty()) return Config.countryEpgUrl(DEFAULT_COUNTRY)
        if (!IPTV_ORG_HOST.containsMatchIn(url)) return ""
        return COUNTRY_IN_PATH.find(url)?.groupValues?.get(1)
            ?.let { Config.countryEpgUrl(it.lowercase()) }
            .orEmpty()
    }

    /**
     * URL guida effettivo: manuale > paese scelto nei selettori >
     * automatico dalla playlist; "" = nessuna guida. Il parametro
     * [countryCode] è opzionale per compatibilità con i chiamanti che non
     * conoscono ancora la preferenza dedicata (fix19).
     */
    fun resolve(
        manualEpgUrl: String,
        playlistUrl: String,
        countryCode: String? = null,
    ): String {
        val manual = manualEpgUrl.trim()
        if (manual.isNotEmpty()) return manual
        val country = Config.normalizeCountryCode(countryCode)
        if (country != null) return Config.countryEpgUrl(country)
        return autoUrlForPlaylist(playlistUrl)
    }
}
