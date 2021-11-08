//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.ui.ButtonItemAdapter
import timber.log.Timber
import java.util.*

class CardBrowserMySearchesDialog : AnalyticsDialogFragment() {
    private var mButtonItemAdapter: ButtonItemAdapter? = null
    private var mSavedFilters: HashMap<String, String>? = null
    private var mSavedFilterKeys: ArrayList<String>? = null
    private var mCurrentSearchTerms: String? = null

    interface MySearchesDialogListener {
        fun onSelection(searchName: String?)
        fun onRemoveSearch(searchName: String?)
        fun onSaveSearch(searchName: String?, searchTerms: String?)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val res = resources
        val activity: Activity? = activity
        val builder = MaterialDialog.Builder(activity!!)
        val type = requireArguments().getInt("type")
        if (type == CARD_BROWSER_MY_SEARCHES_TYPE_LIST) {
            mSavedFilters = requireArguments().getSerializable("savedFilters") as HashMap<String, String>?
            mSavedFilterKeys = ArrayList(mSavedFilters!!.keys)
            mButtonItemAdapter = ButtonItemAdapter(mSavedFilterKeys)
            mButtonItemAdapter!!.notifyAdapterDataSetChanged() // so the values are sorted.
            mButtonItemAdapter!!.setCallbacks(
                { searchName: String? ->
                    Timber.d("item clicked: %s", searchName)
                    mMySearchesDialogListener!!.onSelection(searchName)
                    dialog!!.dismiss()
                }
            ) { searchName: String ->
                Timber.d("button clicked: %s", searchName)
                removeSearch(searchName)
            }
            builder.title(res.getString(R.string.card_browser_list_my_searches_title))
                .adapter(mButtonItemAdapter!!, null)
        } else if (type == CARD_BROWSER_MY_SEARCHES_TYPE_SAVE) {
            mCurrentSearchTerms = requireArguments().getString("currentSearchTerms")
            builder.title(getString(R.string.card_browser_list_my_searches_save))
                .positiveText(getString(android.R.string.ok))
                .negativeText(getString(R.string.dialog_cancel))
                .input(R.string.card_browser_list_my_searches_new_name, R.string.empty_string) { _: MaterialDialog?, text: CharSequence ->
                    Timber.d("Saving search with title/terms: %s/%s", text, mCurrentSearchTerms)
                    mMySearchesDialogListener!!.onSaveSearch(text.toString(), mCurrentSearchTerms)
                }
        }
        val dialog = builder.build()
        if (dialog.recyclerView != null) {
            val layoutManager = dialog.recyclerView.layoutManager as LinearLayoutManager?
            val dividerItemDecoration = DividerItemDecoration(dialog.recyclerView.context, layoutManager!!.orientation)
            val scale = res.displayMetrics.density
            val dpAsPixels = (5 * scale + 0.5f).toInt()
            dialog.view.setPadding(dpAsPixels, 0, dpAsPixels, dpAsPixels)
            dialog.recyclerView.addItemDecoration(dividerItemDecoration)
        }
        return dialog
    }

    private fun removeSearch(searchName: String) {
        val res = resources
        MaterialDialog.Builder(requireActivity())
            .content(res.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
            .positiveText(android.R.string.ok)
            .negativeText(R.string.dialog_cancel)
            .onPositive { dialog: MaterialDialog, _: DialogAction? ->
                mMySearchesDialogListener!!.onRemoveSearch(searchName)
                mSavedFilters!!.remove(searchName)
                mSavedFilterKeys!!.remove(searchName)
                mButtonItemAdapter!!.remove(searchName)
                mButtonItemAdapter!!.notifyAdapterDataSetChanged()
                dialog.dismiss()
                if (mSavedFilters!!.isEmpty()) {
                    getDialog()!!.dismiss()
                }
            }.show()
    }

    companion object {
        const val CARD_BROWSER_MY_SEARCHES_TYPE_LIST = 0 // list searches dialog
        const val CARD_BROWSER_MY_SEARCHES_TYPE_SAVE = 1 // save searches dialog
        private var mMySearchesDialogListener: MySearchesDialogListener? = null
        @JvmStatic
        fun newInstance(
            savedFilters: HashMap<String?, String?>?,
            mySearchesDialogListener: MySearchesDialogListener?,
            currentSearchTerms: String?,
            type: Int
        ): CardBrowserMySearchesDialog {
            mMySearchesDialogListener = mySearchesDialogListener
            val m = CardBrowserMySearchesDialog()
            val args = Bundle()
            args.putSerializable("savedFilters", savedFilters)
            args.putInt("type", type)
            args.putString("currentSearchTerms", currentSearchTerms)
            m.arguments = args
            return m
        }
    }
}
