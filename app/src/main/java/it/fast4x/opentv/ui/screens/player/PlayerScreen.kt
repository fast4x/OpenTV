package it.fast4x.opentv.ui.screens.player

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.R
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.ProgrammeReminder
import it.fast4x.opentv.data.repository.CatchupDay
import it.fast4x.opentv.data.repository.CatchupProgramme
import it.fast4x.opentv.data.repository.CatchupSession
import it.fast4x.opentv.data.repository.La7Catchup
import it.fast4x.opentv.openTvApp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import it.fast4x.opentv.ui.components.ChannelLogo
import it.fast4x.opentv.ui.components.PlayerControlButton
import it.fast4x.opentv.ui.components.tvFocusHighlight
import it.fast4x.opentv.ui.theme.AppError
import it.fast4x.opentv.ui.theme.AppFavorite
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppSurfaceHigh
import it.fast4x.opentv.ui.theme.AppTextPrimary
import it.fast4x.opentv.ui.theme.AppTextSecondary
import it.fast4x.opentv.util.findActivity
import it.fast4x.opentv.util.dayKey
import it.fast4x.opentv.util.formatHourRange
import it.fast4x.opentv.util.isTvDevice
import kotlinx.coroutines.delay
import kotlin.math.ceil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

/**
 * Player a schermo intero:
 * - superficie video con Media3/ExoPlayer (HLS, TS, DASH) e riprove
 *   progressivo + diagnosi del sorgente in caso di errore;
 * - overlay controlli con auto-nascondimento: tap su telefono, qualsiasi
 *   tasto del telecomando su TV (CH+/CH- zappano, tasti media play/pausa);
 * - precedente/successivo senza tornare alla Home;
 * - "in onda adesso / a seguire" quando è configurata la guida TV;
 * - su telefono: landscape forzato + modalità immersiva; su TV: focus D-pad.
 */
