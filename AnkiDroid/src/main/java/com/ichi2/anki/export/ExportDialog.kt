package com.ichi2.anki.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.theme.AnkiDroidTheme

// State holder classes for checkbox groups
data class CollectionExportState(
    val includeMedia: Boolean = true,
    val supportOlderVersions: Boolean = false,
)

data class ApkgExportState(
    val includeScheduling: Boolean = true,
    val includeDeckConfigs: Boolean = false,
    val includeMedia: Boolean = true,
    val supportOlderVersions: Boolean = false,
)

data class NotesExportState(
    val includeHtml: Boolean = true,
    val includeTags: Boolean = true,
    val includeDeckName: Boolean = false,
    val includeNotetypeName: Boolean = false,
    val includeGuid: Boolean = false,
)

data class CardsExportState(
    val includeHtml: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    exportFormats: List<String>,
    selectedFormat: String,
    onFormatSelected: (String) -> Unit,
    decks: List<DeckNameId>,
    selectedDeck: DeckNameId?,
    onDeckSelected: (DeckNameId) -> Unit,
    decksLoading: Boolean,
    showDeckSelector: Boolean,
    showSelectedNotesLabel: Boolean,
    collectionState: CollectionExportState,
    onCollectionStateChanged: (CollectionExportState) -> Unit,
    apkgState: ApkgExportState,
    onApkgStateChanged: (ApkgExportState) -> Unit,
    notesState: NotesExportState,
    onNotesStateChanged: (NotesExportState) -> Unit,
    cardsState: CardsExportState,
    onCardsStateChanged: (CardsExportState) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // I'm not using HtmlCompat.fromHtml here because it's not directly supported in Compose.
        // The strings will be plain text. If HTML is required, a more complex solution is needed.
        Text(text = stringResource(R.string.exporting_export_format))
        DropdownSelector(
            options = exportFormats,
            selectedOption = selectedFormat,
            onOptionSelected = onFormatSelected,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = stringResource(R.string.exporting_include))

        if (showDeckSelector) {
            DropdownSelector(
                options = decks.map { it.name },
                selectedOption = selectedDeck?.name ?: "",
                onOptionSelected = { name -> decks.find { it.name == name }?.let { onDeckSelected(it) } },
                loading = decksLoading,
            )
        }

        if (showSelectedNotesLabel) {
            Text(
                text = stringResource(R.string.exporting_selected_notes),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // This feels a bit clumsy, but it mirrors the logic of showing/hiding the layouts
        // based on the selected export format index.
        when (exportFormats.indexOf(selectedFormat)) {
            0 -> CollectionExportOptions(collectionState, onCollectionStateChanged)
            1 -> ApkgExportOptions(apkgState, onApkgStateChanged)
            2 -> NotesExportOptions(notesState, onNotesStateChanged)
            3 -> CardsExportOptions(cardsState, onCardsStateChanged)
        }
    }
}

@Composable
fun CollectionExportOptions(
    state: CollectionExportState,
    onStateChanged: (CollectionExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_media),
            checked = state.includeMedia,
            onCheckedChange = { onStateChanged(state.copy(includeMedia = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_support_older_anki_versions),
            checked = state.supportOlderVersions,
            onCheckedChange = { onStateChanged(state.copy(supportOlderVersions = it)) },
        )
    }
}

@Composable
fun ApkgExportOptions(
    state: ApkgExportState,
    onStateChanged: (ApkgExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_scheduling_information),
            checked = state.includeScheduling,
            onCheckedChange = { onStateChanged(state.copy(includeScheduling = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_deck_configs),
            checked = state.includeDeckConfigs,
            onCheckedChange = { onStateChanged(state.copy(includeDeckConfigs = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_media),
            checked = state.includeMedia,
            onCheckedChange = { onStateChanged(state.copy(includeMedia = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_support_older_anki_versions),
            checked = state.supportOlderVersions,
            onCheckedChange = { onStateChanged(state.copy(supportOlderVersions = it)) },
        )
    }
}

@Composable
fun NotesExportOptions(
    state: NotesExportState,
    onStateChanged: (NotesExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_html_and_media_references),
            checked = state.includeHtml,
            onCheckedChange = { onStateChanged(state.copy(includeHtml = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_tags),
            checked = state.includeTags,
            onCheckedChange = { onStateChanged(state.copy(includeTags = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_deck),
            checked = state.includeDeckName,
            onCheckedChange = { onStateChanged(state.copy(includeDeckName = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_notetype),
            checked = state.includeNotetypeName,
            onCheckedChange = { onStateChanged(state.copy(includeNotetypeName = it)) },
        )
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_guid),
            checked = state.includeGuid,
            onCheckedChange = { onStateChanged(state.copy(includeGuid = it)) },
        )
    }
}

@Composable
fun CardsExportOptions(
    state: CardsExportState,
    onStateChanged: (CardsExportState) -> Unit,
) {
    Column {
        CheckboxWithLabel(
            label = stringResource(R.string.exporting_include_html_and_media_references),
            checked = state.includeHtml,
            onCheckedChange = { onStateChanged(state.copy(includeHtml = it)) },
        )
    }
}

@Composable
fun CheckboxWithLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                readOnly = true,
                value = selectedOption,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onOptionSelected(selectionOption)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExportDialogPreview() {
    val exportFormats = listOf("Collection (.colpkg)", "Deck (.apkg)", "Notes (.txt)", "Cards (.txt)")
    val decks = listOf(DeckNameId("All Decks", 0), DeckNameId("Default", 1), DeckNameId("French", 2))

    var selectedFormat by remember { mutableStateOf(exportFormats[1]) }
    var selectedDeck by remember { mutableStateOf(decks[0]) }
    var decksLoading by remember { mutableStateOf(false) }
    var showDeckSelector by remember { mutableStateOf(true) }
    var showNotesLabel by remember { mutableStateOf(false) }

    var collectionState by remember { mutableStateOf(CollectionExportState()) }
    var apkgState by remember { mutableStateOf(ApkgExportState()) }
    var notesState by remember { mutableStateOf(NotesExportState()) }
    var cardsState by remember { mutableStateOf(CardsExportState()) }

    AnkiDroidTheme {
        ExportDialog(
            exportFormats = exportFormats,
            selectedFormat = selectedFormat,
            onFormatSelected = { selectedFormat = it },
            decks = decks,
            selectedDeck = selectedDeck,
            onDeckSelected = { selectedDeck = it },
            decksLoading = decksLoading,
            showDeckSelector = showDeckSelector,
            showSelectedNotesLabel = showNotesLabel,
            collectionState = collectionState,
            onCollectionStateChanged = { collectionState = it },
            apkgState = apkgState,
            onApkgStateChanged = { apkgState = it },
            notesState = notesState,
            onNotesStateChanged = { notesState = it },
            cardsState = cardsState,
            onCardsStateChanged = { cardsState = it },
        )
    }
}
