/*
 * Copyright (c) 2015 Ryan Annis <squeenix@live.ca>
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype.fieldeditor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.ItemNotetypeFieldBinding
import com.ichi2.anki.databinding.NoteTypeFieldEditorBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.KEY_SELECTED_LOCALE
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.Companion.newInstance
import com.ichi2.anki.dialogs.NoteTypeFieldEditorContextMenu.NoteTypeFieldEditorContextMenuAction
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.setCompoundDrawablesRelativeWithIntrinsicBoundsKt
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.withProgress
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@NeedsTest("perform one action, then another")
class NoteTypeFieldEditor : AnkiActivity(R.layout.note_type_field_editor) {
    private val binding by viewBinding(NoteTypeFieldEditorBinding::bind)
    val viewModel by viewModels<NoteTypeFieldEditorViewModel>()

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.note_type_field_editor)
        enableToolbar()
        binding.notetypeName.text = intent.getStringExtra(EXTRA_NOTETYPE_NAME)
        startLoadingCollection()
        setFragmentResultListener(REQUEST_HINT_LOCALE_SELECTION) { _, bundle ->
            val selectedLocale =
                BundleCompat.getSerializable(
                    bundle,
                    KEY_SELECTED_LOCALE,
                    Locale::class.java,
                )
            if (selectedLocale != null) {
                addFieldLocaleHint(selectedLocale)
            }
            dismissAllDialogFragments()
        }
    }

    // ----------------------------------------------------------------------------
    // ANKI METHODS
    // ----------------------------------------------------------------------------
    override fun onCollectionLoaded(col: Collection) {
        super.onCollectionLoaded(col)
        initialize()
    }

    // ----------------------------------------------------------------------------
    // UI SETUP
    // ----------------------------------------------------------------------------

    /**
     * Initialize the data holding properties and the UI from the model. This method expects that it
     * isn't followed by other type of work that access the data properties as it has the capability
     * to finish the activity.
     */
    private fun initialize() {
        val noteTypeID = intent.getLongExtra(EXTRA_NOTETYPE_ID, 0)
        val collectionModel = getColUnsafe.notetypes.get(noteTypeID)
        if (collectionModel == null) {
            showThemedToast(this, R.string.field_editor_model_not_available, true)
            finish()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect {
                    binding.fields.adapter =
                        NoteFieldAdapter(this@NoteTypeFieldEditor, fieldNamesWithKind())
                }
            }
        }
        binding.fields.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position: Int, _ ->
                showDialogFragment(newInstance(viewModel.state.value.fieldsLabels[position]))
                viewModel.updateCurrentPosition(position)
            }
        binding.btnAdd.setOnClickListener { addFieldDialog() }
    }
    // ----------------------------------------------------------------------------
    // CONTEXT MENU DIALOGUES
    // ----------------------------------------------------------------------------

    /**
     * Clean the input field or explain why it's rejected
     * @param name the input
     * @return The value to use, or null in case of failure
     */
    private fun uniqueName(name: String): String? {
        var input =
            name
                .replace("[\\n\\r{}:\"]".toRegex(), "")
        // The number of #, ^, /, space, tab, starting the input
        var offset = 0
        while (offset < input.length) {
            if (!listOf('#', '^', '/', ' ', '\t').contains(input[offset])) {
                break
            }
            offset++
        }
        input = input.substring(offset).trim()
        if (input.isEmpty()) {
            showThemedToast(this, resources.getString(R.string.toast_empty_name), true)
            return null
        }
        val fieldsLabels = viewModel.state.value.fieldsLabels
        if (fieldsLabels.any { input == it }) {
            showThemedToast(this, resources.getString(R.string.toast_duplicate_field), true)
            return null
        }
        return input
    }

    /*
     * Creates a dialog to create a field
     */
    private fun addFieldDialog() {
        val addFieldDialog = AddNewNoteTypeField(this)
        addFieldDialog.showAddNewNoteTypeFieldDialog { name ->
            launchCatchingTask {
                val validName = uniqueName(name) ?: return@launchCatchingTask
                val isConfirmed = userAcceptsSchemaChange()
                if (!isConfirmed) return@launchCatchingTask
                viewModel.add(validName)
            }
        }
    }

    /*
     * Creates a dialog to delete the currently selected field
     */
    private fun deleteFieldDialog() {
        if (viewModel.state.value.fieldsLabels.size < 2) {
            showThemedToast(
                this,
                resources.getString(R.string.toast_last_field),
                true,
            )
            return
        }

        val fieldName =
            viewModel.state.value.noteFields[viewModel.state.value.currentPos]
                .name
        ConfirmationDialog().let {
            it.setArgs(
                title = fieldName,
                message = resources.getString(R.string.field_delete_warning),
            )
            it.setConfirm {
                launchCatchingTask {
                    val isConfirmed = userAcceptsSchemaChange()
                    if (!isConfirmed) return@launchCatchingTask

                    val field = viewModel.state.value.noteFields[viewModel.state.value.currentPos]
                    viewModel.delete(field)

                    // This ensures that the context menu closes after the field has been deleted
                    supportFragmentManager.popBackStackImmediate(
                        null,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE,
                    )
                }
            }
            showDialogFragment(it)
        }
    }

    /*
     * Creates a dialog to rename the currently selected field
     * Processing time is constant
     */
    private fun renameFieldDialog() {
        val field = viewModel.state.value.noteFields[viewModel.state.value.currentPos]
        val renameFieldDialog = RenameNoteTypeField(this@NoteTypeFieldEditor, field.name)
        renameFieldDialog.showRenameNoteTypeFieldDialog { name ->
            val field = viewModel.state.value.noteFields[viewModel.state.value.currentPos]
            launchCatchingTask {
                val validName = uniqueName(name) ?: return@launchCatchingTask
                val isConfirmed = userAcceptsSchemaChange()
                if (!isConfirmed) return@launchCatchingTask
                viewModel.rename(field, validName)
            }
        }
    }

    /**
     * Displays a dialog to allow the user to reposition a field within a list.
     */
    private fun repositionFieldDialog() {
        val repositionFieldDialog = RepositionTypeField(this@NoteTypeFieldEditor, viewModel.state.value.fieldsLabels.size)
        repositionFieldDialog.showRepositionNoteTypeFieldDialog { position ->
            launchCatchingTask {
                val isConfirmed = userAcceptsSchemaChange()
                if (!isConfirmed) return@launchCatchingTask
                Timber.i("Repositioning field from %d to %d", viewModel.state.value.currentPos, position)
                val field = viewModel.state.value.noteFields[viewModel.state.value.currentPos]
                viewModel.reposition(field, position)
            }
        }
    }

    /*
     * Changes the sort field (that displays in card browser) to the current field
     */
    private fun sortByField() {
        launchCatchingTask {
            val isConfirmed = userAcceptsSchemaChange()
            if (!isConfirmed) return@launchCatchingTask
            viewModel.changeSort(viewModel.state.value.currentPos)
        }
    }

    fun handleAction(contextMenuAction: NoteTypeFieldEditorContextMenuAction) {
        when (contextMenuAction) {
            NoteTypeFieldEditorContextMenuAction.Sort -> sortByField()
            NoteTypeFieldEditorContextMenuAction.Reposition -> repositionFieldDialog()
            NoteTypeFieldEditorContextMenuAction.Delete -> deleteFieldDialog()
            NoteTypeFieldEditorContextMenuAction.Rename -> renameFieldDialog()
            NoteTypeFieldEditorContextMenuAction.AddLanguageHint -> localeHintDialog()
        }
    }

    private fun localeHintDialog() {
        Timber.i("displaying locale hint dialog")
        // We don't currently show the current value, but we may want to in the future
        showDialogFragment(LocaleSelectionDialog())
    }

    /*
     * Sets the Locale Hint of the field to the provided value.
     * This allows some keyboard (GBoard) to change language
     */
    private fun addFieldLocaleHint(selectedLocale: Locale) {
        launchCatchingTask {
            withProgress(message = getString(R.string.model_field_editor_changing)) {
                viewModel.languageHint(selectedLocale)
            }
        }
        val format = getString(R.string.model_field_editor_language_hint_dialog_success_result, selectedLocale.displayName)
        showSnackbar(format, Snackbar.LENGTH_SHORT)
        initialize()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun addField(name: String) {
        val fieldName = uniqueName(name) ?: return
        launchCatchingTask {
            viewModel.add(fieldName)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @Throws(ConfirmModSchemaException::class)
    fun renameField(name: String) {
        val fieldLabel = uniqueName(name) ?: return
        val field = viewModel.state.value.noteFields[viewModel.state.value.currentPos]
        launchCatchingTask {
            viewModel.rename(field, fieldLabel)
        }
    }

    /*
     * Returns a list of field names with their kind
     * So far the only kind is SORT, which defines the field upon which notes could be sorted
     */
    private fun fieldNamesWithKind(): List<Pair<String, NotetypeKind>> =
        viewModel.state.value.fieldsLabels.mapIndexed { index, fieldName ->
            Pair(
                fieldName,
                if (index == viewModel.state.value.notetype.sortf) NotetypeKind.SORT else NotetypeKind.UNDEFINED,
            )
        }

    companion object {
        const val EXTRA_NOTETYPE_NAME = "extra_notetype_name"
        const val EXTRA_NOTETYPE_ID = "extra_notetype_id"
    }
}

enum class NotetypeKind {
    SORT,
    UNDEFINED,
}

internal class NoteFieldAdapter(
    private val context: Context,
    labels: List<Pair<String, NotetypeKind>>,
) : ArrayAdapter<Pair<String, NotetypeKind>>(context, 0, labels) {
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val binding =
            if (convertView != null) {
                ItemNotetypeFieldBinding.bind(convertView)
            } else {
                ItemNotetypeFieldBinding.inflate(LayoutInflater.from(context), parent, false)
            }

        getItem(position)?.let {
            val (name, kind) = it
            binding.fieldName.text = name
            binding.fieldName.setCompoundDrawablesRelativeWithIntrinsicBoundsKt(
                end =
                    when (kind) {
                        NotetypeKind.SORT -> R.drawable.ic_sort
                        NotetypeKind.UNDEFINED -> 0
                    },
            )
        }
        return binding.root
    }
}
