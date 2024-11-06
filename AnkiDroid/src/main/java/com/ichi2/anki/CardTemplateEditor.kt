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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldDialog.Companion.REQUEST_FIELD_INSERT
import com.ichi2.anki.notetype.RenameCardTemplateDialog
import com.ichi2.anki.notetype.RepositionCardTemplateDialog
import com.ichi2.anki.previewer.TemplatePreviewerArguments
import com.ichi2.anki.previewer.TemplatePreviewerFragment
import com.ichi2.anki.previewer.TemplatePreviewerPage
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.isImageOcclusion
import com.ichi2.anki.utils.postDelayed
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Note
import com.ichi2.libanki.NoteId
import com.ichi2.libanki.NoteTypeId
import com.ichi2.libanki.NotetypeJson
import com.ichi2.libanki.Notetypes
import com.ichi2.libanki.Notetypes.Companion.NOT_FOUND_NOTE_TYPE
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.themes.Themes
import com.ichi2.ui.FixedEditText
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.Translations
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * Allows the user to view the template for the current note type
 */
@KotlinCleanup("lateinit wherever possible")
open class CardTemplateEditor : AnkiActivity(), DeckSelectionListener {
    @VisibleForTesting
    lateinit var viewPager: ViewPager2
    private var slidingTabLayout: TabLayout? = null
    var tempModel: CardTemplateNotetype? = null
        private set
    private var fieldNames: List<String>? = null
    private var modelId: NoteTypeId = 0
    private var noteId: NoteId = 0

