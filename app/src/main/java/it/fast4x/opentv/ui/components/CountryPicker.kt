package it.fast4x.opentv.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import it.fast4x.opentv.data.Config
import java.util.Locale

/**
 * Nome visualizzato di un codice paese nella lingua di sistema. "uk" non è
 * risolto da java.util.Locale (il codice ISO ufficiale del Regno Unito è
 * "gb"): per la visualizzazione viene mappato su GB, mentre verso iptv-org
 * il codice trasmesso resta "uk".
 */
internal fun countryDisplayName(code: String): String {
    val locale = Locale("", if (code.equals("uk", ignoreCase = true)) "GB" else code)
    return locale.displayCountry.ifBlank { code.uppercase() }
}

/**
 * Opzioni del selettore paese: un eventuale codice salvato fuori lista
 * (inserito manualmente in versioni precedenti) è preservato come voce
 * extra; i paesi supportati ([Config.SUPPORTED_COUNTRIES]) sono ordinati
 * per nome visualizzato nella lingua di sistema.
 */
fun countryOptions(sourceCountry: String?): List<String> = buildList {
    val saved = Config.normalizeCountryCode(sourceCountry)
    if (saved != null && saved !in Config.SUPPORTED_COUNTRIES) add(saved)
    addAll(Config.SUPPORTED_COUNTRIES)
}.sortedBy { countryDisplayName(it) }

/** Etichetta composta di una voce paese: bandierina + nome + codice. */
fun countryOptionLabel(code: String): String = listOfNotNull(
    Config.countryFlagEmoji(code).ifEmpty { null },
    countryDisplayName(code),
    "(${code.uppercase()})",
).joinToString(" ")

/**
 * Voci del menu del selettore paese (da chiamare dentro un DropdownMenu):
 * la prima voce riporta la modalità automatica (paese del dispositivo),
 * le altre elencano i paesi con bandierina emoji e nome localizzato.
 * [autoLabel] è già formattato dalla schermata chiamante perché ogni
 * piattaforma usa le proprie stringhe.
 *
 * Con [focusFirstItemOnOpen] (su Android TV) il focus viene spostato
 * esplicitamente sulla prima voce all'apertura del menu: il popup di
 * material3 lo passa di norma da solo, ma su alcuni box TV con versioni
 * Android vecchie non arriva e il D-pad resta "fuori" dal menu; con il
 * focus richiesto a mano il comportamento è deterministico su ogni device.
 */
@Composable
fun CountryMenuItems(
    autoLabel: String,
    options: List<String>,
    onSelect: (String?) -> Unit,
    focusFirstItemOnOpen: Boolean = false,
) {
    val firstItemFocus = remember { FocusRequester() }
    if (focusFirstItemOnOpen) {
        LaunchedEffect(Unit) {
            // Un frame di attesa: al primo fotogramma la voce potrebbe non
            // essere ancora attaccata all'albero, requestFocus è protetto.
            withFrameNanos { }
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    DropdownMenuItem(
        text = { Text(autoLabel) },
        onClick = { onSelect(null) },
        modifier = if (focusFirstItemOnOpen) Modifier.focusRequester(firstItemFocus) else Modifier,
    )
    options.forEach { code ->
        DropdownMenuItem(
            text = { Text(countryOptionLabel(code)) },
            onClick = { onSelect(code) },
        )
    }
}
