package it.fast4x.opentv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import it.fast4x.opentv.data.Config
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Image

/**
 * Nome visualizzato di un codice paese nella lingua di sistema (mirror di
 * CountryPicker.kt del modulo app). "uk" non è risolto da java.util.Locale
 * (il codice ISO ufficiale del Regno Unito è "gb"): per la visualizzazione
 * viene mappato su GB, mentre verso iptv-org il codice trasmesso resta "uk".
 */
internal fun countryDisplayName(code: String): String {
    val locale = Locale("", if (code.equals("uk", ignoreCase = true)) "GB" else code)
    return locale.displayCountry.ifBlank { code.uppercase() }
}

/**
 * Opzioni del selettore paese (mirror di countryOptions in CountryPicker.kt):
 * un eventuale codice attivo fuori lista è preservato come voce extra;
 * i paesi supportati ([Config.SUPPORTED_COUNTRIES]) sono ordinati per nome
 * visualizzato nella lingua di sistema.
 */
fun countryOptions(sourceCountry: String?): List<String> = buildList {
    val saved = Config.normalizeCountryCode(sourceCountry)
    if (saved != null && saved !in Config.SUPPORTED_COUNTRIES) add(saved)
    addAll(Config.SUPPORTED_COUNTRIES)
}.sortedBy { countryDisplayName(it) }

/** Etichetta di una voce paese: nome localizzato + codice (la bandierina è l'immagine PNG). */
fun countryOptionLabel(code: String): String =
    "${countryDisplayName(code)} (${code.uppercase()})"

/**
 * Bandierine dei paesi caricate dalle risorse dell'app desktop
 * (resources/flags/<codice>.png, immagini PNG e non emoji: su Windows i
 * glifi bandiera non esistono e degraderebbero alle due lettere del codice).
 * Le bitmap sono decodificate una sola volta e messe in cache.
 */
object FlagIcons {
    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    fun load(code: String): ImageBitmap? = cache.getOrPut(code.lowercase()) {
        runCatching {
            val bytes = FlagIcons::class.java.classLoader
                .getResourceAsStream("flags/${code.lowercase()}.png")
                ?.use { stream -> stream.readBytes() }
            if (bytes == null) null
            else Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

    /**
     * Precarica tutte le bandierine in background (una sola volta): al primo
     * menu aperto le bitmap sono già in cache e nessun frame va in stutter.
     */
    fun preloadAll(codes: List<String>) {
        Thread {
            codes.forEach { load(it) }
        }.apply { isDaemon = true; name = "flag-icons-preload" }.start()
    }
}

/**
 * Bandierina PNG di un paese; se l'immagine manca mostra il codice
 * maiuscolo su fondo scuro (stesso ingombro della bandierina).
 */
@Composable
fun CountryFlagIcon(code: String, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val bitmap = remember(code) { FlagIcons.load(code) }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = countryDisplayName(code),
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(3.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(3.dp))
                .background(AppSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = code.uppercase(),
                color = AppTextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Selettore del paese dei canali (playlist curata iptv-org
 * countries/<codice>.m3u): pillola con bandierina PNG e nome del paese
 * attivo, che apre il menu con tutti i paesi supportati (bandierina + nome
 * localizzato, ordine alfabetico) più la voce automatica ([autoLabel] =
 * paese del dispositivo). Il paese attivo è la preferenza dedicata
 * countryCode, la stessa delle Impostazioni (fix19); con un URL personale
 * il menu parte dalla voce automatica.
 */
@Composable
fun CountryDropdown(
    currentCode: String?,
    autoLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // Fix19: il paese attivo è la preferenza dedicata countryCode
    // (normalizzata in lettura dallo store), non più derivato dall'URL
    // playlist; la voce automatica copre sia la modalità automatica sia
    // un URL personale.
    val activeCode = Config.normalizeCountryCode(currentCode)

    var expanded by remember { mutableStateOf(false) }

    // Precaricamento asincrono delle bandierine (una sola volta per processo).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { FlagIcons.preloadAll(Config.SUPPORTED_COUNTRIES) }
    }

    Box(modifier.alpha(if (enabled) 1f else 0.45f)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = AppSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AppSurfaceHigh),
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                if (activeCode != null) {
                    CountryFlagIcon(activeCode)
                    Text(
                        text = countryDisplayName(activeCode),
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    Text(
                        text = autoLabel,
                        color = AppTextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(
                    text = "▾",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            DropdownMenuItem(
                text = { Text(autoLabel, color = AppTextPrimary) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            countryOptions(activeCode).forEach { code ->
                val selected = code == activeCode
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CountryFlagIcon(code, size = 18.dp)
                            Text(
                                text = countryOptionLabel(code),
                                color = if (selected) AppPrimary else AppTextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}
