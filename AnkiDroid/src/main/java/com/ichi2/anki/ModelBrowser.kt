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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemLongClickListener
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.UIUtils.saveCollectionInBackground
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.ModelBrowserContextMenu
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.async.CollectionTask.CountModels
import com.ichi2.async.CollectionTask.DeleteModel
import com.ichi2.async.TaskListenerWithContext
import com.ichi2.async.TaskManager
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Model
import com.ichi2.libanki.StdModels
import com.ichi2.libanki.Utils
import com.ichi2.ui.FixedEditText
import com.ichi2.utils.KotlinCleanup
import com.ichi2.widget.WidgetStatus.update
import timber.log.Timber
import java.lang.RuntimeException
import java.util.ArrayList

@KotlinCleanup("Try converting variables to be non-null wherever possible + Standard in-IDE cleanup")
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
    private var mNewModelLabels: ArrayList<String>? = null
    private var mExistingModelNames: ArrayList<String>? = null
    private var mCol: Collection? = null
    private var mActionBar: ActionBar? = null

    // Dialogue used in renaming
    private var mModelNameInput: EditText? = null
    private var mContextMenu: ModelBrowserContextMenu? = null
    private var mNewModelNames: ArrayList<String>? = null

    // ----------------------------------------------------------------------------
    // AsyncTask methods
    // ----------------------------------------------------------------------------
    /*
     * Displays the loading bar when loading the mModels and displaying them
     * loading bar is necessary because card count per model is not cached *
     */
    private fun loadingModelsHandler(): LoadingModelsHandler {
        return LoadingModelsHandler(this)
    }

    private class LoadingModelsHandler(browser: ModelBrowser) : TaskListenerWithContext<ModelBrowser, Void?, Pair<List<Model?>?, ArrayList<Int>?>?>(browser) {
        override fun actualOnCancelled(context: ModelBrowser) {
            context.hideProgressBar()
        }

        override fun actualOnPreExecute(context: ModelBrowser) {
            context.showProgressBar()
        }

        @KotlinCleanup("Rename context in the base class to activity and see if we can make it non-null")
        override fun actualOnPostExecute(context: ModelBrowser, result: Pair<List<Model?>?, ArrayList<Int>?>?) {
            if (result == null) {
                throw RuntimeException()
            }
            context.let {
                it.hideProgressBar()
                it.mModels = ArrayList(result.first!!)
                it.mCardCounts = result.second
                it.fillModelList()
            }
        }
    }

    /*
     * Displays loading bar when deleting a model loading bar is needed
     * because deleting a model also deletes all of the associated cards/notes *
     */
    private fun deleteModelHandler(): DeleteModelHandler {
        return DeleteModelHandler(this)
    }

    private class DeleteModelHandler(browser: ModelBrowser) : TaskListenerWithContext<ModelBrowser, Void?, Boolean?>(browser) {
        override fun actualOnPreExecute(context: ModelBrowser) {
            context.showProgressBar()
        }

        override fun actualOnPostExecute(context: ModelBrowser, result: Boolean?) {
            if (result == false) {
                throw RuntimeException()
            }
            context.hideProgressBar()
            context.refreshList()
        }
    }

    /*
     * Listens to long hold context menu for main list items
     */
    private val mContextMenuListener = ListCallback { _: MaterialDialog?, _: View?, selection: Int, _: CharSequence? ->
        when (selection) {
            ModelBrowserContextMenu.MODEL_DELETE -> deleteModelDialog()
            ModelBrowserContextMenu.MODEL_RENAME -> renameModelDialog()
            ModelBrowserContextMenu.MODEL_TEMPLATE -> openTemplateEditor()
        }
    }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    public override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.model_browser)
        mModelListView = findViewById(R.id.note_type_browser_list)
        enableToolbar()
        mActionBar = supportActionBar
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

    @KotlinCleanup("Replace with when")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == android.R.id.home) {
            onBackPressed()
            return true
        } else if (itemId == R.id.action_add_new_note_type) {
            addNewNoteTypeDialog()
            return true
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

    public override fun onDestroy() {
        TaskManager.cancelAllTasks(CountModels::class.java)
        super.onDestroy()
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    public override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        mCol = col
        TaskManager.launchCollectionTask(CountModels(), loadingModelsHandler())
    }

    // ----------------------------------------------------------------------------
    // HELPER METHODS
    // ----------------------------------------------------------------------------
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
            mModelDisplayList!!.add(DisplayPair(mModels!![i].getString("name"), mCardCounts!![i].toInt()))
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
            mContextMenu = ModelBrowserContextMenu.newInstance(cardName, mContextMenuListener)
            showDialogFragment(mContextMenu)
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

    /*
     *Creates the dialogue box to select a note type, add a name, and then clone it
     */
    private fun addNewNoteTypeDialog() {
        initializeNoteTypeList()
        val addSelectionSpinner = Spinner(this)
        val newModelAdapter = ArrayAdapter(this, R.layout.dropdown_deck_item, mNewModelLabels!!.toList())
        addSelectionSpinner.adapter = newModelAdapter
        MaterialDialog.Builder(this)
            .title(R.string.model_browser_add)
            .positiveText(R.string.dialog_ok)
            .customView(addSelectionSpinner, true)
            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                mModelNameInput = FixedEditText(this@ModelBrowser)
                mModelNameInput?.let {
                    it.setSingleLine()
                    val isStdModel = addSelectionSpinner.selectedItemPosition < mNewModelLabels!!.size
                    // Try to find a unique model name. Add "clone" if cloning, and random digits if necessary.
                    var suggestedName = mNewModelNames!![addSelectionSpinner.selectedItemPosition]
                    if (!isStdModel) {
                        suggestedName += " " + resources.getString(R.string.model_clone_suffix)
                    }
                    if (mExistingModelNames!!.contains(suggestedName)) {
                        suggestedName = randomizeName(suggestedName)
                    }
                    it.setText(suggestedName)
                    it.setSelection(it.text.length)

                    // Create textbox to name new model
                    MaterialEditTextDialog.Builder(this@ModelBrowser, mModelNameInput)
                        .title(R.string.model_browser_add)
                        .positiveText(R.string.dialog_ok)
                        .onPositive { _: MaterialDialog?, _: DialogAction? ->
                            val modelName = it.text.toString()
                            addNewNoteType(modelName, addSelectionSpinner.selectedItemPosition)
                        }
                        .negativeText(R.string.dialog_cancel)
                        .show()
                }
            }
            .negativeText(R.string.dialog_cancel)
            .show()
    }

    /**
     * Add a new note type
     * @param modelName name of the new model
     * @param position position in dialog the user selected to add / clone the model type from
     */
    @KotlinCleanup("Use scope function while initializing oldModel + Invert and return early")
    private fun addNewNoteType(modelName: String, position: Int) {
        val model: Model
        if (modelName.isNotEmpty()) {
            val nbStdModels = StdModels.STD_MODELS.size
            model = if (position < nbStdModels) {
                StdModels.STD_MODELS[position].add(mCol)
            } else {
                // New model
                // Model that is being cloned
                val oldModel = mModels!![position - nbStdModels].deepClone()
                val newModel = StdModels.BASIC_MODEL.add(mCol)
                oldModel.put("id", newModel.getLong("id"))
                oldModel
            }
            model.put("name", modelName)
            mCol!!.models.update(model)
            fullRefresh()
        } else {
            showToast(resources.getString(R.string.toast_empty_name))
        }
    }

    /*
     * retrieve list of note type in variable, which will going to be in use for adding/cloning note type
     */
    private fun initializeNoteTypeList() {
        val add = resources.getString(R.string.model_browser_add_add)
        val clone = resources.getString(R.string.model_browser_add_clone)

        // Populates array adapters listing the mModels (includes prefixes/suffixes)
        val existingModelSize = mModels!!.size
        val stdModelSize = StdModels.STD_MODELS.size
        mNewModelLabels = ArrayList(existingModelSize + stdModelSize)
        mExistingModelNames = ArrayList(existingModelSize)

        // Used to fetch model names
        mNewModelNames = ArrayList(stdModelSize)
        for (StdModels in StdModels.STD_MODELS) {
            val defaultName = StdModels.defaultName
            mNewModelLabels!!.add(String.format(add, defaultName))
            mNewModelNames!!.add(defaultName)
        }
        for (model in mModels!!) {
            val name = model.getString("name")
            mNewModelLabels!!.add(String.format(clone, name))
            mNewModelNames!!.add(name)
            mExistingModelNames!!.add(name)
        }
    }

    /*
     * Displays a confirmation box asking if you want to delete the note type and then deletes it if confirmed
     */
    @KotlinCleanup("Rename d and c variables")
    private fun deleteModelDialog() {
        if (mModelIds!!.size > 1) {
            val confirm = Runnable {
                mCol!!.modSchemaNoCheck()
                deleteModel()
                dismissContextMenu()
            }
            val cancel = Runnable { dismissContextMenu() }
            try {
                mCol!!.modSchema()
                val d = ConfirmationDialog()
                d.setArgs(resources.getString(R.string.model_delete_warning))
                d.setConfirm(confirm)
                d.setCancel(cancel)
                this@ModelBrowser.showDialogFragment(d)
            } catch (e: ConfirmModSchemaException) {
                e.log()
                val c = ConfirmationDialog()
                c.setArgs(resources.getString(R.string.full_sync_confirmation))
                c.setConfirm(confirm)
                c.setCancel(cancel)
                showDialogFragment(c)
            }
        } else {
            showToast(getString(R.string.toast_last_model))
        }
    }

    /*
     * Displays a confirmation box asking if you want to rename the note type and then renames it if confirmed
     */
    private fun renameModelDialog() {
        initializeNoteTypeList()
        mModelNameInput = FixedEditText(this)
        mModelNameInput?.let {
            it.isSingleLine = true
            it.setText(mModels!![mModelListPosition].getString("name"))
            it.setSelection(it.text.length)
            MaterialEditTextDialog.Builder(this, mModelNameInput)
                .title(R.string.rename_model)
                .positiveText(R.string.rename)
                .negativeText(R.string.dialog_cancel)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    val model = mModels!![mModelListPosition]
                    var deckName = it.text.toString() // Anki desktop doesn't allow double quote characters in deck names
                        .replace("[\"\\n\\r]".toRegex(), "")
                    if (mExistingModelNames!!.contains(deckName)) {
                        deckName = randomizeName(deckName)
                    }
                    if (deckName.isNotEmpty()) {
                        model.put("name", deckName)
                        mCol!!.models.update(model)
                        mModels!![mModelListPosition].put("name", deckName)
                        mModelDisplayList!![mModelListPosition] = DisplayPair(
                            mModels!![mModelListPosition].getString("name"),
                            mCardCounts!![mModelListPosition].toInt()
                        )
                        refreshList()
                    } else {
                        showToast(resources.getString(R.string.toast_empty_name))
                    }
                }
                .show()
        }
    }

    private fun dismissContextMenu() {
        if (mContextMenu != null) {
            mContextMenu!!.dismiss()
            mContextMenu = null
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
        TaskManager.launchCollectionTask(CountModels(), loadingModelsHandler())
    }

    /*
     * Deletes the currently selected model
     */
    private fun deleteModel() {
        TaskManager.launchCollectionTask(DeleteModel(mCurrentID), deleteModelHandler())
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
        return s + "-" + Utils.checksum(col.time.intTimeMS().toString()).substring(0, 5)
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
    inner class DisplayPairAdapter(context: Context?, items: ArrayList<DisplayPair>?) : ArrayAdapter<DisplayPair?>(context!!, R.layout.model_browser_list_item, R.id.model_list_item_1, items!!.toList()) {
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
            TaskManager.launchCollectionTask(CountModels(), loadingModelsHandler())
        }
    }
}
