package it.fast4x.opentv.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import it.fast4x.opentv.data.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap

/**
 * Piccolo loader di loghi per il desktop: scarica l'immagine via OkHttp e la
 * decodifica in un [ImageBitmap] (skiko), con cache in memoria per URL.
 * Su Android questo ruolo è di Coil; su desktop ci bastano ~40 righe.
 */
object DesktopLogoLoader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .readTimeout(java.time.Duration.ofSeconds(15))
        .build()

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache[url]?.let { return@withContext it }
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Config.DEFAULT_STREAM_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body.bytes()
                if (bytes.isEmpty()) return@withContext null
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }.getOrNull()?.also { cache[url] = it }
    }
}

/** Schermate dell'app desktop. */
sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Guide : Screen
    data object Player : Screen
}

/**
 * Stato di navigazione condiviso: tengo l'id del canale in riproduzione così
 * il player può risolverlo dalla playlist del core.
 */
class DesktopNavigator {

    var current by mutableStateOf<Screen>(Screen.Home)

    var playerChannelId by mutableStateOf<String?>(null)

    /**
     * Mini-player (feature 11): cablato a livello app; se presente, ogni
     * apertura del player pieno chiude la finestretta (una sola riproduzione
     * attiva: mai due audio sovrapposti). Lo sfogliare guida/Home lascia il
     * mini aperto, che è il suo scopo.
     */
    var miniPlayer: DesktopMiniPlayerController? = null

    fun openPlayer(channelId: String) {
        // Il player pieno riprende il comando: il mini-player si chiude.
        miniPlayer?.dismiss()
        playerChannelId = channelId
        current = Screen.Player
    }

    fun openSettings() {
        current = Screen.Settings
    }

    fun openGuide() {
        current = Screen.Guide
    }

    fun goHome() {
        current = Screen.Home
    }

    /** Torna alla Home chiudendo il player. */
    fun backFromPlayer() {
        current = Screen.Home
    }
}
