//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialog.ListCallback
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment

class ModelEditorContextMenu : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val entries = arrayOfNulls<String>(entryCount)
        entries[FIELD_REPOSITION] = resources.getString(R.string.model_field_editor_reposition_menu)
        entries[SORT_FIELD] = resources.getString(R.string.model_field_editor_sort_field)
        entries[FIELD_RENAME] = resources.getString(R.string.model_field_editor_rename)
        entries[FIELD_DELETE] = resources.getString(R.string.model_field_editor_delete)
        entries[FIELD_TOGGLE_STICKY] = resources.getString(R.string.model_field_editor_toggle_sticky)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            entries[FIELD_ADD_LANGUAGE_HINT] = resources.getString(R.string.model_field_editor_language_hint)
        }
        return MaterialDialog.Builder(requireActivity())
            .title(requireArguments().getString("label")!!)
            .items(*entries)
            .itemsCallback(mContextMenuListener!!)
            .build()
    }

    private val entryCount: Int
        get() {
            var entryCount = 5
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                entryCount++
            }
            return entryCount
        }

    companion object {
        const val FIELD_REPOSITION = 0
        const val SORT_FIELD = 1
        const val FIELD_RENAME = 2
        const val FIELD_DELETE = 3
        const val FIELD_TOGGLE_STICKY = 4

        @RequiresApi(api = Build.VERSION_CODES.N)
        const val FIELD_ADD_LANGUAGE_HINT = 5
        private var mContextMenuListener: ListCallback? = null
        @JvmStatic
        fun newInstance(label: String?, contextMenuListener: ListCallback?): ModelEditorContextMenu {
            val n = ModelEditorContextMenu()
            mContextMenuListener = contextMenuListener
            val b = Bundle()
            b.putString("label", label)
            mContextMenuListener = contextMenuListener
            n.arguments = b
            return n
        }
    }
}
