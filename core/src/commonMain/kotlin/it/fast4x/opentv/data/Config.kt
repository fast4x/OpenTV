package it.fast4x.opentv.data

/**
 * Costanti di configurazione dell'app.
 */
object Config {

    /** Playlist italiana di default (progetto iptv-org, aggiornata dalla community). */
    const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/it.m3u"

    /**
     * Paesi con playlist curata iptv-org selezionabile dall'utente in Home e
     * Impostazioni: ogni voce corrisponde a countries/<codice>.m3u di
     * iptv-org (e alla relativa bandierina nelle risorse desktop).
     * L'elenco completo segue le playlist "countries" pubblicate da
     * iptv-org; i nomi mostrati in UI sono localizzati per paese del
     * dispositivo (vedi CountryPicker / selettore desktop).
     */
    val SUPPORTED_COUNTRIES: List<String> = listOf(
        "ad", "ae", "al", "am", "ao", "ar", "at", "au",
        "az", "ba", "bd", "be", "bg", "bh", "bo", "br",
        "by", "ca", "ch", "ci", "cl", "cm", "cn", "co",
        "cr", "cu", "cy", "cz", "de", "dk", "do", "dz",
        "ec", "ee", "eg", "es", "et", "fi", "fj", "fr",
        "ge", "gh", "gr", "gt", "hk", "hn", "hr", "hu",
        "ie", "il", "in", "iq", "ir", "is", "it", "jo",
        "jp", "ke", "kr", "kw", "kz", "lb", "lk", "lt",
        "lu", "lv", "ma", "md", "mk", "mn", "mt", "mx",
        "my", "mz", "na", "ng", "ni", "nl", "no", "np",
        "nz", "om", "pa", "pe", "ph", "pk", "pl", "pr",
        "pt", "py", "qa", "ro", "rs", "ru", "sa", "se",
        "sg", "si", "sk", "sm", "sn", "sv", "th", "tn",
        "tr", "tt", "tw", "tz", "ua", "ug", "uk", "us",
        "uy", "uz", "ve", "vn", "za", "zm", "zw",
    )