@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun PlayerScreen(
    channelId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = remember { context.isTvDevice() }
    val app = context.openTvApp

    val viewModel: PlayerViewModel = viewModel(key = "player_$channelId") {
        PlayerViewModel(
            channelId = channelId,
            appContext = context.applicationContext,
            playlistRepository = app.playlistRepository,
            prefs = app.prefs,
            epgRepository = app.epgRepository,
            catchupRepository = app.catchupRepository,
        )
    }

    val channel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val epg by viewModel.epg.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val sleepTimerEndsAt by viewModel.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    // Sottotitoli (feature "sottotitoli"): stesso schema del menu audio.
    val subtitleTracks by viewModel.subtitleTracks.collectAsStateWithLifecycle()
    // Promemoria programmi (feature "reminder"): chiavi armate per i
    // campanelli del foglio "Programmazione".
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()

    // Rivedi (catch-up La7 Play): sessione attiva, giorni caricati, stato di
    // caricamento ed errore; il pannello si apre con lo stesso schema degli
    // altri fogli e carica la guida alla prima apertura.
    val catchupSession by viewModel.catchupSession.collectAsStateWithLifecycle()
    val catchupDays by viewModel.catchupDays.collectAsStateWithLifecycle()
    val catchupLoading by viewModel.catchupLoading.collectAsStateWithLifecycle()
    val catchupError by viewModel.catchupError.collectAsStateWithLifecycle()
    var showCatchup by remember { mutableStateOf(false) }

    // Feature 9: il permesso notifiche (Android 13+) serve alla notifica di
    // riproduzione; negarlo non blocca nulla — la sessione media continua a
    // gestire lockscreen e tasti degli auricolari, sparisce solo la notifica.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showSchedule by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showAudioTracks by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }

    //
    // Proporzioni video (Fit → Zoom → Riempi): rememberSaveable così il ciclo
    // sopravvive a rotazione/ricreazione dell'activity. I canali 4:3 riempiono
    // lo schermo con Zoom, i flussi anamorfici si correggono con Riempi; il
    // ciclo parte sempre da Fit (niente deformazione a sorpresa).
    //
    var resizeMode by rememberSaveable {
        mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    }
    // Feedback breve del modo attivo: etichetta transitoria in alto a destra.
    var resizeFeedbackTick by remember { mutableIntStateOf(0) }

    fun showControls() {
        controlsVisible = true
        interactionTick++
    }

    fun cycleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        resizeFeedbackTick++
        showControls()
    }

    val isError = playback is PlaybackStatus.Error

    // Auto-nascondimento dei controlli dopo qualche secondo dall'ultima interazione.
    LaunchedEffect(interactionTick, controlsVisible) {
        if (controlsVisible) {
            delay(Config.CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Etichetta transitoria del modo proporzioni: scompare dopo poco dal
    // cambio, anche se i controlli restano aperti.
    LaunchedEffect(resizeFeedbackTick) {
        if (resizeFeedbackTick > 0) {
            delay(1_500L)
            resizeFeedbackTick = 0
        }
    }

    // Back: con i controlli visibili prima li nasconde, alla seconda pressione esce.
    BackHandler(enabled = controlsVisible && !isError) {
        controlsVisible = false
    }

    // Focus su TV: con i controlli visibili parte da play/pausa; quando sono
    // nascosti (o in errore) lo prende la superficie video — senza un nodo
    // focalizzabile i tasti del telecomando non raggiungono la UI.
    val playFocusRequester = remember { FocusRequester() }
    val surfaceFocusRequester = remember { FocusRequester() }
    LaunchedEffect(controlsVisible, isError, isTv) {
        when {
            controlsVisible && isTv && !isError ->
                runCatching { playFocusRequester.requestFocus() }
            else ->
                runCatching { surfaceFocusRequester.requestFocus() }
        }
    }

    // Telecomando: qualunque tasto (OK, D-pad, tasti media...) fa comparire i
    // controlli; CH+/CH- (e PageUp/PageDown) zappano il canale; i tasti media
    // governano play/pausa. A controlli visibili il D-pad scorre fra i pulsanti
    // e ogni pressione rinvia l'auto-nascondimento.
    val onPreviewKey: (KeyEvent) -> Boolean = { event ->
        when (event.key) {
            Key.Back, Key.Escape -> false

            Key.ChannelUp, Key.PageUp -> {
                if (event.type == KeyEventType.KeyUp) {
                    showControls()
                    viewModel.switchChannel(+1)
                }
                true
            }

            Key.ChannelDown, Key.PageDown -> {
                if (event.type == KeyEventType.KeyUp) {
                    showControls()
                    viewModel.switchChannel(-1)
                }
                true
            }

            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                if (event.type == KeyEventType.KeyUp) {
                    showControls()
                    viewModel.togglePlayPause()
                }
                true
            }

            else -> {
                val errorNow = playback is PlaybackStatus.Error
                when {
                    errorNow -> false
                    !controlsVisible -> {
                        // Il tasto serve solo a far comparire i controlli.
                        if (event.type == KeyEventType.KeyDown) showControls()
                        true
                    }
                    else -> {
                        // Visibili: ogni pressione rinvia l'auto-nascondimento,
                        // il ritorno false lascia scorrere il D-pad.
                        if (event.type == KeyEventType.KeyDown) showControls()
                        false
                    }
                }
            }
        }
    }

    PhoneTheaterMode(isTv = isTv)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(onPreviewKey)
            .focusRequester(surfaceFocusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { showControls() }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                }
            },
            update = { view ->
                if (view.player !== viewModel.player) view.player = viewModel.player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Caricamento o riconnessione automatica (feature 7): in entrambi i
        // casi l'immagine resta a schermo (PlayerView con shutter nero) con
        // l'indicatore al centro; solo Error la copre con l'overlay.
        val reconnecting = playback as? PlaybackStatus.Reconnecting
        if (playback is PlaybackStatus.Buffering || reconnecting != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when {
                            reconnecting != null -> stringResource(
                                R.string.player_reconnecting,
                                reconnecting.attempt,
                                reconnecting.maxAttempts,
                            )

                            else -> stringResource(R.string.player_buffering)
                        },
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        (playback as? PlaybackStatus.Error)?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9070A10)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = AppError,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error.message,
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.retry))
                        }
                        OutlinedButton(onClick = onBack) {
                            Text(stringResource(R.string.content_desc_back))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && playback !is PlaybackStatus.Error,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerTopBar(
                    channel = channel,
                    isFavorite = channel?.id?.let { it in favorites } ?: false,
                    onBack = onBack,
                    onCycleResize = ::cycleResizeMode,
                    audioAvailable = audioTracks.size > 1,
                    onOpenAudioTracks = {
                        showControls()
                        showAudioTracks = true
                    },
                    subtitlesAvailable = subtitleTracks.isNotEmpty(),
                    subtitleActive = subtitleTracks.any { it.selected },
                    onOpenSubtitles = {
                        showControls()
                        showSubtitles = true
                    },
                    sleepActive = sleepTimerEndsAt != null,
                    onOpenSleepTimer = {
                        showControls()
                        showSleepTimer = true
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                )
                Spacer(Modifier.weight(1f))
                PlayerBottomControls(
                    epg = epg,
                    isPlaying = playback is PlaybackStatus.Playing,
                    scheduleAvailable = schedule.isNotEmpty(),
                    onOpenSchedule = {
                        showControls()
                        showSchedule = true
                    },
                    catchupSupported = channel?.let { La7Catchup.slugFor(it) } != null,
                    catchupActive = catchupSession != null,
                    catchupSession = catchupSession,
                    onOpenCatchup = {
                        showControls()
                        viewModel.loadCatchupSchedule()
                        showCatchup = true
                    },
                    onReturnToLive = {
                        showControls()
                        viewModel.returnToLive()
                    },
                    onPlayPause = {
                        showControls()
                        viewModel.togglePlayPause()
                    },
                    onPrev = {
                        showControls()
                        viewModel.switchChannel(-1)
                    },
                    onNext = {
                        showControls()
                        viewModel.switchChannel(+1)
                    },
                    playFocusRequester = playFocusRequester,
                )
            }
        }

        // Programmazione del canale: foglio modale con i programmi della
        // finestra guida (-1h → +24h), indipendente dai controlli che si
        // auto-nascondono (resta aperto finché l'utente non lo chiude).
        if (showSchedule) {
            ModalBottomSheet(onDismissRequest = { showSchedule = false }) {
                EpgScheduleSheet(
                    schedule = schedule,
                    reminderKeys = remember(reminders) { reminders.map { it.key() }.toSet() },
                    onToggleReminder = viewModel::toggleReminder,
                )
            }
        }

        // Rivedi (catch-up La7 Play): foglio modale con i giorni (oggi + i
        // 6 precedenti) e le repliche selezionabili; resta aperto come gli
        // altri fogli e non dipende dall'auto-nascondimento dei controlli.
        if (showCatchup) {
            ModalBottomSheet(onDismissRequest = { showCatchup = false }) {
                CatchupSheet(
                    days = catchupDays,
                    loading = catchupLoading,
                    error = catchupError,
                    session = catchupSession,
                    onSelect = { programme ->
                        viewModel.playCatchup(programme)
                        showCatchup = false
                    },
                    onRetry = { viewModel.loadCatchupSchedule(force = true) },
                )
            }
        }

        // Timer di spegnimento: foglio modale con le durate (15/30/60 min)
        // e lo stato attivo; alla scadenza il player va in pausa.
        if (showSleepTimer) {
            ModalBottomSheet(onDismissRequest = { showSleepTimer = false }) {
                SleepTimerSheet(
                    activeMinutes = viewModel.sleepTimerMinutes.collectAsStateWithLifecycle().value,
                    endsAt = sleepTimerEndsAt,
                    onSelect = { minutes ->
                        viewModel.setSleepTimer(minutes)
                        showSleepTimer = false
                    },
                )
            }
        }

        // Tracce audio: foglio modale con le lingue disponibili del canale;
        // la selezione applica l'override su ExoPlayer e chiude (il feedback
        // è l'ascolto stesso). Il pulsante che lo apre compare solo sui
        // canali con più di una traccia riproducibile.
        if (showAudioTracks) {
            ModalBottomSheet(onDismissRequest = { showAudioTracks = false }) {
                AudioTracksSheet(
                    options = audioTracks,
                    onSelect = { index ->
                        viewModel.selectAudioTrack(index)
                        showAudioTracks = false
                    },
                )
            }
        }

        // Sottotitoli (feature "sottotitoli"): gemello del foglio tracce
        // audio, con la voce "Disattivati" al posto di "Automatica".
        if (showSubtitles) {
            ModalBottomSheet(onDismissRequest = { showSubtitles = false }) {
                SubtitlesSheet(
                    options = subtitleTracks,
                    onSelect = { index ->
                        viewModel.selectSubtitleTrack(index)
                        showSubtitles = false
                    },
                )
            }
        }

        // Etichetta transitoria del modo proporzioni: sopra il gradiente,
        // ben leggibile anche con i controlli nascosti.
        androidx.compose.animation.AnimatedVisibility(
            visible = resizeFeedbackTick > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 16.dp),
        ) {
            Surface(
                color = Color(0xCC10131A),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(
                        when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.player_resize_zoom
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.player_resize_fill
                            else -> R.string.player_resize_fit
                        },
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Su telefono: landscape forzato + modalità immersiva (barre di sistema nascoste).
 * Su TV non fa nulla: la UI resta in 16:9 nativa.
 */
@Composable
private fun PhoneTheaterMode(isTv: Boolean) {
    if (isTv) return
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PlayerTopBar(
    channel: Channel?,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onCycleResize: () -> Unit,
    audioAvailable: Boolean,
    onOpenAudioTracks: () -> Unit,
    subtitlesAvailable: Boolean,
    subtitleActive: Boolean,
    onOpenSubtitles: () -> Unit,
    sleepActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xB3000000),
                    1f to Color.Transparent,
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocusHighlight()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_desc_back),
                tint = Color.White,
            )
        }

        ChannelLogo(
            logoUrl = channel?.logoUrl,
            channelName = channel?.name.orEmpty(),
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurfaceHigh),
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel?.name.orEmpty(),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel?.group?.takeIf { it.isNotBlank() }?.let { group ->
                Text(
                    text = group,
                    color = Color(0xFFAEB9C9),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Proporzioni video: cicla Adatta → Zoom → Riempi (utile per i canali
        // 4:3 e i flussi anamorfici); l'etichetta del modo attivo appare a
        // scomparsa accanto alla barra.
        IconButton(onClick = onCycleResize, modifier = Modifier.tvFocusHighlight()) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = stringResource(R.string.player_resize),
                tint = Color.White,
            )
        }

        // Tracce audio: il pulsante compare solo sui canali multilingua (più
        // di una traccia riproducibile), dove la scelta ha davvero senso.
        if (audioAvailable) {
            IconButton(onClick = onOpenAudioTracks, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.Outlined.Audiotrack,
                    contentDescription = stringResource(R.string.player_audio_tracks),
                    tint = Color.White,
                )
            }
        }

        // Sottotitoli (feature "sottotitoli"): gemello del pulsante tracce
        // audio; compare solo se il flusso annuncia tracce di testo, l'icona
        // è piena e colorata mentre una traccia è selezionata.
        if (subtitlesAvailable) {
            IconButton(onClick = onOpenSubtitles, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = if (subtitleActive) Icons.Filled.ClosedCaption
                        else Icons.Outlined.ClosedCaption,
                    contentDescription = stringResource(R.string.player_subtitles_tracks),
                    tint = if (subtitleActive) AppPrimary else Color.White,
                )
            }
        }

        // Timer di spegnimento: icona piena e colorata mentre il timer è
        // attivo, contorno nelle altre condizioni.
        IconButton(onClick = onOpenSleepTimer, modifier = Modifier.tvFocusHighlight()) {
            Icon(
                imageVector = if (sleepActive) Icons.Filled.Timer else Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.player_sleep_timer),
                tint = if (sleepActive) AppPrimary else Color.White,
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            enabled = channel != null,
            modifier = Modifier.tvFocusHighlight(),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = stringResource(R.string.content_desc_favorite),
                tint = if (isFavorite) AppFavorite else Color.White,
            )
        }
    }
}

