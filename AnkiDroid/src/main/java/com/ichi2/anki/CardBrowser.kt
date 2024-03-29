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

import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.ThemeUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import anki.collection.OpChanges
import com.afollestad.materialdialogs.utils.MDUtil.ifNotZero
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.CardBrowserColumn
import com.ichi2.anki.browser.CardBrowserColumn.Companion.COLUMN1_KEYS
import com.ichi2.anki.browser.CardBrowserColumn.Companion.COLUMN2_KEYS
import com.ichi2.anki.browser.CardBrowserLaunchOptions
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.CardBrowserViewModel.*
import com.ichi2.anki.browser.PreviewerIdsFile
import com.ichi2.anki.browser.SaveSearchResult
import com.ichi2.anki.browser.SharedPreferencesLastDeckIdRepository
import com.ichi2.anki.browser.toCardBrowserLaunchOptions
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.Companion.newInstance
import com.ichi2.anki.dialogs.CardBrowserMySearchesDialog.MySearchesDialogListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.RescheduleDialog.Companion.rescheduleMultipleCards
import com.ichi2.anki.dialogs.RescheduleDialog.Companion.rescheduleSingleCard
import com.ichi2.anki.dialogs.tags.TagsDialog
import com.ichi2.anki.dialogs.tags.TagsDialogFactory
import com.ichi2.anki.dialogs.tags.TagsDialogListener
import com.ichi2.anki.export.ActivityExportingDelegate
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.export.ExportDialogsFactory
import com.ichi2.anki.export.ExportDialogsFactoryProvider
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.*
import com.ichi2.anki.model.SortType
import com.ichi2.anki.noteeditor.EditCardDestination
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.anki.pages.CardInfo.Companion.toIntent
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.servicelayer.NoteService
import com.ichi2.anki.servicelayer.NoteService.isMarked
import com.ichi2.anki.servicelayer.avgIntervalOfNote
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.servicelayer.totalLapsesOfNote
import com.ichi2.anki.servicelayer.totalReviewsForNote
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.BasicItemSelectedListener
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.anki.utils.roundedTimeSpanUnformatted
import com.ichi2.anki.widgets.DeckDropDownAdapter.SubtitleListener
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.*
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.ui.CardBrowserSearchView
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.*
import com.ichi2.utils.HandlerUtils.postDelayedOnNewHandler
import com.ichi2.utils.TagsUtil.getUpdatedTags
import com.ichi2.widget.WidgetStatus.updateInBackground
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.util.*
import java.util.function.Consumer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

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

    @NeedsTest("15448: double-selecting deck does nothing")
    override fun onDeckSelected(deck: SelectableDeck?) {
        deck?.let {
            launchCatchingTask { selectDeckAndSave(deck.deckId) }
        }
    }

    private enum class TagsDialogListenerAction {
        FILTER, EDIT_TAGS
    }

    lateinit var viewModel: CardBrowserViewModel

    private var launchOptions: CardBrowserLaunchOptions? = null

    /** List of cards in the browser.
     * When the list is changed, the position member of its elements should get changed. */
    private val cards get() = viewModel.cards
    var deckSpinnerSelection: DeckSpinnerSelection? = null

    @VisibleForTesting
    lateinit var cardsListView: ListView
    private var searchView: CardBrowserSearchView? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var cardsAdapter: MultiColumnListAdapter

    private var searchTerms
        get() = viewModel.searchTerms
        set(value) { viewModel.searchTerms = value }
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
            launchCatchingTask { saveEditedCard() }
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
    private val onboarding = Onboarding.CardBrowser(this)

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private var unmountReceiver: BroadcastReceiver? = null

    init {
        ChangeManager.subscribe(this)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    fun changeCardOrder(which: SortType) {
        when (viewModel.changeCardOrder(which)) {
            ChangeCardOrderResult.OrderChange -> { searchCards() }
            ChangeCardOrderResult.DirectionChange -> {
                cards.reverse()
                updateList()
            }
            null -> {}
        }
    }

    @VisibleForTesting
    internal val mySearchesDialogListener: MySearchesDialogListener = object : MySearchesDialogListener {

        override fun onSelection(searchName: String) {
            Timber.d("OnSelection using search named: %s", searchName)
            launchCatchingTask {
                viewModel.savedSearches()[searchName]?.apply {
                    Timber.d("OnSelection using search terms: %s", this)
                    searchTerms = this
                    searchView!!.setQuery(this, false)
                    searchItem!!.expandActionView()
                    searchCards()
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

    private fun onSearch() {
        searchTerms = searchView!!.query.toString()
        if (searchTerms.isEmpty()) {
            searchView!!.queryHint = resources.getString(R.string.deck_conf_cram_search)
        }
        searchCards()
    }

    private val selectedRowIds: List<CardId>
        get() = viewModel.selectedRowIds

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
            val changed = withProgress {
                val selectedCardIds = viewModel.queryAllSelectedCardIds()
                undoableOp {
                    setDeck(selectedCardIds, did)
                }
            }
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
        // must be called once we have an accessible collection
        viewModel = createViewModel()

        launchOptions = intent?.toCardBrowserLaunchOptions() // must be called after super.onCreate()
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
            COLUMN1_KEYS[viewModel.column1Index],
            COLUMN2_KEYS[viewModel.column2Index]
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
        startLoadingCollection()

        when (val options = launchOptions) {
            is CardBrowserLaunchOptions.DeepLink -> {
                searchTerms = options.search
                searchCards()
            }
            is CardBrowserLaunchOptions.SearchQueryJs -> {
                searchTerms = options.search
                if (options.allDecks) {
                    onDeckSelected(SelectableDeck(ALL_DECKS_ID, getString(R.string.card_browser_all_decks)))
                }
                searchCards()
            }
            else -> {} // Context Menu handled in onCreateOptionsMenu
        }
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
        onboarding.onCreate()

        viewModel.flowOfIsTruncated.launchCollectionInLifecycleScope { cardsAdapter.notifyDataSetChanged() }

        viewModel.flowOfCardsOrNotes
            .launchCollectionInLifecycleScope { runOnUiThread { searchCards() } }

        viewModel.flowOfSearchQueryExpanded
            .launchCollectionInLifecycleScope { searchQueryExpanded ->
                Timber.d("query expansion changed: %b", searchQueryExpanded)
                if (searchQueryExpanded) {
                    runOnUiThread { searchItem?.expandActionView() }
                } else {
                    runOnUiThread { searchItem?.collapseActionView() }
                    // invalidate options menu so that disappeared icons would appear again
                    invalidateOptionsMenu()
                }
            }

        viewModel.flowOfSelectedRows
            .launchCollectionInLifecycleScope { runOnUiThread { onSelectionChanged() } }

        viewModel.flowOfColumnIndex1
            .launchCollectionInLifecycleScope { index -> cardsAdapter.updateMapping { it[0] = COLUMN1_KEYS[index] } }

        viewModel.flowOfColumnIndex2
            .launchCollectionInLifecycleScope { index -> cardsAdapter.updateMapping { it[1] = COLUMN2_KEYS[index] } }

        viewModel.flowOfFilterQuery
            .launchCollectionInLifecycleScope { filterQuery ->
                searchView!!.setQuery("", false)
                searchTerms = filterQuery
                searchView!!.setQuery(searchTerms, true)
                searchCards()
            }

        viewModel.flowOfDeckId
            .launchCollectionInLifecycleScope { deckId ->
                if (deckId == null) return@launchCollectionInLifecycleScope
                // this handles ALL_DECKS_ID
                deckSpinnerSelection!!.selectDeckById(deckId, false)
                searchCards()
            }

        viewModel.flowOfCanSearch
            .launchCollectionInLifecycleScope { canSave ->
                runOnUiThread { saveSearchItem?.isVisible = canSave }
            }

        viewModel.flowOfIsInMultiSelectMode
            .launchCollectionInLifecycleScope { inMultiSelect ->
                if (inMultiSelect) {
                    // Turn on Multi-Select Mode so that the user can select multiple cards at once.
                    Timber.d("load multiselect mode")
                    // show title and hide spinner
                    actionBarTitle.visibility = View.VISIBLE
                    deckSpinnerSelection!!.setSpinnerVisibility(View.GONE)
                } else {
                    Timber.d("end multiselect mode")
                    // If view which was originally selected when entering multi-select is visible then maintain its position
                    val view = cardsListView.getChildAt(lastSelectedPosition - cardsListView.firstVisiblePosition)
                    view?.let { recenterListView(it) }
                    // update adapter to remove check boxes
                    cardsAdapter.notifyDataSetChanged()
                    deckSpinnerSelection!!.setSpinnerVisibility(View.VISIBLE)
                    actionBarTitle.visibility = View.GONE
                }
                // reload the actionbar using the multi-select mode actionbar
                invalidateOptionsMenu()
            }

        viewModel.flowOfInitCompleted
            .launchCollectionInLifecycleScope { completed -> if (completed) searchCards() }
    }

    fun searchWithFilterQuery(filterQuery: String) = launchCatchingTask {
        viewModel.setFilterQuery(filterQuery)
    }

    // Finish initializing the activity after the collection has been correctly loaded
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        Timber.d("onCollectionLoaded()")
        registerExternalStorageListener()
        cards.reset()
        // Create a spinner for column 1
        findViewById<Spinner>(R.id.browser_column1_spinner).apply {
            adapter = ArrayAdapter.createFromResource(
                this@CardBrowser,
                R.array.browser_column1_headings,
                android.R.layout.simple_spinner_item
            ).apply {
                setDropDownViewResource(R.layout.spinner_custom_layout)
            }
            onItemSelectedListener = BasicItemSelectedListener { pos, _ ->
                viewModel.setColumn1Index(pos)
            }
            setSelection(viewModel.column1Index)
        }
        // Setup the column 2 heading as a spinner so that users can easily change the column type
        findViewById<Spinner>(R.id.browser_column2_spinner).apply {
            adapter = ArrayAdapter.createFromResource(
                this@CardBrowser,
                R.array.browser_column2_headings,
                android.R.layout.simple_spinner_item
            ).apply {
                // The custom layout for the adapter is used to prevent the overlapping of various interactive components on the screen
                setDropDownViewResource(R.layout.spinner_custom_layout)
            }
            // Create a new list adapter with updated column map any time the user changes the column
            onItemSelectedListener = BasicItemSelectedListener { pos, _ ->
                viewModel.setColumn2Index(pos)
            }
            setSelection(viewModel.column2Index)
        }

        cardsListView.setOnItemClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
            if (viewModel.isInMultiSelectMode) {
                // click on whole cell triggers select
                val cb = view!!.findViewById<CheckBox>(R.id.card_checkbox)
                cb.toggle()
                viewModel.toggleRowSelectionAtPosition(position)
            } else {
                // load up the card selected on the list
                val clickedCardId = viewModel.getCardIdAtPosition(position)
                saveScrollingState(position)
                openNoteEditorForCard(clickedCardId)
            }
        }
        @KotlinCleanup("helper function for min/max range")
        cardsListView.setOnItemLongClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
            if (viewModel.isInMultiSelectMode) {
                viewModel.selectRowsBetweenPositions(lastSelectedPosition, position)
            } else {
                lastSelectedPosition = position
                saveScrollingState(position)

                // click on whole cell triggers select
                val cb = view!!.findViewById<CheckBox>(R.id.card_checkbox)
                cb.toggle()
                viewModel.toggleRowSelectionAtPosition(position)
                recenterListView(view)
                cardsAdapter.notifyDataSetChanged()
            }
            true
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        deckSpinnerSelection = DeckSpinnerSelection(
            this,
            findViewById(R.id.toolbar_spinner),
            showAllDecks = true,
            alwaysShowDefault = false,
            showFilteredDecks = true
        ).apply {
            initializeActionBarDeckSpinner(col, supportActionBar!!)
            launchCatchingTask { selectDeckById(viewModel.deckId ?: ALL_DECKS_ID, false) }
        }
    }

    suspend fun selectDeckAndSave(deckId: DeckId) {
        viewModel.setDeckId(deckId)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // NOTE: These are all active when typing in the search - doesn't matter as all need CTRL
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+A - Select All")
                    viewModel.selectAll()
                    return true
                }
            }
            KeyEvent.KEYCODE_E -> {
                // Ctrl+Shift+E: Export (TODO)
                if (event.isCtrlPressed) {
                    Timber.i("Ctrl+E: Add Note")
                    launchCatchingTask { addNoteFromCardBrowser() }
                    return true
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
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                Timber.i("Delete pressed - Delete Selected Note")
                deleteSelectedNotes()
                return true
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
        }
        return super.onKeyDown(keyCode, event)
    }

    /** All the notes of the selected cards will be marked
     * If one or more card is unmarked, all will be marked,
     * otherwise, they will be unmarked  */
    @NeedsTest("Test that the mark get toggled as expected for a list of selected cards")
    @VisibleForTesting
    fun toggleMark() = launchCatchingTask {
        withProgress { viewModel.toggleMark(selectedRowIds) }
        cardsAdapter.notifyDataSetChanged()
    }

    @VisibleForTesting
    suspend fun selectAllDecks() {
        viewModel.setDeckId(ALL_DECKS_ID)
    }

    /** Opens the note editor for a card.
     * We use the Card ID to specify the preview target  */
    @NeedsTest("note edits are saved")
    @NeedsTest("I/O edits are saved")
    private fun openNoteEditorForCard(cardId: CardId) {
        currentCardId = cardId
        val intent = EditCardDestination(currentCardId).toIntent(this, animation = Direction.DEFAULT)
        onEditCardActivityResult.launch(intent)
        // #6432 - FIXME - onCreateOptionsMenu crashes if receiving an activity result from edit card when in multiselect
        viewModel.endMultiSelectMode()
    }

    private fun openNoteEditorForCurrentlySelectedNote() {
        try {
            // Just select the first one. It doesn't particularly matter if there's a multiselect occurring.
            openNoteEditorForCard(selectedRowIds[0])
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
        if (unmountReceiver != null) {
            unregisterReceiver(unmountReceiver)
        }
    }

    @Deprecated("Deprecated in Java")
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
    }

    @KotlinCleanup("Add a few variables to get rid of the !!")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        actionBarMenu = menu
        if (!viewModel.isInMultiSelectMode) {
            // restore drawer click listener and icon
            restoreDrawerIcon()
            menuInflater.inflate(R.menu.card_browser, menu)
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
                    searchTerms = ""
                    searchView!!.setQuery(searchTerms, false)
                    searchCards()
                    return true
                }
            })
            searchView = searchItem!!.actionView as CardBrowserSearchView
            searchView!!.setMaxWidth(Integer.MAX_VALUE)
            searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    if (searchView!!.shouldIgnoreValueChange()) {
                        return true
                    }
                    viewModel.updateQueryText(newText)
                    return true
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    onSearch()
                    searchView!!.clearFocus()
                    return true
                }
            })
            // Fixes #6500 - keep the search consistent if coming back from note editor
            // Fixes #9010 - consistent search after drawer change calls invalidateOptionsMenu
            if (!viewModel.tempSearchQuery.isNullOrEmpty() || searchTerms.isNotEmpty()) {
                searchItem!!.expandActionView() // This calls mSearchView.setOnSearchClickListener
                val toUse =
                    if (!viewModel.tempSearchQuery.isNullOrEmpty()) viewModel.tempSearchQuery else searchTerms
                searchView!!.setQuery(toUse!!, false)
            }
            searchView!!.setOnSearchClickListener {
                // Provide SearchView with the previous search terms
                searchView!!.setQuery(searchTerms, false)
            }
        } else {
            // multi-select mode
            menuInflater.inflate(R.menu.card_browser_multiselect, menu)
            showBackIcon()
            increaseHorizontalPaddingOfOverflowMenuIcons(menu)
        }
        actionBarMenu?.findItem(R.id.action_undo)?.run {
            isVisible = getColUnsafe.undoAvailable()
            title = getColUnsafe.undoLabel()
        }

        launchOptions?.let { options ->
            if (options !is CardBrowserLaunchOptions.SystemContextMenu) return@let
            // Fill in the search.
            Timber.i("CardBrowser :: Called with search intent: %s", launchOptions.toString())
            searchWithFilterQuery(options.search.toString())
            launchOptions = null
        }

        previewItem = menu.findItem(R.id.action_preview)
        onSelectionChanged()
        updatePreviewMenuItem()
        return super.onCreateOptionsMenu(menu)
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
        if (actionBarMenu == null || actionBarMenu!!.findItem(R.id.action_suspend_card) == null) {
            return
        }
        if (viewModel.hasSelectedAnyRows()) {
            actionBarMenu!!.findItem(R.id.action_suspend_card).apply {
                title = TR.browsingToggleSuspend().toSentenceCase(R.string.sentence_toggle_suspend)
                setIcon(R.drawable.ic_suspend)
            }
            actionBarMenu!!.findItem(R.id.action_mark_card).apply {
                title = TR.browsingToggleMark()
                setIcon(R.drawable.ic_star_border_white)
            }
        }
        actionBarMenu!!.findItem(R.id.action_export_selected).apply {
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
        actionBarMenu!!.findItem(R.id.action_delete_card).apply {
            this.title = if (viewModel.cardsOrNotes == CARDS) {
                resources.getQuantityString(
                    R.plurals.card_browser_delete_cards,
                    viewModel.selectedRowCount()
                )
            } else {
                resources.getQuantityString(
                    R.plurals.card_browser_delete_notes,
                    viewModel.selectedRowCount()
                )
            }
        }
        actionBarMenu!!.findItem(R.id.action_select_all).isVisible = !hasSelectedAllCards()
        // Note: Theoretically should not happen, as this should kick us back to the menu
        actionBarMenu!!.findItem(R.id.action_select_none).isVisible =
            viewModel.hasSelectedAnyRows()
        actionBarMenu!!.findItem(R.id.action_edit_note).isVisible = canPerformMultiSelectEditNote()
        actionBarMenu!!.findItem(R.id.action_view_card_info).isVisible = canPerformCardInfo()
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
        val updatedCards = withProgress { viewModel.updateSelectedCardsFlag(flag) }
        // TODO: try to offload the cards processing in updateCardsInList() on a background thread,
        // otherwise it could hang the main thread
        updateCardsInList(updatedCards)
        invalidateOptionsMenu() // maybe the availability of undo changed
        if (updatedCards.any { card -> card.id == reviewerCardId }) {
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
                val searchTerms = searchView!!.query.toString()
                showDialogFragment(
                    newInstance(
                        null,
                        mySearchesDialogListener,
                        searchTerms,
                        CardBrowserMySearchesDialog.CARD_BROWSER_MY_SEARCHES_TYPE_SAVE
                    )
                )
                return true
            }
            R.id.action_list_my_searches -> {
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

                return true
            }
            R.id.action_sort_by_size -> {
                showDialogFragment(
                    // TODO: move this into the ViewModel
                    CardBrowserOrderDialog.newInstance { dialog: DialogInterface, which: Int ->
                        dialog.dismiss()
                        changeCardOrder(SortType.fromCardBrowserLabelIndex(which))
                    }
                )
                return true
            }

            @NeedsTest("filter-marked query needs testing")
            R.id.action_show_marked -> {
                launchCatchingTask { viewModel.searchForMarkedNotes() }
                return true
            }

            @NeedsTest("filter-suspended query needs testing")
            R.id.action_show_suspended -> {
                launchCatchingTask { viewModel.searchForSuspendedCards() }
                return true
            }
            R.id.action_search_by_tag -> {
                showFilterByTagsDialog()
                return true
            }
            R.id.action_flag_zero -> {
                updateFlagForSelectedRows(Flag.NONE)
                return true
            }
            R.id.action_flag_one -> {
                updateFlagForSelectedRows(Flag.RED)
                return true
            }
            R.id.action_flag_two -> {
                updateFlagForSelectedRows(Flag.ORANGE)
                return true
            }
            R.id.action_flag_three -> {
                updateFlagForSelectedRows(Flag.GREEN)
                return true
            }
            R.id.action_flag_four -> {
                updateFlagForSelectedRows(Flag.BLUE)
                return true
            }
            R.id.action_flag_five -> {
                updateFlagForSelectedRows(Flag.PINK)
                return true
            }
            R.id.action_flag_six -> {
                updateFlagForSelectedRows(Flag.TURQUOISE)
                return true
            }
            R.id.action_flag_seven -> {
                updateFlagForSelectedRows(Flag.PURPLE)
                return true
            }
            R.id.action_select_flag_zero -> {
                filterByFlag(Flag.NONE)
                return true
            }
            R.id.action_select_flag_one -> {
                filterByFlag(Flag.RED)
                return true
            }
            R.id.action_select_flag_two -> {
                filterByFlag(Flag.ORANGE)
                return true
            }
            R.id.action_select_flag_three -> {
                filterByFlag(Flag.GREEN)
                return true
            }
            R.id.action_select_flag_four -> {
                filterByFlag(Flag.BLUE)
                return true
            }
            R.id.action_select_flag_five -> {
                filterByFlag(Flag.PINK)
                return true
            }
            R.id.action_select_flag_six -> {
                filterByFlag(Flag.TURQUOISE)
                return true
            }
            R.id.action_select_flag_seven -> {
                filterByFlag(Flag.PURPLE)
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
                suspendCards()
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
                Timber.i("CardBrowser:: Reposition button pressed")
                if (warnUserIfInNotesOnlyMode()) return true
                // `selectedRowIds` getter does a lot of work so save it in a val beforehand
                val selectedCardIds = selectedRowIds
                // Only new cards may be repositioned (If any non-new found show error dialog and return false)
                if (selectedCardIds.any { getColUnsafe.getCard(it).queue != Consts.QUEUE_TYPE_NEW }) {
                    showDialogFragment(
                        SimpleMessageDialog.newInstance(
                            title = getString(R.string.vague_error),
                            message = getString(R.string.reposition_card_not_new_error),
                            reload = false
                        )
                    )
                    return false
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
                return true
            }
            R.id.action_edit_note -> {
                openNoteEditorForCurrentlySelectedNote()
                return super.onOptionsItemSelected(item)
            }
            R.id.action_view_card_info -> {
                viewModel.cardInfoDestination?.let { destination ->
                    val intent: Intent = destination.toIntent(this)
                    startActivity(intent)
                }
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
        }
        return super.onOptionsItemSelected(item)
    }

    override fun exportDialogsFactory(): ExportDialogsFactory = exportingDelegate.dialogsFactory

    private fun exportSelected() = launchCatchingTask {
        val (type, selectedIds) = viewModel.getSelectionExportData() ?: return@launchCatchingTask
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
        // Show confirmation dialog before resetting card progress
        val dialog = ConfirmationDialog()
        val title = getString(R.string.reset_card_dialog_title)
        val message = getString(R.string.reset_card_dialog_message)
        dialog.setArgs(title, message)
        val confirm = Runnable {
            Timber.i("CardBrowser:: ResetProgress button pressed")
            resetProgressNoConfirm(selectedRowIds)
        }
        dialog.setConfirm(confirm)
        showDialogFragment(dialog)
    }

    @VisibleForTesting
    fun resetProgressNoConfirm(cardIds: List<Long>) {
        launchCatchingTask {
            resetCards(cardIds)
        }
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
        val intentData = viewModel.previewIntentData
        onPreviewCardsActivityResult.launch(getPreviewIntent(intentData.currentIndex, intentData.previewerIdsFile))
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
        val rescheduleDialog: RescheduleDialog = selectedRowIds.run {
            val consumer = Consumer { newDays: Int -> rescheduleWithoutValidation(this, newDays) }
            if (size == 1) {
                rescheduleSingleCard(resources, getColUnsafe.getCard(this[0]), consumer)
            } else {
                rescheduleMultipleCards(resources, consumer, size)
            }
        }
        showDialogFragment(rescheduleDialog)
    }

    @VisibleForTesting
    fun rescheduleWithoutValidation(selectedCardIds: List<CardId>, newDays: Int) {
        launchCatchingTask {
            rescheduleCards(selectedCardIds, newDays)
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
        get() {
            val intent = Intent(this@CardBrowser, NoteEditor::class.java)
            intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_CARDBROWSER_ADD)
            if (viewModel.lastDeckId?.let { id -> id > 0 } == true) {
                intent.putExtra(NoteEditor.EXTRA_DID, viewModel.lastDeckId)
            }
            intent.putExtra(NoteEditor.EXTRA_TEXT_FROM_SEARCH_VIEW, searchTerms)
            return intent
        }

    private fun addNoteFromCardBrowser() {
        onAddNoteActivityResult.launch(addNoteIntent)
    }

    private val reviewerCardId: CardId
        get() = intent.getLongExtra("currentCard", -1)

    private fun showEditTagsDialog() {
        if (selectedRowIds.isEmpty()) {
            Timber.d("showEditTagsDialog: called with empty selection")
        }
        val allTags = getColUnsafe.tags.all()
        val selectedNotes = selectedRowIds
            .map { cardId: CardId? -> getColUnsafe.getCard(cardId!!).note(getColUnsafe) }
            .distinct()
        val checkedTags = selectedNotes
            .flatMap { note: Note -> note.tags }
        if (selectedNotes.size == 1) {
            Timber.d("showEditTagsDialog: edit tags for one note")
            tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
            val dialog = tagsDialogFactory.newTagsDialog().withArguments(TagsDialog.DialogType.EDIT_TAGS, checkedTags, allTags)
            showDialogFragment(dialog)
            return
        }
        val uncheckedTags = selectedNotes
            .flatMap { note: Note ->
                val noteTags: List<String?> = note.tags
                allTags.filter { t: String? -> !noteTags.contains(t) }
            }
        Timber.d("showEditTagsDialog: edit tags for multiple note")
        tagsDialogListenerAction = TagsDialogListenerAction.EDIT_TAGS
        val dialog = tagsDialogFactory.newTagsDialog().withArguments(
            TagsDialog.DialogType.EDIT_TAGS,
            checkedTags,
            uncheckedTags,
            allTags
        )
        showDialogFragment(dialog)
    }

    private fun showFilterByTagsDialog() {
        tagsDialogListenerAction = TagsDialogListenerAction.FILTER
        val dialog = tagsDialogFactory.newTagsDialog().withArguments(
            TagsDialog.DialogType.FILTER_BY_TAG,
            ArrayList(0),
            getColUnsafe.tags.all()
        )
        showDialogFragment(dialog)
    }

    private fun showOptionsDialog() {
        val dialog = BrowserOptionsDialog(viewModel.cardsOrNotes, viewModel.isTruncated)
        dialog.show(supportFragmentManager, "browserOptionsDialog")
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        // Save current search terms
        savedInstanceState.putString("mSearchTerms", searchTerms)
        savedInstanceState.putLong("mOldCardId", oldCardId)
        savedInstanceState.putInt("mOldCardTopOffset", oldCardTopOffset)
        savedInstanceState.putBoolean("mShouldRestoreScroll", shouldRestoreScroll)
        savedInstanceState.putBoolean("mPostAutoScroll", postAutoScroll)
        savedInstanceState.putInt("mLastSelectedPosition", lastSelectedPosition)
        exportingDelegate.onSaveInstanceState(savedInstanceState)
        super.onSaveInstanceState(savedInstanceState)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        searchTerms = savedInstanceState.getString("mSearchTerms", "")
        oldCardId = savedInstanceState.getLong("mOldCardId")
        oldCardTopOffset = savedInstanceState.getInt("mOldCardTopOffset")
        shouldRestoreScroll = savedInstanceState.getBoolean("mShouldRestoreScroll")
        postAutoScroll = savedInstanceState.getBoolean("mPostAutoScroll")
        lastSelectedPosition = savedInstanceState.getInt("mLastSelectedPosition")
        searchCards()
    }

    private fun invalidate() {
        renderBrowserQAJob?.cancel()
    }

    private fun forceRefreshSearch(useSearchTextValue: Boolean = false) {
        if (useSearchTextValue && searchView != null) {
            searchTerms = searchView!!.query.toString()
        }
        searchCards()
    }

    @RustCleanup("remove card cache; switch to RecyclerView and browserRowForId (#11889)")
    @VisibleForTesting
    fun searchCards() {
        if (!viewModel.initCompleted) {
            Timber.d("!initCompleted, not searching")
            return
        }
        // cancel the previous search & render tasks if still running
        invalidate()
        if ("" != searchTerms && searchView != null) {
            searchView!!.setQuery(searchTerms, false)
            searchItem!!.expandActionView()
        }
        val searchText: String = if (searchTerms.contains("deck:")) {
            "($searchTerms)"
        } else {
            if ("" != searchTerms) "${viewModel.restrictOnDeck}($searchTerms)" else viewModel.restrictOnDeck
        }
        // clear the existing card list
        cards.reset()
        cardsAdapter.notifyDataSetChanged()
        val order = viewModel.order.toSortOrder()
        launchCatchingTask {
            Timber.d("performing search")
            val cards = withProgress { searchForCards(searchText, order, viewModel.cardsOrNotes) }
            Timber.d("Search returned %d cards", cards.size)
            // Render the first few items
            for (i in 0 until min(numCardsToRender(), cards.size)) {
                cards[i].load(false, viewModel.column1Index, viewModel.column2Index)
            }
            redrawAfterSearch(cards)
        }
    }

    fun redrawAfterSearch(cards: MutableList<CardCache>) {
        this.cards.replaceWith(cards)
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
    protected open fun numCardsToRender(): Int {
        return ceil(
            (
                cardsListView.height /
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics)
                ).toDouble()
        ).toInt() + 5
    }

    private fun updateList() {
        if (colIsOpenUnsafe()) {
            cardsAdapter.notifyDataSetChanged()
            deckSpinnerSelection!!.notifyDataSetChanged()
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

    // convenience method for updateCardsInList(...)
    private fun updateCardInList(card: Card) {
        val cards: MutableList<Card> = ArrayList(1)
        cards.add(card)
        updateCardsInList(cards)
    }

    /** Returns the decks which are valid targets for "Change Deck"  */
    suspend fun getValidDecksForChangeDeck(): List<DeckNameId> =
        deckSpinnerSelection!!.computeDropDownDecks(includeFiltered = false)

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
        undoableOp {
            val selectedNotes = selectedRowIds
                .map { cardId -> getCard(cardId).note() }
                .distinct()
                .onEach { note ->
                    val previousTags: List<String> = note.tags
                    val updatedTags = getUpdatedTags(previousTags, selectedTags, indeterminateTags)
                    note.setTagsFromStr(tags.join(updatedTags))
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
     * Loads/Reloads (Updates the Q, A & etc) of cards in the [cards] list
     * @param cards Cards that were changed
     */
    private fun updateCardsInList(cards: List<Card>) {
        val idToPos = viewModel.cardIdToPositionMap
        // TODO: Inefficient
        cards
            .mapNotNull { c -> idToPos[c.id] }
            .filterNot { pos -> pos >= viewModel.rowCount }
            .map { pos -> viewModel.getRowAtPosition(pos) }
            .forEach { it.load(true, viewModel.column1Index, viewModel.column2Index) }
        updateList()
    }

    private suspend fun saveEditedCard() {
        Timber.d("CardBrowser - saveEditedCard()")
        val card = try {
            withCol { getCard(currentCardId) }
        } catch (e: Exception) {
            Timber.i("edited card no longer exists")
            return
        }
        updateCardInList(card)
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

    private fun suspendCards() = launchCatchingTask { withProgress { viewModel.suspendCards() } }

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
    private fun saveScrollingState(position: Int) {
        oldCardId = viewModel.getCardIdAtPosition(position)
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
            viewModel.column1Index,
            viewModel.column2Index
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
    private fun createViewModel() = ViewModelProvider(
        viewModelStore,
        CardBrowserViewModel.factory(AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository, cacheDir),
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
            val flagColor = Flag.fromCode(card.userFlag()).browserColorRes
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
            return ThemeUtils.getThemeAttrColor(context, colorAttr)
        }

        fun getColumnHeaderText(key: CardBrowserColumn?): String? {
            return when (key) {
                CardBrowserColumn.FLAGS -> Integer.valueOf(card.userFlag()).toString()
                CardBrowserColumn.SUSPENDED -> if (card.queue == Consts.QUEUE_TYPE_SUSPENDED) "True" else "False"
                CardBrowserColumn.MARKED -> if (isMarked(col, card.note(col))) "marked" else null
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
        fun load(reload: Boolean, column1Index: Int, column2Index: Int) {
            if (reload) {
                reload()
            }
            card.note(col)
            // First column can not be the answer. If it were to change, this code should also be changed.
            if (COLUMN1_KEYS[column1Index] == CardBrowserColumn.QUESTION || arrayOf(CardBrowserColumn.QUESTION, CardBrowserColumn.ANSWER).contains(COLUMN2_KEYS[column2Index])) {
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
     * Show/dismiss dialog when sd card is ejected/remounted (collection is saved by SdCardReceiver)
     */
    private fun registerExternalStorageListener() {
        if (unmountReceiver == null) {
            unmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                        finish()
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            registerReceiver(unmountReceiver, iFilter)
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
    fun searchCards(searchQuery: String) {
        searchTerms = searchQuery
        searchCards()
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

        const val CARD_NOT_AVAILABLE = -1

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
            this@launchCollectionInLifecycleScope.collect { block(it) }
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
            .toCardCache(cardsOrNotes)
            .toMutableList()
    }
}

context (Collection)
private fun Sequence<CardId>.toCardCache(isInCardMode: CardsOrNotes): Sequence<CardBrowser.CardCache> {
    return this.mapIndexed { idx, cid -> CardBrowser.CardCache(cid, this@Collection, idx, isInCardMode) }
}

class PreviewerDestination(val currentIndex: Int, val previewerIdsFile: PreviewerIdsFile)

@CheckResult
fun PreviewerDestination.toIntent(context: Context) =
    PreviewerFragment.getIntent(context, previewerIdsFile, currentIndex)
