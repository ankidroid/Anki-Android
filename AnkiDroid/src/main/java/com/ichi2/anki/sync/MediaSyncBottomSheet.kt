// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.databinding.FragmentMediaSyncBottomSheetBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.syncAuth
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.UniqueWorkNames
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.google.android.material.R as MaterialR
import com.ichi2.anki.common.android.R as CommonR

class MediaSyncBottomSheet : BottomSheetDialogFragment(R.layout.fragment_media_sync_bottom_sheet) {
    internal val binding by viewBinding(FragmentMediaSyncBottomSheetBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        ViewCompat.setAccessibilityHeading(binding.title, true)
        binding.syncNow.setOnClickListener { startMediaSync() }
        binding.logIn.setOnClickListener {
            Timber.i("MediaSyncBottomSheet: Log in pressed")
            startActivity(AccountActivity.getIntent(requireContext()))
        }
        binding.abort.text = TR.syncAbortButton()
        binding.abort.setOnClickListener {
            Timber.i("MediaSyncBottomSheet: Abort pressed")
            WorkManager.getInstance(requireContext()).cancelUniqueWork(UniqueWorkNames.SYNC_MEDIA)
        }

        val owner = viewLifecycleOwner
        val workInfos = WorkManager.getInstance(requireContext()).getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                workInfos.collectLatest { infos ->
                    val work = infos.lastOrNull()
                    showStatus(work)
                    if (work?.state == WorkInfo.State.RUNNING) {
                        val backend = CollectionManager.getBackend()
                        showMediaSyncProgress({ withContext(Dispatchers.IO) { backend.mediaSyncStatus() } }) { text ->
                            binding.progress.text = text
                            binding.progress.isVisible = true
                        }
                    }
                }
            }
        }
    }

    private fun showStatus(work: WorkInfo?) {
        val status = mediaSyncStatus(work)
        val color = MaterialColors.getColor(binding.icon, status.colorAttr)
        binding.icon.setImageResource(status.iconRes)
        binding.icon.imageTintList = ColorStateList.valueOf(color)
        binding.icon.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, ICON_BACKGROUND_ALPHA))
        binding.title.setText(status.titleRes)
        binding.summary.text = status.summary()
        binding.progressIndicator.isVisible = status == MediaSyncStatus.Syncing
        binding.progress.isVisible = false
        binding.syncNow.isVisible = status.canSync
        binding.logIn.isVisible = status == MediaSyncStatus.NotLoggedIn
        binding.abort.isVisible = status.canAbort
    }

    private fun startMediaSync() {
        Timber.i("MediaSyncBottomSheet: Sync now pressed")
        val auth = syncAuth() ?: return
        val context = requireContext()
        with(context) {
            MeteredSyncPolicy.confirmThen(onDialogShown = {}) { permission ->
                val networkType = MeteredSyncPolicy.getNetworkTypeRequiredForSync(forMedia = true, permission = permission)
                launchCatchingTask { SyncMediaWorker.start(context, auth, networkType) }
            }
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

    @get:AttrRes
    private val MediaSyncStatus.colorAttr: Int
        get() =
            when (this) {
                is MediaSyncStatus.Failed -> CommonR.attr.badgeWarningColor
                is MediaSyncStatus.Completed,
                MediaSyncStatus.Syncing,
                MediaSyncStatus.NotRecorded,
                -> androidx.appcompat.R.attr.colorPrimary
                MediaSyncStatus.NotLoggedIn,
                MediaSyncStatus.Off,
                MediaSyncStatus.WaitingForNetwork,
                MediaSyncStatus.WaitingForUnmeteredNetwork,
                -> MaterialR.attr.colorOnSurfaceVariant
            }

    @get:StringRes
    private val MediaSyncStatus.titleRes: Int
        get() =
            when (this) {
                MediaSyncStatus.NotLoggedIn -> CommonString.sync_account_summ_logged_out
                MediaSyncStatus.Off -> CommonString.media_sync_off_title
                MediaSyncStatus.Syncing -> CommonString.media_sync_syncing_title
                MediaSyncStatus.WaitingForNetwork -> CommonString.media_sync_waiting_for_network_title
                MediaSyncStatus.WaitingForUnmeteredNetwork -> CommonString.media_sync_waiting_for_wifi_title
                MediaSyncStatus.NotRecorded -> CommonString.media_sync_not_recorded_title
                is MediaSyncStatus.Failed -> CommonString.media_sync_incomplete_title
                is MediaSyncStatus.Completed -> CommonString.media_sync_up_to_date_title
            }

    private fun MediaSyncStatus.summary(): String =
        when (this) {
            MediaSyncStatus.NotLoggedIn -> getString(CommonString.media_sync_not_logged_in_summary)
            MediaSyncStatus.Off -> getString(CommonString.media_sync_off_summary)
            MediaSyncStatus.Syncing -> getString(CommonString.media_sync_syncing_summary)
            MediaSyncStatus.WaitingForNetwork -> getString(CommonString.media_sync_waiting_for_network_summary)
            MediaSyncStatus.WaitingForUnmeteredNetwork -> getString(CommonString.media_sync_waiting_for_wifi_summary)
            MediaSyncStatus.NotRecorded -> getString(CommonString.media_sync_not_recorded_summary)
            is MediaSyncStatus.Failed -> getString(CommonString.media_sync_incomplete_summary, formatSyncTime(time))
            is MediaSyncStatus.Completed -> getString(CommonString.media_sync_up_to_date_summary, formatSyncTime(time))
        }

    private fun formatSyncTime(time: EpochMilliseconds): String =
        DateUtils.formatDateTime(
            requireContext(),
            time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
        )

    companion object {
        const val TAG = "MediaSyncBottomSheet"
        private const val ICON_BACKGROUND_ALPHA = 0x29
    }
}
