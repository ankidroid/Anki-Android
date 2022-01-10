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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DeckSelectionDialog.Companion.newInstance
import com.ichi2.anki.dialogs.DeckSelectionDialog.DeckSelectionListener
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck
import com.ichi2.anki.dialogs.DeckSelectionDialog.SelectableDeck.Companion.fromCollection
import com.ichi2.anki.dialogs.DiscardChangesDialog.Companion.getDefault
import com.ichi2.anki.dialogs.InsertFieldDialog.InsertFieldListener
import com.ichi2.anki.dialogs.InsertFieldDialogFactory
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.libanki.*
import com.ichi2.themes.StyledProgressDialog.Companion.show
import com.ichi2.ui.FixedEditText
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.*
import timber.log.Timber
import java.util.*
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

/**
 * Allows the user to view the template for the current note type
 */
@KotlinCleanup("use lateinit")
class CardTemplateEditor : AnkiActivity(), DeckSelectionListener {
    @JvmField
    @VisibleForTesting
    var mViewPager: ViewPager2? = null
    private var mSlidingTabLayout: TabLayout? = null
    var tempModel: TemporaryModel? = null
        private set

    @KotlinCleanup("make mFieldsNames non-null")
    private var mFieldNames: List<String>? = null
    private var mModelId: Long = 0
    private var mNoteId: Long = 0

    // the position of the cursor in the editor view
    private var mEditorPosition: HashMap<Int, Int?>? = null

    // the current editor view among front/style/back
    private var mEditorViewId: HashMap<Int, Int?>? = null
    private var mStartingOrdId = 0

