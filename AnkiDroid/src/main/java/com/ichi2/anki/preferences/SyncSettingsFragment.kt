// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.SharedPreferences
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.sync.MediaSyncStatus
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.mediaSyncStatus
import com.ichi2.anki.syncAuth
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.utils.show
import kotlinx.coroutines.launch

/**
 * Fragment with preferences related to syncing
 */
class SyncSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_sync
    override val analyticsScreenNameConstant: String
        get() = "prefs.sync"

    private var mediaSyncWork: WorkInfo? = null

    override fun initSubscreen() {
        // AnkiWeb Account
        updateSyncAccountSummary()

        // Enable/disable one-way sync if the user is logged in
        updateOneWaySyncEnabledState()

        setupMediaSyncStatus()

        // Configure 'Network timeout'
        // TODO: add 'reset to default' functionality
        requirePreference<NumberRangePreferenceCompat>(R.string.sync_io_timeout_secs_key).apply {
            title = TR.preferencesNetworkTimeout()
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    runCatchingWithReport("network_timeout") {
                        TR.qtMiscSecond(this.getValue())
                    }.getOrNull() ?: getString(CommonString.pref__etc__summary__error)
                }
        }

        // Configure one-way sync option
        requirePreference<Preference>(R.string.one_way_sync_key).apply {
            setSummary(TR.preferencesOnNextSyncForceChangesIn())
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(CommonString.one_way_sync_title)
                    setMessage(TR.preferencesOnNextSyncForceChangesIn())
                    setPositiveButton(CommonString.dialog_ok) { _, _ ->
                        launchCatchingTask {
                            withCol { modSchema(check = false) }
                            showSnackbar(CommonString.one_way_sync_confirmation, Snackbar.LENGTH_SHORT)
                        }
                    }
                    setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                }
                false
            }
        }

        requirePreference<Preference>(R.string.sync_account_key).apply {
            title = TR.sentenceCase.ankiWebAccount
            setOnPreferenceClickListener {
                val accountActivityIntent = AccountActivity.getIntent(requireContext())
                startActivity(accountActivityIntent)
                true
            }
        }

        // Custom sync server
        requirePreference<Preference>(R.string.custom_sync_server_key).setSummaryProvider {
            customSyncBase() ?: getString(CommonString.custom_sync_server_summary_none_of_the_two_servers_used)
        }
    }

    private fun setupMediaSyncStatus() {
        requirePreference<Preference>(CommonPreferenceKey.media_sync_status_key).setOnPreferenceClickListener {
            if (mediaSyncStatus(mediaSyncWork).canSync) startMediaSync()
            true
        }
        val fragmentLifecycle = lifecycle
        fragmentLifecycle.coroutineScope.launch {
            fragmentLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkManager
                    .getInstance(requireContext())
                    .getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)
                    .collect { workInfos ->
                        mediaSyncWork = workInfos.lastOrNull()
                        updateMediaSyncStatus()
                    }
            }
        }
    }

    private fun startMediaSync() {
        val auth = syncAuth() ?: return
        val context = requireContext()
        with(context) {
            MeteredSyncPolicy.confirmThen(onDialogShown = {}) { permission ->
                val networkType = MeteredSyncPolicy.getNetworkTypeRequiredForSync(forMedia = true, permission = permission)
                launchCatchingTask { SyncMediaWorker.start(context, auth, networkType) }
            }
        }
    }

    private fun updateMediaSyncStatus() {
        val status = mediaSyncStatus(mediaSyncWork)
        requirePreference<Preference>(CommonPreferenceKey.media_sync_status_key).apply {
            isEnabled = status != MediaSyncStatus.NotLoggedIn
            setIcon(status.iconRes)
            summary = status.summary()
        }
    }

    @get:DrawableRes
    private val MediaSyncStatus.iconRes: Int
        get() =
            when (this) {
                is MediaSyncStatus.Completed -> R.drawable.ic_done
                is MediaSyncStatus.Failed -> R.drawable.ic_sync_problem
                MediaSyncStatus.WaitingForUnmeteredNetwork -> R.drawable.baseline_wifi_24
                MediaSyncStatus.NotLoggedIn,
                MediaSyncStatus.Off,
                MediaSyncStatus.Syncing,
                MediaSyncStatus.WaitingForNetwork,
                MediaSyncStatus.NotRecorded,
                -> R.drawable.ic_sync
            }

    private fun MediaSyncStatus.summary(): String =
        when (this) {
            MediaSyncStatus.NotLoggedIn -> getString(CommonString.sync_account_summ_logged_out)
            MediaSyncStatus.Off -> getString(CommonString.media_sync_status_off)
            MediaSyncStatus.Syncing -> getString(CommonString.media_sync_status_syncing)
            MediaSyncStatus.WaitingForNetwork -> getString(CommonString.media_sync_status_waiting_for_network)
            MediaSyncStatus.WaitingForUnmeteredNetwork -> getString(CommonString.media_sync_status_waiting_for_wifi)
            MediaSyncStatus.NotRecorded -> getString(CommonString.media_sync_status_not_recorded)
            is MediaSyncStatus.Failed -> getString(CommonString.media_sync_status_failed, formatSyncTime(time))
            is MediaSyncStatus.Completed -> getString(CommonString.media_sync_status_completed, formatSyncTime(time))
        }

    private fun formatSyncTime(time: EpochMilliseconds): String =
        DateUtils.formatDateTime(
            requireContext(),
            time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if (key in mediaSyncStatusKeys) updateMediaSyncStatus()
    }

    private val mediaSyncStatusKeys
        get() =
            listOf(
                R.string.hkey_key,
                R.string.sync_fetch_media_key,
                CommonPreferenceKey.last_media_sync_time_key,
                CommonPreferenceKey.last_media_sync_failed_key,
            ).map { getString(it) }

    private fun updateSyncAccountSummary() {
        requirePreference<Preference>(R.string.sync_account_key)
            .summary =
            Prefs.username.ifNullOrEmpty { getString(CommonString.sync_account_summ_logged_out) }
    }

    private fun updateOneWaySyncEnabledState() {
        val isLoggedIn = !Prefs.username.isNullOrEmpty()
        requirePreference<Preference>(R.string.one_way_sync_key).isEnabled = isLoggedIn
    }

    // TODO trigger the summary change from MyAccount.kt once it is migrated to a fragment
    override fun onResume() {
        // Trigger a summary update in case the user logged in/out on MyAccount activity
        updateSyncAccountSummary()
        updateOneWaySyncEnabledState()
        super.onResume()
    }

    private fun customSyncBase(): String? {
        if (!Prefs.isCustomSyncEnabled) return null
        return Prefs.customSyncUri?.ifEmpty { null }
    }
}
