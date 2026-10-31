package it.fast4x.opentv.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.fast4x.opentv.R
import it.fast4x.opentv.openTvApp
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.data.model.Channel
import it.fast4x.opentv.data.model.GroupInfo
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.model.UNCATEGORIZED_GROUP
import it.fast4x.opentv.ui.components.ChannelCard
import it.fast4x.opentv.ui.components.CountryMenuItems
import it.fast4x.opentv.ui.components.countryDisplayName
import it.fast4x.opentv.ui.components.countryOptions
import it.fast4x.opentv.ui.components.EmptyMessage
import it.fast4x.opentv.ui.components.ErrorState
import it.fast4x.opentv.ui.components.InfoBanner
import it.fast4x.opentv.ui.components.LoadingState
import it.fast4x.opentv.ui.components.SectionHeader
import it.fast4x.opentv.ui.components.tvFocusHighlight
import it.fast4x.opentv.ui.theme.AppBackground
import it.fast4x.opentv.ui.theme.AppFavorite
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppSurface
import it.fast4x.opentv.ui.theme.AppTextPrimary
import it.fast4x.opentv.ui.theme.AppTextSecondary
import it.fast4x.opentv.util.isTvDevice

/**
 * Home dell'app: righe orizzontali stile Leanback (Ultimi visti, Preferiti,
 * canali raggruppati per iniziale) oppure griglia di risultati in modalità
 * ricerca o filtro per gruppo (genere).
 */
