//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class ModelBrowserContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val entries = arrayOfNulls<String>(3)
        entries[MODEL_TEMPLATE] = resources.getString(R.string.model_browser_template)
        entries[MODEL_RENAME] = resources.getString(R.string.model_browser_rename)
        entries[MODEL_DELETE] = resources.getString(R.string.model_browser_delete)
        return MaterialDialog.Builder(requireActivity())
            .title(requireArguments().getString("label")!!)
            .items(*entries)
            .itemsCallback(mContextMenuListener!!)
            .build()
    }

    companion object {
        const val MODEL_TEMPLATE = 0
        const val MODEL_RENAME = 1
        const val MODEL_DELETE = 2
        private var mContextMenuListener: ListCallback? = null
        @JvmStatic
        fun newInstance(label: String?, contextMenuListener: ListCallback?): ModelBrowserContextMenu {
            mContextMenuListener = contextMenuListener
            val n = ModelBrowserContextMenu()
            val b = Bundle()
            b.putString("label", label)
            n.arguments = b
            return n
        }
    }
}
