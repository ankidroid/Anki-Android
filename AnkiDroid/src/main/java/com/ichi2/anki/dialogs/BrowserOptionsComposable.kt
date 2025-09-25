package com.ichi2.anki.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R

@Composable
fun BrowserOptions(
    onCardsModeSelected: () -> Unit,
    onNotesModeSelected: () -> Unit,
    initialMode: Int,
    onTruncateChanged: (Boolean) -> Unit,
    initialTruncate: Boolean,
    onIgnoreAccentsChanged: (Boolean) -> Unit,
    initialIgnoreAccents: Boolean,
    onManageColumnsClicked: () -> Unit,
    onRenameFlagClicked: () -> Unit,
) {
    val selectedMode = remember { mutableStateOf(initialMode) }
    val truncateChecked = remember { mutableStateOf(initialTruncate) }
    val ignoreAccentsChecked = remember { mutableStateOf(initialIgnoreAccents) }

    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(id = R.string.toggle_cards_notes),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedMode.value == 0,
                onClick = {
                    selectedMode.value = 0
                    onCardsModeSelected()
                },
            )
            Text(
                text = stringResource(id = R.string.show_cards),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selectedMode.value == 1,
                onClick = {
                    selectedMode.value = 1
                    onNotesModeSelected()
                },
            )
            Text(
                text = stringResource(id = R.string.show_notes),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = R.string.card_browser_truncate),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = truncateChecked.value,
                onCheckedChange = {
                    truncateChecked.value = it
                    onTruncateChanged(it)
                },
            )
            Text(
                text = stringResource(id = R.string.card_browser_truncate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(id = R.string.truncate_content_help),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = R.string.pref_cat_studying),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = ignoreAccentsChecked.value,
                onCheckedChange = {
                    ignoreAccentsChecked.value = it
                    onIgnoreAccentsChanged(it)
                },
            )
            Text(
                text = stringResource(id = R.string.ignore_accents_in_search),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = R.string.browse_manage_columns_main_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onManageColumnsClicked) {
            Text(text = stringResource(id = R.string.browse_manage_columns))
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(id = R.string.menu_flag),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onRenameFlagClicked) {
            Text(text = stringResource(id = R.string.rename_flag))
        }
    }
}

@Preview
@Composable
fun PreviewBrowserOptions() {
    BrowserOptions(
        onCardsModeSelected = {},
        onNotesModeSelected = {},
        initialMode = 0,
        onTruncateChanged = {},
        initialTruncate = false,
        onIgnoreAccentsChanged = {},
        initialIgnoreAccents = false,
        onManageColumnsClicked = {},
        onRenameFlagClicked = {},
    )
}
