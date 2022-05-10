//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.ui.ButtonItemAdapter
import com.ichi2.utils.BundleUtils.getSerializableWithCast
import timber.log.Timber

// TODO: Add different classes for the two different dialogs

class CardBrowserMySearchesDialog : AnalyticsDialogFragment() {
    private var buttonItemAdapter: ButtonItemAdapter? = null
    private var savedFilters: HashMap<String, String>? = null
    private var savedFilterKeys: ArrayList<String>? = null
    private var currentSearchTerms: String? = null

    interface MySearchesDialogListener {
        fun onSelection(searchName: String?)
        fun onRemoveSearch(searchName: String?)
        fun onSaveSearch(searchName: String?, searchTerms: String?)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val builder = MaterialDialog.Builder(requireActivity())
        val type = requireArguments().getInt("type")
        if (type == CARD_BROWSER_MY_SEARCHES_TYPE_LIST) {
            savedFilters = requireArguments().getSerializableWithCast<HashMap<String, String>>("savedFilters")

            savedFilters?.let {
                savedFilterKeys = ArrayList(it.keys)
            }
            buttonItemAdapter = ButtonItemAdapter(savedFilterKeys!!)
            buttonItemAdapter!!.apply {
                notifyAdapterDataSetChanged() // so the values are sorted.
                setCallbacks(
                    object : ButtonItemAdapter.ItemCallback {
                        override fun onItemClicked(searchName: String) {
                            Timber.d("item clicked: %s", searchName)
                            mySearchesDialogListener!!.onSelection(searchName)
                            dialog?.dismiss()
                        }
                    },
                    object : ButtonItemAdapter.ButtonCallback {
                        override fun onButtonClicked(searchName: String) {
                            Timber.d("button clicked: %s", searchName)
                            removeSearch(searchName)
                        }
                    }
                )
                builder.title(resources.getString(R.string.card_browser_list_my_searches_title))
                    .adapter(this, null)
            }
        } else if (type == CARD_BROWSER_MY_SEARCHES_TYPE_SAVE) {
            currentSearchTerms = requireArguments().getString("currentSearchTerms")
            builder.title(getString(R.string.card_browser_list_my_searches_save))
                .positiveText(getString(android.R.string.ok))
                .negativeText(getString(R.string.dialog_cancel))
                .input(R.string.card_browser_list_my_searches_new_name, R.string.empty_string) { _: MaterialDialog?, text: CharSequence ->
                    Timber.d("Saving search with title/terms: %s/%s", text, currentSearchTerms)
                    mySearchesDialogListener!!.onSaveSearch(text.toString(), currentSearchTerms)
                }
        }
        val dialog = builder.build()
        if (dialog.recyclerView != null) {
            val layoutManager = dialog.recyclerView.layoutManager as LinearLayoutManager
            val dividerItemDecoration = DividerItemDecoration(dialog.recyclerView.context, layoutManager.orientation)
            val scale = resources.displayMetrics.density
            val dpAsPixels = (5 * scale + 0.5f).toInt()
            dialog.view.setPadding(dpAsPixels, 0, dpAsPixels, dpAsPixels)
            dialog.recyclerView.addItemDecoration(dividerItemDecoration)
        }
        return dialog
    }

    private fun removeSearch(searchName: String) {
        MaterialDialog.Builder(requireActivity())
            .content(resources.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
            .positiveText(android.R.string.ok)
            .negativeText(R.string.dialog_cancel)
            .onPositive { dialog: MaterialDialog, _: DialogAction? ->
                mySearchesDialogListener!!.onRemoveSearch(searchName)
                savedFilters!!.remove(searchName)
                savedFilterKeys!!.remove(searchName)
                buttonItemAdapter!!.apply {
                    remove(searchName)
                    notifyAdapterDataSetChanged()
                }
                dialog.dismiss()
                if (savedFilters!!.isEmpty()) {
                    getDialog()!!.dismiss()
                }
            }.show()
    }

    companion object {
        const val CARD_BROWSER_MY_SEARCHES_TYPE_LIST = 0 // list searches dialog
        const val CARD_BROWSER_MY_SEARCHES_TYPE_SAVE = 1 // save searches dialog
        private var mySearchesDialogListener: MySearchesDialogListener? = null
        @JvmStatic
        fun newInstance(
            savedFilters: HashMap<String?, String?>?,
            mySearchesDialogListener: MySearchesDialogListener?,
            currentSearchTerms: String?,
            type: Int
        ): CardBrowserMySearchesDialog {
            this.mySearchesDialogListener = mySearchesDialogListener
            val cardBrowserMySearchesDialog = CardBrowserMySearchesDialog()
            val args = Bundle()
            args.putSerializable("savedFilters", savedFilters)
            args.putInt("type", type)
            args.putString("currentSearchTerms", currentSearchTerms)
            cardBrowserMySearchesDialog.arguments = args
            return cardBrowserMySearchesDialog
        }
    }
}