@Composable
fun HomeScreen(
    onOpenChannel: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val context = LocalContext.current
    val isTv = rememberTvMode()
    val app = context.openTvApp

    val viewModel: HomeViewModel = viewModel {
        HomeViewModel(
            playlistRepository = app.playlistRepository,
            prefs = app.prefs,
            epgRepository = app.epgRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Il badge genere è cliccabile solo dove non serve il telecomando: su TV
    // resta decorativo per non aggiungere target di focus alla navigazione D-pad
    // (il filtro per genere resta comunque disponibile nella riga di chip).
    val onGroupBadgeClick: ((String) -> Unit)? =
        if (isTv) null else { group -> viewModel.onGroupChange(group) }

    // Feature "nascondi canali": card in attesa di conferma. Come per il badge
    // genere, su TV l'affordance non esiste (nessun target di focus in più);
    // altrove il tap sull'icona NON nasconde mai direttamente: apre il dialog.
    var channelToHide by remember { mutableStateOf<Channel?>(null) }
    val onHideCard: ((Channel) -> Unit)? =
        if (isTv) null else { channel -> channelToHide = channel }

    var searchActive by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .safeDrawingPadding(),
    ) {
        HomeTopBar(
            searchActive = searchActive,
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearchClick = { searchActive = true },
            onCloseSearch = {
                searchActive = false
                viewModel.onQueryChange("")
            },
            onOpenSettings = onOpenSettings,
            onOpenGuide = onOpenGuide,
            onRefresh = viewModel::refresh,
        )

        // Riga sorgente (feature "profilazione della lista"): menu dei profili
        // di sorgente (IPTV-ORG automatica + profili utente) e selettore paese.
        // Il paese serve solo alla sorgente automatica: con un profilo attivo
        // il selettore paese è disabilitato (la lista del profilo è autonoma).
        val currentCountry by viewModel.currentCountry.collectAsStateWithLifecycle()
        val profiles by viewModel.sourceProfiles.collectAsStateWithLifecycle()
        val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()

        // Titolo della riga Preferiti (feature "preferiti per profilo"): con
        // un profilo attivo il nome dice a colpo d'occhio a quale lista
        // appartiene l'elenco mostrato (ogni sorgente ha i suoi preferiti).
        val favoritesTitle = profiles
            .firstOrNull { it.id == activeProfileId }
            ?.let { stringResource(R.string.section_favorites_profile, it.name) }
            ?: stringResource(R.string.section_favorites)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourcePickerButton(
                profiles = profiles,
                activeProfileId = activeProfileId,
                onSelect = viewModel::onProfileChange,
                onManage = onOpenSettings,
                focusMenuOnOpen = rememberTvMode(),
                modifier = Modifier.padding(start = 12.dp),
            )
            CountryPickerButton(
                currentCode = currentCountry,
                onSelect = viewModel::onCountryChange,
                focusMenuOnOpen = rememberTvMode(),
                enabled = activeProfileId == null,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        // Filtro canali: chip stella "solo preferiti", chip "solo con guida
        // TV" e riga di chip per gruppo (genere) scorrevole. La riga esiste
        // anche senza gruppi quando ci sono preferiti o canali coperti dalla
        // guida, così i commutatori sono sempre raggiungibili.
        if (state.groups.isNotEmpty() ||
            state.favoriteChannels.isNotEmpty() ||
            state.channelsWithGuide > 0
        ) {
            GroupFilterRow(
                groups = state.groups,
                selected = state.selectedGroup,
                onSelect = viewModel::onGroupChange,
                favoritesOnly = state.favoritesOnly,
                onFavoritesOnlyChange = viewModel::onFavoritesOnlyChange,
                guideOnly = state.guideOnly,
                channelsWithGuide = state.channelsWithGuide,
                onGuideOnlyChange = viewModel::onGuideOnlyChange,
            )
        }

        if (state.fromCache && state.channels.isNotEmpty()) {
            InfoBanner(stringResource(R.string.offline_cache))
        }

        when {
            state.loading && state.channels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingState(stringResource(R.string.loading_channels))
                }
            }

            state.error != null && state.channels.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    ErrorState(
                        message = stringResource(R.string.error_load_channels) + "\n" + (state.error ?: ""),
                        retryLabel = stringResource(R.string.retry),
                        onRetry = viewModel::refresh,
                    )
                }
            }

            searchActive || state.selectedGroup != null || state.favoritesOnly || state.guideOnly -> {
                SearchResults(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onGroupBadgeClick = onGroupBadgeClick,
                    onHideCard = onHideCard,
                    isTv = isTv,
                )
            }

            else -> {
                ChannelsColumn(
                    state = state,
                    isTv = isTv,
                    favoritesTitle = favoritesTitle,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onGroupBadgeClick = onGroupBadgeClick,
                    onHideCard = onHideCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }

    // Conferma "nascondi canale" (feature "nascondi canali"): mai nascondere
    // in silenzio — l'utente vede il nome del canale e sa come tornare indietro
    // (ripristino dalle Impostazioni).
    channelToHide?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToHide = null },
            title = { Text(stringResource(R.string.hide_channel_title)) },
            text = { Text(stringResource(R.string.hide_channel_message, channel.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideChannel(channel.id)
                    channelToHide = null
                }) {
                    Text(stringResource(R.string.hide_channel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { channelToHide = null }) {
                    Text(stringResource(R.string.profiles_cancel))
                }
            },
        )
    }
}

@Composable
private fun rememberTvMode(): Boolean {
    val context = LocalContext.current
    return androidx.compose.runtime.remember { context.isTvDevice() }
}

