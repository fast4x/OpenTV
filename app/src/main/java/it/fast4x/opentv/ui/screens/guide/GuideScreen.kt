package it.fast4x.opentv.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.fast4x.opentv.R
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.repository.EpgRepository
import it.fast4x.opentv.data.repository.PlaylistRepository
import it.fast4x.opentv.data.repository.epgKey
import it.fast4x.opentv.data.store.UserPrefsStore
import it.fast4x.opentv.openTvApp
import it.fast4x.opentv.ui.components.ChannelLogo
import it.fast4x.opentv.ui.components.tvFocusHighlight
import it.fast4x.opentv.ui.theme.AppBackground
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppPrimaryDim
import it.fast4x.opentv.ui.theme.AppSurface
import it.fast4x.opentv.ui.theme.AppSurfaceHigh
import it.fast4x.opentv.ui.theme.AppTextPrimary
import it.fast4x.opentv.ui.theme.AppTextSecondary
import it.fast4x.opentv.util.dayKey
import it.fast4x.opentv.util.formatHourRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Guida TV a griglia (feature 8): canali sulle righe, programmi sulla linea
 * del tempo orizzontale. La finestra mostrata parte dall'ora piena corrente
 * e dura [GUIDE_WINDOW_HOURS] ore; il programma in onda è evidenziato con
 * bordo primario e barra di avanzamento. Toccare un programma (o il canale)
 * apre direttamente il player su quel canale.
 */

/** Riga della griglia: un canale con i suoi programmi dentro la finestra. */
data class GuideRowData(
    val channel: Channel,
    val programmes: List<Programme>,
)

/** Stato della schermata guida, pronto per la UI. */
data class GuideUiState(
    val loading: Boolean = true,
    val playlistError: String? = null,
    val epgReady: Boolean = false,
    val rows: List<GuideRowData> = emptyList(),
    /** Inizio della finestra mostrata (ora piena corrente). */
    val windowStart: Long = 0L,
    /** Fine della finestra mostrata. */
    val windowEnd: Long = 0L,
    /** Istante di calcolo (marker "adesso" e avanzamento dei programmi). */
    val now: Long = 0L,
)

