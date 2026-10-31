package it.fast4x.opentv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.fast4x.opentv.ui.theme.AppSurfaceHigh
import it.fast4x.opentv.ui.theme.AppTextSecondary

/**
 * Logo del canale con fallback elegante: se l'URL manca o non carica,
 * mostra un'iniziale su fondo neutro.
 */
@Composable
fun ChannelLogo(
    logoUrl: String?,
    channelName: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }

    if (logoUrl.isNullOrEmpty() || failed) {
        Box(
            modifier = modifier.background(AppSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channelName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = AppTextSecondary,
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            onError = { failed = true },
        )
    }
}
