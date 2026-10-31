package it.fast4x.opentv.data.remote

import it.fast4x.opentv.data.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Sonda HTTP usata quando entrambi i motori di riproduzione hanno fallito:
 * interroga il sorgente con le stesse intestazioni che userebbe il player e
 * restituisce i fatti (codice HTTP, URL finale dopo i redirect, Content-Type)
 * su cui costruire un messaggio d'errore preciso per l'utente.
 *
 * Serve a distinguere i casi che NESSUN player può risolvere:
 * - 403/451: blocco geografico o accesso riservato;
 * - 404/410: link morto nella playlist;
 * - redirect verso un segnaposto tipo `video_no_available.mp4`
 *   (relinker Rai quando il canale non è disponibile);
 * - 5xx: server in errore temporaneo;
 * - timeout/errore di rete: canale offline o rete che blocca il flusso.
 */
object StreamProbe {

    /** Esito della sonda: [httpCode] null significa errore di rete/timeout. */
    data class Result(
        val httpCode: Int?,
        val finalUrl: String?,
        val contentType: String?,
        val networkError: Boolean,
    )

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Esegue una richiesta leggera del manifest: non scarica lo stream,
     * registra solo esito, URL finale (dopo i redirect) e Content-Type.
     */
    suspend fun probe(
        url: String,
        userAgent: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Result = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent ?: Config.DEFAULT_STREAM_USER_AGENT)
            headers.forEach { (name, value) -> builder.header(name, value) }

            client.newCall(builder.build()).execute().use { response ->
                Result(
                    httpCode = response.code,
                    finalUrl = response.request.url.toString(),
                    contentType = response.header("Content-Type"),
                    networkError = false,
                )
            }
        } catch (_: Exception) {
            // DNS fallito, timeout, connessione rifiutata: tutto converge su networkError.
            Result(httpCode = null, finalUrl = null, contentType = null, networkError = true)
        }
    }

    /**
     * true se l'URL finale è un segnaposto "video non disponibile"
     * (es. i relinker Rai ridirigono a `video_no_available.mp4`).
     */
    fun isPlaceholder(result: Result): Boolean {
        val final = result.finalUrl?.lowercase() ?: return false
        return "no_available" in final ||
            "not_available" in final ||
            "notavailable" in final ||
            "unavailable" in final
    }
}
