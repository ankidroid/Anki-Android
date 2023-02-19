//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.ichi2.anki.ModelBrowser
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import timber.log.Timber

class ModelBrowserContextMenu : AnalyticsDialogFragment() {

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val items = ModelBrowserContextMenuAction.values().sortedBy { it.order }
        return MaterialDialog(requireActivity()).show {
            title(text = requireArguments().getString(KEY_LABEL)!!)
            listItems(items = items.map { resources.getString(it.actionTextResId) }) { _: MaterialDialog, index: Int, _: CharSequence ->
                (requireActivity() as? ModelBrowser)?.run { handleAction(items[index]) }
                    ?: Timber.e("ContextMenu used from outside of its target activity!")
            }
        }
    }

    companion object {
        private const val KEY_LABEL = "key_label"

        fun newInstance(label: String?): ModelBrowserContextMenu = ModelBrowserContextMenu().apply {
            arguments = bundleOf(KEY_LABEL to label)
        }
    }
}

enum class ModelBrowserContextMenuAction(val order: Int, @StringRes val actionTextResId: Int) {
    Template(0, R.string.model_browser_template),
    Rename(1, R.string.model_browser_rename),
    Delete(2, R.string.model_browser_delete)
}
