/****************************************************************************************
 * Copyright (c) 2015 Ryan Annis <squeenix@live.ca>                                     *
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.ModelBrowserContextMenu
import com.ichi2.anki.dialogs.ModelBrowserContextMenuAction
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.getAllModelsAndNotesCount
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.libanki.StdModels
import com.ichi2.libanki.Utils
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.displayKeyboard
import com.ichi2.widget.WidgetStatus.update
import kotlinx.coroutines.Job
import timber.log.Timber
import java.util.*

@KotlinCleanup("Try converting variables to be non-null wherever possible + Standard in-IDE cleanup")
@NeedsTest("add tests to ensure changes(renames & deletions) to the list of note types are visible in the UI")
class ModelBrowser : AnkiActivity() {
    private var modelDisplayAdapter: DisplayPairAdapter? = null
    private var mModelListView: ListView? = null

    // Of the currently selected model
    private var mCurrentID: Long = 0
    private var mModelListPosition = 0

    // Used exclusively to display model name
    private var mModels: ArrayList<Model>? = null
    private var mCardCounts: ArrayList<Int>? = null
    private var mModelIds: ArrayList<Long>? = null
    private var mModelDisplayList: ArrayList<DisplayPair>? = null
    private lateinit var mCol: Collection
    private var mActionBar: ActionBar? = null

    // Dialogue used in renaming
    private var modelNameInput: EditText? = null

    private var loadModelsJob: Job? = null

    // ----------------------------------------------------------------------------
    // AsyncTask methods
    // ----------------------------------------------------------------------------

    /**
     * Handle the actions that can be done on  a note type from the list.
     */
    fun handleAction(contextMenuAction: ModelBrowserContextMenuAction) {
        supportFragmentManager.popBackStackImmediate()
        when (contextMenuAction) {
            ModelBrowserContextMenuAction.Delete -> deleteModelDialog()
            ModelBrowserContextMenuAction.Rename -> renameModelDialog()
            ModelBrowserContextMenuAction.Template -> openTemplateEditor()
        }
    }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    @NeedsTest("Title follows AnkiDroid's language instead of system's")
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setTitle(R.string.model_browser_label)
        setContentView(R.layout.model_browser)
        mModelListView = findViewById(R.id.note_type_browser_list)
        mActionBar = enableToolbar()

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

        startLoadingCollection()
    }

    public override fun onResume() {
        Timber.d("onResume()")
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.model_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_add_new_note_type -> {
                showAddNewNoteTypeDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            update(this)
            saveCollectionInBackground()
        }
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    public override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        mCol = col
        loadModels()
    }

    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------

    /**
     * Schedules a job to load all models and note count associated with each of model
     * displays a progress dialog till the completion of job
     *
     * After completion, initializes mModels and mCardCounts and refreshes UI with new data
     */
    private fun loadModels() {
        loadModelsJob?.cancel() // cancel if any previous task was scheduled, ideally only one job should exist
        loadModelsJob = launchCatchingTask {
            // Pair of list of models and corresponding notesCount
            Timber.d("doInBackgroundLoadModels: Started")
            val allModelsAndNotesCount = withProgress {
                getAllModelsAndNotesCount()
            }
            Timber.d("doInBackgroundLoadModels: Completed, refreshing UI")
            mModels = ArrayList(allModelsAndNotesCount.first)
            mCardCounts = ArrayList(allModelsAndNotesCount.second)
            fillModelList()
        }
    }

    /*
     * Fills the main list view with model names.
     * Handles filling the ArrayLists and attaching
     * ArrayAdapters to main ListView
     */
    private fun fillModelList() {
        // Anonymous class for handling list item clicks
        mModelDisplayList = ArrayList(mModels!!.size)
        mModelIds = ArrayList(mModels!!.size)
        for (i in mModels!!.indices) {
            mModelIds!!.add(mModels!![i].getLong("id"))
            mModelDisplayList!!.add(DisplayPair(mModels!![i].getString("name"), mCardCounts!![i]))
        }
        modelDisplayAdapter = DisplayPairAdapter(this, mModelDisplayList)
        mModelListView!!.adapter = modelDisplayAdapter
        mModelListView!!.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            val noteTypeID = mModelIds!![position]
            mModelListPosition = position
            val noteOpenIntent = Intent(this@ModelBrowser, ModelFieldEditor::class.java)
            noteOpenIntent.putExtra("title", mModelDisplayList!![position].name)
            noteOpenIntent.putExtra("noteTypeID", noteTypeID)
            startActivityForResultWithAnimation(noteOpenIntent, 0, ActivityTransitionAnimation.Direction.START)
        }
        mModelListView!!.onItemLongClickListener = OnItemLongClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
            val cardName = mModelDisplayList!![position].name
            mCurrentID = mModelIds!![position]
            mModelListPosition = position
            showDialogFragment(ModelBrowserContextMenu.newInstance(cardName))
            true
        }
        updateSubtitleText()
    }

    /*
     * Updates the subtitle showing the amount of mModels available
     * ONLY CALL THIS AFTER initializing the main list
     */
    private fun updateSubtitleText() {
        val count = mModelIds!!.size
        mActionBar!!.subtitle = resources.getQuantityString(R.plurals.model_browser_types_available, count, count)
    }

    /**
     *   * Shows a single choice dialog titled “Add note type” with items such as:
     *     * Add: Basic
     *     * Add: Basic (type in the answer)
     *     * ...
     *     * Clone: Vocabulary
     *
     *   * When user selects one of these and presses Ok,
     *     shows a text input dialog with the same title and a suggested name
     *     such as “Basic”, “Vocabulary copy” or “Basic-ce43a”.
     *
     *   * When user Okays the new name, a new model with the provided name is created
     *     based on the previously selected model.
     */
    @SuppressLint("CheckResult") // listItemsSingleChoice() is annotated with @CheckResult ¯\_(ツ)_/¯
    private fun showAddNewNoteTypeDialog() {
        val addTemplate = resources.getString(R.string.model_browser_add_add)
        val cloneTemplate = resources.getString(R.string.model_browser_add_clone)

        open class ModelInfo(val name: String, val label: String)
        class StandardModelInfo(val model: StdModels, name: String, label: String) : ModelInfo(name, label)
        class UserModelInfo(val model: Model, name: String, label: String) : ModelInfo(name, label)

        val infos = sequence {
            StdModels.STD_MODELS.forEach { model ->
                val name = model.defaultName
                val label = String.format(addTemplate, name)
                yield(StandardModelInfo(model, name, label))
            }

            mModels!!.forEach { model ->
                val name = model.getString("name")
                val label = String.format(cloneTemplate, name)
                yield(UserModelInfo(model, name, label))
            }
        }.toList()

        fun ModelInfo.makeSuggestedName(): String {
            val suggestion = when (this) {
                is StandardModelInfo -> name
                else -> "$name ${resources.getString(R.string.model_clone_suffix)}"
            }

            val alreadyExists = infos.any { it is UserModelInfo && it.name == suggestion }

            return if (alreadyExists) randomizeName(suggestion) else suggestion
        }

        // TODO This will happily accept names that already exist in the user model list.
        //   If duplicate names are not undesirable, add a comment stating so.
        fun addNewModel(sourceModelInfo: ModelInfo, newName: String) {
            if (newName.isEmpty()) {
                showToast(resources.getString(R.string.toast_empty_name))
                return
            }

            val newModel = if (sourceModelInfo is StandardModelInfo) {
                sourceModelInfo.model.add(mCol)
            } else {
                sourceModelInfo as UserModelInfo // Kotlin does not yet support sealed local classes
                sourceModelInfo.model.deepClone().apply {
                    put("id", StdModels.BASIC_MODEL.add(mCol).getLong("id"))
                }
            }

            newModel.put("name", newName)
            mCol.models.update(newModel)
            fullRefresh()
        }

        // TODO These dialogs are slightly confusing.
        //   The first one says “Add note type ... Ok”, but it doesn't add anything straight away.
        //   The second one also says “Add note type ... Ok”, and it is not mention the model
        //   that you are cloning. I suggest reworking the strings so this is less confusing.
        MaterialDialog(this).show {
            title(R.string.model_browser_add)
            positiveButton(R.string.dialog_ok)
            listItemsSingleChoice(items = infos.map { it.label }) { _, index, _ ->
                MaterialDialog(this@ModelBrowser).show {
                    title(R.string.model_browser_add)
                    positiveButton(R.string.dialog_ok)
                    input(prefill = infos[index].makeSuggestedName()) { _, text ->
                        addNewModel(infos[index], text.toString())
                    }
                }
            }
        }
    }

    /**
     * Display a dialog to confirm the note type deletion, if the user accepts then proceed with the
     * deletion process.
     */
    private fun deleteModelDialog() {
        if (mModelIds!!.size > 1) {
            val confirmTextId = try {
                mCol.modSchema()
                R.string.model_delete_warning
            } catch (e: ConfirmModSchemaException) {
                e.log()
                R.string.full_sync_confirmation
            }
            showDialogFragment(
                ConfirmationDialog().apply {
                    setArgs(this@ModelBrowser.resources.getString(confirmTextId))
                    setConfirm {
                        mCol.modSchemaNoCheck()
                        deleteModel()
                    }
                }
            )
        } else {
            showToast(getString(R.string.toast_last_model))
        }
    }

    /*
     * Displays a confirmation box asking if you want to rename the note type and then renames it if confirmed
     */
    private fun renameModelDialog() {
        modelNameInput = FixedEditText(this)
        modelNameInput?.let { modelNameEditText ->
            modelNameEditText.isSingleLine = true
            modelNameEditText.setText(mModels!![mModelListPosition].getString("name"))
            modelNameEditText.setSelection(modelNameEditText.text.length)

            MaterialDialog(this).show {
                customView(view = modelNameEditText, scrollable = true)
                title(R.string.rename_model)
                positiveButton(R.string.rename) {
                    val model = mModels!![mModelListPosition]
                    var name = modelNameEditText.text.toString() // Anki desktop doesn't allow double quote characters in deck names
                        .replace("[\"\\n\\r]".toRegex(), "")
                    if (mModels!!.any { it.getString("name") == name }) {
                        name = randomizeName(name)
                    }
                    if (name.isNotEmpty()) {
                        model.put("name", name)
                        mCol.models.update(model)
                        mModels!![mModelListPosition].put("name", name)
                        mModelDisplayList!![mModelListPosition] = DisplayPair(
                            mModels!![mModelListPosition].getString("name"),
                            mCardCounts!![mModelListPosition]
                        )
                        refreshList()
                    } else {
                        showToast(resources.getString(R.string.toast_empty_name))
                    }
                }
                negativeButton(R.string.dialog_cancel)
                displayKeyboard(modelNameEditText)
            }
        }
    }

    /*
     * Opens the Template Editor (Card Editor) to allow
     * the user to edit the current note's templates.
     */
    private fun openTemplateEditor() {
        val intent = Intent(this, CardTemplateEditor::class.java)
        intent.putExtra("modelId", mCurrentID)
        launchActivityForResultWithAnimation(intent, mEditTemplateResultLauncher, ActivityTransitionAnimation.Direction.START)
    }

    // ----------------------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------------------
    /*
     * Updates the ArrayAdapters for the main ListView.
     * ArrayLists must be manually updated.
     */
    private fun refreshList() {
        modelDisplayAdapter!!.notifyDataSetChanged()
        updateSubtitleText()
    }

    /*
     * Reloads everything
     */
    private fun fullRefresh() {
        loadModels()
    }

    /**
     * Deletes the currently selected model and all notes associated with it
     *
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes
     */
    private fun deleteModel() {
        launchCatchingTask {
            withProgress {
                withCol {
                    Timber.d("doInBackGroundDeleteModel")
                    col.models.rem(col.models.get(mCurrentID)!!)
                    col.save()
                }
            }
            refreshList()
        }
        mModels!!.removeAt(mModelListPosition)
        mModelIds!!.removeAt(mModelListPosition)
        mModelDisplayList!!.removeAt(mModelListPosition)
        mCardCounts!!.removeAt(mModelListPosition)
        refreshList()
    }

    /*
     * Takes current timestamp from col and append to the end of new note types to dissuade
     * User from reusing names (which are technically not unique however
     */
    private fun randomizeName(s: String): String {
        return s + "-" + Utils.checksum(TimeManager.time.intTimeMS().toString()).substring(0, 5)
    }

    private fun showToast(text: CharSequence) {
        showThemedToast(this, text, true)
    }

    // ----------------------------------------------------------------------------
    // CUSTOM ADAPTERS
    // ----------------------------------------------------------------------------
    /*
     * Used so that the main ListView is able to display the number of notes using the model
     * along with the name.
     */
    class DisplayPair(val name: String, val count: Int) {
        override fun toString(): String {
            return name
        }
    }

    /*
     * For display in the main list via an ArrayAdapter
     */
    inner class DisplayPairAdapter(
        context: Context,
        items: ArrayList<DisplayPair>?
    ) : ArrayAdapter<DisplayPair>(context, R.layout.model_browser_list_item, R.id.model_list_item_1, items!!) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val _convertView = convertView ?: LayoutInflater.from(context).inflate(R.layout.model_browser_list_item, parent, false)
            val item = getItem(position)
            val tvName = _convertView.findViewById<TextView>(R.id.model_list_item_1)
            val tvHome = _convertView.findViewById<TextView>(R.id.model_list_item_2)
            val count = item!!.count
            tvName.text = item.name
            tvHome.text = resources.getQuantityString(R.plurals.model_browser_of_type, count, count)
            return _convertView
        }
    }

    private val mEditTemplateResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            loadModels()
        }
    }
}
