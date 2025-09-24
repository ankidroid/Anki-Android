package com.ichi2.anki.notetype

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import anki.notetypes.copy
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.getNotetype
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.getNotetypeNames
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.libanki.updateNotetype
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.theme.AnkiDroidTheme
import com.ichi2.anki.userAcceptsSchemaChange
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.withProgress
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import net.ankiweb.rsdroid.BackendException

class ManageNotetypes : AnkiActivity() {
    private lateinit var actionBar: ActionBar

    private var currentNotetypes: List<ManageNoteTypeUiModel> = emptyList()

    // Store search query
    private var searchQuery: String = ""

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
        actionBar = enableToolbar()
        setContent {
            AnkiDroidTheme {
                ManageNoteTypesScreen(
                    noteTypes = currentNotetypes,
                    onAddNoteType = {
                        val addNewNotesType = AddNewNotesType(this)
                        launchCatchingTask { addNewNotesType.showAddNewNotetypeDialog() }
                    },
                    onShowFields = {
                        launchForChanges<NoteTypeFieldEditor>(
                            mapOf(
                                "title" to it.name,
                                "noteTypeID" to it.id,
                            ),
                        )
                    },
                    onEditCards = { launchForChanges<CardTemplateEditor>(mapOf("noteTypeId" to it.id)) },
                    onRename = ::renameNotetype,
                    onDelete = ::deleteNotetype,
                )
            }
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

        searchView?.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Update the search query
                    searchQuery = newText.orEmpty()
                    filterNoteTypes(searchQuery)
                    return true
                }
            },
        )
        return true
    }

    /**
     * Filters and updates the note types list based on the query
     */
    @NeedsTest("verify note types list still filtered by search query after rename or delete")
    private fun filterNoteTypes(query: String) {
        currentNotetypes =
            if (query.isEmpty()) {
                currentNotetypes
            } else {
                currentNotetypes.filter {
                    it.name.lowercase().contains(query.lowercase())
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun renameNotetype(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
        launchCatchingTask {
            val allNotetypes = mutableListOf<AddNotetypeUiModel>()
            allNotetypes.addAll(
                withProgress {
                    withCol { getNotetypeNames().map { it.toUiModel() } }
                },
            )
            val dialog =
                AlertDialog
                    .Builder(this@ManageNotetypes)
                    .show {
                        title(R.string.rename_model)
                        positiveButton(R.string.rename) {
                            launchCatchingTask(
                                // TODO: Change to CardTypeException: https://github.com/ankidroid/Anki-Android-Backend/issues/537
                                // Card template 1 in note type 'character' has a problem.
                                // Expected to find a field replacement on the front of the card template.
                                skipCrashReport = { it is BackendException },
                            ) {
                                runAndRefreshAfter {
                                    val initialNotetype = getNotetype(manageNoteTypeUiModel.id)
                                    val renamedNotetype =
                                        initialNotetype.copy {
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
                                text.isNotEmpty() &&
                                !allNotetypes
                                    .map { it.name }
                                    .contains(text.toString())
                        },
                    )
            // start with the button disabled as dialog shows the initial name
            dialog.positiveButton.isEnabled = false
        }
    }

    private fun deleteNotetype(manageNoteTypeUiModel: ManageNoteTypeUiModel) {
        launchCatchingTask {
            @StringRes val messageResourceId: Int? =
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
    suspend fun runAndRefreshAfter(action: com.ichi2.anki.libanki.Collection.() -> Unit = {}) {
        val updatedNotetypes =
            withProgress {
                withCol {
                    action()
                    getNotetypeNameIdUseCount().map { it.toUiModel() }
                }
            }

        currentNotetypes = updatedNotetypes

        filterNoteTypes(searchQuery)
        actionBar.subtitle =
            resources.getQuantityString(
                R.plurals.model_browser_types_available,
                updatedNotetypes.size,
                updatedNotetypes.size,
            )
    }

    private inline fun <reified T : AnkiActivity> launchForChanges(extras: Map<String, Any>) {
        val targetIntent =
            Intent(this@ManageNotetypes, T::class.java).apply {
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

class ManageNoteTypesDestination : Destination {
    override fun toIntent(context: Context) = Intent(context, ManageNotetypes::class.java)
}