class GuideViewModel(
    playlistRepository: PlaylistRepository,
    epgRepository: EpgRepository,
    prefs: UserPrefsStore,
) : ViewModel() {

    private val tick = flow {
        while (true) {
            emit(Unit)
            delay(GUIDE_TICK_MS)
        }
    }

    val state: StateFlow<GuideUiState> = combine(
        playlistRepository.state,
        epgRepository.programmes,
        tick,
        // Canali nascosti della sorgente attiva (feature "nascondi canali"):
        // non compaiono nemmeno nella griglia della guida.
        prefs.hiddenChannelsForActiveScope(),
    ) { playlist, programmes, _, hidden ->
        val now = System.currentTimeMillis()
        val windowStart = floorToHour(now)
        val windowEnd = windowStart + GUIDE_WINDOW_HOURS * HOUR_MS

        val channels = ((playlist as? PlaylistState.Success)?.channels.orEmpty())
            .filter { it.id !in hidden }
        val rows = channels.mapNotNull { channel ->
            // Match sulla chiave base (come nel player): la playlist qualifica
            // le varianti con suffissi ("Rai1.it@SD"), la guida usa "Rai1.it".
            // fix35: guide con id diversi si recuperano per nome.
            val inWindow = (programmes[epgKey(channel.id)]
                ?: epgRepository.aliasKeyFor(channel)?.let { programmes[it] })
                ?.filter { it.stop > windowStart && it.start < windowEnd }
                ?.sortedBy { it.start }
                .orEmpty()
            if (inWindow.isEmpty()) null else GuideRowData(channel, inWindow)
        }.sortedBy { it.channel.name.lowercase() }

        GuideUiState(
            loading = playlist is PlaylistState.Loading,
            playlistError = (playlist as? PlaylistState.Error)?.message,
            epgReady = programmes.isNotEmpty(),
            rows = rows,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(GUIDE_STOP_TIMEOUT_MS),
        initialValue = GuideUiState(),
    )

    private companion object {
        const val GUIDE_STOP_TIMEOUT_MS = 5_000L
    }
}

/** Durata della finestra temporale mostrata dalla griglia (ore). */
const val GUIDE_WINDOW_HOURS = 4

/** Spazio orizzontale per ogni ora di programma. */
val GUIDE_DP_PER_HOUR = 150.dp

/** Larghezza minima di una cella programma (i flash brevi restano leggibili). */
val GUIDE_MIN_CELL_DP = 56.dp

private const val GUIDE_TICK_MS = 30_000L
private const val HOUR_MS = 3_600_000L

private fun floorToHour(timestamp: Long): Long = timestamp / HOUR_MS * HOUR_MS

@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.openTvApp
    val viewModel: GuideViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                GuideViewModel(
                    playlistRepository = app.playlistRepository,
                    epgRepository = app.epgRepository,
                    prefs = app.prefs,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .safeDrawingPadding(),
    ) {
        // Top bar: indietro + titolo + finestra temporale mostrata.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_back),
                    tint = AppTextPrimary,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.guide_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTextPrimary,
                )
                Text(
                    text = windowLabel(state),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = stringResource(R.string.content_desc_guide),
                tint = AppPrimary,
                modifier = Modifier.size(28.dp),
            )
        }

        when {
            state.loading && state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = AppPrimary)
                }
            }

            state.playlistError != null && state.rows.isEmpty() -> {
                GuideMessage(
                    text = stringResource(R.string.error_load_channels) + "\n" +
                        state.playlistError.orEmpty(),
                )
            }

            // Playlist pronta ma nessun programma: la sorgente EPG non ha
            // dati per questi canali (guida assente o id non corrispondenti).
            !state.epgReady || state.rows.isEmpty() -> {
                GuideMessage(text = stringResource(R.string.guide_empty))
            }

            else -> GuideGrid(state = state, onOpenChannel = onOpenChannel)
        }
    }
}

/** Etichetta della finestra temporale ("14:00 – 18:00", col giorno se diverso da oggi). */
private fun windowLabel(state: GuideUiState): String {
    if (state.windowEnd <= state.windowStart) return ""
    val time = SimpleDateFormat("HH:mm", Locale.getDefault())
    val today = dayKey(System.currentTimeMillis())
    val windowDay = dayKey(state.windowStart)
    val dayPrefix = if (today == windowDay) "" else
        SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(state.windowStart)) + " · "
    return dayPrefix + "${time.format(Date(state.windowStart))} – ${time.format(Date(state.windowEnd))}"
}

@Composable
private fun GuideMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = AppTextSecondary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = text,
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * La griglia vera e propria. Lo scroll orizzontale è CONDIVISO fra tutte le
 * righe e il righello orario (un solo [rememberScrollState]): le celle sono
 * Row plain a larghezza deterministica (non LazyRow), quindi lo stato
 * condiviso mantiene l'allineamento perfetto fra righello e programmi; la
 * virtualizzazione verticale resta a carico della LazyColumn delle righe.
 */
@Composable
private fun GuideGrid(
    state: GuideUiState,
    onOpenChannel: (String) -> Unit,
) {
    val horizontalScroll = rememberScrollState()
    val totalWidth = GUIDE_DP_PER_HOUR * GUIDE_WINDOW_HOURS
    val hourFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Righello orario + marker "adesso": prima riga, colonna etichette
        // della stessa larghezza delle righe canale per l'allineamento.
        item(key = "ruler") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(GUIDE_LABEL_DP))
                Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                    Box(modifier = Modifier.width(totalWidth)) {
                        Row {
                            repeat(GUIDE_WINDOW_HOURS) { index ->
                                Box(modifier = Modifier.width(GUIDE_DP_PER_HOUR)) {
                                    Text(
                                        text = hourFormat.format(
                                            Date(state.windowStart + index * HOUR_MS),
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AppTextSecondary,
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                                    )
                                }
                            }
                        }
                        // Marker "adesso": la finestra parte dall'ora piena
                        // corrente, l'istante cade sempre dentro la prima ora.
                        val nowFraction = ((state.now - state.windowStart)
                            .toFloat() / (state.windowEnd - state.windowStart))
                            .coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .padding(start = nowFraction.times(totalWidth))
                                .width(2.dp)
                                .height(18.dp)
                                .background(AppPrimary),
                        )
                    }
                }
            }
        }

        items(state.rows, key = { it.channel.id }) { row ->
            GuideGridRow(
                row = row,
                state = state,
                horizontalScroll = horizontalScroll,
                onOpenChannel = onOpenChannel,
            )
        }
    }
}

