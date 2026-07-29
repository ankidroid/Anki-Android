// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.dialogs.registerDeckSelectedHandler
import com.ichi2.anki.dialogs.startDeckSelection
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.utils.show
import timber.log.Timber

class BlockerSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_blocker
    override val analyticsScreenNameConstant: String
        get() = "prefs.blocker"

    override fun initSubscreen() {
        requirePreference<SwitchPreferenceCompat>(R.string.blocker_enabled_key).setOnPreferenceChangeListener { _, newValue ->
            if (newValue == true && !isAccessibilityServiceEnabled()) {
                showDisclosureDialog()
            }
            true
        }
        requirePreference<Preference>(R.string.blocker_accessibility_status_key).setOnPreferenceClickListener {
            openAccessibilitySettings()
            true
        }
        requirePreference<Preference>(R.string.blocker_blocked_apps_entry_key).setOnPreferenceClickListener {
            startActivity(SingleFragmentActivity.getIntent(requireContext(), BlockedAppsFragment::class))
            true
        }
        requirePreference<Preference>(R.string.blocker_blocked_domains_entry_key).setOnPreferenceClickListener {
            startActivity(SingleFragmentActivity.getIntent(requireContext(), BlockedDomainsFragment::class))
            true
        }
        setupDeckPreference()
    }

    override fun onResume() {
        super.onResume()
        refreshSummaries()
    }

    private fun setupDeckPreference() {
        registerDeckSelectedHandler { deck ->
            if (deck is SelectableDeck.Deck) {
                Timber.i("Blocker: gate deck set to %d", deck.deckId)
                BlockerPrefs.gateDeckId = deck.deckId
                requirePreference<Preference>(R.string.blocker_gate_deck_entry_key).summary = deck.name
            }
        }
        requirePreference<Preference>(R.string.blocker_gate_deck_entry_key).setOnPreferenceClickListener {
            startDeckSelection(allowAll = false, allowFiltered = false)
            true
        }
    }

    private fun refreshSummaries() {
        requirePreference<Preference>(R.string.blocker_accessibility_status_key).setSummary(
            if (isAccessibilityServiceEnabled()) R.string.blocker_a11y_status_on else R.string.blocker_a11y_status_off,
        )
        requirePreference<Preference>(R.string.blocker_blocked_apps_entry_key).summary =
            getString(R.string.blocker_blocked_count, BlockerPrefs.blockedApps.size)
        requirePreference<Preference>(R.string.blocker_blocked_domains_entry_key).summary =
            getString(R.string.blocker_blocked_count, BlockerPrefs.blockedDomains.size)
        launchCatchingTask {
            val deckName =
                BlockerPrefs.gateDeckId
                    .takeIf { it != BlockerPrefs.NO_GATE_DECK }
                    ?.let { withCol { decks.nameIfExists(it) } }
            requirePreference<Preference>(R.string.blocker_gate_deck_entry_key).summary =
                deckName ?: getString(R.string.blocker_gate_deck_current)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val context = requireContext()
        val enabledServices =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val component = ComponentName(context, BlockerAccessibilityService::class.java)
        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == component }
    }

    private fun showDisclosureDialog() {
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.blocker_disclosure_title)
            setMessage(R.string.blocker_disclosure_message)
            setPositiveButton(R.string.dialog_ok) { _, _ -> openAccessibilitySettings() }
            setNegativeButton(R.string.dialog_cancel) { _, _ -> }
        }
    }

    private fun openAccessibilitySettings() {
        Timber.i("Blocker: opening system accessibility settings")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
