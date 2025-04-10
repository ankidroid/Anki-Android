/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CheckResult
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import anki.collection.OpChanges
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.browser.BrowserColumnCollection
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.BrowserMultiColumnAdapter
import com.ichi2.anki.browser.BrowserRowCollection
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Initializing
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState.Searching
import com.ichi2.anki.browser.CardOrNoteId
import com.ichi2.anki.browser.ColumnHeading
import com.ichi2.anki.browser.FindAndReplaceDialogFragment
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.browser.RepositionCardFragment
import com.ichi2.anki.browser.RepositionCardFragment.Companion.REQUEST_REPOSITION_NEW_CARDS
import com.ichi2.anki.browser.RepositionCardsRequest.ContainsNonNewCardsError
import com.ichi2.anki.browser.RepositionCardsRequest.RepositionData
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.registerFindReplaceHandler
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.BrowserOptionsDialog
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.Companion.newInstance
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.MySearchesDialogListener
import com.ichi2.anki.dialogs.CardBrowserOrderDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ActivityExportingDelegate
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.export.ExportDialogsFactory
import com.ichi2.anki.export.ExportDialogsFactoryProvider
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS
import com.ichi2.anki.model.CardsOrNotes.NOTES
import com.ichi2.anki.model.SortType
import com.ichi2.anki.noteeditor.NoteEditorLauncher
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.getCurrentDialogFragment
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.CardId
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.DeckNameId
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.SortOrder
import com.ichi2.libanki.undoableOp
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.TagsUtil.getUpdatedTags
import com.ichi2.utils.dp
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.updatePaddingRelative
import com.ichi2.widget.WidgetStatus.updateInBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.Translations
import timber.log.Timber

