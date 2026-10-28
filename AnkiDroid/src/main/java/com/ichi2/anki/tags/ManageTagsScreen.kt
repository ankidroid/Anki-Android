// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.ui.internationalization.trCase
import com.ichi2.compose.ui.dialogs.MessageDialog
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews
import com.ichi2.compose.ui.preview.ThemePreviews
import androidx.appcompat.R as AppCompatR

/**
 * Displays tag search, the hierarchy and action dialogs, including loading and error states.
 *
 * * Rename tag
 * * Delete tag
 * * Clear unused tags (global)
 * * TODO: Move tag.
 * * TODO: 'Merge into'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageTagsScreen(
    state: ManageTagsState,
    searchQuery: String,
    onSearch: (String) -> Unit,
    onToggleCollapsed: (TagName) -> Unit,
    tagExists: (TagName) -> Boolean,
    onAction: (TagAction, TagName?, String) -> Unit,
    onNavigateUp: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: (Throwable) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogAction by rememberSaveable { mutableStateOf<TagAction?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var previousQuery by remember { mutableStateOf(searchQuery) }
    LaunchedEffect(searchQuery) {
        if (previousQuery != searchQuery) {
            listState.scrollToItem(0)
            previousQuery = searchQuery
        }
    }
    val content = state as? ManageTagsState.Content
    val actionsEnabled = content != null && !content.isWorking
    val animateRows = !LocalInspectionMode.current && Animations.areAnimationsEnabled(LocalContext.current)
    val error =
        when (state) {
            is ManageTagsState.Content -> state.error
            is ManageTagsState.Error -> state.error
            ManageTagsState.Loading -> null
        }

    Scaffold(
        // The activity pads system bars and cutouts. Consume those before applying the IME inset.
        modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars.union(WindowInsets.displayCutout)).imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(CommonString.manage_tags))
                        if (content != null) {
                            Text(
                                pluralStringResource(CommonPlurals.manage_tags_summary, content.totalTagCount, content.totalTagCount),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_arrow_back_24),
                            stringResource(AppCompatR.string.abc_action_bar_up_description),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vertical),
                                stringResource(AppCompatR.string.abc_action_menu_overflow_description),
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(trCase().clearUnusedTags()) },
                                enabled = actionsEnabled,
                                onClick = {
                                    menuExpanded = false
                                    selectedTag = null
                                    dialogAction = TagAction.CLEAR_UNUSED
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TagSearchBar(
                query = searchQuery,
                onQueryChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (content?.isWorking == true) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                state is ManageTagsState.Loading ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                content != null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // Retain the list when empty so the final removed rows can finish fading out.
                        LazyColumn(Modifier.fillMaxSize().testTag("tag_list"), state = listState) {
                            items(content.visibleNodes, key = { it.fullTag.value }) { item ->
                                TagRow(
                                    item = item,
                                    actionsEnabled = actionsEnabled,
                                    onToggleCollapsed = { onToggleCollapsed(item.fullTag) },
                                    onOverflowClick = { selectedTag = item.fullTag.value },
                                    modifier = if (animateRows) Modifier.animateItem() else Modifier,
                                )
                            }
                        }
                        if (content.visibleNodes.isEmpty()) {
                            Text(stringResource(if (searchQuery.isBlank()) CommonString.no_tags else CommonString.manage_tags_no_matches))
                        }
                    }
            }
        }
    }

    val tag = selectedTag?.let(TagName::build)
    val action = dialogAction
    if (tag != null && action == null) {
        TagActionsBottomSheet(
            tag = tag,
            enabled = actionsEnabled,
            onAction = { dialogAction = it },
            onDismissRequest = { selectedTag = null },
        )
    }
    if (action != null) {
        ManageTagDialog(
            action = action,
            tag = tag,
            enabled = actionsEnabled,
            tagExists = tagExists,
            onConfirm = { input ->
                if (actionsEnabled) {
                    onAction(action, tag, input)
                    dialogAction = null
                    selectedTag = null
                }
            },
            onDismissRequest = {
                dialogAction = null
                selectedTag = null
            },
        )
    }
    if (error != null) {
        ManageTagsErrorDialog(error = error, onRetry = onRetry, onDismiss = { onDismissError(error) })
    }
}

/** Filters the tag hierarchy as you type, with a clear action and a keyboard search action. */
@Composable
internal fun TagSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val hint = stringResource(CommonString.manage_tags_search_hint)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.semantics { contentDescription = hint },
        placeholder = { Text(hint) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search_white), contentDescription = null) },
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

/** Displays a tag error with actions to reload the list or dismiss the dialog. */
@Composable
private fun ManageTagsErrorDialog(
    error: Throwable,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    MessageDialog(
        title = stringResource(CommonString.vague_error),
        message = error.localizedMessage ?: error.javaClass.simpleName,
        confirmText = stringResource(CommonString.dialog_ok),
        onConfirm = onRetry,
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

class ManageTagsPreviewProvider : PreviewParameterProvider<ManageTagsState> {
    private val nodes =
        listOf(
            TagListItemState("History", "History", 0, hasChildren = false, collapsed = false),
            TagListItemState("Science", "Science", 0, hasChildren = true, collapsed = false),
            TagListItemState("Science::Biology", "Biology", 1, hasChildren = false, collapsed = false),
        )
    override val values =
        sequenceOf(
            ManageTagsState.Content(nodes, totalTagCount = nodes.size),
            ManageTagsState.Content(emptyList()),
            ManageTagsState.Loading,
            ManageTagsState.Content(nodes, isWorking = true, totalTagCount = nodes.size),
        )

    override fun getDisplayName(index: Int): String = listOf("Tags", "Empty", "Loading", "Busy")[index]
}

/** @see ManageTagsScreen */
@ThemePreviews
@Composable
private fun ManageTagsScreenPreview(
    @PreviewParameter(ManageTagsPreviewProvider::class) state: ManageTagsState,
) {
    AnkiDroidPreview {
        ManageTagsScreen(
            state = state,
            searchQuery = "",
            onSearch = {},
            onToggleCollapsed = {},
            tagExists = { false },
            onAction = { _, _, _ -> },
            onNavigateUp = {},
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** @see ManageTagsErrorDialog */
@DialogPreviews
@Composable
private fun ManageTagsErrorDialogPreview() {
    AnkiDroidPreview {
        ManageTagsErrorDialog(IllegalStateException("Unable to load tags"), onRetry = {}, onDismiss = {})
    }
}
