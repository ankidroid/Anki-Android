/*
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CheckResult
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.BrowserRowCollection
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.registerFindReplaceHandler
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.databinding.CardBrowserBinding
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.GradeNowDialog
import com.ichi2.anki.dialogs.SaveBrowserSearchDialogFragment
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.dialogs.registerSaveSearchHandler
import com.ichi2.anki.dialogs.registerSavedSearchActionHandler
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.utils.AndroidUiUtils.hideKeyboard
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber

@Suppress("LeakingThis")
// The class is only 'open' due to testing
@KotlinCleanup("scan through this class and add attributes - in process")
open class CardBrowser :
    NavigationDrawerActivity(),
    DeckSelectionListener,
    TagsDialogListener,
    ChangeManager.Subscriber {
    /**
     * Provides an instance of NoteEditorLauncher for adding a note
     */
    @get:VisibleForTesting
    val addNoteLauncher: NoteEditorLauncher
        get() = createAddNoteLauncher(viewModel)

    /**
     * Provides an instance of NoteEditorLauncher for editing a note
     */
    private val editNoteLauncher: NoteEditorLauncher
        get() =
            NoteEditorLauncher.EditCard(viewModel.currentCardId, Direction.DEFAULT, fragmented).also {
                Timber.i("editNoteLauncher: %s", it)
            }

    override fun onDeckSelected(deck: SelectableDeck?) {
        deck?.let { deck -> launchCatchingTask { viewModel.setSelectedDeck(deck) } }
    }

    override var fragmented: Boolean
        get() = viewModel.isFragmented
        set(value) {
            throw UnsupportedOperationException()
        }

    lateinit var viewModel: CardBrowserViewModel

    private lateinit var binding: CardBrowserBinding

    lateinit var cardBrowserFragment: CardBrowserFragment

    private var actionBarTitle: TextView? = null

    private var searchView: CardBrowserSearchView? = null

    lateinit var tagsDialogFactory: TagsDialogFactory
    private var searchItem: MenuItem? = null
    private var saveSearchItem: MenuItem? = null
    private var mySearchesItem: MenuItem? = null
    private var previewItem: MenuItem? = null

    // card that was clicked (not marked)
    override var currentCardId
        get() = viewModel.currentCardId
        set(value) {
            viewModel.currentCardId = value
        }

    // Dev option for Issue 18709
    // TODO: Broken currently; needs R.layout.card_browser_searchview
    val useSearchView: Boolean
        get() = Prefs.devUsingCardBrowserSearchView

    @Suppress("unused")
    @get:LayoutRes
    private val layout: Int
        get() = if (useSearchView) R.layout.card_browser_searchview else R.layout.card_browser

    private var onEditCardActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.i("onEditCardActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
                return@registerForActivityResult
            }

            // handle template edits

            // in use by reviewer?
            result.data?.let {
                if (
                    it.getBooleanExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                    it.getBooleanExtra(NoteEditorFragment.NOTE_CHANGED_EXTRA_KEY, false)
                ) {
                    if (reviewerCardId == currentCardId) {
                        reloadRequired = true
                    }
                }
            }

            invalidateOptionsMenu() // maybe the availability of undo changed

            // handle card edits
            if (result.resultCode == RESULT_OK) {
                viewModel.onCurrentNoteEdited()
            }
        }
    private var onAddNoteActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onAddNoteActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
            }
            if (result.resultCode == RESULT_OK) {
                forceRefreshSearch(useSearchTextValue = true)
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
        }
    private var onPreviewCardsActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onPreviewCardsActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
            }
            // Previewing can now perform an "edit", so it can pass on a reloadRequired
            val data = result.data
            if (data != null &&
                (
                    data.getBooleanExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                        data.getBooleanExtra(NoteEditorFragment.NOTE_CHANGED_EXTRA_KEY, false)
                )
            ) {
                forceRefreshSearch()
                if (reviewerCardId == currentCardId) {
                    reloadRequired = true
                }
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
        }

    // TODO: Remove this and use `opChanges`
    private var reloadRequired = false

    @VisibleForTesting
    internal var actionBarMenu: Menu? = null

    init {
        ChangeManager.subscribe(this)
    }

    /**
     * Updates the browser's ui after a user search was saved based on the result of the operation.
     * @param saveSearchResult the result of the save search operation
     * @see [CardBrowser.registerSaveSearchHandler]
     */
    fun updateAfterUserSearchIsSaved(saveSearchResult: SaveSearchResult) {
        when (saveSearchResult) {
            SaveSearchResult.ALREADY_EXISTS ->
                showSnackbar(
                    R.string.card_browser_list_my_searches_new_search_error_dup,
                    Snackbar.LENGTH_SHORT,
                )
            SaveSearchResult.SUCCESS -> {
                searchView!!.setQuery("", false)
                mySearchesItem!!.isVisible = true
                showSnackbar(
                    R.string.card_browser_list_my_searches_successful_save,
                    Snackbar.LENGTH_SHORT,
                )
            }
        }
    }

    private val multiSelectOnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed - exiting multiselect")
                viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
            }
        }

    @MainThread
    @NeedsTest("search bar is set after selecting a saved search as first action")
    private fun searchForQuery(query: String) {
        // setQuery before expand does not set the view's value
        searchItem?.expandActionView()
        searchView?.setQuery(query, submit = true)
    }

    private fun canPerformCardInfo(): Boolean = viewModel.selectedRowCount() == 1

    private fun canPerformMultiSelectEditNote(): Boolean = viewModel.selectedRowCount() == 1

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        super.onCreate(savedInstanceState)
        binding = CardBrowserBinding.inflate(layoutInflater)
        if (!ensureStoragePermissions()) {
            return
        }

        // set the default result
        setResult(
            RESULT_OK,
            Intent().apply {
                // Add reload flag to result intent so that schedule reset when returning to note editor
                putExtra(NoteEditorFragment.RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
            },
        )

        val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()

        setViewBinding(binding)
        initNavigationDrawer(findViewById(android.R.id.content))

        /**
         * Check if noteEditorFrame is not null and if its visibility is set to VISIBLE.
         * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
         * [fragmented] will be true if the view size is large otherwise false
         */
        // TODO: Consider refactoring by storing noteEditorFrame and similar views in a sealed class (e.g., FragmentAccessor).
        val fragmented =
            Prefs.devIsCardBrowserFragmented &&
                !useSearchView &&
                binding.noteEditorFrame?.visibility == View.VISIBLE
        Timber.i("Using split Browser: %b", fragmented)

        if (fragmented) {
            ResizablePaneManager(
                parentLayout = requireNotNull(binding.cardBrowserXlView) { "cardBrowserXlView" },
                divider = requireNotNull(binding.cardBrowserResizingDivider) { "cardBrowserResizingDivider" },
                leftPane = requireNotNull(binding.cardBrowserFrame) { "cardBrowserFrame" },
                rightPane = requireNotNull(binding.noteEditorFrame) { "noteEditorFrame" },
                sharedPrefs = Prefs.getUiConfig(this),
                leftPaneWeightKey = PREF_CARD_BROWSER_PANE_WEIGHT,
                rightPaneWeightKey = PREF_NOTE_EDITOR_PANE_WEIGHT,
            )
        } else {
            binding.noteEditorFrame?.isVisible = false
        }

        // must be called once we have an accessible collection
        viewModel = createViewModel(launchOptions, fragmented)

        cardBrowserFragment = supportFragmentManager.findFragmentById(R.id.card_browser_frame) as? CardBrowserFragment
            ?: CardBrowserFragment().also { fragment ->
                supportFragmentManager.commit {
                    replace(R.id.card_browser_frame, fragment)
                }
            }

        if (!useSearchView) {
            // initialize the lateinit variables
            // Load reference to action bar title
            actionBarTitle = findViewById(R.id.toolbar_title)
            // new deck selection is only available when the new search view is not used
            findViewById<LinearLayout>(R.id.toolbar_content).setOnClickListener { startDeckSelection(all = true, filtered = true) }
        }

        startLoadingCollection()

        setupFlows()
        registerOnForgetHandler { viewModel.queryAllSelectedCardIds() }
        registerSaveSearchHandler()

        registerFindReplaceHandler { result ->
            launchCatchingTask {
                withProgress {
                    val count =
                        withProgress {
                            viewModel.findAndReplace(result)
                        }.await()
                    showSnackbar(TR.browsingNotesUpdated(count))
                }
            }
        }
        registerSavedSearchActionHandler { type, searchName ->
            if (searchName == null) return@registerSavedSearchActionHandler
            when (type) {
                SavedBrowserSearchesDialogFragment.TYPE_SEARCH_SELECTED -> {
                    Timber.d("Selecting saved search selection named: %s", searchName)
                    launchCatchingTask {
                        viewModel.savedSearches()[searchName]?.also { savedSearch ->
                            Timber.d("OnSelection using search terms: %s", savedSearch)
                            searchForQuery(savedSearch)
                        }
                    }
                }
                SavedBrowserSearchesDialogFragment.TYPE_SEARCH_REMOVED -> {
                    Timber.d("Removing saved search named: %s", searchName)
                    launchCatchingTask {
                        val updatedFilters = viewModel.removeSavedSearch(searchName)
                        if (updatedFilters.isEmpty()) {
                            mySearchesItem!!.isVisible = false
                        }
                    }
                }
                else -> error("Unexpected saved search action: $type")
            }
        }
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, multiSelectOnBackPressedCallback)
        super.setupBackPressedCallbacks()
    }

    private fun showSaveChangesDialog(launcher: NoteEditorLauncher) {
        DiscardChangesDialog.showDialog(
            context = this,
            positiveButtonText = this.getString(R.string.save),
            negativeButtonText = this.getString(R.string.discard),
            // The neutral button allows the user to back out of the action,
            // e.g., if they accidentally triggered a navigation or card selection.
            neutralButtonText = this.getString(R.string.dialog_cancel),
            message = this.getString(R.string.save_changes_message),
            positiveMethod = {
                launchCatchingTask {
                    fragment?.saveNote()
                    loadNoteEditorFragment(launcher)
                }
            },
            negativeMethod = {
                loadNoteEditorFragment(launcher)
            },
            neutralMethod = {},
        )
    }

    private fun loadNoteEditorFragment(launcher: NoteEditorLauncher) {
        val noteEditorFragment = NoteEditorFragment.newInstance(launcher)
        supportFragmentManager.commit {
            replace(R.id.note_editor_frame, noteEditorFragment)
        }
        // Invalidate options menu so that note editor menu will show
        invalidateOptionsMenu()
    }

    /**
     * Retrieves the `NoteEditor` fragment if it is present in the fragment container
     */
    val fragment: NoteEditorFragment?
        get() = supportFragmentManager.findFragmentById(R.id.note_editor_frame) as? NoteEditorFragment

    /**
     * Loads the NoteEditor fragment in container if the view is x-large.
     */
    fun loadNoteEditorFragmentIfFragmented() {
        if (!fragmented) {
            return
        }
        // Show note editor frame
        binding.noteEditorFrame!!.isVisible = true

        // If there are unsaved changes in NoteEditor then show dialog for confirmation
        if (fragment?.hasUnsavedChanges() == true) {
            showSaveChangesDialog(editNoteLauncher)
        } else {
            loadNoteEditorFragment(editNoteLauncher)
        }
    }

    private fun refreshSubtitle() {
        (findViewById<Spinner>(R.id.toolbar_spinner)?.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        // provides a name for each flow receiver to improve stack traces

        fun onSearchQueryExpanded(searchQueryExpanded: Boolean) {
            Timber.d("query expansion changed: %b", searchQueryExpanded)
            if (searchQueryExpanded) {
                searchItem?.expandActionView()
            } else {
                searchItem?.collapseActionView()
                // invalidate options menu so that disappeared icons would appear again
                invalidateOptionsMenu()
            }
        }

        fun onSelectedRowsChanged(rows: Set<Any>) = onSelectionChanged()

        fun onFilterQueryChanged(filterQuery: String) {
            // setQuery before expand does not set the view's value
            searchItem?.expandActionView()
            searchView?.setQuery(filterQuery, submit = false)
        }

        fun onDeckIdChanged(deckId: DeckId?) {
            if (deckId == null) return
            // this handles ALL_DECKS_ID
            updateAppBarInfo(deckId)
        }

        fun onCanSaveChanged(canSave: Boolean) {
            saveSearchItem?.isVisible = canSave
        }

        fun onMultiSelectModeChanged(modeChange: ChangeMultiSelectMode) {
            if (modeChange.resultedInMultiSelect) {
                // Turn on Multi-Select Mode so that the user can select multiple cards at once.
                Timber.d("load multiselect mode")
                // show title and hide spinner
                actionBarTitle?.visibility = View.VISIBLE
                findViewById<TextView>(R.id.deck_name)?.isVisible = false
                findViewById<TextView>(R.id.subtitle)?.isVisible = false
                multiSelectOnBackPressedCallback.isEnabled = true
            } else {
                Timber.d("end multiselect mode")
                refreshSubtitle()
                findViewById<TextView>(R.id.deck_name)?.isVisible = true
                findViewById<TextView>(R.id.subtitle)?.isVisible = true
                actionBarTitle?.visibility = View.GONE
                multiSelectOnBackPressedCallback.isEnabled = false
            }
            // reload the actionbar using the multi-select mode actionbar
            invalidateOptionsMenu()
        }

        fun cardsUpdatedChanged(unit: Unit) = refreshSubtitle()

        fun searchStateChanged(searchState: SearchState) {
            Timber.d("search state: %s", searchState)
            refreshSubtitle()

            when (searchState) {
                Initializing -> { }
                Searching -> {
                    if ("" != viewModel.searchTerms && searchView != null) {
                        searchView!!.setQuery(viewModel.searchTerms, false)
                        searchItem!!.expandActionView()
                    }
                }
                SearchState.Completed -> redrawAfterSearch()
                is SearchState.Error -> {
                    showError(searchState.error, crashReportData = null)
                }
            }
        }

        fun onSelectedCardUpdated(unit: Unit) {
            if (fragmented) {
                loadNoteEditorFragmentIfFragmented()
            } else {
                onEditCardActivityResult.launch(editNoteLauncher.toIntent(this))
            }
        }

        fun onSaveSearchNamePrompt(searchTerms: String) {
            Timber.i("opening 'save search' name input dialog")
            val dialog =
                SaveBrowserSearchDialogFragment.newInstance(searchQuery = searchTerms)
            showDialogFragment(dialog)
        }

        viewModel.flowOfSearchQueryExpanded.launchCollectionInLifecycleScope(::onSearchQueryExpanded)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfFilterQuery.launchCollectionInLifecycleScope(::onFilterQueryChanged)
        viewModel.flowOfDeckId.launchCollectionInLifecycleScope(::onDeckIdChanged)
        viewModel.flowOfCanSearch.launchCollectionInLifecycleScope(::onCanSaveChanged)
        viewModel.flowOfMultiSelectModeChanged.launchCollectionInLifecycleScope(::onMultiSelectModeChanged)
        viewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.cardSelectionEventFlow.launchCollectionInLifecycleScope(::onSelectedCardUpdated)
        viewModel.flowOfSaveSearchNamePrompt.launchCollectionInLifecycleScope(::onSaveSearchNamePrompt)
    }

    fun isKeyboardVisible(view: View?): Boolean =
        view?.let {
            ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())
        } ?: false

    private fun hideKeyboard() {
        Timber.d("hideKeyboard()")
        searchView?.let { view ->
            view.clearFocus()
            view.hideKeyboard()
        }
    }

    // Finish initializing the activity after the collection has been correctly loaded
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        updateAppBarInfo(viewModel.deckId ?: ALL_DECKS_ID)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (cardBrowserFragment.onKeyUp(keyCode, event)) {
            return true
        }

        // This method is called even when the user is typing in the search text field.
        // So we must ensure that all shortcuts uses a modifier.
        // A shortcut without modifier would be triggered while the user types, which is not what we want.
        when (keyCode) {
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+E: Add Note")
                    launchCatchingTask { addNoteFromCardBrowser() }
                    return true
                } else if (searchView?.isIconified == true) {
                    Timber.i("E: Edit note")
                    // search box is not available so treat the event as a shortcut
                    // Disable 'E' edit shortcut in split mode as the integrated NoteEditor
                    // is already available in the split view, making the shortcut redundant
                    if (fragmented) {
                        Timber.i("E: Ignored in split mode")
                        return true
                    }
                    openNoteEditorForCurrentlySelectedNote()
                    return true
                } else {
                    Timber.i("E: Character added")
                    // search box might be available and receiving input so treat this as usual text
                    return false
                }
            }
            KeyEvent.KEYCODE_G -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+G - Grade Now")
                    openGradeNow()
                    return true
                }
            }
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL -> {
                if (searchView?.isIconified == false) {
                    Timber.i("Delete pressed - Search active, deleting character")
                    // the search box is available and could potentially receive input so handle the
                    // DEL as a simple text deletion and not as a keyboard shortcut
                    return false
                } else {
                    Timber.i("Delete pressed - Delete Selected Note")
                    cardBrowserFragment.deleteSelectedNotes()
                    return true
                }
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+F - Find notes")
                    searchItem?.expandActionView()
                    return true
                }
            }
            KeyEvent.KEYCODE_P -> {
                if (event.isShiftPressed && event.isCtrlPressed) {
                    Timber.i("Ctrl+Shift+P - Preview")
                    onPreview()
                    return true
                }
            }
            KeyEvent.KEYCODE_S -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+S: Show saved searches")
                    showSavedSearches()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+S: Save search")
                    viewModel.saveCurrentSearch()
                    return true
                }
            }
            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+I: Card info")
                    displayCardInfo()
                    return true
                }
            }
            KeyEvent.KEYCODE_Z -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+Z: Undo")
                    onUndo()
                    return true
                }
            }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_7 -> {
                if (event.isCtrlPressed) {
                    Timber.i("Update flag")
                    updateFlag(keyCode)
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun updateFlag(keyCode: Int) {
        val flag =
            when (keyCode) {
                KeyEvent.KEYCODE_1 -> Flag.RED
                KeyEvent.KEYCODE_2 -> Flag.ORANGE
                KeyEvent.KEYCODE_3 -> Flag.GREEN
                KeyEvent.KEYCODE_4 -> Flag.BLUE
                KeyEvent.KEYCODE_5 -> Flag.PINK
                KeyEvent.KEYCODE_6 -> Flag.TURQUOISE
                KeyEvent.KEYCODE_7 -> Flag.PURPLE
                else -> return
            }
        cardBrowserFragment.updateFlagForSelectedRows(flag)
    }

    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target  */
    @NeedsTest("note edits are saved")
    @NeedsTest("I/O edits are saved")
    fun openNoteEditorForCard(cardId: CardId) {
        viewModel.openNoteEditorForCard(cardId)
    }

    /**
     * In case of selection, the first card that was selected, otherwise the first card of the list.
     */
    private suspend fun getCardIdForNoteEditor(): CardId {
        // Just select the first one if there's a multiselect occurring.
        return if (viewModel.isInMultiSelectMode) {
            viewModel.querySelectedCardIdAtPosition(0)
        } else {
            viewModel.getRowAtPosition(0).toCardId(viewModel.cardsOrNotes)
        }
    }

    private fun openNoteEditorForCurrentlySelectedNote() =
        launchCatchingTask {
            // Check whether the deck is empty
            if (viewModel.rowCount == 0) {
                showSnackbar(R.string.no_note_to_edit)
                return@launchCatchingTask
            }

            try {
                val cardId = getCardIdForNoteEditor()
                openNoteEditorForCard(cardId)
            } catch (e: Exception) {
                Timber.w(e, "Error Opening Note Editor")
                showSnackbar(R.string.multimedia_editor_something_wrong)
            }
        }

    override fun onPause() {
        super.onPause()
        // If the user entered something into the search, but didn't press "search", clear this.
        // It's confusing if the bar is shown with a query that does not relate to the data on the screen
        viewModel.removeUnsubmittedInput()
    }

    override fun onResume() {
        super.onResume()
        selectNavigationItem(R.id.nav_browser)
        searchView?.post {
            hideKeyboard()
        }
    }

    @KotlinCleanup("Add a few variables to get rid of the !!")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        actionBarMenu = menu
        if (!viewModel.isInMultiSelectMode) {
            // restore drawer click listener and icon
            restoreDrawerIcon()
            menuInflater.inflate(R.menu.card_browser, menu)
            menu.findItem(R.id.action_search_by_flag).subMenu?.let { subMenu ->
                setupFlags(subMenu, Mode.SINGLE_SELECT)
            }
            menu.findItem(R.id.action_create_filtered_deck).title = TR.qtMiscCreateFilteredDeck()
            saveSearchItem = menu.findItem(R.id.action_save_search)
            saveSearchItem?.isVisible = false // the searchview's query always starts empty.
            mySearchesItem = menu.findItem(R.id.action_list_my_searches)
            val savedFiltersObj = viewModel.savedSearchesUnsafe(getColUnsafe)
            mySearchesItem!!.isVisible = savedFiltersObj.isNotEmpty()
            searchItem = menu.findItem(R.id.action_search)
            searchItem!!.setOnActionExpandListener(
                object : MenuItem.OnActionExpandListener {
                    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                        viewModel.setSearchQueryExpanded(true)
                        return true
                    }

                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        if (item.actionView == searchView) {
                            if (isKeyboardVisible(searchView)) {
                                Timber.d("keyboard is visible, hiding it")
                                hideKeyboard()
                                return false
                            }
                        }
                        viewModel.setSearchQueryExpanded(false)
                        // SearchView doesn't support empty queries so we always reset the search when collapsing
                        searchView!!.setQuery("", false)
                        viewModel.launchSearchForCards("")
                        return true
                    }
                },
            )
            searchView =
                (searchItem!!.actionView as CardBrowserSearchView).apply {
                    queryHint = resources.getString(R.string.card_browser_search_hint)
                    setMaxWidth(Integer.MAX_VALUE)
                    setOnQueryTextListener(
                        object : SearchView.OnQueryTextListener {
                            override fun onQueryTextChange(newText: String): Boolean {
                                if (this@apply.ignoreValueChange) {
                                    return true
                                }
                                viewModel.updateQueryText(newText)
                                return true
                            }

                            override fun onQueryTextSubmit(query: String): Boolean {
                                viewModel.launchSearchForCards(query)
                                searchView!!.clearFocus()
                                return true
                            }
                        },
                    )
                }
            // Fixes #6500 - keep the search consistent if coming back from note editor
            // Fixes #9010 - consistent search after drawer change calls invalidateOptionsMenu
            if (!viewModel.tempSearchQuery.isNullOrEmpty() || viewModel.searchTerms.isNotEmpty()) {
                searchItem!!.expandActionView() // This calls mSearchView.setOnSearchClickListener
                val toUse = if (!viewModel.tempSearchQuery.isNullOrEmpty()) viewModel.tempSearchQuery else viewModel.searchTerms
                searchView!!.setQuery(toUse!!, false)
            }
            searchView!!.setOnSearchClickListener {
                // Provide SearchView with the previous search terms
                searchView!!.setQuery(viewModel.searchTerms, false)
            }
        } else {
            // multi-select mode
            menuInflater.inflate(R.menu.card_browser_multiselect, menu)
            menu.findItem(R.id.action_flag).subMenu?.let { subMenu ->
                setupFlags(subMenu, Mode.MULTI_SELECT)
            }
            showBackIcon()
            increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        }
        // Remove save note and preview note options if there are no notes
        if (fragmented && viewModel.rowCount == 0) {
            menu.removeItem(R.id.action_save)
            menu.removeItem(R.id.action_preview)
        }
        actionBarMenu?.findItem(R.id.action_undo)?.run {
            isVisible = getColUnsafe.undoAvailable()
            title = getColUnsafe.undoLabel()
        }

        actionBarMenu?.findItem(R.id.action_reschedule_cards)?.title =
            TR.actionsSetDueDate().toSentenceCase(this, R.string.sentence_set_due_date)

        actionBarMenu?.findItem(R.id.action_grade_now)?.title =
            TR.actionsGradeNow().toSentenceCase(this, R.string.sentence_grade_now)

        val isFindReplaceEnabled = sharedPrefs().getBoolean(getString(R.string.pref_browser_find_replace), false)
        menu.findItem(R.id.action_find_replace)?.apply {
            isVisible = isFindReplaceEnabled
            title = TR.browsingFindAndReplace().toSentenceCase(this@CardBrowser, R.string.sentence_find_and_replace)
        }
        previewItem = menu.findItem(R.id.action_preview)
        onSelectionChanged()
        refreshMenuItems()
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Representing different selection modes.
     */
    enum class Mode(
        val value: Int,
    ) {
        SINGLE_SELECT(1000),
        MULTI_SELECT(1001),
    }

    private fun setupFlags(
        subMenu: SubMenu,
        mode: Mode,
    ) {
        lifecycleScope.launch {
            val groupId =
                when (mode) {
                    Mode.SINGLE_SELECT -> mode.value
                    Mode.MULTI_SELECT -> mode.value
                }

            for ((flag, displayName) in Flag.queryDisplayNames()) {
                val item =
                    subMenu
                        .add(groupId, flag.code, Menu.NONE, displayName)
                        .setIcon(flag.drawableRes)
                if (flag == Flag.NONE) {
                    val color = ThemeUtils.getThemeAttrColor(this@CardBrowser, android.R.attr.colorControlNormal)
                    item.icon?.mutate()?.setTint(color)
                }
            }
        }
    }

    override fun onNavigationPressed() {
        if (viewModel.isInMultiSelectMode) {
            viewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
        } else {
            super.onNavigationPressed()
        }
    }

    private fun refreshMenuItems() {
        previewItem?.isVisible = !fragmented && viewModel.rowCount > 0
        actionBarMenu?.findItem(R.id.action_select_all)?.isVisible =
            viewModel.rowCount > 0 &&
            viewModel.selectedRowCount() < viewModel.rowCount
    }

    private fun updateMultiselectMenu() {
        Timber.d("updateMultiselectMenu()")
        val actionBarMenu = actionBarMenu
        if (actionBarMenu?.findItem(R.id.action_suspend_card) == null) {
            return
        }
        // set the number of selected rows (only in multiselect)
        actionBarTitle?.text = String.format(LanguageUtil.getLocaleCompat(resources), "%d", viewModel.selectedRowCount())
        findViewById<TextView>(R.id.deck_name)?.isVisible = !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode
        findViewById<TextView>(R.id.subtitle)?.isVisible = !viewModel.hasSelectedAnyRows() && !viewModel.isInMultiSelectMode

        actionBarMenu.findItem(R.id.action_flag).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_suspend_card).apply {
            title = TR.browsingToggleSuspend().toSentenceCase(this@CardBrowser, R.string.sentence_toggle_suspend)
            // TODO: I don't think this icon is necessary
            setIcon(R.drawable.ic_suspend)
            isVisible = viewModel.hasSelectedAnyRows()
        }
        actionBarMenu.findItem(R.id.action_toggle_bury).apply {
            title = TR.browsingToggleBury().toSentenceCase(this@CardBrowser, R.string.sentence_toggle_bury)
            isVisible = viewModel.hasSelectedAnyRows()
        }
        actionBarMenu.findItem(R.id.action_mark_card).apply {
            title = TR.browsingToggleMark()
            setIcon(R.drawable.ic_star_border_white)
            isVisible = viewModel.hasSelectedAnyRows()
        }
        actionBarMenu.findItem(R.id.action_change_deck).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_reposition_cards).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_grade_now).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_reschedule_cards).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_edit_tags).isVisible = viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_reset_cards_progress).isVisible = viewModel.hasSelectedAnyRows()

        actionBarMenu.findItem(R.id.action_export_selected).apply {
            this.title =
                if (viewModel.cardsOrNotes == CARDS) {
                    resources.getQuantityString(
                        R.plurals.card_browser_export_cards,
                        viewModel.selectedRowCount(),
                    )
                } else {
                    resources.getQuantityString(
                        R.plurals.card_browser_export_notes,
                        viewModel.selectedRowCount(),
                    )
                }
            isVisible = viewModel.hasSelectedAnyRows()
        }

        actionBarMenu.findItem(R.id.action_edit_note).isVisible = !fragmented && canPerformMultiSelectEditNote()
        actionBarMenu.findItem(R.id.action_view_card_info).isVisible = canPerformCardInfo()

        val deleteNoteItem =
            actionBarMenu.findItem(R.id.action_delete_card).apply {
                isVisible = viewModel.hasSelectedAnyRows()
            }

        launchCatchingTask {
            deleteNoteItem.apply {
                this.title =
                    resources.getQuantityString(
                        R.plurals.card_browser_delete_notes,
                        viewModel.selectedNoteCount(),
                    )
            }
        }
    }

    fun onCardsUpdated(cardIds: List<CardId>) {
        // TODO: try to offload the cards processing in updateCardsInList() on a background thread,
        // otherwise it could hang the main thread
        updateCardsInList(cardIds)
        invalidateOptionsMenu() // maybe the availability of undo changed
        if (cardIds.any { it == reviewerCardId }) {
            reloadRequired = true
        }
    }

    /**
     * @return `false` if the user may proceed; `true` if a warning is shown due to being in [NOTES]
     */
    fun warnUserIfInNotesOnlyMode(): Boolean {
        if (viewModel.cardsOrNotes != NOTES) return false
        showSnackbar(R.string.card_browser_unavailable_when_notes_mode) {
            setAction(R.string.error_handling_options) { cardBrowserFragment.showOptionsDialog() }
        }
        return true
    }

    @NeedsTest("filter-marked query needs testing")
    @NeedsTest("filter-suspended query needs testing")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        cardBrowserFragment.prepareForUndoableOperation()

        Flag.entries.find { it.ordinal == item.itemId }?.let { flag ->
            when (item.groupId) {
                Mode.SINGLE_SELECT.value -> filterByFlag(flag)
                Mode.MULTI_SELECT.value -> cardBrowserFragment.updateFlagForSelectedRows(flag)
                else -> return@let
            }
            return true
        }

        when (item.itemId) {
            R.id.action_add_note_from_card_browser -> {
                addNoteFromCardBrowser()
                return true
            }
            R.id.action_save_search -> {
                viewModel.saveCurrentSearch()
                return true
            }
            R.id.action_list_my_searches -> {
                showSavedSearches()
                return true
            }
            R.id.action_undo -> {
                Timber.w("CardBrowser:: Undo pressed")
                onUndo()
                return true
            }
            R.id.action_preview -> {
                onPreview()
                return true
            }
            R.id.action_edit_note -> {
                openNoteEditorForCurrentlySelectedNote()
                return super.onOptionsItemSelected(item)
            }
            R.id.action_view_card_info -> {
                displayCardInfo()
                return true
            }
            R.id.action_grade_now -> {
                Timber.i("CardBrowser:: Grade now button pressed")
                openGradeNow()
                return true
            }
        }

        // TODO: make better use of MenuProvider
        if (fragment?.onMenuItemSelected(item) == true) {
            return true
        }
        if (fragment == null) {
            Timber.w("Unexpected onOptionsItemSelected call: %s", item.itemId)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSavedSearches() {
        launchCatchingTask {
            val dialog =
                SavedBrowserSearchesDialogFragment.newInstance(
                    viewModel.savedSearches(),
                )
            showDialogFragment(dialog)
        }
    }

    fun openGradeNow() =
        launchCatchingTask {
            val cardIds = viewModel.queryAllSelectedCardIds()
            GradeNowDialog.showDialog(this@CardBrowser, cardIds)
        }

    private fun displayCardInfo() {
        launchCatchingTask {
            viewModel.queryCardInfoDestination()?.let { destination ->
                val intent: Intent = destination.toIntent(this@CardBrowser)
                startActivity(intent)
            }
        }
    }

    @VisibleForTesting
    fun onUndo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    private fun onPreview() {
        launchCatchingTask {
            val intentData = viewModel.queryPreviewIntentData()
            onPreviewCardsActivityResult.launch(getPreviewIntent(intentData.currentIndex, intentData.idsFile))
        }
    }

    private fun getPreviewIntent(
        index: Int,
        idsFile: IdsFile,
    ): Intent = PreviewerDestination(index, idsFile).toIntent(this)

    private fun addNoteFromCardBrowser() {
        onAddNoteActivityResult.launch(addNoteLauncher.toIntent(this))
    }

    private val reviewerCardId: CardId
        get() = intent.getLongExtra("currentCard", -1)

    public override fun onSaveInstanceState(outState: Bundle) {
        // Save current search terms
        outState.putString("mSearchTerms", viewModel.searchTerms)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        viewModel.onReinit()
        viewModel.launchSearchForCards(
            savedInstanceState.getString("mSearchTerms", ""),
            forceRefresh = false,
        )
    }

    private fun forceRefreshSearch(useSearchTextValue: Boolean = false) {
        if (useSearchTextValue && searchView != null) {
            viewModel.launchSearchForCards(searchView!!.query.toString())
        } else {
            viewModel.launchSearchForCards()
        }
    }

    @NeedsTest("searchView == null -> return early & ensure no snackbar when the screen is opened")
    @MainThread
    private fun redrawAfterSearch() {
        launchCatchingTask {
            Timber.i("CardBrowser:: Completed searchCards() Successfully")
            updateList()
            // Check whether deck is empty or not
            val isDeckEmpty = viewModel.rowCount == 0
            // Hide note editor frame if deck is empty and fragmented
            binding.noteEditorFrame?.visibility =
                if (fragmented && !isDeckEmpty) {
                    viewModel.currentCardId = (viewModel.focusedRow ?: viewModel.cards[0]).toCardId(viewModel.cardsOrNotes)
                    loadNoteEditorFragmentIfFragmented()
                    View.VISIBLE
                } else {
                    invalidateOptionsMenu()
                    View.GONE
                }
            // check whether mSearchView is initialized as it is lateinit property.
            if (searchView == null || searchView!!.isIconified) {
                return@launchCatchingTask
            }
            updateList()
            if (viewModel.hasSelectedAllDecks()) {
                showSnackbar(numberOfCardsOrNoteShown, Snackbar.LENGTH_SHORT)
            } else {
                // If we haven't selected all decks, allow the user the option to search all decks.
                val message =
                    if (viewModel.rowCount == 0) {
                        getString(R.string.card_browser_no_cards_in_deck, selectedDeckNameForUi)
                    } else {
                        numberOfCardsOrNoteShown
                    }
                showSnackbar(message, Snackbar.LENGTH_SHORT) {
                    setAction(R.string.card_browser_search_all_decks) { searchAllDecks() }
                }
            }
            refreshMenuItems()
        }
    }

    @MainThread
    private fun updateList() {
        if (!colIsOpenUnsafe()) return
        Timber.d("updateList")
        updateAppBarInfo(viewModel.deckId)
        onSelectionChanged()
        refreshMenuItems()
    }

    @NeedsTest("select 1, check title, select 2, check title")
    private fun onSelectionChanged() {
        Timber.d("onSelectionChanged")
        updateMultiselectMenu()
        refreshSubtitle()
    }

    /**
     * @return A message stating the number of cards/notes shown by the browser.
     */
    val numberOfCardsOrNoteShown: String
        get() {
            val count = viewModel.rowCount

            @androidx.annotation.StringRes val subtitleId =
                if (viewModel.cardsOrNotes == CARDS) {
                    R.plurals.card_browser_subtitle
                } else {
                    R.plurals.card_browser_subtitle_notes_mode
                }
            return resources.getQuantityString(subtitleId, count, count)
        }

    @RustCleanup("this isn't how Desktop Anki does it")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        cardBrowserFragment.onSelectedTags(selectedTags, indeterminateTags, stateFilter)
    }

    /** Updates search terms to only show cards with selected flag.  */
    @VisibleForTesting
    fun filterByFlag(flag: Flag) = launchCatchingTask { viewModel.setFlagFilter(flag) }

    /**
     * Loads/Reloads (Updates the Q, A & etc) of cards in the [cardIds] list
     * @param cardIds Card IDs that were changed
     */
    private fun updateCardsInList(
        @Suppress("UNUSED_PARAMETER") cardIds: List<CardId>,
    ) {
        updateList()
    }

    private fun refreshAfterUndo() {
        hideProgressBar()
        // reload whole view
        forceRefreshSearch()
        viewModel.endMultiSelectMode(SingleSelectCause.Other)
        refreshSubtitle()
        refreshMenuItems()
        invalidateOptionsMenu() // maybe the availability of undo changed
    }

    fun searchAllDecks() =
        launchCatchingTask {
            // all we need to do is select all decks
            viewModel.setSelectedDeck(SelectableDeck.AllDecks)
        }

    /**
     * Returns the current deck name, "All Decks" if all decks are selected, or "Unknown"
     * Do not use this for any business logic, as this will return inconsistent data
     * with the collection.
     */
    val selectedDeckNameForUi: String
        get() =
            try {
                when (val deckId = viewModel.lastDeckId) {
                    null -> getString(R.string.card_browser_unknown_deck_name)
                    ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                    else -> getColUnsafe.decks.name(deckId)
                }
            } catch (e: Exception) {
                Timber.w(e, "Unable to get selected deck name")
                getString(R.string.card_browser_unknown_deck_name)
            }

    private fun closeCardBrowser(
        result: Int,
        data: Intent? = null,
    ) {
        // Set result and finish
        setResult(result, data)
        finish()
    }

    /**
     * Implementation of `by viewModels()` for use in [onCreate]
     *
     * @see showedActivityFailedScreen - we may not have AnkiDroidApp.instance and therefore can't
     * create the ViewModel
     *
     * @param fragmented True if `noteEditorFrame` is non-null (x-large displays)
     */
    private fun createViewModel(
        launchOptions: CardBrowserLaunchOptions?,
        fragmented: Boolean,
    ) = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = cacheDir,
            options = launchOptions,
            isFragmented = fragmented,
        ),
        defaultViewModelCreationExtras,
    )[CardBrowserViewModel::class.java]

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (handler === this || handler === viewModel) {
            return
        }

        if (changes.browserSidebar ||
            changes.browserTable ||
            changes.noteText ||
            changes.card
        ) {
            refreshAfterUndo()
        }
    }

    override val shortcuts
        get() = cardBrowserFragment.shortcuts

    /**
     * Sets the selected deck name and current selection count based on [numberOfCardsOrNoteShown]
     */
    private fun updateAppBarInfo(deckId: DeckId?) {
        if (deckId == null || useSearchView) return
        findViewById<TextView>(R.id.subtitle)?.text = numberOfCardsOrNoteShown
        launchCatchingTask {
            val deckName =
                when (deckId) {
                    ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                    else -> withCol { decks.getLegacy(deckId)?.name }
                }
            findViewById<TextView>(R.id.deck_name)?.text = deckName
        }
    }

    companion object {
        // Keys for saving pane weights in SharedPreferences
        private const val PREF_CARD_BROWSER_PANE_WEIGHT = "cardBrowserPaneWeight"
        private const val PREF_NOTE_EDITOR_PANE_WEIGHT = "noteEditorPaneWeight"

        // Values related to persistent state data
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()

        @VisibleForTesting
        fun createAddNoteLauncher(viewModel: CardBrowserViewModel): NoteEditorLauncher =
            NoteEditorLauncher.AddNoteFromCardBrowser(viewModel)
    }
}

suspend fun searchForRows(
    query: String,
    order: SortOrder,
    cardsOrNotes: CardsOrNotes,
): BrowserRowCollection =
    withCol {
        when (cardsOrNotes) {
            CARDS -> findCards(query, order)
            NOTES -> findNotes(query, order)
        }
    }.let { ids ->
        BrowserRowCollection(cardsOrNotes, ids.map { CardOrNoteId(it) }.toMutableList())
    }

class PreviewerDestination(
    val currentIndex: Int,
    val idsFile: IdsFile,
)

@CheckResult
fun PreviewerDestination.toIntent(context: Context) = PreviewerFragment.getIntent(context, idsFile, currentIndex)