/**
 * Foglio del timer di spegnimento: durate rapide (15/30/60 min) con la
 * corrente evidenziata, disattivazione e orario previsto di stop.
 */
@Composable
private fun SleepTimerSheet(
    activeMinutes: Int?,
    endsAt: Long?,
    onSelect: (Int?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.player_sleep_timer),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        endsAt?.let { timestamp ->
            Text(
                text = stringResource(
                    R.string.sleep_until,
                    SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(Date(timestamp)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = AppPrimary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                // FilledTonal sulla durata selezionata, contorno sulle altre:
                // lo stato resta leggibile a colpo d'occhio anche su TV.
                if (minutes == activeMinutes) {
                    FilledTonalButton(onClick = { onSelect(minutes) }) {
                        Text(stringResource(R.string.sleep_minutes, minutes))
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(minutes) }) {
                        Text(stringResource(R.string.sleep_minutes, minutes))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSelect(null) }) {
                Text(stringResource(R.string.sleep_off))
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Nome leggibile di una lingua dal codice ISO del flusso ("ita" -> "Italiano").
 * Restituisce null quando il codice non è risolvibile (es. codici riservati
 * "qaa-qtz" usati da alcune playlist per le tracce di audiodescrizione):
 * in quel caso la UI mostra l'etichetta grezza o il contatore di traccia.
 */
private fun languageDisplayName(code: String): String? {
    if (code.isBlank()) return null
    return runCatching {
        val display = Locale(code).displayLanguage
        display.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
    }
        .getOrNull()
        ?.replaceFirstChar { it.uppercase(Locale.getDefault()) }
}

/**
 * Foglio di selezione della traccia audio: la voce "Automatica" (scelta
 * gestita da ExoPlayer, evidenziata quando nessuna traccia è selezionata
 * esplicitamente) più una riga per traccia con etichetta, lingua e dettagli
 * (audiodescrizione, numero di canali).
 */
@Composable
private fun AudioTracksSheet(
    options: List<AudioTrackOption>,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.player_audio_tracks),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        // Difensivo: il pulsante che apre il foglio compare solo con più
        // tracce disponibili, ma un flusso che ritira le tracce a metà
        // (zapping durante la scelta) lascerebbe il foglio aperto su vuoto.
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.player_audio_none),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        } else {
            AudioTrackRow(
                title = stringResource(R.string.player_audio_auto),
                detail = null,
                selected = options.none { it.selected },
                onClick = { onSelect(-1) },
            )
            options.forEach { option ->
                val language = languageDisplayName(option.language.orEmpty())
                val title = option.label
                    ?: language
                    ?: stringResource(R.string.player_audio_track_n, option.index + 1)
                val detail = buildList {
                    // La lingua nel dettaglio solo se l'etichetta non la dice già.
                    if (option.label != null && language != null &&
                        !option.label.equals(language, ignoreCase = true)
                    ) {
                        add(language)
                    }
                    if (option.described) add(stringResource(R.string.player_audio_ad))
                    option.channelCount?.let { add(stringResource(R.string.player_audio_channels, it)) }
                }.joinToString(" · ")
                AudioTrackRow(
                    title = title,
                    detail = detail.takeIf { it.isNotBlank() },
                    selected = option.selected,
                    onClick = { onSelect(option.index) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Riga del foglio tracce: radio di selezione + titolo e dettaglio; la traccia
 * in riproduzione è evidenziata col colore primario. Le righe sono cliccabili
 * (e quindi raggiungibili col D-pad su TV).
 */
@Composable
private fun AudioTrackRow(
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) AppPrimary else AppTextPrimary,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextSecondary,
                )
            }
        }
    }
}

/**
 * Foglio di selezione dei sottotitoli (feature "sottotitoli"): gemello del
 * foglio tracce audio, con la voce "Disattivati" al posto di "Automatica".
 * Le righe riusano [AudioTrackRow] (stesso schema UI richiesto).
 */
@Composable
private fun SubtitlesSheet(
    options: List<TextTrackOption>,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.player_subtitles_tracks),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        // Difensivo (come nel foglio audio): il pulsante che apre il foglio
        // compare solo con tracce disponibili, ma un flusso che ritira le
        // tracce a metà lascerebbe il foglio aperto su vuoto.
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.player_subtitles_none),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        } else {
            // "Disattivati": evidenziato quando nessuna traccia di testo è
            // in riproduzione (anche perché il flusso non ne ha selezionabili).
            AudioTrackRow(
                title = stringResource(R.string.player_subtitles_off),
                detail = null,
                selected = options.none { it.selected },
                onClick = { onSelect(-1) },
            )
            options.forEach { option ->
                val language = languageDisplayName(option.language.orEmpty())
                val title = option.label
                    ?: language
                    ?: stringResource(R.string.player_audio_track_n, option.index + 1)
                // La lingua nel dettaglio solo se l'etichetta non la dice già.
                val detail = language?.takeIf {
                    option.label == null || !option.label.equals(it, ignoreCase = true)
                }
                AudioTrackRow(
                    title = title,
                    detail = detail,
                    selected = option.selected,
                    onClick = { onSelect(option.index) },
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PlayerBottomControls(
    epg: Pair<Programme?, Programme?>,
    isPlaying: Boolean,
    scheduleAvailable: Boolean,
    onOpenSchedule: () -> Unit,
    catchupSupported: Boolean,
    catchupActive: Boolean,
    catchupSession: CatchupSession?,
    onOpenCatchup: () -> Unit,
    onReturnToLive: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    playFocusRequester: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xB3000000),
                ),
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // Durante il catch-up il blocco EPG live è sostituito dal programma
        // Rivedi in riproduzione, con il ritorno al live a un tap: le due
        // viste condividono lo stesso slot (nessun cambio di altezza).
        catchupSession?.let { session ->
            Text(
                text = stringResource(R.string.catchup_badge),
                style = MaterialTheme.typography.labelSmall,
                color = AppPrimary,
            )
            Text(
                text = session.programme.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onReturnToLive,
                modifier = Modifier.padding(top = 2.dp).tvFocusHighlight(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.catchup_return_live),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppPrimary,
                )
            }
        } ?: run {
            epg.first?.let { now ->
            Text(
                text = stringResource(R.string.epg_now),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFAEB9C9),
            )
            Text(
                text = "${formatHourRange(now.start, now.stop)} \u00b7 ${now.title}",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Avanzamento del programma (feature 5): barra viva sotto la
            // riga "orario · titolo", con i minuti che mancano alla fine.
            ProgrammeProgressBar(
                programme = now,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        epg.second?.let { next ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${stringResource(R.string.epg_next)}: ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB9C9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        } // fine ramo live (catchupSession == null)

        // Apre la programmazione del canale (solo se la guida ha dati).
        if (scheduleAvailable) {
            TextButton(
                onClick = onOpenSchedule,
                modifier = Modifier.padding(top = 4.dp).tvFocusHighlight(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.epg_schedule),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppPrimary,
                )
            }
        }

        // Apre il Rivedi (catch-up La7 Play): il pulsante compare SOLO sui
        // canali coperti dal servizio (La7, La7 Cinema — match sull'identità
        // del canale, non sul selettore paese). Evidenziato mentre una
        // replica è in riproduzione.
        if (catchupSupported) {
            TextButton(
                onClick = onOpenCatchup,
                modifier = Modifier.padding(top = 4.dp).tvFocusHighlight(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = if (catchupActive) AppPrimary else Color(0xFFAEB9C9),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.catchup_open),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (catchupActive) AppPrimary else Color.White,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            PlayerControlButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.player_prev),
                onClick = onPrev,
            )
            PlayerControlButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.player_play_pause),
                onClick = onPlayPause,
                big = true,
                modifier = Modifier.focusRequester(playFocusRequester),
            )
            PlayerControlButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next),
                onClick = onNext,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Avanzamento del programma in onda (feature 5)
