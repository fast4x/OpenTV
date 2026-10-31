package it.fast4x.opentv.data.repository

/**
 * Messaggi utente usati dal [PlaylistRepository] (errori di rete, playlist
 * vuota, file illeggibile...). Il testo predefinito è quello italiano delle
 * risorse dell'app Android; su Android viene iniettata una variante che
 * legge dalle risorse localizzate, così il comportamento resta identico a
 * prima su qualunque lingua del dispositivo.
 */
interface PlaylistStrings {

    /** "Rete non raggiungibile..." */
    fun network(): String

    /** Fallimento TLS/handshake. */
    fun ssl(): String

    /** Errore HTTP con codice. */
    fun http(code: Int): String

    /** Playlist senza canali validi. */
    fun emptyPlaylist(): String

    /** File locale illeggibile/scrivibile non riuscito. */
    fun fileError(): String

    /** Nessun canale da esportare. */
    fun noChannels(): String

    /** Profilo senza URL playlist (export pacchetto profilo, feature profili). */
    fun profileNoPlaylist(): String
}

/** Testi italiani (identici a res/values-it/strings.xml). */
object ItalianPlaylistStrings : PlaylistStrings {
    override fun network(): String =
        "Rete non raggiungibile. Controlla la connessione e riprova."

    override fun ssl(): String =
        "Connessione sicura non riuscita: verifica data e ora del dispositivo, " +
            "o la rete (DNS/filtro) blocca il sorgente."

    override fun http(code: Int): String =
        "Il server ha risposto con errore (HTTP $code)"

    override fun emptyPlaylist(): String =
        "La playlist è vuota o non contiene canali validi"

    override fun fileError(): String =
        "Impossibile leggere o scrivere il file selezionato"

    override fun noChannels(): String =
        "Nessun canale da esportare: carica prima una playlist"

    override fun profileNoPlaylist(): String =
        "Il profilo non dichiara l'URL della playlist"
}