@Suppress("LeakingThis")
// The class is only 'open' due to testing
@KotlinCleanup("scan through this class and add attributes - in process")
open class CardBrowser :
    NavigationDrawerActivity(),
    SubtitleListener,
    DeckSelectionListener,
    TagsDialogListener,
    ChangeManager.Subscriber,
    ExportDialogsFactoryProvider {
    override fun onDeckSelected(deck: SelectableDeck?) {
        deck?.let {
            launchCatchingTask { selectDeckAndSave(deck.deckId) }
        }
    }

    private enum class TagsDialogListenerAction {
        FILTER,
        EDIT_TAGS,
    }

    lateinit var viewModel: CardBrowserViewModel

    private lateinit var deckSpinnerSelection: DeckSpinnerSelection

    @VisibleForTesting
    lateinit var cardsListView: RecyclerView
    private var searchView: CardBrowserSearchView? = null

    @VisibleForTesting
    lateinit var browserColumnHeadings: ViewGroup

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: BrowserMultiColumnAdapter

    private lateinit var tagsDialogFactory: TagsDialogFactory
    private var searchItem: MenuItem? = null
    private var saveSearchItem: MenuItem? = null
    private var mySearchesItem: MenuItem? = null
    private var previewItem: MenuItem? = null
    private var undoSnackbar: Snackbar? = null

    private lateinit var exportingDelegate: ActivityExportingDelegate

    // card that was clicked (not marked)
    override var currentCardId
        get() = viewModel.currentCardId
        set(value) {
            viewModel.currentCardId = value
        }

    // DEFECT: Doesn't need to be a local
    private var tagsDialogListenerAction: TagsDialogListenerAction? = null

    private var onEditCardActivityResult =
        registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
            Timber.d("onEditCardActivityResult: resultCode=%d", result.resultCode)
            if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
            }
            if (result.resultCode != RESULT_CANCELED) {
                Timber.i("CardBrowser:: CardBrowser: Saving card...")
                saveEditedCard()
            }
            val data = result.data
            if (data != null &&
                (
                    data.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                        data.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false)
                )
            ) {
                Timber.d("Reloading Card Browser due to activity result")
                // if reloadRequired or noteChanged flag was sent from note editor then reload card list
                forceRefreshSearch()
                // in use by reviewer?
                if (reviewerCardId == currentCardId) {
                    reloadRequired = true
                }
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
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
                    data.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) ||
                        data.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false)
                )
            ) {
                forceRefreshSearch()
                if (reviewerCardId == currentCardId) {
                    reloadRequired = true
                }
            }
            invalidateOptionsMenu() // maybe the availability of undo changed
        }
    private lateinit var actionBarTitle: TextView
    private var reloadRequired = false

    @VisibleForTesting
    internal var actionBarMenu: Menu? = null

    init {
        ChangeManager.subscribe(this)
    }

    @VisibleForTesting
    internal val mySearchesDialogListener: MySearchesDialogListener =
        object : MySearchesDialogListener {
            override fun onSelection(searchName: String) {
                Timber.d("OnSelection using search named: %s", searchName)
                launchCatchingTask {
                    viewModel.savedSearches()[searchName]?.also { savedSearch ->
                        Timber.d("OnSelection using search terms: %s", savedSearch)
                        searchForQuery(savedSearch)
                    }
                }
            }

            override fun onRemoveSearch(searchName: String) {
                Timber.d("OnRemoveSelection using search named: %s", searchName)
                launchCatchingTask {
                    val updatedFilters = viewModel.removeSavedSearch(searchName)
                    if (updatedFilters.isEmpty()) {
                        mySearchesItem!!.isVisible = false
                    }
                }
            }

            override fun onSaveSearch(
                searchName: String,
                searchTerms: String?,
            ) {
                if (searchTerms == null) {
                    return
                }
                if (searchName.isEmpty()) {
                    showSnackbar(
                        R.string.card_browser_list_my_searches_new_search_error_empty_name,
                        Snackbar.LENGTH_SHORT,
                    )
                    return
                }
                launchCatchingTask {
                    when (viewModel.saveSearch(searchName, searchTerms)) {
                        SaveSearchResult.ALREADY_EXISTS ->
                            showSnackbar(
                                R.string.card_browser_list_my_searches_new_search_error_dup,
                                Snackbar.LENGTH_SHORT,
                            )
                        SaveSearchResult.SUCCESS -> {
                            searchView!!.setQuery("", false)
                            mySearchesItem!!.isVisible = true
                        }
                    }
                }
            }
        }

    private val multiSelectOnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed - exiting multiselect")
                viewModel.endMultiSelectMode()
            }
        }

    @MainThread
    @NeedsTest("search bar is set after selecting a saved search as first action")
    private fun searchForQuery(query: String) {
        // setQuery before expand does not set the view's value
        searchItem!!.expandActionView()
        searchView!!.setQuery(query, submit = true)
    }

    private fun canPerformCardInfo(): Boolean = viewModel.selectedRowCount() == 1

    private fun canPerformMultiSelectEditNote(): Boolean = viewModel.selectedRowCount() == 1

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    fun moveSelectedCardsToDeck(did: DeckId): Job =
        launchCatchingTask {
            val changed = withProgress { viewModel.moveSelectedCardsToDeck(did).await() }
            showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
        }

    // TODO: move onTap logic to ViewModel as per #18178
    @VisibleForTesting
    fun onTap(id: CardOrNoteId) =
        launchCatchingTask {
            if (viewModel.isInMultiSelectMode) {
                viewModel.toggleRowSelection(id)
                viewModel.saveScrollingState(id)
                viewModel.oldCardTopOffset = calculateTopOffset(viewModel.lastSelectedPosition)
            } else {
                val cardId = viewModel.queryDataForCardEdit(id)
                if (viewModel.tapCardToEdit) {
                    openNoteEditorForCard(cardId)
                } else {
                    val args = viewModel.getTemplatePreviewArguments(cardId)
                    val templatePreviewerArgs =
                        TemplatePreviewerArguments(
                            notetypeFile = NotetypeFile(this@CardBrowser, args.notetype),
                            fields = args.fields,
                            tags = args.tags,
                            id = args.id,
                            ord = args.ord,
                            fillEmpty = args.fillEmpty,
                        )
                    val intent = TemplatePreviewerPage.getIntent(this@CardBrowser, templatePreviewerArgs)
                    this@CardBrowser.startActivity(intent)
                }
            }
        }

    @VisibleForTesting
    fun onLongPress(id: CardOrNoteId) {
        // click on whole cell triggers select
        if (viewModel.isInMultiSelectMode && viewModel.lastSelectedId != null) {
            viewModel.selectRowsBetween(viewModel.lastSelectedId!!, id)
        } else {
            viewModel.saveScrollingState(id)
            viewModel.oldCardTopOffset = calculateTopOffset(viewModel.lastSelectedPosition)
            viewModel.toggleRowSelection(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        tagsDialogFactory = TagsDialogFactory(this).attachToActivity<TagsDialogFactory>(this)
        exportingDelegate = ActivityExportingDelegate(this) { getColUnsafe }
        super.onCreate(savedInstanceState)
        if (!ensureStoragePermissions()) {
            return
        }

        // set the default result
        setResult(
            RESULT_OK,
            Intent().apply {
                // Add reload flag to result intent so that schedule reset when returning to note editor
                putExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
            },
        )

        val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()
        // must be called once we have an accessible collection
        viewModel = createViewModel(launchOptions)

        setContentView(R.layout.card_browser)
        initNavigationDrawer(findViewById(android.R.id.content))
        // initialize the lateinit variables
        // Load reference to action bar title
        actionBarTitle = findViewById(R.id.toolbar_title)
        cardsListView = findViewById(R.id.card_browser_list)
        DividerItemDecoration(this, DividerItemDecoration.VERTICAL).apply {
            setDrawable(ContextCompat.getDrawable(this@CardBrowser, R.drawable.browser_divider)!!)
            cardsListView.addItemDecoration(this)
        }

        cardsAdapter =
            BrowserMultiColumnAdapter(
                this,
                viewModel,
                onTap = ::onTap,
                onLongPress = ::onLongPress,
            )
        cardsListView.adapter = cardsAdapter
        cardsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        val layoutManager = LinearLayoutManager(this)
        cardsListView.layoutManager = layoutManager
        cardsListView.addItemDecoration(DividerItemDecoration(this@CardBrowser, layoutManager.orientation))

        deckSpinnerSelection =
            DeckSpinnerSelection(
                this,
                findViewById(R.id.toolbar_spinner),
                showAllDecks = true,
                alwaysShowDefault = false,
                showFilteredDecks = true,
            )

        this.browserColumnHeadings =
            findViewById<ViewGroup>(R.id.browser_column_headings).apply {
                setOnLongClickListener {
                    Timber.d("long press on headings: opening column selection options")
                    val dialog = BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
                    dialog.show(supportFragmentManager, null)
                    true
                }
            }

        startLoadingCollection()

        exportingDelegate.onRestoreInstanceState(savedInstanceState)

        // Selected cards aren't restored on activity recreation,
        // so it is necessary to dismiss the change deck dialog
        getCurrentDialogFragment<DeckSelectionDialog>()?.let { dialogFragment ->
            if (dialogFragment.requireArguments().getBoolean(CHANGE_DECK_KEY, false)) {
                Timber.d("onCreate(): Change deck dialog dismissed")
                dialogFragment.dismiss()
            }
        }

        setupFlows()
        registerOnForgetHandler { viewModel.queryAllSelectedCardIds() }
        setFragmentResultListener(REQUEST_REPOSITION_NEW_CARDS) { _, bundle ->
            repositionCardsNoValidation(
                position = bundle.getInt(RepositionCardFragment.ARG_POSITION),
                step = bundle.getInt(RepositionCardFragment.ARG_STEP),
                shuffle = bundle.getBoolean(RepositionCardFragment.ARG_RANDOM),
                shift = bundle.getBoolean(RepositionCardFragment.ARG_SHIFT),
            )
        }

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
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, multiSelectOnBackPressedCallback)
        super.setupBackPressedCallbacks()
    }

    fun notifyDataSetChanged() {
        cardsAdapter.notifyDataSetChanged()
        refreshSubtitle()
    }

    private fun refreshSubtitle() {
        (findViewById<Spinner>(R.id.toolbar_spinner)?.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        // provides a name for each flow receiver to improve stack traces
        fun onIsTruncatedChanged(isTruncated: Boolean) = notifyDataSetChanged()

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

        fun onColumnsChanged(columnCollection: BrowserColumnCollection) {
            Timber.d("columns changed")
            notifyDataSetChanged()
        }

        fun onFilterQueryChanged(filterQuery: String) {
            // setQuery before expand does not set the view's value
            searchItem!!.expandActionView()
            searchView!!.setQuery(filterQuery, submit = false)
        }

        suspend fun onDeckIdChanged(deckId: DeckId?) {
            if (deckId == null) return
            // this handles ALL_DECKS_ID
            deckSpinnerSelection.selectDeckById(deckId, false)
        }

        fun onCanSaveChanged(canSave: Boolean) {
            saveSearchItem?.isVisible = canSave
        }

        fun isInMultiSelectModeChanged(inMultiSelect: Boolean) {
            if (inMultiSelect) {
                // Turn on Multi-Select Mode so that the user can select multiple cards at once.
                Timber.d("load multiselect mode")
                // show title and hide spinner
                actionBarTitle.visibility = View.VISIBLE
                deckSpinnerSelection.setSpinnerVisibility(View.GONE)
                // A checkbox is added on the rows, match padding to keep the headings aligned
                // Due to the ripple on long press, we set padding
                browserColumnHeadings.updatePaddingRelative(start = 48.dp)
                multiSelectOnBackPressedCallback.isEnabled = true
                autoScrollTo(viewModel.lastSelectedPosition, viewModel.oldCardTopOffset)
            } else {
                Timber.d("end multiselect mode")
                // update adapter to remove check boxes
                notifyDataSetChanged()
                deckSpinnerSelection.setSpinnerVisibility(View.VISIBLE)
                actionBarTitle.visibility = View.GONE
                browserColumnHeadings.updatePaddingRelative(start = 0.dp)
                multiSelectOnBackPressedCallback.isEnabled = false
                autoScrollTo(viewModel.lastSelectedPosition, viewModel.oldCardTopOffset)
            }
            // reload the actionbar using the multi-select mode actionbar
            invalidateOptionsMenu()
        }

        fun cardsUpdatedChanged(unit: Unit) = notifyDataSetChanged()

        fun searchStateChanged(searchState: SearchState) {
            Timber.d("search state: %s", searchState)
            notifyDataSetChanged()

            findViewById<LinearProgressIndicator>(R.id.browser_progress).isVisible =
                searchState == Initializing ||
                searchState == Searching
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
                    showError(this, searchState.error)
                }
            }
        }

        fun onColumnNamesChanged(columnCollection: List<ColumnHeading>) {
            Timber.d("column names changed")
            // reset headings
            browserColumnHeadings.removeAllViews()

            // set up the new columns
            val layoutInflater = LayoutInflater.from(browserColumnHeadings.context)
            for (column in columnCollection) {
                Timber.d("setting up column %s", column)
                layoutInflater.inflate(R.layout.browser_column_heading, browserColumnHeadings, false).apply {
                    browserColumnHeadings.addView(this)
                    (this as TextView).text = column.label
                }
            }
        }

        viewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        viewModel.flowOfSearchQueryExpanded.launchCollectionInLifecycleScope(::onSearchQueryExpanded)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfActiveColumns.launchCollectionInLifecycleScope(::onColumnsChanged)
        viewModel.flowOfFilterQuery.launchCollectionInLifecycleScope(::onFilterQueryChanged)
        viewModel.flowOfDeckId.launchCollectionInLifecycleScope(::onDeckIdChanged)
        viewModel.flowOfCanSearch.launchCollectionInLifecycleScope(::onCanSaveChanged)
        viewModel.flowOfIsInMultiSelectMode.launchCollectionInLifecycleScope(::isInMultiSelectModeChanged)
        viewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.flowOfColumnHeadings.launchCollectionInLifecycleScope(::onColumnNamesChanged)
    }

    // Finish initializing the activity after the collection has been correctly loaded
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        deckSpinnerSelection.apply {
            initializeActionBarDeckSpinner(col, supportActionBar!!)
            launchCatchingTask {
                selectDeckById(
                    viewModel.deckId ?: DeckSpinnerSelection.ALL_DECKS_ID,
                    false,
                )
            }
        }
    }

    suspend fun selectDeckAndSave(deckId: DeckId) {
        viewModel.setDeckId(deckId)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        // This method is called even when the user is typing in the search text field.
        // So we must ensure that all shortcuts uses a modifier.
        // A shortcut without modifier would be triggered while the user types, which is not what we want.
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+A - Show edit tags dialog")
                    showEditTagsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+A - Select All")
                    viewModel.selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+E: Export selected cards")
                    exportSelected()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+E: Add Note")
                    launchCatchingTask { addNoteFromCardBrowser() }
                    return true
                } else if (searchView?.isIconified == true) {
                    Timber.i("E: Edit note")
                    // search box is not available so treat the event as a shortcut
                    openNoteEditorForCurrentlySelectedNote()
                    return true
                } else {
                    Timber.i("E: Character added")
                    // search box might be available and receiving input so treat this as usual text
                    return false
                }
            }
            KeyEvent.KEYCODE_D -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+D: Change Deck")
                    showChangeDeckDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_K -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+K: Toggle Mark")
                    toggleMark()
                    return true
                }
            }
            KeyEvent.KEYCODE_R -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+R - Reschedule")
                    rescheduleSelectedCards()
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
                    deleteSelectedNotes()
                    return true
                }
            }
            KeyEvent.KEYCODE_F -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("CTRL+ALT+F - Find and replace")
                    showFindAndReplaceDialog()
                    return true
                }
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
            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+N: Reset card progress")
                    onResetProgress()
                    return true
                }
            }
            KeyEvent.KEYCODE_T -> {
                if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+T: Toggle cards/notes")
                    showOptionsDialog()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+T: Show filter by tags dialog")
                    showFilterByTagsDialog()
                    return true
                }
            }
            KeyEvent.KEYCODE_S -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+S: Reposition selected cards")
                    repositionSelectedCards()
                    return true
                } else if (event.isCtrlPressed && event.isAltPressed) {
                    Timber.i("Ctrl+Alt+S: Show saved searches")
                    showSavedSearches()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+S: Save search")
                    openSaveSearchView()
                    return true
                } else if (event.isAltPressed) {
                    Timber.i("Alt+S: Show suspended cards")
                    searchForSuspendedCards()
                    return true
                }
            }
            KeyEvent.KEYCODE_J -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    Timber.i("Ctrl+Shift+J: Toggle bury cards")
                    toggleBury()
                    return true
                } else if (event.isCtrlPressed) {
                    Timber.i("Ctrl+J: Toggle suspended cards")
                    toggleSuspendCards()
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
            KeyEvent.KEYCODE_O -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+O: Show order dialog")
                    changeDisplayOrder()
                    return true
                }
            }
            KeyEvent.KEYCODE_M -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+M: Search marked notes")
                    searchForMarkedNotes()
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
            KeyEvent.KEYCODE_ESCAPE -> {
                Timber.i("ESC: Select none")
                viewModel.selectNone()
                return true
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
        updateFlagForSelectedRows(flag)
    }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked  */
    @NeedsTest("Test that the mark get toggled as expected for a list of selected cards")
    @VisibleForTesting
    fun toggleMark() =
        launchCatchingTask {
            withProgress { viewModel.toggleMark() }
            notifyDataSetChanged()
        }

    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target  */
    @NeedsTest("note edits are saved")
    @NeedsTest("I/O edits are saved")
    private fun openNoteEditorForCard(cardId: CardId) {
        currentCardId = cardId
        val intent = NoteEditorLauncher.EditCard(currentCardId, Direction.DEFAULT).toIntent(this)
        onEditCardActivityResult.launch(intent)
        // #6432 - FIXME - onCreateOptionsMenu crashes if receiving an activity result from edit card when in multiselect
        viewModel.endMultiSelectMode()
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

    override fun onStop() {
        // cancel rendering the question and answer, which has shared access to mCards
        super.onStop()
        if (!isFinishing) {
            updateInBackground(this)
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
        actionBarMenu?.findItem(R.id.action_select_all)?.run {
            isVisible = !hasSelectedAllCards()
        }
        actionBarMenu?.findItem(R.id.action_select_none)?.run {
            isVisible = viewModel.hasSelectedAnyRows()
        }
        actionBarMenu?.findItem(R.id.action_undo)?.run {
            isVisible = getColUnsafe.undoAvailable()
            title = getColUnsafe.undoLabel()
        }

        actionBarMenu?.findItem(R.id.action_reschedule_cards)?.title =
            TR.actionsSetDueDate().toSentenceCase(this, R.string.sentence_set_due_date)

        val isFindReplaceEnabled = sharedPrefs().getBoolean(getString(R.string.pref_browser_find_replace), false)
        menu.findItem(R.id.action_find_replace)?.apply {
            isVisible = isFindReplaceEnabled
            title = TR.browsingFindAndReplace().toSentenceCase(this@CardBrowser, R.string.sentence_find_and_replace)
        }
        previewItem = menu.findItem(R.id.action_preview)
        onSelectionChanged()
        updatePreviewMenuItem()
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
            viewModel.endMultiSelectMode()
        } else {
            super.onNavigationPressed()
        }
    }

    private fun updatePreviewMenuItem() {
        previewItem?.isVisible = viewModel.rowCount > 0
    }

    private fun updateMultiselectMenu() {
        Timber.d("updateMultiselectMenu()")
        val actionBarMenu = actionBarMenu
        if (actionBarMenu?.findItem(R.id.action_suspend_card) == null) {
            return
        }
        // set the number of selected rows (only in multiselect)
        actionBarTitle.text = String.format(LanguageUtil.getLocaleCompat(resources), "%d", viewModel.selectedRowCount())
        if (viewModel.hasSelectedAnyRows()) {
            actionBarMenu.findItem(R.id.action_suspend_card).apply {
                title = TR.browsingToggleSuspend().toSentenceCase(this@CardBrowser, R.string.sentence_toggle_suspend)
                // TODO: I don't think this icon is necessary
                setIcon(R.drawable.ic_suspend)
            }
            actionBarMenu.findItem(R.id.action_toggle_bury).apply {
                title = TR.browsingToggleBury().toSentenceCase(this@CardBrowser, R.string.sentence_toggle_bury)
            }
            actionBarMenu.findItem(R.id.action_mark_card).apply {
                title = TR.browsingToggleMark()
                setIcon(R.drawable.ic_star_border_white)
            }
        }
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
        }
        launchCatchingTask {
            actionBarMenu.findItem(R.id.action_delete_card).apply {
                this.title =
                    resources.getQuantityString(
                        R.plurals.card_browser_delete_notes,
                        viewModel.selectedNoteCount(),
                    )
            }
        }

        actionBarMenu.findItem(R.id.action_select_all).isVisible = !hasSelectedAllCards()
        // Note: Theoretically should not happen, as this should kick us back to the menu
        actionBarMenu.findItem(R.id.action_select_none).isVisible =
            viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_edit_note).isVisible = canPerformMultiSelectEditNote()
        actionBarMenu.findItem(R.id.action_view_card_info).isVisible = canPerformCardInfo()
    }

    private fun hasSelectedAllCards(): Boolean = viewModel.selectedRowCount() >= viewModel.rowCount // must handle 0.

    private fun updateFlagForSelectedRows(flag: Flag) =
        launchCatchingTask {
            updateSelectedCardsFlag(flag)
        }

    /**
     * Sets the flag for selected cards, default norm of flags are as:
     *
     * 0: No Flag, 1: RED, 2: ORANGE, 3: GREEN
     * 4: BLUE, 5: PINK, 6: Turquoise, 7: PURPLE
     *
     */
    @VisibleForTesting
    suspend fun updateSelectedCardsFlag(flag: Flag) {
        // list of cards with updated flags
        val updatedCardIds = withProgress { viewModel.updateSelectedCardsFlag(flag) }
        // TODO: try to offload the cards processing in updateCardsInList() on a background thread,
        // otherwise it could hang the main thread
        updateCardsInList(updatedCardIds)
        invalidateOptionsMenu() // maybe the availability of undo changed
        if (updatedCardIds.any { it == reviewerCardId }) {
            reloadRequired = true
        }
    }

    /**
     * @return `false` if the user may proceed; `true` if a warning is shown due to being in [NOTES]
     */
    private fun warnUserIfInNotesOnlyMode(): Boolean {
        if (viewModel.cardsOrNotes != NOTES) return false
        showSnackbar(R.string.card_browser_unavailable_when_notes_mode) {
            setAction(R.string.error_handling_options) { showOptionsDialog() }
        }
        return true
    }

    @NeedsTest("filter-marked query needs testing")
    @NeedsTest("filter-suspended query needs testing")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when {
            drawerToggle.onOptionsItemSelected(item) -> return true

            // dismiss undo-snackbar if shown to avoid race condition
            // (when another operation will be performed on the model, it will undo the latest operation)
            undoSnackbar != null && undoSnackbar!!.isShown -> undoSnackbar!!.dismiss()
        }

        Flag.entries.find { it.ordinal == item.itemId }?.let { flag ->
            when (item.groupId) {
                Mode.SINGLE_SELECT.value -> filterByFlag(flag)
                Mode.MULTI_SELECT.value -> updateFlagForSelectedRows(flag)
                else -> return@let
            }
            return true
        }

        when (item.itemId) {
            android.R.id.home -> {
                viewModel.endMultiSelectMode()
                return true
            }
            R.id.action_add_note_from_card_browser -> {
                addNoteFromCardBrowser()
                return true
            }
            R.id.action_save_search -> {
                openSaveSearchView()
                return true
            }
            R.id.action_list_my_searches -> {
                showSavedSearches()
                return true
            }
            R.id.action_sort_by_size -> {
                changeDisplayOrder()
                return true
            }
            R.id.action_show_marked -> {
                searchForMarkedNotes()
                return true
            }
            R.id.action_show_suspended -> {
                searchForSuspendedCards()
                return true
            }
            R.id.action_search_by_tag -> {
                showFilterByTagsDialog()
                return true
            }
            R.id.action_delete_card -> {
                deleteSelectedNotes()
                return true
            }
            R.id.action_mark_card -> {
                toggleMark()
                return true
            }
            R.id.action_suspend_card -> {
                toggleSuspendCards()
                return true
            }
            R.id.action_toggle_bury -> {
                toggleBury()
                return true
            }
            R.id.action_change_deck -> {
                showChangeDeckDialog()
                return true
            }
            R.id.action_undo -> {
                Timber.w("CardBrowser:: Undo pressed")
                onUndo()
                return true
            }
            R.id.action_select_none -> {
                viewModel.selectNone()
                return true
            }
            R.id.action_select_all -> {
                viewModel.selectAll()
                return true
            }
            R.id.action_preview -> {
                onPreview()
                return true
            }
            R.id.action_reset_cards_progress -> {
                Timber.i("NoteEditor:: Reset progress button pressed")
                onResetProgress()
                return true
            }
            R.id.action_reschedule_cards -> {
                Timber.i("CardBrowser:: Reschedule button pressed")
                rescheduleSelectedCards()
                return true
            }
            R.id.action_reposition_cards -> {
                repositionSelectedCards()
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
            R.id.action_edit_tags -> {
                showEditTagsDialog()
            }
            R.id.action_open_options -> {
                showOptionsDialog()
            }
            R.id.action_export_selected -> {
                exportSelected()
            }
            R.id.action_create_filtered_deck -> {
                showCreateFilteredDeckDialog()
            }
            R.id.action_find_replace -> {
                showFindAndReplaceDialog()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCreateFilteredDeckDialog() {
        val dialog = CreateDeckDialog(this, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
        dialog.onNewDeckCreated = {
            val intent = Intent(this, FilteredDeckOptions::class.java)
            intent.putExtra("search", viewModel.searchTerms)
            startActivity(intent)
        }
        launchCatchingTask {
            withProgress {
                dialog.showFilteredDeckDialog()
            }
        }
    }

    /**
     * @see CardBrowserViewModel.searchForSuspendedCards
     */
    private fun searchForSuspendedCards() {
        launchCatchingTask { viewModel.searchForSuspendedCards() }
    }

    /**
     * @see CardBrowserViewModel.searchForMarkedNotes
     */
    private fun searchForMarkedNotes() {
        launchCatchingTask { viewModel.searchForMarkedNotes() }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun showFindAndReplaceDialog() {
        FindAndReplaceDialogFragment().show(supportFragmentManager, FindAndReplaceDialogFragment.TAG)
    }

    private fun changeDisplayOrder() {
        showDialogFragment(
            // TODO: move this into the ViewModel
            CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                viewModel.changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
            },
        )
    }

    private fun showSavedSearches() {
        launchCatchingTask {
            val savedFilters = viewModel.savedSearches()
            showDialogFragment(
                newInstance(
                    savedFilters,
                    mySearchesDialogListener,
                    "",
                    CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST,
                ),
            )
        }
    }

    private fun openSaveSearchView() {
        val searchTerms = searchView!!.query.toString()
        showDialogFragment(
            newInstance(
                null,
                mySearchesDialogListener,
                searchTerms,
                CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE,
            ),
        )
    }

    private fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        if (warnUserIfInNotesOnlyMode()) return false
        launchCatchingTask {
            when (val repositionCardsResult = viewModel.prepareToRepositionCards()) {
                is ContainsNonNewCardsError -> {
                    // Only new cards may be repositioned (If any non-new found show error dialog and return false)
                    showDialogFragment(
                        SimpleMessageDialog.newInstance(
                            title = getString(R.string.vague_error),
                            message = getString(R.string.reposition_card_not_new_error),
                            reload = false,
                        ),
                    )
                    return@launchCatchingTask
                }
                is RepositionData -> {
                    val top = repositionCardsResult.queueTop
                    val bottom = repositionCardsResult.queueBottom
                    if (top == null || bottom == null) {
                        showSnackbar(R.string.something_wrong)
                        return@launchCatchingTask
                    }
                    val repositionDialog =
                        RepositionCardFragment.newInstance(
                            queueTop = top,
                            queueBottom = bottom,
                            random = repositionCardsResult.random,
                            shift = repositionCardsResult.shift,
                        )
                    showDialogFragment(repositionDialog)
                }
            }
        }
        return true
    }

    private fun displayCardInfo() {
        launchCatchingTask {
            viewModel.queryCardInfoDestination()?.let { destination ->
                val intent: Intent = destination.toIntent(this@CardBrowser)
                startActivity(intent)
            }
        }
    }

    override fun exportDialogsFactory(): ExportDialogsFactory = exportingDelegate.dialogsFactory

    private fun exportSelected() {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return
        ExportDialogFragment.newInstance(type, selectedIds).show(supportFragmentManager, "exportDialog")
    }

    private fun deleteSelectedNotes() =
        launchCatchingTask {
            withProgress(R.string.deleting_selected_notes) {
                viewModel.deleteSelectedNotes()
            }.ifNotZero { noteCount ->
                val deletedMessage = resources.getQuantityString(R.plurals.card_browser_cards_deleted, noteCount, noteCount)
                showUndoSnackbar(deletedMessage)
            }
        }

    @VisibleForTesting
    fun onUndo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    private fun onResetProgress() {
        if (warnUserIfInNotesOnlyMode()) return
        showDialogFragment(ForgetCardsDialog())
    }

    @VisibleForTesting
    fun repositionCardsNoValidation(
        position: Int,
        step: Int,
        shuffle: Boolean,
        shift: Boolean,
    ) = launchCatchingTask {
        val count =
            withProgress {
                viewModel.repositionSelectedRows(
                    position = position,
                    step = step,
                    shuffle = shuffle,
                    shift = shift,
                )
            }
        showSnackbar(
            TR.browsingChangedNewPosition(count),
            Snackbar.LENGTH_SHORT,
        )
    }

    private fun onPreview() {
        launchCatchingTask {
            val intentData = viewModel.queryPreviewIntentData()
            onPreviewCardsActivityResult.launch(getPreviewIntent(intentData.currentIndex, intentData.previewerIdsFile))
        }
    }

    private fun getPreviewIntent(
        index: Int,
        previewerIdsFile: PreviewerIdsFile,
    ): Intent = PreviewerDestination(index, previewerIdsFile).toIntent(this)

    private fun rescheduleSelectedCards() {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.i("Attempted reschedule - no cards selected")
            return
        }
        if (warnUserIfInNotesOnlyMode()) return

        launchCatchingTask {
            val allCardIds = viewModel.queryAllSelectedCardIds()
            showDialogFragment(SetDueDateDialog.newInstance(allCardIds))
        }
    }

    @KotlinCleanup("DeckSelectionListener is almost certainly a bug - deck!!")
    fun getChangeDeckDialog(selectableDecks: List<SelectableDeck>?): DeckSelectionDialog {
        val dialog =
            newInstance(
                getString(R.string.move_all_to_deck),
                null,
                false,
                selectableDecks!!,
            )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener = DeckSelectionListener { deck: SelectableDeck? -> moveSelectedCardsToDeck(deck!!.deckId) }
        return dialog
    }

    private fun showChangeDeckDialog() =
        launchCatchingTask {
            if (!viewModel.hasSelectedAnyRows()) {
                Timber.i("Not showing Change Deck - No Cards")
                return@launchCatchingTask
            }
            val selectableDecks =
                getValidDecksForChangeDeck()
                    .map { d -> SelectableDeck(d) }
            val dialog = getChangeDeckDialog(selectableDecks)
            showDialogFragment(dialog)
        }

    @get:VisibleForTesting
    val addNoteIntent: Intent
        get() = createAddNoteIntent(this, viewModel)

    private fun addNoteFromCardBrowser() {
        onAddNoteActivityResult.launch(addNoteIntent)
    }

    private val reviewerCardId: CardId
        get() = intent.getLongExtra("currentCard", -1)

    private fun showEditTagsDialog() {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.d("showEditTagsDialog: called with empty selection")
        }

        var progressMax: Int? = null // this can be made null to blank the dialog
        var progress = 0

        fun onProgress(progressContext: ProgressContext) {
            val max = progressMax
            if (max == null) {
                progressContext.amount = null
                progressContext.text = getString(R.string.dialog_processing)
            } else {
                progressContext.amount = Pair(progress, max)
            }
        }
        launchCatchingTask {
            withProgress(extractProgress = ::onProgress) {
                val allTags = withCol { tags.all() }
                val selectedNoteIds = viewModel.queryAllSelectedNoteIds()

                progressMax = selectedNoteIds.size * 2
                // TODO!! This is terribly slow on AnKing
                val checkedTags =
                    withCol {
                        selectedNoteIds
                            .asSequence() // reduce memory pressure
                            .flatMap { nid ->
                                progress++
                                getNote(nid).tags // requires withCol
                            }.distinct()
                            .toList()
                    }

                if (selectedNoteIds.size == 1) {
                    Timber.d("showEditTagsDialog: edit tags for one note")
                    tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
                    val dialog =
                        tagsDialogFactory.newTagsDialog().withArguments(
                            this@CardBrowser,
                            type = TagsDialog.DialogType.EDIT_TAGS,
                            checkedTags = checkedTags,
                            allTags = allTags,
                        )
                    showDialogFragment(dialog)
                    return@withProgress
                }
                // TODO!! This is terribly slow on AnKing
                // PERF: This MUST be combined with the above sequence - this becomes O(2n) on a
                // database operation performed over 30k times
                val uncheckedTags =
                    withCol {
                        selectedNoteIds
                            .asSequence() // reduce memory pressure
                            .flatMap { nid: NoteId ->
                                progress++
                                val note = getNote(nid) // requires withCol
                                val noteTags = note.tags.toSet()
                                allTags.filter { t: String? -> !noteTags.contains(t) }
                            }.distinct()
                            .toList()
                    }

                progressMax = null

                Timber.d("showEditTagsDialog: edit tags for multiple note")
                tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS

                // withArguments performs IO, can be 18 seconds
                val dialog =
                    withContext(Dispatchers.IO) {
                        tagsDialogFactory.newTagsDialog().withArguments(
                            context = this@CardBrowser,
                            type = TagsDialog.DialogType.EDIT_TAGS,
                            checkedTags = checkedTags,
                            uncheckedTags = uncheckedTags,
                            allTags = allTags,
                        )
                    }
                showDialogFragment(dialog)
            }
        }
    }

    private fun showFilterByTagsDialog() {
        launchCatchingTask {
            tagsDialogListenerAction = TagsDialogListenerAction.FILTER
            val dialog =
                tagsDialogFactory.newTagsDialog().withArguments(
                    context = this@CardBrowser,
                    type = TagsDialog.DialogType.FILTER_BY_TAG,
                    checkedTags = ArrayList(0),
                    allTags = withCol { tags.all() },
                )
            showDialogFragment(dialog)
        }
    }

    private fun showOptionsDialog() {
        val dialog =
            BrowserOptionsDialog.newInstance(
                viewModel.cardsOrNotes,
                viewModel.isTruncated,
                viewModel.tapCardToEdit,
            )
        dialog.show(supportFragmentManager, "browserOptionsDialog")
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        // Save current search terms
        outState.putString("mSearchTerms", viewModel.searchTerms)
        exportingDelegate.onSaveInstanceState(outState)
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
        Timber.i("CardBrowser:: Completed searchCards() Successfully")
        updateList()
        if (searchView == null || searchView!!.isIconified) {
            return
        }
        updateList()
        if (viewModel.hasSelectedAllDecks()) {
            showSnackbar(subtitleText, Snackbar.LENGTH_SHORT)
        } else {
            // If we haven't selected all decks, allow the user the option to search all decks.
            val message =
                if (viewModel.rowCount == 0) {
                    getString(R.string.card_browser_no_cards_in_deck, selectedDeckNameForUi)
                } else {
                    subtitleText
                }
            showSnackbar(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.card_browser_search_all_decks) { searchAllDecks() }
            }
        }
        updatePreviewMenuItem()
    }

    @MainThread
    private fun updateList() {
        if (!colIsOpenUnsafe()) return
        Timber.d("updateList")
        deckSpinnerSelection.notifyDataSetChanged()
        onSelectionChanged()
        updatePreviewMenuItem()
    }

    @NeedsTest("select 1, check title, select 2, check title")
    private fun onSelectionChanged() {
        Timber.d("onSelectionChanged")
        updateMultiselectMenu()
        actionBarMenu?.findItem(R.id.action_select_all)?.isVisible = !hasSelectedAllCards()
        actionBarMenu?.findItem(R.id.action_select_none)?.isVisible = viewModel.hasSelectedAnyRows()
        notifyDataSetChanged()
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    override val subtitleText: String
        get() {
            val count = viewModel.rowCount

            @StringRes val subtitleId =
                if (viewModel.cardsOrNotes == CARDS) {
                    R.plurals.card_browser_subtitle
                } else {
                    R.plurals.card_browser_subtitle_notes_mode
                }
            return resources.getQuantityString(subtitleId, count, count)
        }

    /** Returns the decks which are valid targets for "Change Deck"  */
    suspend fun getValidDecksForChangeDeck(): List<DeckNameId> = deckSpinnerSelection.computeDropDownDecks(includeFiltered = false)

    @RustCleanup("this isn't how Desktop Anki does it")
    override fun onSelectedTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
        stateFilter: CardStateFilter,
    ) {
        when (tagsDialogListenerAction) {
            TagsDialogListenerAction.FILTER -> filterByTags(selectedTags, stateFilter)
            TagsDialogListenerAction.EDIT_TAGS ->
                launchCatchingTask {
                    editSelectedCardsTags(selectedTags, indeterminateTags)
                }
            else -> {}
        }
    }

    /**
     * Updates the tags of selected/checked notes and saves them to the disk
     * @param selectedTags list of checked tags
     * @param indeterminateTags a list of tags which can checked or unchecked, should be ignored if not expected
     * For more info on [selectedTags] and [indeterminateTags] see [com.ichi2.anki.dialogs.tags.TagsDialogListener.onSelectedTags]
     */
    private suspend fun editSelectedCardsTags(
        selectedTags: List<String>,
        indeterminateTags: List<String>,
    ) = withProgress {
        val selectedNoteIds = viewModel.queryAllSelectedNoteIds().distinct()
        undoableOp {
            val selectedNotes =
                selectedNoteIds
                    .map { noteId -> getNote(noteId) }
                    .onEach { note ->
                        val previousTags: List<String> = note.tags
                        val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                        note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                    }
            updateNotes(selectedNotes)
        }
    }

    private fun filterByTags(
        selectedTags: List<String>,
        cardState: CardStateFilter,
    ) = launchCatchingTask {
        viewModel.filterByTags(selectedTags, cardState)
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

    private fun saveEditedCard() {
        Timber.d("CardBrowser - saveEditedCard()")
        updateCardsInList(listOf(currentCardId))
    }

    private fun toggleSuspendCards() = launchCatchingTask { withProgress { viewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    private fun toggleBury() =
        launchCatchingTask {
            val result = withProgress { viewModel.toggleBury() } ?: return@launchCatchingTask
            // show a snackbar as there's currently no colored background for buried cards
            val message =
                when (result.wasBuried) {
                    true -> TR.studyingCardsBuried(result.count)
                    false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
                }
            showUndoSnackbar(message)
        }

    private fun showUndoSnackbar(message: CharSequence) {
        showSnackbar(message) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            undoSnackbar = this
        }
    }

    private fun refreshAfterUndo() {
        hideProgressBar()
        // reload whole view
        forceRefreshSearch()
        viewModel.endMultiSelectMode()
        notifyDataSetChanged()
        updatePreviewMenuItem()
        invalidateOptionsMenu() // maybe the availability of undo changed
    }

    fun searchAllDecks() =
        launchCatchingTask {
            // all we need to do is select all decks
            viewModel.setDeckId(DeckSpinnerSelection.ALL_DECKS_ID)
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
                    DeckSpinnerSelection.ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
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
     */
    private fun createViewModel(launchOptions: CardBrowserLaunchOptions?) =
        ViewModelProvider(
            viewModelStore,
            CardBrowserViewModel.factory(
                lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
                cacheDir = cacheDir,
                options = launchOptions,
            ),
            defaultViewModelCreationExtras,
        )[CardBrowserViewModel::class.java]

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun filterByTag(vararg tags: String) {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        onSelectedTags(tags.toList(), emptyList(), CardStateFilter.ALL_CARDS)
        filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        if (handler === this || handler === viewModel) {
            return
        }

        if ((
                changes.browserSidebar ||
                    changes.browserTable ||
                    changes.noteText ||
                    changes.card
            )
        ) {
            refreshAfterUndo()
        }
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+Shift+A", R.string.edit_tags_dialog),
                    shortcut("Ctrl+A", R.string.card_browser_select_all),
                    shortcut("Ctrl+Shift+E", Translations::exportingExport),
                    shortcut("Ctrl+E", R.string.menu_add_note),
                    shortcut("E", R.string.cardeditor_title_edit_card),
                    shortcut("Ctrl+D", R.string.card_browser_change_deck),
                    shortcut("Ctrl+K", Translations::browsingToggleMark),
                    shortcut("Ctrl+Alt+R", Translations::browsingReschedule),
                    shortcut("DEL", R.string.delete_card_title),
                    shortcut("Ctrl+Alt+N", R.string.reset_card_dialog_title),
                    shortcut("Ctrl+Alt+T", R.string.toggle_cards_notes),
                    shortcut("Ctrl+T", R.string.card_browser_search_by_tag),
                    shortcut("Ctrl+Shift+S", Translations::actionsReposition),
                    shortcut("Ctrl+Alt+S", R.string.card_browser_list_my_searches),
                    shortcut("Ctrl+S", R.string.card_browser_list_my_searches_save),
                    shortcut("Alt+S", R.string.card_browser_show_suspended),
                    shortcut("Ctrl+Shift+J", Translations::browsingToggleBury),
                    shortcut("Ctrl+J", Translations::browsingToggleSuspend),
                    shortcut("Ctrl+Shift+I", Translations::actionsCardInfo),
                    shortcut("Ctrl+O", R.string.show_order_dialog),
                    shortcut("Ctrl+M", R.string.card_browser_show_marked),
                    shortcut("Esc", R.string.card_browser_select_none),
                    shortcut("Ctrl+1", R.string.gesture_flag_red),
                    shortcut("Ctrl+2", R.string.gesture_flag_orange),
                    shortcut("Ctrl+3", R.string.gesture_flag_green),
                    shortcut("Ctrl+4", R.string.gesture_flag_blue),
                    shortcut("Ctrl+5", R.string.gesture_flag_pink),
                    shortcut("Ctrl+6", R.string.gesture_flag_turquoise),
                    shortcut("Ctrl+7", R.string.gesture_flag_purple),
                ),
                R.string.card_browser_context_menu,
            )

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"

        // Values related to persistent state data
        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()

        @VisibleForTesting
        fun createAddNoteIntent(
            context: Context,
            viewModel: CardBrowserViewModel,
        ): Intent = NoteEditorLauncher.AddNoteFromCardBrowser(viewModel).toIntent(context)
    }

    private fun calculateTopOffset(cardPosition: Int): Int {
        val layoutManager = cardsListView.layoutManager as LinearLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val view = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return view?.top ?: 0
    }

    private fun autoScrollTo(
        newPosition: Int,
        offset: Int,
    ) {
        (cardsListView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(newPosition, offset)
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
    val previewerIdsFile: PreviewerIdsFile,
)

@CheckResult
fun PreviewerDestination.toIntent(context: Context) = PreviewerFragment.getIntent(context, previewerIdsFile, currentIndex)
