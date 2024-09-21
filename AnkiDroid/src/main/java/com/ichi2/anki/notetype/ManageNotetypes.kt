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
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ModelFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.userAcceptsSchemaChange
import com.ichi2.anki.withProgress
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.getNotetype
import com.ichi2.libanki.getNotetypeNameIdUseCount
import com.ichi2.libanki.getNotetypeNames
import com.ichi2.libanki.removeNotetype
import com.ichi2.libanki.updateNotetype
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class ManageNotetypes : AnkiActivity() {
    private lateinit var actionBar: ActionBar
    private lateinit var noteTypesList: RecyclerView

    private var currentNotetypes: List<ManageNoteTypeUiModel> = emptyList()

    // Store search query
    private var searchQuery: String = ""

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
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu.findItem(R.id.search_item)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = searchItem?.actionView as? AccessibleSearchView
        searchView?.maxWidth = Integer.MAX_VALUE
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Update the search query
                searchQuery = newText.orEmpty()
                filterNoteTypes(searchQuery)
                return true
            }
        })
        return true
    }

    /**
     * Filters and updates the note types list based on the query
     */
    @NeedsTest("verify note types list still filtered by search query after rename or delete")
    private fun filterNoteTypes(query: String) {
        val filteredList = if (query.isEmpty()) {
            currentNotetypes
        } else {
            currentNotetypes.filter {
                it.name.lowercase().contains(query.lowercase())
            }
        }
        notetypesAdapter.submitList(filteredList)
    }

    @SuppressLint("CheckResult")
    private fun renameNotetype(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
        launchCatchingTask {
            val allNotetypes = mutableListOf<AddNotetypeUiModel>()
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
                            val initialNotetype = getNotetype(manageNoteTypeUiModel.id)
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
                prefill = manageNoteTypeUiModel.name,
                waitForPositiveButton = false,
                displayKeyboard = true,
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

    private fun deleteNotetype(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
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
                        runAndRefreshAfter { removeNotetype(manageNoteTypeUiModel.id) }
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

        filterNoteTypes(searchQuery)
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
