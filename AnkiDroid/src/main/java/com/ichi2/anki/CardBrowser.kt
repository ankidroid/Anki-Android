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
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserColumn.Companion.COLUMN1_KEYS
import com.ichi2.anki.browser.CardBrowserColumn.Companion.COLUMN2_KEYS
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.SearchState
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.getLabel
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.common.utils.android.isRobolectric
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
import com.ichi2.anki.dialogs.IntegerDialog
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
import com.ichi2.anki.scheduling.ForgetCardsDialog
import com.ichi2.anki.scheduling.SetDueDateDialog
import com.ichi2.anki.scheduling.registerOnForgetHandler
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.avgIntervalOfNote
import com.ichi2.anki.servicelayer.totalLapsesOfNote
import com.ichi2.anki.servicelayer.totalReviewsForNote
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.anki.utils.ext.ifNotZero
import com.ichi2.anki.utils.roundedTimeSpanUnformatted
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.renderBrowserQA
import com.ichi2.libanki.Card
import com.ichi2.libanki.CardId
import com.ichi2.libanki.ChangeManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.DeckId
import com.ichi2.libanki.DeckNameId
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.SortOrder
import com.ichi2.libanki.Sound
import com.ichi2.libanki.TemplateManager
import com.ichi2.libanki.Utils
import com.ichi2.libanki.stripAvRefs
import com.ichi2.libanki.undoableOp
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.Themes
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.HandlerUtils
import com.ichi2.utils.HandlerUtils.postDelayedOnNewHandler
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.TagsUtil.getUpdatedTags
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.widget.WidgetStatus.updateInBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.RustCleanup
import net.ankiweb.rsdroid.Translations
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.ceil

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
        FILTER, EDIT_TAGS
    }

    lateinit var viewModel: CardBrowserViewModel

    /** List of cards in the browser.
     * When the list is changed, the position member of its elements should get changed. */
    private val cards get() = viewModel.cards
    private lateinit var deckSpinnerSelection: DeckSpinnerSelection

    @VisibleForTesting
    lateinit var cardsListView: ListView
    private var searchView: CardBrowserSearchView? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: MultiColumnListAdapter

    private lateinit var tagsDialogFactory: TagsDialogFactory
    private var searchItem: MenuItem? = null
    private var saveSearchItem: MenuItem? = null
    private var mySearchesItem: MenuItem? = null
    private var previewItem: MenuItem? = null
    private var undoSnackbar: Snackbar? = null

    private var renderBrowserQAJob: Job? = null

    private lateinit var exportingDelegate: ActivityExportingDelegate

    // card that was clicked (not marked)
    override var currentCardId
        get() = viewModel.currentCardId
        set(value) { viewModel.currentCardId = value }

    // DEFECT: Doesn't need to be a local
    private var tagsDialogListenerAction: TagsDialogListenerAction? = null

    private var onEditCardActivityResult = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
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
            (data.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) || data.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false))
        ) {
            Timber.d("Reloading Card Browser due to activity result")
            // if reloadRequired or noteChanged flag was sent from note editor then reload card list
            shouldRestoreScroll = true
            forceRefreshSearch()
            // in use by reviewer?
            if (reviewerCardId == currentCardId) {
                reloadRequired = true
            }
        }
        invalidateOptionsMenu() // maybe the availability of undo changed
    }
    private var onAddNoteActivityResult = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
        Timber.d("onAddNoteActivityResult: resultCode=%d", result.resultCode)
        if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
        }
        if (result.resultCode == RESULT_OK) {
            forceRefreshSearch(useSearchTextValue = true)
        }
        invalidateOptionsMenu() // maybe the availability of undo changed
    }
    private var onPreviewCardsActivityResult = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
        Timber.d("onPreviewCardsActivityResult: resultCode=%d", result.resultCode)
        if (result.resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR)
        }
        // Previewing can now perform an "edit", so it can pass on a reloadRequired
        val data = result.data
        if (data != null &&
            (data.getBooleanExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, false) || data.getBooleanExtra(NoteEditor.NOTE_CHANGED_EXTRA_KEY, false))
        ) {
            forceRefreshSearch()
            if (reviewerCardId == currentCardId) {
                reloadRequired = true
            }
        }
        invalidateOptionsMenu() // maybe the availability of undo changed
    }
    private var lastRenderStart: Long = 0
    private lateinit var actionBarTitle: TextView
    private var reloadRequired = false

    private var lastSelectedPosition
        get() = viewModel.lastSelectedPosition
        set(value) { viewModel.lastSelectedPosition = value }
    private var actionBarMenu: Menu? = null
    private var oldCardId: CardId = 0
    private var oldCardTopOffset = 0
    private var shouldRestoreScroll = false
    private var postAutoScroll = false

    init {
        ChangeManager.subscribe(this)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    fun changeCardOrder(sortType: SortType) = launchCatchingTask {
        // TODO: remove withProgress and replace with search progress bar
        withProgress { viewModel.changeCardOrder(sortType)?.join() }
    }

    @VisibleForTesting
    internal val mySearchesDialogListener: MySearchesDialogListener = object : MySearchesDialogListener {

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

        override fun onSaveSearch(searchName: String, searchTerms: String?) {
            if (searchTerms == null) {
                return
            }
            if (searchName.isEmpty()) {
                showSnackbar(
                    R.string.card_browser_list_my_searches_new_search_error_empty_name,
                    Snackbar.LENGTH_SHORT
                )
                return
            }
            launchCatchingTask {
                when (viewModel.saveSearch(searchName, searchTerms)) {
                    SaveSearchResult.ALREADY_EXISTS -> showSnackbar(
                        R.string.card_browser_list_my_searches_new_search_error_dup,
                        Snackbar.LENGTH_SHORT
                    )
                    SaveSearchResult.SUCCESS -> {
                        searchView!!.setQuery("", false)
                        mySearchesItem!!.isVisible = true
                    }
                }
            }
        }
    }

    @MainThread
    @NeedsTest("search bar is set after selecting a saved search as first action")
    private fun searchForQuery(query: String) {
        // setQuery before expand does not set the view's value
        searchItem!!.expandActionView()
        searchView!!.setQuery(query, submit = true)
    }

    private fun canPerformCardInfo(): Boolean {
        return viewModel.selectedRowCount() == 1
    }

    private fun canPerformMultiSelectEditNote(): Boolean {
        // The noteId is not currently available. Only allow if a single card is selected for now.
        return viewModel.selectedRowCount() == 1
    }

    /**
     * Change Deck
     * @param did Id of the deck
     */
    @VisibleForTesting
    fun moveSelectedCardsToDeck(did: DeckId): Job {
        return launchCatchingTask {
            val changed = withProgress { viewModel.moveSelectedCardsToDeck(did).await() }
            showUndoSnackbar(TR.browsingCardsUpdated(changed.count))
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
        val launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()
        // must be called once we have an accessible collection
        viewModel = createViewModel(launchOptions)

        setContentView(R.layout.card_browser)
        initNavigationDrawer(findViewById(android.R.id.content))
        // initialize the lateinit variables
        // Load reference to action bar title
        actionBarTitle = findViewById(R.id.toolbar_title)
        cardsListView = findViewById(R.id.card_browser_list)
        val preferences = baseContext.sharedPrefs()
        // get the font and font size from the preferences
        val sflRelativeFontSize =
            preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO)
        val columnsContent = arrayOf(
            viewModel.column1,
            viewModel.column2
        )
        // make a new list adapter mapping the data in mCards to column1 and column2 of R.layout.card_item_browser
        cardsAdapter = MultiColumnListAdapter(
            this,
            R.layout.card_item_browser,
            columnsContent,
            intArrayOf(R.id.card_sfld, R.id.card_column2),
            sflRelativeFontSize
        )
        // link the adapter to the main mCardsListView
        cardsListView.adapter = cardsAdapter
        // make the items (e.g. question & answer) render dynamically when scrolling
        cardsListView.setOnScrollListener(RenderOnScroll())

        deckSpinnerSelection = DeckSpinnerSelection(
            this,
            findViewById(R.id.toolbar_spinner),
            showAllDecks = true,
            alwaysShowDefault = false,
            showFilteredDecks = true
        )

        updateNumCardsToRender()

        startLoadingCollection()

        exportingDelegate.onRestoreInstanceState(savedInstanceState)

        // Selected cards aren't restored on activity recreation,
        // so it is necessary to dismiss the change deck dialog
        val dialogFragment = supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
        if (dialogFragment is DeckSelectionDialog) {
            if (dialogFragment.requireArguments().getBoolean(CHANGE_DECK_KEY, false)) {
                Timber.d("onCreate(): Change deck dialog dismissed")
                dialogFragment.dismiss()
            }
        }

        setupFlows()
        registerOnForgetHandler { viewModel.queryAllSelectedCardIds() }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        // provides a name for each flow receiver to improve stack traces
        fun onIsTruncatedChanged(isTruncated: Boolean) = cardsAdapter.notifyDataSetChanged()
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
        fun onSelectedRowsChanged(rows: Set<CardCache>) = onSelectionChanged()
        fun onColumn1Changed(column: CardBrowserColumn) {
            cardsAdapter.updateMapping { it[0] = column }
            findViewById<Spinner>(R.id.browser_column1_spinner)
                .setSelection(COLUMN1_KEYS.indexOf(column))
        }

        fun onColumn2Changed(column: CardBrowserColumn) {
            cardsAdapter.updateMapping { it[1] = column }
            findViewById<Spinner>(R.id.browser_column2_spinner)
                .setSelection(COLUMN2_KEYS.indexOf(column))
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
            } else {
                Timber.d("end multiselect mode")
                // If view which was originally selected when entering multi-select is visible then maintain its position
                val view = cardsListView.getChildAt(lastSelectedPosition - cardsListView.firstVisiblePosition)
                view?.let { recenterListView(it) }
                // update adapter to remove check boxes
                cardsAdapter.notifyDataSetChanged()
                deckSpinnerSelection.setSpinnerVisibility(View.VISIBLE)
                actionBarTitle.visibility = View.GONE
            }
            // reload the actionbar using the multi-select mode actionbar
            invalidateOptionsMenu()
        }
        fun cardsUpdatedChanged(unit: Unit) = cardsAdapter.notifyDataSetChanged()
        fun searchStateChanged(searchState: SearchState) {
            Timber.d("search state: %s", searchState)
            when (searchState) {
                SearchState.Initializing -> { }
                SearchState.Searching -> {
                    invalidate()
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

        fun setupColumnSpinners() {
            // Create a spinner for column 1
            findViewById<Spinner>(R.id.browser_column1_spinner).apply {
                adapter = ArrayAdapter(
                    this@CardBrowser,
                    android.R.layout.simple_spinner_item,
                    viewModel.column1Candidates.map { it.getLabel(viewModel.cardsOrNotes) }
                ).apply {
                    setDropDownViewResource(R.layout.spinner_custom_layout)
                }
                setSelection(COLUMN1_KEYS.indexOf(viewModel.column1))
                onItemSelectedListener = BasicItemSelectedListener { pos, _ ->
                    viewModel.setColumn1(COLUMN1_KEYS[pos])
                }
            }

            // Setup the column 2 heading as a spinner so that users can easily change the column type
            findViewById<Spinner>(R.id.browser_column2_spinner).apply {
                adapter = ArrayAdapter(
                    this@CardBrowser,
                    android.R.layout.simple_spinner_item,
                    viewModel.column2Candidates.map { it.getLabel(viewModel.cardsOrNotes) }
                ).apply {
                    // The custom layout for the adapter is used to prevent the overlapping of various interactive components on the screen
                    setDropDownViewResource(R.layout.spinner_custom_layout)
                }
                setSelection(COLUMN2_KEYS.indexOf(viewModel.column2))
                // Create a new list adapter with updated column map any time the user changes the column
                onItemSelectedListener = BasicItemSelectedListener { pos, _ ->
                    viewModel.setColumn2(COLUMN2_KEYS[pos])
                }
            }
        }

        fun initCompletedChanged(completed: Boolean) {
            if (!completed) return

            setupColumnSpinners()
            searchCards()
        }

        @Suppress("UNCHECKED_CAST") // as? ArrayAdapter<String>?
        fun cardsOrNotesChanged(cardsOrNotes: CardsOrNotes) {
            Timber.d("mode change: %s - updating spinner titles", cardsOrNotes)
            findViewById<Spinner>(R.id.browser_column1_spinner)?.adapter?.apply {
                val adapter = this as? ArrayAdapter<String>? ?: return@apply
                adapter.clear()
                adapter.addAll(viewModel.column1Candidates.map { it.getLabel(cardsOrNotes) })
            }
            findViewById<Spinner>(R.id.browser_column2_spinner)?.adapter?.apply {
                val adapter = this as? ArrayAdapter<String>? ?: return@apply
                adapter.clear()
                adapter.addAll(viewModel.column2Candidates.map { it.getLabel(cardsOrNotes) })
            }
        }
        viewModel.flowOfIsTruncated.launchCollectionInLifecycleScope(::onIsTruncatedChanged)
        viewModel.flowOfSearchQueryExpanded.launchCollectionInLifecycleScope(::onSearchQueryExpanded)
        viewModel.flowOfSelectedRows.launchCollectionInLifecycleScope(::onSelectedRowsChanged)
        viewModel.flowOfColumn1.launchCollectionInLifecycleScope(::onColumn1Changed)
        viewModel.flowOfColumn2.launchCollectionInLifecycleScope(::onColumn2Changed)
        viewModel.flowOfFilterQuery.launchCollectionInLifecycleScope(::onFilterQueryChanged)
        viewModel.flowOfDeckId.launchCollectionInLifecycleScope(::onDeckIdChanged)
        viewModel.flowOfCanSearch.launchCollectionInLifecycleScope(::onCanSaveChanged)
        viewModel.flowOfIsInMultiSelectMode.launchCollectionInLifecycleScope(::isInMultiSelectModeChanged)
        viewModel.flowOfCardsUpdated.launchCollectionInLifecycleScope(::cardsUpdatedChanged)
        viewModel.flowOfSearchState.launchCollectionInLifecycleScope(::searchStateChanged)
        viewModel.flowOfInitCompleted.launchCollectionInLifecycleScope(::initCompletedChanged)
        viewModel.flowOfCardsOrNotes.launchCollectionInLifecycleScope(::cardsOrNotesChanged)
    }

    // Finish initializing the activity after the collection has been correctly loaded
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerReceiver()
        cards.reset()

        cardsListView.setOnItemClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
            if (viewModel.isInMultiSelectMode) {
                // click on whole cell triggers select
                val cb = view!!.findViewById<CheckBox>(R.id.card_checkbox)
                cb.toggle()
                viewModel.toggleRowSelectionAtPosition(position)
            } else {
                launchCatchingTask {
                    // load up the card selected on the list
                    val clickedCardId = viewModel.queryCardIdAtPosition(position)
                    saveScrollingState(position)
                    openNoteEditorForCard(clickedCardId)
                }
            }
        }
        @KotlinCleanup("helper function for min/max range")
        cardsListView.setOnItemLongClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
            if (viewModel.isInMultiSelectMode) {
                viewModel.selectRowsBetweenPositions(lastSelectedPosition, position)
            } else {
                launchCatchingTask {
                    lastSelectedPosition = position
                    saveScrollingState(position)

                    // click on whole cell triggers select
                    val cb = view!!.findViewById<CheckBox>(R.id.card_checkbox)
                    cb.toggle()
                    viewModel.toggleRowSelectionAtPosition(position)
                    recenterListView(view)
                    cardsAdapter.notifyDataSetChanged()
                }
            }
            true
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        deckSpinnerSelection.apply {
            initializeActionBarDeckSpinner(col, supportActionBar!!)
            launchCatchingTask { selectDeckById(viewModel.deckId ?: ALL_DECKS_ID, false) }
        }
    }

    suspend fun selectDeckAndSave(deckId: DeckId) {
        viewModel.setDeckId(deckId)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
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
                } else if (event.isAltPressed) {
                    Timber.i("Alt+K: Show keyboard shortcuts dialog")
                    showKeyboardShortcutsDialog()
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
        val flag = when (keyCode) {
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
    fun toggleMark() = launchCatchingTask {
        withProgress { viewModel.toggleMark() }
        cardsAdapter.notifyDataSetChanged()
    }

    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target  */
    @NeedsTest("note edits are saved")
    @NeedsTest("I/O edits are saved")
    private fun openNoteEditorForCard(cardId: CardId) {
        currentCardId = cardId
        val intent = NoteEditorLauncher.EditCard(currentCardId, Direction.DEFAULT).getIntent(this)
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
            viewModel.getRowAtPosition(0).id
        }
    }

    private fun openNoteEditorForCurrentlySelectedNote() = launchCatchingTask {
        // Check whether the deck is empty
        if (viewModel.rowCount == 0) {
            showSnackbar(
                R.string.no_note_to_edit,
                Snackbar.LENGTH_LONG
            )
            return@launchCatchingTask
        }

        try {
            val cardId = getCardIdForNoteEditor()
            openNoteEditorForCard(cardId)
        } catch (e: Exception) {
            Timber.w(e, "Error Opening Note Editor")
            showSnackbar(
                R.string.multimedia_editor_something_wrong,
                Snackbar.LENGTH_LONG
            )
        }
    }

    override fun onStop() {
        // cancel rendering the question and answer, which has shared access to mCards
        super.onStop()
        if (!isFinishing) {
            updateInBackground(this)
        }
    }

    override fun onDestroy() {
        invalidate()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            isDrawerOpen -> super.onBackPressed()
            viewModel.isInMultiSelectMode -> viewModel.endMultiSelectMode()
            else -> {
                Timber.i("Back key pressed")
                val data = Intent()
                // Add reload flag to result intent so that schedule reset when returning to note editor
                data.putExtra(NoteEditor.RELOAD_REQUIRED_EXTRA_KEY, reloadRequired)
                closeCardBrowser(RESULT_OK, data)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // If the user entered something into the search, but didn't press "search", clear this.
        // It's confusing if the bar is shown with a query that does not relate to the data on the screen
        viewModel.removeUnsubmittedInput()
        if (postAutoScroll) {
            postAutoScroll = false
        }
    }

    override fun onResume() {
        super.onResume()
        selectNavigationItem(R.id.nav_browser)
        updateNumCardsToRender()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNumCardsToRender()
    }

    @KotlinCleanup("Add a few variables to get rid of the !!")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        actionBarMenu = menu
        if (!viewModel.isInMultiSelectMode) {
            // restore drawer click listener and icon
            restoreDrawerIcon()
            menuInflater.inflate(R.menu.card_browser, menu)
            menu.findItem(R.id.action_search_by_flag).subMenu?.let {
                    subMenu ->
                setupFlags(subMenu, Mode.SINGLE_SELECT)
            }
            menu.findItem(R.id.action_create_filtered_deck).title = TR.qtMiscCreateFilteredDeck()
            saveSearchItem = menu.findItem(R.id.action_save_search)
            saveSearchItem?.isVisible = false // the searchview's query always starts empty.
            mySearchesItem = menu.findItem(R.id.action_list_my_searches)
            val savedFiltersObj = viewModel.savedSearchesUnsafe(getColUnsafe)
            mySearchesItem!!.isVisible = savedFiltersObj.size > 0
            searchItem = menu.findItem(R.id.action_search)
            searchItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    viewModel.setSearchQueryExpanded(true)
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    viewModel.setSearchQueryExpanded(false)
                    // SearchView doesn't support empty queries so we always reset the search when collapsing
                    searchView!!.setQuery("", false)
                    searchCards("")
                    return true
                }
            })
            searchView = (searchItem!!.actionView as CardBrowserSearchView).apply {
                queryHint = resources.getString(R.string.card_browser_search_hint)
                setMaxWidth(Integer.MAX_VALUE)
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextChange(newText: String): Boolean {
                        if (this@apply.ignoreValueChange) {
                            return true
                        }
                        viewModel.updateQueryText(newText)
                        return true
                    }

                    override fun onQueryTextSubmit(query: String): Boolean {
                        searchCards(query)
                        searchView!!.clearFocus()
                        return true
                    }
                })
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
            menu.findItem(R.id.action_flag).subMenu?.let {
                    subMenu ->
                setupFlags(subMenu, Mode.MULTI_SELECT)
            }
            showBackIcon()
            increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        }
        actionBarMenu?.findItem(R.id.action_undo)?.run {
            isVisible = getColUnsafe.undoAvailable()
            title = getColUnsafe.undoLabel()
        }

        actionBarMenu?.findItem(R.id.action_reschedule_cards)?.title =
            TR.actionsSetDueDate().toSentenceCase(this, R.string.sentence_set_due_date)

        previewItem = menu.findItem(R.id.action_preview)
        onSelectionChanged()
        updatePreviewMenuItem()
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Representing different selection modes.
     */
    enum class Mode(val value: Int) {
        SINGLE_SELECT(1000),
        MULTI_SELECT(1001)
    }

    private fun setupFlags(subMenu: SubMenu, mode: Mode) {
        lifecycleScope.launch {
            val groupId = when (mode) {
                Mode.SINGLE_SELECT -> mode.value
                Mode.MULTI_SELECT -> mode.value
            }

            for ((flag, displayName) in Flag.queryDisplayNames()) {
                subMenu.add(groupId, flag.code, Menu.NONE, displayName)
                    .setIcon(flag.drawableRes)
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
            this.title = if (viewModel.cardsOrNotes == CARDS) {
                resources.getQuantityString(
                    R.plurals.card_browser_export_cards,
                    viewModel.selectedRowCount()
                )
            } else {
                resources.getQuantityString(
                    R.plurals.card_browser_export_notes,
                    viewModel.selectedRowCount()
                )
            }
        }
        actionBarMenu.findItem(R.id.action_delete_card).apply {
            this.title = resources.getQuantityString(
                R.plurals.card_browser_delete_notes,
                viewModel.selectedNoteCount()
            )
        }
        actionBarMenu.findItem(R.id.action_select_all).isVisible = !hasSelectedAllCards()
        // Note: Theoretically should not happen, as this should kick us back to the menu
        actionBarMenu.findItem(R.id.action_select_none).isVisible =
            viewModel.hasSelectedAnyRows()
        actionBarMenu.findItem(R.id.action_edit_note).isVisible = canPerformMultiSelectEditNote()
        actionBarMenu.findItem(R.id.action_view_card_info).isVisible = canPerformCardInfo()
    }

    private fun hasSelectedAllCards(): Boolean {
        return viewModel.selectedRowCount() >= viewModel.rowCount // must handle 0.
    }

    private fun updateFlagForSelectedRows(flag: Flag) = launchCatchingTask {
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

            @NeedsTest("filter-marked query needs testing")
            R.id.action_show_marked -> {
                searchForMarkedNotes()
                return true
            }

            @NeedsTest("filter-suspended query needs testing")
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

    private fun changeDisplayOrder() {
        showDialogFragment(
            // TODO: move this into the ViewModel
            CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
            }
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
                    CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_LIST
                )
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
                CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE
            )
        )
    }

    private fun repositionSelectedCards(): Boolean {
        Timber.i("CardBrowser:: Reposition button pressed")
        if (warnUserIfInNotesOnlyMode()) return false
        launchCatchingTask {
            val selectedCardIds = viewModel.queryAllSelectedCardIds()
            // Only new cards may be repositioned (If any non-new found show error dialog and return false)
            if (selectedCardIds.any { getColUnsafe.getCard(it).queue != Consts.QUEUE_TYPE_NEW }) {
                showDialogFragment(
                    SimpleMessageDialog.newInstance(
                        title = getString(R.string.vague_error),
                        message = getString(R.string.reposition_card_not_new_error),
                        reload = false
                    )
                )
                return@launchCatchingTask
            }
            val repositionDialog = IntegerDialog().apply {
                setArgs(
                    title = this@CardBrowser.getString(R.string.reposition_card_dialog_title),
                    prompt = this@CardBrowser.getString(R.string.reposition_card_dialog_message),
                    digits = 5
                )
                setCallbackRunnable(::repositionCardsNoValidation)
            }
            showDialogFragment(repositionDialog)
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

    private fun exportSelected() = launchCatchingTask {
        val (type, selectedIds) = viewModel.querySelectionExportData() ?: return@launchCatchingTask
        ExportDialogFragment.newInstance(type, selectedIds).show(supportFragmentManager, "exportDialog")
    }

    private fun deleteSelectedNotes() = launchCatchingTask {
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
    fun repositionCardsNoValidation(position: Int) = launchCatchingTask {
        val count = withProgress { viewModel.repositionSelectedRows(position) }
        showSnackbar(
            resources.getQuantityString(
                R.plurals.reposition_card_dialog_acknowledge,
                count,
                count
            ),
            Snackbar.LENGTH_SHORT
        )
    }

    private fun onPreview() {
        launchCatchingTask {
            val intentData = viewModel.queryPreviewIntentData()
            onPreviewCardsActivityResult.launch(getPreviewIntent(intentData.currentIndex, intentData.previewerIdsFile))
        }
    }

    private fun getPreviewIntent(index: Int, previewerIdsFile: PreviewerIdsFile): Intent {
        return PreviewerDestination(index, previewerIdsFile).toIntent(this)
    }

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
        val dialog = newInstance(
            getString(R.string.move_all_to_deck),
            null,
            false,
            selectableDecks!!
        )
        // Add change deck argument so the dialog can be dismissed
        // after activity recreation, since the selected cards will be gone with it
        dialog.requireArguments().putBoolean(CHANGE_DECK_KEY, true)
        dialog.deckSelectionListener = DeckSelectionListener { deck: SelectableDeck? -> moveSelectedCardsToDeck(deck!!.deckId) }
        return dialog
    }

    private fun showChangeDeckDialog() = launchCatchingTask {
        if (!viewModel.hasSelectedAnyRows()) {
            Timber.i("Not showing Change Deck - No Cards")
            return@launchCatchingTask
        }
        val selectableDecks = getValidDecksForChangeDeck()
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
                val checkedTags = withCol {
                    selectedNoteIds
                        .asSequence() // reduce memory pressure
                        .flatMap { nid ->
                            progress++
                            getNote(nid).tags // requires withCol
                        }
                        .distinct()
                        .toList()
                }

                if (selectedNoteIds.size == 1) {
                    Timber.d("showEditTagsDialog: edit tags for one note")
                    tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
                    val dialog = tagsDialogFactory.newTagsDialog().withArguments(
                        this@CardBrowser,
                        type = TagsDialog.DialogType.EDIT_TAGS,
                        checkedTags = checkedTags,
                        allTags = allTags
                    )
                    showDialogFragment(dialog)
                    return@withProgress
                }
                // TODO!! This is terribly slow on AnKing
                // PERF: This MUST be combined with the above sequence - this becomes O(2n) on a
                // database operation performed over 30k times
                val uncheckedTags = withCol {
                    selectedNoteIds
                        .asSequence() // reduce memory pressure
                        .flatMap { nid: NoteId ->
                            progress++
                            val note = getNote(nid) // requires withCol
                            val noteTags = note.tags.toSet()
                            allTags.filter { t: String? -> !noteTags.contains(t) }
                        }
                        .distinct()
                        .toList()
                }

                progressMax = null

                Timber.d("showEditTagsDialog: edit tags for multiple note")
                tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS

                // withArguments performs IO, can be 18 seconds
                val dialog = withContext(Dispatchers.IO) {
                    tagsDialogFactory.newTagsDialog().withArguments(
                        context = this@CardBrowser,
                        type = TagsDialog.DialogType.EDIT_TAGS,
                        checkedTags = checkedTags,
                        uncheckedTags = uncheckedTags,
                        allTags = allTags
                    )
                }
                showDialogFragment(dialog)
            }
        }
    }

    private fun showFilterByTagsDialog() {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        val dialog = tagsDialogFactory.newTagsDialog().withArguments(
            context = this@CardBrowser,
            type = TagsDialog.DialogType.FILTER_BY_TAG,
            checkedTags = ArrayList(0),
            allTags = getColUnsafe.tags.all()
        )
        showDialogFragment(dialog)
    }

    private fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog.newInstance(viewModel.cardsOrNotes, viewModel.isTruncated)
        dialog.show(supportFragmentManager, "browserOptionsDialog")
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        // Save current search terms
        outState.putString("mSearchTerms", viewModel.searchTerms)
        outState.putLong("mOldCardId", oldCardId)
        outState.putInt("mOldCardTopOffset", oldCardTopOffset)
        outState.putBoolean("mShouldRestoreScroll", shouldRestoreScroll)
        outState.putBoolean("mPostAutoScroll", postAutoScroll)
        outState.putInt("mLastSelectedPosition", lastSelectedPosition)
        exportingDelegate.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        oldCardId = savedInstanceState.getLong("mOldCardId")
        oldCardTopOffset = savedInstanceState.getInt("mOldCardTopOffset")
        shouldRestoreScroll = savedInstanceState.getBoolean("mShouldRestoreScroll")
        postAutoScroll = savedInstanceState.getBoolean("mPostAutoScroll")
        lastSelectedPosition = savedInstanceState.getInt("mLastSelectedPosition")
        searchCards(savedInstanceState.getString("mSearchTerms", ""))
    }

    private fun invalidate() {
        renderBrowserQAJob?.cancel()
    }

    private fun forceRefreshSearch(useSearchTextValue: Boolean = false) {
        if (useSearchTextValue && searchView != null) {
            searchCards(searchView!!.query.toString())
        } else {
            searchCards()
        }
    }

    @RustCleanup("remove card cache; switch to RecyclerView and browserRowForId (#11889)")
    @VisibleForTesting
    fun searchCards() {
        launchCatchingTask {
            // TODO: Move this to a LinearProgressIndicator and remove withProgress
            withProgress { viewModel.launchSearchForCards()?.join() }
        }
    }

    @MainThread
    private fun redrawAfterSearch() {
        Timber.i("CardBrowser:: Completed searchCards() Successfully")
        updateList()
        /*check whether mSearchView is initialized as it is lateinit property.*/
        if (searchView == null || searchView!!.isIconified) {
            restoreScrollPositionIfRequested()
            return
        }
        if (hasSelectedAllDecks()) {
            showSnackbar(subtitleText, Snackbar.LENGTH_SHORT)
        } else {
            // If we haven't selected all decks, allow the user the option to search all decks.
            val message = if (viewModel.rowCount == 0) {
                getString(R.string.card_browser_no_cards_in_deck, selectedDeckNameForUi)
            } else {
                subtitleText
            }
            showSnackbar(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.card_browser_search_all_decks) { searchAllDecks() }
            }
        }
        restoreScrollPositionIfRequested()
        updatePreviewMenuItem()
    }

    /**
     * Restores the scroll position of the browser when requested (for example after editing a card)
     */
    @NeedsTest("Issue 14220: Ensure this is called if mSearchView == null. Use Espresso to test")
    private fun restoreScrollPositionIfRequested() {
        if (!shouldRestoreScroll) {
            Timber.d("Not restoring search position")
            return
        }
        shouldRestoreScroll = false
        val card = viewModel.findCardById(oldCardId) ?: return
        Timber.d("Restoring scroll position after search")
        autoScrollTo(card.position)
    }

    @VisibleForTesting
    protected open fun updateNumCardsToRender() {
        viewModel.numCardsToRender = ceil(
            (
                cardsListView.height /
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)
                ).toDouble()
        ).toInt() + 5
    }

    @MainThread
    private fun updateList() {
        if (colIsOpenUnsafe()) {
            cardsAdapter.notifyDataSetChanged()
            deckSpinnerSelection.notifyDataSetChanged()
            onSelectionChanged()
            updatePreviewMenuItem()
        }
    }

    /**
     * @return text to be used in the subtitle of the drop-down deck selector
     */
    override val subtitleText: String
        get() {
            val count = viewModel.rowCount

            @androidx.annotation.StringRes val subtitleId = if (viewModel.cardsOrNotes == CARDS) {
                R.plurals.card_browser_subtitle
            } else {
                R.plurals.card_browser_subtitle_notes_mode
            }
            return resources.getQuantityString(subtitleId, count, count)
        }

    /** Returns the decks which are valid targets for "Change Deck"  */
    suspend fun getValidDecksForChangeDeck(): List<DeckNameId> =
        deckSpinnerSelection.computeDropDownDecks(includeFiltered = false)

    @RustCleanup("this isn't how Desktop Anki does it")
    override fun onSelectedTags(selectedTags: List<String>, indeterminateTags: List<String>, stateFilter: CardStateFilter) {
        when (tagsDialogListenerAction) {
            TagsDialogListenerAction.FILTER -> filterByTags(selectedTags, stateFilter)
            TagsDialogListenerAction.EDIT_TAGS -> launchCatchingTask {
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
    private suspend fun editSelectedCardsTags(selectedTags: List<String>, indeterminateTags: List<String>) = withProgress {
        val selectedNoteIds = viewModel.queryAllSelectedNoteIds().distinct()
        undoableOp {
            val selectedNotes = selectedNoteIds
                .map { noteId -> getNote(noteId) }
                .onEach { note ->
                    val previousTags: List<String> = note.tags
                    val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                    note.setTagsFromStr(this@undoableOp, tags.join(updatedTags))
                }
            updateNotes(selectedNotes)
        }
    }

    private fun filterByTags(selectedTags: List<String>, cardState: CardStateFilter) =
        launchCatchingTask {
            viewModel.filterByTags(selectedTags, cardState)
        }

    /** Updates search terms to only show cards with selected flag.  */
    @VisibleForTesting
    fun filterByFlag(flag: Flag) = launchCatchingTask { viewModel.setFlagFilter(flag) }

    /**
     * Loads/Reloads (Updates the Q, A & etc) of cards in the [cardIds] list
     * @param cardIds Card IDs that were changed
     */
    private fun updateCardsInList(cardIds: List<CardId>) {
        val idToPos = viewModel.cardIdToPositionMap
        // TODO: Inefficient
        cardIds
            .mapNotNull { cid -> idToPos[cid] }
            .filterNot { pos -> pos >= viewModel.rowCount }
            .map { pos -> viewModel.getRowAtPosition(pos) }
            .forEach { it.load(true, viewModel.column1, viewModel.column2) }
        updateList()
    }

    private fun saveEditedCard() {
        Timber.d("CardBrowser - saveEditedCard()")
        updateCardsInList(listOf(currentCardId))
    }

    /**
     * Removes cards from view. Doesn't delete them in model (database).
     * @param reorderCards Whether to rearrange the positions of checked items (DEFECT: Currently deselects all)
     */
    private fun removeNotesView(cardsIds: List<Long>, reorderCards: Boolean) {
        val idToPos = viewModel.cardIdToPositionMap
        val idToRemove = cardsIds.filter { cId -> idToPos.containsKey(cId) }
        reloadRequired = reloadRequired || cardsIds.contains(reviewerCardId)
        val newMCards: MutableList<CardCache> = cards
            .filterNot { c -> idToRemove.contains(c.id) }
            .mapIndexed { i, c -> CardCache(c, i) }
            .toMutableList()
        cards.replaceWith(newMCards)
        if (reorderCards) {
            // Suboptimal from a UX perspective, we should reorder
            // but this is only hit on a rare sad path and we'd need to rejig the data structures to allow an efficient
            // search
            Timber.w("Removing current selection due to unexpected removal of cards")
            viewModel.selectNone()
        }
        updateList()
    }

    private fun toggleSuspendCards() = launchCatchingTask { withProgress { viewModel.toggleSuspendCards().join() } }

    /** @see CardBrowserViewModel.toggleBury */
    private fun toggleBury() = launchCatchingTask {
        val result = withProgress { viewModel.toggleBury() } ?: return@launchCatchingTask
        // show a snackbar as there's currently no colored background for buried cards
        val message = when (result.wasBuried) {
            true -> TR.studyingCardsBuried(result.count)
            false -> resources.getQuantityString(R.plurals.unbury_cards_feedback, result.count, result.count)
        }
        showUndoSnackbar(message)
    }

    private fun showUndoSnackbar(message: CharSequence) {
        showSnackbar(message, Snackbar.LENGTH_LONG) {
            setAction(R.string.undo) { launchCatchingTask { undoAndShowSnackbar() } }
            undoSnackbar = this
        }
    }

    private fun refreshAfterUndo() {
        hideProgressBar()
        // reload whole view
        forceRefreshSearch()
        viewModel.endMultiSelectMode()
        cardsAdapter.notifyDataSetChanged()
        updatePreviewMenuItem()
        invalidateOptionsMenu() // maybe the availability of undo changed
    }
    private suspend fun saveScrollingState(position: Int) {
        oldCardId = viewModel.queryCardIdAtPosition(position)
        oldCardTopOffset = calculateTopOffset(position)
    }

    private fun autoScrollTo(newPosition: Int) {
        cardsListView.setSelectionFromTop(newPosition, oldCardTopOffset)
        postAutoScroll = true
    }

    private fun calculateTopOffset(cardPosition: Int): Int {
        val firstVisiblePosition = cardsListView.firstVisiblePosition
        val v = cardsListView.getChildAt(cardPosition - firstVisiblePosition)
        return v?.top ?: 0
    }

    fun hasSelectedAllDecks(): Boolean = viewModel.lastDeckId == ALL_DECKS_ID

    fun searchAllDecks() = launchCatchingTask {
        // all we need to do is select all decks
        viewModel.setDeckId(ALL_DECKS_ID)
    }

    /**
     * Returns the current deck name, "All Decks" if all decks are selected, or "Unknown"
     * Do not use this for any business logic, as this will return inconsistent data
     * with the collection.
     */
    val selectedDeckNameForUi: String
        get() = try {
            when (val deckId = viewModel.lastDeckId) {
                null -> getString(R.string.card_browser_unknown_deck_name)
                ALL_DECKS_ID -> getString(R.string.card_browser_all_decks)
                else -> getColUnsafe.decks.name(deckId)
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to get selected deck name")
            getString(R.string.card_browser_unknown_deck_name)
        }

    private fun onPostExecuteRenderBrowserQA(result: Pair<List<CardCache>, List<Long>>) {
        val cardsIdsToHide = result.second
        try {
            if (cardsIdsToHide.isNotEmpty()) {
                Timber.i("Removing %d invalid cards from view", cardsIdsToHide.size)
                removeNotesView(cardsIdsToHide, true)
            }
        } catch (e: Exception) {
            Timber.e(e, "failed to hide cards")
        }
        hideProgressBar() // Some places progressbar is launched explicitly, so hide it
        cardsAdapter.notifyDataSetChanged()
        Timber.d("Completed doInBackgroundRenderBrowserQA Successfully")
    }

    private fun closeCardBrowser(result: Int, data: Intent? = null) {
        // Set result and finish
        setResult(result, data)
        finish()
    }

    /**
     * Render the second column whenever the user stops scrolling
     */
    @VisibleForTesting
    inner class RenderOnScroll : AbsListView.OnScrollListener {
        override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
            // Show the progress bar if scrolling to given position requires rendering of the question / answer
            val lastVisibleItem = firstVisibleItem + visibleItemCount - 1
            // List is never cleared, only reset to a new list. So it's safe here.
            val size = viewModel.rowCount
            if (size > 0 && visibleItemCount <= 0) {
                // According to Mike, there used to be 5 to 10 report by hour on the beta version. All with
                // > com.ichi2.anki.exception.ManuallyReportedException: Useless onScroll call, with size 0 firstVisibleItem 0,
                // > lastVisibleItem 0 and visibleItemCount 0.

                // This change ensure that we log more specifically case where #8821 could have occurred. That is, there are cards but we
                // are asked to display nothing.

                // Note that this is not a bug. The fact that `visibleItemCount` is equal to 0 is actually authorized by the method we
                // override and mentioned in the javadoc. It perfectly makes sens to get this order, since it can be used to know that we
                // can delete some elements from the cache for example, since nothing is displayed.

                // It would be interesting to know how often it occurs, but it is not a bug.
                // CrashReportService.sendExceptionReport("CardBrowser Scroll Issue 8821", "In a search result of $size cards, with totalItemCount = $totalItemCount, somehow we got $visibleItemCount elements to display.")
                Timber.w("CardBrowser Scroll Issue 15441/8821: In a search result of $size cards, with totalItemCount = $totalItemCount, somehow we got $visibleItemCount elements to display.")
            }
            // In all of those cases, there is nothing to do:
            if (size <= 0 || firstVisibleItem >= size || lastVisibleItem >= size || visibleItemCount <= 0) {
                return
            }
            val firstLoaded = viewModel.getRowAtPosition(firstVisibleItem).isLoaded
            // Note: max value of lastVisibleItem is totalItemCount, so need to subtract 1
            val lastLoaded = viewModel.getRowAtPosition(lastVisibleItem).isLoaded
            if (!firstLoaded || !lastLoaded) {
                if (!postAutoScroll) {
                    showProgressBar()
                }
                // Also start rendering the items on the screen every 300ms while scrolling
                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastRenderStart > 300 || lastVisibleItem + 1 >= totalItemCount) {
                    lastRenderStart = currentTime
                    renderBrowserQAJob?.cancel()
                    launchCatchingTask { renderBrowserQAParams(firstVisibleItem, visibleItemCount, viewModel.cards.toList()) }
                }
            }
        }

        override fun onScrollStateChanged(listView: AbsListView, scrollState: Int) {
            // TODO: Try change to RecyclerView as currently gets stuck a lot when using scrollbar on right of ListView
            // Start rendering the question & answer every time the user stops scrolling
            if (postAutoScroll) {
                postAutoScroll = false
            }
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                val startIdx = listView.firstVisiblePosition
                val numVisible = listView.lastVisiblePosition - startIdx
                launchCatchingTask { renderBrowserQAParams(startIdx - 5, 2 * numVisible + 5, viewModel.cards.toList()) }
            }
        }
    }

    // TODO: Improve progress bar handling in places where this function is used
    protected suspend fun renderBrowserQAParams(firstVisibleItem: Int, visibleItemCount: Int, cards: List<CardCache>) {
        Timber.d("Starting Q&A background rendering")
        val result = renderBrowserQA(
            cards,
            firstVisibleItem,
            visibleItemCount,
            viewModel.column1,
            viewModel.column2
        ) {
            // Note: This is called every time a card is rendered.
            // It blocks the long-click callback while the task is running, so usage of the task should be minimized
            cardsAdapter.notifyDataSetChanged()
        }
        onPostExecuteRenderBrowserQA(result)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    inner class MultiColumnListAdapter(
        context: Context?,
        private val resource: Int,
        private var fromKeys: Array<CardBrowserColumn>,
        private val toIds: IntArray,
        private val fontSizeScalePcent: Int
    ) : BaseAdapter() {
        private var originalTextSize = -1.0f
        private val inflater: LayoutInflater
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            // Get the main container view if it doesn't already exist, and call bindView
            val v: View
            if (convertView == null) {
                v = inflater.inflate(resource, parent, false)
                val count = toIds.size
                val columns = arrayOfNulls<View>(count)
                for (i in 0 until count) {
                    columns[i] = v.findViewById(toIds[i])
                }
                v.tag = columns
            } else {
                v = convertView
            }
            bindView(position, v)
            return v
        }

        @KotlinCleanup("Unchecked cast")
        private fun bindView(position: Int, v: View) {
            // Draw the content in the columns
            val card = getItem(position)
            (v.tag as Array<*>)
                .forEachIndexed { i, column ->
                    setFont(column as TextView) // set font for column
                    column.text = card.getColumnHeaderText(fromKeys[i]) // set text for column
                }
            // set card's background color
            val backgroundColor: Int = card.getBackgroundColor(this@CardBrowser)
            v.setBackgroundColor(backgroundColor)
            // setup checkbox to change color in multi-select mode
            val checkBox = v.findViewById<CheckBox>(R.id.card_checkbox)
            // if in multi-select mode, be sure to show the checkboxes
            if (viewModel.isInMultiSelectMode) {
                checkBox.visibility = View.VISIBLE
                checkBox.isChecked = viewModel.selectedRows.contains(card)
                // this prevents checkboxes from showing an animation from selected -> unselected when
                // checkbox was selected, then selection mode was ended and now restarted
                checkBox.jumpDrawablesToCurrentState()
            } else {
                checkBox.isChecked = false
                checkBox.visibility = View.GONE
            }
            // change bg color on check changed
            checkBox.setOnClickListener { viewModel.toggleRowSelectionAtPosition(position) }
            val column1 = v.findViewById<FixedTextView>(R.id.card_sfld)
            val column2 = v.findViewById<FixedTextView>(R.id.card_column2)

            if (viewModel.isTruncated) {
                column1.maxLines = LINES_VISIBLE_WHEN_COLLAPSED
                column2.maxLines = LINES_VISIBLE_WHEN_COLLAPSED
                column1.ellipsize = TextUtils.TruncateAt.END
                column2.ellipsize = TextUtils.TruncateAt.END
            } else {
                column1.maxLines = Integer.MAX_VALUE
                column2.maxLines = Integer.MAX_VALUE
            }
        }

        private fun setFont(v: TextView) {
            // Set the font and font size for a TextView v
            val currentSize = v.textSize
            if (originalTextSize < 0) {
                originalTextSize = v.textSize
            }
            // do nothing when pref is 100% and apply scaling only once
            if (fontSizeScalePcent != 100 && abs(originalTextSize - currentSize) < 0.1) {
                // getTextSize returns value in absolute PX so use that in the setter
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize * (fontSizeScalePcent / 100.0f))
            }
        }

        private var fromMapping: Array<CardBrowserColumn>
            get() = fromKeys
            set(from) {
                fromKeys = from
                notifyDataSetChanged()
            }

        fun updateMapping(fn: (Array<CardBrowserColumn>) -> Unit) {
            val fromMap = fromMapping
            fn(fromMap)
            // this doesn't need to be run on the UI thread: this calls notifyDataSetChanged()
            fromMapping = fromMap
        }

        override fun getCount(): Int {
            return viewModel.rowCount
        }

        override fun getItem(position: Int): CardCache = viewModel.getRowAtPosition(position)

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        init {
            inflater = LayoutInflater.from(context)
        }
    }

    fun onSelectionChanged() {
        Timber.d("onSelectionChanged()")
        try {
            // If we're not in mutliselect, we can select cards if there are cards to select
            if (!viewModel.isInMultiSelectMode) {
                actionBarMenu?.findItem(R.id.action_select_all)?.apply {
                    isVisible = viewModel.rowCount != 0
                }
                return
            }

            // set the number of selected rows (only in multiselect)
            actionBarTitle.text = String.format(LanguageUtil.getLocaleCompat(resources), "%d", viewModel.selectedRowCount())
            updateMultiselectMenu()
        } finally {
            if (colIsOpenUnsafe()) {
                cardsAdapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Implementation of `by viewModels()` for use in [onCreate]
     *
     * @see showedActivityFailedScreen - we may not have AnkiDroidApp.instance and therefore can't
     * create the ViewModel
     */
    private fun createViewModel(launchOptions: CardBrowserLaunchOptions?) = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = cacheDir,
            options = launchOptions
        ),
        defaultViewModelCreationExtras
    )[CardBrowserViewModel::class.java]

    // This could be better: use a wrapper class PositionAware<T> to store the position so it's
    // no longer a responsibility of CardCache and we can guarantee it's consistent just by using this collection
    /** A position-aware collection to ensure consistency between the position of items and the collection  */
    class CardCollection<T : PositionAware?> : Iterable<T> {
        var wrapped: MutableList<T> = ArrayList(0)
            private set
        fun size(): Int {
            return wrapped.size
        }

        operator fun get(index: Int): T {
            return wrapped[index]
        }

        fun reset() {
            wrapped = ArrayList(0)
        }

        fun replaceWith(value: MutableList<T>) {
            wrapped = value
        }

        fun reverse() {
            wrapped.reverse()
            wrapped.forEachIndexed { pos, card -> card!!.position = pos }
        }

        override fun iterator(): MutableIterator<T> {
            return wrapped.iterator()
        }

        fun clear() {
            wrapped.clear()
        }
    }

    @VisibleForTesting
    interface PositionAware {
        var position: Int
    }

    class CardCache : Card.Cache, PositionAware {
        var isLoaded = false
            private set
        private var qa: Pair<String, String>? = null
        override var position: Int

        private val inCardMode: Boolean
        constructor(id: Long, col: Collection, position: Int, cardsOrNotes: CardsOrNotes) : super(col, id) {
            this.position = position
            this.inCardMode = cardsOrNotes == CARDS
        }

        constructor(cache: CardCache, position: Int) : super(cache) {
            isLoaded = cache.isLoaded
            qa = cache.qa
            this.position = position
            this.inCardMode = cache.inCardMode
        }

        /** clear all values except ID. */
        override fun reload() {
            super.reload()
            isLoaded = false
            qa = null
        }

        /**
         * Get the background color of items in the card list based on the Card
         * @return index into TypedArray specifying the background color
         */
        @ColorInt
        fun getBackgroundColor(context: Context): Int {
            val flagColor = card.userFlag().browserColorRes
            if (flagColor != null) {
                return context.getColor(flagColor)
            }
            val colorAttr = if (isMarked(col, card.note(col))) {
                R.attr.markedColor
            } else if (card.queue == Consts.QUEUE_TYPE_SUSPENDED) {
                R.attr.suspendedColor
            } else {
                android.R.attr.colorBackground
            }
            return Themes.getColorFromAttr(context, colorAttr)
        }

        fun getColumnHeaderText(key: CardBrowserColumn?): String? {
            return when (key) {
                CardBrowserColumn.SFLD -> card.note(col).sFld(col)
                CardBrowserColumn.DECK -> col.decks.name(card.did)
                CardBrowserColumn.TAGS -> card.note(col).stringTags(col)
                CardBrowserColumn.CARD -> if (inCardMode) card.template(col).optString("name") else "${card.note(col).numberOfCards(col)}"
                CardBrowserColumn.DUE -> dueString(col, card)
                CardBrowserColumn.EASE -> if (inCardMode) getEaseForCards() else getAvgEaseForNotes()
                CardBrowserColumn.CHANGED -> LanguageUtil.getShortDateFormatFromS(if (inCardMode) card.mod else card.note(col).mod.toLong())
                CardBrowserColumn.CREATED -> LanguageUtil.getShortDateFormatFromMs(card.nid)
                CardBrowserColumn.EDITED -> LanguageUtil.getShortDateFormatFromS(card.note(col).mod)
                CardBrowserColumn.INTERVAL -> if (inCardMode) queryIntervalForCards() else queryAvgIntervalForNotes()
                CardBrowserColumn.LAPSES -> (if (inCardMode) card.lapses else card.totalLapsesOfNote(col)).toString()
                CardBrowserColumn.NOTE_TYPE -> card.noteType(col).optString("name")
                CardBrowserColumn.REVIEWS -> if (inCardMode) card.reps.toString() else card.totalReviewsForNote(col).toString()
                CardBrowserColumn.QUESTION -> {
                    updateSearchItemQA()
                    qa!!.first
                }
                CardBrowserColumn.ORIGINAL_POSITION -> card.originalPosition?.toString().orEmpty()
                CardBrowserColumn.ANSWER -> {
                    updateSearchItemQA()
                    qa!!.second
                }
                else -> null
            }
        }

        private fun getEaseForCards(): String {
            return if (card.type == Consts.CARD_TYPE_NEW) {
                AnkiDroidApp.instance.getString(R.string.card_browser_interval_new_card)
            } else {
                "${card.factor / 10}%"
            }
        }

        private fun getAvgEaseForNotes(): String {
            val avgEase = NoteService.avgEase(col, card.note(col))

            return if (avgEase == null) {
                AnkiDroidApp.instance.getString(R.string.card_browser_interval_new_card)
            } else {
                "$avgEase%"
            }
        }

        private fun queryIntervalForCards(): String {
            return when (card.type) {
                Consts.CARD_TYPE_NEW -> AnkiDroidApp.instance.getString(R.string.card_browser_interval_new_card)
                Consts.CARD_TYPE_LRN -> AnkiDroidApp.instance.getString(R.string.card_browser_interval_learning_card)
                else -> roundedTimeSpanUnformatted(AnkiDroidApp.instance, card.ivl * SECONDS_PER_DAY)
            }
        }

        private fun queryAvgIntervalForNotes(): String {
            val avgInterval = card.avgIntervalOfNote(col)

            return if (avgInterval == null) {
                "" // upstream does not display interval for notes with new or learning cards
            } else {
                roundedTimeSpanUnformatted(AnkiDroidApp.instance, avgInterval * SECONDS_PER_DAY)
            }
        }

        /** pre compute the note and question/answer.  It can safely
         * be called twice without doing extra work.  */
        fun load(reload: Boolean, column1: CardBrowserColumn, column2: CardBrowserColumn) {
            if (reload) {
                reload()
            }
            card.note(col)
            // First column can not be the answer. If it were to change, this code should also be changed.
            if (column1 == CardBrowserColumn.QUESTION || arrayOf(CardBrowserColumn.QUESTION, CardBrowserColumn.ANSWER).contains(column2)) {
                updateSearchItemQA()
            }
            isLoaded = true
        }

        /**
         * Reload question and answer. Use browser format. If it's empty
         * uses non-browser format. If answer starts by question, remove
         * question.
         */
        private fun updateSearchItemQA() {
            if (qa != null) {
                return
            }
            // render question and answer
            val qa = card.renderOutput(col, reload = true, browser = true)
            // Render full question / answer if the bafmt (i.e. "browser appearance") setting forced blank result
            if (qa.questionText.isEmpty() || qa.answerText.isEmpty()) {
                val (questionText, answerText) = card.renderOutput(
                    col,
                    reload = true,
                    browser = false
                )
                if (qa.questionText.isEmpty()) {
                    qa.questionText = questionText
                }
                if (qa.answerText.isEmpty()) {
                    qa.answerText = answerText
                }
            }
            // update the original hash map to include rendered question & answer
            var q = qa.questionText
            var a = qa.answerText
            // remove the question from the start of the answer if it exists
            if (a.startsWith(q)) {
                a = a.substring(q.length)
            }
            a = formatQA(a, qa, AnkiDroidApp.instance)
            q = formatQA(q, qa, AnkiDroidApp.instance)
            this.qa = Pair(q, a)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null) {
                return false
            }
            return if (javaClass != other.javaClass) {
                false
            } else {
                id == (other as CardCache).id
            }
        }

        override fun hashCode(): Int {
            return java.lang.Long.valueOf(id).hashCode()
        }
    }

    /**
     * The views expand / contract when switching between multi-select mode so we manually
     * adjust so that the vertical position of the given view is maintained
     */
    private fun recenterListView(view: View) {
        val position = cardsListView.getPositionForView(view)
        // Get the current vertical position of the top of the selected view
        val top = view.top
        // Post to event queue with some delay to give time for the UI to update the layout
        postDelayedOnNewHandler({
            // Scroll to the same vertical position before the layout was changed
            cardsListView.setSelectionFromTop(position, top)
        }, 10)
    }

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val isShowingSelectAll: Boolean
        get() = actionBarMenu?.findItem(R.id.action_select_all)?.isVisible == true

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val isShowingSelectNone: Boolean
        get() = actionBarMenu?.findItem(R.id.action_select_none)?.isVisible == true

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun clearCardData(position: Int) {
        viewModel.getRowAtPosition(position).reload()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun rerenderAllCards() {
        renderBrowserQAParams(0, viewModel.rowCount - 1, viewModel.cards.toList())
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun filterByTag(vararg tags: String) {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        onSelectedTags(tags.toList(), emptyList(), CardStateFilter.ALL_CARDS)
        filterByTags(tags.toList(), CardStateFilter.ALL_CARDS)
    }

    @VisibleForTesting
    fun searchCards(searchQuery: String) =
        launchCatchingTask {
            withProgress { viewModel.launchSearchForCards(searchQuery)?.join() }
        }

    override fun opExecuted(changes: OpChanges, handler: Any?) {
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
        get() = ShortcutGroup(
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
                shortcut("Ctrl+7", R.string.gesture_flag_purple)
            ),
            R.string.card_browser_context_menu
        )

    companion object {
        /**
         * Argument key to add on change deck dialog,
         * so it can be dismissed on activity recreation,
         * since the cards are unselected when this happens
         */
        private const val CHANGE_DECK_KEY = "CHANGE_DECK"
        private const val DEFAULT_FONT_SIZE_RATIO = 100

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val LINES_VISIBLE_WHEN_COLLAPSED = 3

        // Values related to persistent state data
        private const val ALL_DECKS_ID = 0L

        fun clearLastDeckId() = SharedPreferencesLastDeckIdRepository.clearLastDeckId()

        @VisibleForTesting
        fun createAddNoteIntent(context: Context, viewModel: CardBrowserViewModel): Intent {
            return NoteEditorLauncher.AddNoteFromCardBrowser(viewModel).getIntent(context)
        }

        @CheckResult
        private fun formatQA(
            text: String,
            qa: TemplateManager.TemplateRenderContext.TemplateRenderOutput,
            context: Context
        ): String {
            val showFilenames =
                context.sharedPrefs().getBoolean("card_browser_show_media_filenames", false)
            return formatQAInternal(text, qa, showFilenames)
        }

        /**
         * @param txt The text to strip HTML, comments, tags and media from
         * @param showFileNames Whether [sound:foo.mp3] should be rendered as " foo.mp3 " or  " "
         * @return The formatted string
         */
        @VisibleForTesting
        @CheckResult
        fun formatQAInternal(
            txt: String,
            qa: TemplateManager.TemplateRenderContext.TemplateRenderOutput,
            showFileNames: Boolean
        ): String {
            /* Strips all formatting from the string txt for use in displaying question/answer in browser */
            var s = txt
            s = s.replace("<!--.*?-->".toRegex(), "")
            s = s.replace("<br>", " ")
            s = s.replace("<br />", " ")
            s = s.replace("<div>", " ")
            s = s.replace("\n", " ")
            // we use " " as often users won't leave a space between the '[sound:] tag
            // and continuation of the content
            s = if (showFileNames) Sound.replaceWithFileNames(s, qa) else stripAvRefs(s, " ")
            s = s.replace("\\[\\[type:[^]]+]]".toRegex(), "")
            s = if (showFileNames) Utils.stripHTMLMedia(s) else Utils.stripHTMLMedia(s, " ")
            s = s.trim { it <= ' ' }
            return s
        }

        fun dueString(col: Collection, card: Card): String {
            var t = nextDue(col, card)
            if (card.queue < 0) {
                t = "($t)"
            }
            return t
        }

        @VisibleForTesting
        fun nextDue(col: Collection, card: Card): String {
            val date: Long
            val due = card.due
            date = if (card.isInDynamicDeck) {
                return AnkiDroidApp.appResources.getString(R.string.card_browser_due_filtered_card)
            } else if (card.queue == Consts.QUEUE_TYPE_LRN) {
                due.toLong()
            } else if (card.queue == Consts.QUEUE_TYPE_NEW || card.type == Consts.CARD_TYPE_NEW) {
                return due.toString()
            } else if (card.queue == Consts.QUEUE_TYPE_REV || card.queue == Consts.QUEUE_TYPE_DAY_LEARN_RELEARN || card.type == Consts.CARD_TYPE_REV && card.queue < 0) {
                val time = TimeManager.time.intTime()
                val nbDaySinceCreation = due - col.sched.today
                time + nbDaySinceCreation * SECONDS_PER_DAY
            } else {
                return ""
            }
            return LanguageUtil.getShortDateFormatFromS(date)
        } // In Anki Desktop, a card with oDue <> 0 && oDid == 0 is not marked as dynamic.
    }

    private fun <T> Flow<T>.launchCollectionInLifecycleScope(block: suspend (T) -> Unit) {
        lifecycleScope.launch {
            this@launchCollectionInLifecycleScope.collect {
                if (isRobolectric) {
                    // hack: lifecycleScope/runOnUiThread do not handle our
                    // test dispatcher overriding both IO and Main
                    // in tests, waitForAsyncTasksToComplete may be required.
                    HandlerUtils.postOnNewHandler { runBlocking { block(it) } }
                } else {
                    block(it)
                }
            }
        }
    }
}

suspend fun searchForCards(
    query: String,
    order: SortOrder,
    cardsOrNotes: CardsOrNotes
): MutableList<CardBrowser.CardCache> {
    return withCol {
        (if (cardsOrNotes == CARDS) findCards(query, order) else findOneCardByNote(query, order)).asSequence()
            .toCardCache(this@withCol, cardsOrNotes)
            .toMutableList()
    }
}

private fun Sequence<CardId>.toCardCache(col: Collection, isInCardMode: CardsOrNotes): Sequence<CardBrowser.CardCache> {
    return this.mapIndexed { idx, cid -> CardBrowser.CardCache(cid, col, idx, isInCardMode) }
}

class PreviewerDestination(val currentIndex: Int, val previewerIdsFile: PreviewerIdsFile)

@CheckResult
fun PreviewerDestination.toIntent(context: Context) =
    PreviewerFragment.getIntent(context, previewerIdsFile, currentIndex)
