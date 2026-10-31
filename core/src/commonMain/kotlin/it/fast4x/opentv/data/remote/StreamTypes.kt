package it.fast4x.opentv.data.remote

/**
 * Inferenza del tipo di stream dall'URL, pensata per playlist IPTV.
 *
 * ExoPlayer deduce il tipo di sorgente dall'estensione del file nell'URL:
 * per gli URL "gateway" senza estensione riconoscibile (es. i relinker di
 * RAI e Mediaset come `...relinkerServlet.htm?cont=...`, che in realtà
 * restituiscono una playlist HLS) deduce un file generico e prova gli
 * extractor binari sopra il testo M3U8, ottenendo
 * [androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED].
 *
 * Qui il tipo viene impostato esplicitamente sul [MediaItem][androidx.media3.common.MediaItem];
 * il player prevede comunque un tentativo successivo in modalità progressiva
 * nel caso l'ipotesi HLS fosse sbagliata (vedi PlayerViewModel).
 */
object StreamTypes {

    // Costanti MIME equivalenti a androidx.media3.common.MimeTypes: il modulo
    // core è multiplatform e non dipende da Media3, le stringhe sono identiche
    // a quelle usate da ExoPlayer/VLC.
    private const val MIME_HLS = "application/x-mpegurl"
    private const val MIME_DASH = "application/dash+xml"
    private const val MIME_SS = "application/vnd.ms-sstr+xml"
    private const val MIME_MP4 = "video/mp4"
    private const val MIME_MP2T = "video/mp2t"
    private const val MIME_MP3 = "audio/mpeg"
    private const val MIME_AAC = "audio/mp4a-latm"
    private const val MIME_FLV = "video/x-flv"

    /**
     * Restituisce il mimeType più probabile per l'URL. Gli URL senza una
     * estensione nota nell'IPTV sono quasi sempre endpoint HLS (relinker,
     * gateway CDN, index senza estensione), quindi il default è HLS.
     */
    fun guessMimeType(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MIME_HLS

            path.endsWith(".mpd") || path.contains("dash") -> MIME_DASH

            path.endsWith(".ism") || path.endsWith(".isml") -> MIME_SS

            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") ->
                MIME_MP4

            path.endsWith(".ts") || path.endsWith(".mts") || path.endsWith(".m2ts") ->
                MIME_MP2T

            path.endsWith(".mp3") -> MIME_MP3
            path.endsWith(".aac") -> MIME_AAC
            path.endsWith(".flv") -> MIME_FLV

            // URL "gateway" senza estensione nota: assumiamo HLS.
            else -> MIME_HLS
        }
    }
}