    /**
     * Normalizza un codice paese inserito dall'utente o letto dalle
     * impostazioni: spazi rimossi, minuscole, esattamente due lettere
     * a-z; altrimenti null (codice assente o non valido).
     */
    fun normalizeCountryCode(code: String?): String? =
        code?.trim()?.lowercase()
            ?.takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } }

    /**
     * Bandierina emoji di un paese, costruita dai regional indicator
     * symbols della coppia ISO (nessuna mappa da mantenere: funziona per
     * ogni paese della lista). "uk" non è un codice ISO risolto in glifo:
     * per il glifo viene mappato su GB. Per un codice non valido ritorna
     * il codice maiuscolo; su desktop le emoji bandiera possono degradare
     * alle due lettere (Segoe UI non ha i glifi): lì si usano le immagini
     * PNG delle risorse.
     */
    fun countryFlagEmoji(countryCode: String): String {
        val cc = normalizeCountryCode(countryCode) ?: return countryCode.uppercase()
        val iso = if (cc == "uk") "gb" else cc
        // MAIUSCOLO prima dell'offset: i regional indicator partono da 'A'
        // (0x1F1E6); con le lettere minuscole l'offset sballa di 32 e produce
        // simboli arbitrari invece della bandierina (bug fixato in 48-fix8:
        // il picker mostrava glifi errati al posto delle bandierine).
        return iso.uppercase().map { regionalIndicator(it) }.joinToString("")
    }

    /** Coppia UTF-16 del regional indicator symbol di una lettera A-Z. */
    private fun regionalIndicator(letter: Char): String =
        StringBuilder()
            .apply { appendCodePoint(0x1F1E6 + (letter - 'A')) }
            .toString()

    /**
     * Mirror di riserva per la playlist italiana: file "stream" originale del
     * repository iptv-org su raw.githubusercontent.com. Viene provato in
     * automatico se il sorgente principale non è raggiungibile (DNS bloccato,
     * filtro di rete, problemi IPv6).
     */
    const val FALLBACK_PLAYLIST_URL =
        "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/it.m3u"

    /**
     * Template delle playlist curate per paese di iptv-org (es. countries/it.m3u).
     * Sono la fonte da cui recuperare loghi e categorie quando l'utente carica
     * una playlist grezza della cartella "streams" di iptv-org che ne è priva.
     */
    const val COUNTRY_PLAYLIST_URL_TEMPLATE =
        "https://iptv-org.github.io/iptv/countries/%s.m3u"

    /** Durata della cache dei dizionari logo/categoria per paese (24 ore). */
    const val LOGO_CACHE_TTL_MS: Long = 24L * 60 * 60 * 1000

    /** Numero massimo di playlist-per-paese scaricate in un passaggio di arricchimento. */
    const val MAX_LOGO_SOURCES = 6

    /** URL della playlist curata di un paese (codice a 2 lettere, es. "it"). */
    fun countryPlaylistUrl(countryCode: String): String =
        COUNTRY_PLAYLIST_URL_TEMPLATE.format(countryCode)

    /**
     * Template delle guide TV per paese pubblicate da iptv-epg.org, il
     * servizio community che mantiene gli EPG dell'ecosistema iptv-org
     * (stessa toolchain del repo iptv-org/epg e stesso database canali):
     * file XMLTV compresso gzip, aggiornato più volte al giorno, con id
     * canale nello stesso namespace dei tvg-id delle playlist iptv-org
     * (es. "Rai1.it"), quindi match diretto senza conversioni.
     */
    const val EPG_COUNTRY_URL_TEMPLATE = "https://iptv-epg.org/files/epg-%s.xml.gz"

    /** URL della guida TV per paese (codice a 2 lettere, es. "it"). */
    fun countryEpgUrl(countryCode: String): String =
        EPG_COUNTRY_URL_TEMPLATE.format(countryCode.lowercase())

    // Set lazy degli URL guida generati dall'app per i paesi supportati:
    // serve alla migrazione delle preferenze per riconoscere gli URL EPG
    // inseriti dai vecchi chip "guide rapide" (pre-fix15) e distinguerli
    // dagli URL manuali dell'utente, che NON vanno mai toccati.
    private val COUNTRY_EPG_URLS: Set<String> by lazy {
        SUPPORTED_COUNTRIES.mapTo(HashSet(SUPPORTED_COUNTRIES.size)) { countryEpgUrl(it) }
    }

    /**
     * True se l'URL EPG è uno di quelli generati dall'app (guida automatica
     * per paese di iptv-epg.org): questi URL non sono una scelta manuale
     * dell'utente e la migrazione li può rimuovere in favore della modalità
     * automatica (campo vuoto).
     */
    fun isAutoDerivableEpgUrl(url: String): Boolean = url.trim() in COUNTRY_EPG_URLS

    // Set lazy degli URL playlist curata per paese dei paesi supportati:
    // serve alla migrazione per riconoscere gli URL scritti dall'app dal
    // selettore paese pre-fix19 (che persisteva la scelta dentro la
    // preferenza playlistUrl) e distinguerli dagli URL manuali dell'utente,
    // che NON vanno mai toccati.
    private val COUNTRY_PLAYLIST_URLS: Set<String> by lazy {
        SUPPORTED_COUNTRIES.mapTo(HashSet(SUPPORTED_COUNTRIES.size)) { countryPlaylistUrl(it) }
    }

    /**
     * Codice paese se l'URL è una playlist curata iptv-org di un paese
     * supportato (es. countries/it.m3u, gli URL scritti dai selettori),
     * altrimenti null (campo vuoto o URL personale). Usato dalla migrazione
     * per spostare la scelta nella nuova preferenza [countryCode] dedicata.
     */
    fun countryCodeOfPlaylistUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed !in COUNTRY_PLAYLIST_URLS) return null
        return normalizeCountryCode(
            trimmed.substringAfterLast('/').removeSuffix(".m3u"),
        )
    }

    /** Durata della cache locale della playlist prima di un nuovo download (6 ore). */
    const val PLAYLIST_CACHE_TTL_MS: Long = 6L * 60 * 60 * 1000

    /**
     * User-Agent HTTP predefinito per le richieste dei flussi (ExoPlayer, VLC
     * e sonda di diagnosi). I UA "di prodotto" (VLC/3.x, ExoPlayer/…) vengono
     * rifiutati con 403 da molti CDN: un UA simile a un browser massimizza la
     * compatibilità. Override per-canale via `#EXTVLCOPT:http-user-agent`.
     */
    const val DEFAULT_STREAM_USER_AGENT = "Mozilla/5.0 (Linux; Android) OpenTV/1.0"

    /** Ritmo con cui l'orario di EPG ("in onda adesso") viene ricalcolato. */
    const val EPG_TICK_MS: Long = 60_000L

    /** Ritardo di auto-nascondimento dei controlli del player. */
    const val CONTROLS_HIDE_DELAY_MS: Long = 4_000L

    /** Finestra EPG mantenuta in memoria: da 1 ora fa a 24 ore avanti
     *  (copre la programmazione del resto della giornata e una parte di domani,
     *  ~7k programmi su guida Italia: circa 1 MB di testo in memoria). */
    const val EPG_WINDOW_PAST_MS: Long = 60L * 60 * 1000
    const val EPG_WINDOW_FUTURE_MS: Long = 24L * 60 * 60 * 1000
}
