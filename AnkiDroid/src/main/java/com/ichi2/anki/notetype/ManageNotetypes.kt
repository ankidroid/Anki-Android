/****************************************************************************************
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>             *
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
package com.ichi2.anki.notetype

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView
import anki.notetypes.StockNotetype
import anki.notetypes.copy
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.*
import com.ichi2.libanki.backend.BackendUtils.from_json_bytes
import com.ichi2.libanki.backend.BackendUtils.to_json_bytes
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.libanki.utils.set
import com.ichi2.utils.*

// TODO when the new backend becomes the default delete the old implementation ModelBrowser and its
//  related classes
class ManageNotetypes : AnkiActivity() {
    private lateinit var actionBar: ActionBar
    private lateinit var noteTypesList: RecyclerView
    private val notetypesAdapter: NotetypesAdapter by lazy {
        NotetypesAdapter(
            this@ManageNotetypes,
            onShowFields = {
                launchForChanges<ModelFieldEditor>(
                    mapOf(
                        "title" to it.name,
                        "noteTypeID" to it.id
                    )
                )
            },
            onEditCards = { launchForChanges<CardTemplateEditor>(mapOf("modelId" to it.id)) },
            onRename = ::renameNotetype,
            onDelete = ::deleteNotetype
        )
    }
    private val outsideChangesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                launchCatchingTask { runAndRefreshAfter() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setTitle(R.string.model_browser_label)
        setContentView(R.layout.activity_manage_note_types)
        actionBar = enableToolbar()
        noteTypesList = findViewById<RecyclerView?>(R.id.note_types_list).apply {
            adapter = notetypesAdapter
        }
        findViewById<FloatingActionButton>(R.id.note_type_add).setOnClickListener {
            launchCatchingTask { addNewNotetype() }
        }
        launchCatchingTask { runAndRefreshAfter() } // shows the initial note types list
    }

    @SuppressLint("CheckResult")
    private fun renameNotetype(noteTypeUiModel: NoteTypeUiModel) {
        launchCatchingTask {
            val allNotetypes = mutableListOf<NotetypeBasicUiModel>()
            allNotetypes.addAll(
                withProgress {
                    withCol { newBackend.getNotetypeNames().map { it.toUiModel() } }
                }
            )
            val dialog = MaterialDialog(this@ManageNotetypes).show {
                title(R.string.rename_model)
                input(
                    prefill = noteTypeUiModel.name,
                    waitForPositiveButton = false,
                    callback = { dialog, text ->
                        dialog.getActionButton(WhichButton.POSITIVE).isEnabled =
                            text.isNotEmpty() && !allNotetypes.map { it.name }
                            .contains(text.toString())
                    }
                )
                positiveButton(R.string.rename) {
                    launchCatchingTask {
                        runAndRefreshAfter {
                            val initialNotetype = newBackend.getNotetype(noteTypeUiModel.id)
                            val renamedNotetype = initialNotetype.copy {
                                this.name = it.getInputField().text.toString()
                            }
                            newBackend.updateNotetype(renamedNotetype)
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
            // start with the button disabled as dialog shows the initial name
            dialog.getActionButton(WhichButton.POSITIVE).isEnabled = false
        }
    }

    private fun deleteNotetype(noteTypeUiModel: NoteTypeUiModel) {
        launchCatchingTask {
            val messageResourceId: Int? = if (userAcceptsSchemaChange()) {
                withProgress {
                    withCol {
                        if (newBackend.getNotetypeNames().size <= 1) {
                            return@withCol null
                        }
                        R.string.model_delete_warning
                    }
                }
            } else {
                return@launchCatchingTask
            }
            if (messageResourceId == null) {
                showSnackbar(getString(R.string.toast_last_model))
                return@launchCatchingTask
            }
            AlertDialog.Builder(this@ManageNotetypes).show {
                title(R.string.model_browser_delete)
                message(messageResourceId)
                positiveButton(R.string.dialog_ok) {
                    launchCatchingTask {
                        runAndRefreshAfter { newBackend.removeNotetype(noteTypeUiModel.id) }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }

    private suspend fun addNewNotetype() {
        val optionsToDisplay = withProgress {
            withCol {
                val standardNotetypesModels = StockNotetype.Kind.values()
                    .filter { it != StockNotetype.Kind.UNRECOGNIZED }
                    .map {
                        val stockNotetype = from_json_bytes(newBackend.getStockNotetypeLegacy(it))
                        NotetypeBasicUiModel(
                            id = it.number.toLong(),
                            name = stockNotetype.get("name") as String,
                            isStandard = true
                        )
                    }
                mutableListOf<NotetypeBasicUiModel>().apply {
                    addAll(standardNotetypesModels)
                    addAll(newBackend.getNotetypeNames().map { it.toUiModel() })
                }
            }
        }
        val dialog = MaterialDialog(this).show {
            customView(R.layout.dialog_new_note_type, horizontalPadding = true)
            positiveButton(R.string.dialog_ok) { dialog ->
                val newName =
                    dialog.view.findViewById<EditText>(R.id.notetype_new_name).text.toString()
                val selectedPosition =
                    dialog.view.findViewById<Spinner>(R.id.notetype_new_type).selectedItemPosition
                if (selectedPosition == AdapterView.INVALID_POSITION) return@positiveButton
                val selectedOption = optionsToDisplay[selectedPosition]
                if (selectedOption.isStandard) {
                    addStandardNotetype(newName, selectedOption)
                } else {
                    cloneStandardNotetype(newName, selectedOption)
                }
            }
            negativeButton(R.string.dialog_cancel)
        }
        dialog.initializeViewsWith(optionsToDisplay)
    }

    private fun MaterialDialog.initializeViewsWith(optionsToDisplay: List<NotetypeBasicUiModel>) {
        val addPrefixStr = resources.getString(R.string.model_browser_add_add)
        val clonePrefixStr = resources.getString(R.string.model_browser_add_clone)
        val nameInput = view.findViewById<EditText>(R.id.notetype_new_name)
        nameInput.addTextChangedListener { editableText ->
            val currentName = editableText?.toString() ?: ""
            getActionButton(WhichButton.POSITIVE).isEnabled =
                currentName.isNotEmpty() && !optionsToDisplay.map { it.name }.contains(currentName)
        }
        view.findViewById<Spinner>(R.id.notetype_new_type).apply {
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(av: AdapterView<*>?, rv: View?, index: Int, id: Long) {
                    val selectedNotetype = optionsToDisplay[index]
                    nameInput.setText(randomizeName(selectedNotetype.name))
                }

                override fun onNothingSelected(widget: AdapterView<*>?) {
                    nameInput.setText("")
                }
            }
            adapter = ArrayAdapter(
                this@ManageNotetypes,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                optionsToDisplay.map {
                    String.format(
                        if (it.isStandard) addPrefixStr else clonePrefixStr,
                        it.name
                    )
                }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
    }

    private fun addStandardNotetype(newName: String, selectedOption: NotetypeBasicUiModel) {
        launchCatchingTask {
            runAndRefreshAfter {
                val kind = StockNotetype.Kind.forNumber(selectedOption.id.toInt())
                val updatedStandardNotetype =
                    from_json_bytes(newBackend.getStockNotetypeLegacy(kind)).apply {
                        set("name", newName)
                    }
                newBackend.addNotetypeLegacy(to_json_bytes(updatedStandardNotetype))
            }
        }
    }

    private fun cloneStandardNotetype(newName: String, model: NotetypeBasicUiModel) {
        launchCatchingTask {
            runAndRefreshAfter {
                val targetNotetype = newBackend.getNotetype(model.id)
                val newNotetype = targetNotetype.copy {
                    id = 0
                    name = newName
                }
                newBackend.addNotetype(newNotetype)
            }
        }
    }

    /**
     * Run the provided block on the [Collection](also displaying progress) and then refresh the list
     * of note types to show the changes. This method expects to be called from the main thread.
     *
     * @param action the action to run before the notetypes refresh, if not provided simply refresh
     */
    private suspend fun runAndRefreshAfter(action: com.ichi2.libanki.Collection.() -> Unit = {}) {
        val updatedNotetypes = withProgress {
            withCol {
                action()
                newBackend.getNotetypeNameIdUseCount().map { it.toUiModel() }
            }
        }
        notetypesAdapter.submitList(updatedNotetypes)
        actionBar.subtitle = resources.getQuantityString(
            R.plurals.model_browser_types_available,
            updatedNotetypes.size,
            updatedNotetypes.size
        )
    }

    private inline fun <reified T : AnkiActivity> launchForChanges(extras: Map<String, Any>) {
        val targetIntent = Intent(this@ManageNotetypes, T::class.java).apply {
            extras.forEach { toExtra(it) }
        }
        launchActivityForResultWithAnimation(
            targetIntent,
            outsideChangesLauncher,
            ActivityTransitionAnimation.Direction.START
        )
    }

    private fun Intent.toExtra(newExtra: Map.Entry<String, Any>) {
        when (newExtra.value) {
            is String -> putExtra(newExtra.key, newExtra.value as String)
            is Long -> putExtra(newExtra.key, newExtra.value as Long)
            else -> throw IllegalArgumentException("Unexpected value type: ${newExtra.value}")
        }
    }

    /**
     * Takes the current timestamp from [Collection] and appends it to the end of the new note
     * type to dissuade the user from reusing names(which are technically not unique however).
     */
    private fun randomizeName(currentName: String): String {
        return "$currentName-${Utils.checksum(time.intTimeMS().toString()).substring(0, 5)}"
    }
}
