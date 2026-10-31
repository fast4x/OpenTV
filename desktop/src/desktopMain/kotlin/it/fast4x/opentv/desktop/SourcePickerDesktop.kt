package it.fast4x.opentv.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.fast4x.opentv.data.model.SourceProfile

/** Etichetta della voce automatica del menu sorgente (gemella Android). */
internal const val SOURCE_AUTO_LABEL = "IPTV-ORG (automatica)"

/**
 * Selettore della sorgente attiva (feature "profilazione della lista"):
 * pillola col nome della sorgente attiva (profilo utente o automatica
 * iptv-org) che apre il menu con la voce automatica, i profili salvati e la
 * voce di gestione che porta alle Impostazioni (aggiungere, modificare e
 * rimuovere i profili). La selezione è immediata: il repository ricarica
 * lista e guida dalla nuova sorgente (DesktopHomeState.onProfileChange).
 * Stile gemello del CountryDropdown (pillolaSurface + bordo sottile).
 */
@Composable
fun SourceDropdown(
    profiles: List<SourceProfile>,
    activeProfileId: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = profiles.firstOrNull { it.id == activeProfileId }

    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = AppSurface,
            border = BorderStroke(1.dp, AppSurfaceHigh),
            onClick = { expanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = active?.name ?: SOURCE_AUTO_LABEL,
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
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
                text = {
                    Text(
                        text = SOURCE_AUTO_LABEL,
                        color = if (active == null) AppPrimary else AppTextPrimary,
                    )
                },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = profile.name,
                            color = if (profile.id == activeProfileId) AppPrimary else AppTextPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Gestisci profili nelle Impostazioni…", color = AppTextPrimary) },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }
}
