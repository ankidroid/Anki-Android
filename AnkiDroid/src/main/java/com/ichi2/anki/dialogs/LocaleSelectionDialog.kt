/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleListAdapter.TextViewHolder
import com.ichi2.ui.RecyclerSingleTouchAdapter
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import java.util.*

/** Locale selection dialog. Note: this must be dismissed onDestroy if not called from an activity implementing LocaleSelectionDialogHandler  */
class LocaleSelectionDialog : AnalyticsDialogFragment() {
    private var mDialogHandler: LocaleSelectionDialogHandler? = null

    interface LocaleSelectionDialogHandler {
        fun onSelectedLocale(selectedLocale: Locale)
        fun onLocaleSelectionCancelled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Activity) {
            if (mDialogHandler == null) {
                require(context is LocaleSelectionDialogHandler) { "Calling activity must implement LocaleSelectionDialogHandler" }
                mDialogHandler = context
            }
            resizeWhenSoftInputShown(context.window)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()
        val tagsDialogView = LayoutInflater.from(activity)
            .inflate(R.layout.locale_selection_dialog, activity.findViewById(R.id.root_layout), false)
        val adapter = LocaleListAdapter(Locale.getAvailableLocales())
        setupRecyclerView(activity, tagsDialogView, adapter)
        inflateMenu(tagsDialogView, adapter)
        // Only show a negative button, use the RecyclerView for positive actions
        val builder = MaterialDialog.Builder(activity)
            .negativeText(getString(R.string.dialog_cancel))
            .customView(tagsDialogView, false)
            .onNegative { _: MaterialDialog?, _: DialogAction? -> mDialogHandler!!.onLocaleSelectionCancelled() }
        val dialog: Dialog = builder.build()
        val window = dialog.window
        if (window != null) {
            resizeWhenSoftInputShown(window)
        }
        return dialog
    }

    private fun setupRecyclerView(activity: Activity, tagsDialogView: View, adapter: LocaleListAdapter) {
        val recyclerView: RecyclerView = tagsDialogView.findViewById(R.id.locale_dialog_selection_list)
        recyclerView.requestFocus()
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addOnItemTouchListener(
            RecyclerSingleTouchAdapter(activity) { _: View?, position: Int ->
                val l = adapter.getLocaleAtPosition(position)
                mDialogHandler!!.onSelectedLocale(l)
            }
        )
    }

    private fun inflateMenu(tagsDialogView: View, adapter: LocaleListAdapter) {
        val toolbar: Toolbar = tagsDialogView.findViewById(R.id.locale_dialog_selection_toolbar)
        toolbar.setTitle(R.string.locale_selection_dialog_title)
        toolbar.inflateMenu(R.menu.locale_dialog_search_bar)
        val searchItem = toolbar.menu.findItem(R.id.locale_dialog_action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.imeOptions = EditorInfo.IME_ACTION_DONE
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                adapter.filter.filter(newText)
                return false
            }
        })
    }

    class LocaleListAdapter(locales: Array<Locale?>) : RecyclerView.Adapter<TextViewHolder>(), Filterable {
        private val mCurrentlyVisibleLocales: MutableList<Locale>
        private val mSelectableLocales: List<Locale> = Collections.unmodifiableList(ArrayList(mutableListOf(*locales)))

        class TextViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
            fun setText(text: String) {
                textView.text = text
            }

            fun setLocale(locale: Locale) {
                val displayValue = locale.displayName
                textView.text = displayValue
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): TextViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.locale_dialog_fragment_textview, parent, false) as TextView
            return TextViewHolder(v)
        }

        override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
            holder.setLocale(mCurrentlyVisibleLocales[position])
        }

        override fun getItemCount(): Int {
            return mCurrentlyVisibleLocales.size
        }

        fun getLocaleAtPosition(position: Int): Locale {
            return mCurrentlyVisibleLocales[position]
        }

        override fun getFilter(): Filter {
            val selectableLocales = mSelectableLocales
            val visibleLocales = mCurrentlyVisibleLocales
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence): FilterResults {
                    if (TextUtils.isEmpty(constraint)) {
                        val filterResults = FilterResults()
                        filterResults.values = selectableLocales
                        return filterResults
                    }
                    val normalisedConstraint = constraint.toString().lowercase(Locale.getDefault())
                    val locales = ArrayList<Locale>(selectableLocales.size)
                    for (l in selectableLocales) {
                        if (l.displayName.lowercase(Locale.getDefault()).contains(normalisedConstraint)) {
                            locales.add(l)
                        }
                    }
                    val filterResults = FilterResults()
                    filterResults.values = locales
                    return filterResults
                }
                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence, results: FilterResults) {
                    visibleLocales.clear()
                    val values = results.values as Collection<Locale>
                    visibleLocales.addAll(values)
                    notifyDataSetChanged()
                }
            }
        }

        init {
            mCurrentlyVisibleLocales = ArrayList(mutableListOf(*locales))
        }
    }

    companion object {
        /**
         * @param handler Marker interface to enforce the convention the caller implementing LocaleSelectionDialogHandler
         */
        @JvmStatic
        fun newInstance(handler: LocaleSelectionDialogHandler): LocaleSelectionDialog {
            val t = LocaleSelectionDialog()
            t.mDialogHandler = handler
            val args = Bundle()
            t.arguments = args
            return t
        }
    }
}