/** Larghezza della colonna etichette canale (allineata su righello e righe). */
private val GUIDE_LABEL_DP = 120.dp

@Composable
private fun GuideGridRow(
    row: GuideRowData,
    state: GuideUiState,
    horizontalScroll: ScrollState,
    onOpenChannel: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Colonna canale: logo e nome allineati a sinistra (richiesta utente),
        // sempre visibili fuori dallo scroll.
        Column(
            modifier = Modifier
                .width(GUIDE_LABEL_DP)
                .clickable { onOpenChannel(row.channel.id) }
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            ChannelLogo(
                logoUrl = row.channel.logoUrl,
                channelName = row.channel.name,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.channel.name,
                style = MaterialTheme.typography.labelSmall,
                color = AppTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Timeline del canale: celle proporzionali alla durata del programma,
        // con un coltello alla fine di ogni cella (i buchi restano vuoti).
        Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            var cursor = state.windowStart
            row.programmes.forEach { programme ->
                val visibleStart = maxOf(programme.start, state.windowStart)
                val visibleStop = minOf(programme.stop, state.windowEnd)
                if (visibleStop <= cursor) return@forEach

                val onAir = programme.isOnAir(state.now)
                val durationMs = (visibleStop - maxOf(visibleStart, cursor)).coerceAtLeast(1L)
                val cellWidth = durationPerDp(durationMs)
                val gap = (visibleStart - cursor).coerceAtLeast(0L)

                if (gap > 0) Spacer(modifier = Modifier.width(gapPerDp(gap)))

                ProgrammeCell(
                    programme = programme,
                    onAir = onAir,
                    now = state.now,
                    width = cellWidth,
                    onClick = { onOpenChannel(row.channel.id) },
                )
                cursor = visibleStop
            }
        }
    }
}

/** Converte una durata in millisecondi nella larghezza sulla timeline. */
private fun durationPerDp(durationMs: Long): Dp =
    (durationMs.toFloat() / HOUR_MS * GUIDE_DP_PER_HOUR.value).dp
        .coerceAtLeast(GUIDE_MIN_CELL_DP)

/**
 * Larghezza esatta di un buco fra programmi: SENZA larghezza minima (a
 * differenza delle celle) perché qualunque arrotondamento com'è, va rispettato
 * per mantenere allineate le timeline e il righello orario.
 */
private fun gapPerDp(gapMs: Long): Dp =
    (gapMs.toFloat() / HOUR_MS * GUIDE_DP_PER_HOUR.value).dp

@Composable
private fun ProgrammeCell(
    programme: Programme,
    onAir: Boolean,
    now: Long,
    width: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .width(width)
            .clip(RoundedCornerShape(8.dp))
            .background(if (onAir) AppSurfaceHigh else AppSurface)
            .then(
                if (onAir) {
                    Modifier.border(1.dp, AppPrimary, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .tvFocusHighlight()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = programme.title,
                style = MaterialTheme.typography.labelMedium,
                color = AppTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatHourRange(programme.start, programme.stop),
                style = MaterialTheme.typography.labelSmall,
                color = AppTextSecondary,
                maxLines = 1,
            )
            if (onAir) {
                val progress = programme.progressAt(now) ?: 0f
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppPrimaryDim),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(AppPrimary),
                    )
                }
            }
        }
    }
}
