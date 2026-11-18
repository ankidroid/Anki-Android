// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR
import com.ichi2.anki.common.android.R as CommonR

/** Filters the tag hierarchy as you type, with a clear action and a keyboard search action. */
@Composable
internal fun TagSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val hint = stringResource(CommonString.manage_tags_search_hint)
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }
    TextField(
        value = input.copy(text = query),
        onValueChange = {
            input = it.filterTagInput()
            if (input.text != query) onQueryChange(input.text)
        },
        modifier = modifier.semantics { contentDescription = hint },
        placeholder = { Text(hint) },
        leadingIcon = { Icon(painterResource(CommonR.drawable.ic_search_white), contentDescription = null) },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painterResource(R.drawable.ic_clear_white),
                            stringResource(AppCompatR.string.abc_searchview_description_clear),
                        )
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

/** @see TagSearchBar */
@ThemePreviews
@Composable
private fun TagSearchBarPreview() {
    AnkiDroidPreview {
        Column(Modifier.padding(16.dp)) {
            TagSearchBar(query = "", onQueryChange = {}, modifier = Modifier.fillMaxWidth())
            TagSearchBar(query = "Biology", onQueryChange = {}, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
