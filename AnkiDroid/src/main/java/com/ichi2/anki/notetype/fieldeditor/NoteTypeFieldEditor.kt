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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.databinding.ItemNotetypeFieldBinding
import com.ichi2.anki.databinding.NoteTypeFieldEditorBinding
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.KEY_SELECTED_FIELD_POSITION
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.KEY_SELECTED_LOCALE
import com.ichi2.anki.dialogs.LocaleSelectionDialog.Companion.REQUEST_HINT_LOCALE_SELECTION
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.userAcceptsSchemaChange
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.getIntOrNull
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.utils.hideKeyboard
import com.ichi2.utils.moveCursorToEnd
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@NeedsTest("perform one action, then another")
class NoteTypeFieldEditor : com.ichi2.anki.AnkiActivity(R.layout.note_type_field_editor) {
    private val binding by viewBinding(NoteTypeFieldEditorBinding::bind)
    val viewModel by viewModels<NoteTypeFieldEditorViewModel>()

    private val adapter: NoteFieldAdapter by lazy {
        val listener =
            object : NoteFieldAdapter.ItemChangeListener {
                override fun onNameChanged(
                    position: Int,
                    name: String,
                ) {
                    launchCatchingTask {
                        viewModel.rename(position, name)
                    }
                }

                override fun onSortChanged(position: Int) {
                    launchCatchingTask {
                        viewModel.changeSort(position)
                    }
                }

                override fun onLocaleChangeRequested(position: Int) {
                    val locale =
                        viewModel.state.value.fields[position]
                            .locale
                    localeHintDialog(locale, position)
                }
            }
        return@lazy NoteFieldAdapter(listener)
    }

    val touchHelper by lazy {
        val callback =
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            ) {
                var dragFromPosition: Int = RecyclerView.NO_POSITION

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    viewModel.visuallyReposition(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        deleteFieldDialog(position)
                        // reset transitionX whether the field is deleted or not
                        viewHolder.bindingAdapter?.notifyItemChanged(position)
                    }
                }

                override fun onSelectedChanged(
                    viewHolder: RecyclerView.ViewHolder?,
                    actionState: Int,
                ) {
                    super.onSelectedChanged(viewHolder, actionState)

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        dragFromPosition = viewHolder?.bindingAdapterPosition ?: RecyclerView.NO_POSITION
                    }
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    val dragFromPosition = dragFromPosition
                    val dragToPosition = viewHolder.bindingAdapterPosition

                    if (dragFromPosition != RecyclerView.NO_POSITION && dragToPosition != RecyclerView.NO_POSITION &&
                        dragFromPosition != dragToPosition
                    ) {
                        launchCatchingTask {
                            viewModel.reposition(dragFromPosition, dragToPosition)
                        }
                    }

