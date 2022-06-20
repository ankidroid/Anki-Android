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
import android.util.Pair
import android.view.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.InsertFieldDialog.Companion.REQUEST_FIELD_INSERT
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Models.NOT_FOUND_NOTE_TYPE
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.ui.FixedEditText
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.*
import com.ichi2.utils.BundleUtils.getSerializableWithCast
import timber.log.Timber
import java.lang.Integer.max
import java.lang.Integer.min

/**
 * Allows the user to view the template for the current note type
 */
open class CardTemplateEditor : AnkiActivity(), DeckSelectionListener {
    @VisibleForTesting
    lateinit var viewPager: ViewPager2
    private lateinit var mSlidingTabLayout: TabLayout
    var tempModel: TemporaryModel? = null
        private set
    private lateinit var mFieldNames: List<String>
    private var mModelId: Long = 0
    private var mNoteId: Long = 0

    // the position of the cursor in the editor view
    private lateinit var tabToCursorPosition: HashMap<Int, Int?>

    // the current editor view among front/style/back
    private lateinit var tabToViewId: HashMap<Int, Int?>
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
        tabToCursorPosition = HashMap()
        tabToViewId = HashMap()
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
        }
        savedInstanceState?.run {
            mModelId = getLong(EDITOR_MODEL_ID)
            mNoteId = getLong(EDITOR_NOTE_ID)
            mStartingOrdId = getInt(EDITOR_START_ORD_ID)
            tabToCursorPosition = getSerializableWithCast(TAB_TO_CURSOR_POSITION_KEY)
            tabToViewId = getSerializableWithCast(TAB_TO_VIEW_ID)
            tempModel = TemporaryModel.fromBundle(this)
        }

        // Disable the home icon
        enableToolbar()
        startLoadingCollection()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.run {
            putAll(tempModel!!.toBundle())
            putLong(EDITOR_MODEL_ID, mModelId)
            putLong(EDITOR_NOTE_ID, mNoteId)
            putInt(EDITOR_START_ORD_ID, mStartingOrdId)
            putSerializable(TAB_TO_VIEW_ID, tabToViewId)
            putSerializable(TAB_TO_CURSOR_POSITION_KEY, tabToCursorPosition)
            super.onSaveInstanceState(this)
        }
    }

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
        tempModel = tempModel ?: TemporaryModel(Model(col.models.get(mModelId).toString()))
        mFieldNames = tempModel!!.model.fieldsNames
        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById(R.id.pager)
        viewPager.adapter = TemplatePagerAdapter(this)
        mSlidingTabLayout = findViewById(R.id.sliding_tabs)
        // Set activity title
        supportActionBar?.setTitle(R.string.title_activity_template_editor)
        supportActionBar?.subtitle = tempModel!!.model.optString("name")
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", mModelId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", mStartingOrdId)
        if (mStartingOrdId != -1) {
            viewPager.setCurrentItem(mStartingOrdId, animationDisabled())
        }
    }

    fun modelHasChanged(): Boolean {
        val oldModel: JSONObject? = col.models.get(mModelId)
        return tempModel != null && tempModel!!.model.toString() != oldModel.toString()
    }

    @VisibleForTesting
    fun showDiscardChangesDialog(): MaterialDialog {
        return DiscardChangesDialog.getDefault(this)
            .onPositive { _, _ ->
                Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
                // Clear the edited model from any cache files, and clear it from this objects memory to discard changes
                TemporaryModel.clearTempModelFiles()
                tempModel = null
                finishWithAnimation(END)
            }.build()
            .apply { show() }
    }

    /** When a deck is selected via Deck Override  */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (tempModel!!.model.isCloze) {
            Timber.w("Attempted to set deck for cloze model")
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true)
            return
        }

        val ordinal = viewPager.currentItem
        val template = tempModel!!.getTemplate(ordinal)
        val templateName = template.getString("name")

        if (deck != null && Decks.isDynamic(col, deck.deckId)) {
            Timber.w("Attempted to set default deck of %s to dynamic deck %s", templateName, deck.name)
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true)
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

        UIUtils.showThemedToast(this, message, true)

        // Deck Override can change from "on" <-> "off"
        invalidateOptionsMenu()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_P && event.isCtrlPressed) {
            currentFragment?.performPreview()
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

        override fun getItemCount(): Int {
            return tempModel?.templateCount ?: 0
        }

        override fun getItemId(position: Int): Long {
            return mBaseId + position
        }

        override fun containsItem(id: Long): Boolean {
            return (id - mBaseId) in 0 until itemCount
        }

        /** Force fragments to reinitialize contents by invalidating previous set of ordinal-based ids  */
        fun ordinalShift() {
            mBaseId += itemCount + 1
        }
    }

    class CardTemplateFragment : Fragment() {
        private lateinit var mCurrentEditorTitle: FixedTextView
        private lateinit var mEditorEditText: FixedEditText

        var currentEditorViewId = 0
        private var cursorPosition = 0

        private lateinit var mTemplateEditor: CardTemplateEditor
        private var mTabLayoutMediator: TabLayoutMediator? = null

        /**
         * @return The index of the card template which is currently referred to by the fragment
         */
        // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.
        private val currentCardTemplateIndex: Int
            get() = requireArguments().getInt(CARD_INDEX)

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

            mainView.findViewById<BottomNavigationView>(R.id.card_template_editor_bottom_navigation).apply {
                setOnItemSelectedListener { item: MenuItem ->
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
                selectedItemId = requireArguments().getInt(EDITOR_VIEW_ID_KEY)
            }

            // Set text change listeners
            val templateEditorWatcher = object : TextWatcher {
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

            // Enable menu
            setHasOptionsMenu(true)
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
            mTemplateEditor.mFieldNames.let { fieldNames ->
                mTemplateEditor.showDialogFragment(InsertFieldDialog.newInstance(fieldNames))
            }
        }

        private fun insertField(fieldName: String) {
            val start = max(mEditorEditText.selectionStart, 0)
            val end = max(mEditorEditText.selectionEnd, 0)
            // add string to editText
            val updatedString = "{{$fieldName}}"
            mEditorEditText.text!!.replace(min(start, end), max(start, end), updatedString, 0, updatedString.length)
        }

        fun setCurrentEditorView(id: Int, editorContent: String, editorTitleId: Int) {
            currentEditorViewId = id
            mEditorEditText.apply {
                setText(editorContent)
                mCurrentEditorTitle.text = resources.getString(editorTitleId)
                setSelection(cursorPosition)
                requestFocus()
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            initTabLayoutMediator()
            parentFragmentManager.setFragmentResultListener(REQUEST_FIELD_INSERT, viewLifecycleOwner) { key, bundle ->
                if (key == REQUEST_FIELD_INSERT) {
                    // this is guaranteed to be non null, as we put a non null value on the other side
                    insertField(bundle.getString(InsertFieldDialog.KEY_INSERTED_FIELD)!!)
                }
            }
        }

        private fun initTabLayoutMediator() {
            mTabLayoutMediator?.detach()
            mTabLayoutMediator = TabLayoutMediator(mTemplateEditor.mSlidingTabLayout, mTemplateEditor.viewPager) { tab, position ->
                tab.text = mTemplateEditor.tempModel!!.getTemplate(position).getString("name")
            }.apply { attach() }
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            menu.clear()
            inflater.inflate(R.menu.card_template_editor, menu)

            if (mTemplateEditor.tempModel!!.model.isCloze) {
                Timber.d("Editing cloze model, disabling add/delete card template and deck override functionality")
                menu.findItem(R.id.action_add).isVisible = false
                menu.findItem(R.id.action_add_deck_override).isVisible = false
            } else {
                val template = getCurrentTemplate()

                @StringRes val overrideStringRes = if (template?.has("did") == true && !template.isNull("did")) {
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
            super.onCreateOptionsMenu(menu, inflater)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val col = mTemplateEditor.col
            val tempModel = mTemplateEditor.tempModel
            when (item.itemId) {
                R.id.action_add -> {
                    Timber.i("CardTemplateEditor:: Add template button pressed")
                    // Show confirmation dialog
                    val ordinal = mTemplateEditor.viewPager.currentItem
                    // isOrdinalPendingAdd method will check if there are any new card types added or not,
                    // if TempModel has new card type then numAffectedCards will be 0 by default.
                    val numAffectedCards = if (!TemporaryModel.isOrdinalPendingAdd(tempModel!!, ordinal)) {
                        col.models.tmplUseCount(tempModel.model, ordinal)
                    } else {
                        0
                    }
                    confirmAddCards(tempModel.model, numAffectedCards)
                    return true
                }
                R.id.action_insert_field -> {
                    showInsertFieldDialog()
                }
                R.id.action_delete -> {
                    Timber.i("CardTemplateEditor:: Delete template button pressed")
                    val ordinal = mTemplateEditor.viewPager.currentItem
                    val template = tempModel!!.getTemplate(ordinal)
                    // Don't do anything if only one template
                    if (tempModel.templateCount < 2) {
                        mTemplateEditor.showSimpleMessageDialog(resources.getString(R.string.card_template_editor_cant_delete))
                        return true
                    }

                    if (deletionWouldOrphanNote(col, tempModel, ordinal)) {
                        return true
                    }

                    // Show confirmation dialog
                    val numAffectedCards = if (!TemporaryModel.isOrdinalPendingAdd(tempModel, ordinal)) {
                        Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                        col.models.tmplUseCount(tempModel.model, ordinal)
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
                        if (confirmButton?.isEnabled == false) {
                            Timber.d("CardTemplateEditor::discarding extra click after button disabled")
                            return true
                        }
                        confirmButton?.isEnabled = false
                        tempModel!!.saveToDatabase(saveModelAndExitHandler())
                    } else {
                        Timber.d("CardTemplateEditor:: model has not changed, exiting")
                        mTemplateEditor.finishWithAnimation(END)
                    }

                    return true
                }
                R.id.action_card_browser_appearance -> {
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                    getCurrentTemplate()?.run { launchCardBrowserAppearance(this) }
                    return super.onOptionsItemSelected(item)
                }
            }
            return super.onOptionsItemSelected(item)
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
                val cids = col.getNote(noteId).cids()
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
                UIUtils.showThemedToast(activity, getString(R.string.multimedia_editor_something_wrong), true)
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
                tempModel.getTemplate(ordinal).getString("name")
            } catch (e: Exception) {
                Timber.w(e, "Failed to get name for template")
                ""
            }
        }

        private fun launchCardBrowserAppearance(currentTemplate: JSONObject) {
            val context = AnkiDroidApp.getInstance().baseContext
            val browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate)
            onCardBrowserAppearanceActivityResult.launch(browserAppearanceIntent)
        }

        @CheckResult
        private fun getCurrentTemplate(): JSONObject? {
            return try {
                mTemplateEditor.tempModel!!.model.getJSONArray("tmpls")
                    .getJSONObject(currentCardTemplateIndex)
            } catch (e: JSONException) {
                Timber.w(e, "CardTemplateEditor::getCurrentTemplate - unexpectedly unable to fetch template? %d", currentCardTemplateIndex)
                null
            }
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
                if (col.models.getCardIdsForModel(tempModel.modelId, currentDeletes) == null) {

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
                if (result.resultCode == RESULT_OK) {
                    onCardBrowserAppearanceResult(result.data)
                }
            }
        private var onRequestPreviewResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    TemporaryModel.clearTempModelFiles()
                    // Make sure the fragments reinitialize, otherwise there is staleness on return
                    (mTemplateEditor.viewPager.adapter as TemplatePagerAdapter?)!!.run {
                        ordinalShift()
                        notifyDataSetChanged()
                    }
                }
            }

        private fun onCardBrowserAppearanceResult(data: Intent?) {
            val result = CardTemplateBrowserAppearanceEditor.Result.fromIntent(data)
            if (result == null) {
                Timber.w("Error processing Card Template Browser Appearance result")
                return
            }
            Timber.i("Applying Card Template Browser Appearance result")
            getCurrentTemplate()?.run {
                result.applyTo(this)
            }
        }

        /* Used for updating the collection when a model has been edited */
        private fun saveModelAndExitHandler(): SaveModelAndExitHandler {
            return SaveModelAndExitHandler(this)
        }

        class SaveModelAndExitHandler(templateFragment: CardTemplateFragment) :
            TaskListenerWithContext<CardTemplateFragment, Void?, Pair<Boolean, String?>>(
                templateFragment
            ) {
            private var mProgressDialog: MaterialDialog? = null
            override fun actualOnPreExecute(context: CardTemplateFragment) {
                Timber.d("saveModelAndExitHandler::preExecute called")
                mProgressDialog = StyledProgressDialog.show(context.mTemplateEditor, AnkiDroidApp.getAppResources().getString(R.string.saving_model), context.resources.getString(R.string.saving_changes), false)
            }

            override fun actualOnPostExecute(context: CardTemplateFragment, result: Pair<Boolean, String?>) {
                Timber.d("saveModelAndExitHandler::postExecute called")
                context.mTemplateEditor.findViewById<View>(R.id.action_confirm)
                    ?.run { isEnabled = true }
                if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                    mProgressDialog!!.dismiss()
                }
                context.mTemplateEditor.tempModel = null
                if (result.first) {
                    context.mTemplateEditor.finishWithAnimation(
                        END
                    )
                } else {
                    Timber.w("CardTemplateFragment:: save model task failed: %s", result.second)
                    UIUtils.showThemedToast(context.mTemplateEditor, context.getString(R.string.card_template_editor_save_error, result.second), false)
                    context.mTemplateEditor.finishWithoutAnimation()
                }
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
            ConfirmationDialog().run {
                val msg = String.format(
                    this@CardTemplateFragment.resources.getQuantityString(
                        R.plurals.card_template_editor_confirm_delete,
                        numAffectedCards
                    ),
                    numAffectedCards, tmpl.optString("name")
                )
                setArgs(msg)
                setConfirm { deleteTemplateWithCheck(tmpl, model) }
                mTemplateEditor.showDialogFragment(this)
            }
        }

        /**
         * Confirm if the user wants to add new card template
         * @param model model to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(model: Model, numAffectedCards: Int) {
            ConfirmationDialog().run {
                val msg = String.format(
                    this@CardTemplateFragment.resources.getQuantityString(R.plurals.card_template_editor_confirm_add, numAffectedCards),
                    numAffectedCards
                )
                setArgs(msg)
                setConfirm { addNewTemplateWithCheck(model) }
                mTemplateEditor.showDialogFragment(this)
            }
        }

        /**
         * Delete tmpl from model, asking user to confirm again if it's going to require a full sync
         *
         * @param tmpl template to remove
         * @param model model to remove template from, modified in place by reference
         */
        private fun deleteTemplateWithCheck(tmpl: JSONObject, model: Model) {
            try {
                mTemplateEditor.col.modSchema()
                deleteTemplate(tmpl, model)
            } catch (e: ConfirmModSchemaException) {
                e.log()
                ConfirmationDialog().run {
                    setArgs(resources.getString(R.string.full_sync_confirmation))
                    setConfirm {
                        mTemplateEditor.col.modSchemaNoCheck()
                        deleteTemplate(tmpl, model)
                    }
                    setCancel { mTemplateEditor.dismissAllDialogFragments() }
                    mTemplateEditor.showDialogFragment(this)
                }
            }
        }

        /**
         * @param tmpl template to remove
         * @param model model to remove from, updated in place by reference
         */
        private fun deleteTemplate(tmpl: JSONObject, model: Model) {
            val oldTemplates = model.getJSONArray("tmpls")
            val newTemplates = JSONArray().apply {
                oldTemplates.jsonObjectIterable()
                    .forEach {
                        if (it.getInt("ord") != tmpl.getInt("ord")) {
                            put(it)
                        } else {
                            Timber.d("deleteTemplate() found match - removing template with ord %s", it.getInt("ord"))
                            mTemplateEditor.tempModel!!.removeTemplate(it.getInt("ord"))
                        }
                    }
            }
            model.put("tmpls", newTemplates)
            Models._updateTemplOrds(model)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            mTemplateEditor.viewPager.run {
                (adapter as TemplatePagerAdapter?)!!.run {
                    ordinalShift()
                    notifyDataSetChanged()
                }
                setCurrentItem(newTemplates.length() - 1, mTemplateEditor.animationDisabled())
            }
            (activity as CardTemplateEditor?)?.dismissAllDialogFragments()
        }

        /**
         * Add new template to model, asking user to confirm if it's going to require a full sync
         *
         * @param model model to add new template to
         */
        private fun addNewTemplateWithCheck(model: JSONObject) {
            try {
                mTemplateEditor.col.modSchema()
                Timber.d("addNewTemplateWithCheck() called and no ConfirmModSchemaException?")
                addNewTemplate(model)
            } catch (e: ConfirmModSchemaException) {
                e.log()
                ConfirmationDialog().run {
                    setArgs(resources.getString(R.string.full_sync_confirmation))
                    setConfirm {
                        mTemplateEditor.col.modSchemaNoCheck()
                        addNewTemplate(model)
                    }
                    mTemplateEditor.showDialogFragment(this)
                }
            }
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
            Models.newTemplate(newCardName(templates)).apply {
                // Set up question & answer formats
                put("qfmt", oldTemplate.getString("qfmt"))
                put("afmt", oldTemplate.getString("afmt"))

                // Reverse the front and back if only one template
                if (templates.length() == 1) {
                    flipQA(this)
                }

                val lastExistingOrd = templates.getJSONObject(templates.length() - 1).getInt("ord")
                Timber.d("addNewTemplate() lastExistingOrd was %s", lastExistingOrd)
                put("ord", lastExistingOrd + 1)

                templates.put(this)
                mTemplateEditor.tempModel!!.addNewTemplate(this)
            }
            mTemplateEditor.viewPager.run {
                adapter!!.notifyDataSetChanged()
                setCurrentItem(templates.length() - 1, mTemplateEditor.animationDisabled())
            }
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
        private fun flipQA(template: JSONObject) {
            template.run {
                val qfmt = getString("qfmt")
                val afmt = getString("afmt")
                val m = "(?s)(.+)<hr id=answer>(.+)".toRegex().find(afmt)
                put(
                    "qfmt",
                    m?.destructured?.component2()?.trim { it <= ' ' }
                        ?: afmt.replace("{{FrontSide}}", "")
                )
                put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n$qfmt")
            }
        }

        /**
         * Get name for new template
         * @param templates array of templates which is being added to
         * @return name for new template
         */
        private fun newCardName(templates: JSONArray): String {
            return templates.jsonObjectIterable()
                .mapNotNull { it.getString("name").split(" ").last().toInt() } // get "n" at the end of each "Card %d"
                .reduce { acc, n -> max(acc, n) } // find maximum of all "n"
                .let { resources.getString(R.string.card_n_name, it + 1) }
        }

        companion object {
            fun newInstance(cardIndex: Int, noteId: Long, cursorPosition: Int, viewId: Int): CardTemplateFragment {
                return CardTemplateFragment().apply {
                    arguments = bundleOf(
                        CARD_INDEX to cardIndex,
                        EDITOR_NOTE_ID to noteId,
                        CURSOR_POSITION_KEY to cursorPosition,
                        EDITOR_VIEW_ID_KEY to viewId
                    )
                }
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
        private const val REQUEST_PREVIEWER = 0
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1
    }
}
