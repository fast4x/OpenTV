package it.fast4x.opentv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.AppSettings
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.GroupInfo
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.Programme
import it.fast4x.opentv.data.model.ProgrammeReminder
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.model.UNCATEGORIZED_GROUP
import it.fast4x.opentv.data.model.channelGroupKeys
import it.fast4x.opentv.data.repository.EpgStatus
import it.fast4x.opentv.data.repository.SourceResolver
import it.fast4x.opentv.data.remote.ProfileCodec
import it.fast4x.opentv.data.remote.ProfileExportBundle
import it.fast4x.opentv.data.store.UserPrefsStore
import it.fast4x.opentv.util.dayKey
import it.fast4x.opentv.util.formatHourRange
import kotlin.math.ceil
import kotlin.math.roundToInt
import java.awt.AWTEvent
import java.awt.Cursor
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI desktop (Compose Multiplatform): Home con ricerca/preferiti/ultimi visti,
 * player VLC a schermo pieno e impostazioni (URL, import/export playlist).
 * Le scelte visuali seguono l'app Android (palette Dark TV, card simili).
 */
@Composable
fun OpenTvDesktopApp(deps: DesktopDependencies, fullscreen: DesktopFullscreenController, window: Window) {
    val navigator = remember { DesktopNavigator() }
    val scope = rememberCoroutineScope()
    val homeState = remember { DesktopHomeState(deps, scope) }
    val guideState = remember { DesktopGuideState(deps, scope) }

    // Mini-player (feature 11): il controller vive a livello app perché la
    // finestretta sopravvive alla schermata del player (l'utente torna in
    // Home/guida e il video prosegue in un angolo, sempre in primo piano).
    // Il navigatore lo conosce così ogni apertura del player pieno chiude il
    // mini (una sola riproduzione attiva, un solo audio).
    val miniPlayer = remember { DesktopMiniPlayerController() }
    navigator.miniPlayer = miniPlayer

    // Scheduler dei promemoria (feature "reminder"): vive a livello app, non
    // nel player — l'avviso deve arrivare anche mentre si sfoglia la guida.
    LaunchedEffect(Unit) {
        DesktopReminderScheduler.start(deps)
    }

    // Nota: la finestra e' creata senza decorazioni (undecorated = true in
    // Main.kt) con title bar custom in Compose (WindowTitleBar.kt): il toggle
    // schermo intero qui sotto non deve piu' nascondere nessuna decorazione di
    // sistema e non lascia residui nella parte alta della finestra.

    when (navigator.current) {
        Screen.Home -> DesktopHomeScreen(navigator, homeState)
        Screen.Settings -> DesktopSettingsScreen(deps, navigator)
        Screen.Guide -> DesktopGuideScreen(navigator, guideState)
        Screen.Player -> DesktopPlayerScreen(deps, navigator, fullscreen, window)
    }

    // Finestretta del mini-player: fuori dalle schermate, sopravvive al
    // cambio di schermata; si apre/chiude col controller (feature 11).
    DesktopMiniPlayerWindow(deps, miniPlayer, navigator)
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

/**
 * Inserisce [char] alla selezione corrente del campo (sostituendo l'eventuale
 * testo selezionato) e riporta il cursore dopo il carattere inserito.
 * Selezione degenere o fuori range: accodamento in fondo (difesa difensiva,
 * non dovrebbe mai capitare con un campo monoriga normalissimo).
 */
private fun TextFieldValue.insertAtSelection(char: Char): TextFieldValue {
    val text = this.text
    val start = selection.min
    val end = selection.max
    return if (start in 0..text.length && end in start..text.length) {
        val newText = text.substring(0, start) + char + text.substring(end)
        copy(text = newText, selection = TextRange(start + 1), composition = null)
    } else {
        copy(text = text + char, selection = TextRange(text.length + 1), composition = null)
    }
}

@Composable
private fun DesktopHomeScreen(
    navigator: DesktopNavigator,
    state: DesktopHomeState,
) {
    val ui by state.uiState.collectAsState()
    val currentCountry by state.currentCountry.collectAsState()
    val profiles by state.profiles.collectAsState()
    val activeProfileId by state.activeProfileId.collectAsState()

    // fix33 — SPAZIO nel campo di ricerca (desktop), cura definitiva.
    // Storia: fix32 (valore RAW al campo, trim solo ai filtri) ha risolto su
    // Android ma non su desktop; fix32-bis (dispatcher AWT che consumava il
    // KEY_PRESSED e rispediva un KEY_TYPED sintetico) non ha prodotto
    // l'inserimento. Fatti verificati sui sorgenti CMP/foundation 1.9.0:
    // 1) i text field su desktop inseriscono i caratteri SOLO dall'evento
    //    AWT KEY_TYPED (foundation TextFieldKeyInput.kt: typedCommand ->
    //    isTypedEvent; TextFieldKeyInput.desktop.kt: isTypedEvent =
    //    awtEventOrNull?.id == KEY_TYPED && keyChar stampabile), mentre il
    //    KEY_PRESSED di VK_SPACE non e' mappato a nessun comando;
    // 2) keyPressed/keyTyped sono registrati sullo STESSO handler
    //    (ComposeSceneMediator.desktop.kt: keyPressed/keyReleased/keyTyped
    //    -> onKeyEvent(event) -> toComposeEvent -> scene.sendKeyEvent), e
    //    FocusOwnerImpl.validateKeyEvent lascia passare SEMPRE gli eventi
    //    KeyEventType.Unknown: se le lettere arrivano (arrivano: la ricerca
    //    digita), lo stesso canale porta a Compose anche il KeyDown dello
    //    spazio — lo conferma il dispatcher tasti del player che lavora;
    // 3) l'evento Compose e' visibile a Modifier.onPreviewKeyEvent PRIMA
    //    dell'elaborazione interna del campo (TextFieldKeyInput si registra
    //    con onKeyEvent, fase a risalita: TextFieldKeyInput.kt in fondo).
    // Cura (niente piu' eventi AWT sintetici):
    // a) il campo usa uno STATO LOCALE TextFieldValue come fonte di verita':
    //    onValueChange aggiorna lo stato locale e inoltra al flusso di
    //    filtro; il flusso NON riscrive piu' il campo, quindi nessuna
    //    rielaborazione asincrona (combine/epgTick/EPG) puo' piu' cancellare
    //    o rindietreggiare cio' che e' stato digitato;
    // b) un onPreviewKeyEvent sul campo inserisce lo spazio direttamente al
    //    KeyDown (Key.Spacebar, senza ctrl/alt/meta) alla posizione di
    //    selezione e consuma l'eventuale Unknown dello spazio (il KEY_TYPED
    //    che, dove arriva, inserirebbe un SECONDO spazio): un solo spazio
    //    sempre, quale che sia la via realmente consegnata dalla piattaforma.
    // Lettere, frecce, backspace, copia/incolla e attivazione da tastiera
    // restano sul percorso normale (il handler e' agganciato al solo campo).
    var searchQuery by remember { mutableStateOf(TextFieldValue(state.uiState.value.query)) }

    // Feature "nascondi canali": card/riga in attesa di conferma. Il clic
    // destro NON nasconde mai direttamente: apre sempre il dialog, come il
    // tap sull'icona nell'app Android.
    var channelToHide by remember { mutableStateOf<Channel?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // Top bar: titolo, ricerca, refresh, impostazioni
        Surface(color = AppSurface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "OpenTV",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTextPrimary,
                )
                Spacer(Modifier.width(24.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        // fix33: fonte di verita' = stato locale; il flusso
                        // riceve il testo per i filtri ma non lo riscrive.
                        searchQuery = it
                        state.onQueryChange(it.text)
                    },
                    placeholder = { Text("Cerca canale…", color = AppTextSecondary) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        // fix33: inserimento dello spazio a livello Compose,
                        // indipendente dal KEY_TYPED AWT (vedi il commento
                        // sopra la dichiarazione di searchQuery).
                        .onPreviewKeyEvent { event ->
                            val harmful = event.isCtrlPressed || event.isAltPressed ||
                                event.isMetaPressed
                            when {
                                // KEY_PRESSED (KeyDown) dello spazio: qui lo
                                // spazio non e' mappato a comandi e l'inserimento
                                // avviene di norma solo dal KEY_TYPED (che non
                                // arriva): inseriamo noi alla selezione corrente
                                // e consumiamo l'evento.
                                event.type == KeyEventType.KeyDown &&
                                    event.key == Key.Spacebar &&
                                    !harmful -> {
                                    val updated = searchQuery.insertAtSelection(' ')
                                    searchQuery = updated
                                    state.onQueryChange(updated.text)
                                    true
                                }
                                // KEY_TYPED dello spazio (su desktop diventa
                                // KeyEventType.Unknown): lo spazio e' GIA'
                                // stato inserito al KeyDown — consumato senza
                                // reinserirlo, cosi' lo spazio non arriva mai
                                // due volte ne' dove il typed funziona.
                                event.type == KeyEventType.Unknown &&
                                    event.utf16CodePoint == ' '.code &&
                                    !harmful -> true
                                else -> false
                            }
                        },
                )
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { state.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna", tint = AppTextPrimary)
                }
                // Guida TV a griglia (feature 8): canali sulle righe,
                // programmi sulla timeline; un clic apre il canale.
                IconButton(onClick = navigator::openGuide) {
                    Icon(Icons.Filled.Tv, contentDescription = "Guida TV", tint = AppTextPrimary)
                }
                IconButton(onClick = navigator::openSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Impostazioni", tint = AppTextPrimary)
                }
            }
        }

        // Riga sorgente (feature "profilazione della lista"): menu dei profili
        // di sorgente (IPTV-ORG automatica + profili utente) e selettore paese.
        // Il paese serve solo alla sorgente automatica: con un profilo attivo
        // il selettore paese è disabilitato (la lista del profilo è autonoma).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceDropdown(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelect = state::onProfileChange,
                onManage = navigator::openSettings,
            )
            CountryDropdown(
                currentCode = currentCountry,
                autoLabel = COUNTRY_AUTO_LABEL,
                onSelect = state::onCountryChange,
                enabled = activeProfileId == null,
            )
        }

        // Filtro canali: chip stella "solo preferiti", chip "solo con guida
        // TV" e riga di chip per gruppo (genere) scorrevole. La riga esiste
        // anche senza gruppi quando ci sono preferiti o canali coperti dalla
        // guida, così i commutatori sono sempre raggiungibili.
        if (ui.groups.isNotEmpty() ||
            ui.favoriteChannels.isNotEmpty() ||
            ui.channelsWithGuide > 0
        ) {
            GroupFilterChips(
                groups = ui.groups,
                selected = ui.selectedGroup,
                onSelect = state::onGroupChange,
                favoritesOnly = ui.favoritesOnly,
                onFavoritesOnlyChange = state::onFavoritesOnlyChange,
                guideOnly = ui.guideOnly,
                channelsWithGuide = ui.channelsWithGuide,
                onGuideOnlyChange = state::onGuideOnlyChange,
            )
        }

        when {
            ui.loading && ui.channels.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text("Carico i canali…", color = AppTextSecondary)
                }
            }

            ui.error != null && ui.channels.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = AppError, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(ui.error.orEmpty(), color = AppTextPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { state.refresh() }) { Text("Riprova") }
                }
            }

            else -> ChannelList(
                ui = ui,
                state = state,
                navigator = navigator,
                onHideRequest = { channel -> channelToHide = channel },
            )
        }
    }

    // Dialog di conferma (feature "nascondi canali"): mai nascondere in
    // silenzio, da qualunque via arrivi la richiesta (clic destro su card o
    // riga). Il ripristino vive nelle Impostazioni, sezione "Canali nascosti".
    channelToHide?.let { channel ->
        Dialog(onDismissRequest = { channelToHide = null }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                contentColor = AppTextPrimary,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Nascondere questo canale?",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                    )
                    Text(
                        text = "«${channel.name}» sparirà da tutti gli elenchi " +
                            "(Home, ricerca, gruppi, preferiti, guida TV) di questa " +
                            "sorgente. La playlist resta intatta: potrai ripristinarlo " +
                            "quando vuoi dalle Impostazioni.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            state.hideChannel(channel.id)
                            channelToHide = null
                        }) { Text("Nascondi") }
                        OutlinedButton(onClick = { channelToHide = null }) { Text("Annulla") }
                    }
                }
            }
        }
    }
}

