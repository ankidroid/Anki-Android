package com.ichi2.anki.notetype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.ichi2.anki.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageNoteTypesScreen(
    noteTypes: List<ManageNoteTypeUiModel>,
    onAddNoteType: () -> Unit,
    onShowFields: (ManageNoteTypeUiModel) -> Unit,
    onEditCards: (ManageNoteTypeUiModel) -> Unit,
    onRename: (ManageNoteTypeUiModel) -> Unit,
    onDelete: (ManageNoteTypeUiModel) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNoteType) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_manage_notetypes_add),
                )
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(noteTypes) { noteType ->
                NoteTypeItem(
                    noteType = noteType,
                    onShowFields = { onShowFields(noteType) },
                    onEditCards = { onEditCards(noteType) },
                    onRename = { onRename(noteType) },
                    onDelete = { onDelete(noteType) },
                )
            }
        }
    }
}

@Composable
fun NoteTypeItem(
    noteType: ManageNoteTypeUiModel,
    onShowFields: () -> Unit,
    onEditCards: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(noteType.name) },
        supportingContent = { Text(stringResource(R.plurals.manage_notetypes_note_count, noteType.useCount, noteType.useCount)) },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(id = R.string.more_options),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.fields)) },
                        onClick = {
                            onShowFields()
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.cards)) },
                        onClick = {
                            onEditCards()
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.rename)) },
                        onClick = {
                            onRename()
                            showMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.dialog_positive_delete)) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                    )
                }
            }
        },
    )
}

@Preview
@Composable
fun PreviewManageNoteTypesScreen() {
    val noteTypes =
        listOf(
            ManageNoteTypeUiModel(0, "Basic", 1),
            ManageNoteTypeUiModel(1, "Basic (and reversed card)", 2),
            ManageNoteTypeUiModel(2, "Cloze", 3),
        )
    ManageNoteTypesScreen(
        noteTypes = noteTypes,
        onAddNoteType = {},
        onShowFields = {},
        onEditCards = {},
        onRename = {},
        onDelete = {},
    )
}
