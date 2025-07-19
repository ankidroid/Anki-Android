//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.ui.ButtonItemAdapter
import com.ichi2.utils.customListAdapterWithDecoration
import com.ichi2.utils.input
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

// TODO: Add different classes for the two different dialogs

class CardBrowserMySearchesDialog : AnalyticsDialogFragment() {
    private var buttonItemAdapter: ButtonItemAdapter? = null
    private var savedFilters: HashMap<String, String>? = null
    private var savedFilterKeys: ArrayList<String>? = null

    interface MySearchesDialogListener {
        fun onSelection(searchName: String)

        fun onRemoveSearch(searchName: String)

        fun onSaveSearch(
            searchName: String,
            searchTerms: String?,
        )
    }

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreate(savedInstanceState)
        val dialog = AlertDialog.Builder(requireActivity())
        val type = requireArguments().getSerializableCompat<SavedSearchesType>(SAVED_TYPE_KEY)
        if (type == SavedSearchesType.List) {
            savedFilters = requireArguments().getSerializableCompat(SAVED_FILTER_KEY)

            savedFilters?.let {
                savedFilterKeys = ArrayList(it.keys)
            }

            buttonItemAdapter =
                ButtonItemAdapter(
                    savedFilterKeys!!,
                    itemCallback = { searchName ->
                        Timber.d("item clicked: %s", searchName)
                        mySearchesDialogListener!!.onSelection(searchName)
                        dismiss()
                    },
                    buttonCallback = { searchName ->
                        Timber.d("button clicked: %s", searchName)
                        removeSearch(searchName)
                    },
                ).apply {
                    notifyAdapterDataSetChanged() // so the values are sorted.
                    dialog
                        .title(text = resources.getString(R.string.card_browser_list_my_searches_title))
                        .customListAdapterWithDecoration(this, requireActivity())
                }
        } else if (type == SavedSearchesType.Save) {
            val currentSearchTerms = requireArguments().getString(SAVED_CURRENT_SEARCH_TERMS_KEY)
            return dialog
                .show {
                    title(text = getString(R.string.card_browser_list_my_searches_save))
                    positiveButton(android.R.string.ok)
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
                }.apply {
                    input(
                        hint = getString(R.string.card_browser_list_my_searches_new_name),
                        allowEmpty = false,
                        displayKeyboard = true,
                        waitForPositiveButton = true,
                    ) { dialog, text ->
                        Timber.d("Saving search with title/terms: %s/%s", text, currentSearchTerms)
                        mySearchesDialogListener?.onSaveSearch(text.toString(), currentSearchTerms)
                        dialog.dismiss()
                    }
                }
        }
        return dialog.create()
    }

    private fun removeSearch(searchName: String) {
        AlertDialog.Builder(requireActivity()).show {
            message(text = resources.getString(R.string.card_browser_list_my_searches_remove_content, searchName))
            positiveButton(android.R.string.ok) {
                mySearchesDialogListener!!.onRemoveSearch(searchName)
                savedFilters!!.remove(searchName)
                savedFilterKeys!!.remove(searchName)
                buttonItemAdapter!!.apply {
                    remove(searchName)
                    notifyAdapterDataSetChanged()
                }
                dialog?.dismiss() // Dismiss the root dialog
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    enum class SavedSearchesType(
        val code: Int,
    ) {
        List(0),
        Save(1),
        ;

        companion object {
            fun fromCode(c: Int) = SavedSearchesType.entries.first { it.code == c }
        }
    }

    companion object {
        const val SAVED_FILTER_KEY = "savedFilters"
        const val SAVED_TYPE_KEY = "type"
        const val SAVED_CURRENT_SEARCH_TERMS_KEY = "currentSearchTerms"
        private var mySearchesDialogListener: MySearchesDialogListener? = null

        fun newInstance(
            savedFilters: Map<String, String>?,
            mySearchesDialogListener: MySearchesDialogListener?,
            currentSearchTerms: String?,
            type: SavedSearchesType,
        ): CardBrowserMySearchesDialog {
            this.mySearchesDialogListener = mySearchesDialogListener
            val cardBrowserMySearchesDialog = CardBrowserMySearchesDialog()
            val args = Bundle()
            args.putSerializable(SAVED_FILTER_KEY, savedFilters?.let(::HashMap))
            args.putSerializable(SAVED_TYPE_KEY, type)
            args.putString(SAVED_CURRENT_SEARCH_TERMS_KEY, currentSearchTerms)
            cardBrowserMySearchesDialog.arguments = args
            return cardBrowserMySearchesDialog
        }
    }
}
