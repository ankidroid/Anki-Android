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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.databinding.ItemLocaleDialogFragmentBinding
import com.ichi2.anki.databinding.LocaleSelectionDialogBinding
import com.ichi2.anki.dialogs.LocaleSelectionDialog.LocaleListAdapter.TextViewHolder
import com.ichi2.anki.servicelayer.LanguageHintService
import com.ichi2.anki.servicelayer.LanguageHintService.compareLanguage
import com.ichi2.ui.AccessibleSearchView
import com.ichi2.utils.DisplayUtils.resizeWhenSoftInputShown
import com.ichi2.utils.cancelable
import com.ichi2.utils.customView
import com.ichi2.utils.show
import java.util.Locale

/**
 * Shows a list of [Locale] from which the user can set one as the keyboard hint for that field.
 * Currently supported only by Gboard.
 * @see LanguageHintService
 */
class LocaleSelectionDialog private constructor() : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val selectedLocale: Locale? =
            BundleCompat.getSerializable(
                requireArguments(),
                KEY_SELECTED_LOCALE,
                Locale::class.java,
            )
        val fieldPosition = requireArguments().getInt(KEY_SELECTED_FIELD_POSITION)

        val localeAdapter =
            LocaleListAdapter(selectedLocale) {
                sendSelectionResult(fieldPosition, it)
            }

        val binding = LocaleSelectionDialogBinding.inflate(layoutInflater)
        binding.localeDialogSelectionList.adapter = localeAdapter
        localeAdapter.submitList(createLocaleList(selectedLocale))
        binding.localeDialogSelectionToolbar.setupMenuWith(
            localeAdapter,
            Pair(fieldPosition, selectedLocale),
        )
        return AlertDialog.Builder(requireContext()).show {
            cancelable(true)
            customView(binding.root)
        }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        dialog.window?.let { resizeWhenSoftInputShown(it) }
        // this is required for the keyboard to appear: https://stackoverflow.com/a/10133603/
        dialog.window?.clearFlags(FLAG_NOT_FOCUSABLE or FLAG_ALT_FOCUSABLE_IM)
    }

    private fun Toolbar.setupMenuWith(
        adapter: LocaleListAdapter,
        currentSelection: Pair<Int, Locale?>,
    ) {
        val (fieldPosition, selectedLocale) = currentSelection
        inflateMenu(R.menu.locale_dialog_search_bar)
        setNavigationOnClickListener { sendSelectionResult(fieldPosition, null) }
        (menu.findItem(R.id.locale_dialog_action_search).actionView as AccessibleSearchView).apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean = false

                    override fun onQueryTextChange(newText: String): Boolean {
                        val locales = createLocaleList(selectedLocale, newText)
                        adapter.submitList(locales)
                        return false
                    }
                },
            )
        }
    }

    private fun sendSelectionResult(
        position: Int,
        locale: Locale?,
    ) {
        parentFragmentManager.setFragmentResult(
            REQUEST_HINT_LOCALE_SELECTION,
            bundleOf(
                KEY_SELECTED_FIELD_POSITION to position,
                KEY_SELECTED_LOCALE to locale,
            ),
        )
    }

    private fun createLocaleList(
        selectedLanguage: Locale?,
        text: String = "",
    ): List<Locale> {
        var result = Locales.toList()
        if (selectedLanguage != null) {
            result = listOf(selectedLanguage) +
                result.filter { locale ->
                    !compareLanguage(locale, selectedLanguage)
                }
        }
        if (text.isNotEmpty()) {
            val normalisedConstraint = text.lowercase(Locale.getDefault())
            result =
                result.filter {
                    it.displayName.lowercase(Locale.getDefault()).contains(normalisedConstraint)
                }
        }
        return result
    }

    private class LocaleListAdapter(
        private val selectedLocale: Locale?,
        private val onLocaleSelected: (Locale) -> Unit,
    ) : ListAdapter<Locale, TextViewHolder>(DIFF_CALLBACK) {
        class TextViewHolder(
            val binding: ItemLocaleDialogFragmentBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = TextViewHolder(
            ItemLocaleDialogFragmentBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

        override fun onBindViewHolder(
            holder: TextViewHolder,
            position: Int,
        ) {
            val locale = getItem(position)
            holder.binding.localeDialogFragmentTextView.text = locale.displayName
            holder.binding.root.setOnClickListener { onLocaleSelected(locale) }
            val isSelected = selectedLocale != null && compareLanguage(locale, selectedLocale)
            holder.binding.localeDialogFragmentImageView.isVisible = isSelected
        }
    }

    companion object {
        const val REQUEST_HINT_LOCALE_SELECTION = "request_hint_locale_selection"
        const val KEY_SELECTED_FIELD_POSITION = "key_selected_field_position"
        const val KEY_SELECTED_LOCALE = "key_selected_locale"

        /**
         * Language identifier for International Phonetic Alphabet. This isn't available from [Locale.getAvailableLocales], but
         * GBoard seems to understand this as a language code.
         *
         * See issue #13883
         * See https://en.wikipedia.org/wiki/International_Phonetic_Alphabet#IETF_language_tags
         */
        private val IPALanguage = Locale.Builder().setLanguageTag("und-fonipa").build()
        private val Locales = Locale.getAvailableLocales() + IPALanguage

        private val DIFF_CALLBACK =
            object : DiffUtil.ItemCallback<Locale>() {
                override fun areItemsTheSame(
                    oldItem: Locale,
                    newItem: Locale,
                ) = compareLanguage(oldItem, newItem)

                override fun areContentsTheSame(
                    oldItem: Locale,
                    newItem: Locale,
                ) = compareLanguage(oldItem, newItem)
            }

        fun newInstance(
            fieldPosition: Int,
            locale: Locale?,
        ) = LocaleSelectionDialog().apply {
            arguments =
                bundleOf(
                    KEY_SELECTED_FIELD_POSITION to fieldPosition,
                    KEY_SELECTED_LOCALE to locale,
                )
        }
    }
}
