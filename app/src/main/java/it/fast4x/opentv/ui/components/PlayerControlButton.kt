package it.fast4x.opentv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.opentv.ui.theme.AppPrimary

/**
 * Bottone circolare del player, ottimizzato per la navigazione D-pad:
 * evidenzia focus con scala + colore, come i controlli Leanback.
 */
@Composable
fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val size: Dp = if (big) 72.dp else 56.dp

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1f,
        label = "controlScale",
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .size(size)
            .background(
                if (focused) AppPrimary.copy(alpha = 0.45f) else Color(0x4DFFFFFF),
                CircleShape,
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) AppPrimary else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (big) 40.dp else 26.dp),
        )
    }
}
