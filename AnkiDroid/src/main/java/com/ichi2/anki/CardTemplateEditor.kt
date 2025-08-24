/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import anki.notetypes.StockNotetype
import anki.notetypes.StockNotetype.OriginalStockKind.ORIGINAL_STOCK_KIND_UNKNOWN_VALUE
import anki.notetypes.notetypeId
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldDialog.Companion.REQUEST_FIELD_INSERT
import com.ichi2.anki.libanki.CardTemplates
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.Notetypes.Companion.NOT_FOUND_NOTE_TYPE
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.getStockNotetype
import com.ichi2.anki.libanki.getStockNotetypeKinds
import com.ichi2.anki.libanki.utils.append
import com.ichi2.anki.notetype.RenameCardTemplateDialog
import com.ichi2.anki.notetype.RepositionCardTemplateDialog
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.ResizablePaneManager
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.postDelayed
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.themes.Themes
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.dp
import com.ichi2.utils.listItems
import com.ichi2.utils.show
import kotlinx.coroutines.launch
import net.ankiweb.rsdroid.Translations
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private typealias BackendCardTemplate = com.ichi2.anki.libanki.CardTemplate

/**
 * Allows the user to view the template for the current note type
 */
@KotlinCleanup("lateinit wherever possible")
open class CardTemplateEditor :
    AnkiActivity(),
    DeckSelectionListener {
    @VisibleForTesting
    lateinit var viewPager: ViewPager2
    private var slidingTabLayout: TabLayout? = null
    var tempNoteType: CardTemplateNotetype? = null
        private set
    private var fieldNames: List<String>? = null
    private var noteTypeId: NoteTypeId = 0
    private var noteId: NoteId = 0

    /**
     * Stores the cursor position for each editor window (front, style, back) within each card template.
     * The outer HashMap's key is the card template's ordinal (position).
     * The inner HashMap's key is the editor window ID (e.g., R.id.front_edit).
     * The value is the cursor position within that editor window.
     */
    private var tabToCursorPositions: HashMap<Int, HashMap<Int, Int>> = HashMap()

    // the current editor view among front/style/back
    private var tabToViewId: HashMap<Int, Int?> = HashMap()
    private var startingOrdId = 0

    /**
     * The frame containing the template previewer. Non null only in layout x-large.
     */
    private var templatePreviewerFrame: FragmentContainerView? = null

    /**
     * If true, the view is split in two. The template editor appears on the leading side and the previewer on the trailing side.
     * This occurs when the view is big enough.
     */
    private var fragmented = false
    val displayDiscardChangesCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showDiscardChangesDialog()
            }
        }

    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------
    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_template_editor)
        // Load the args either from the intent or savedInstanceState bundle
        if (savedInstanceState == null) {
            // get note type id
            noteTypeId = intent.getLongExtra(EDITOR_NOTE_TYPE_ID, NOT_FOUND_NOTE_TYPE)
            if (noteTypeId == NOT_FOUND_NOTE_TYPE) {
                Timber.e("CardTemplateEditor :: no note type ID was provided")
                finish()
                return
            }
            // get id for currently edited note (optional)
            noteId = intent.getLongExtra(EDITOR_NOTE_ID, -1L)
            // get id for currently edited template (optional)
            startingOrdId = intent.getIntExtra("ordId", -1)
            tabToCursorPositions[0] = hashMapOf()
            tabToViewId[0] = R.id.front_edit
        } else {
            noteTypeId = savedInstanceState.getLong(EDITOR_NOTE_TYPE_ID)
            noteId = savedInstanceState.getLong(EDITOR_NOTE_ID)
            startingOrdId = savedInstanceState.getInt(EDITOR_START_ORD_ID)
            tabToCursorPositions = savedInstanceState.getSerializableCompat<HashMap<Int, HashMap<Int, Int>>>(TAB_TO_CURSOR_POSITION_KEY)!!
            tabToViewId = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(TAB_TO_VIEW_ID)!!
            tempNoteType = CardTemplateNotetype.fromBundle(savedInstanceState)
        }

        templatePreviewerFrame = findViewById(R.id.fragment_container)
        /**
         * Check if templatePreviewerFrame is not null and if its visibility is set to VISIBLE.
         * If both conditions are true, assign true to the variable [fragmented], otherwise assign false.
         * [fragmented] will be true if the screen size is large otherwise false
         */
        fragmented = templatePreviewerFrame != null && templatePreviewerFrame?.visibility == View.VISIBLE

        slidingTabLayout = findViewById(R.id.sliding_tabs)
        viewPager = findViewById(R.id.card_template_editor_pager)
        setNavigationBarColor(R.attr.alternativeBackgroundColor)

        // Disable the home icon
        enableToolbar()
        startLoadingCollection()

        if (fragmented) {
            val parentLayout = findViewById<LinearLayout>(R.id.card_template_editor_xl_view)
            val divider = findViewById<View>(R.id.card_template_editor_resizing_divider)
            val cardTemplateEditorPane = findViewById<View>(R.id.template_editor)
            val templatePreviewerPane = findViewById<View>(R.id.fragment_container)
            ResizablePaneManager(
                parentLayout = parentLayout,
                divider = divider,
                leftPane = cardTemplateEditorPane,
                rightPane = templatePreviewerPane,
                sharedPrefs = Prefs.getUiConfig(this),
                leftPaneWeightKey = PREF_TEMPLATE_EDITOR_PANE_WEIGHT,
                rightPaneWeightKey = PREF_TEMPLATE_PREVIEWER_PANE_WEIGHT,
            )
        }

        // Open TemplatePreviewerFragment if in fragmented mode
        loadTemplatePreviewerFragmentIfFragmented()
        onBackPressedDispatcher.addCallback(this, displayDiscardChangesCallback)
    }

    /**
     *  Loads or reloads [tempNoteType] in [R.id.fragment_container] if the view is fragmented. Do nothing otherwise.
     */
    private fun loadTemplatePreviewerFragmentIfFragmented() {
        if (!fragmented) {
            return
        }
        launchCatchingTask {
            val notetype = tempNoteType!!.notetype
            val notetypeFile = NotetypeFile(this@CardTemplateEditor, notetype)
            val ord = viewPager.currentItem
            val note = withCol { currentFragment?.getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
            val args =
                TemplatePreviewerArguments(
                    notetypeFile = notetypeFile,
                    id = note.id,
                    ord = ord,
                    fields = note.fields,
                    tags = note.tags,
                    fillEmpty = true,
                )
            val backgroundColor = Themes.getColorFromAttr(this@CardTemplateEditor, R.attr.alternativeBackgroundColor)
            val fragment = TemplatePreviewerFragment.newInstance(args, backgroundColor)
            supportFragmentManager.commitNow {
                replace(R.id.fragment_container, fragment)
            }

            // Modify the "Show Answer" button height to 80dp to maintain visual consistency with the BottomNavigationView,
            // which has a default height of 80dp.
            fragment.view?.post {
                val showAnswerButton = fragment.view?.findViewById<MaterialButton>(R.id.show_answer)
                showAnswerButton?.let { button ->
                    button.layoutParams.height = 80.dp.toPx(button.context)
                    button.requestLayout()
                }

                // Adjust the top margin of the webview container to match template editor top margin
                val webView = fragment.view?.findViewById<MaterialCardView>(R.id.webview_container)
                webView?.let { container ->
                    val params = container.layoutParams as ViewGroup.MarginLayoutParams
                    val topMargin = resources.getDimensionPixelSize(R.dimen.reviewer_side_margin)
                    params.topMargin = topMargin
                    container.layoutParams = params
                }
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            tempNoteType?.let { putAll(it.toBundle()) }
            putLong(EDITOR_NOTE_TYPE_ID, noteTypeId)
            putLong(EDITOR_NOTE_ID, noteId)
            putInt(EDITOR_START_ORD_ID, startingOrdId)
            putSerializable(TAB_TO_VIEW_ID, tabToViewId)
            putSerializable(TAB_TO_CURSOR_POSITION_KEY, tabToCursorPositions)
            super.onSaveInstanceState(this)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Callback used to finish initializing the activity after the collection has been correctly loaded
     * @param col Collection which has been loaded
     */
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        // without this call the editor doesn't see the latest changes to notetypes, see #16630
        @NeedsTest("Add test to check that renaming notetypes in ManageNotetypes is seen in CardTemplateEditor(#16630)")
        col.notetypes.clearCache()
        // The first time the activity loads it has a model id but no edits yet, so no edited model
        // take the passed model id load it up for editing
        if (tempNoteType == null) {
            tempNoteType = CardTemplateNotetype(col.notetypes.get(noteTypeId)!!.deepClone())
            // Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
        fieldNames = tempNoteType!!.notetype.fieldsNames
        // Set up the ViewPager with the sections adapter.
        viewPager.adapter = TemplatePagerAdapter(this@CardTemplateEditor)
        TabLayoutMediator(slidingTabLayout!!, viewPager) { tab: TabLayout.Tab, position: Int ->
            tab.text = tempNoteType!!.getTemplate(position).name
        }.apply { attach() }

        // Set activity title
        supportActionBar?.let {
            it.setTitle(R.string.title_activity_template_editor)
            it.subtitle = tempNoteType!!.notetype.name
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for note type id %d", noteTypeId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", startingOrdId)
        if (startingOrdId != -1) {
            viewPager.setCurrentItem(startingOrdId, animationDisabled())
        }
    }

    fun noteTypeHasChanged(): Boolean {
        val oldNoteType: NotetypeJson? = getColUnsafe.notetypes.get(noteTypeId)
        return tempNoteType != null && tempNoteType!!.notetype.toString() != oldNoteType.toString()
    }

    private fun showDiscardChangesDialog() =
        DiscardChangesDialog.showDialog(this) {
            Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
            // Clear the edited note type from any cache files, and clear it from this objects memory to discard changes
            CardTemplateNotetype.clearTempNoteTypeFiles()
            tempNoteType = null
            finish()
        }

    /** When a deck is selected via Deck Override  */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (tempNoteType!!.notetype.isCloze) {
            Timber.w("Attempted to set deck for cloze note type")
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val ordinal = viewPager.currentItem
        val template = tempNoteType!!.getTemplate(ordinal)
        val templateName = template.name

        if (deck != null && getColUnsafe.decks.isFiltered(deck.deckId)) {
            Timber.w("Attempted to set default deck of %s to dynamic deck %s", templateName, deck.name)
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val message: String =
            if (deck == null) {
                Timber.i("Removing default template from template '%s'", templateName)
                template.jsonObject.put("did", JSONObject.NULL)
                getString(R.string.model_manager_deck_override_removed_message, templateName)
            } else {
                Timber.i("Setting template '%s' to '%s'", templateName, deck.name)
                template.jsonObject.put("did", deck.deckId)
                getString(R.string.model_manager_deck_override_added_message, templateName, deck.name)
            }

        showSnackbar(message, Snackbar.LENGTH_SHORT)

        // Deck Override can change from "on" <-> "off"
        invalidateOptionsMenu()
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val currentFragment = currentFragment ?: return super.onKeyUp(keyCode, event)
        if (!event.isCtrlPressed) {
            return super.onKeyUp(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_P -> {
                Timber.i("Ctrl+P: Perform preview from keypress")
                currentFragment.performPreview()
            }
            KeyEvent.KEYCODE_1 -> {
                Timber.i("Ctrl+1: Edit front template from keypress")
                currentFragment.bottomNavigation.selectedItemId = R.id.front_edit
            }
            KeyEvent.KEYCODE_2 -> {
                Timber.i("Ctrl+2: Edit back template from keypress")
                currentFragment.bottomNavigation.selectedItemId = R.id.back_edit
            }
            KeyEvent.KEYCODE_3 -> {
                Timber.i("Ctrl+3: Edit styling from keypress")
                currentFragment.bottomNavigation.selectedItemId = R.id.styling_edit
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Ctrl+S: Save note from keypress")
                currentFragment.saveNoteType()
            }
            KeyEvent.KEYCODE_I -> {
                Timber.i("Ctrl+I: Insert field from keypress")
                currentFragment.showInsertFieldDialog()
            }
            KeyEvent.KEYCODE_N -> {
                Timber.i("Ctrl+N: Add card template from keypress")
                currentFragment.addCardTemplate()
            }
            KeyEvent.KEYCODE_R -> {
                Timber.i("Ctrl+R: Rename card from keypress")
                currentFragment.showRenameDialog()
            }
            KeyEvent.KEYCODE_B -> {
                Timber.i("Ctrl+B: Open browser appearance from keypress")
                currentFragment.openBrowserAppearance()
            }
            KeyEvent.KEYCODE_D -> {
                Timber.i("Ctrl+D: Delete card from keypress")
                currentFragment.deleteCardTemplate()
            }
            KeyEvent.KEYCODE_O -> {
                Timber.i("Ctrl+O: Display deck override dialog from keypress")
                currentFragment.displayDeckOverrideDialog(currentFragment.tempModel)
            }
            KeyEvent.KEYCODE_M -> {
                Timber.i("Ctrl+M: Copy markdown from keypress")
                currentFragment.copyMarkdownTemplateToClipboard()
            }
            else -> {
                return super.onKeyUp(keyCode, event)
            }
        }
        // We reach this only if we didn't reach the `else` case.
        return true
    }

    @get:VisibleForTesting
    val currentFragment: CardTemplateFragment?
        get() =
            try {
                supportFragmentManager.findFragmentByTag("f" + viewPager.currentItem) as CardTemplateFragment?
            } catch (e: Exception) {
                Timber.w("Failed to get current fragment")
                null
            }
    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * A [androidx.viewpager2.adapter.FragmentStateAdapter] that returns a fragment corresponding to
     * one of the tabs.
     */
    inner class TemplatePagerAdapter(
        fragmentActivity: FragmentActivity,
    ) : FragmentStateAdapter(fragmentActivity) {
        private var baseId: Long = 0

        override fun createFragment(position: Int): Fragment {
            val editorViewId = tabToViewId[position] ?: R.id.front_edit
            return CardTemplateFragment.newInstance(position, noteId, editorViewId)
        }

        override fun getItemCount(): Int = tempNoteType?.templateCount ?: 0

        override fun getItemId(position: Int): Long = baseId + position

        override fun containsItem(id: Long): Boolean {
            @Suppress("ConvertTwoComparisonsToRangeCheck") // more readable without the range check
            return (id - baseId < itemCount) && (id - baseId >= 0)
        }

        /** Force fragments to reinitialize contents by invalidating previous set of ordinal-based ids  */
        fun ordinalShift() {
            baseId += (itemCount + 1).toLong()
        }
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOf(
                    shortcut("Ctrl+P", R.string.card_editor_preview_card),
                    shortcut("Ctrl+1", R.string.edit_front_template),
                    shortcut("Ctrl+2", R.string.edit_back_template),
                    shortcut("Ctrl+3", R.string.edit_styling),
                    shortcut("Ctrl+S", R.string.save),
                    shortcut("Ctrl+I", R.string.card_template_editor_insert_field),
                    shortcut("Ctrl+A", Translations::cardTemplatesAddCardType),
                    shortcut("Ctrl+R", Translations::cardTemplatesRenameCardType),
                    shortcut("Ctrl+B", R.string.edit_browser_appearance),
                    shortcut("Ctrl+D", Translations::cardTemplatesRemoveCardType),
                    shortcut("Ctrl+O", Translations::cardTemplatesDeckOverride),
                    shortcut("Ctrl+M", R.string.copy_the_template),
                ),
                R.string.card_template_editor_group,
            )

    class CardTemplateFragment : Fragment() {
        private val refreshFragmentHandler = Handler(Looper.getMainLooper())
        private lateinit var editorEditText: FixedEditText

        var currentEditorViewId = 0

        private lateinit var templateEditor: CardTemplateEditor
        lateinit var tempModel: CardTemplateNotetype
        lateinit var bottomNavigation: BottomNavigationView

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View? {
            // Storing a reference to the templateEditor allows us to use member variables
            templateEditor = activity as CardTemplateEditor
            val mainView = inflater.inflate(R.layout.card_template_editor_item, container, false)
            val cardIndex = requireArguments().getInt(CARD_INDEX)
            tempModel = templateEditor.tempNoteType!!
            // Load template
            val template: BackendCardTemplate =
                try {
                    tempModel.getTemplate(cardIndex)
                } catch (e: JSONException) {
                    Timber.d(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                    return mainView
                }
            // initializing the hash map which stores the cursor position for each editor window
            if (templateEditor.tabToCursorPositions[cardIndex] == null) {
                templateEditor.tabToCursorPositions[cardIndex] = hashMapOf()
            }

            editorEditText = mainView.findViewById(R.id.editor_editText)

            editorEditText.customInsertionActionModeCallback = ActionModeCallback()

            bottomNavigation = mainView.findViewById(R.id.card_template_editor_bottom_navigation)

            // If in fragmented mode, wrap the edit area in a MaterialCardView
            if (templateEditor.fragmented) {
                val mainLayout = mainView.findViewById<LinearLayout>(R.id.main_layout)

                // Set the background color of the main layout to match the previewer
                mainLayout.setBackgroundColor(Themes.getColorFromAttr(requireContext(), R.attr.alternativeBackgroundColor))

                // Create a MaterialCardView to wrap the editorEditText
                val cardView =
                    MaterialCardView(requireContext()).apply {
                        layoutParams =
                            LinearLayout
                                .LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    0,
                                    1f,
                                ).apply {
                                    val sideMargin = resources.getDimensionPixelSize(R.dimen.reviewer_side_margin)
                                    setMargins(sideMargin, 0, sideMargin, 0)
                                }
                    }

                // Remove the ScrollView from the main layout and add it to the cardView
                val editScrollView = mainLayout.findViewById<ScrollView>(R.id.card_template_editor_scroll_view)
                mainLayout.removeViewInLayout(editScrollView)

                cardView.addView(
                    editScrollView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )

                mainLayout.addView(cardView, 0)
            }

            bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
                val currentSelectedId = item.itemId
                templateEditor.tabToViewId[cardIndex] = currentSelectedId
                when (currentSelectedId) {
                    R.id.styling_edit -> setCurrentEditorView(currentSelectedId, cardIndex, tempModel.css)
                    R.id.back_edit -> setCurrentEditorView(currentSelectedId, cardIndex, template.afmt)
                    else -> setCurrentEditorView(currentSelectedId, cardIndex, template.qfmt)
                }
                // contents of menu have changed and menu should be redrawn
                templateEditor.invalidateOptionsMenu()
                true
            }
            // set saved or default view
            bottomNavigation.selectedItemId =
                templateEditor.tabToViewId[cardIndex] ?: requireArguments().getInt(EDITOR_VIEW_ID_KEY)

            // Set text change listeners
            val templateEditorWatcher: TextWatcher =
                object : TextWatcher {
                    /**
                     * Declare a nullable variable refreshFragmentRunnable of type Runnable.
                     * This will hold a reference to the Runnable that refreshes the previewer fragment.
                     * It is used to manage delayed fragment updates and can be null if no updates in card.
                     */
                    private var refreshFragmentRunnable: Runnable? = null

                    override fun afterTextChanged(arg0: Editable) {
                        refreshFragmentRunnable?.let { refreshFragmentHandler.removeCallbacks(it) }

                        when (currentEditorViewId) {
                            R.id.styling_edit -> tempModel.css = editorEditText.text.toString()
                            R.id.back_edit -> template.afmt = editorEditText.text.toString()
                            else -> template.qfmt = editorEditText.text.toString()
                        }
                        templateEditor.tempNoteType!!.updateTemplate(cardIndex, template)
                        val updateRunnable =
                            Runnable {
                                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
                            }
                        refreshFragmentRunnable = updateRunnable
                        refreshFragmentHandler.postDelayed(updateRunnable, REFRESH_PREVIEW_DELAY)
                        templateEditor.displayDiscardChangesCallback.isEnabled = noteTypeHasChanged()
                    }

                    override fun beforeTextChanged(
                        arg0: CharSequence,
                        arg1: Int,
                        arg2: Int,
                        arg3: Int,
                    ) {
                        // do nothing
                    }

                    override fun onTextChanged(
                        arg0: CharSequence,
                        arg1: Int,
                        arg2: Int,
                        arg3: Int,
                    ) {
                        // do nothing
                    }
                }
            editorEditText.addTextChangedListener(templateEditorWatcher)

            /* When keyboard is visible, hide the bottom navigation bar to allow viewing
            of all template text when resize happens */
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { _, insets ->
                val imeIsVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                if (imeIsVisible) {
                    bottomNavigation.visibility = View.GONE
                } else {
                    bottomNavigation.visibility = View.VISIBLE
                }
                insets
            }

            /**
             * We focus on the editText to indicate it's editable, but we don't automatically
             * show the keyboard. This is intentional - the keyboard should only appear
             * when the user taps on the edit field, not every time the fragment loads.
             */
            editorEditText.post {
                editorEditText.requestFocus()
            }
            return mainView
        }

        /**
         * Custom ActionMode.Callback implementation for adding new field action
         * button in the text selection menu.
         */
        private inner class ActionModeCallback : ActionMode.Callback {
            private val insertFieldId = 1

            override fun onCreateActionMode(
                mode: ActionMode,
                menu: Menu,
            ): Boolean = true

            override fun onPrepareActionMode(
                mode: ActionMode,
                menu: Menu,
            ): Boolean {
                if (menu.findItem(insertFieldId) != null) {
                    return false
                }
                val initialSize = menu.size

                if (currentEditorViewId != R.id.styling_edit) {
                    // 10644: Do not pass in a R.string as the final parameter as MIUI on Android 12 crashes.
                    menu.add(Menu.FIRST, insertFieldId, 0, getString(R.string.card_template_editor_insert_field))
                }

                return initialSize != menu.size
            }

            override fun onActionItemClicked(
                mode: ActionMode,
                item: MenuItem,
            ): Boolean {
                val itemId = item.itemId
                return if (itemId == insertFieldId) {
                    showInsertFieldDialog()
                    mode.finish()
                    true
                } else {
                    false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Left empty on purpose
            }
        }

        @NeedsTest(
            "the kotlin migration made this method crash due to a recursive call when the dialog would return its data",
        )
        fun showInsertFieldDialog() {
            templateEditor.fieldNames?.let { fieldNames ->
                templateEditor.showDialogFragment(InsertFieldDialog.newInstance(fieldNames))
            }
        }

        @NeedsTest("Cancellation")
        @NeedsTest("Prefill is correct")
        @NeedsTest("Does not work for Cloze/Occlusion")
        @NeedsTest("UI is updated on success")
        fun showRenameDialog() {
            if (noteTypeCreatesDynamicNumberOfNotes()) {
                Timber.w("attempted to rename a dynamic note type")
                return
            }
            val ordinal = templateEditor.viewPager.currentItem
            val template = templateEditor.tempNoteType!!.getTemplate(ordinal)

            RenameCardTemplateDialog.showInstance(
                requireContext(),
                prefill = template.name,
            ) { newName ->
                template.name = newName
                Timber.i("updated card template name")
                Timber.d("updated name of template %d to '%s'", ordinal, newName)

                // update the tab
                templateEditor.viewPager.adapter!!.notifyDataSetChanged()
                // Update the tab name in previewer
                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
            }
        }

        private fun showRepositionDialog() {
            RepositionCardTemplateDialog.showInstance(requireContext(), templateEditor.viewPager.adapter!!.itemCount) { newPosition ->
                val currentPosition = templateEditor.viewPager.currentItem
                Timber.w("moving card template %d to %d", currentPosition, newPosition)
                TODO("CardTemplateNotetype is a complex class and requires significant testing")
            }
        }

        @Suppress("unused")
        private fun insertField(fieldName: String) {
            val start = max(editorEditText.selectionStart, 0)
            val end = max(editorEditText.selectionEnd, 0)
            // add string to editText
            val updatedString = "{{$fieldName}}"
            editorEditText.text!!.replace(min(start, end), max(start, end), updatedString, 0, updatedString.length)
        }

        fun setCurrentEditorView(
            viewId: Int,
            cardId: Int,
            editorContent: String,
        ) {
            // saving the cursor position before changing the editor view
            templateEditor.tabToCursorPositions[cardId]?.set(
                currentEditorViewId,
                editorEditText.selectionStart,
            )
            currentEditorViewId = viewId
            editorEditText.setText(editorContent)
            editorEditText.requestFocus()
            editorEditText.setSelection(templateEditor.tabToCursorPositions[cardId]?.get(currentEditorViewId) ?: 0)
        }

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) {
            templateEditor.slidingTabLayout?.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(p0: TabLayout.Tab?) {
                        templateEditor.loadTemplatePreviewerFragmentIfFragmented()
                    }

                    override fun onTabUnselected(p0: TabLayout.Tab?) {
                    }

                    override fun onTabReselected(p0: TabLayout.Tab?) {
                    }
                },
            )
            parentFragmentManager.setFragmentResultListener(REQUEST_FIELD_INSERT, viewLifecycleOwner) { key, bundle ->
                if (key == REQUEST_FIELD_INSERT) {
                    // this is guaranteed to be non null, as we put a non null value on the other side
                    insertField(bundle.getString(InsertFieldDialog.KEY_INSERTED_FIELD)!!)
                }
            }
            setupMenu()
        }

        /**
         * Cloze and image occlusion note types can generate an arbitrary number of cards from a note
         * Anki only offers:
         * * Restore to Default
         * * Browser Appearance
         */
        @NeedsTest("cannot perform operations on Image Occlusion")
        private fun noteTypeCreatesDynamicNumberOfNotes(): Boolean {
            val noteType = templateEditor.tempNoteType!!.notetype
            return noteType.isCloze || noteType.isImageOcclusion
        }

        private fun setupMenu() {
            (requireActivity() as MenuHost).addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(
                        menu: Menu,
                        menuInflater: MenuInflater,
                    ) {
                        menuInflater.inflate(R.menu.card_template_editor, menu)
                        setupCommonMenu(menu)
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem): Boolean = handleCommonMenuItemSelected(menuItem)
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED,
            )
        }

        fun deleteCardTemplate() {
            templateEditor.lifecycleScope.launch {
                val tempModel = templateEditor.tempNoteType
                val ordinal = templateEditor.viewPager.currentItem
                val template = tempModel!!.getTemplate(ordinal)
                // Don't do anything if only one template
                if (tempModel.templateCount < 2) {
                    templateEditor.showSimpleMessageDialog(resources.getString(R.string.card_template_editor_cant_delete))
                    return@launch
                }

                if (deletionWouldOrphanNote(tempModel, ordinal)) {
                    showOrphanNoteDialog()
                    return@launch
                }

                // Show confirmation dialog
                val numAffectedCards =
                    if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel, ordinal)) {
                        Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                        withCol { notetypes.tmplUseCount(tempModel.notetype, ordinal) }
                    } else {
                        0
                    }
                confirmDeleteCards(template, tempModel.notetype, numAffectedCards)
            }
        }

        /* showOrphanNoteDialog shows a AlertDialog if the deletionWouldOrphanNote returns true
         * it displays a warning for the user when they attempt to delete a card type that
            would leave some notes without any cards (orphan notes) */
        private fun showOrphanNoteDialog() {
            val builder =
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.orphan_note_title)
                    .setMessage(R.string.orphan_note_message)
                    .setPositiveButton(android.R.string.ok, null)

            builder.show()
        }

        fun openBrowserAppearance(): Boolean {
            val currentTemplate = getCurrentTemplate()
            currentTemplate?.let { launchCardBrowserAppearance(it) }
            return true
        }

        fun addCardTemplate() {
            if (templateEditor.tempNoteType!!.notetype.isCloze) {
                Timber.w("addCardTemplate attempted on cloze note type")
                return
            }
            // Show confirmation dialog
            val ordinal = templateEditor.viewPager.currentItem
            // isOrdinalPendingAdd method will check if there are any new card types added or not,
            // if TempModel has new card type then numAffectedCards will be 0 by default.
            val numAffectedCards =
                if (!CardTemplateNotetype.isOrdinalPendingAdd(templateEditor.tempNoteType!!, ordinal)) {
                    templateEditor.getColUnsafe.notetypes.tmplUseCount(templateEditor.tempNoteType!!.notetype, ordinal)
                } else {
                    0
                }
            confirmAddCards(templateEditor.tempNoteType!!.notetype, numAffectedCards)
        }

        fun saveNoteType(): Boolean {
            if (noteTypeHasChanged()) {
                val confirmButton = templateEditor.findViewById<View>(R.id.action_confirm)
                if (confirmButton != null) {
                    if (!confirmButton.isEnabled) {
                        Timber.d("CardTemplateEditor::discarding extra click after button disabled")
                        return true
                    }
                    confirmButton.isEnabled = false
                }
                launchCatchingTask(resources.getString(R.string.card_template_editor_save_error)) {
                    requireActivity().withProgress(resources.getString(R.string.saving_model)) {
                        withCol { templateEditor.tempNoteType!!.saveToDatabase(this@withCol) }
                    }
                    onModelSaved()
                }
            } else {
                Timber.d("CardTemplateEditor:: note type has not changed, exiting")
                templateEditor.finish()
            }
            return true
        }

        /**
         * Setups the part of the menu that can be used either in template editor or in previewer fragment.
         */
        fun setupCommonMenu(menu: Menu) {
            menu.findItem(R.id.action_restore_to_default).title = CollectionManager.TR.cardTemplatesRestoreToDefault()
            if (noteTypeCreatesDynamicNumberOfNotes()) {
                Timber.d("Editing cloze/occlusion note type, disabling add/delete card template and deck override functionality")
                menu.findItem(R.id.action_add).isVisible = false
                menu.findItem(R.id.action_rename).isVisible = false
                menu.findItem(R.id.action_add_deck_override).isVisible = false
            } else {
                val template = getCurrentTemplate()

                @StringRes val overrideStringRes =
                    if (template != null && template.jsonObject.has("did") && !template.jsonObject.isNull("did")) {
                        R.string.card_template_editor_deck_override_on
                    } else {
                        R.string.card_template_editor_deck_override_off
                    }
                menu.findItem(R.id.action_add_deck_override).setTitle(overrideStringRes)
            }

            // It is invalid to delete if there is only one card template, remove the option from UI
            if (templateEditor.tempNoteType!!.templateCount < 2) {
                menu.findItem(R.id.action_delete).isVisible = false
            }

            // Hide preview option if the view is big enough
            if (templateEditor.fragmented) {
                menu.findItem(R.id.action_preview).isVisible = false
            }

            // marked insert field menu item invisible for style view
            val isInsertFieldItemVisible = currentEditorViewId != R.id.styling_edit
            menu.findItem(R.id.action_insert_field).isVisible = isInsertFieldItemVisible
        }

        @NeedsTest("Notetype is restored to stock kind")
        private suspend fun restoreNotetypeToStock(kind: StockNotetype.Kind? = null) {
            val nid = notetypeId { ntid = tempModel.noteTypeId }
            undoableOp { notetypes.restoreNotetypeToStock(nid, kind) }
            onModelSaved()
            showThemedToast(
                requireContext(),
                TR.cardTemplatesRestoredToDefault(),
                shortLength = false,
            )
        }

        /**
         * Handles the part of the menu set by [setupCommonMenu].
         * @returns whether the given item was handled
         * @see [onMenuItemSelected] and [onMenuItemClick]
         */
        @NeedsTest("Restore to default option")
        fun handleCommonMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_add -> {
                    Timber.i("CardTemplateEditor:: Add template button pressed")
                    addCardTemplate()
                    return true
                }
                R.id.action_reposition -> {
                    showRepositionDialog()
                    return true
                }
                R.id.action_rename -> {
                    Timber.i("CardTemplateEditor:: Rename button pressed")
                    showRenameDialog()
                    return true
                }
                R.id.action_copy_as_markdown -> {
                    Timber.i("CardTemplateEditor:: Copy markdown button pressed")
                    copyMarkdownTemplateToClipboard()
                    return true
                }
                R.id.action_insert_field -> {
                    Timber.i("CardTemplateEditor:: Insert field button pressed")
                    showInsertFieldDialog()
                    return true
                }
                R.id.action_delete -> {
                    Timber.i("CardTemplateEditor:: Delete template button pressed")
                    deleteCardTemplate()
                    return true
                }
                R.id.action_add_deck_override -> {
                    Timber.i("CardTemplateEditor:: Deck override button pressed")
                    displayDeckOverrideDialog(tempModel)
                    return true
                }
                R.id.action_preview -> {
                    Timber.i("CardTemplateEditor:: Preview button pressed")
                    performPreview()
                    return true
                }
                R.id.action_confirm -> {
                    Timber.i("CardTemplateEditor:: Save note type button pressed")
                    saveNoteType()
                }
                R.id.action_card_browser_appearance -> {
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                    openBrowserAppearance()
                }
                R.id.action_restore_to_default -> {
                    Timber.i("CardTemplateEditor:: Restore to default button pressed")

                    fun askUser(kind: StockNotetype.Kind? = null) {
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.cardTemplatesRestoreToDefault())
                            setMessage(TR.cardTemplatesRestoreToDefaultConfirmation())
                            setPositiveButton(R.string.dialog_ok) { _, _ ->
                                launchCatchingTask {
                                    restoreNotetypeToStock(kind)
                                }
                            }
                            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                        }
                    }

                    val originalStockKind = tempModel.notetype.originalStockKind
                    if (originalStockKind != ORIGINAL_STOCK_KIND_UNKNOWN_VALUE) {
                        Timber.d("Asking to restore to original stock kind %s", originalStockKind)
                        askUser()
                        return true
                    }

                    launchCatchingTask {
                        Timber.d("Unknown stock kind: asking which kind to restore to")
                        val stockNotetypeKinds = getStockNotetypeKinds()
                        val stockNotetypesNames =
                            withCol {
                                stockNotetypeKinds.map { getStockNotetype(it).name }
                            }
                        AlertDialog.Builder(requireContext()).show {
                            setTitle(TR.cardTemplatesRestoreToDefault())
                            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                            listItems(stockNotetypesNames) { _: DialogInterface, index: Int ->
                                val kind = stockNotetypeKinds[index]
                                askUser(kind)
                            }
                        }
                    }
                    true
                }
                else -> {
                    return false
                }
            }
        }

        private val currentTemplate: CardTemplate?
            get() =
                try {
                    val tempModel = templateEditor.tempNoteType
                    val template: BackendCardTemplate = tempModel!!.getTemplate(templateEditor.viewPager.currentItem)
                    CardTemplate(
                        front = template.qfmt,
                        back = template.afmt,
                        style = tempModel.css,
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                    null
                }

        /** Copies the template to clipboard in markdown format */
        fun copyMarkdownTemplateToClipboard() {
            // A number of users who post their templates to Reddit/Discord have these badly formatted
            // It makes it much easier for people to understand if these are provided as markdown
            val template = currentTemplate ?: return

            context?.let { ctx ->
                ctx.copyToClipboard(
                    template.toMarkdown(ctx),
                )
            }
        }

        private fun onModelSaved() {
            Timber.d("saveModelAndExitHandler::postExecute called")
            val button = templateEditor.findViewById<View>(R.id.action_confirm)
            if (button != null) {
                button.isEnabled = true
            }
            templateEditor.tempNoteType = null
            templateEditor.finish()
        }

        fun getNote(col: Collection): Note? {
            val nid = requireArguments().getLong(EDITOR_NOTE_ID)
            return if (nid != -1L) col.getNote(nid) else null
        }

        fun performPreview() {
            launchCatchingTask {
                val notetype = templateEditor.tempNoteType!!.notetype
                val notetypeFile = NotetypeFile(requireContext(), notetype)
                val ord = templateEditor.viewPager.currentItem
                val note = withCol { getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
                val args =
                    TemplatePreviewerArguments(
                        notetypeFile = notetypeFile,
                        id = note.id,
                        ord = ord,
                        fields = note.fields,
                        tags = note.tags,
                        fillEmpty = true,
                    )
                val intent = TemplatePreviewerPage.getIntent(requireContext(), args)
                startActivity(intent)
            }
        }

        fun displayDeckOverrideDialog(tempModel: CardTemplateNotetype) =
            launchCatchingTask {
                val activity = requireAnkiActivity()
                if (tempModel.notetype.isCloze) {
                    showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
                    return@launchCatchingTask
                }
                val name = getCurrentTemplateName(tempModel)
                val explanation = getString(R.string.deck_override_explanation, name)
                // Anki Desktop allows Dynamic decks, have reported this as a bug:
                // https://forums.ankiweb.net/t/minor-bug-deck-override-to-filtered-deck/1493
                val decks = SelectableDeck.fromCollection(includeFiltered = false)
                val title = getString(R.string.card_template_editor_deck_override)
                val dialog = DeckSelectionDialog.newInstance(title, explanation, true, decks)
                activity.showDialogFragment(dialog)
            }

        private fun getCurrentTemplateName(tempModel: CardTemplateNotetype): String =
            try {
                val ordinal = templateEditor.viewPager.currentItem
                val template = tempModel.getTemplate(ordinal)
                template.name
            } catch (e: Exception) {
                Timber.w(e, "Failed to get name for template")
                ""
            }

        private fun launchCardBrowserAppearance(currentTemplate: BackendCardTemplate) {
            val context = AnkiDroidApp.instance.baseContext
            val browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate)
            onCardBrowserAppearanceActivityResult.launch(browserAppearanceIntent)
        }

        @CheckResult
        private fun getCurrentTemplate(): BackendCardTemplate? {
            val currentCardTemplateIndex = getCurrentCardTemplateIndex()
            return try {
                templateEditor.tempNoteType!!.notetype.templates[currentCardTemplateIndex]
            } catch (e: JSONException) {
                Timber.w(e, "CardTemplateEditor::getCurrentTemplate - unexpectedly unable to fetch template? %d", currentCardTemplateIndex)
                null
            }
        } // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.

        /**
         * @return The index of the card template which is currently referred to by the fragment
         */
        private fun getCurrentCardTemplateIndex(): Int {
            // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.
            return requireArguments().getInt(CARD_INDEX)
        }

        private suspend fun deletionWouldOrphanNote(
            tempModel: CardTemplateNotetype?,
            position: Int,
        ): Boolean {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel!!, position)) {
                val currentDeletes = tempModel.getDeleteDbOrds(position)
                val cardIds = withCol { notetypes.getCardIdsForNoteType(tempModel.noteTypeId, currentDeletes) }
                if (cardIds == null) {
                    // It is possible but unlikely that a user has an in-memory template addition that would
                    // generate cards making the deletion safe, but we don't handle that. All users who do
                    // not already have cards generated making it safe will see this error message:
                    return true
                }
            }
            return false
        }

        private var onCardBrowserAppearanceActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) {
                    return@registerForActivityResult
                }
                onCardBrowserAppearanceResult(result.data)
            }
        private var onRequestPreviewResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode != RESULT_OK) {
                    return@registerForActivityResult
                }
                CardTemplateNotetype.clearTempNoteTypeFiles()
                // Make sure the fragments reinitialize, otherwise there is staleness on return
                (templateEditor.viewPager.adapter as TemplatePagerAdapter).ordinalShift()
                templateEditor.viewPager.adapter!!.notifyDataSetChanged()
            }

        private fun onCardBrowserAppearanceResult(data: Intent?) {
            val result = CardTemplateBrowserAppearanceEditor.Result.fromIntent(data)
            if (result == null) {
                Timber.w("Error processing Card Template Browser Appearance result")
                return
            }
            Timber.i("Applying Card Template Browser Appearance result")
            val currentTemplate = getCurrentTemplate()
            if (currentTemplate != null) {
                result.applyTo(currentTemplate)
            }
        }

        private fun noteTypeHasChanged(): Boolean = templateEditor.noteTypeHasChanged()

        /**
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param notetype note type to remove template from, modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmDeleteCards(
            tmpl: BackendCardTemplate,
            notetype: NotetypeJson,
            numAffectedCards: Int,
        ) {
            val d = ConfirmationDialog()
            val msg =
                String.format(
                    resources.getQuantityString(
                        R.plurals.card_template_editor_confirm_delete,
                        numAffectedCards,
                    ),
                    numAffectedCards,
                    tmpl.jsonObject.optString("name"),
                )
            d.setArgs(msg)

            val deleteCard = Runnable { deleteTemplate(tmpl, notetype) }
            val confirm = Runnable { executeWithSyncCheck(deleteCard) }
            d.setConfirm(confirm)
            templateEditor.showDialogFragment(d)
        }

        /**
         * Confirm if the user wants to add new card template
         * @param notetype note type to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(
            notetype: NotetypeJson,
            numAffectedCards: Int,
        ) {
            val d = ConfirmationDialog()
            val msg =
                String.format(
                    resources.getQuantityString(
                        R.plurals.card_template_editor_confirm_add,
                        numAffectedCards,
                    ),
                    numAffectedCards,
                )
            d.setArgs(msg)

            val addCard = Runnable { addNewTemplate(notetype) }
            val confirm = Runnable { executeWithSyncCheck(addCard) }
            d.setConfirm(confirm)
            templateEditor.showDialogFragment(d)
        }

        /**
         * Execute an action on the schema, asking the user to confirm that a full sync is ok
         * If [schemaChangingAction] is successfully executed, then the template is reloaded.
         *
         * This method is always useful because all calls to executeWithSyncCheck may need to refresh the previewer.
         * Due to conditional generation (e.g., {{#c5}}foo{{/c5}} which is non-empty only if it's the 5th card and is
         * empty otherwise), it's important to reload the template. This is particularly useful for cloze types,
         * where a card can move from the 5th to the 6th position due to adding an extra card type, causing content
         * to change or be deleted.
         *
         * @param schemaChangingAction The action to execute (adding / removing card)
         */
        private fun executeWithSyncCheck(schemaChangingAction: Runnable) {
            try {
                templateEditor.getColUnsafe.modSchema()
                schemaChangingAction.run()
                templateEditor.loadTemplatePreviewerFragmentIfFragmented()
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(R.string.full_sync_confirmation))
                val confirm =
                    Runnable {
                        templateEditor.getColUnsafe.modSchemaNoCheck()
                        schemaChangingAction.run()
                        templateEditor.dismissAllDialogFragments()
                    }
                val cancel = Runnable { templateEditor.dismissAllDialogFragments() }
                d.setConfirm(confirm)
                d.setCancel(cancel)
                templateEditor.showDialogFragment(d)
            }
        }

        /**
         * @param tmpl template to remove
         * @param notetype note type to remove from, updated in place by reference
         */
        private fun deleteTemplate(
            tmpl: BackendCardTemplate,
            notetype: NotetypeJson,
        ) {
            val oldTemplates = notetype.templates
            val newTemplates = CardTemplates(JSONArray())
            for (possibleMatch in oldTemplates) {
                if (possibleMatch.ord != tmpl.ord) {
                    newTemplates.append(possibleMatch)
                } else {
                    Timber.d("deleteTemplate() found match - removing template with ord %s", possibleMatch.ord)
                    templateEditor.tempNoteType!!.removeTemplate(possibleMatch.ord)
                }
            }
            notetype.templates = newTemplates
            Notetypes._updateTemplOrds(notetype)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            (templateEditor.viewPager.adapter as TemplatePagerAdapter).ordinalShift()
            templateEditor.viewPager.adapter!!.notifyDataSetChanged()
            templateEditor.viewPager.setCurrentItem(newTemplates.length() - 1, templateEditor.animationDisabled())
        }

        /**
         * Add new template to a given note type
         * @param noteType note type to add new template to
         */
        private fun addNewTemplate(noteType: NotetypeJson) {
            // Build new template
            val oldCardIndex = requireArguments().getInt(CARD_INDEX)
            val templates = noteType.templates
            val oldTemplate = templates[oldCardIndex]
            val newTemplate = Notetypes.newTemplate(newCardName(templates))
            // Set up question & answer formats
            newTemplate.qfmt = oldTemplate.qfmt
            newTemplate.afmt = oldTemplate.afmt
            // Reverse the front and back if only one template
            if (templates.length() == 1) {
                flipQA(newTemplate)
            }
            val lastExistingOrd = templates.last().ord
            Timber.d("addNewTemplate() lastExistingOrd was %s", lastExistingOrd)
            newTemplate.setOrd(lastExistingOrd + 1)
            templates.append(newTemplate)
            templateEditor.tempNoteType!!.addNewTemplate(newTemplate)
            templateEditor.viewPager.adapter!!.notifyDataSetChanged()
            templateEditor.viewPager.setCurrentItem(templates.length() - 1, templateEditor.animationDisabled())
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        @KotlinCleanup("Use Kotlin's Regex methods")
        private fun flipQA(template: BackendCardTemplate) {
            val qfmt = template.qfmt
            val afmt = template.afmt
            val m = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt)
            template.qfmt =
                if (!m.find()) {
                    afmt.replace("{{FrontSide}}", "")
                } else {
                    m.group(2)!!.trim()
                }
            template.afmt = "{{FrontSide}}\n\n<hr id=answer>\n\n$qfmt"
        }

        /**
         * Get name for new template
         * @param templates array of templates which is being added to
         * @return name for new template
         */
        private fun newCardName(templates: CardTemplates): String {
            // Start by trying to set the name to "Card n" where n is the new num of templates
            var n = templates.length() + 1
            // If the starting point for name already exists, iteratively increase n until we find a unique name
            while (true) {
                // Get new name
                val name = TR.cardTemplatesCard(n)
                // Cycle through all templates checking if new name exists
                if (templates.all { name != it.name }) {
                    return name
                }
                n += 1
            }
        }

        data class CardTemplate(
            val front: String,
            val back: String,
            val style: String,
        ) {
            fun toMarkdown(context: Context) =
                // backticks are not supported by old reddit
                buildString {
                    appendLine("**${context.getString(R.string.card_template_editor_front)}**\n")
                    appendLine("```html\n$front\n```\n")
                    appendLine("**${context.getString(R.string.card_template_editor_back)}**\n")
                    appendLine("```html\n$back\n```\n")
                    appendLine("**${context.getString(R.string.card_template_editor_styling)}**\n")
                    append("```css\n$style\n```")
                }
        }

        companion object {
            fun newInstance(
                cardIndex: Int,
                noteId: NoteId,
                viewId: Int,
            ): CardTemplateFragment {
                val f = CardTemplateFragment()
                val args = Bundle()
                args.putInt(CARD_INDEX, cardIndex)
                args.putLong(EDITOR_NOTE_ID, noteId)
                args.putInt(EDITOR_VIEW_ID_KEY, viewId)
                f.arguments = args
                return f
            }
        }
    }

    companion object {
        private const val TAB_TO_CURSOR_POSITION_KEY = "tabToCursorPosition"
        private const val EDITOR_VIEW_ID_KEY = "editorViewId"
        private const val TAB_TO_VIEW_ID = "tabToViewId"
        private const val EDITOR_NOTE_TYPE_ID = "noteTypeId"
        private const val EDITOR_NOTE_ID = "noteId"
        private const val EDITOR_START_ORD_ID = "ordId"
        private const val CARD_INDEX = "card_ord"

        // Keys for saving pane weights in SharedPreferences
        private const val PREF_TEMPLATE_EDITOR_PANE_WEIGHT = "cardTemplateEditorPaneWeight"
        private const val PREF_TEMPLATE_PREVIEWER_PANE_WEIGHT = "cardTemplatePreviewerPaneWeight"

        // Time to wait before refreshing the previewer
        private val REFRESH_PREVIEW_DELAY = 1.seconds

        @Suppress("unused")
        private const val REQUEST_PREVIEWER = 0

        @Suppress("unused")
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1
    }
}
