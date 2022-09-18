/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import androidx.preference.ListPreference
import androidx.preference.SwitchPreference
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.contextmenu.AnkiCardContextMenu
import com.ichi2.anki.contextmenu.CardBrowserContextMenu
import com.ichi2.annotations.NeedsTest
import com.ichi2.utils.LanguageUtil
import com.ichi2.utils.LanguageUtil.getSystemLocale
import kotlinx.coroutines.runBlocking
import java.util.*

class GeneralSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_general
    override val analyticsScreenNameConstant: String
        get() = "prefs.general"

    override fun initSubscreen() {
        val col = col!!
        // Build languages
        initializeLanguageDialog()

        // Deck for new cards
        // Represents in the collections pref "addToCur": i.e.
        // if true, then add note to current decks, otherwise let the note type's configuration decide
        // Note that "addToCur" is a boolean while USE_CURRENT is "0" or "1"
        requirePreference<ListPreference>(R.string.deck_for_new_cards_key).apply {
            setValueIndex(if (col.get_config("addToCur", true)!!) 0 else 1)
            setOnPreferenceChangeListener { newValue ->
                col.set_config("addToCur", "0" == newValue)
            }
        }
        // Paste PNG
        // Represents in the collection's pref "pastePNG" , i.e.
        // whether to convert clipboard uri to png format or not.
        requirePreference<SwitchPreference>(R.string.paste_png_key).apply {
            isChecked = col.get_config("pastePNG", false)!!
            setOnPreferenceChangeListener { newValue ->
                col.set_config("pastePNG", newValue)
            }
        }
        // Error reporting mode
        requirePreference<ListPreference>(R.string.error_reporting_mode_key).setOnPreferenceChangeListener { newValue ->
            CrashReportService.onPreferenceChanged(requireContext(), newValue as String)
        }
        // Anki card context menu
        requirePreference<SwitchPreference>(R.string.anki_card_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.context_menu_anki_card_label))
            summary = getString(R.string.card_browser_enable_external_context_menu_summary, getString(R.string.context_menu_anki_card_label))
            setOnPreferenceChangeListener { newValue ->
                AnkiCardContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue as Boolean)
            }
        }
        // Card browser context menu
        requirePreference<SwitchPreference>(R.string.card_browser_external_context_menu_key).apply {
            title = getString(R.string.card_browser_enable_external_context_menu, getString(R.string.card_browser_context_menu))
            summary = getString(R.string.card_browser_enable_external_context_menu_summary, getString(R.string.card_browser_context_menu))
            setOnPreferenceChangeListener { newValue ->
                CardBrowserContextMenu.ensureConsistentStateWithPreferenceStatus(requireContext(), newValue as Boolean)
            }
        }
    }

    @NeedsTest("")
    private fun initializeLanguageDialog() {
        val languageSelection = requirePreference<ListPreference>(R.string.pref_language_key)

        val items: MutableMap<String, String> = TreeMap(java.lang.String.CASE_INSENSITIVE_ORDER)
        for (localeCode in LanguageUtil.APP_LANGUAGES) {
            val loc = LanguageUtil.getLocale(localeCode)
            items[loc.getDisplayName(loc)] = loc.toString()
        }
        val languageDialogLabels = arrayOfNulls<CharSequence>(items.size + 1)
        val languageDialogValues = arrayOfNulls<CharSequence>(items.size + 1)
        languageDialogLabels[0] = resources.getString(R.string.language_system)
        languageDialogValues[0] = "${getSystemLocale()}"
        val itemsList = items.toList()
        for (i in 1..itemsList.size) {
            languageDialogLabels[i] = itemsList[i - 1].first
            languageDialogValues[i] = itemsList[i - 1].second
        }

        languageSelection.entries = languageDialogLabels
        languageSelection.entryValues = languageDialogValues

        // It's only possible to change the language by recreating the activity,
        // so do it if the language has changed.
        languageSelection.setOnPreferenceChangeListener { newValue ->
            LanguageUtil.setDefaultBackendLanguages(newValue as String)
            runBlocking { CollectionManager.discardBackend() }

            requireActivity().recreate()
        }
    }
}
