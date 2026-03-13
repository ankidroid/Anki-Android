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
import androidx.recyclerview.widget.RecyclerView
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
import com.ichi2.anki.servicelayer.LanguageHint
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    isEditing: Boolean,
                ) {
                    viewModel.rename(position, name, isEditing)
                }

                override fun onSortChanged(position: Int) {
                    viewModel.changeSort(position)
                }

                override fun onLocaleChangeRequested(
                    position: Int,
                    languageHint: LanguageHint?,
                ) {
                    localeHintDialog(languageHint, position)
                }

                override fun onRepositionRequested(viewHolder: NoteFieldAdapter.NoteFieldViewHolder) {
                    touchHelper.startDrag(viewHolder)
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

                override fun isLongPressDragEnabled() = false

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition
                    if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) return false
                    adapter.submitListMove(fromPosition, toPosition)
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {
                    val position = viewHolder.bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        deleteFieldDialog(position, adapter.getItem(position).name)
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
                        // when viewHolder is moved with focus on edittext, swipe doesn't work correctly
                        currentFocus?.clearFocus()
                        hideKeyboard()
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
                        viewModel.reposition(dragFromPosition, dragToPosition)
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
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@NoteTypeFieldEditor)
            adapter = this@NoteTypeFieldEditor.adapter
            touchHelper.attachToRecyclerView(this@apply)
        }
        binding.btnAdd.setOnClickListener { addFieldDialog() }
        onBackPressedDispatcher.addCallback(this) {
            viewModel.requestDiscardChangesAndClose()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    withContext(Dispatchers.Main) {
                        Timber.d("state: $state")
                        adapter.submitList(state.fields)
                        consumeAction(state.action)
                    }
                    viewModel.resetAction()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.notetype_field_editor, menu)
        menu.findItem(R.id.action_save).setOnMenuItemClickListener {
            viewModel.requestSaveAndClose()
            true
        }
        return true
    }

    fun consumeAction(action: NoteTypeFieldEditorState.Action) {
        when (action) {
            is NoteTypeFieldEditorState.Action.Undoable -> {
                val message = getString(action.resId, *action.formatArgs.toTypedArray())

                showUndoSnackbar(message)
            }
            is NoteTypeFieldEditorState.Action.Error ->
                CrashReportService.sendExceptionReport(
                    action.e.source,
                    NoteTypeFieldEditor::class.java.simpleName,
                )
            is NoteTypeFieldEditorState.Action.Rejected ->
                showThemedToast(
                    this@NoteTypeFieldEditor,
                    getString(action.resId),
                    true,
                )
            is NoteTypeFieldEditorState.Action.SaveRequested ->
                showSaveChangesDialog(action.isNotUndoable, action.isSchemaChanges)
            NoteTypeFieldEditorState.Action.DiscardRequested ->
                showDiscardChangesDialog()
            is NoteTypeFieldEditorState.Action.Close -> {
                if (action.resId != null) {
                    showThemedToast(
                        this@NoteTypeFieldEditor,
                        getString(action.resId),
                        true,
                    )
                }
                finish()
            }
            NoteTypeFieldEditorState.Action.None -> { }
        }
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
                viewModel.undo()
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
    private fun deleteFieldDialog(
        position: Int,
        fieldName: String,
    ) {
        ConfirmationDialog().let {
            it.setArgs(
                title = fieldName,
                message = resources.getString(R.string.field_delete_warning),
            )
            it.setConfirm {
                viewModel.delete(position)
                // This ensures that the context menu closes after the field has been deleted
                supportFragmentManager.popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE,
                )
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
) : RecyclerView.Adapter<NoteFieldAdapter.NoteFieldViewHolder>() {
    val items = mutableListOf<NoteTypeFieldRowData>()

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

    override fun onBindViewHolder(
        holder: NoteFieldViewHolder,
        position: Int,
        payloads: List<Any?>,
    ) {
        val changes = payloads.filterIsInstance<NoteTypeFieldDiffUtil.Payload>().toSet()
        if (changes.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.bind(getItem(position), changes)
        }
    }

    fun getItem(position: Int) = items[position]

    override fun getItemCount() = items.size

    override fun onViewRecycled(holder: NoteFieldViewHolder) {
        holder.recycled()
    }

    suspend fun submitList(list: List<NoteTypeFieldRowData>) =
        withContext(Dispatchers.Main) {
            Timber.d("submitList: $items $list")
            val diffResult =
                withContext(Dispatchers.Default) {
                    val diffUtil = NoteTypeFieldDiffUtil(items.toList(), list)
                    val result = DiffUtil.calculateDiff(diffUtil)
                    items.clear()
                    items.addAll(list)
                    return@withContext result
                }
            diffResult.dispatchUpdatesTo(this@NoteFieldAdapter)
        }

    fun submitListMove(
        oldPosition: Int,
        newPosition: Int,
    ) {
        val field = items.removeAt(oldPosition)
        items.add(newPosition, field)
        notifyItemMoved(oldPosition, newPosition)
    }

    class NoteTypeFieldDiffUtil(
        private val oldList: List<NoteTypeFieldRowData>,
        private val newList: List<NoteTypeFieldRowData>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].uuid == newList[newItemPosition].uuid || oldList[oldItemPosition].name == newList[newItemPosition].name

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition] == newList[newItemPosition]

        override fun getChangePayload(
            oldItemPosition: Int,
            newItemPosition: Int,
        ): Any? {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            Timber.d("payload: $oldItemPosition, $newItemPosition")
            Timber.d("payload: $oldItem, $newItem")
            return when {
                oldItem.name != newItem.name -> Payload.Rename
                oldItem.isOrder != newItem.isOrder -> Payload.Sort
                oldItem.locale != newItem.locale -> Payload.Locale
                else -> super.getChangePayload(oldItemPosition, newItemPosition)
            }
        }

        enum class Payload {
            Rename,
            Sort,
            Locale,
            ;

            companion object {
                val entriesSet = entries.toSet()
            }
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
                fieldDragHandle.setOnLongClickListener { _ ->
                    listener.onRepositionRequested(this@NoteFieldViewHolder)
                    return@setOnLongClickListener true
                }
                fieldSortButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        clozeImeAndClearFocus()
                        listener.onSortChanged(position)
                    }
                }
                fieldLanguageButton.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        clozeImeAndClearFocus()
                        val locale = getItem(position).locale
                        listener.onLocaleChangeRequested(position, locale)
                    }
                }
                fieldEdit.apply {
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) return@setOnFocusChangeListener
                        save()
                        clozeImeAndClearFocus()
                    }
                    setOnEditorActionListener { _, actionId, _ ->
                        return@setOnEditorActionListener when (actionId) {
                            EditorInfo.IME_ACTION_DONE -> {
                                clearFocus()
                                true
                            }
                            else -> false
                        }
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
                            }

                            override fun afterTextChanged(s: Editable?) {
                                save()
                            }
                        },
                    )
                }
            }
        }

        fun bind(
            item: NoteTypeFieldRowData,
            payload: Set<NoteTypeFieldDiffUtil.Payload> = NoteTypeFieldDiffUtil.Payload.entriesSet,
        ) {
            Timber.d("bind: $item at $bindingAdapterPosition ")
            binding.apply {
                if (payload.contains(NoteTypeFieldDiffUtil.Payload.Rename) && item.name != fieldEdit.text?.toString().orEmpty()) {
                    Timber.d("field edittext: ${fieldEdit.text} to ${item.name} at $bindingAdapterPosition")
                    setText(item.name)
                }
                if (payload.contains(NoteTypeFieldDiffUtil.Payload.Sort)) {
                    fieldSortButton.isChecked = item.isOrder
                }
                if (payload.contains(NoteTypeFieldDiffUtil.Payload.Locale)) {
                    fieldLanguageButton.isChecked = item.locale != null
                }
            }
        }

        fun recycled() {
            binding.root.translationX = 0f
        }

        fun setText(name: String) {
            binding.fieldEdit.apply {
                setText(name)
                if (hasFocus()) {
                    moveCursorToEnd()
                }
            }
        }

        fun save() {
            val position = bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return
            val newName =
                binding.fieldEdit.text
                    ?.toString()
                    .orEmpty()
            listener.onNameChanged(position, newName, binding.fieldEdit.hasFocus())
        }

        fun clozeImeAndClearFocus() {
            binding.fieldEdit.hideKeyboard()
            if (binding.fieldEdit.hasFocus()) {
                binding.root.requestFocus()
            }
        }
    }

    interface ItemChangeListener {
        fun onNameChanged(
            position: Int,
            name: String,
            isEditing: Boolean,
        )

        fun onSortChanged(position: Int)

        fun onLocaleChangeRequested(
            position: Int,
            languageHint: LanguageHint?,
        )

        fun onRepositionRequested(viewHolder: NoteFieldViewHolder)
    }
}