@Composable
private fun HomeTopBar(
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!searchActive) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            IconButton(onClick = onSearchClick, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.content_desc_search),
                    tint = AppTextPrimary,
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.content_desc_refresh),
                    tint = AppTextPrimary,
                )
            }
            // Guida TV a griglia (feature 8): canali sulle righe, programmi
            // sulla timeline, aprire un programma = aprire il canale.
            IconButton(onClick = onOpenGuide, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = stringResource(R.string.content_desc_guide),
                    tint = AppTextPrimary,
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.content_desc_settings),
                    tint = AppTextPrimary,
                )
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = AppTextPrimary)
                },
                trailingIcon = {
                    IconButton(onClick = onCloseSearch, modifier = Modifier.tvFocusHighlight()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.content_desc_back),
                            tint = AppTextPrimary,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPrimary,
                    cursorColor = AppPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Vista normale: righe orizzontali per recenti, preferiti e iniziali del canale. */
@Composable
private fun ChannelsColumn(
    state: HomeUiState,
    isTv: Boolean,
    favoritesTitle: String,
    onOpenChannel: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onGroupBadgeClick: ((String) -> Unit)?,
    onHideCard: ((Channel) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (state.channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyMessage(stringResource(R.string.no_channels_yet))
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (state.recents.isNotEmpty()) {
            item(key = "recents") {
                ChannelRowSection(
                    title = stringResource(R.string.section_recents),
                    channels = state.recents,
                    state = state,
                    isTv = isTv,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = onToggleFavorite,
                    onGroupBadgeClick = onGroupBadgeClick,
                    onHideCard = onHideCard,
                )
            }
        }

        if (state.favoriteChannels.isNotEmpty()) {
            item(key = "favorites") {
                ChannelRowSection(
                    title = favoritesTitle,
                    channels = state.favoriteChannels,
                    state = state,
                    isTv = isTv,
                    onOpenChannel = onOpenChannel,
                    onToggleFavorite = onToggleFavorite,
                    onGroupBadgeClick = onGroupBadgeClick,
                    onHideCard = onHideCard,
                )
            }
        }

        rowItems(state.groupedChannels, key = { it.first }) { (letter, channels) ->
            ChannelRowSection(
                title = letter.toString(),
                channels = channels,
                state = state,
                isTv = isTv,
                onOpenChannel = onOpenChannel,
                onToggleFavorite = onToggleFavorite,
                onGroupBadgeClick = onGroupBadgeClick,
                onHideCard = onHideCard,
            )
        }
    }
}

@Composable
private fun ChannelRowSection(
    title: String,
    channels: List<Channel>,
    state: HomeUiState,
    isTv: Boolean,
    onOpenChannel: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onGroupBadgeClick: ((String) -> Unit)?,
    onHideCard: ((Channel) -> Unit)?,
) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
        ) {
            rowItems(channels, key = { it.url }) { channel ->
                ChannelCard(
                    channel = channel,
                    isFavorite = channel.id in state.favorites,
                    epgNow = state.epgNow[channel.id],
                    isTv = isTv,
                    onOpen = { onOpenChannel(channel.id) },
                    onToggleFavorite = { onToggleFavorite(channel.id) },
                    onGroupClick = onGroupBadgeClick,
                    onHide = onHideCard?.let { callback -> ({ callback(channel) }) },
                )
            }
        }
    }
}

