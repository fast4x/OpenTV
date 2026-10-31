package it.fast4x.opentv.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import it.fast4x.opentv.data.Config
import it.fast4x.opentv.R
import it.fast4x.opentv.data.model.PlaylistState
import it.fast4x.opentv.data.model.SourceProfile
import it.fast4x.opentv.data.remote.ProfileCodec
import it.fast4x.opentv.openTvApp
import it.fast4x.opentv.ui.components.CountryMenuItems
import it.fast4x.opentv.ui.components.countryOptionLabel
import it.fast4x.opentv.ui.components.countryOptions
import it.fast4x.opentv.ui.components.tvFocusHighlight
import it.fast4x.opentv.ui.theme.AppAccent
import it.fast4x.opentv.ui.theme.AppBackground
import it.fast4x.opentv.ui.theme.AppError
import it.fast4x.opentv.ui.theme.AppPrimary
import it.fast4x.opentv.ui.theme.AppSurface
import it.fast4x.opentv.ui.theme.AppSurfaceHigh
import it.fast4x.opentv.ui.theme.AppTextPrimary
import it.fast4x.opentv.ui.theme.AppTextSecondary

/**
 * Impostazioni: URL della playlist M3U (vuoto = modalità automatica, playlist
 * iptv-org per paese del dispositivo) e URL opzionale della guida TV XMLTV,
 * con stato di caricamento corrente. I campi restano allineati alle
 * preferenze salvate a ogni emissione: la selezione paese mostrata qui
 * coincide sempre con quella del selettore della Home (fix18).
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.openTvApp

    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            appContext = context.applicationContext,
            playlistRepository = app.playlistRepository,
            epgRepository = app.epgRepository,
            prefs = app.prefs,
        )
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
    val epgStatus by viewModel.epgStatus.collectAsStateWithLifecycle()
    val profiles by viewModel.sourceProfiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val hiddenChannels by viewModel.hiddenChannels.collectAsStateWithLifecycle()

    var playlistUrl by rememberSaveable { mutableStateOf("") }
    var epgUrl by rememberSaveable { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    // Form dei profili di sorgente (feature "profilazione della lista"):
    // aggiunta di un nuovo profilo o modifica di quello selezionato con
    // "Modifica" (editingProfileId = profilo in modifica, null = aggiunta).
    var profileName by rememberSaveable { mutableStateOf("") }
    var profilePlaylistUrl by rememberSaveable { mutableStateOf("") }
    var profileEpgUrl by rememberSaveable { mutableStateOf("") }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }

    val savedMessage = stringResource(R.string.settings_saved)
    val resetMessage = stringResource(R.string.settings_reset_playlist)
    val noFileManagerMessage = stringResource(R.string.settings_no_filemanager)
    val fileErrorMessage = stringResource(R.string.settings_file_error)
    val hiddenRestoredMessage = stringResource(R.string.settings_hidden_restored)
    val hiddenRestoredAllMessage = stringResource(R.string.settings_hidden_restored_all)

    // Feature export/import a livello di profilo: il profilo in attesa di
    // essere esportato (appena il SAF restituisce la destinazione) e il
    // piano di importazione in attesa di conferma (profilo omonimo trovato).
    var pendingExportProfile by remember { mutableStateOf<SourceProfile?>(null) }
    var pendingImport by remember { mutableStateOf<SettingsViewModel.ImportPlan?>(null) }

    fun importResultMessage(plan: SettingsViewModel.ImportPlan, result: Result<Int>): String {
        val bundle = plan.bundle
        return result.fold(
            onSuccess = { count ->
                if (bundle != null) {
                    context.getString(
                        R.string.settings_profile_import_ok,
                        bundle.profile.name,
                        count,
                    )
                } else {
                    context.getString(R.string.settings_import_ok, count)
                }
            },
            onFailure = { error -> error.message ?: fileErrorMessage },
        )
    }

    // SAF: esportazione su un nuovo file (CreateDocument) e import da file
    // esistente (OpenDocument). Nessun permesso runtime necessario: il
    // selettore di sistema concede l'accesso al singolo file scelto.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportPlaylistTo(uri) { result ->
                feedback = result.fold(
                    onSuccess = { count -> context.getString(R.string.settings_export_ok, count) },
                    onFailure = { error -> error.message ?: fileErrorMessage },
                )
            }
        }
    }
    val profileExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val target = pendingExportProfile
        pendingExportProfile = null
        if (uri != null && target != null) {
            viewModel.exportProfileTo(target, uri) { result ->
                feedback = result.fold(
                    onSuccess = { count ->
                        context.getString(R.string.settings_profile_export_ok, count)
                    },
                    onFailure = { error -> error.message ?: fileErrorMessage },
                )
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Import generico (feature profili): il file può essere una
            // playlist M3U oppure un pacchetto profilo OpenTV (profilo +
            // canali). Il piano arriva già classificato; con un profilo
            // omonimo già presente si chiede conferma prima di sovrascrivere.
            viewModel.planImport(uri) { result ->
                result.fold(
                    onSuccess = { plan ->
                        if (plan.nameCollision) {
                            pendingImport = plan
                        } else {
                            viewModel.applyImport(plan) { r ->
                                feedback = importResultMessage(plan, r)
                            }
                        }
                    },
                    onFailure = { error -> error.message ?: fileErrorMessage },
                )
            }
        }
    }

    fun launchOrReport(launch: () -> Unit) {
        // Su alcune TV box non c'è nessun gestore file installato: il launch
        // solleva ActivityNotFoundException e va intercettato con un messaggio
        // chiaro invece di crashare.
        runCatching(launch).onFailure { feedback = noFileManagerMessage }
    }

    // I campi seguono le impostazioni persistite a OGNI emissione (fix18).
    // Il flusso parte con un placeholder vuoto e il valore reale arriva dal
    // DataStore poco dopo: il vecchio controllo "solo la prima volta"
    // (loadedInitial) inizializzava i campi col placeholder e non si
    // allineava più, così un paese cambiato in Home non compariva qui (e il
    // campo restava su un valore vecchio o pre-compilato). Ora ogni cambio
    // arrivato da fuori riallinea i campi; mentre questa schermata è aperta
    // le preferenze cambiano solo dopo Salva/Ripristina, che scrivono
    // proprio i valori dei campi: nessun rischio di perdere digitazioni.
    LaunchedEffect(settings) {
        playlistUrl = settings.playlistUrl
        epgUrl = settings.epgUrl
    }

    val channelCount = (playlistState as? PlaylistState.Success)?.channels?.size ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.tvFocusHighlight()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                label = { Text(stringResource(R.string.settings_playlist_label)) },
                placeholder = { Text(stringResource(R.string.settings_playlist_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPrimary,
                    cursorColor = AppPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Nota modalità automatica (fix15): il campo vuoto è uno stato
            // valido (paese del dispositivo), non un campo dimenticato; non
            // viene più pre-compilato con l'URL Italia come prima.
            Text(
                text = stringResource(R.string.settings_playlist_auto_hint),
                style = MaterialTheme.typography.bodySmall,
            )

            // Paese della lista canali: il selettore apre il menu completo
            // (bandierina + nome localizzato) più la voce automatica. Fix19:
            // la scelta viene applicata SUBITO nella preferenza dedicata
            // (la stessa della Home, che resta allineata) e NON tocca il
            // campo URL, che significa solo "lista personale": selezionare
            // un paese non valorizza più il campo qui sotto.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_country_quick_title),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.settings_country_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                CountrySettingsPicker(
                    countryCode = settings.countryCode,
                    onSelect = viewModel::setCountry,
                )
            }

            OutlinedTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                label = { Text(stringResource(R.string.settings_epg_label)) },
                placeholder = { Text(stringResource(R.string.settings_epg_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppPrimary,
                    cursorColor = AppPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Guida automatica: se il campo resta vuoto, dalla playlist
            // iptv-org viene derivata la guida per paese di iptv-epg.org.
            Text(
                text = stringResource(R.string.settings_epg_auto_hint),
                style = MaterialTheme.typography.bodySmall,
            )

            // I chip "guide rapide per paese" dell'EPG sono stati rimossi
            // (fix15): la guida per paese parte da sola in modalità
            // automatica e un URL manuale si incolla direttamente nel campo.

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    viewModel.save(playlistUrl, epgUrl)
                    feedback = savedMessage
                }) {
                    Text(stringResource(R.string.settings_save))
                }
                OutlinedButton(onClick = {
                    viewModel.resetPlaylistToDefault { playlistUrl = it }
                    feedback = resetMessage
                }) {
                    Text(stringResource(R.string.settings_reset))
                }
            }

            feedback?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppAccent,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Profili di sorgente (feature "profilazione della lista"): coppie
            // playlist+guida salvate dall'utente e selezionabili dal menu
            // sorgente della Home e da qui. La sorgente automatica iptv-org
            // resta sempre disponibile e non è eliminabile; il profilo attivo
            // è evidenziato e bypassa URL/paese dei campi qui sopra.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.profiles_desc),
                    style = MaterialTheme.typography.bodySmall,
                )

                // fix34: elenco RAGGRUPPATO con etichetta dedicata: prima i
                // profili e il form erano mescolati nello stesso flusso e
                // sembravano impostazioni sparse.
                Text(
                    text = stringResource(R.string.profiles_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppPrimary,
                )
                if (profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profiles_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = AppTextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (active) {
                                // fix34: badge "Attiva" a pillola, leggibile
                                // sul tint azzurro della scheda attiva.
                                Text(
                                    text = stringResource(R.string.profiles_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(AppPrimary.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        // fix34: valori etichettati e leggibili (prima: URL in
                        // grigio scuro su sfondo ancora più scuro).
                        ProfileValueRow(
                            label = stringResource(R.string.profiles_line_list),
                            value = profile.playlistUrl,
                        )
                        ProfileValueRow(
                            label = stringResource(R.string.profiles_line_guide),
                            value = profile.epgUrl.ifBlank {
                                stringResource(R.string.profiles_guide_auto)
                            },
                            muted = profile.epgUrl.isBlank(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!active) {
                                Button(onClick = { viewModel.setActiveProfile(profile.id) }) {
                                    Text(stringResource(R.string.profiles_use))
                                }
                            }
                            OutlinedButton(onClick = {
                                editingProfileId = profile.id
                                profileName = profile.name
                                profilePlaylistUrl = profile.playlistUrl
                                profileEpgUrl = profile.epgUrl
                            }) {
                                Text(stringResource(R.string.profiles_edit))
                            }
                            // Feature profili: esporta QUESTO profilo con i
                            // canali della sua playlist (pacchetto .opentv).
                            OutlinedButton(onClick = {
                                pendingExportProfile = profile
                                launchOrReport {
                                    profileExportLauncher.launch(
                                        ProfileCodec.suggestedFileName(profile.name),
                                    )
                                }
                            }) {
                                Text(stringResource(R.string.settings_export_profile))
                            }
                            OutlinedButton(onClick = {
                                viewModel.removeProfile(profile.id)
                                if (editingProfileId == profile.id) {
                                    editingProfileId = null
                                    profileName = ""
                                    profilePlaylistUrl = ""
                                    profileEpgUrl = ""
                                }
                                feedback = context.getString(R.string.profiles_removed)
                            }) {
                                Text(stringResource(R.string.profiles_delete))
                            }
                        }
                    }
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (editingProfileId == null) R.string.profiles_form_new
                            else R.string.profiles_form_edit,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = AppTextPrimary,
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text(stringResource(R.string.profiles_name_hint)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            cursorColor = AppPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = profilePlaylistUrl,
                        onValueChange = { profilePlaylistUrl = it },
                        label = { Text(stringResource(R.string.profiles_playlist_hint)) },
                        placeholder = { Text("https://esempio.it/playlist.m3u") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            cursorColor = AppPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = profileEpgUrl,
                        onValueChange = { profileEpgUrl = it },
                        label = { Text(stringResource(R.string.profiles_epg_hint)) },
                        placeholder = { Text("https://esempio.it/guida.xml.gz") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppPrimary,
                            cursorColor = AppPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val formValid = profileName.isNotBlank() && profilePlaylistUrl.isNotBlank()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                val wasEditing = editingProfileId != null
                                val editingId = editingProfileId
                                if (editingId == null) {
                                    viewModel.addProfile(profileName, profilePlaylistUrl, profileEpgUrl)
                                } else {
                                    viewModel.updateProfile(
                                        SourceProfile(editingId, profileName, profilePlaylistUrl, profileEpgUrl),
                                    )
                                }
                                editingProfileId = null
                                profileName = ""
                                profilePlaylistUrl = ""
                                profileEpgUrl = ""
                                feedback = context.getString(
                                    if (wasEditing) R.string.profiles_updated else R.string.profiles_added,
                                )
                            },
                            enabled = formValid,
                        ) {
                            Text(
                                stringResource(
                                    if (editingProfileId == null) R.string.profiles_add else R.string.profiles_save_edit,
                                ),
                            )
                        }
                        if (editingProfileId != null) {
                            OutlinedButton(onClick = {
                                editingProfileId = null
                                profileName = ""
                                profilePlaylistUrl = ""
                                profileEpgUrl = ""
                            }) {
                                Text(stringResource(R.string.profiles_cancel))
                            }
                        }
                    }
                    if (!formValid && (profileName.isNotBlank() || profilePlaylistUrl.isNotBlank())) {
                        Text(
                            text = stringResource(R.string.profiles_need_fields),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Backup playlist: esporta la lista corrente in un file M3U o
            // importa una lista da un file locale, entrambi tramite il
            // selettore di sistema (SAF).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { launchOrReport { exportLauncher.launch("opentv_canali.m3u") } },
                        enabled = channelCount > 0,
                    ) {
                        Text(stringResource(R.string.settings_export))
                    }
                    OutlinedButton(
                        onClick = {
                            launchOrReport { importLauncher.launch(arrayOf("*/*")) }
                        },
                    ) {
                        Text(stringResource(R.string.settings_import))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Canali nascosti (feature "nascondi canali"): gestione per la
            // sorgente attiva (profilo attivo o automatica). I canali nascosti
            // spariscono da Home, ricerca, gruppi, preferiti e guida TV; la
            // playlist originale resta intatta e il ripristino è sempre
            // possibile, canale per canale o tutto insieme.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_hidden_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_hidden_desc),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hiddenChannels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_hidden_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    hiddenChannels.forEach { channel ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = AppTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = {
                                viewModel.unhideChannel(channel.id)
                                feedback = hiddenRestoredMessage
                            }) {
                                Text(stringResource(R.string.settings_hidden_restore))
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        viewModel.unhideAllChannels()
                        feedback = hiddenRestoredAllMessage
                    }) {
                        Text(stringResource(R.string.settings_hidden_restore_all))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_channels_loaded, channelCount),
                    style = MaterialTheme.typography.bodyMedium,
                )

                when (val status = epgStatus) {
                    is it.fast4x.opentv.data.repository.EpgStatus.Idle ->
                        Text(
                            text = stringResource(R.string.settings_epg_status_none),
                            style = MaterialTheme.typography.bodySmall,
                        )

                    is it.fast4x.opentv.data.repository.EpgStatus.Loading ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                text = stringResource(R.string.settings_epg_status_loading),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                    is it.fast4x.opentv.data.repository.EpgStatus.Ready ->
                        Text(
                            text = stringResource(
                                R.string.settings_epg_status_ready,
                                status.programmeCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )

                    is it.fast4x.opentv.data.repository.EpgStatus.Error ->
                        Text(
                            text = stringResource(R.string.settings_epg_status_error, status.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = AppError,
                        )
                }
            }
        }

        // Dialog di conferma per l'import di un pacchetto profilo con il
        // nome di un profilo GIÀ presente: la sovrascrittura (profili + lista
        // canali del file) avviene solo dopo un consenso esplicito.
        pendingImport?.let { plan ->
            val bundle = plan.bundle ?: return@let
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text(stringResource(R.string.settings_profile_exists_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.settings_profile_exists_msg,
                            bundle.profile.name,
                            bundle.channels.size,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val toApply = plan
                        pendingImport = null
                        viewModel.applyImport(toApply) { r ->
                            feedback = importResultMessage(toApply, r)
                        }
                    }) {
                        Text(stringResource(R.string.settings_profile_overwrite))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) {
                        Text(stringResource(R.string.profiles_cancel))
                    }
                },
            )
        }
    }
}