/**
 * Voce automatica del selettore paese: usa il paese del dispositivo
 * (hardcoded italiano, coerente con la UI desktop).
 */
const val COUNTRY_AUTO_LABEL = "Automatica (paese del dispositivo)"

@Composable
private fun ChannelList(
    ui: DesktopHomeUiState,
    state: DesktopHomeState,
    navigator: DesktopNavigator,
    onHideRequest: (Channel) -> Unit,
) {
    // Titolo "Preferiti" consapevole del profilo (feature "preferiti per
    // profilo"): con una sorgente attiva il nome nel titolo dice a colpo
    // d'occhio a quale lista appartiene l'elenco mostrato.
    val profiles by state.profiles.collectAsState()
    val activeProfileId by state.activeProfileId.collectAsState()
    val favoritesTitle = profiles
        .firstOrNull { it.id == activeProfileId }
        ?.let { "Preferiti · ${it.name}" }
        ?: "Preferiti"

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        if (ui.fromCache) {
            item {
                Text(
                    "Offline: canali mostrati dalla cache",
                    color = AppGeoBlocked,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Con un filtro attivo (preferiti o guida TV) le due righe orizzontali
        // in testa sarebbero duplicate della lista sotto: le nascondiamo.
        // fix32: con la query raw nel campo, qui (e nelle sezioni seguenti)
        // si usa isBlank/isNotBlank: una query di soli spazi resta "nessuna
        // ricerca", identico al comportamento di quando il valore era trimmato.
        if (!ui.favoritesOnly && !ui.guideOnly && ui.query.isBlank() && ui.selectedGroup == null && ui.favoriteChannels.isNotEmpty()) {
            item { SectionTitle(favoritesTitle) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.favoriteChannels, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            isFavorite = true,
                            epgTitle = ui.epgNow[channel.id]?.title,
                            onClick = { navigator.openPlayer(channel.id) },
                            onToggleFavorite = { state.toggleFavorite(channel.id) },
                            onGroupClick = { group -> state.onGroupChange(group) },
                            onHide = { onHideRequest(channel) },
                        )
                    }
                }
            }
        }

        if (!ui.favoritesOnly && !ui.guideOnly && ui.query.isBlank() && ui.selectedGroup == null && ui.recents.isNotEmpty()) {
            item { SectionTitle("Ultimi visti") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ui.recents, key = { it.id }) { channel ->
                        ChannelCard(
                            channel = channel,
                            isFavorite = channel.id in ui.favorites,
                            epgTitle = ui.epgNow[channel.id]?.title,
                            onClick = { navigator.openPlayer(channel.id) },
                            onToggleFavorite = { state.toggleFavorite(channel.id) },
                            onGroupClick = { group -> state.onGroupChange(group) },
                            onHide = { onHideRequest(channel) },
                        )
                    }
                }
            }
        }

        if (ui.favoritesOnly) {
            // Filtro "solo preferiti": elenco dei preferiti già intersecati con
            // gruppo e ricerca; con la lista vuota un messaggio dedicato guida
            // l'utente (come sull'app Android).
            item { SectionTitle("Preferiti · ${ui.favoriteResults.size} canali") }
            if (ui.favoriteResults.isEmpty()) {
                item {
                    Text(
                        "Nessun preferito: usa la stella su un canale per aggiungerlo",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            items(ui.favoriteResults, key = { it.id }) { channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = true,
                    epgTitle = ui.epgNow[channel.id]?.title,
                    onClick = { navigator.openPlayer(channel.id) },
                    onToggleFavorite = { state.toggleFavorite(channel.id) },
                    onGroupClick = { group -> state.onGroupChange(group) },
                    onHide = { onHideRequest(channel) },
                )
            }
        } else if (ui.selectedGroup != null) {
            // Filtro gruppo attivo: elenco dei soli canali del genere scelto
            // (già intersecati con la ricerca, se presente).
            val label = if (ui.selectedGroup == UNCATEGORIZED_GROUP) "Senza categoria" else ui.selectedGroup
            item { SectionTitle("$label · ${ui.groupChannels.size} canali") }
            items(ui.groupChannels, key = { it.id }) { channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = channel.id in ui.favorites,
                    epgTitle = ui.epgNow[channel.id]?.title,
                    onClick = { navigator.openPlayer(channel.id) },
                    onToggleFavorite = { state.toggleFavorite(channel.id) },
                    onGroupClick = { group -> state.onGroupChange(group) },
                    onHide = { onHideRequest(channel) },
                )
            }
        } else if (ui.query.isNotBlank()) {
            item { SectionTitle("Risultati per \"${ui.query}\"") }
            items(ui.filteredChannels, key = { it.id }) { channel ->
                ChannelRow(
                    channel = channel,
                    isFavorite = channel.id in ui.favorites,
                    epgTitle = ui.epgNow[channel.id]?.title,
                    onClick = { navigator.openPlayer(channel.id) },
                    onToggleFavorite = { state.toggleFavorite(channel.id) },
                    onGroupClick = { group -> state.onGroupChange(group) },
                    onHide = { onHideRequest(channel) },
                )
            }
        } else {
            // Filtro "solo con guida TV" attivo (senza altri filtri): un
            // titolo col conteggio rende subito visibile l'effetto del chip.
            if (ui.guideOnly) {
                item { SectionTitle("Solo con guida · ${ui.channels.size} canali") }
            }
            ui.groupedChannels.forEach { (letter, channels) ->
                item(key = "header_$letter") { SectionTitle(letter.toString()) }
                items(channels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        isFavorite = channel.id in ui.favorites,
                        epgTitle = ui.epgNow[channel.id]?.title,
                        onClick = { navigator.openPlayer(channel.id) },
                        onToggleFavorite = { state.toggleFavorite(channel.id) },
                        onGroupClick = { group -> state.onGroupChange(group) },
                        onHide = { onHideRequest(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = AppTextPrimary,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/**
 * Riga orizzontale di chip per filtrare i canali: in testa il chip stella
 * "solo preferiti" (commutatore di sessione, combinabile con i gruppi), il
 * chip "solo con guida TV" (persistito, visibile solo se la guida copre
 * almeno un canale), poi i chip per gruppo (genere). Porting del filtro
 * Android (fix20, guida TV feature successiva).
 */
@Composable
private fun GroupFilterChips(
    groups: List<GroupInfo>,
    selected: String?,
    onSelect: (String?) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    guideOnly: Boolean,
    channelsWithGuide: Int,
    onGuideOnlyChange: (Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    // Un clic sul badge genere può selezionare un chip fuori schermo: la riga
    // si allinea da sola al chip del gruppo scelto (item 0 = stella, item 1 =
    // guida, item 2 = "Tutti", i gruppi partono da 3).
    LaunchedEffect(selected) {
        val index = if (selected == null) 2 else groups.indexOfFirst { it.key == selected } + 3
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "chip_favorites") {
            GroupChip(
                label = "Preferiti",
                icon = if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                selected = favoritesOnly,
                onClick = { onFavoritesOnlyChange(!favoritesOnly) },
            )
        }
        // Slot sempre presente (anche vuoto) così gli indici di scroll restano
        // stabili: il chip appare solo se la guida copre almeno un canale.
        item(key = "chip_guide") {
            if (channelsWithGuide > 0) {
                GroupChip(
                    label = "Solo con guida",
                    icon = if (guideOnly) Icons.Filled.Tv else Icons.Outlined.Tv,
                    count = channelsWithGuide,
                    selected = guideOnly,
                    onClick = { onGuideOnlyChange(!guideOnly) },
                )
            }
        }
        item(key = "chip_all") {
            GroupChip(
                label = "Tutti",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(groups, key = { it.key }) { group ->
            GroupChip(
                label = if (group.key == UNCATEGORIZED_GROUP) "Senza categoria" else group.key,
                count = group.count,
                selected = selected == group.key,
                onClick = { onSelect(group.key) },
            )
        }
    }
}

/** Chip a pillola del filtro canali (gruppi e stella preferiti). */
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    icon: ImageVector? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppPrimary else AppSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    // Stella dorata da spenta, bianca sul chip attivo (sfondo
                    // AppPrimary): coerente con il chip Android.
                    tint = if (selected) Color.White else AppFavorite,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = if (count != null) "$label · $count" else label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else AppTextSecondary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Card e righe canale
// ---------------------------------------------------------------------------

/**
 * Genere per il badge su card/riga (desktop): primo gruppo reale del
 * group-title (le categorie multiple restano visibili nei chip del filtro);
 * null per i canali senza gruppo.
 */
private fun groupBadgeText(channel: Channel): String? =
    channelGroupKeys(channel).firstOrNull { it != UNCATEGORIZED_GROUP }

/**
 * Badge genere (desktop): pillola verde acqua con testo scuro, come il badge
 * sulla card Android; un clic filtra i canali dello stesso genere (mouse,
 * nessun vincolo D-pad). Lo Spacer finale lo distacca dal testo vicino.
 */
@Composable
private fun GroupBadgeLabel(channel: Channel, onGroupClick: (String) -> Unit) {
    val group = groupBadgeText(channel) ?: return
    Text(
        text = group,
        color = Color(0xFF1B1B1F),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onGroupClick(group) }
            .background(AppAccent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
    Spacer(Modifier.width(6.dp))
}

/**
 * Clic destro (feature "nascondi canali"): con CMP 1.9 (sorgenti unify) lo
 * stato dei bottoni sta su PointerEvent, NON su PointerInputChange, e
 * isSecondaryPressed e' un'estensione da importare esplicitamente. Il down
 * del tasto secondario viene intercettato e consumato al pass Initial, PRIMA
 * che il clickable della card lo veda: il player non si apre e viene invocato
 * [onHide], che apre il dialog di conferma. Con onHide null non installa
 * nessun gestore (risparmia un pointerInput in ascolto per ogni card).
 */
private fun Modifier.onSecondaryClickDown(onHide: (() -> Unit)?): Modifier {
    if (onHide == null) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (
                    event.buttons.isSecondaryPressed &&
                    event.changes.all { it.changedToDown() }
                ) {
                    event.changes.forEach { it.consume() }
                    onHide()
                    break
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    isFavorite: Boolean,
    epgTitle: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGroupClick: (String) -> Unit,
    onHide: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppSurface)
            // Tasto destro (feature "nascondi canali"): richiede il dialog di
            // conferma al chiamante; il down consumato al pass Initial non
            // apre il player.
            .onSecondaryClickDown(onHide)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(channel)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = channel.name,
            color = AppTextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Seconda riga: solo il programma in onda; il genere sta nel badge.
        epgTitle?.let { programmeTitle ->
            Text(
                text = programmeTitle,
                color = AppTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Preferito",
                    tint = if (isFavorite) AppFavorite else AppTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (channel.geoBlocked) {
                Text(
                    text = "Geo-blocked",
                    color = AppGeoBlocked,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            GroupBadgeLabel(channel, onGroupClick)
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    epgTitle: String?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGroupClick: (String) -> Unit,
    onHide: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppSurface)
            // Tasto destro (feature "nascondi canali"): richiede il dialog di
            // conferma al chiamante; il down consumato al pass Initial non
            // apre il player.
            .onSecondaryClickDown(onHide)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(channel)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = AppTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Seconda riga: solo il programma in onda; il genere sta nel badge.
            epgTitle?.let { programmeTitle ->
                Text(
                    text = programmeTitle,
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GroupBadgeLabel(channel, onGroupClick)
        if (channel.geoBlocked) {
            Text(
                text = "Geo-blocked",
                color = AppGeoBlocked,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = "Preferito",
                tint = if (isFavorite) AppFavorite else AppTextSecondary,
            )
        }
    }
}

@Composable
private fun ChannelLogo(channel: Channel) {
    var bitmap by remember(channel.logoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(channel.logoUrl) {
        bitmap = channel.logoUrl?.let { DesktopLogoLoader.load(it) }
    }
    val logo = bitmap
    if (logo != null) {
        androidx.compose.foundation.Image(
            bitmap = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(4.dp),
        )
    } else {
        Text(
            text = channel.name.take(2).uppercase(),
            color = AppTextSecondary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---------------------------------------------------------------------------
// Player
// ---------------------------------------------------------------------------

@Composable
private fun DesktopPlayerScreen(
    deps: DesktopDependencies,
    navigator: DesktopNavigator,
    fullscreen: DesktopFullscreenController,
    window: Window,
) {
    val channelId = navigator.playerChannelId ?: return
    val scope = rememberCoroutineScope()
    val state = remember(channelId) { DesktopPlayerState(deps, scope, channelId) }

    val channel by state.currentChannel.collectAsState()
    val status by state.playback.collectAsState()
    val epg by state.epg.collectAsState()
    val schedule by state.schedule.collectAsState()
    val favorites by state.favorites.collectAsState()
    val isFavorite = favorites.isNotEmpty() && channel?.id?.let { it in favorites } == true
    val isError = status is PlayerStatus.Error

    // Timer di spegnimento (porting Android): icona attiva in top bar + menu
    // con le durate e l'orario previsto di stop.
    val sleepTimerMinutes by state.sleepTimerMinutes.collectAsState()
    val sleepTimerEndsAt by state.sleepTimerEndsAt.collectAsState()
    var showSleepMenu by remember { mutableStateOf(false) }

    // Tracce audio (porting Android): menu di selezione per i canali
    // multilingua; il pulsante compare solo se il flusso offre più di una
    // traccia, quella attiva è evidenziata nel menu.
    val audioTracks by state.audioTracks.collectAsState()
    val currentAudioTrackId by state.currentAudioTrackId.collectAsState()
    var showAudioMenu by remember { mutableStateOf(false) }

    // Sottotitoli (gemello del menu audio, feature "sottotitoli"): lo stesso
    // schema, con la voce "Disattivati" al posto di "Automatica". Il pulsante
    // compare se il flusso annuncia almeno una traccia SPU.
    val subtitleTracks by state.subtitleTracks.collectAsState()
    val currentSubtitleTrackId by state.currentSubtitleTrackId.collectAsState()
    var showSubtitleMenu by remember { mutableStateOf(false) }

    // Statistiche stream (feature "statistiche"): toggle con il tasto I o col
    // pulsante; mentre sono attive la riga sotto il nome canale mostra i dati
    // tecnici al posto del programma EPG (stessa altezza della riga: lo slot
    // della barra non cambia mai, difesa fix28).
    var showStats by remember { mutableStateOf(false) }
    val streamStats by state.streamStats.collectAsState()
    LaunchedEffect(showStats) {
        if (!showStats) return@LaunchedEffect
        while (true) {
            state.refreshStreamStats()
            delay(1000)
        }
    }

    // Zapping numerico (feature "zapping numerico"): le cifre digitate si
    // accumulano nel buffer e il canale corrispondente alla posizione nella
    // playlist viene aperto alla scadenza del timer (1.2s) o premendo Invio.
    // Il timer è cancellato a ogni nuova cifra, come sui telecomandi veri.
    var zapBuffer by remember { mutableStateOf("") }
    var zapTimeoutJob by remember { mutableStateOf<Job?>(null) }

    fun resolveZap() {
        val number = zapBuffer.toIntOrNull()
        zapBuffer = ""
        zapTimeoutJob?.cancel()
        zapTimeoutJob = null
        if (number == null) return
        val channels = (deps.playlistRepository.state.value as? PlaylistState.Success)
            ?.channels.orEmpty()
        val channel = channels.getOrNull(number - 1)
        if (channel != null) {
            state.playChannel(channel)
        }
    }

    fun zapDigit(digit: Char) {
        // Cap pratico: un numero di canale non supera mai le cifre della
        // playlist; oltre, il buffer riparte (nuova cifra = nuovo inizio).
        val maxLength = (deps.playlistRepository.state.value as? PlaylistState.Success)
            ?.channels?.size?.toString()?.length ?: 4
        zapBuffer = if (zapBuffer.length >= maxLength) digit.toString()
            else zapBuffer + digit
        zapTimeoutJob?.cancel()
        zapTimeoutJob = scope.launch {
            delay(1200)
            resolveZap()
        }
    }


    // Pannello "Programmazione" affiancato al video (aperto/chiuso dal pulsante
    // nella barra superiore; disabilitato se la guida non ha dati per il canale).
    var showSchedule by remember { mutableStateOf(false) }

    // Promemoria programmi (feature "reminder"): le voci armate (chiave
    // canale@inizio) arrivano dal DataStore; il campanello nella programmazione
    // arma/disarma la voce. La notifica la spara lo scheduler a livello app.
    val reminders by deps.prefs.reminders.collectAsState(emptyList())
    val reminderKeys = remember(reminders) { reminders.map { it.key() }.toSet() }
    fun toggleReminder(programme: Programme) {
        scope.launch {
            val armed = ProgrammeReminder(
                channelId = programme.channelId,
                start = programme.start,
                title = programme.title,
            ).key() in reminderKeys
            if (armed) deps.prefs.removeReminder(programme.channelId, programme.start)
            else deps.prefs.addReminder(
                ProgrammeReminder(
                    channelId = programme.channelId,
                    start = programme.start,
                    title = programme.title,
                ),
            )
        }
    }

    // Suggerimento volume temporaneo: premendo ↑/↓ o girando la rotella del
    // mouse sul video (feature 6) la riga sotto i controlli mostra "Volume N%"
    // per qualche secondo, poi torna ai suggerimenti.
    var volumeHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(volumeHint) {
        if (volumeHint != null) {
            delay(1600)
            volumeHint = null
        }
    }

    // Suggerimento proporzioni temporaneo (stesso schema del volume): dopo il
    // clic sul pulsante la riga dei suggerimenti mostra il modo attivo.
    var resizeHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resizeHint) {
        if (resizeHint != null) {
            delay(1600)
            resizeHint = null
        }
    }

    // Schermo intero reale a livello OS, in modo "borderless": la finestra e'
    // creata senza decorazioni (undecorated = true in Main.kt) e lo schermo
    // intero porta i SUOI bounds su quelli dello schermo corrente
    // (DesktopFullscreenController). NON si usa WindowPlacement.Fullscreen:
    // in CMP 1.9.0 attiva il fullscreen ESCLUSIVO di AWT
    // (GraphicsDevice.setFullScreenWindow), che su Windows riscrive gli stili
    // nativi della finestra e rende inaffidabile il mixing AWT lightweight/
    // heavyweight: alla nascondita dei controlli la regione di clip del video
    // si allarga fino a coprire lo schermo e l'area esposta non viene
    // ridipinta (lo "sfondo bianco senza video" a schermo intero). Con la
    // finestra borderless il percorso di rendering e' identico a quello della
    // modalita' finestra, dove hide/show funziona senza difetti. Pulsante
    // nella top bar + tasto F: stessa funzione, stesso stato.
    val isFullscreen = fullscreen.active
    fun toggleFullscreen() = fullscreen.toggle(window)

    // Uscita dal player: riporta sempre la finestra a normale (se si era a
    // schermo intero la richiude), così la Home non resta mai fullscreen senza
    // i tasti del player (F funziona solo qui dentro).
    fun exitPlayer() {
        if (fullscreen.active) fullscreen.exit(window)
        navigator.backFromPlayer()
    }

    // ---- Auto-nascondimento controlli (in ogni modalità) --------------------
    // La superficie VLC è un componente AWT HEAVYWEIGHT: sopra il video Compose
    // non vede ALCUN evento mouse (li assorbe il componente nativo) e quando i
    // componenti heavyweight cambiano geometria AWT/CMP RIGENERA eventi
    // MOUSE_MOVED "sintetici" con il puntatore FERMO (coordinate identiche).
    // Da fix28 lo show/hide dei controlli NON cambia più geometria (barre in
    // slot a altezza fissa, scomparsa solo visuale), ma il resize della
    // finestra e il toggle fullscreen rigenerano comunque eventi sintetici:
    // contarli come attività riavvierebbe il ciclo mostra/nascondi a ripetizione.
    // Difesa: vale solo un CAMBIAMENTO REALE DI POSIZIONE del puntatore; eventi
    // con la stessa posizione sullo schermo sono sintetici e vengono ignorati.
    var lastMouseActivity by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastMousePos by remember { mutableStateOf<Point?>(null) }

    // Feature 6 — volume con la rotella: la rotella regola il volume SOLO con
    // il puntatore sopra la superficie video (come VLC desktop). L'evento AWT
    // ha come sorgente il componente più interno sotto il cursore: se discende
    // dalla superficie vlcj siamo sul video. Sui pannelli Compose (pannello
    // programmazione, liste della Home) la rotella continua a scorrere i
    // contenuti senza toccare il volume; sopra il video gli eventi non
    // arrivano a Compose (il componente nativo li assorbe), quindi il
    // listener Toolkit è l'unico punto di intercettazione possibile.
    fun applyWheelVolume(event: MouseWheelEvent) {
        val video = state.videoComponent() ?: return
        val source = event.source as? java.awt.Component ?: return
        if (!javax.swing.SwingUtilities.isDescendingFrom(source, video)) return
        // wheelRotation < 0 = rotella in avanti = alza volume; un cric = ±10,
        // identico ai tasti ↑/↓. Con rotelle ad alta risoluzione/trackpad gli
        // scatti frazionari di preciseWheelRotation si accumulano in
        // wheelRotation: nessun handle speciale serve.
        val step = -event.wheelRotation * 10
        if (step != 0) state.changeVolume(step)?.let { volumeHint = "Volume $it%" }
    }

    // Chiave = state (come per il dispatcher tasti): se lo stato del player
    // venisse ricreato la closure della rotella resta agganciata a quello vero.
    DisposableEffect(state) {
        val listener = AWTEventListener { event ->
            when (event.id) {
                MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED -> {
                    val me = event as MouseEvent
                    val pos = runCatching { me.locationOnScreen }.getOrNull()
                    if (pos != null && pos != lastMousePos) {
                        lastMousePos = pos
                        lastMouseActivity = System.currentTimeMillis()
                    }
                }
                MouseEvent.MOUSE_PRESSED ->
                    lastMouseActivity = System.currentTimeMillis()

                MouseEvent.MOUSE_WHEEL -> {
                    // La rotella conta come attività (riporta i controlli) E,
                    // se siamo sopra il video, regola il volume (feature 6).
                    lastMouseActivity = System.currentTimeMillis()
                    if (event is MouseWheelEvent) applyWheelVolume(event)
                }
            }
        }
        val mask = (AWTEvent.MOUSE_EVENT_MASK or
            AWTEvent.MOUSE_MOTION_EVENT_MASK or
            AWTEvent.MOUSE_WHEEL_EVENT_MASK).toLong()
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask)
        onDispose {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        }
    }

    // controlsVisible è DERIVATO dal timestamp di attività in un unico punto
    // (questo loop): lo stato cambia solo quando supera la soglia, mai in
    // reazione a eventi spuri di ricomposizione. In errore i controlli restano
    // visibili (servono per "Riprova"/"Indietro"). L'auto-nascondimento vale
    // in OGNI modalità (finestra inclusa, non solo fullscreen): dopo il tempo
    // di inattività le barre sbiadiscono nei loro slot (solo visuale: il
    // video NON viene ridimensionato, vedi "slot a altezza costante" sotto);
    // al primo movimento del mouse tornano. La barra del titolo della finestra resta
    // sempre visibile in modalità finestra (trascinamento/chiudi sempre
    // raggiungibili): vive fuori dal player, in Main.kt.
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            val idleMs = System.currentTimeMillis() - lastMouseActivity
            controlsVisible =
                (status is PlayerStatus.Error) || idleMs < Config.CONTROLS_HIDE_DELAY_MS
            delay(200)
        }
    }
    // Al cambio di modalità (ingresso/uscita fullscreen) i controlli tornano
    // visibili per un ciclo di inattività: evita di restare senza barre subito
    // dopo la transizione, prima di un nuovo movimento del mouse.
    LaunchedEffect(isFullscreen) {
        lastMouseActivity = System.currentTimeMillis()
    }

    // I menu a tendina (timer, tracce audio, sottotitoli) vivono in finestre
    // Popup native: l'alpha della barra non li spegne. Se i controlli si
    // nascondono mentre un menu è aperto, chiudiamolo esplicitamente
    // (AnimatedVisibility, in passato, lo smontava insieme alla barra).
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            showSleepMenu = false
            showAudioMenu = false
            showSubtitleMenu = false
        }
    }

    // Alpha animato delle barre: la visibilità è SOLO RENDER (grafica), il
    // layout non cambia mai -> la superficie video non viene ridimensionata
    // quando le barre si nascondono o riappaiono (cura dello sfondo bianco:
    // il ridimensionamento ripetuto della finestra nativa vlcj era la causa).
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "controlsAlpha",
    )

    // A schermo intero con controlli nascosti nasconde anche il puntatore sul
    // video (cursore vuoto sul ComposeWindow; il Canvas VLC, senza cursore
    // proprio, lo eredita). Tornando visibili i controlli torna il normale.
    DisposableEffect(isFullscreen, controlsVisible, isError) {
        window.cursor =
            if (isFullscreen && !controlsVisible && !isError) blankCursor()
            else Cursor.getDefaultCursor()
        onDispose { window.cursor = Cursor.getDefaultCursor() }
    }

    // Dopo un ridimensionamento dell'area video (resize finestra, ingresso/
    // uscita fullscreen, apertura pannello programmazione, arrivo EPG) il
    // rendering VLC puo' restare congelato/vuoto: un ComponentListener lo
    // risveglia a ogni resize. Lo show/hide dei controlli da fix28 non
    // ridimensiona più nulla (barre in slot a altezza fissa).
    DisposableEffect(Unit) {
        val comp = state.videoComponent()
        val adapter = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                state.recoverVideoSurface()
            }
        }
        if (comp != null) comp.addComponentListener(adapter)
        onDispose { comp?.removeComponentListener(adapter) }
    }

    // Rete di sicurezza per le transizioni che ridisegnano ancora la finestra
    // (ingresso/uscita fullscreen, apertura player, apertura pannello
    // programmazione, arrivo EPG): se un resize della superficie arriva
    // mentre la gerarchia Swing e' in transizione il risveglio puo'
    // perdersi e la finestra resta senza superfici vive (il "tutto bianco").
    // Lo show/hide dei controlli NON e' più tra i casi (slot a altezza
    // fissa: nessun reflow, solo alpha), ma la rete resta: costa nulla ed
    // e' idempotente. A OGNI transizione: subito un ponte nero sull'albero
    // AWT (SwingBlackout: le aree cancellate dal nativo diventano nere, non
    // bianche, e la scena skiko viene riportata in pipeline) e, a layout
    // concluso (500ms), il recovery completo della superficie video.
    // Entrambi senza effetto se tutto e' gia' a posto.
    LaunchedEffect(isFullscreen, controlsVisible) {
        state.blackoutWindow()
        delay(500)
        state.recoverVideoSurface()
    }

    // Scorciatoie da tastiera: la superficie VLC è un Canvas AWT heavyweight
    // che prende il focus al click sul video, quindi un onPreviewKeyEvent di
    // Compose NON riceverebbe i tasti. Un KeyEventDispatcher globale AWT
    // intercetta i tasti PRIMA della dispatch AWT qualunque componente abbia
    // il focus (Canvas o Compose); i tasti gestiti vengono consumati
    // (return true) per evitare che arrivino anche a Compose (doppio gesto).
    DisposableEffect(state) {
        val dispatcher = KeyEventDispatcher { event ->
            if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
            // Qualunque tasto premuto conta come attività: riporta a schermo i
            // controlli nascosti e rinvia l'auto-nascondimento.
            lastMouseActivity = System.currentTimeMillis()
            when (event.keyCode) {
                KeyEvent.VK_SPACE -> { state.togglePlayPause(); true }
                KeyEvent.VK_ESCAPE -> { exitPlayer(); true }
                KeyEvent.VK_F -> { toggleFullscreen(); true }
                KeyEvent.VK_LEFT -> { state.switchChannel(-1); true }
                KeyEvent.VK_RIGHT -> { state.switchChannel(+1); true }
                KeyEvent.VK_UP -> {
                    state.changeVolume(+10)?.let { volumeHint = "Volume $it%" }
                    true
                }
                KeyEvent.VK_DOWN -> {
                    state.changeVolume(-10)?.let { volumeHint = "Volume $it%" }
                    true
                }
                // Statistiche stream (feature "statistiche"): toggle con I,
                // come il pannello informazioni dei player desktop.
                KeyEvent.VK_I -> { showStats = !showStats; true }
                // Zapping numerico (feature "zapping numerico"): le cifre si
                // accumulano e il canale parte alla scadenza del buffer (1.2s)
                // o premendo Invio; il buffer si annulla con il backspace.
                // Cifre accettate da ENTRAMBE le fonti: riga superiore
                // (VK_0..VK_9 = 48..57) e tastierino numerico (VK_NUMPAD0..
                // VK_NUMPAD9 = 96..105, intervallo SEPARATO: senza questo ramo
                // "103" digitato col tastierino non arrivava a zappare).
                in KeyEvent.VK_0..KeyEvent.VK_9, in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 -> {
                    val base = if (event.keyCode in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9)
                        KeyEvent.VK_NUMPAD0 else KeyEvent.VK_0
                    zapDigit('0' + (event.keyCode - base))
                    true
                }
                // Invio: la costante VK_NUMPAD_ENTER NON esiste in
                // java.awt.event.KeyEvent (errore di risoluzione in
                // compilazione): l'Invio del tastierino arriva gia' come
                // VK_ENTER (10) con keyLocation = KEY_LOCATION_NUMPAD. Lo
                // conferma anche Compose (Key.desktop.kt: NumPadEnter =
                // Key(VK_ENTER, KEY_LOCATION_NUMPAD)). Un solo ramo copre
                // quindi entrambi i tasti Invio.
                KeyEvent.VK_ENTER -> {
                    resolveZap(); true
                }
                KeyEvent.VK_BACK_SPACE -> {
                    if (zapBuffer.isNotEmpty()) {
                        zapBuffer = zapBuffer.dropLast(1)
                        zapTimeoutJob?.cancel()
                        if (zapBuffer.isNotEmpty()) {
                            zapTimeoutJob = scope.launch { delay(1200); resolveZap() }
                        }
                    }
                    true
                }
                else -> false
            }
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
            state.dispose()
        }
    }

    // Layout SENZA sovrapposizioni: la superficie VLC è un componente AWT
    // "heavyweight" (finestra nativa con compose.layers.type=WINDOW) che copre
    // SEMPRE il contenuto Compose e assorbe tutti i click: barre e messaggi
    // vivono quindi FUORI dall'area video, sempre visibili e cliccabili
    // (niente overlay sopra il video). Barre in SLOT A ALTEZZA FISSA: il loro
    // show/hide è un cambio di alpha, MAI di layout, così la superficie video
    // non viene mai ridimensionata dall'auto-nascondimento (cura dello
    // "sfondo bianco", vedi note sulle barre più sotto).
    Column(Modifier.fillMaxSize().background(Color.Black)) {

        // ---- Top bar: back + canale/EPG + preferito + schermo intero --------
        // SLOT A ALTEZZA COSTANTE (cura definitiva dello "sfondo bianco senza
        // video" alla nascondita dei controlli). Prima le barre vivevano in
        // AnimatedVisibility: smontandole il layout cambiava, l'area video
        // tra le barre si allargava/restringeva e lo SwingPanel della
        // superficie vlcj (finestra nativa heavyweight) veniva RIDIMENSIONATO
        // decine di volte durante l'animazione: è il ridimensionamento
        // ripetuto a rompere il painting della superficie (il bianco
        // persistente a schermo intero). Ora le barre sono SEMPRE in
        // composizione nei loro slot e la scomparsa è solo visuale (alpha
        // animata): il video non cambia mai bounds durante show/hide, come
        // voleva la vecchia fix che "non ridimensionava il video". Il campo
        // visivo guadagnato nascondendo le barre resta nero (sfondo della
        // colonna), il video mantiene la sua dimensione.
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Prima di tutto il disegno della barra (sfondo incluso):
                    // l'alpha è solo render, zero effetti sul layout AWT.
                    .graphicsLayer { alpha = controlsAlpha }
                    .background(Color(0xFF0A0D14))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::exitPlayer) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        channel?.name.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            // Statistiche stream (feature "statistiche"): mentre
                            // sono attive questa riga mostra i dati tecnici al
                            // posto del programma EPG (stessa altezza, slot
                            // costante: difesa fix28).
                            showStats ->
                                formatStreamStats(streamStats) ?: "Statistiche non disponibili"

                            status is PlayerStatus.Buffering -> "Caricamento stream…"
                            status is PlayerStatus.Reconnecting -> {
                                val reconnecting = status as PlayerStatus.Reconnecting
                                "Riconnessione al canale… " +
                                    "(tentativo ${reconnecting.attempt} di ${reconnecting.maxAttempts})"
                            }

                            else -> epg.first
                                ?.let { "${formatHourRange(it.start, it.stop)} · ${it.title}" }
                                .orEmpty()
                        },
                        color = Color(0xFFAEB9C9),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        fontFamily = if (showStats) FontFamily.Monospace else null,
                    )
                    epg.first?.let { now ->
                        // Avanzamento del programma (feature 5): barra viva
                        // nella top bar, fuori dall'area video (vincolo
                        // superficie heavyweight, mai overlay sopra il flusso).
                        ProgrammeProgressBar(
                            programme = now,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                if (status is PlayerStatus.Buffering || status is PlayerStatus.Reconnecting) {
                    CircularProgressIndicator(color = AppPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                TextButton(
                    onClick = { showSchedule = !showSchedule },
                    enabled = schedule.isNotEmpty(),
                ) {
                    Text(
                        "Programmazione",
                        color = if (showSchedule && schedule.isNotEmpty()) AppPrimary else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                // Proporzioni video (porting Android): cicla Adatta → Zoom →
                // Riempi con feedback temporaneo nella riga dei suggerimenti.
                IconButton(onClick = {
                    val mode = state.cycleVideoResizeMode()
                    resizeHint = "Proporzioni: " + when (mode) {
                        VideoResizeMode.FIT -> "Adatta"
                        VideoResizeMode.ZOOM -> "Zoom"
                        VideoResizeMode.FILL -> "Riempi"
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.AspectRatio,
                        contentDescription = "Proporzioni video",
                        tint = Color.White,
                    )
                }
                // Tracce audio (porting Android): pulsante visibile solo sui
                // canali multilingua (la lista arriva dopo l'evento "playing"
                // e viene riletta all'apertura del menu); voci = tracce con
                // la traccia attiva in colore primario, come le durate del
                // timer attive nel menu accanto.
                if (audioTracks.size > 1) {
                    Box {
                        IconButton(onClick = {
                            // Rilettura immediata: lo stream può aver cambiato
                            // le tracce da quando è partito.
                            state.refreshAudioTracks()
                            showAudioMenu = true
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Audiotrack,
                                contentDescription = "Tracce audio",
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = showAudioMenu,
                            onDismissRequest = { showAudioMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Tracce audio",
                                        color = AppTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                            if (audioTracks.isEmpty()) {
                                // Stato vuoto difensivo: lo stream ha ritirato
                                // le tracce tra apertura menu e ricomposizione.
                                DropdownMenuItem(
                                    text = { Text("Nessuna traccia disponibile", color = AppTextSecondary) },
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                            audioTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            track.label,
                                            // Traccia attiva in colore primario,
                                            // come le durate attive del timer.
                                            color = if (track.id == currentAudioTrackId) AppPrimary
                                                else AppTextPrimary,
                                        )
                                    },
                                    onClick = {
                                        state.selectAudioTrack(track.id)
                                        showAudioMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                // Sottotitoli (gemello del menu audio, feature "sottotitoli"):
                // pulsante visibile se il flusso annuncia tracce SPU; l'icona è
                // piena e colorata mentre una traccia è selezionata. Il menu è
                // lo stesso schema dell'audio, con "Disattivati" al posto di
                // "Automatica".
                if (subtitleTracks.isNotEmpty()) {
                    Box {
                        IconButton(onClick = {
                            state.refreshSubtitleTracks()
                            showSubtitleMenu = true
                        }) {
                            Icon(
                                imageVector = if (currentSubtitleTrackId != null) Icons.Filled.ClosedCaption
                                    else Icons.Outlined.ClosedCaption,
                                contentDescription = "Sottotitoli",
                                tint = if (currentSubtitleTrackId != null) AppPrimary else Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = showSubtitleMenu,
                            onDismissRequest = { showSubtitleMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Sottotitoli",
                                        color = AppTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Disattivati",
                                        // Nessuna traccia attiva = voce Disattivati
                                        // evidenziata (onesto anche quando libVLC
                                        // non è riuscito a selezionare nulla).
                                        color = if (currentSubtitleTrackId == null) AppPrimary
                                            else AppTextPrimary,
                                    )
                                },
                                onClick = {
                                    state.selectSubtitleTrack(-1)
                                    showSubtitleMenu = false
                                },
                            )
                            subtitleTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            track.label,
                                            // Traccia attiva in colore primario,
                                            // come per le tracce audio.
                                            color = if (track.id == currentSubtitleTrackId) AppPrimary
                                                else AppTextPrimary,
                                        )
                                    },
                                    onClick = {
                                        state.selectSubtitleTrack(track.id)
                                        showSubtitleMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                // Statistiche stream (feature "statistiche"): toggle con il
                // pulsante o il tasto I; la riga sotto il nome canale mostra
                // codec/risoluzione/bitrate/perdite finché attive.
                IconButton(onClick = { showStats = !showStats }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Statistiche stream",
                        tint = if (showStats) AppPrimary else Color.White,
                    )
                }
                // Timer di spegnimento (porting Android): icona piena e colorata
                // mentre il timer è attivo, menu con durate e orario di stop.
                Box {
                    IconButton(onClick = { showSleepMenu = true }) {
                        Icon(
                            imageVector = if (sleepTimerEndsAt != null) Icons.Filled.Timer else Icons.Outlined.Timer,
                            contentDescription = "Timer di spegnimento",
                            tint = if (sleepTimerEndsAt != null) AppPrimary else Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = showSleepMenu,
                        onDismissRequest = { showSleepMenu = false },
                    ) {
                        sleepTimerEndsAt?.let { timestamp ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "La riproduzione si fermerà alle " +
                                            SimpleDateFormat("HH:mm", Locale.getDefault())
                                                .format(Date(timestamp)),
                                        color = AppPrimary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                        }
                        listOf(15, 30, 60).forEach { minutes ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "$minutes min",
                                        // Il modo attivo è evidenziato col colore
                                        // primario, come sul foglio Android.
                                        color = if (minutes == sleepTimerMinutes) AppPrimary
                                            else AppTextPrimary,
                                    )
                                },
                                onClick = {
                                    state.setSleepTimer(minutes)
                                    showSleepMenu = false
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Disattiva", color = AppTextSecondary) },
                            onClick = {
                                state.setSleepTimer(null)
                                showSleepMenu = false
                            },
                        )
                    }
                }
                IconButton(
                    onClick = state::toggleFavorite,
                    enabled = channel != null,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Preferito",
                        tint = if (isFavorite) AppFavorite else Color.White,
                    )
                }
                // Mini-player (feature 11): sposta la visione nella finestretta
                // sempre in primo piano e torna in Home; il player pieno si
                // chiude e il flusso prosegue nel mini con il suo player.
                IconButton(onClick = {
                    val id = channel?.id ?: return@IconButton
                    navigator.miniPlayer?.enter(id)
                    exitPlayer()
                }) {
                    Icon(
                        imageVector = Icons.Filled.PictureInPictureAlt,
                        contentDescription = "Mini-player",
                        tint = Color.White,
                    )
                }
                // Zapping numerico (feature "zapping numerico"): le cifre
                // digitate compaiono in un chip nella barra (fuori dall'area
                // video: la superficie heavyweight coprirebbe ogni overlay
                // Compose) finché il buffer non risolve o scade.
                if (zapBuffer.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppPrimary.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = zapBuffer,
                            color = AppPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = { toggleFullscreen() }) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Esci da schermo intero" else "Schermo intero",
                        tint = Color.White,
                    )
                }
            }
            // Da nascosta la barra resta nell'hit testing (l'alpha non tocca i
            // click): questo velo trasparente consuma i tap così i pulsanti
            // invisibili non sono cliccabili (AnimatedVisibility prima li
            // smontava del tutto). Un click qui conta comunque come attività:
            // l'AWT listener riporta le barre visibili.
            if (!controlsVisible) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }

        // ---- Area video + pannello programmazione ---------------------------
        // Il pannello EPG vive DI LATO al video (mai sopra): la superficie VLC
        // e' un Canvas AWT heavyweight che coprirebbe qualsiasi overlay Compose
        // e ne assorbirebbe i click. Qui video e pannello si dividono la larghezza.
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // In errore la superficie esce dalla composizione: essendo un
            // componente AWT heavyweight coprirebbe il messaggio sottostante.
            // NOTA: SwingPanel<T : Component> NON accetta un factory nullable ->
            // il videoComponent() nullable va gestito FUORI dal factory, altrimenti
            // il compilatore non inferisce T ("Cannot infer type for type parameter T").
            if (status !is PlayerStatus.Error) {
                state.videoComponent()?.let { video ->
                    androidx.compose.ui.awt.SwingPanel<java.awt.Container>(
                        // NERO: il default di SwingPanel e' Color.White e il
                        // viewHolder interop lo dipinge; durante il reflow
                        // hide/show in fullscreen le dirty-region Swing
                        // calcolate sui bounds vecchi non ridipingono la
                        // nuova area ed emergono i pixel BIANCHI del holder
                        // (il "schermo bianco" persistente). Un holder nero
                        // rende il caso peggiore nero coerente col tema.
                        background = Color.Black,
                        factory = { video },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            (status as? PlayerStatus.Error)?.let { error ->
                Box(Modifier.fillMaxSize().background(Color(0xD9070A10)), Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = AppError, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(error.message, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = state::retry) { Text("Riprova") }
                            OutlinedButton(onClick = ::exitPlayer) { Text("Indietro") }
                        }
                    }
                }
            }
        }

            // Pannello programmazione: si apre/chiude dal pulsante in alto;
            // presenza accanto al video, non sovrapposta (vedi nota sopra).
            AnimatedVisibility(
                visible = showSchedule && schedule.isNotEmpty(),
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                EpgSchedulePanel(
                    schedule = schedule,
                    channelName = channel?.name.orEmpty(),
                    reminders = reminderKeys,
                    onToggleReminder = ::toggleReminder,
                    onClose = { showSchedule = false },
                )
            }
        }

        // ---- Controlli inferiori --------------------------------------------
        // Stesso slot a altezza costante della top bar: sempre in
        // composizione, scomparsa solo visuale (alpha), superficie video mai
        // ridimensionata alla nascondita. Auto-hide in ogni modalità.
        Box(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = controlsAlpha }
                    .background(Color(0xFF0A0D14))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { state.switchChannel(-1) }) {
                        Icon(Icons.Filled.SkipPrevious, "Canale precedente", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = state::togglePlayPause) {
                        Icon(
                            imageVector = if (status is PlayerStatus.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pausa",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    IconButton(onClick = { state.switchChannel(+1) }) {
                        Icon(Icons.Filled.SkipNext, "Canale successivo", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                // Riga suggerimenti: elenco dei tasti rapidi, oppure il volume
                // temporaneo (↑/↓ o rotella sul video) o le proporzioni appena
                // cambiate.
                Text(
                    text = volumeHint
                        ?: resizeHint
                        ?: "Spazio play/pausa · ←→ canale · ↑ ↓ o rotella volume · cifre canale · " +
                            "F schermo intero · I statistiche · Esc indietro",
                    color = Color(0xFF6B7688),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // Velo anti-click sulla barra nascosta: vedi top bar.
            if (!controlsVisible) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
    }
}

/**
 * Cursore "invisibile": immagine 1×1 trasparente, usato per nascondere il
 * puntatore sul video quando i controlli si nascondono a schermo intero.
 */
private fun blankCursor(): Cursor =
    Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        Point(0, 0),
        "blank",
    )

// ---------------------------------------------------------------------------
// Impostazioni
// ---------------------------------------------------------------------------

/**
 * Pacchetto profilo letto da file e in attesa di conferma (feature
 * export/import a livello di profilo): il dialog chiede all'utente se
 * sovrascrivere il profilo omonimo; il testo resta in memoria perché
 * nel frattempo il file potrebbe essere spostato o cancellato.
 */
private data class PendingProfileImport(
    val file: File,
    val text: String,
    val bundle: ProfileExportBundle,
)

@Composable
private fun DesktopSettingsScreen(
    deps: DesktopDependencies,
    navigator: DesktopNavigator,
) {
    val scope = rememberCoroutineScope()
    val settings by deps.prefs.settings.collectAsState(initial = AppSettings())
    val playlistState by deps.playlistRepository.state.collectAsState()
    val epgStatus by deps.epgRepository.status.collectAsState()
    val profiles by deps.prefs.sourceProfiles.collectAsState(initial = emptyList())
    val activeProfileId by deps.prefs.activeProfileId.collectAsState(initial = null)

    // Feature "nascondi canali": canali nascosti della sorgente attiva,
    // risolti con la playlist caricata (nome visibile) e ripristinabili qui.
    val hiddenMap by deps.prefs.hiddenChannels.collectAsState(initial = emptyMap())
    val hiddenChannels = remember(hiddenMap, activeProfileId, playlistState) {
        val hidden = hiddenMap[activeProfileId ?: UserPrefsStore.DEFAULT_HIDDEN_SCOPE].orEmpty()
        ((playlistState as? PlaylistState.Success)?.channels ?: emptyList())
            .filter { it.id in hidden }
            .sortedBy { it.name.lowercase() }
    }

    var playlistUrl by remember(settings.playlistUrl) { mutableStateOf(settings.playlistUrl) }
    var epgUrl by remember(settings.epgUrl) { mutableStateOf(settings.epgUrl) }
    var feedback by remember { mutableStateOf<String?>(null) }

    // Form dei profili di sorgente (feature "profilazione della lista"):
    // aggiunta di un nuovo profilo o modifica di quello selezionato con
    // "Modifica" (editingProfileId = profilo in modifica, null = aggiunta).
    var profileName by remember { mutableStateOf("") }
    var profilePlaylistUrl by remember { mutableStateOf("") }
    var profileEpgUrl by remember { mutableStateOf("") }
    var editingProfileId by remember { mutableStateOf<String?>(null) }

    fun chooseFile(
        save: Boolean,
        dialogTitle: String = if (save) "Esporta playlist M3U" else "Importa playlist o profilo",
        suggestedName: String? = null,
    ): File? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            this.dialogTitle = dialogTitle
            if (save) {
                selectedFile = File(suggestedName ?: "opentv_canali.m3u")
            } else {
                // Il filtro accetta anche i pacchetti profilo (.opentv): il
                // riconoscimento vero avviene sul CONTENUTO del file (header
                // #OPENTV-PROFILE), non sull'estensione.
                fileFilter = FileNameExtensionFilter(
                    "Playlist e profili OpenTV",
                    "m3u", "m3u8", "txt", "opentv",
                )
            }
        }
        val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    // Feature export/import a livello di profilo: pacchetto profilo in
    // attesa di conferma quando il nome coincide con un profilo esistente
    // (il dialog chiede sovrascrivi/annulla prima di toccare qualsiasi cosa).
    var pendingProfileImport by remember { mutableStateOf<PendingProfileImport?>(null) }

    fun applyProfileImport(pending: PendingProfileImport) {
        scope.launch {
            // Sovrascrittura per NOME con id mantenuto (se omonimo): la
            // selezione attiva resta valida e i canali del file diventano
            // la lista caricata (sorgente locale, copia di cache inclusa).
            val profile = deps.prefs.upsertSourceProfileByName(
                pending.bundle.profile.name,
                pending.bundle.profile.playlistUrl,
                pending.bundle.profile.epgUrl,
            )
            deps.prefs.setActiveProfileId(profile.id)
            deps.playlistRepository.importPlaylist(
                ProfileCodec.m3uBody(pending.text),
                sourceId = pending.file.absolutePath,
            )
                .onSuccess { count ->
                    // Guida ricaricata con la catena appena cambiata
                    // (profilo attivato + eventuale guida incorporata nel
                    // corpo del pacchetto): risoluzione unica con Home.
                    deps.epgRepository.load(
                        SourceResolver.resolveEpgChain(
                            settings = deps.prefs.settings.first(),
                            profiles = deps.prefs.sourceProfiles.first(),
                            activeProfileId = deps.prefs.activeProfileId.first(),
                            embeddedFromPlaylist = deps.playlistRepository.embeddedEpgChain.value,
                        ),
                    )
                    feedback = "Profilo «${profile.name}» importato: $count canali."
                }
                .onFailure { feedback = it.message }
            pendingProfileImport = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge, color = AppTextPrimary)
        Spacer(Modifier.height(16.dp))

        SettingSection("Playlist") {
            // Paese della lista canali: come su Android è la via principale
            // per cambiare lista e sta SOPRA al campo URL (che resta per le
            // liste personali). Il menu apre i paesi supportati (bandierina
            // PNG + nome localizzato) più la voce automatica (paese del
            // dispositivo). Fix19: la scelta viene applicata SUBITO nella
            // preferenza dedicata (la stessa della Home) e NON riempie il
            // campo URL qui sotto, che significa solo "lista personale".
            Text(
                "Paese della lista canali",
                color = AppTextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Scegli un paese: la lista iptv-org di quel paese viene " +
                    "caricata subito, con la guida TV dello stesso paese. La voce " +
                    "«Automatica» usa il paese del dispositivo. La selezione è la " +
                    "stessa visibile nella Home e non tocca il campo URL qui sotto. " +
                    "Per una lista personale incolla l'URL nel campo e premi «Salva».",
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            CountryDropdown(
                currentCode = settings.countryCode,
                autoLabel = COUNTRY_AUTO_LABEL,
                onSelect = { code ->
                    // Applicazione immediata (come nella Home): preferenza
                    // dedicata, campo URL svuotato (significa solo "lista
                    // personale"), ricaricamento lista e guida TV.
                    scope.launch {
                        deps.prefs.setLocalPlaylistUri(null)
                        deps.prefs.setPlaylistUrl("")
                        deps.prefs.setCountryCode(code)
                        deps.playlistRepository.refresh(force = true)
                        // Risoluzione unica con i profili (SourceResolver): con
                        // un profilo attivo la guida resta quella del profilo.
                        // fix35: catena completa (guida incorporata nella
                        // playlist + storica), la catena è fresca perché il
                        // refresh(force = true) qui sopra ha già riparato la lista.
                        deps.epgRepository.load(
                            SourceResolver.resolveEpgChain(
                                settings = deps.prefs.settings.first(),
                                profiles = deps.prefs.sourceProfiles.first(),
                                activeProfileId = deps.prefs.activeProfileId.first(),
                                embeddedFromPlaylist = deps.playlistRepository.embeddedEpgChain.value,
                            ),
                        )
                        feedback = "Paese aggiornato."
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            LabeledField(
                label = "URL playlist M3U",
                value = playlistUrl,
                placeholder = "Automatico (paese del dispositivo)",
                onValueChange = { playlistUrl = it },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Lascia vuoto per la lista automatica: viene caricata la " +
                    "playlist iptv-org del paese scelto nel selettore (o del paese " +
                    "del dispositivo se nessun paese è stato scelto). Per una lista " +
                    "personale incolla qui l'URL, poi premi «Salva»: un URL " +
                    "personale ha sempre la precedenza sul paese scelto.",
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            LabeledField(
                label = "URL guida TV XMLTV (opzionale)",
                value = epgUrl,
                placeholder = "https://esempio.it/guida.xml.gz",
                onValueChange = { epgUrl = it },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Vuota = guida automatica: dalla playlist iptv-org viene " +
                    "caricata la guida per paese di iptv-epg.org (stessi id dei tvg-id, " +
                    "un solo download per tutta la lista).",
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            // I vecchi chip "guide rapide per paese" sono rimossi (come su
            // Android): ridondanti con la modalità automatica, che deriva la
            // guida per paese dalla playlist iptv-org; un URL personale resta
            // comunque impostabile nel campo qui sopra.
            Spacer(Modifier.height(10.dp))
            Text(
                text = when (val status = epgStatus) {
                    EpgStatus.Idle -> "Guida TV non attiva: imposta un URL XMLTV o usa una playlist iptv-org."
                    EpgStatus.Loading -> "Carico la guida TV\u2026"
                    is EpgStatus.Ready -> "Guida TV attiva: ${status.programmeCount} programmi in memoria."
                    is EpgStatus.Error -> "Errore guida TV: ${status.message}"
                },
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    scope.launch {
                        deps.prefs.setLocalPlaylistUri(null)
                        deps.prefs.setPlaylistUrl(playlistUrl)
                        // Fix19: un URL personale prende il posto del paese
                        // scelto nei selettori; con il campo vuoto il paese
                        // scelto resta attivo e non viene sovrascritto.
                        if (playlistUrl.isNotBlank()) deps.prefs.setCountryCode(null)
                        deps.prefs.setEpgUrl(epgUrl)
                        deps.playlistRepository.refresh(force = true)
                        // Ricarica con la catena effettiva risolta UNIFORMEMENTE
                        // con i profili di sorgente (SourceResolver): con un
                        // profilo attivo i campi qui sopra non toccano la guida;
                        // fix35 aggiunge la guida incorporata nella playlist.
                        deps.epgRepository.load(
                            SourceResolver.resolveEpgChain(
                                settings = deps.prefs.settings.first(),
                                profiles = deps.prefs.sourceProfiles.first(),
                                activeProfileId = deps.prefs.activeProfileId.first(),
                                embeddedFromPlaylist = deps.playlistRepository.embeddedEpgChain.value,
                            ),
                        )
                        feedback = "Impostazioni salvate."
                    }
                }) { Text("Salva") }
                OutlinedButton(onClick = {
                    scope.launch {
                        deps.prefs.setLocalPlaylistUri(null)
                        // Campo vuoto + paese azzerato = modalità automatica
                        // (paese del dispositivo): stessa semantica descritta
                        // dalle note e dalla voce "Automatica" dei selettori.
                        deps.prefs.setPlaylistUrl("")
                        deps.prefs.setCountryCode(null)
                        deps.playlistRepository.refresh(force = true)
                        feedback = "Ripristinata la lista automatica (paese del dispositivo)."
                    }
                }) { Text("Ripristina predefinito") }
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingSection("Esporta e importa lista") {
            Text(
                text = "Canali caricati: " + ((playlistState as? it.fast4x.opentv.data.model.PlaylistState.Success)?.channels?.size ?: 0),
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        deps.playlistRepository.exportPlaylistText()
                            .onSuccess { export ->
                                val file = chooseFile(save = true)
                                if (file != null) {
                                    runCatching { file.writeText(export.m3uText) }
                                        .onSuccess { feedback = "Esportati ${export.channelCount} canali." }
                                        .onFailure { feedback = "Impossibile scrivere il file." }
                                }
                            }
                            .onFailure { feedback = it.message }
                    }
                }) { Text("Esporta…") }

                OutlinedButton(onClick = {
                    scope.launch {
                        val file = chooseFile(save = false)
                        if (file != null) {
                            val text = runCatching { file.readText() }.getOrNull()
                            if (text == null) {
                                feedback = "Impossibile leggere il file selezionato"
                            } else if (ProfileCodec.isBundle(text)) {
                                // Pacchetto profilo OpenTV (feature profili):
                                // nome + URL sorgente + canali. Con un profilo
                                // omonimo già presente si chiede conferma,
                                // altrimenti si applica subito.
                                val bundle = ProfileCodec.decode(text)
                                if (bundle == null) {
                                    feedback = "File profilo OpenTV non valido"
                                } else {
                                    val collision = profiles.any {
                                        it.name.trim()
                                            .equals(bundle.profile.name.trim(), ignoreCase = true)
                                    }
                                    val pending = PendingProfileImport(file, text, bundle)
                                    if (collision) {
                                        pendingProfileImport = pending
                                    } else {
                                        applyProfileImport(pending)
                                    }
                                }
                            } else {
                                deps.playlistRepository.importPlaylist(text, sourceId = file.absolutePath)
                                    .onSuccess { feedback = "Importati $it canali." }
                                    .onFailure { feedback = it.message }
                            }
                        }
                    }
                }) { Text("Importa da file…") }
            }
        }

        // Profili di sorgente (feature "profilazione della lista"): coppie
        // playlist+guida salvate dall'utente e selezionabili dal menu sorgente
        // della Home e da qui. La sorgente automatica iptv-org resta sempre
        // disponibile; il profilo attivo è evidenziato e bypassa URL/paese
        // della sezione Playlist qui sopra.
        SettingSection("Profili di sorgente") {
            Text(
                text = "Salva le tue coppie playlist+guida e cambiale dal menu " +
                    "sorgente della Home. Con un profilo attivo la sorgente " +
                    "automatica iptv-org (sezione qui sopra) viene bypassata; " +
                    "puoi sempre tornare con la voce «IPTV-ORG (automatica)».",
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = "Ancora nessun profilo: creane uno qui sotto con un " +
                        "nome e l'URL della playlist.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // fix34: elenco RAGGRUPPATO con etichetta dedicata, come su Android:
            // prima i profili e il form erano mescolati nello stesso flusso.
            Text(
                text = "Profili salvati",
                color = AppPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            profiles.forEach { profile ->
                val active = profile.id == activeProfileId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) AppPrimary.copy(alpha = 0.14f) else AppSurfaceHigh,
                        )
                        .border(
                            width = 1.dp,
                            color = if (active) AppPrimary else AppSurfaceHigh,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            Text(
                                text = "Attiva",
                                color = AppPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(AppPrimary.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // fix34: valori etichettati (prima: URL grigio scuro su
                    // sfondo ancora piu' scuro, quasi illeggibili).
                    ProfileValueRow(
                        label = "Lista",
                        value = profile.playlistUrl,
                    )
                    ProfileValueRow(
                        label = "Guida",
                        value = profile.epgUrl.ifBlank { "Automatica dalla playlist (iptv-org)" },
                        muted = profile.epgUrl.isBlank(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!active) {
                            Button(onClick = {
                                scope.launch {
                                    deps.prefs.setLocalPlaylistUri(null)
                                    deps.prefs.setActiveProfileId(profile.id)
                                    deps.playlistRepository.refresh(force = true)
                                    feedback = "Profilo «${profile.name}» attivato."
                                }
                            }) { Text("Usa") }
                        }
                        OutlinedButton(onClick = {
                            editingProfileId = profile.id
                            profileName = profile.name
                            profilePlaylistUrl = profile.playlistUrl
                            profileEpgUrl = profile.epgUrl
                        }) { Text("Modifica") }
                        // Feature profili: esporta QUESTO profilo con i canali
                        // della sua playlist (pacchetto .opentv riassorbibile
                        // dall'import generico).
                        OutlinedButton(onClick = {
                            scope.launch {
                                deps.playlistRepository.exportProfileBundle(profile)
                                    .onSuccess { bundle ->
                                        val file = chooseFile(
                                            save = true,
                                            dialogTitle = "Esporta profilo OpenTV",
                                            suggestedName = ProfileCodec.suggestedFileName(bundle.profile.name),
                                        )
                                        if (file != null) {
                                            runCatching { file.writeText(ProfileCodec.encode(bundle)) }
                                                .onSuccess {
                                                    feedback = "Profilo esportato: ${bundle.channels.size} canali."
                                                }
                                                .onFailure { feedback = "Impossibile scrivere il file." }
                                        }
                                    }
                                    .onFailure { feedback = it.message }
                            }
                        }) { Text("Esporta profilo…") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                deps.prefs.removeSourceProfile(profile.id)
                                deps.playlistRepository.refresh(force = true)
                                if (editingProfileId == profile.id) {
                                    editingProfileId = null
                                    profileName = ""
                                    profilePlaylistUrl = ""
                                    profileEpgUrl = ""
                                }
                                feedback = "Profilo rimosso."
                            }
                        }) { Text("Elimina") }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Form aggiunta/modifica: nome + URL playlist obbligatori (una
            // sorgente senza lista non è una sorgente), guida opzionale
            // (vuota = automatica dalla playlist iptv-org del profilo).
            // fix34: blocco VISUALMENTE SEPARATO dall'elenco (sfondo più
            // scuro + bordo dedicato) con un titolo che dice cosa si sta
            // facendo: prima i tre campi sembravano tre impostazioni
            // qualunque e non si capiva dove finiva l'elenco.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppBackground)
                    .border(
                        width = 1.dp,
                        color = AppSurfaceHigh,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = if (editingProfileId == null) "Nuovo profilo" else "Modifica profilo",
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                LabeledField(
                    label = "Nome del profilo",
                    value = profileName,
                    placeholder = "Es. Lista di casa",
                    onValueChange = { profileName = it },
                )
                Spacer(Modifier.height(4.dp))
                LabeledField(
                    label = "URL playlist M3U",
                    value = profilePlaylistUrl,
                    placeholder = "https://esempio.it/playlist.m3u",
                    onValueChange = { profilePlaylistUrl = it },
                )
                Spacer(Modifier.height(4.dp))
                LabeledField(
                    label = "URL guida TV XMLTV (opzionale)",
                    value = profileEpgUrl,
                    placeholder = "https://esempio.it/guida.xml.gz",
                    onValueChange = { profileEpgUrl = it },
                )
                val formValid = profileName.isNotBlank() && profilePlaylistUrl.isNotBlank()
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val wasEditing = editingProfileId != null
                            val editingId = editingProfileId
                            scope.launch {
                                if (editingId == null) {
                                    deps.prefs.addSourceProfile(profileName, profilePlaylistUrl, profileEpgUrl)
                                } else {
                                    deps.prefs.updateSourceProfile(
                                        SourceProfile(editingId, profileName, profilePlaylistUrl, profileEpgUrl),
                                    )
                                }
                                editingProfileId = null
                                profileName = ""
                                profilePlaylistUrl = ""
                                profileEpgUrl = ""
                                feedback = if (wasEditing) "Profilo aggiornato." else "Profilo aggiunto."
                            }
                        },
                        enabled = formValid,
                    ) {
                        Text(if (editingProfileId == null) "Aggiungi profilo" else "Salva modifiche")
                    }
                    if (editingProfileId != null) {
                        OutlinedButton(onClick = {
                            editingProfileId = null
                            profileName = ""
                            profilePlaylistUrl = ""
                            profileEpgUrl = ""
                        }) { Text("Annulla") }
                    }
                }
                if (!formValid && (profileName.isNotBlank() || profilePlaylistUrl.isNotBlank())) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Nome e URL playlist sono obbligatori.",
                        color = AppTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Canali nascosti (feature "nascondi canali"): gestione per la
        // sorgente attiva (profilo attivo o automatica). Lì nascondi con il
        // clic destro + dialog; qui ripristini uno a uno o tutti insieme.
        SettingSection("Canali nascosti") {
            Text(
                text = "I canali nascosti della sorgente attiva spariscono da " +
                    "tutti gli elenchi (Home, ricerca, gruppi, preferiti, guida TV). " +
                    "La playlist originale resta intatta: ripristinali quando vuoi.",
                color = AppTextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (hiddenChannels.isEmpty()) {
                Text(
                    text = "Nessun canale nascosto: nella Home clicca col tasto " +
                        "destro su un canale e conferma «Nascondi».",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                hiddenChannels.forEach { channel ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = channel.name,
                            color = AppTextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = {
                            scope.launch { deps.prefs.unhideChannel(channel.id) }
                            feedback = "Canale ripristinato."
                        }) { Text("Ripristina") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    scope.launch { deps.prefs.unhideAllChannels() }
                    feedback = "Tutti i canali nascosti sono stati ripristinati."
                }) { Text("Ripristina tutti") }
            }
        }

        feedback?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = AppAccent, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = AppSurfaceHigh)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Riproduzione desktop via libVLC (VLC Media Player installato sul sistema).",
            color = AppTextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = navigator::goHome) { Text("Torna alla Home") }
    }

    // Dialog di conferma (feature profili): l'import di un pacchetto con il
    // nome di un profilo già presente sovrascrive profilo e lista canali
    // solo dopo un consenso esplicito.
    pendingProfileImport?.let { pending ->
        Dialog(onDismissRequest = { pendingProfileImport = null }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                contentColor = AppTextPrimary,
                modifier = Modifier.padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Profilo già presente",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTextPrimary,
                    )
                    Text(
                        text = "Esiste già un profilo chiamato «${pending.bundle.profile.name}». " +
                            "Vuoi sovrascriverlo con quello del file " +
                            "(${pending.bundle.channels.size} canali)? Il profilo verrà " +
                            "attivato e i suoi canali caricati.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { applyProfileImport(pending) }) { Text("Sovrascrivi") }
                        OutlinedButton(onClick = { pendingProfileImport = null }) { Text("Annulla") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AppTextPrimary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * Riga "etichetta: valore" di una scheda profilo (fix34): le coppie
 * playlist/guida prima erano righe di testo senza etichette su uno sfondo
 * più scuro del contenitore, difficili da leggere. L'etichetta ha larghezza
 * fissa così i valori delle schede si allineano verticalmente.
 */
@Composable
private fun ProfileValueRow(
    label: String,
    value: String,
    muted: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = AppTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            color = if (muted) AppTextSecondary else AppTextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
) {
    Column {
        Text(label, color = AppTextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // Suggerimento dentro il campo vuoto (es. "Automatico (paese del
            // dispositivo)"): descrive il comportamento SENZA valorizzare il
            // campo, così "Salva" non persiste alcun URL implicito.
            placeholder = if (placeholder.isNullOrBlank()) null else {
                { Text(placeholder) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Programmazione del canale (EPG -1h → +24h)
// ---------------------------------------------------------------------------

/** Righe del pannello programmazione: intestazione di giorno o programma. */
private sealed interface ScheduleRow {
    data class Header(val label: String) : ScheduleRow
    data class Item(val programme: Programme, val isNow: Boolean) : ScheduleRow
}

/** Raggruppa i programmi per giorno civile: "Oggi", "Domani", poi la data. */
private fun buildScheduleRows(schedule: List<Programme>): List<ScheduleRow> {
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
                    today -> "Oggi"
                    tomorrow -> "Domani"
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
 * Barra di avanzamento del programma in onda (feature 5, porting Android):
 * riempimento proporzionale ai minuti trascorsi e, a destra, i minuti che
 * mancano alla fine. Il tick EPG dello stato cambia programma solo al
 * confine (e lo StateFlow non riemette coppie uguali), quindi la barra
 * possiede un suo ritmo interno: ricalcola ogni 30 secondi. Il remember
 * chiavato su [programme] riporta il riempimento al valore corretto al
 * cambio programma, senza attendere il primo tick.
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
                .height(3.dp)
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
                text = "Ancora $remainingMin min",
                color = Color(0xFFAEB9C9),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * Pannello affiancato al video con la programmazione del canale: sezioni per
 * giorno, programma in onda evidenziato e portato in vista all'apertura.
 * Ogni programma futuro ha il campanello "avvisami quando inizia" (feature
 * reminder): lo stato armato arriva dal DataStore tramite [reminders], la
 * notifica la spara lo scheduler a livello app. Etichette in italiano,
 * coerenti con il resto della UI desktop.
 */
@Composable
private fun EpgSchedulePanel(
    schedule: List<Programme>,
    channelName: String,
    reminders: Set<String>,
    onToggleReminder: (Programme) -> Unit,
    onClose: () -> Unit,
) {
    val rows = remember(schedule) { buildScheduleRows(schedule) }
    val listState = rememberLazyListState()
    val nowIndex = rows.indexOfFirst { it is ScheduleRow.Item && it.isNow }

    // All'apertura porta in vista il programma in onda, con una riga di
    // contesto sopra se possibile.
    LaunchedEffect(rows) {
        if (nowIndex > 1) listState.scrollToItem(nowIndex - 1)
    }

    Column(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(Color(0xFF0A0D14)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Programmazione",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (channelName.isNotEmpty()) {
                    Text(
                        channelName,
                        color = Color(0xFF6B7688),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Chiudi programmazione", tint = Color.White)
            }
        }
        HorizontalDivider(color = Color(0xFF1D2736))

        if (schedule.isEmpty()) {
            Text(
                "Nessun programma in guida per questo canale.",
                color = Color(0xFF6B7688),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(rows.size) { index ->
                    when (val row = rows[index]) {
                        is ScheduleRow.Header -> Text(
                            text = row.label.replaceFirstChar { it.uppercase() },
                            color = AppPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )

                        is ScheduleRow.Item -> {
                            val now = System.currentTimeMillis()
                            ScheduleItemRow(
                                row = row,
                                remindable = row.programme.start > now,
                                // La chiave del promemoria coincide con quella
                                // che UserPrefsStore usa per (canale, inizio).
                                reminderArmed = ProgrammeReminder(
                                    channelId = row.programme.channelId,
                                    start = row.programme.start,
                                    title = row.programme.title,
                                ).key() in reminders,
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
 * programmi futuri mostrano il campanello "avvisami" (feature reminder);
 * quello in onda non è remindabile (già iniziato) e non lo mostra.
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
            .background(if (isCurrent) AppPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        "In onda",
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
            // colorato quando il promemoria è armato. La notifica arriva dallo
            // scheduler a livello app, qui si arma/disarma solo la voce.
            IconButton(
                onClick = onToggleReminder,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (reminderArmed) Icons.Filled.Notifications
                        else Icons.Outlined.Notifications,
                    contentDescription = if (reminderArmed) "Rimuovi promemoria"
                        else "Avvisami quando inizia",
                    tint = if (reminderArmed) AppPrimary else Color(0xFF6B7688),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Riga delle statistiche stream (feature "statistiche"): codice video,
 * risoluzione, fps, codec e bitrate audio, bitrate di demux e perdite
 * segnalate da libVLC. Null se le statistiche non sono ancora disponibili
 * (stream in apertura): la UI mostra un messaggio neutro.
 */
private fun formatStreamStats(stats: StreamStats?): String? {
    stats ?: return null
    val parts = buildList {
        stats.videoCodec?.let { video ->
            val res = if (stats.videoWidth != null && stats.videoHeight != null) {
                " ${stats.videoWidth}×${stats.videoHeight}"
            } else {
                ""
            }
            val fps = stats.videoFps?.let { "@${it.roundToInt()}" }.orEmpty()
            add("$video$res$fps")
        }
        stats.audioCodec?.let { audio ->
            val kbps = stats.audioBitrateKbps?.let { " ${it} kb/s" }.orEmpty()
            add("$audio$kbps")
        }
        if (stats.demuxBitrateKbps > 0f) {
            add(String.format(Locale.getDefault(), "%.1f Mb/s", stats.demuxBitrateKbps / 1000f))
        }
        val lost = buildList {
            if (stats.picturesLost > 0) add("${stats.picturesLost} img")
            if (stats.audioBuffersLost > 0) add("${stats.audioBuffersLost} buf")
            if (stats.discontinuities > 0) add("${stats.discontinuities} disc")
            if (stats.corrupted > 0) add("${stats.corrupted} err")
        }
        add(if (lost.isEmpty()) "perdita: 0" else "persi: ${lost.joinToString(", ")}")
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}
