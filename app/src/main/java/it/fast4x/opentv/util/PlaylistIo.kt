package it.fast4x.opentv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.fast4x.opentv.R
import it.fast4x.opentv.data.repository.LocalPlaylistReader
import it.fast4x.opentv.data.repository.PlaylistStrings

/**
 * Adattatori Android per il modulo :core: lettura della playlist importata
 * via ContentResolver (SAF) e messaggi utente localizzati dalle risorse.
 */

/**
 * Legge la sorgente locale salvata nelle preferenze. L'id è l'URI SAF scelto
 * con il selettore di sistema; il chiamante ([it.fast4x.opentv.data.repository.PlaylistRepository])
 * esegue già la lettura su Dispatchers.IO. null = sorgente illeggibile.
 */
class ContentResolverPlaylistReader(
    private val context: Context,
) : LocalPlaylistReader {

    override suspend fun read(sourceId: String): String? =
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(sourceId))
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
}

/**
 * Messaggi utente del repository presi dalle risorse localizzate: sulle
 * lingue non tradotte cadrà sul default inglese (values/), sull'italiano su
 * values-it — come prima della conversione multiplatform.
 */
class ResourcePlaylistStrings(
    private val context: Context,
) : PlaylistStrings {

    override fun network(): String = context.getString(R.string.error_network)

    override fun ssl(): String = context.getString(R.string.error_ssl)

    override fun http(code: Int): String = context.getString(R.string.error_http, code)

    override fun emptyPlaylist(): String = context.getString(R.string.error_empty_playlist)

    override fun fileError(): String = context.getString(R.string.settings_file_error)

    override fun noChannels(): String = context.getString(R.string.settings_no_channels)

    override fun profileNoPlaylist(): String =
        context.getString(R.string.settings_profile_no_playlist)
}

/** Legge il testo completo da un URI SAF (null se illeggibile). */
fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)
        ?.bufferedReader()
        ?.use { it.readText() }
}.getOrNull()

/**
 * Scrive testo su un URI SAF.
 *
 * "wt" tronca il file esistente (sovrascrivere un file più lungo senza
 * troncamento ne lascerebbe la coda); alcuni provider non accettano il modo
 * esplicito e vogliono quello predefinito.
 */
fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean {
    val bytes = text.toByteArray(Charsets.UTF_8)
    return runCatching { openAndWrite(context, uri, "wt", bytes) }
        .recoverCatching { openAndWrite(context, uri, null, bytes) }
        .getOrDefault(false)
}

private fun openAndWrite(context: Context, uri: Uri, mode: String?, bytes: ByteArray): Boolean {
    val output = if (mode == null) {
        context.contentResolver.openOutputStream(uri)
    } else {
        context.contentResolver.openOutputStream(uri, mode)
    }
    return output?.use { stream ->
        stream.write(bytes)
        true
    } ?: false
}

/**
 * Richiede il permesso di lettura persistabile per l'URI SAF importato:
 * senza, dopo un riavvio il ContentResolver negherebbe l'accesso e il
 * repository cadrebbe sul fallback cache. Alcuni provider non lo supportano:
 * il fallimento è accettabile.
 */
fun takePersistableUriPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