/** Vista ricerca, filtro gruppo o "solo preferiti": griglia adattiva dei canali selezionati. */
@Composable
private fun SearchResults(
    state: HomeUiState,
    isTv: Boolean,
    onOpenChannel: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onGroupBadgeClick: ((String) -> Unit)?,
    onHideCard: ((Channel) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val results = when {
        // Filtro "solo preferiti": i canali arrivano già intersecati con
        // gruppo e ricerca dal ViewModel.
        state.favoritesOnly -> state.favoriteResults
        // Gruppo selezionato: i canali arrivano già filtrati per gruppo
        // e (se c'è una query) intersecati con la ricerca.
        state.selectedGroup != null -> state.groupChannels
        state.query.isBlank() -> state.channels
        else -> state.filteredChannels
    }

    if (results.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            // Con il filtro preferiti attivo il messaggio dedicato guida
            // l'utente ("tocca la stella"), più utile del generico
            // "nessun risultato".
            EmptyMessage(
                if (state.favoritesOnly) {
                    stringResource(R.string.favorites_empty)
                } else {
                    stringResource(R.string.no_results, state.query)
                },
            )
        }
        return
    }

    Column(modifier = modifier) {
        if (state.selectedGroup != null || state.favoritesOnly || state.guideOnly) {
            Text(
                text = stringResource(
                    R.string.group_count,
                    results.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = AppTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gridItems(results, key = { it.url }) { channel ->
                ChannelCard(
                    channel = channel,
                    isFavorite = channel.id in state.favorites,
                    epgNow = state.epgNow[channel.id],
                    isTv = isTv,
                    onOpen = { onOpenChannel(channel.id) },
                    onToggleFavorite = { onToggleFavorite(channel.id) },
                    onGroupClick = onGroupBadgeClick,
                    onHide = onHideCard?.let { callback -> ({ callback(channel) }) },
                    useCellWidth = true,
                )
            }
        }
    }
}

/**
 * Selettore del paese dei canali (playlist curata iptv-org
 * countries/<codice>.m3u): pillola con bandierina e nome del paese attivo,
 * che apre il menu con tutti i paesi supportati (bandierina + nome
 * localizzato, ordine alfabetico) più la voce automatica (paese del
 * dispositivo). Il paese attivo è la preferenza dedicata countryCode, la
 * stessa delle Impostazioni (fix19); con un URL personale il menu parte
 * dalla voce automatica.
 *
 * Su Android TV il menu richiede il focus esplicito alla prima voce
 * (focusMenuOnOpen), altrimenti il D-pad resta fuori dal popup.
 */
@Composable
private fun CountryPickerButton(
    currentCode: String?,
    onSelect: (String?) -> Unit,
    focusMenuOnOpen: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Fix19: il paese attivo è la preferenza dedicata countryCode (normalizzata
    // in lettura dallo store), non più derivato dall'URL playlist; la voce
    // automatica copre sia la modalità automatica sia un URL personale.
    val activeCode = Config.normalizeCountryCode(currentCode)

    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.country_auto)

    // Fix focus TV: su Android TV la pillola deve mostrare il focus D-pad,
    // altrimenti il telecomando non indica quale selettore è selezionato.
    // Stesso riscontro visivo dei chip del filtro (GroupChip): scala + glow
    // + bordo luminoso animato.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        label = "countryPickerScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) AppPrimary else AppTextSecondary.copy(alpha = 0.35f),
        label = "countryPickerBorder",
    )

    Box(modifier) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .scale(scale)
                .shadow(
                    elevation = if (focused) 12.dp else 0.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = AppPrimary,
                    spotColor = AppPrimary,
                )
                .alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(50))
                .background(AppSurface)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(50),
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (activeCode != null) {
                    "${Config.countryFlagEmoji(activeCode)} ${countryDisplayName(activeCode)}"
                } else {
                    autoLabel
                },
                color = AppTextPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            CountryMenuItems(
                autoLabel = autoLabel,
                options = countryOptions(activeCode),
                onSelect = { code ->
                    expanded = false
                    onSelect(code)
                },
                focusFirstItemOnOpen = focusMenuOnOpen,
            )
        }
    }
}

/**
 * Etichetta della voce automatica del menu sorgente (profili): è la lista
 * iptv-org per paese, la sorgente storica dell'app. Su desktop la stessa
 * etichetta vive in SourcePickerDesktop.kt (SOURCE_AUTO_LABEL).
 */
internal const val SOURCE_AUTO_LABEL = "IPTV-ORG (automatica)"

/**
 * Selettore della sorgente attiva (feature "profilazione della lista"):
 * pillola col nome della sorgente attiva (profilo utente o automatica
 * iptv-org) che apre il menu con la voce automatica, i profili salvati e la
 * voce di gestione che porta alle Impostazioni (dove si aggiungono, modificano
 * e rimuovono i profili). La selezione è immediata: il repository ricarica
 * lista e guida dalla nuova sorgente (HomeViewModel.onProfileChange).
 */