/**
 * Selettore paese per le Impostazioni: pillola con bandierina e nome del
 * paese che apre il menu completo (voci con bandierina emoji + nome
 * localizzato + codice, più la voce automatica). Fix19: la scelta viene
 * applicata subito (come nella Home) nella preferenza dedicata countryCode
 * e NON riempie più il campo URL, che resta per le liste personali; la voce
 * automatica azzera la preferenza (paese del dispositivo).
 */
@Composable
private fun CountrySettingsPicker(
    countryCode: String?,
    onSelect: (String?) -> Unit,
) {
    // Fix19: il paese attivo è la preferenza dedicata (normalizzata in
    // lettura dallo store), la stessa fonte della Home: i due selettori
    // coincidono sempre, in entrambe le direzioni.
    val activeCode = Config.normalizeCountryCode(countryCode)
    var expanded by remember { mutableStateOf(false) }
    val autoLabel = stringResource(R.string.country_auto)

    Column {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                text = if (activeCode != null) {
                    countryOptionLabel(activeCode)
                } else {
                    autoLabel
                },
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
                    // Voce automatica: null (modalità automatica, playlist
                    // del paese del dispositivo); un paese concreto attiva
                    // la sua playlist iptv-org. L'applicazione è immediata.
                    onSelect(code)
                },
            )
        }
    }
}

/**
 * Riga "etichetta: valore" di una scheda profilo (fix34): le coppie
 * playlist/guida prima erano due righe di testo senza etichette, col colore
 * di default scuro su sfondo scurissimo e quasi illeggibili. L'etichetta ha
 * larghezza fissa così i valori delle schede si allineano verticalmente.
 */
@Composable
private fun ProfileValueRow(
    label: String,
    value: String,
    muted: Boolean = false,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppTextSecondary,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (muted) AppTextSecondary else AppTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