// ---------------------------------------------------------------------------

/**
 * Barra di avanzamento del programma in onda: riempimento proporzionale ai
 * minuti trascorsi e, a destra, i minuti che mancano alla fine. Il tick EPG
 * del ViewModel cambia il programma solo al confine (e lo StateFlow non
 * riemette coppie uguali), quindi la barra possiede un suo ritmo interno:
 * ricalcola ogni 30 secondi. Il remember chiavato su [programme] riporta il
 * riempimento al valore corretto al cambio programma, senza attendere il
 * primo tick. Non è focalizzabile: su TV il D-pad non la incrocia.
 */
@Composable
private fun ProgrammeProgressBar(
    programme: Programme,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember(programme) { mutableLongStateOf(System.currentTimeMillis()) }
    // Durata non valida (stop <= start): niente barra, niente tick inutile.
    val fraction = remember(programme, nowMs) { programme.progressAt(nowMs) } ?: return
    val remainingMin = remember(programme, nowMs) {
        ceil((programme.stop - nowMs) / 60_000.0).toInt().coerceAtLeast(0)
    }
    LaunchedEffect(programme) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Timeline "manuale" con Box: pieno controllo della forma (nessuna
        // differenza di stile fra versioni di material3, niente stop
        // indicator/gap), altezza fissa, estremità arrotondate.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x26FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(AppPrimary),
            )
        }
        if (remainingMin > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.epg_remaining, remainingMin),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFAEB9C9),
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Programmazione del canale (EPG -1h → +24h)
// ---------------------------------------------------------------------------

