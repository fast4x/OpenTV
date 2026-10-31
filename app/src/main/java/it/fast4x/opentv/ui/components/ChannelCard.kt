package it.fast4x.opentv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.opentv.R
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.UNCATEGORIZED_GROUP
import it.fast4x.opentv.data.model.channelGroupKeys
import it.fast4x.opentv.ui.theme.AppAccent
import it.fast4x.opentv.ui.theme.AppFavorite
import it.fast4x.opentv.ui.theme.AppGeoBlocked
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppSurface
import it.fast4x.opentv.util.formatHourRange

private val CardShape = RoundedCornerShape(14.dp)

/**
 * Card di un canale, stile "Dark TV":
 * - scala + bordo luminoso quando riceve il focus (D-pad su Android TV);
 * - tap = apri il canale, tap prolungato = toggle preferito;
 * - mostra il programma in onda quando è disponibile l'EPG;
 * - il badge genere è cliccabile (tap = filtra i canali dello stesso genere)
 *   solo quando viene passato [onGroupClick]: su Android TV resta decorativo
 *   così il D-pad non trova target di focus extra dentro la card.
 * - l'icona "occhio barrato" in alto a sinistra richiede di nascondere il
 *   canale (feature "nascondi canali") solo quando viene passato [onHide]:
 *   su Android TV non esiste (nessun target di focus in più); la conferma
 *   con dialog è sempre responsabilità del chiamante, mai nascondere in
 *   silenzio.
 *
 * @param useCellWidth se true la card riempie la cella della griglia (modalità ricerca).
 * @param onGroupClick callback opzionale invocata al tap sul badge genere; null = badge non interattivo.
 * @param onHide callback opzionale invocata al tap sull'icona nascondi; null = icona non mostrata.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    epgNow: Programme?,
    isTv: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGroupClick: ((String) -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    useCellWidth: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        label = "cardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) AppPrimary else Color.Transparent,
        label = "cardBorder",
    )
    val cardWidth: Dp = if (isTv) 196.dp else 168.dp

    Column(
        modifier = modifier
            .then(
                if (useCellWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.width(cardWidth)
                },
            )
            .scale(scale)
            .shadow(
                elevation = if (focused) 16.dp else 0.dp,
                shape = CardShape,
                ambientColor = AppPrimary,
                spotColor = AppPrimary,
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(CardShape)
            .background(AppSurface)
            .border(2.dp, borderColor, CardShape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onToggleFavorite,
            )
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            ChannelLogo(
                logoUrl = channel.logoUrl,
                channelName = channel.name,
                modifier = Modifier.fillMaxSize(),
            )

            // Azione "nascondi canale" (feature "nascondi canali"): icona
            // discreta in alto a sinistra, presente SOLO dove c'è il tocco
            // (su Android TV non esiste, stessa scelta del badge genere). Il
            // tap NON nasconde: il chiamante mostra sempre il dialog di
            // conferma prima di eseguire.
            if (onHide != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClickLabel = stringResource(R.string.hide_channel_action)) { onHide() }
                        .padding(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Indicatore preferito (toggle con tap prolungato sulla card).
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (isFavorite) AppFavorite else Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp),
            )

            // Badge "GEO": la playlist dichiara il canale bloccato in alcune
            // zone ([Geo-blocked]). Aiuta a evitare canali che non partiranno.
            if (channel.geoBlocked) {
                Text(
                    text = "GEO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1B1B1F),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(AppGeoBlocked, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }

            // Badge genere: primo gruppo dichiarato dalla playlist
            // (group-title, senza le categorie multiple dopo ";").
            val groupBadge = channelGroupKeys(channel)
                .first()
                .takeIf { it != UNCATEGORIZED_GROUP }
            if (groupBadge != null) {
                // Su TV il badge resta decorativo (nessun focus D-pad in più);
                // su telefono un tap filtra i canali dello stesso genere.
                val badgeTouch = if (onGroupClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            onClickLabel = stringResource(R.string.group_badge_filter),
                            onClick = { onGroupClick(groupBadge) },
                        )
                } else {
                    Modifier
                }
                Text(
                    text = groupBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1B1B1F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .then(badgeTouch)
                        .padding(6.dp)
                        .widthIn(max = 92.dp)
                        .background(AppAccent, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        epgNow?.let { programme ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatHourRange(programme.start, programme.stop)} · ${programme.title}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
