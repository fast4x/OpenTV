package it.fast4x.opentv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object StreamResolver {

    private val client = OkHttpClient.Builder()
        .followRedirects(false) // Blucchiamo il redirect automatico per intercettare manualmente l'URL finale
        .followSslRedirects(false)
        .build()

    private const val USER_AGENT = "rainet/4.0.5"

    /**
     * Risolve l'URL effettivo. Se rileva il relinker Rai, estrae il flusso finale .m3u8 con il token valido.
     */
    suspend fun resolveStreamUrl(inputUrl: String): String = withContext(Dispatchers.IO) {
        if (!inputUrl.contains("rai.it/relinker")) {
            return@withContext inputUrl
        }

        // 1. Convertiamo mediapolisevent nel dominio standard se si tratta di canali TV lineari
        var sanitizedUrl = inputUrl.replace("mediapolisevent.rai.it", "mediapolis.rai.it")

        // 2. Forziamo il parametro di output nativo HLS mobile se manca
        if (!sanitizedUrl.contains("output=")) {
            sanitizedUrl = if (sanitizedUrl.contains("?")) {
                "$sanitizedUrl&output=16"
            } else {
                "$sanitizedUrl?output=16"
            }
        }

        // Ora esegui la chiamata OkHttp con l'URL pulito
        try {
            val request = Request.Builder()
                .url(sanitizedUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 302 || response.code == 301) {
                    val realUrl = response.header("Location")
                    if (!realUrl.isNullOrEmpty()) return@withContext realUrl
                }
                return@withContext sanitizedUrl
            }
        } catch (e: IOException) {
            return@withContext inputUrl
        }
    }

}