/** Righe del foglio programmazione: intestazione di giorno oppure programma. */
private sealed interface ScheduleRow {
    data class Header(val label: String) : ScheduleRow
    data class Item(val programme: Programme, val isNow: Boolean) : ScheduleRow
}

/**
 * Raggruppa i programmi per giorno civile ("Oggi", "Domani", poi la data):
 * le etichette localizzate arrivano dalla UI, il raggruppamento usa [dayKey].
 */
private fun buildScheduleRows(
    schedule: List<Programme>,
    todayLabel: String,
    tomorrowLabel: String,
): List<ScheduleRow> {
    val now = System.currentTimeMillis()
    val today = dayKey(now)
    val tomorrow = dayKey(now + 86_400_000L)
    val rows = mutableListOf<ScheduleRow>()
    var lastDay: Pair<Int, Int>? = null
    for (programme in schedule) {
        val key = dayKey(programme.start)
        if (key != lastDay) {
            lastDay = key
            rows += ScheduleRow.Header(
                when (key) {
                    today -> todayLabel
                    tomorrow -> tomorrowLabel
                    else -> SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
                        .format(Date(programme.start))
                },
            )
        }
        rows += ScheduleRow.Item(programme, programme.start <= now && now < programme.stop)
    }
    return rows
}

/**
 * Foglio modale con la programmazione del canale: sezioni per giorno,
 * programma in onda evidenziato e portato in vista all'apertura.
 */