    // the position of the cursor in the editor view
    private var tabToCursorPosition: HashMap<Int, Int?> = HashMap()

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
            // get model id
            modelId = intent.getLongExtra(EDITOR_MODEL_ID, NOT_FOUND_NOTE_TYPE)
            if (modelId == NOT_FOUND_NOTE_TYPE) {
                Timber.e("CardTemplateEditor :: no model ID was provided")
                finish()
                return
            }
            // get id for currently edited note (optional)
            noteId = intent.getLongExtra(EDITOR_NOTE_ID, -1L)
            // get id for currently edited template (optional)
            startingOrdId = intent.getIntExtra("ordId", -1)
            tabToCursorPosition[0] = 0
            tabToViewId[0] = R.id.front_edit
        } else {
            modelId = savedInstanceState.getLong(EDITOR_MODEL_ID)
            noteId = savedInstanceState.getLong(EDITOR_NOTE_ID)
            startingOrdId = savedInstanceState.getInt(EDITOR_START_ORD_ID)
            tabToCursorPosition = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(TAB_TO_CURSOR_POSITION_KEY)!!
            tabToViewId = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(TAB_TO_VIEW_ID)!!
            tempModel = CardTemplateNotetype.fromBundle(savedInstanceState)
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

        // Open TemplatePreviewerFragment if in fragmented mode
        loadTemplatePreviewerFragmentIfFragmented()
    }

    /**
     *  Loads or reloads [tempModel] in [R.id.fragment_container] if the view is fragmented. Do nothing otherwise.
     */
    private fun loadTemplatePreviewerFragmentIfFragmented() {
        if (!fragmented) {
            return
        }
        launchCatchingTask {
            val notetype = tempModel!!.notetype
            val notetypeFile = NotetypeFile(this@CardTemplateEditor, notetype)
            val ord = viewPager.currentItem
            val note = withCol { currentFragment?.getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
            val args = TemplatePreviewerArguments(
                notetypeFile = notetypeFile,
                id = note.id,
                ord = ord,
                fields = note.fields,
                tags = note.tags,
                fillEmpty = true
            )
            val backgroundColor = Themes.getColorFromAttr(this@CardTemplateEditor, R.attr.alternativeBackgroundColor)
            val fragment = TemplatePreviewerFragment.newInstance(args, backgroundColor)
            supportFragmentManager.commitNow {
                replace(R.id.fragment_container, fragment)
            }
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            tempModel?.let { putAll(it.toBundle()) }
            putLong(EDITOR_MODEL_ID, modelId)
            putLong(EDITOR_NOTE_ID, noteId)
            putInt(EDITOR_START_ORD_ID, startingOrdId)
            putSerializable(TAB_TO_VIEW_ID, tabToViewId)
            putSerializable(TAB_TO_CURSOR_POSITION_KEY, tabToCursorPosition)
            super.onSaveInstanceState(this)
        }
    }

    @Suppress("deprecation") // onBackPressed
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (modelHasChanged()) {
            showDiscardChangesDialog()
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
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
        if (tempModel == null) {
            tempModel = CardTemplateNotetype(NotetypeJson(col.notetypes.get(modelId).toString()))
            // Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
        fieldNames = tempModel!!.notetype.fieldsNames
        // Set up the ViewPager with the sections adapter.
        viewPager.adapter = TemplatePagerAdapter(this@CardTemplateEditor)
        TabLayoutMediator(slidingTabLayout!!, viewPager) { tab: TabLayout.Tab, position: Int ->
            tab.text = tempModel!!.getTemplate(position).getString("name")
        }.apply { attach() }

        // Set activity title
        supportActionBar?.let {
            it.setTitle(R.string.title_activity_template_editor)
            it.subtitle = tempModel!!.notetype.optString("name")
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", modelId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", startingOrdId)
        if (startingOrdId != -1) {
            viewPager.setCurrentItem(startingOrdId, animationDisabled())
        }
    }

    fun modelHasChanged(): Boolean {
        val oldModel: JSONObject? = getColUnsafe.notetypes.get(modelId)
        return tempModel != null && tempModel!!.notetype.toString() != oldModel.toString()
    }

    private fun showDiscardChangesDialog() = DiscardChangesDialog.showDialog(this) {
        Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
        // Clear the edited model from any cache files, and clear it from this objects memory to discard changes
        CardTemplateNotetype.clearTempModelFiles()
        tempModel = null
        finish()
    }

    /** When a deck is selected via Deck Override  */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (tempModel!!.notetype.isCloze) {
            Timber.w("Attempted to set deck for cloze model")
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val ordinal = viewPager.currentItem
        val template = tempModel!!.getTemplate(ordinal)
        val templateName = template.getString("name")

        if (deck != null && getColUnsafe.decks.isFiltered(deck.deckId)) {
            Timber.w("Attempted to set default deck of %s to dynamic deck %s", templateName, deck.name)
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val message: String = if (deck == null) {
            Timber.i("Removing default template from template '%s'", templateName)
            template.put("did", JSONObject.NULL)
            getString(R.string.model_manager_deck_override_removed_message, templateName)
        } else {
            Timber.i("Setting template '%s' to '%s'", templateName, deck.name)
            template.put("did", deck.deckId)
            getString(R.string.model_manager_deck_override_added_message, templateName, deck.name)
        }

        showSnackbar(message, Snackbar.LENGTH_SHORT)

        // Deck Override can change from "on" <-> "off"
        invalidateOptionsMenu()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val currentFragment = currentFragment ?: return super.onKeyUp(keyCode, event)
        if (!event.isCtrlPressed) { return super.onKeyUp(keyCode, event) }
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
            KeyEvent.KEYCODE_A -> {
                Timber.i("Ctrl+A: Add card template from keypress")
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
        get() = try {
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
    inner class TemplatePagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

        private var baseId: Long = 0

        override fun createFragment(position: Int): Fragment {
            val editorPosition = tabToCursorPosition[position] ?: 0
            val editorViewId = tabToViewId[position] ?: R.id.front_edit
            return CardTemplateFragment.newInstance(position, noteId, editorPosition, editorViewId)
        }

        override fun getItemCount(): Int = tempModel?.templateCount ?: 0

        override fun getItemId(position: Int): Long {
            return baseId + position
        }

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
        get() = ShortcutGroup(
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
                shortcut("Ctrl+M", R.string.copy_the_template)
            ),
            R.string.card_template_editor_group
        )

    class CardTemplateFragment : Fragment() {
        private val refreshFragmentHandler = Handler(Looper.getMainLooper())
        private var currentEditorTitle: FixedTextView? = null
        private lateinit var editorEditText: FixedEditText

        var currentEditorViewId = 0
        private var cursorPosition = 0

        private lateinit var templateEditor: CardTemplateEditor
        lateinit var tempModel: CardTemplateNotetype
        lateinit var bottomNavigation: BottomNavigationView

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            // Storing a reference to the templateEditor allows us to use member variables
            templateEditor = activity as CardTemplateEditor
            val mainView = inflater.inflate(R.layout.card_template_editor_item, container, false)
            val cardIndex = requireArguments().getInt(CARD_INDEX)
            tempModel = templateEditor.tempModel!!
            // Load template
            val template: JSONObject = try {
                tempModel.getTemplate(cardIndex)
            } catch (e: JSONException) {
                Timber.d(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                return mainView
            }

            currentEditorTitle = mainView.findViewById(R.id.title_edit)
            editorEditText = mainView.findViewById(R.id.editor_editText)
            cursorPosition = requireArguments().getInt(CURSOR_POSITION_KEY)

            editorEditText.customInsertionActionModeCallback = ActionModeCallback()

            bottomNavigation = mainView.findViewById(R.id.card_template_editor_bottom_navigation)
            bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
                val currentSelectedId = item.itemId
                templateEditor.tabToViewId[cardIndex] = currentSelectedId
                when (currentSelectedId) {
                    R.id.styling_edit -> setCurrentEditorView(currentSelectedId, tempModel.css, R.string.card_template_editor_styling)
                    R.id.back_edit -> setCurrentEditorView(currentSelectedId, template.getString("afmt"), R.string.card_template_editor_back)
                    else -> setCurrentEditorView(currentSelectedId, template.getString("qfmt"), R.string.card_template_editor_front)
                }
                // contents of menu have changed and menu should be redrawn
                templateEditor.invalidateOptionsMenu()
                true
            }
            // set saved or default view
            bottomNavigation.selectedItemId =
                templateEditor.tabToViewId[cardIndex] ?: requireArguments().getInt(EDITOR_VIEW_ID_KEY)

            // Set text change listeners
            val templateEditorWatcher: TextWatcher = object : TextWatcher {
                /**
                 * Declare a nullable variable refreshFragmentRunnable of type Runnable.
                 * This will hold a reference to the Runnable that refreshes the previewer fragment.
                 * It is used to manage delayed fragment updates and can be null if no updates in card.
                 */
                private var refreshFragmentRunnable: Runnable? = null
                override fun afterTextChanged(arg0: Editable) {
                    refreshFragmentRunnable?.let { refreshFragmentHandler.removeCallbacks(it) }
                    templateEditor.tabToCursorPosition[cardIndex] = editorEditText.selectionStart
                    when (currentEditorViewId) {
                        R.id.styling_edit -> tempModel.updateCss(editorEditText.text.toString())
                        R.id.back_edit -> template.put("afmt", editorEditText.text)
                        else -> template.put("qfmt", editorEditText.text)
                    }
                    templateEditor.tempModel!!.updateTemplate(cardIndex, template)
                    val updateRunnable = Runnable {
                        templateEditor.loadTemplatePreviewerFragmentIfFragmented()
                    }
                    refreshFragmentRunnable = updateRunnable
                    refreshFragmentHandler.postDelayed(updateRunnable, REFRESH_PREVIEW_DELAY)
                }

                override fun beforeTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                    /* do nothing */
                }

                override fun onTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                    /* do nothing */
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

            return mainView
        }

        /**
         * Custom ActionMode.Callback implementation for adding new field action
         * button in the text selection menu.
         */
        private inner class ActionModeCallback : ActionMode.Callback {
            private val insertFieldId = 1

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (menu.findItem(insertFieldId) != null) {
                    return false
                }
                val initialSize = menu.size()

                if (currentEditorViewId != R.id.styling_edit) {
                    // 10644: Do not pass in a R.string as the final parameter as MIUI on Android 12 crashes.
                    menu.add(Menu.FIRST, insertFieldId, 0, getString(R.string.card_template_editor_insert_field))
                }

                return initialSize != menu.size()
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
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
            "the kotlin migration made this method crash due to a recursive call when the dialog would return its data"
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
            val template = templateEditor.tempModel!!.getTemplate(ordinal)

            RenameCardTemplateDialog.showInstance(
                requireContext(),
                prefill = template.getString("name")
            ) { newName ->
                template.put("name", newName)
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

        fun setCurrentEditorView(id: Int, editorContent: String, editorTitleId: Int) {
            currentEditorViewId = id
            editorEditText.setText(editorContent)
            currentEditorTitle!!.text = resources.getString(editorTitleId)
            editorEditText.setSelection(cursorPosition)
            editorEditText.requestFocus()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            templateEditor.slidingTabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(p0: TabLayout.Tab?) {
                    templateEditor.loadTemplatePreviewerFragmentIfFragmented()
                }
                override fun onTabUnselected(p0: TabLayout.Tab?) {
                }
                override fun onTabReselected(p0: TabLayout.Tab?) {
                }
            })
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
            val noteType = templateEditor.tempModel!!.notetype
            return noteType.isCloze || noteType.isImageOcclusion
        }

        private fun setupMenu() {
            (requireActivity() as MenuHost).addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                        menuInflater.inflate(R.menu.card_template_editor, menu)
                        setupCommonMenu(menu)
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                        return handleCommonMenuItemSelected(menuItem)
                    }
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED
            )
        }

        // TODO: Use withCol {} instead
        fun deleteCardTemplate() {
            val col = templateEditor.getColUnsafe
            val tempModel = templateEditor.tempModel
            val ordinal = templateEditor.viewPager.currentItem
            val template = tempModel!!.getTemplate(ordinal)
            // Don't do anything if only one template
            if (tempModel.templateCount < 2) {
                templateEditor.showSimpleMessageDialog(resources.getString(R.string.card_template_editor_cant_delete))
                return
            }

            if (deletionWouldOrphanNote(col, tempModel, ordinal)) {
                showOrphanNoteDialog()
                return
            }

            // Show confirmation dialog
            val numAffectedCards = if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel, ordinal)) {
                Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                col.notetypes.tmplUseCount(tempModel.notetype, ordinal)
            } else {
                0
            }
            confirmDeleteCards(template, tempModel.notetype, numAffectedCards)
        }

        /* showOrphanNoteDialog shows a AlertDialog if the deletionWouldOrphanNote returns true
        * it displays a warning for the user when they attempt to delete a card type that
            would leave some notes without any cards (orphan notes) */
        private fun showOrphanNoteDialog() {
            val builder = AlertDialog.Builder(requireContext())
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
            // Show confirmation dialog
            val ordinal = templateEditor.viewPager.currentItem
            // isOrdinalPendingAdd method will check if there are any new card types added or not,
            // if TempModel has new card type then numAffectedCards will be 0 by default.
            val numAffectedCards = if (!CardTemplateNotetype.isOrdinalPendingAdd(templateEditor.tempModel!!, ordinal)) {
                templateEditor.getColUnsafe.notetypes.tmplUseCount(templateEditor.tempModel!!.notetype, ordinal)
            } else {
                0
            }
            confirmAddCards(templateEditor.tempModel!!.notetype, numAffectedCards)
        }

        fun saveNoteType(): Boolean {
            if (modelHasChanged()) {
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
                        withCol { templateEditor.tempModel!!.saveToDatabase(this@withCol) }
                    }
                    onModelSaved()
                }
            } else {
                Timber.d("CardTemplateEditor:: model has not changed, exiting")
                templateEditor.finish()
            }
            return true
        }

        /**
         * Setups the part of the menu that can be used either in template editor or in previewer fragment.
         */
        fun setupCommonMenu(menu: Menu) {
            if (noteTypeCreatesDynamicNumberOfNotes()) {
                Timber.d("Editing cloze/occlusion model, disabling add/delete card template and deck override functionality")
                menu.findItem(R.id.action_add).isVisible = false
                menu.findItem(R.id.action_rename).isVisible = false
                menu.findItem(R.id.action_add_deck_override).isVisible = false
            } else {
                val template = getCurrentTemplate()

                @StringRes val overrideStringRes = if (template != null && template.has("did") && !template.isNull("did")) {
                    R.string.card_template_editor_deck_override_on
                } else {
                    R.string.card_template_editor_deck_override_off
                }
                menu.findItem(R.id.action_add_deck_override).setTitle(overrideStringRes)
            }

            // It is invalid to delete if there is only one card template, remove the option from UI
            if (templateEditor.tempModel!!.templateCount < 2) {
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

        /**
         * Handles the part of the menu set by [setupCommonMenu].
         * @returns whether the given item was handled
         * @see [onMenuItemSelected] and [onMenuItemClick]
         */
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
                    Timber.i("CardTemplateEditor:: Save model button pressed")
                    saveNoteType()
                }
                R.id.action_card_browser_appearance -> {
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                    openBrowserAppearance()
                }
                else -> {
                    return false
                }
            }
        }

        private val currentTemplate: CardTemplate?
            get() = try {
                val tempModel = templateEditor.tempModel
                val template: JSONObject = tempModel!!.getTemplate(templateEditor.viewPager.currentItem)
                CardTemplate(
                    front = template.getString("qfmt"),
                    back = template.getString("afmt"),
                    style = tempModel.css
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
                    template.toMarkdown(ctx)
                )
            }
        }

        private fun onModelSaved() {
            Timber.d("saveModelAndExitHandler::postExecute called")
            val button = templateEditor.findViewById<View>(R.id.action_confirm)
            if (button != null) {
                button.isEnabled = true
            }
            templateEditor.tempModel = null
            templateEditor.finish()
        }

        fun getNote(col: Collection): Note? {
            val nid = requireArguments().getLong(EDITOR_NOTE_ID)
            return if (nid != -1L) col.getNote(nid) else null
        }

        fun performPreview() {
            launchCatchingTask {
                val notetype = templateEditor.tempModel!!.notetype
                val notetypeFile = NotetypeFile(requireContext(), notetype)
                val ord = templateEditor.viewPager.currentItem
                val note = withCol { getNote(this) ?: Note.fromNotetypeId(this@withCol, notetype.id) }
                val args = TemplatePreviewerArguments(
                    notetypeFile = notetypeFile,
                    id = note.id,
                    ord = ord,
                    fields = note.fields,
                    tags = note.tags,
                    fillEmpty = true
                )
                val intent = TemplatePreviewerPage.getIntent(requireContext(), args)
                startActivity(intent)
            }
        }

        fun displayDeckOverrideDialog(tempModel: CardTemplateNotetype) = launchCatchingTask {
            val activity = requireActivity() as AnkiActivity
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
            showDialogFragment(activity, dialog)
        }

        private fun getCurrentTemplateName(tempModel: CardTemplateNotetype): String {
            return try {
                val ordinal = templateEditor.viewPager.currentItem
                val template = tempModel.getTemplate(ordinal)
                template.getString("name")
            } catch (e: Exception) {
                Timber.w(e, "Failed to get name for template")
                ""
            }
        }

        private fun launchCardBrowserAppearance(currentTemplate: JSONObject) {
            val context = AnkiDroidApp.instance.baseContext
            val browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate)
            onCardBrowserAppearanceActivityResult.launch(browserAppearanceIntent)
        }

        @CheckResult
        private fun getCurrentTemplate(): JSONObject? {
            val currentCardTemplateIndex = getCurrentCardTemplateIndex()
            return try {
                templateEditor.tempModel!!.notetype.getJSONArray("tmpls")
                    .getJSONObject(currentCardTemplateIndex)
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

        private fun deletionWouldOrphanNote(col: Collection, tempModel: CardTemplateNotetype?, position: Int): Boolean {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!CardTemplateNotetype.isOrdinalPendingAdd(tempModel!!, position)) {
                val currentDeletes = tempModel.getDeleteDbOrds(position)
                // TODO - this is a SQL query on GUI thread - should see a DeckTask conversion ideally
                if (col.notetypes.getCardIdsForModel(tempModel.modelId, currentDeletes) == null) {
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
                CardTemplateNotetype.clearTempModelFiles()
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

        private fun modelHasChanged(): Boolean {
            return templateEditor.modelHasChanged()
        }

        /**
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param notetype model to remove template from, modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmDeleteCards(tmpl: JSONObject, notetype: NotetypeJson, numAffectedCards: Int) {
            val d = ConfirmationDialog()
            val msg = String.format(
                resources.getQuantityString(
                    R.plurals.card_template_editor_confirm_delete,
                    numAffectedCards
                ),
                numAffectedCards,
                tmpl.optString("name")
            )
            d.setArgs(msg)

            val deleteCard = Runnable { deleteTemplate(tmpl, notetype) }
            val confirm = Runnable { executeWithSyncCheck(deleteCard) }
            d.setConfirm(confirm)
            templateEditor.showDialogFragment(d)
        }

        /**
         * Confirm if the user wants to add new card template
         * @param notetype model to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(notetype: NotetypeJson, numAffectedCards: Int) {
            val d = ConfirmationDialog()
            val msg = String.format(
                resources.getQuantityString(
                    R.plurals.card_template_editor_confirm_add,
                    numAffectedCards
                ),
                numAffectedCards
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
                val confirm = Runnable {
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
         * @param notetype model to remove from, updated in place by reference
         */
        private fun deleteTemplate(tmpl: JSONObject, notetype: NotetypeJson) {
            val oldTemplates = notetype.getJSONArray("tmpls")
            val newTemplates = JSONArray()
            for (possibleMatch in oldTemplates.jsonObjectIterable()) {
                if (possibleMatch.getInt("ord") != tmpl.getInt("ord")) {
                    newTemplates.put(possibleMatch)
                } else {
                    Timber.d("deleteTemplate() found match - removing template with ord %s", possibleMatch.getInt("ord"))
                    templateEditor.tempModel!!.removeTemplate(possibleMatch.getInt("ord"))
                }
            }
            notetype.put("tmpls", newTemplates)
            Notetypes._updateTemplOrds(notetype)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            (templateEditor.viewPager.adapter as TemplatePagerAdapter).ordinalShift()
            templateEditor.viewPager.adapter!!.notifyDataSetChanged()
            templateEditor.viewPager.setCurrentItem(newTemplates.length() - 1, templateEditor.animationDisabled())
        }

        /**
         * Add new template to a given model
         * @param model model to add new template to
         */
        private fun addNewTemplate(model: JSONObject) {
            // Build new template
            val oldCardIndex = requireArguments().getInt(CARD_INDEX)
            val templates = model.getJSONArray("tmpls")
            val oldTemplate = templates.getJSONObject(oldCardIndex)
            val newTemplate = Notetypes.newTemplate(newCardName(templates))
            // Set up question & answer formats
            newTemplate.put("qfmt", oldTemplate.getString("qfmt"))
            newTemplate.put("afmt", oldTemplate.getString("afmt"))
            // Reverse the front and back if only one template
            if (templates.length() == 1) {
                flipQA(newTemplate)
            }
            val lastExistingOrd = templates.getJSONObject(templates.length() - 1).getInt("ord")
            Timber.d("addNewTemplate() lastExistingOrd was %s", lastExistingOrd)
            newTemplate.put("ord", lastExistingOrd + 1)
            templates.put(newTemplate)
            templateEditor.tempModel!!.addNewTemplate(newTemplate)
            templateEditor.viewPager.adapter!!.notifyDataSetChanged()
            templateEditor.viewPager.setCurrentItem(templates.length() - 1, templateEditor.animationDisabled())
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        @KotlinCleanup("Use Kotlin's Regex methods")
        private fun flipQA(template: JSONObject) {
            val qfmt = template.getString("qfmt")
            val afmt = template.getString("afmt")
            val m = Pattern.compile("(?s)(.+)<hr id=answer>(.+)").matcher(afmt)
            if (!m.find()) {
                template.put("qfmt", afmt.replace("{{FrontSide}}", ""))
            } else {
                template.put("qfmt", m.group(2)!!.trim { it <= ' ' })
            }
            template.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n$qfmt")
        }

        /**
         * Get name for new template
         * @param templates array of templates which is being added to
         * @return name for new template
         */
        private fun newCardName(templates: JSONArray): String {
            // Start by trying to set the name to "Card n" where n is the new num of templates
            var n = templates.length() + 1
            // If the starting point for name already exists, iteratively increase n until we find a unique name
            while (true) {
                // Get new name
                val name = CollectionManager.TR.cardTemplatesCard(n)
                // Cycle through all templates checking if new name exists
                if (templates.jsonObjectIterable().all { name != it.getString("name") }) {
                    return name
                }
                n += 1
            }
        }

        data class CardTemplate(val front: String, val back: String, val style: String) {
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
                cursorPosition: Int,
                viewId: Int
            ): CardTemplateFragment {
                val f = CardTemplateFragment()
                val args = Bundle()
                args.putInt(CARD_INDEX, cardIndex)
                args.putLong(EDITOR_NOTE_ID, noteId)
                args.putInt(CURSOR_POSITION_KEY, cursorPosition)
                args.putInt(EDITOR_VIEW_ID_KEY, viewId)
                f.arguments = args
                return f
            }
        }
    }

    companion object {
        private const val CURSOR_POSITION_KEY = "cursorPosition"
        private const val TAB_TO_CURSOR_POSITION_KEY = "tabToCursorPosition"
        private const val EDITOR_VIEW_ID_KEY = "editorViewId"
        private const val TAB_TO_VIEW_ID = "tabToViewId"
        private const val EDITOR_MODEL_ID = "modelId"
        private const val EDITOR_NOTE_ID = "noteId"
        private const val EDITOR_START_ORD_ID = "ordId"
        private const val CARD_INDEX = "card_ord"

        // Time to wait before refreshing the previewer
        private val REFRESH_PREVIEW_DELAY = 1.seconds

        @Suppress("unused")
        private const val REQUEST_PREVIEWER = 0

        @Suppress("unused")
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1
    }
}
