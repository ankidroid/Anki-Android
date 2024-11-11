//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.ModelFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.create
import timber.log.Timber

/**
 * Note: the class is declared as open only to support testing.
 */
open class ModelEditorContextMenu : AnalyticsDialogFragment() {

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val availableItems = ModelEditorContextMenuAction.entries.sortedBy { it.order }

        return AlertDialog.Builder(requireActivity()).create {
            setTitle(requireArguments().getString(KEY_LABEL))
            setItems(availableItems.map { resources.getString(it.actionTextId) }.toTypedArray()) { _, index ->
                (activity as? ModelFieldEditor)?.run { handleAction(availableItems[index]) }
                    ?: Timber.e("ContextMenu used from outside of its target activity!")
            }
        }
    }

    enum class ModelEditorContextMenuAction(val order: Int, @StringRes val actionTextId: Int) {
        Reposition(0, R.string.model_field_editor_reposition_menu),
        Sort(1, R.string.model_field_editor_sort_field),
        Rename(2, R.string.model_field_editor_rename),
        Delete(3, R.string.model_field_editor_delete),
        ToggleSticky(4, R.string.model_field_editor_toggle_sticky),
        AddLanguageHint(5, R.string.model_field_editor_language_hint)
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_LABEL = "key_label"

        fun newInstance(label: String): ModelEditorContextMenu = ModelEditorContextMenu().apply {
            arguments = bundleOf(KEY_LABEL to label)
        }
    }
}
