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

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anim.ActivityTransitionAnimation.Direction.END
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.InsertFieldDialog
import com.ichi2.anki.dialogs.InsertFieldDialog.Companion.REQUEST_FIELD_INSERT
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.annotations.NeedsTest
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Models.Companion.NOT_FOUND_NOTE_TYPE
import com.ichi2.ui.FixedEditText
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.FunctionalInterfaces
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Allows the user to view the template for the current note type
 */
@KotlinCleanup("lateinit wherever possible")
open class CardTemplateEditor : AnkiActivity(), DeckSelectionListener {
    @VisibleForTesting
    lateinit var viewPager: ViewPager2
    private var mSlidingTabLayout: TabLayout? = null
    var tempModel: TemporaryModel? = null
        private set
    private var mFieldNames: List<String>? = null
    private var mModelId: NoteTypeId = 0
    private var mNoteId: NoteId = 0

    // the position of the cursor in the editor view
    private var tabToCursorPosition: HashMap<Int, Int?> = HashMap()

    // the current editor view among front/style/back
    private var tabToViewId: HashMap<Int, Int?> = HashMap()
    private var mStartingOrdId = 0

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
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_template_editor_activity)
        // Load the args either from the intent or savedInstanceState bundle
        if (savedInstanceState == null) {
            // get model id
            mModelId = intent.getLongExtra(EDITOR_MODEL_ID, NOT_FOUND_NOTE_TYPE)
            if (mModelId == NOT_FOUND_NOTE_TYPE) {
                Timber.e("CardTemplateEditor :: no model ID was provided")
                finishWithoutAnimation()
                return
            }
            // get id for currently edited note (optional)
            mNoteId = intent.getLongExtra(EDITOR_NOTE_ID, -1L)
            // get id for currently edited template (optional)
            mStartingOrdId = intent.getIntExtra("ordId", -1)
            tabToCursorPosition[0] = 0
            tabToViewId[0] = R.id.front_edit
        } else {
            mModelId = savedInstanceState.getLong(EDITOR_MODEL_ID)
            mNoteId = savedInstanceState.getLong(EDITOR_NOTE_ID)
            mStartingOrdId = savedInstanceState.getInt(EDITOR_START_ORD_ID)
            tabToCursorPosition = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(TAB_TO_CURSOR_POSITION_KEY)!!
            tabToViewId = savedInstanceState.getSerializableCompat<HashMap<Int, Int?>>(TAB_TO_VIEW_ID)!!
            tempModel = TemporaryModel.fromBundle(savedInstanceState)
        }

        mSlidingTabLayout = findViewById(R.id.sliding_tabs)

        // Disable the home icon
        enableToolbar()
        startLoadingCollection()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        with(outState) {
            tempModel?.let { putAll(it.toBundle()) }
            putLong(EDITOR_MODEL_ID, mModelId)
            putLong(EDITOR_NOTE_ID, mNoteId)
            putInt(EDITOR_START_ORD_ID, mStartingOrdId)
            putSerializable(TAB_TO_VIEW_ID, tabToViewId)
            putSerializable(TAB_TO_CURSOR_POSITION_KEY, tabToCursorPosition)
            super.onSaveInstanceState(this)
        }
    }

    @Suppress("deprecation") // onBackPressed
    override fun onBackPressed() {
        if (modelHasChanged()) {
            showDiscardChangesDialog()
        } else {
            super.onBackPressed()
        }
    }

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
        // The first time the activity loads it has a model id but no edits yet, so no edited model
        // take the passed model id load it up for editing
        if (tempModel == null) {
            tempModel = TemporaryModel(Model(col.models.get(col, mModelId).toString()))
            // Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
        mFieldNames = tempModel!!.model.fieldsNames
        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById<ViewPager2?>(R.id.pager).apply {
            adapter = TemplatePagerAdapter(this@CardTemplateEditor)
        }
        // Set activity title
        supportActionBar?.let {
            it.setTitle(R.string.title_activity_template_editor)
            it.subtitle = tempModel!!.model.optString("name")
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", mModelId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", mStartingOrdId)
        if (mStartingOrdId != -1) {
            viewPager.setCurrentItem(mStartingOrdId, animationDisabled())
        }
    }

    fun modelHasChanged(): Boolean {
        val oldModel: JSONObject? = col.models.get(col, mModelId)
        return tempModel != null && tempModel!!.model.toString() != oldModel.toString()
    }

    private fun showDiscardChangesDialog() = DiscardChangesDialog.showDialog(this) {
        Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
        // Clear the edited model from any cache files, and clear it from this objects memory to discard changes
        TemporaryModel.clearTempModelFiles()
        tempModel = null
        finishWithAnimation(END)
    }

    /** When a deck is selected via Deck Override  */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (tempModel!!.model.isCloze) {
            Timber.w("Attempted to set deck for cloze model")
            showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
            return
        }

        val ordinal = viewPager.currentItem
        val template = tempModel!!.getTemplate(ordinal)
        val templateName = template.getString("name")

        if (deck != null && Decks.isDynamic(col, deck.deckId)) {
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
        if (keyCode == KeyEvent.KEYCODE_P) {
            if (event.isCtrlPressed) {
                val currentFragment = currentFragment
                currentFragment?.performPreview()
            }
        }
        return super.onKeyUp(keyCode, event)
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

        private var mBaseId: Long = 0

        override fun createFragment(position: Int): Fragment {
            val editorPosition = tabToCursorPosition[position] ?: 0
            val editorViewId = tabToViewId[position] ?: R.id.front_edit
            return CardTemplateFragment.newInstance(position, mNoteId, editorPosition, editorViewId)
        }

        override fun getItemCount(): Int = tempModel?.templateCount ?: 0

        override fun getItemId(position: Int): Long {
            return mBaseId + position
        }

        override fun containsItem(id: Long): Boolean {
            @Suppress("ConvertTwoComparisonsToRangeCheck") // more readable without the range check
            return (id - mBaseId < itemCount) && (id - mBaseId >= 0)
        }

        /** Force fragments to reinitialize contents by invalidating previous set of ordinal-based ids  */
        fun ordinalShift() {
            mBaseId += (itemCount + 1).toLong()
        }
    }

    class CardTemplateFragment : Fragment() {
        private var mCurrentEditorTitle: FixedTextView? = null
        private lateinit var mEditorEditText: FixedEditText

        var currentEditorViewId = 0
        private var cursorPosition = 0

        private lateinit var mTemplateEditor: CardTemplateEditor
        private var mTabLayoutMediator: TabLayoutMediator? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            // Storing a reference to the templateEditor allows us to use member variables
            mTemplateEditor = activity as CardTemplateEditor
            val mainView = inflater.inflate(R.layout.card_template_editor_item, container, false)
            val cardIndex = requireArguments().getInt(CARD_INDEX)
            val tempModel = mTemplateEditor.tempModel
            // Load template
            val template: JSONObject = try {
                tempModel!!.getTemplate(cardIndex)
            } catch (e: JSONException) {
                Timber.d(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                return mainView
            }

            mCurrentEditorTitle = mainView.findViewById(R.id.title_edit)
            mEditorEditText = mainView.findViewById(R.id.editor_editText)
            cursorPosition = requireArguments().getInt(CURSOR_POSITION_KEY)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mEditorEditText.customInsertionActionModeCallback = ActionModeCallback()
            }

            val bottomNavigation: BottomNavigationView = mainView.findViewById(R.id.card_template_editor_bottom_navigation)
            bottomNavigation.setOnItemSelectedListener { item: MenuItem ->
                val currentSelectedId = item.itemId
                mTemplateEditor.tabToViewId[cardIndex] = currentSelectedId
                when (currentSelectedId) {
                    R.id.styling_edit -> setCurrentEditorView(currentSelectedId, tempModel.css, R.string.card_template_editor_styling)
                    R.id.back_edit -> setCurrentEditorView(currentSelectedId, template.getString("afmt"), R.string.card_template_editor_back)
                    else -> setCurrentEditorView(currentSelectedId, template.getString("qfmt"), R.string.card_template_editor_front)
                }
                // contents of menu have changed and menu should be redrawn
                mTemplateEditor.invalidateOptionsMenu()
                true
            }
            // set saved or default view
            bottomNavigation.selectedItemId = requireArguments().getInt(EDITOR_VIEW_ID_KEY)

            // Set text change listeners
            val templateEditorWatcher: TextWatcher = object : TextWatcher {
                override fun afterTextChanged(arg0: Editable) {
                    mTemplateEditor.tabToCursorPosition[cardIndex] = mEditorEditText.selectionStart
                    when (currentEditorViewId) {
                        R.id.styling_edit -> tempModel.updateCss(mEditorEditText.text.toString())
                        R.id.back_edit -> template.put("afmt", mEditorEditText.text)
                        else -> template.put("qfmt", mEditorEditText.text)
                    }
                    mTemplateEditor.tempModel!!.updateTemplate(cardIndex, template)
                }

                override fun beforeTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                    /* do nothing */
                }

                override fun onTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) {
                    /* do nothing */
                }
            }
            mEditorEditText.addTextChangedListener(templateEditorWatcher)

            return mainView
        }

        /**
         * Custom ActionMode.Callback implementation for adding new field action
         * button in the text selection menu.
         */
        @TargetApi(23)
        private inner class ActionModeCallback : ActionMode.Callback {
            @RequiresApi(Build.VERSION_CODES.N)
            private val mInsertFieldId = 1

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && menu.findItem(mInsertFieldId) != null) {
                    return false
                }
                val initialSize = menu.size()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && currentEditorViewId != R.id.styling_edit) {
                    // 10644: Do not pass in a R.string as the final parameter as MIUI on Android 12 crashes.
                    menu.add(Menu.FIRST, mInsertFieldId, 0, getString(R.string.card_template_editor_insert_field))
                }

                return initialSize != menu.size()
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val itemId = item.itemId
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && itemId == mInsertFieldId) {
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
        private fun showInsertFieldDialog() {
            mTemplateEditor.mFieldNames?.let { fieldNames ->
                mTemplateEditor.showDialogFragment(InsertFieldDialog.newInstance(fieldNames))
            }
        }

        @Suppress("unused")
        private fun insertField(fieldName: String) {
            val start = max(mEditorEditText.selectionStart, 0)
            val end = max(mEditorEditText.selectionEnd, 0)
            // add string to editText
            val updatedString = "{{$fieldName}}"
            mEditorEditText.text!!.replace(min(start, end), max(start, end), updatedString, 0, updatedString.length)
        }

        fun setCurrentEditorView(id: Int, editorContent: String, editorTitleId: Int) {
            currentEditorViewId = id
            mEditorEditText.setText(editorContent)
            mCurrentEditorTitle!!.text = resources.getString(editorTitleId)
            mEditorEditText.setSelection(cursorPosition)
            mEditorEditText.requestFocus()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            initTabLayoutMediator()
            parentFragmentManager.setFragmentResultListener(REQUEST_FIELD_INSERT, viewLifecycleOwner) { key, bundle ->
                if (key == REQUEST_FIELD_INSERT) {
                    // this is guaranteed to be non null, as we put a non null value on the other side
                    insertField(bundle.getString(InsertFieldDialog.KEY_INSERTED_FIELD)!!)
                }
            }
            setupMenu()
        }

        private fun initTabLayoutMediator() {
            if (mTabLayoutMediator != null) {
                mTabLayoutMediator!!.detach()
            }
            mTabLayoutMediator = TabLayoutMediator(mTemplateEditor.mSlidingTabLayout!!, mTemplateEditor.viewPager) { tab: TabLayout.Tab, position: Int ->
                tab.text = mTemplateEditor.tempModel!!.getTemplate(position).getString("name")
            }
            mTabLayoutMediator!!.attach()
        }

        private fun setupMenu() {
            // Enable menu
            (requireActivity() as MenuHost).addMenuProvider(
                object : MenuProvider {
                    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                        menu.clear()
                        menuInflater.inflate(R.menu.card_template_editor, menu)

                        if (mTemplateEditor.tempModel!!.model.isCloze) {
                            Timber.d("Editing cloze model, disabling add/delete card template and deck override functionality")
                            menu.findItem(R.id.action_add).isVisible = false
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
                        if (mTemplateEditor.tempModel!!.templateCount < 2) {
                            menu.findItem(R.id.action_delete).isVisible = false
                        }

                        // marked insert field menu item invisible for style view
                        val isInsertFieldItemVisible = currentEditorViewId != R.id.styling_edit
                        menu.findItem(R.id.action_insert_field).isVisible = isInsertFieldItemVisible
                    }

                    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                        val col = mTemplateEditor.col
                        val tempModel = mTemplateEditor.tempModel
                        when (menuItem.itemId) {
                            R.id.action_add -> {
                                Timber.i("CardTemplateEditor:: Add template button pressed")
                                // Show confirmation dialog
                                val ordinal = mTemplateEditor.viewPager.currentItem
                                // isOrdinalPendingAdd method will check if there are any new card types added or not,
                                // if TempModel has new card type then numAffectedCards will be 0 by default.
                                val numAffectedCards = if (!TemporaryModel.isOrdinalPendingAdd(tempModel!!, ordinal)) {
                                    col.models.tmplUseCount(col, tempModel.model, ordinal)
                                } else {
                                    0
                                }
                                confirmAddCards(tempModel.model, numAffectedCards)
                                return true
                            }
                            R.id.action_insert_field -> showInsertFieldDialog()
                            R.id.action_delete -> {
                                Timber.i("CardTemplateEditor:: Delete template button pressed")
                                val res = resources
                                val ordinal = mTemplateEditor.viewPager.currentItem
                                val template = tempModel!!.getTemplate(ordinal)
                                // Don't do anything if only one template
                                if (tempModel.templateCount < 2) {
                                    mTemplateEditor.showSimpleMessageDialog(res.getString(R.string.card_template_editor_cant_delete))
                                    return true
                                }

                                if (deletionWouldOrphanNote(col, tempModel, ordinal)) {
                                    return true
                                }

                                // Show confirmation dialog
                                val numAffectedCards = if (!TemporaryModel.isOrdinalPendingAdd(tempModel, ordinal)) {
                                    Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                                    col.models.tmplUseCount(col, tempModel.model, ordinal)
                                } else {
                                    0
                                }
                                confirmDeleteCards(template, tempModel.model, numAffectedCards)
                                return true
                            }
                            R.id.action_add_deck_override -> {
                                displayDeckOverrideDialog(col, tempModel!!)
                                return true
                            }
                            R.id.action_preview -> {
                                performPreview()
                                return true
                            }
                            R.id.action_confirm -> {
                                Timber.i("CardTemplateEditor:: Save model button pressed")
                                if (modelHasChanged()) {
                                    val confirmButton = mTemplateEditor.findViewById<View>(R.id.action_confirm)
                                    if (confirmButton != null) {
                                        if (!confirmButton.isEnabled) {
                                            Timber.d("CardTemplateEditor::discarding extra click after button disabled")
                                            return true
                                        }
                                        confirmButton.isEnabled = false
                                    }
                                    launchCatchingTask(resources.getString(R.string.card_template_editor_save_error)) {
                                        requireActivity().withProgress(resources.getString(R.string.saving_model)) {
                                            withCol {
                                                tempModel!!.saveToDatabase(this)
                                            }
                                        }
                                        onModelSaved()
                                    }
                                } else {
                                    Timber.d("CardTemplateEditor:: model has not changed, exiting")
                                    mTemplateEditor.finishWithAnimation(END)
                                }

                                return true
                            }
                            R.id.action_card_browser_appearance -> {
                                Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                                val currentTemplate = getCurrentTemplate()
                                currentTemplate?.let { launchCardBrowserAppearance(it) }
                                return true
                            }
                        }
                        return false
                    }
                },
                viewLifecycleOwner,
                Lifecycle.State.RESUMED
            )
        }

        private fun onModelSaved() {
            Timber.d("saveModelAndExitHandler::postExecute called")
            val button = mTemplateEditor.findViewById<View>(R.id.action_confirm)
            if (button != null) {
                button.isEnabled = true
            }
            mTemplateEditor.tempModel = null
            mTemplateEditor.finishWithAnimation(END)
        }

        fun performPreview() {
            val col = mTemplateEditor.col
            val tempModel = mTemplateEditor.tempModel
            Timber.i("CardTemplateEditor:: Preview on tab %s", mTemplateEditor.viewPager.currentItem)
            // Create intent for the previewer and add some arguments
            val i = Intent(mTemplateEditor, CardTemplatePreviewer::class.java)
            val ordinal = mTemplateEditor.viewPager.currentItem
            val noteId = requireArguments().getLong("noteId")
            i.putExtra("ordinal", ordinal)
            i.putExtra("cardListIndex", 0)

            // If we have a card for this position, send it, otherwise an empty card list signals to show a blank
            if (noteId != -1L) {
                val cids = col.getNote(noteId).cids(col)
                if (ordinal < cids.size) {
                    i.putExtra("cardList", longArrayOf(cids[ordinal]))
                }
            }
            // Save the model and pass the filename if updated
            tempModel!!.editedModelFileName =
                TemporaryModel.saveTempModel(mTemplateEditor, tempModel.model)
            i.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModel.editedModelFileName)
            onRequestPreviewResult.launch(i)
        }

        private fun displayDeckOverrideDialog(col: Collection, tempModel: TemporaryModel) {
            val activity = requireActivity() as AnkiActivity
            if (tempModel.model.isCloze) {
                showSnackbar(getString(R.string.multimedia_editor_something_wrong), Snackbar.LENGTH_SHORT)
                return
            }
            val name = getCurrentTemplateName(tempModel)
            val explanation = getString(R.string.deck_override_explanation, name)
            // Anki Desktop allows Dynamic decks, have reported this as a bug:
            // https://forums.ankiweb.net/t/minor-bug-deck-override-to-filtered-deck/1493
            val nonDynamic = FunctionalInterfaces.Filter { d: Deck -> !Decks.isDynamic(d) }
            val decks = SelectableDeck.fromCollection(col, nonDynamic)
            val title = getString(R.string.card_template_editor_deck_override)
            val dialog = DeckSelectionDialog.newInstance(title, explanation, true, decks)
            showDialogFragment(activity, dialog)
        }

        private fun getCurrentTemplateName(tempModel: TemporaryModel): String {
            return try {
                val ordinal = mTemplateEditor.viewPager.currentItem
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
                mTemplateEditor.tempModel!!.model.getJSONArray("tmpls")
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

        private fun deletionWouldOrphanNote(col: Collection, tempModel: TemporaryModel?, position: Int): Boolean {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!TemporaryModel.isOrdinalPendingAdd(tempModel!!, position)) {
                val currentDeletes = tempModel.getDeleteDbOrds(position)
                // TODO - this is a SQL query on GUI thread - should see a DeckTask conversion ideally
                if (col.models.getCardIdsForModel(col, tempModel.modelId, currentDeletes) == null) {
                    // It is possible but unlikely that a user has an in-memory template addition that would
                    // generate cards making the deletion safe, but we don't handle that. All users who do
                    // not already have cards generated making it safe will see this error message:
                    mTemplateEditor.showSimpleMessageDialog(resources.getString(R.string.card_template_editor_would_delete_note))
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
                TemporaryModel.clearTempModelFiles()
                // Make sure the fragments reinitialize, otherwise there is staleness on return
                (mTemplateEditor.viewPager.adapter as TemplatePagerAdapter).ordinalShift()
                mTemplateEditor.viewPager.adapter!!.notifyDataSetChanged()
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
            return mTemplateEditor.modelHasChanged()
        }

        /**
         * Confirm if the user wants to delete all the cards associated with current template
         *
         * @param tmpl template to remove
         * @param model model to remove template from, modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmDeleteCards(tmpl: JSONObject, model: Model, numAffectedCards: Int) {
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

            val deleteCard = Runnable { deleteTemplate(tmpl, model) }
            val confirm = Runnable { executeWithSyncCheck(deleteCard) }
            d.setConfirm(confirm)
            mTemplateEditor.showDialogFragment(d)
        }

        /**
         * Confirm if the user wants to add new card template
         * @param model model to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(model: Model, numAffectedCards: Int) {
            val d = ConfirmationDialog()
            val msg = String.format(
                resources.getQuantityString(
                    R.plurals.card_template_editor_confirm_add,
                    numAffectedCards
                ),
                numAffectedCards
            )
            d.setArgs(msg)

            val addCard = Runnable { addNewTemplate(model) }
            val confirm = Runnable { executeWithSyncCheck(addCard) }
            d.setConfirm(confirm)
            mTemplateEditor.showDialogFragment(d)
        }

        /**
         * Execute an action on the schema, asking the user to confirm that a full sync is ok
         * @param schemaChangingAction The action to execute (adding / removing card)
         */
        private fun executeWithSyncCheck(schemaChangingAction: Runnable) {
            try {
                mTemplateEditor.col.modSchema()
                schemaChangingAction.run()
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(R.string.full_sync_confirmation))
                val confirm = Runnable {
                    mTemplateEditor.col.modSchemaNoCheck()
                    schemaChangingAction.run()
                    mTemplateEditor.dismissAllDialogFragments()
                }
                val cancel = Runnable { mTemplateEditor.dismissAllDialogFragments() }
                d.setConfirm(confirm)
                d.setCancel(cancel)
                mTemplateEditor.showDialogFragment(d)
            }
        }

        /**
         * @param tmpl template to remove
         * @param model model to remove from, updated in place by reference
         */
        private fun deleteTemplate(tmpl: JSONObject, model: Model) {
            val oldTemplates = model.getJSONArray("tmpls")
            val newTemplates = JSONArray()
            for (possibleMatch in oldTemplates.jsonObjectIterable()) {
                if (possibleMatch.getInt("ord") != tmpl.getInt("ord")) {
                    newTemplates.put(possibleMatch)
                } else {
                    Timber.d("deleteTemplate() found match - removing template with ord %s", possibleMatch.getInt("ord"))
                    mTemplateEditor.tempModel!!.removeTemplate(possibleMatch.getInt("ord"))
                }
            }
            model.put("tmpls", newTemplates)
            Models._updateTemplOrds(model)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            (mTemplateEditor.viewPager.adapter as TemplatePagerAdapter).ordinalShift()
            mTemplateEditor.viewPager.adapter!!.notifyDataSetChanged()
            mTemplateEditor.viewPager.setCurrentItem(newTemplates.length() - 1, mTemplateEditor.animationDisabled())
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
            val newTemplate = Models.newTemplate(newCardName(templates))
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
            mTemplateEditor.tempModel!!.addNewTemplate(newTemplate)
            mTemplateEditor.viewPager.adapter!!.notifyDataSetChanged()
            mTemplateEditor.viewPager.setCurrentItem(templates.length() - 1, mTemplateEditor.animationDisabled())
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
                val name = resources.getString(R.string.card_n_name, n)
                // Cycle through all templates checking if new name exists
                if (templates.jsonObjectIterable().all { name != it.getString("name") }) {
                    return name
                }
                n += 1
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

        @Suppress("unused")
        private const val REQUEST_PREVIEWER = 0

        @Suppress("unused")
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1
    }
}
