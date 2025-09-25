package com.ichi2.anki.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R
import com.ichi2.anki.deckpicker.DisplayDeckNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiDroidApp(
    fragmented: Boolean,
    decks: List<DisplayDeckNode>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    backgroundImage: Painter?,
    onDeckClick: (DisplayDeckNode) -> Unit,
    onExpandClick: (DisplayDeckNode) -> Unit,
    onAddNote: () -> Unit,
    onAddDeck: () -> Unit,
    onAddSharedDeck: () -> Unit,
    onAddFilteredDeck: () -> Unit,
    onDeckOptions: (DisplayDeckNode) -> Unit,
    onRename: (DisplayDeckNode) -> Unit,
    onExport: (DisplayDeckNode) -> Unit,
    onDelete: (DisplayDeckNode) -> Unit,
    onRebuild: (DisplayDeckNode) -> Unit,
    onEmpty: (DisplayDeckNode) -> Unit,
    onNavigationIconClick: () -> Unit,
    studyOptionsData: StudyOptionsData?,
    onStartStudy: () -> Unit,
    onRebuildDeck: (Long) -> Unit,
    onEmptyDeck: (Long) -> Unit,
    onCustomStudy: (Long) -> Unit,
    onDeckOptionsItemSelected: (Long) -> Unit,
    onUnbury: (Long) -> Unit,
    requestSearchFocus: Boolean,
    onSearchFocusRequested: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val searchFocusRequester =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }

    LaunchedEffect(requestSearchFocus) {
        if (requestSearchFocus) {
            searchFocusRequester.requestFocus()
            onSearchFocusRequested()
        }
    }

    if (fragmented) {
        var isSearchOpen by remember { mutableStateOf(false) }
        var isStudyOptionsMenuOpen by remember { mutableStateOf(false) }
        var isFabMenuOpen by remember { mutableStateOf(false) }
        // Tablet layout
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { if (!isSearchOpen) Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigationIconClick) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.navigation_drawer_open))
                        }
                    },
                    actions = {
                        if (isSearchOpen) {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                placeholder = { Text(stringResource(R.string.search_decks)) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onSearchQueryChanged("")
                                        isSearchOpen = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                                    }
                                },
                            )
                        } else {
                            IconButton(onClick = { isSearchOpen = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_decks))
                            }
                        }
                        if (studyOptionsData != null) {
                            IconButton(onClick = { isStudyOptionsMenuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = isStudyOptionsMenuOpen,
                                onDismissRequest = { isStudyOptionsMenuOpen = false },
                            ) {
                                if (studyOptionsData.isFiltered) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rebuild)) },
                                        onClick = {
                                            onRebuildDeck(studyOptionsData.deckId)
                                            isStudyOptionsMenuOpen = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.empty_cards_action)) },
                                        onClick = {
                                            onEmptyDeck(studyOptionsData.deckId)
                                            isStudyOptionsMenuOpen = false
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.custom_study)) },
                                        onClick = {
                                            onCustomStudy(studyOptionsData.deckId)
                                            isStudyOptionsMenuOpen = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.deck_options)) },
                                    onClick = {
                                        onDeckOptionsItemSelected(studyOptionsData.deckId)
                                        isStudyOptionsMenuOpen = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                )
                                if (studyOptionsData.haveBuried) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unbury)) },
                                        onClick = {
                                            onUnbury(studyOptionsData.deckId)
                                            isStudyOptionsMenuOpen = false
                                        },
                                        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_undo), contentDescription = null) },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { isFabMenuOpen = !isFabMenuOpen },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    DropdownMenu(
                        expanded = isFabMenuOpen,
                        onDismissRequest = { isFabMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_note)) },
                            onClick = {
                                onAddNote()
                                isFabMenuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_deck)) },
                            onClick = {
                                onAddDeck()
                                isFabMenuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.get_shared)) },
                            onClick = {
                                onAddSharedDeck()
                                isFabMenuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_dynamic_deck)) },
                            onClick = {
                                onAddFilteredDeck()
                                isFabMenuOpen = false
                            },
                        )
                    }
                }
            },
        ) { paddingValues ->
            Row(Modifier.fillMaxSize().padding(paddingValues)) {
                Box(modifier = Modifier.weight(1f)) {
                    DeckPickerContent(
                        decks = decks,
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        backgroundImage = backgroundImage,
                        onDeckClick = onDeckClick,
                        onExpandClick = onExpandClick,
                        onDeckOptions = onDeckOptions,
                        onRename = onRename,
                        onExport = onExport,
                        onDelete = onDelete,
                        onRebuild = onRebuild,
                        onEmpty = onEmpty,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    StudyOptionsScreen(
                        studyOptionsData = studyOptionsData,
                        onStartStudy = onStartStudy,
                        onCustomStudy = onCustomStudy,
                    )
                }
            }
        }
    } else {
        // Phone layout
        DeckPickerScreen(
            decks = decks,
            isRefreshing = isRefreshing,
            searchFocusRequester = searchFocusRequester,
            snackbarHostState = snackbarHostState,
            onRefresh = onRefresh,
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            backgroundImage = backgroundImage,
            onDeckClick = onDeckClick,
            onExpandClick = onExpandClick,
            onAddNote = onAddNote,
            onAddDeck = onAddDeck,
            onAddSharedDeck = onAddSharedDeck,
            onAddFilteredDeck = onAddFilteredDeck,
            onDeckOptions = onDeckOptions,
            onRename = onRename,
            onExport = onExport,
            onDelete = onDelete,
            onRebuild = onRebuild,
            onEmpty = onEmpty,
            onNavigationIconClick = onNavigationIconClick,
        )
    }
}
