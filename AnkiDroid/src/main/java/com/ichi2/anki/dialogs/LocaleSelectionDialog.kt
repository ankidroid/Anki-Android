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
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleListAdapter.TextViewHolder
import com.ichi2.ui.RecyclerSingleTouchAdapter
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.TypedFilter
import java.util.Locale

/** Locale selection dialog. Note: this must be dismissed onDestroy if not called from an activity implementing LocaleSelectionDialogHandler  */
class LocaleSelectionDialog : AnalyticsDialogFragment() {
    private var dialogHandler: LocaleSelectionDialogHandler? = null

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
            if (dialogHandler == null) {
                require(context is LocaleSelectionDialogHandler) { "Calling activity must implement LocaleSelectionDialogHandler" }
                dialogHandler = context
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.locale_selection_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = LocaleListAdapter(Locale.getAvailableLocales() + IPALanguage)
        setupRecyclerView(requireActivity(), view, adapter)
        inflateMenu(view, adapter)
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        dialog.window?.let { resizeWhenSoftInputShown(it) }
    }

    private fun setupRecyclerView(activity: Activity, tagsDialogView: View, adapter: LocaleListAdapter) {
        tagsDialogView.findViewById<RecyclerView>(R.id.locale_dialog_selection_list).apply {
            requestFocus()
            this.adapter = adapter
            layoutManager = LinearLayoutManager(activity)
            addOnItemTouchListener(
                RecyclerSingleTouchAdapter(activity) { _, position ->
                    dialogHandler!!.onSelectedLocale(adapter.getLocaleAtPosition(position))
                }
            )
        }
    }

    private fun inflateMenu(tagsDialogView: View, adapter: LocaleListAdapter) {
        tagsDialogView.findViewById<Toolbar>(R.id.locale_dialog_selection_toolbar).apply {
            inflateMenu(R.menu.locale_dialog_search_bar)
            setNavigationOnClickListener { dialogHandler!!.onLocaleSelectionCancelled() }
            (menu.findItem(R.id.locale_dialog_action_search).actionView as SearchView).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        return false
                    }

                    override fun onQueryTextChange(newText: String): Boolean {
                        adapter.filter.filter(newText)
                        return false
                    }
                })
            }
        }
    }

    class LocaleListAdapter(locales: Array<Locale>) : RecyclerView.Adapter<TextViewHolder>(), Filterable {
        private val currentlyVisibleLocales: MutableList<Locale> = locales.toMutableList()
        private val selectableLocales: List<Locale> = locales.toList()

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

        override fun onBindViewHolder(holder: TextViewHolder, position: Int) =
            holder.setLocale(currentlyVisibleLocales[position])

        override fun getItemCount(): Int = currentlyVisibleLocales.size

        fun getLocaleAtPosition(position: Int): Locale = currentlyVisibleLocales[position]

        override fun getFilter(): Filter {
            return object : TypedFilter<Locale>(selectableLocales) {
                override fun filterResults(constraint: CharSequence, items: List<Locale>): List<Locale> {
                    val normalisedConstraint = constraint.toString().lowercase(Locale.getDefault())
                    return items.filter {
                        it.displayName.lowercase(Locale.getDefault()).contains(normalisedConstraint)
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: List<Locale>) {
                    currentlyVisibleLocales.clear()
                    currentlyVisibleLocales.addAll(results)
                    notifyDataSetChanged()
                }
            }
        }
    }

    companion object {
        /**
         * Language identifier for International Phonetic Alphabet. This isn't available from [Locale.getAvailableLocales], but
         * GBoard seems to understand this as a language code.
         *
         * See issue #13883
         * See https://en.wikipedia.org/wiki/International_Phonetic_Alphabet#IETF_language_tags
         */
        private val IPALanguage = Locale.Builder().setLanguageTag("und-fonipa").build()

        /**
         * @param handler Marker interface to enforce the convention the caller implementing LocaleSelectionDialogHandler
         */
        fun newInstance(handler: LocaleSelectionDialogHandler): LocaleSelectionDialog {
            return LocaleSelectionDialog().apply {
                dialogHandler = handler
                arguments = Bundle()
            }
        }
    }
}