@Composable
private fun EpgScheduleSheet(
    schedule: List<Programme>,
    reminderKeys: Set<String>,
    onToggleReminder: (Programme) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.epg_schedule),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        if (schedule.isEmpty()) {
            Text(
                text = stringResource(R.string.epg_schedule_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFAEB9C9),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            )
        } else {
            val todayLabel = stringResource(R.string.epg_today)
            val tomorrowLabel = stringResource(R.string.epg_tomorrow)
            val rows = remember(schedule, todayLabel, tomorrowLabel) {
                buildScheduleRows(schedule, todayLabel, tomorrowLabel)
            }
            val listState = rememberLazyListState()
            val nowIndex = rows.indexOfFirst { it is ScheduleRow.Item && it.isNow }

            // All'apertura (e al cambio canale) porta in vista il programma
            // in onda, con una riga di contesto sopra se possibile.
            LaunchedEffect(rows) {
                if (nowIndex > 1) listState.scrollToItem(nowIndex - 1)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(rows.size) { index ->
                    when (val row = rows[index]) {
                        is ScheduleRow.Header -> Text(
                            text = row.label.replaceFirstChar { it.uppercase(LocalLocale.current.platformLocale) },
                            style = MaterialTheme.typography.titleSmall,
                            color = AppPrimary,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )

                        is ScheduleRow.Item -> {
                            val now = System.currentTimeMillis()
                            ScheduleItemRow(
                                row = row,
                                remindable = row.programme.start > now,
                                reminderArmed = ProgrammeReminder(
                                    channelId = row.programme.channelId,
                                    start = row.programme.start,
                                    title = row.programme.title,
                                ).key() in reminderKeys,
                                onToggleReminder = { onToggleReminder(row.programme) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Riga programma: orario a sinistra, titolo e descrizione a destra. I
 * programmi futuri mostrano il campanello "avvisami quando inizia" (feature
 * reminder); quello in onda non è remindabile e non lo mostra.
 */
@Composable
private fun ScheduleItemRow(
    row: ScheduleRow.Item,
    remindable: Boolean,
    reminderArmed: Boolean,
    onToggleReminder: () -> Unit,
) {
    val isCurrent = row.isNow
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // focusable: su Android TV il D-pad può spostare il focus tra le
            // righe e la lista scorre da sola (senza resta bloccata).
            .focusable()
            .background(if (isCurrent) AppPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(116.dp)) {
            Text(
                text = formatHourRange(row.programme.start, row.programme.stop),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) AppPrimary else Color(0xFFAEB9C9),
            )
            if (row.isNow) {
                Surface(
                    color = AppPrimary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.epg_badge_now),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = row.programme.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) AppPrimary else Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.programme.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFAEB9C9),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (remindable) {
            // Campanello "avvisami quando inizia" (feature reminder): pieno e
            // colorato quando il promemoria è armato; raggiungibile col D-pad
            // su TV come le righe.
            IconButton(
                onClick = onToggleReminder,
                modifier = Modifier
                    .size(36.dp)
                    .tvFocusHighlight(),
            ) {
                Icon(
                    imageVector = if (reminderArmed) Icons.Filled.Notifications
                        else Icons.Outlined.Notifications,
                    contentDescription = stringResource(
                        if (reminderArmed) R.string.reminder_remove
                        else R.string.reminder_add,
                    ),
                    tint = if (reminderArmed) AppPrimary else Color(0xFF6B7688),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rivedi (catch-up TV La7 Play): foglio con i 7 giorni e le repliche
// ---------------------------------------------------------------------------

/**
 * Etichetta del giorno Rivedi per i chip: "Oggi", "Ieri", poi il nome del
 * giorno con la data ("sabato 31 ottobre"). Stessa convenzione del foglio
 * Programmazione, ma sul passato.
 */
private fun catchupDayLabel(startOfDay: Long, todayLabel: String, yesterdayLabel: String): String {
    val now = System.currentTimeMillis()
    return when (dayKey(startOfDay)) {
        dayKey(now) -> todayLabel
        dayKey(now - 86_400_000L) -> yesterdayLabel
        else -> SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(startOfDay))
    }
}

/**
 * Foglio del Rivedi: selettore dei giorni (oggi + i 6 precedenti, dal più
 * recente) e lista dei programmi del giorno scelto. Stati: caricamento
 * (prima apertura), errore con "Riprova", lista con repliche selezionabili
 * e righe non disponibili (nessuna replica o contenuto protetto DRM).
 */
@Composable
private fun CatchupSheet(
    days: List<CatchupDay>?,
    loading: Boolean,
    error: String?,
    session: CatchupSession?,
    onSelect: (CatchupProgramme) -> Unit,
    onRetry: () -> Unit,
) {
    var selectedDay by remember(days) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.catchup_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        when {
            loading -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = AppPrimary)
            }

            error != null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppError,
                )
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }

            days == null || days.isEmpty() -> Text(
                text = stringResource(R.string.catchup_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFAEB9C9),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 36.dp),
            )

            else -> {
                val todayLabel = stringResource(R.string.epg_today)
                val yesterdayLabel = stringResource(R.string.catchup_yesterday)
                val day = days[selectedDay.coerceIn(days.indices)]

                // Chip dei giorni: il selezionato è pieno (tondo primario),
                // gli altri in contorno; scorrono orizzontalmente se la
                // riga non li contiene tutti.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    days.forEachIndexed { index, entry ->
                        val selected = index == selectedDay
                        if (selected) {
                            FilledTonalButton(onClick = { selectedDay = index }) {
                                Text(
                                    catchupDayLabel(entry.startOfDay, todayLabel, yesterdayLabel)
                                        .replaceFirstChar { it.uppercase(Locale.getDefault()) },
                                )
                            }
                        } else {
                            OutlinedButton(onClick = { selectedDay = index }) {
                                Text(
                                    catchupDayLabel(entry.startOfDay, todayLabel, yesterdayLabel)
                                        .replaceFirstChar { it.uppercase(LocalLocale.current.platformLocale) },
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                ) {
                    items(day.programmes.size) { index ->
                        CatchupProgrammeRow(
                            programme = day.programmes[index],
                            isActive = session != null &&
                                session.programme == day.programmes[index],
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Riga programma del Rivedi: orario, titolo, sinossi e stato a destra.
 * Le repliche riproducibili aprono lo stream on-demand; le righe "non
 * disponibile" (nessuna replica) e "protette" (DRM) restano evidenziate ma
 * non cliccabili, con la causa leggibile. La riga in riproduzione è
 * evidenziata col colore primario.
 */
@Composable
private fun CatchupProgrammeRow(
    programme: CatchupProgramme,
    isActive: Boolean,
    onSelect: (CatchupProgramme) -> Unit,
) {
    val playable = programme.playable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // focusable: su Android TV il D-pad attraversa le righe come
            // nella programmazione EPG.
            .focusable()
            .background(if (isActive) AppPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (playable) Modifier.clickable { onSelect(programme) } else Modifier,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(96.dp)) {
            Text(
                text = formatHourRange(programme.start, programme.stop),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) AppPrimary else Color(0xFFAEB9C9),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = programme.title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isActive -> AppPrimary
                    playable -> Color.White
                    else -> Color(0xFF6B7688)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            programme.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7688),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when {
            isActive -> CatchupBadge(stringResource(R.string.catchup_playing))
            !playable && programme.drmProtected ->
                CatchupBadge(stringResource(R.string.catchup_drm), dim = true)
            !playable -> CatchupBadge(stringResource(R.string.catchup_unavailable), dim = true)
        }
    }
}

/** Badge di stato di una riga Rivedi ("In riproduzione", "Non disponibile", …). */
@Composable
private fun CatchupBadge(text: String, dim: Boolean = false) {
    Surface(
        color = if (dim) Color.Transparent else AppPrimary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (dim) Color(0xFF6B7688) else AppPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