    // ----------------------------------------------------------------------------
    // Listeners
    // ----------------------------------------------------------------------------

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_template_editor_activity)
        // Load the args either from the intent or savedInstanceState bundle
        mEditorPosition = HashMap()
        mEditorViewId = HashMap()
        if (savedInstanceState == null) {
            // get model id
            mModelId = intent.getLongExtra(EDITOR_MODEL_ID, Models.NOT_FOUND_NOTE_TYPE)
            if (mModelId == Models.NOT_FOUND_NOTE_TYPE) {
                Timber.e("CardTemplateEditor :: no model ID was provided")
                finishWithoutAnimation()
                return
            }
            // get id for currently edited note (optional)
            mNoteId = intent.getLongExtra(EDITOR_NOTE_ID, -1L)
            // get id for currently edited template (optional)
            mStartingOrdId = intent.getIntExtra("ordId", -1)
            mEditorPosition!![0] = 0
            mEditorViewId!![0] = R.id.front_edit
        } else {
            mModelId = savedInstanceState.getLong(EDITOR_MODEL_ID)
            mNoteId = savedInstanceState.getLong(EDITOR_NOTE_ID)
            mStartingOrdId = savedInstanceState.getInt(EDITOR_START_ORD_ID)
            mEditorPosition = savedInstanceState.getSerializable(EDITOR_POSITION_KEY) as HashMap<Int, Int?>?
            mEditorViewId = savedInstanceState.getSerializable(EDITOR_VIEW_ID_KEY) as HashMap<Int, Int?>?
            tempModel = TemporaryModel.fromBundle(savedInstanceState)!!
        }

        // Disable the home icon
        enableToolbar()
        startLoadingCollection()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putAll(tempModel!!.toBundle())
        outState.putLong(EDITOR_MODEL_ID, mModelId)
        outState.putLong(EDITOR_NOTE_ID, mNoteId)
        outState.putInt(EDITOR_START_ORD_ID, mStartingOrdId)
        outState.putSerializable(EDITOR_VIEW_ID_KEY, mEditorViewId)
        outState.putSerializable(EDITOR_POSITION_KEY, mEditorPosition)
        super.onSaveInstanceState(outState)
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
    override fun onCollectionLoaded(col: com.ichi2.libanki.Collection) {
        super.onCollectionLoaded(col)
        // The first time the activity loads it has a model id but no edits yet, so no edited model
        // take the passed model id load it up for editing
        if (tempModel == null) {
            tempModel = TemporaryModel(Model(col.models.get(mModelId).toString()))
            // Timber.d("onCollectionLoaded() model is %s", mTempModel.getModel().toString(2));
        }
        mFieldNames = tempModel!!.model.fieldsNames
        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.pager)
        mViewPager!!.adapter = TemplatePagerAdapter(this)
        mSlidingTabLayout = findViewById(R.id.sliding_tabs)
        // Set activity title
        if (supportActionBar != null) {
            supportActionBar!!.setTitle(R.string.title_activity_template_editor)
            supportActionBar!!.subtitle = tempModel!!.model.optString("name")
        }
        // Close collection opening dialog if needed
        Timber.i("CardTemplateEditor:: Card template editor successfully started for model id %d", mModelId)

        // Set the tab to the current template if an ord id was provided
        Timber.d("Setting starting tab to %d", mStartingOrdId)
        if (mStartingOrdId != -1) {
            mViewPager!!.setCurrentItem(mStartingOrdId, animationDisabled())
        }
    }

    fun modelHasChanged(): Boolean {
        val oldModel: JSONObject? = col.models.get(mModelId)
        return tempModel != null && tempModel!!.model.toString() != oldModel.toString()
    }

    @VisibleForTesting
    fun showDiscardChangesDialog(): MaterialDialog {
        val discardDialog = getDefault(this)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                Timber.i("TemplateEditor:: OK button pressed to confirm discard changes")
                // Clear the edited model from any cache files, and clear it from this objects memory to discard changes
                TemporaryModel.clearTempModelFiles()
                tempModel = null!!
                finishWithAnimation(ActivityTransitionAnimation.Direction.END)
            }
            .build()
        discardDialog.show()
        return discardDialog
    }

    @Suppress("deprecation")
    @KotlinCleanup("make deck non-null")
    /** When a deck is selected via Deck Override  */
    override fun onDeckSelected(deck: SelectableDeck?) {
        if (tempModel!!.model.isCloze) {
            Timber.w("Attempted to set deck for cloze model")
            showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true)
            return
        }
        val ordinal = mViewPager!!.currentItem
        val template = tempModel!!.getTemplate(ordinal)
        val templateName = template.getString("name")
        if (deck != null && Decks.isDynamic(col, deck.deckId)) {
            Timber.w("Attempted to set default deck of %s to dynamic deck %s", templateName, deck.name)
            showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), true)
            return
        }
        @Suppress("unused") val message: String = if (deck == null) {
            Timber.i("Removing default template from template '%s'", templateName)
            template.put("did", JSONObject.NULL)
            getString(R.string.model_manager_deck_override_removed_message, templateName)
        } else {
            Timber.i("Setting template '%s' to '%s'", templateName, deck.name)
            template.put("did", deck.deckId)
            @Suppress("unused")
            getString(R.string.model_manager_deck_override_added_message, templateName, deck.name)
        }
        showThemedToast(this, message, true)

        // Deck Override can change from "on" <-> "off"
        supportInvalidateOptionsMenu()
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

    @get:KotlinCleanup("make currentFragment non-null")
    @get:VisibleForTesting
    val currentFragment: CardTemplateFragment?
        get() = try {
            supportFragmentManager.findFragmentByTag("f" + mViewPager!!.currentItem) as CardTemplateFragment?
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
            var editorPosition = 0
            var editorViewId = R.id.front_edit
            if (mEditorPosition!![position] != null && mEditorViewId!![position] != null) {
                editorPosition = mEditorPosition!![position]!!
                editorViewId = mEditorViewId!![position]!!
            }
            return CardTemplateFragment.newInstance(position, mNoteId, editorPosition, editorViewId)
        }

        override fun getItemCount(): Int {
            return if (tempModel != null) {
                tempModel!!.templateCount
            } else 0
        }

        override fun getItemId(position: Int): Long {
            return mBaseId + position
        }

        override fun containsItem(id: Long): Boolean {
            return id - mBaseId in 0 until itemCount
        }

        /** Force fragments to reinitialize contents by invalidating previous set of ordinal-based ids  */
        fun ordinalShift() {
            mBaseId += (itemCount + 1).toLong()
        }
    }

    class CardTemplateFragment : Fragment() {

        private var mCurrentEdtiorTitle: FixedTextView? = null
        private var mEditorEditText: FixedEditText? = null
        var currentEditorViewId = 0
        private var mEditorPosition = 0
        private var mTemplateEditor: CardTemplateEditor? = null
        private var mTabLayoutMediator: TabLayoutMediator? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            // Storing a reference to the templateEditor allows us to use member variables
            mTemplateEditor = activity as CardTemplateEditor?
            val mainView = inflater.inflate(R.layout.card_template_editor_item, container, false)
            val position = requireArguments().getInt("position")
            val tempModel = mTemplateEditor!!.tempModel
            // Load template
            val template: JSONObject = try {
                tempModel!!.getTemplate(position)
            } catch (e: JSONException) {
                Timber.d(e, "Exception loading template in CardTemplateFragment. Probably stale fragment.")
                return mainView
            }
            mCurrentEdtiorTitle = mainView.findViewById(R.id.title_edit)
            mEditorEditText = mainView.findViewById(R.id.editor_editText)
            mEditorPosition = requireArguments().getInt(EDITOR_POSITION_KEY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mEditorEditText!!.customInsertionActionModeCallback = ActionModeCallback()
            }
            val bottomNavigation: BottomNavigationView = mainView.findViewById(R.id.card_template_editor_bottom_navigation)
            bottomNavigation.setOnNavigationItemSelectedListener { item: MenuItem ->
                val currentSelectedId = item.itemId
                mTemplateEditor!!.mEditorViewId!![position] = currentSelectedId
                if (currentSelectedId == R.id.styling_edit) {
                    setCurrentEditorView(currentSelectedId, tempModel.css, R.string.card_template_editor_styling)
                } else if (currentSelectedId == R.id.back_edit) {
                    setCurrentEditorView(currentSelectedId, template.getString("afmt"), R.string.card_template_editor_back)
                } else {
                    setCurrentEditorView(currentSelectedId, template.getString("qfmt"), R.string.card_template_editor_front)
                }
                // contents of menu have changed and menu should be redrawn
                mTemplateEditor!!.invalidateOptionsMenu()
                true
            }
            // set saved or default view
            bottomNavigation.selectedItemId = requireArguments().getInt(EDITOR_VIEW_ID_KEY)

            // Set text change listeners
            val templateEditorWatcher: TextWatcher = object : TextWatcher {
                override fun afterTextChanged(arg0: Editable) {
                    mTemplateEditor!!.mEditorPosition!![position] = mEditorEditText!!.selectionStart
                    when (currentEditorViewId) {
                        R.id.styling_edit -> {
                            tempModel.updateCss(mEditorEditText!!.text.toString())
                        }
                        R.id.back_edit -> {
                            template.put("afmt", mEditorEditText!!.text)
                        }
                        else -> {
                            template.put("qfmt", mEditorEditText!!.text)
                        }
                    }
                    mTemplateEditor!!.tempModel!!.updateTemplate(position, template)
                }

                override fun beforeTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) { /* do nothing */
                }

                override fun onTextChanged(arg0: CharSequence, arg1: Int, arg2: Int, arg3: Int) { /* do nothing */
                }
            }
            mEditorEditText!!.addTextChangedListener(templateEditorWatcher)

            // Enable menu
            setHasOptionsMenu(true)
            return mainView
        }

        /**
         * Custom ActionMode.Callback implementation for adding new field action
         * button in the text selection menu.
         */
        @TargetApi(23)
        private inner class ActionModeCallback() : ActionMode.Callback {
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
                    menu.add(Menu.FIRST, mInsertFieldId, 0, R.string.card_template_editor_insert_field)
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

        private fun showInsertFieldDialog() {
            if (mTemplateEditor!!.mFieldNames == null) {
                return
            }

            val insertFieldDialogFactory = InsertFieldDialogFactory(object : InsertFieldListener {
                override fun insertField(field: String?) {
                    insertFieldToCurrentEditorView(field!!)
                }
            }).attachToActivity<InsertFieldDialogFactory>(mTemplateEditor!!)

            val insertFieldDialog = insertFieldDialogFactory
                .newInsertFieldDialog()
                .withArguments(mTemplateEditor!!.mFieldNames!!)
            mTemplateEditor!!.showDialogFragment(insertFieldDialog)
        }

        fun insertFieldToCurrentEditorView(fieldName: String) {
            val start = max(mEditorEditText!!.selectionStart, 0)
            val end = max(mEditorEditText!!.selectionEnd, 0)
            // add string to editText
            val updatedString = "{{$fieldName}}"
            mEditorEditText!!.text!!.replace(min(start, end), max(start, end), updatedString, 0, updatedString.length)
        }

        fun setCurrentEditorView(id: Int, editorContent: String, editorTitleId: Int) {
            currentEditorViewId = id
            mEditorEditText!!.setText(editorContent)
            mCurrentEdtiorTitle!!.text = resources.getString(editorTitleId)
            mEditorEditText!!.setSelection(mEditorPosition)
            mEditorEditText!!.requestFocus()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            initTabLayoutMediator()
        }

        private fun initTabLayoutMediator() {
            if (mTabLayoutMediator != null) {
                mTabLayoutMediator!!.detach()
            }
            mTabLayoutMediator = TabLayoutMediator(
                mTemplateEditor!!.mSlidingTabLayout!!, mTemplateEditor!!.mViewPager!!
            ) { tab: TabLayout.Tab, position: Int -> tab.text = mTemplateEditor!!.tempModel!!.getTemplate(position).getString("name") }
            mTabLayoutMediator!!.attach()
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            menu.clear()
            inflater.inflate(R.menu.card_template_editor, menu)
            if (mTemplateEditor!!.tempModel!!.model.isCloze) {
                Timber.d("Editing cloze model, disabling add/delete card template and deck override functionality")
                menu.findItem(R.id.action_add).isVisible = false
                menu.findItem(R.id.action_add_deck_override).isVisible = false
            } else {
                val template = currentTemplate
                @StringRes var overrideStringRes = R.string.card_template_editor_deck_override_off
                if (template != null && template.has("did") && !template.isNull("did")) {
                    overrideStringRes = R.string.card_template_editor_deck_override_on
                }
                menu.findItem(R.id.action_add_deck_override).setTitle(overrideStringRes)
            }

            // It is invalid to delete if there is only one card template, remove the option from UI
            if (mTemplateEditor!!.tempModel!!.templateCount < 2) {
                menu.findItem(R.id.action_delete).isVisible = false
            }

            // marked insert field menu item invisible for style view
            val isInsertFieldItemVisible = currentEditorViewId != R.id.styling_edit
            menu.findItem(R.id.action_insert_field).isVisible = isInsertFieldItemVisible
            super.onCreateOptionsMenu(menu, inflater)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val col = mTemplateEditor!!.col
            val tempModel = mTemplateEditor!!.tempModel
            when (item.itemId) {
                R.id.action_add -> {
                    Timber.i("CardTemplateEditor:: Add template button pressed")
                    // Show confirmation dialog
                    val ordinal = mTemplateEditor!!.mViewPager!!.currentItem
                    var numAffectedCards = 0
                    // isOrdinalPendingAdd method will check if there are any new card types added or not,
                    // if TempModel has new card type then numAffectedCards will be 0 by default.
                    if (!TemporaryModel.isOrdinalPendingAdd(tempModel, ordinal)) {
                        numAffectedCards = col.models.tmplUseCount(tempModel!!.model, ordinal)
                    }
                    confirmAddCards(tempModel!!.model, numAffectedCards)
                    return true
                }
                R.id.action_insert_field -> {
                    showInsertFieldDialog()
                }
                R.id.action_delete -> {
                    Timber.i("CardTemplateEditor:: Delete template button pressed")
                    val res = resources
                    val ordinal = mTemplateEditor!!.mViewPager!!.currentItem
                    val template = tempModel!!.getTemplate(ordinal)
                    // Don't do anything if only one template
                    if (tempModel.templateCount < 2) {
                        mTemplateEditor!!.showSimpleMessageDialog(res.getString(R.string.card_template_editor_cant_delete))
                        return true
                    }
                    if (deletionWouldOrphanNote(col, tempModel, ordinal)) {
                        return true
                    }

                    // Show confirmation dialog
                    var numAffectedCards = 0
                    if (!TemporaryModel.isOrdinalPendingAdd(tempModel, ordinal)) {
                        Timber.d("Ordinal is not a pending add, so we'll get the current card count for confirmation")
                        numAffectedCards = col.models.tmplUseCount(tempModel.model, ordinal)
                    }
                    confirmDeleteCards(template, tempModel.model, numAffectedCards)
                    return true
                }
                R.id.action_add_deck_override -> {
                    displayDeckOverrideDialog(col, tempModel)
                    return true
                }
                R.id.action_preview -> {
                    performPreview()
                    return true
                }
                R.id.action_confirm -> {
                    Timber.i("CardTemplateEditor:: Save model button pressed")
                    if (modelHasChanged()) {
                        val confirmButton = mTemplateEditor!!.findViewById<View>(R.id.action_confirm)
                        if (confirmButton != null) {
                            if (!confirmButton.isEnabled) {
                                Timber.d("CardTemplateEditor::discarding extra click after button disabled")
                                return true
                            }
                            confirmButton.isEnabled = false
                        }
                        tempModel!!.saveToDatabase(saveModelAndExitHandler())
                    } else {
                        Timber.d("CardTemplateEditor:: model has not changed, exiting")
                        mTemplateEditor!!.finishWithAnimation(ActivityTransitionAnimation.Direction.END)
                    }
                    return true
                }
                R.id.action_card_browser_appearance -> {
                    Timber.i("CardTemplateEditor::Card Browser Template button pressed")
                    val currentTemplate = currentTemplate
                    currentTemplate?.let { launchCardBrowserAppearance(it) }
                    return super.onOptionsItemSelected(item)
                }
            }
            return super.onOptionsItemSelected(item)
        }

        fun performPreview() {
            val col = mTemplateEditor!!.col
            val tempModel = mTemplateEditor!!.tempModel
            Timber.i("CardTemplateEditor:: Preview on tab %s", mTemplateEditor!!.mViewPager!!.currentItem)
            // Create intent for the previewer and add some arguments
            val i = Intent(mTemplateEditor, CardTemplatePreviewer::class.java)
            val ordinal = mTemplateEditor!!.mViewPager!!.currentItem
            val noteId = requireArguments().getLong("noteId")
            i.putExtra("ordinal", ordinal)
            i.putExtra("cardListIndex", 0)

            // If we have a card for this position, send it, otherwise an empty cardlist signals to show a blank
            if (noteId != -1L) {
                val cids = col.getNote(noteId).cids()
                if (ordinal < cids.size) {
                    i.putExtra("cardList", longArrayOf(cids[ordinal]))
                }
            }
            // Save the model and pass the filename if updated
            tempModel!!.editedModelFileName = TemporaryModel.saveTempModel(mTemplateEditor!!, tempModel.model)
            i.putExtra(TemporaryModel.INTENT_MODEL_FILENAME, tempModel.editedModelFileName)
            onRequestPreviewResult.launch(i)
        }

        private fun displayDeckOverrideDialog(col: com.ichi2.libanki.Collection, tempModel: TemporaryModel?) {
            val activity = requireActivity() as AnkiActivity
            if (tempModel!!.model.isCloze) {
                showThemedToast(activity, getString(R.string.multimedia_editor_something_wrong), true)
                return
            }
            val name = getCurrentTemplateName(tempModel)
            val explanation = getString(R.string.deck_override_explanation, name)
            // Anki Desktop allows Dynamic decks, have reported this as a bug:
            // https://forums.ankiweb.net/t/minor-bug-deck-override-to-filtered-deck/1493
            val nonDynamic = FunctionalInterfaces.Filter { d: Deck? -> !Decks.isDynamic(d) }
            val decks = fromCollection(col, nonDynamic)
            val title = getString(R.string.card_template_editor_deck_override)
            val dialog = newInstance(title, explanation, true, decks)
            showDialogFragment(activity, dialog)
        }

        private fun getCurrentTemplateName(tempModel: TemporaryModel?): String {
            return try {
                val ordinal = mTemplateEditor!!.mViewPager!!.currentItem
                val template = tempModel!!.getTemplate(ordinal)
                template.getString("name")
            } catch (e: Exception) {
                Timber.w(e, "Failed to get name for template")
                ""
            }
        }

        private fun launchCardBrowserAppearance(currentTemplate: JSONObject) {
            val context = AnkiDroidApp.getInstance().baseContext
            if (context == null) {
                // Catch-22, we can't notify failure as there's no context. Shouldn't happen anyway
                Timber.w("Context was null - couldn't launch Card Browser Appearance window")
                return
            }
            val browserAppearanceIntent = CardTemplateBrowserAppearanceEditor.getIntentFromTemplate(context, currentTemplate)
            onCardBrowserAppearanceActivityResult.launch(browserAppearanceIntent)
        }

        @get:CheckResult
        private val currentTemplate: JSONObject?
            get() {
                val currentCardTemplateIndex = currentCardTemplateIndex
                return try {
                    mTemplateEditor!!.tempModel!!.model.getJSONArray("tmpls").getJSONObject(currentCardTemplateIndex)
                } catch (e: JSONException) {
                    Timber.w(e, "CardTemplateEditor::getCurrentTemplate - unexpectedly unable to fetch template? %d", currentCardTemplateIndex)
                    null
                }
            } // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.

        /**
         * @return The index of the card template which is currently referred to by the fragment
         */
        @get:CheckResult
        private val currentCardTemplateIndex: Int
            get() = // COULD_BE_BETTER: Lots of duplicate code could call this. Hold off on the refactor until #5151 goes in.
                requireArguments().getInt("position")

        private fun deletionWouldOrphanNote(col: com.ichi2.libanki.Collection, tempModel: TemporaryModel?, position: Int): Boolean {
            // For existing templates, make sure we won't leave orphaned notes if we delete the template
            //
            // Note: we are in-memory, so the database is unaware of previous but unsaved deletes.
            // If we were deleting a template we just added, we don't care. If not, then for every
            // template delete queued up, we check the database to see if this delete in combo with any other
            // pending deletes could orphan cards
            if (!TemporaryModel.isOrdinalPendingAdd(tempModel, position)) {
                val currentDeletes = tempModel!!.getDeleteDbOrds(position)
                // TODO - this is a SQL query on GUI thread - should see a DeckTask conversion ideally
                if (col.models.getCardIdsForModel(tempModel.modelId, currentDeletes) == null) {

                    // It is possible but unlikely that a user has an in-memory template addition that would
                    // generate cards making the deletion safe, but we don't handle that. All users who do
                    // not already have cards generated making it safe will see this error message:
                    mTemplateEditor!!.showSimpleMessageDialog(resources.getString(R.string.card_template_editor_would_delete_note))
                    return true
                }
            }
            return false
        }

        private var onCardBrowserAppearanceActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK) {
                return@registerForActivityResult
            }
            onCardBrowserAppearanceResult(result.data)
        }

        private var onRequestPreviewResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode != RESULT_OK) {
                return@registerForActivityResult
            }
            TemporaryModel.clearTempModelFiles()
            // Make sure the fragments reinitialize, otherwise there is staleness on return
            (mTemplateEditor!!.mViewPager!!.adapter as TemplatePagerAdapter?)!!.ordinalShift()
            mTemplateEditor!!.mViewPager!!.adapter!!.notifyDataSetChanged()
        }

        @KotlinCleanup("make data non-null")
        private fun onCardBrowserAppearanceResult(data: Intent?) {
            val result = CardTemplateBrowserAppearanceEditor.Result.fromIntent(data)
            if (result == null) {
                Timber.w("Error processing Card Template Browser Appearance result")
                return
            }
            Timber.i("Applying Card Template Browser Appearance result")
            val currentTemplate = currentTemplate
            if (currentTemplate != null) {
                result.applyTo(currentTemplate)
            }
        }

        /* Used for updating the collection when a model has been edited */
        private fun saveModelAndExitHandler(): SaveModelAndExitHandler {
            return SaveModelAndExitHandler(this)
        }

        internal class SaveModelAndExitHandler(templateFragment: CardTemplateFragment?) : TaskListenerWithContext<CardTemplateFragment?, Void?, Pair<Boolean?, String?>?>(templateFragment) {
            private var mProgressDialog: MaterialDialog? = null
            override fun actualOnPreExecute(templateFragment: CardTemplateFragment) {
                Timber.d("saveModelAndExitHandler::preExecute called")
                mProgressDialog = show(
                    templateFragment.mTemplateEditor!!, AnkiDroidApp.getAppResources().getString(R.string.saving_model),
                    templateFragment.resources.getString(R.string.saving_changes), false
                )
            }

            override fun actualOnPostExecute(templateFragment: CardTemplateFragment, result: Pair<Boolean?, String?>?) {
                Timber.d("saveModelAndExitHandler::postExecute called")
                val button = templateFragment.mTemplateEditor!!.findViewById<View>(R.id.action_confirm)
                if (button != null) {
                    button.isEnabled = true
                }
                if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                    mProgressDialog!!.dismiss()
                }
                templateFragment.mTemplateEditor!!.tempModel = null!!
                if (result!!.first!!) {
                    templateFragment.mTemplateEditor!!.finishWithAnimation(ActivityTransitionAnimation.Direction.END)
                } else {
                    Timber.w("CardTemplateFragment:: save model task failed: %s", result.second)
                    showThemedToast(templateFragment.mTemplateEditor, templateFragment.getString(R.string.card_template_editor_save_error, result.second), false)
                    templateFragment.mTemplateEditor!!.finishWithoutAnimation()
                }
            }
        }

        private fun modelHasChanged(): Boolean {
            return mTemplateEditor!!.modelHasChanged()
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
            val res = resources
            val msg = String.format(
                res.getQuantityString(
                    R.plurals.card_template_editor_confirm_delete,
                    numAffectedCards
                ),
                numAffectedCards, tmpl.optString("name")
            )
            d.setArgs(msg)
            val confirm = Runnable { deleteTemplateWithCheck(tmpl, model) }
            d.setConfirm(confirm)
            mTemplateEditor!!.showDialogFragment(d)
        }

        /**
         * Confirm if the user wants to add new card template
         * @param model model to add new template and modified in place by reference
         * @param numAffectedCards number of cards which will be affected
         */
        private fun confirmAddCards(model: Model, numAffectedCards: Int) {
            val d = ConfirmationDialog()
            val res = resources
            val msg = String.format(
                res.getQuantityString(
                    R.plurals.card_template_editor_confirm_add,
                    numAffectedCards
                ),
                numAffectedCards
            )
            d.setArgs(msg)
            val confirm = Runnable { addNewTemplateWithCheck(model) }
            d.setConfirm(confirm)
            mTemplateEditor!!.showDialogFragment(d)
        }

        /**
         * Delete tmpl from model, asking user to confirm again if it's going to require a full sync
         *
         * @param tmpl template to remove
         * @param model model to remove template from, modified in place by reference
         */
        private fun deleteTemplateWithCheck(tmpl: JSONObject, model: Model) {
            try {
                mTemplateEditor!!.col.modSchema()
                deleteTemplate(tmpl, model)
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(R.string.full_sync_confirmation))
                val confirm = Runnable {
                    mTemplateEditor!!.col.modSchemaNoCheck()
                    deleteTemplate(tmpl, model)
                }
                val cancel = Runnable { mTemplateEditor!!.dismissAllDialogFragments() }
                d.setConfirm(confirm)
                d.setCancel(cancel)
                mTemplateEditor!!.showDialogFragment(d)
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
                    mTemplateEditor!!.tempModel!!.removeTemplate(possibleMatch.getInt("ord"))
                }
            }
            model.put("tmpls", newTemplates)
            Models._updateTemplOrds(model)
            // Make sure the fragments reinitialize, otherwise the reused ordinal causes staleness
            (mTemplateEditor!!.mViewPager!!.adapter as TemplatePagerAdapter?)!!.ordinalShift()
            mTemplateEditor!!.mViewPager!!.adapter!!.notifyDataSetChanged()
            mTemplateEditor!!.mViewPager!!.setCurrentItem(newTemplates.length() - 1, mTemplateEditor!!.animationDisabled())
            if (activity != null) {
                (activity as CardTemplateEditor?)!!.dismissAllDialogFragments()
            }
        }

        /**
         * Add new template to model, asking user to confirm if it's going to require a full sync
         *
         * @param model model to add new template to
         */
        private fun addNewTemplateWithCheck(model: JSONObject) {
            try {
                mTemplateEditor!!.col.modSchema()
                Timber.d("addNewTemplateWithCheck() called and no CMSE?")
                addNewTemplate(model)
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(R.string.full_sync_confirmation))
                val confirm = Runnable {
                    mTemplateEditor!!.col.modSchemaNoCheck()
                    addNewTemplate(model)
                }
                d.setConfirm(confirm)
                mTemplateEditor!!.showDialogFragment(d)
            }
        }

        /**
         * Add new template to a given model
         * @param model model to add new template to
         */
        private fun addNewTemplate(model: JSONObject) {
            // Build new template
            val oldPosition = requireArguments().getInt("position")
            val templates = model.getJSONArray("tmpls")
            val oldTemplate = templates.getJSONObject(oldPosition)
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
            mTemplateEditor!!.tempModel!!.addNewTemplate(newTemplate)
            mTemplateEditor!!.mViewPager!!.adapter!!.notifyDataSetChanged()
            mTemplateEditor!!.mViewPager!!.setCurrentItem(templates.length() - 1, mTemplateEditor!!.animationDisabled())
        }

        /**
         * Flip the question and answer side of the template
         * @param template template to flip
         */
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
            var name: String
            // Start by trying to set the name to "Card n" where n is the new num of templates
            var n = templates.length() + 1
            // If the starting point for name already exists, iteratively increase n until we find a unique name
            while (true) {
                // Get new name
                name = resources.getString(R.string.card_n_name, n)
                // Cycle through all templates checking if new name exists
                var exists = false
                for (template in templates.jsonObjectIterable()) {
                    if (name == template.getString("name")) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    break
                }
                n += 1
            }
            return name
        }

        companion object {
            fun newInstance(position: Int, noteId: Long, editorPosition: Int, viewId: Int): CardTemplateFragment {
                val f = CardTemplateFragment()
                val args = Bundle()
                args.putInt("position", position)
                args.putLong(EDITOR_NOTE_ID, noteId)
                args.putInt(EDITOR_POSITION_KEY, editorPosition)
                args.putInt(EDITOR_VIEW_ID_KEY, viewId)
                f.arguments = args
                return f
            }
        }
    }

    companion object {
        private const val EDITOR_POSITION_KEY = "editorPosition"
        private const val EDITOR_VIEW_ID_KEY = "editorViewId"
        private const val EDITOR_MODEL_ID = "modelId"
        private const val EDITOR_NOTE_ID = "noteId"
        private const val EDITOR_START_ORD_ID = "ordId"
        @Suppress("unused")
        private const val REQUEST_PREVIEWER = 0
        @Suppress("unused")
        private const val REQUEST_CARD_BROWSER_APPEARANCE = 1
    }
}
