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

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.ModelFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.userAcceptsSchemaChange
import com.ichi2.anki.withProgress
import com.ichi2.libanki.getNotetypeNames
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

class ManageNotetypes : AnkiActivity() {
    private val viewModel by viewModels<ManageNotetypeViewModel>()
    private lateinit var actionBar: ActionBar
    private lateinit var noteTypesList: RecyclerView

    private val notetypesAdapter: NotetypesAdapter by lazy {
        NotetypesAdapter(
            this@ManageNotetypes,
            onRename = ::renameNotetype,
            onDelete = ::deleteNotetype,
        )
    }
    private val outsideChangesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                viewModel.refresh()
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
        noteTypesList =
            findViewById<RecyclerView?>(R.id.note_types_list).apply {
                adapter = notetypesAdapter
            }
        findViewById<FloatingActionButton>(R.id.note_type_add).setOnClickListener {
            val addNewNotesType = AddNewNotesType(this, viewModel)
            launchCatchingTask { addNewNotesType.showAddNewNotetypeDialog() }
        }
        viewModel.uiState.launchCollectionInLifecycleScope { state ->
            supportActionBar?.subtitle =
                resources.getQuantityString(
                    R.plurals.model_browser_types_available,
                    state.notetypes.size,
                    state.notetypes.size,
                )
            notetypesAdapter.submitList(state.notetypes)
            handleLoadingStateIfNeeded(state.isProcessing)
            handleNavigationIfNeeded(state.destination)
        }
        viewModel.refresh()
    }

    private fun handleLoadingStateIfNeeded(isProcessing: Boolean) {
        val currentDialog =
            (supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG) as? DialogFragment)
        if (isProcessing) {
            if (currentDialog != null) return
            // DialogFragment.show() is not fast enough in certain situation and we could end up
            // with not "registering" the dialog although it's just about to be shown
            LoadingDialogFragment.newInstance().showNow(
                supportFragmentManager,
                LoadingDialogFragment.TAG,
            )
        } else {
            currentDialog?.dismiss()
        }
    }

    private fun handleNavigationIfNeeded(destination: NotetypesDestination?) {
        if (destination == null) return
        when (destination) {
            is NotetypesDestination.CardTemplateEditor ->
                launchForChanges<CardTemplateEditor>(destination.extras)

            is NotetypesDestination.Fields -> launchForChanges<ModelFieldEditor>(destination.extras)
        }
        viewModel.markNavigationRequestAsDone()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu.findItem(R.id.search_item)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = searchItem?.actionView as? AccessibleSearchView
        searchView?.maxWidth = Integer.MAX_VALUE
        searchView?.setSearchableInfo(searchManager.getSearchableInfo(componentName))

        searchView?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.filter(newText ?: "")
                    return true
                }
            },
        )
        return true
    }

    private fun renameNotetype(notetypeItemUiState: NotetypeItemUiState) {
        launchCatchingTask {
            val allNotetypesNames =
                viewModel.uiState.value.notetypes
                    .map { it.name }
            val dialog =
                AlertDialog
                    .Builder(this@ManageNotetypes)
                    .show {
                        title(R.string.rename_model)
                        positiveButton(R.string.rename) {
                            viewModel.rename(
                                notetypeItemUiState.id,
                                (it as AlertDialog).getInputField().text.toString(),
                            )
                        }
                        negativeButton(R.string.dialog_cancel)
                        setView(R.layout.dialog_generic_text_input)
                    }.input(
                        prefill = notetypeItemUiState.name,
                        waitForPositiveButton = false,
                        displayKeyboard = true,
                        callback = { dialog, text ->
                            dialog.positiveButton.isEnabled =
                                text.isNotEmpty() &&
                                !allNotetypesNames.contains(text.toString())
                        },
                    )
            // start with the button disabled as dialog shows the initial name
            dialog.positiveButton.isEnabled = false
        }
    }

    private fun deleteNotetype(notetypeItemUiState: NotetypeItemUiState) {
        launchCatchingTask {
            val messageResourceId: Int? =
                if (userAcceptsSchemaChange()) {
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
                    viewModel.delete(notetypeItemUiState.id)
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }

    private inline fun <reified T : AnkiActivity> launchForChanges(extras: Map<String, Any>) {
        val targetIntent =
            Intent(this@ManageNotetypes, T::class.java).apply {
                extras.forEach { extra ->
                    when (extra.value) {
                        is String -> putExtra(extra.key, extra.value as String)
                        is Long -> putExtra(extra.key, extra.value as Long)
                        else -> throw IllegalArgumentException("Unexpected value type: ${extra.value}")
                    }
                }
            }
        outsideChangesLauncher.launch(targetIntent)
    }
}
