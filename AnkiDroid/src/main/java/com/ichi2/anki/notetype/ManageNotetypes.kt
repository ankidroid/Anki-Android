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
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import anki.notetypes.copy
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.*
import com.ichi2.utils.*

class ManageNotetypes : AnkiActivity() {
    private lateinit var actionBar: ActionBar
    private lateinit var noteTypesList: RecyclerView

    private var currentNotetypes: List<NoteTypeUiModel> = emptyList()

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
            val addNewNotesType = AddNewNotesType(this)
            launchCatchingTask { addNewNotesType.showAddNewNotetypeDialog() }
        }
        launchCatchingTask { runAndRefreshAfter() } // shows the initial note types list
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.locale_dialog_search_bar, menu)

        val searchItem = menu.findItem(R.id.locale_dialog_action_search)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = searchItem?.actionView as? SearchView
        searchView?.maxWidth = Integer.MAX_VALUE
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredList = if (newText.isNullOrEmpty()) {
                    currentNotetypes
                } else {
                    currentNotetypes.filter {
                        it.name.lowercase().contains(newText.lowercase())
                    }
                }
                notetypesAdapter.submitList(filteredList)
                return true
            }
        })
        return true
    }

    @SuppressLint("CheckResult")
    private fun renameNotetype(noteTypeUiModel: NoteTypeUiModel) {
        launchCatchingTask {
            val allNotetypes = mutableListOf<NotetypeBasicUiModel>()
            allNotetypes.addAll(
                withProgress {
                    withCol { getNotetypeNames().map { it.toUiModel() } }
                }
            )
            val dialog = AlertDialog.Builder(this@ManageNotetypes).show {
                title(R.string.rename_model)
                positiveButton(R.string.rename) {
                    launchCatchingTask {
                        runAndRefreshAfter {
                            val initialNotetype = getNotetype(noteTypeUiModel.id)
                            val renamedNotetype = initialNotetype.copy {
                                this.name = (it as AlertDialog).getInputField().text.toString()
                            }
                            updateNotetype(renamedNotetype)
                        }
                    }
                }
                negativeButton(R.string.dialog_cancel)
                setView(R.layout.dialog_generic_text_input)
            }.input(
                prefill = noteTypeUiModel.name,
                waitForPositiveButton = false,
                callback = { dialog, text ->
                    dialog.positiveButton.isEnabled =
                        text.isNotEmpty() && !allNotetypes.map { it.name }
                        .contains(text.toString())
                }
            )
            // start with the button disabled as dialog shows the initial name
            dialog.positiveButton.isEnabled = false
        }
    }

    private fun deleteNotetype(noteTypeUiModel: NoteTypeUiModel) {
        launchCatchingTask {
            val messageResourceId: Int? = if (userAcceptsSchemaChange()) {
                withProgress {
                    withCol {
                        if (getNotetypeNames().size <= 1) {
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
                positiveButton(R.string.dialog_positive_delete) {
                    launchCatchingTask {
                        runAndRefreshAfter { removeNotetype(noteTypeUiModel.id) }
                    }
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }

    /**
     * Run the provided block on the [Collection](also displaying progress) and then refresh the list
     * of note types to show the changes. This method expects to be called from the main thread.
     *
     * @param action the action to run before the notetypes refresh, if not provided simply refresh
     */
    suspend fun runAndRefreshAfter(action: com.ichi2.libanki.Collection.() -> Unit = {}) {
        val updatedNotetypes = withProgress {
            withCol {
                action()
                getNotetypeNameIdUseCount().map { it.toUiModel() }
            }
        }

        currentNotetypes = updatedNotetypes

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
        outsideChangesLauncher.launch(targetIntent)
    }

    private fun Intent.toExtra(newExtra: Map.Entry<String, Any>) {
        when (newExtra.value) {
            is String -> putExtra(newExtra.key, newExtra.value as String)
            is Long -> putExtra(newExtra.key, newExtra.value as Long)
            else -> throw IllegalArgumentException("Unexpected value type: ${newExtra.value}")
        }
    }
}