@Composable
private fun SourcePickerButton(
    profiles: List<SourceProfile>,
    activeProfileId: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    focusMenuOnOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeProfileId }

    // Fix focus TV: identico al selettore paese (CountryPickerButton), su
    // Android TV la pillola mostra il focus D-pad con scala + glow + bordo.
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        label = "sourcePickerScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) AppPrimary else AppTextSecondary.copy(alpha = 0.35f),
        label = "sourcePickerBorder",
    )
    // Come nel menu paese (CountryMenuItems): su alcuni box TV con Android
    // vecchio il popup material3 non prende il focus da solo e il D-pad resta
    // "fuori" dal menu; il focus richiesto a mano sulla prima voce è
    // deterministico su ogni device.
    val firstItemFocus = remember { FocusRequester() }
    if (focusMenuOnOpen) {
        LaunchedEffect(expanded) {
            if (!expanded) return@LaunchedEffect
            // Un frame di attesa: al primo fotogramma la voce potrebbe non
            // essere ancora attaccata all'albero, requestFocus è protetto.
            withFrameNanos { }
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    Box(modifier) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .scale(scale)
                .shadow(
                    elevation = if (focused) 12.dp else 0.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = AppPrimary,
                    spotColor = AppPrimary,
                )
                .clip(RoundedCornerShape(50))
                .background(AppSurface)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(50),
                )
                .onFocusChanged { focused = it.isFocused }
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = active?.name ?: SOURCE_AUTO_LABEL,
                color = AppTextPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
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
                modifier = if (focusMenuOnOpen) Modifier.focusRequester(firstItemFocus) else Modifier,
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
                text = { Text(stringResource(R.string.profiles_manage), color = AppTextPrimary) },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }
}

/**
 * Riga orizzontale dei filtri canali: il chip stella "solo preferiti", il
 * chip "solo con guida TV" (visibile solo se la guida copre almeno un canale)
 * e i chip per gruppo (genere). I due commutatori sono ortogonali al gruppo:
 * selezionandoli insieme si vede l'intersezione.
 */
@Composable
private fun GroupFilterRow(
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
    // Un tap sul badge genere di una card può selezionare un chip fuori schermo:
    // la riga si allinea da sola al chip del gruppo scelto (item 0 = stella,
    // item 1 = guida, item 2 = "Tutti", i gruppi partono da 3).
    LaunchedEffect(selected) {
        val index = if (selected == null) 2 else groups.indexOfFirst { it.key == selected } + 3
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item(key = "chip_favorites") {
            GroupChip(
                label = stringResource(R.string.favorites_only),
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
                    label = stringResource(R.string.guide_only),
                    icon = if (guideOnly) Icons.Filled.Tv else Icons.Outlined.Tv,
                    count = channelsWithGuide,
                    selected = guideOnly,
                    onClick = { onGuideOnlyChange(!guideOnly) },
                )
            }
        }
        item(key = "chip_all") {
            GroupChip(
                label = stringResource(R.string.group_all),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        rowItems(groups, key = { it.key }) { group ->
            GroupChip(
                label = groupLabel(group.key),
                count = group.count,
                selected = selected == group.key,
                onClick = { onSelect(group.key) },
            )
        }
    }
}

/** Etichetta del chip: i canali senza gruppo usano la stringa dedicata. */
@Composable
private fun groupLabel(key: String): String =
    if (key == UNCATEGORIZED_GROUP) stringResource(R.string.group_uncategorized) else key

/**
 * Chip a pillola del filtro canali (gruppi e stella preferiti). Su Android TV
 * riceve il focus D-pad come le card canale: scala + glow ombra + bordo
 * luminoso. Il bordo è bianco sui chip selezionati (sfondo AppPrimary) per
 * restare visibile anche lì, dove un anello AppPrimary sparirebbe nel colore
 * di fondo.
 */
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        label = "chipScale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused && selected -> Color.White
            focused -> AppPrimary
            else -> Color.Transparent
        },
        label = "chipBorder",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = RoundedCornerShape(50),
                ambientColor = AppPrimary,
                spotColor = AppPrimary,
            )
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppPrimary else AppSurface)
            .border(2.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    // Stella dorata da spenta, bianca sul chip attivo (sfondo
                    // AppPrimary) come il testo: coerente con le altre stelle.
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
