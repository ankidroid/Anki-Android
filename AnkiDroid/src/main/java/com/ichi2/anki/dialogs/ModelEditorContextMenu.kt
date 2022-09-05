//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.ModelFieldEditor
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.ModelEditorContextMenu.ModelEditorContextMenuAction.AddLanguageHint
import timber.log.Timber

/**
 * Note: the class is declared as open only to support testing.
 */
open class ModelEditorContextMenu : AnalyticsDialogFragment() {

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        // add only the actions which can be done at the current API level
        var availableItems = if (isAtLeastAtN()) {
            ModelEditorContextMenuAction.values().toList()
        } else {
            ModelEditorContextMenuAction.values().filterNot { it == AddLanguageHint }
        }
        availableItems = availableItems.sortedBy { it.order }

        return MaterialDialog(requireActivity()).show {
            title(text = requireArguments().getString(KEY_LABEL))
            listItems(items = availableItems.map { resources.getString(it.actionTextId) }) { _, index, _ ->
                (activity as? ModelFieldEditor)?.run { handleAction(availableItems[index]) }
                    ?: Timber.e("ContextMenu used from outside of its target activity!")
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected open fun isAtLeastAtN() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    enum class ModelEditorContextMenuAction(val order: Int, @StringRes val actionTextId: Int) {
        Reposition(0, R.string.model_field_editor_reposition_menu),
        Sort(1, R.string.model_field_editor_sort_field),
        Rename(2, R.string.model_field_editor_rename),
        Delete(3, R.string.model_field_editor_delete),
        ToggleSticky(4, R.string.model_field_editor_toggle_sticky),

        /**
         * This action will be possible only when the api level of the platform is at least at [Build.VERSION_CODES.N].
         */
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
