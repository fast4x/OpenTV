package it.fast4x.opentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.fast4x.opentv.ui.theme.AppAccent
import it.fast4x.opentv.ui.theme.AppError
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppSurface

/**
 * Anello di focus per l'uso con telecomando su Android TV: senza un
 * riscontro grafico il D-pad non indica quale controllo è selezionato.
 * Da applicare ai pulsanti privi di stile focus proprio (IconButton e simili).
 */
@Composable
fun Modifier.tvFocusHighlight(): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = 2.dp,
            color = if (focused) AppPrimary else Color.Transparent,
            shape = CircleShape,
        )
}

/** Sezione di intestazione per le righe orizzontali di canali. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Piccolo indicatore colorato a sinistra del titolo.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .height(18.dp)
                .background(AppAccent, androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                .padding(horizontal = 3.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Spinner centrale durante il primo caricamento della playlist. */
@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Messaggio di errore con pulsante di ripetizione. */
@Composable
fun ErrorState(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = AppError,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(text = retryLabel)
        }
    }
}

/** Messaggio informativo centrale (lista vuota, nessun risultato). */
@Composable
fun EmptyMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    )
}

/** Avviso discreto (es. "offline: mostro la cache"). */
@Composable
fun InfoBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = AppError,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** Chip arrotondato riutilizzabile per piccole azioni. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(text = text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(text = text, color = Color.White)
        }
    }
}