                    this.dragFromPosition = RecyclerView.NO_POSITION
                }
            }
        return@lazy ItemTouchHelper(callback)
    }

    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.note_type_field_editor)
        setFragmentResultListener(REQUEST_HINT_LOCALE_SELECTION) { _, bundle ->
            val selectedLocale =
                BundleCompat.getSerializable(
                    bundle,
                    KEY_SELECTED_LOCALE,
                    Locale::class.java,
                )
            val fieldPosition = bundle.getIntOrNull(KEY_SELECTED_FIELD_POSITION)
            if (fieldPosition != null) {
                viewModel.setLanguageHint(fieldPosition, selectedLocale)
            }
            dismissAllDialogFragments()
        }

        enableToolbar().apply {
            subtitle = intent.getStringExtra(EXTRA_NOTETYPE_NAME)
        }

        binding.fields.apply {
            layoutManager = LinearLayoutManager(this@NoteTypeFieldEditor)
            adapter = this@NoteTypeFieldEditor.adapter
            touchHelper.attachToRecyclerView(this@apply)
        }
        binding.btnAdd.setOnClickListener { addFieldDialog() }
        onBackPressedDispatcher.addCallback(this) {
            val unsavedChange = adapter.unsavedChange
            if (unsavedChange != null) {
                viewModel.updateByUuid(unsavedChange)
            }
            viewModel.requestDiscardChangesAndClose()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.fields)
                    when (state.action) {
                        is NoteTypeFieldEditorState.Action.Undoable -> {
                            val message = getString(state.action.resId, *state.action.formatArgs.toTypedArray())

                            showUndoSnackbar(message)
                        }
                        is NoteTypeFieldEditorState.Action.Error ->
                            CrashReportService.sendExceptionReport(
                                state.action.e.source,
                                NoteTypeFieldEditor::class.java.simpleName,
                            )
                        is NoteTypeFieldEditorState.Action.Rejected ->
                            showThemedToast(
                                this@NoteTypeFieldEditor,
                                getString(state.action.resId),
                                true,
                            )
                        is NoteTypeFieldEditorState.Action.SaveRequested ->
                            showSaveChangesDialog(state.action.isNotUndoable, state.action.isSchemaChanges)
                        NoteTypeFieldEditorState.Action.DiscardRequested ->
                            showDiscardChangesDialog()
                        is NoteTypeFieldEditorState.Action.Close -> {
                            if (state.action.resId != null) {
                                showThemedToast(
                                    this@NoteTypeFieldEditor,
                                    getString(state.action.resId),
                                    true,
                                )
                            }
                            finish()
                        }
                        NoteTypeFieldEditorState.Action.None -> { }
                    }
                    if (state.action != NoteTypeFieldEditorState.Action.None) {
                        viewModel.resetAction()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.notetype_field_editor, menu)
        menu.findItem(R.id.action_save).setOnMenuItemClickListener {
            val unsavedChange = adapter.unsavedChange
            if (unsavedChange != null) {
                viewModel.updateByUuid(unsavedChange)
            }
            viewModel.requestSaveAndClose()
            true
        }
        return true
    }

    // ----------------------------------------------------------------------------
    // ACTION DIALOGUES
    // ----------------------------------------------------------------------------

    /**
     * shows a snackbar with the recent change label and an undo action
     */
    fun showUndoSnackbar(message: String) {
        showSnackbar(message) {
            setAnchorView(findViewById(R.id.btn_add))
            isAnchorViewLayoutListenerEnabled = true
            setAction(R.string.undo) {
                launchCatchingTask {
                    viewModel.undo()
                }
            }
        }
    }

    /**
     * shows a dialog to discard the changes
     */
    fun showDiscardChangesDialog() {
        DiscardChangesDialog.showDialog(
            this,
            positiveMethod = {
                viewModel.requestDiscardChangesAndClose(true)
            },
        )
    }

    /**
     * shows a dialog to save the changes
     */
    fun showSaveChangesDialog(
        isNotUndoable: Boolean,
        schemaChanges: Boolean,
    ) {
        val save: () -> Unit = {
            launchCatchingTask {
                if (!schemaChanges || userAcceptsSchemaChange()) {
                    viewModel.requestSaveAndClose(true)
                }
            }
        }
        if (isNotUndoable) {
            val confirmationDialog =
                ConfirmationDialog().apply {
                    setArgs(this@NoteTypeFieldEditor.getString(R.string.model_field_editor_save_not_undoable))
                    setConfirm {
                        save()
                    }
                }
            showDialogFragment(confirmationDialog)
        } else {
            save()
        }
    }

    /**
     * Creates a dialog to create a field
     */
    private fun addFieldDialog() {
        AddNewNoteTypeField(this).showAddNewNoteTypeFieldDialog { name ->
            viewModel.add(name = name)
        }
    }

    /**
     * Creates a dialog to delete the field
     * @param position the position of the field
     */
    private fun deleteFieldDialog(position: Int) {
        if (viewModel.state.value.fields.size < 2) {
            showThemedToast(
                this,
                resources.getString(R.string.toast_last_field),
                true,
            )
            return
        }

        val fieldName =
            viewModel.state.value.fields[position]
                .name
        ConfirmationDialog().let {
            it.setArgs(
                title = fieldName,
                message = resources.getString(R.string.field_delete_warning),
            )
            it.setConfirm {
                launchCatchingTask {
                    viewModel.delete(position)
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

    /**
     * Creates a dialog to show the available locale list for the field
     * @param locale the current locale of the field
     * @param position the position of the field
     */
    private fun localeHintDialog(
        locale: Locale?,
        position: Int,
    ) {
        Timber.i("displaying locale hint dialog")
        showDialogFragment(LocaleSelectionDialog.newInstance(position, locale))
    }

    companion object {
        const val EXTRA_NOTETYPE_NAME = "extra_notetype_name"
        const val EXTRA_NOTETYPE_ID = "extra_notetype_id"
    }
}

private class NoteFieldAdapter(
    private val listener: ItemChangeListener,
) : ListAdapter<NoteTypeFieldRowData, NoteFieldAdapter.NoteFieldViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = NoteFieldViewHolder(
        ItemNotetypeFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        listener,
    )

    override fun onBindViewHolder(
        holder: NoteFieldViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    override fun onViewDetachedFromWindow(holder: NoteFieldViewHolder) {
        holder.detached()
    }

    override fun onViewRecycled(holder: NoteFieldViewHolder) {
        holder.recycled()
    }

    private var _unsavedChange: NoteTypeFieldRowData? = null

    val unsavedChange: NoteTypeFieldRowData? get() {
        val change = _unsavedChange
        _unsavedChange = null
        return change
    }

    companion object {
        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<NoteTypeFieldRowData>() {
                override fun areItemsTheSame(
                    oldItem: NoteTypeFieldRowData,
                    newItem: NoteTypeFieldRowData,
                ) = oldItem.uuid == newItem.uuid

                override fun areContentsTheSame(
                    oldItem: NoteTypeFieldRowData,
                    newItem: NoteTypeFieldRowData,
                ) = oldItem == newItem
            }
    }

    inner class NoteFieldViewHolder(
        private val binding: ItemNotetypeFieldBinding,
        listener: ItemChangeListener,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.apply {
                root.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                fieldSortButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onSortChanged(position)
                    }
                }
                fieldLanguageButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onLocaleChangeRequested(position)
                    }
                }
                fieldEdit.apply {
                    setOnFocusChangeListener { v, _ ->
                        moveCursorToEnd()
                        hideKeyboard()
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val name = (v as TextInputEditText).text?.toString()
                            if (!name.isNullOrBlank()) {
                                listener.onNameChanged(position, name)
                                _unsavedChange = null
                            } else {
                                setText((bindingAdapter as NoteFieldAdapter).getItem(bindingAdapterPosition).name)
                            }
                        }
                    }
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            root.requestFocus()
                        }
                        false
                    }
                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                end: Int,
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                end: Int,
                            ) {
                                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                    val unsavedChange =
                                        (bindingAdapter as NoteFieldAdapter)
                                            .getItem(
                                                bindingAdapterPosition,
                                            ).copy(
                                                name = s?.toString().orEmpty(),
                                            )
                                    _unsavedChange = unsavedChange
                                }
                            }

                            override fun afterTextChanged(s: Editable?) { }
                        },
                    )
                }
                fieldEditLayout.setEndIconOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        fieldEdit.setText((bindingAdapter as NoteFieldAdapter).getItem(position).name)
                        fieldEdit.moveCursorToEnd()
                    }
                    fieldEditLayout.isEndIconVisible = false
                }
            }
        }

        fun bind(item: NoteTypeFieldRowData) {
            binding.apply {
                if (fieldEdit.text.toString() != item.name) {
                    fieldEdit.setText(item.name)
                }
                fieldEditLayout.isEndIconVisible = false
                fieldSortButton.isChecked = item.isOrder
                fieldLanguageButton.isChecked = item.locale != null
            }
        }

        fun detached() {
            binding.fieldEdit.clearFocus()
        }

        fun recycled() {
            binding.root.translationX = 0f
        }
    }

    interface ItemChangeListener {
        fun onNameChanged(
            position: Int,
            name: String,
        )

        fun onSortChanged(position: Int)

        fun onLocaleChangeRequested(position: Int)
    }
}
