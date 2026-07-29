// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

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
            if (newValue != true) {
                view?.post { refreshSummaries() }
                return@setOnPreferenceChangeListener true
            }
            // The accessibility disclosure must be accepted before the feature turns on,
            // not after: consent has to be an affirmative action by the user.
            if (!BlockerPrefs.hasAcceptedDisclosure) {
                showDisclosureDialog()
                return@setOnPreferenceChangeListener false
            }
            if (!BlockerStatus.isAccessibilityServiceEnabled(requireContext())) {
                openAccessibilitySettings()
            }
            view?.post { refreshSummaries() }
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
        val context = requireContext()
        val serviceEnabled = BlockerStatus.isAccessibilityServiceEnabled(context)
        requirePreference<Preference>(R.string.blocker_accessibility_status_key).setSummary(
            if (serviceEnabled) R.string.blocker_a11y_status_on else R.string.blocker_a11y_status_off,
        )
        val blockedApps = BlockerPrefs.blockedApps.size
        val blockedDomains = BlockerPrefs.blockedDomains.size
        requirePreference<Preference>(R.string.blocker_blocked_apps_entry_key).summary =
            getString(R.string.blocker_blocked_count, blockedApps)
        requirePreference<Preference>(R.string.blocker_blocked_domains_entry_key).summary =
            getString(R.string.blocker_blocked_count, blockedDomains)
        requirePreference<Preference>(R.string.blocker_status_warning_key).apply {
            val warning =
                when {
                    !BlockerPrefs.isEnabled -> null
                    !serviceEnabled -> getString(R.string.blocker_inactive_settings_warning)
                    blockedApps == 0 && blockedDomains == 0 -> getString(R.string.blocker_nothing_blocked)
                    else -> null
                }
            isVisible = warning != null
            summary = warning
        }
        BlockerStatus.refreshInactiveNotification(context)
        launchCatchingTask {
            val deckName =
                BlockerPrefs.gateDeckId
                    .takeIf { it != BlockerPrefs.NO_GATE_DECK }
                    ?.let { withCol { decks.nameIfExists(it) } }
            requirePreference<Preference>(R.string.blocker_gate_deck_entry_key).summary =
                deckName ?: getString(R.string.blocker_gate_deck_current)
        }
    }

    private fun showDisclosureDialog() {
        AlertDialog.Builder(requireContext()).show {
            setTitle(R.string.blocker_disclosure_title)
            setMessage(R.string.blocker_disclosure_message)
            setCancelable(false)
            setPositiveButton(R.string.blocker_disclosure_accept) { _, _ ->
                Timber.i("Blocker: accessibility disclosure accepted")
                BlockerPrefs.hasAcceptedDisclosure = true
                BlockerPrefs.isEnabled = true
                requirePreference<SwitchPreferenceCompat>(R.string.blocker_enabled_key).isChecked = true
                refreshSummaries()
                if (!BlockerStatus.isAccessibilityServiceEnabled(requireContext())) {
                    openAccessibilitySettings()
                }
            }
            setNegativeButton(R.string.dialog_cancel) { _, _ ->
                Timber.i("Blocker: accessibility disclosure declined")
            }
        }
    }

    private fun openAccessibilitySettings() {
        Timber.i("Blocker: opening system accessibility settings")
        startActivity(BlockerStatus.accessibilitySettingsIntent())
    }
}
