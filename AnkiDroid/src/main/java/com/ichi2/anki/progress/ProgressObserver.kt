/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.progress

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.dialogs.dismissLoadingDialog
import com.ichi2.anki.dialogs.showLoadingDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows/dismisses a loading dialog driven by [viewModel]'s progress flow.
 * [delayMillis] defers the initial show so quick operations don't flash a dialog.
 */
fun AnkiActivity.observeProgress(
    viewModel: HasProgress,
    delayMillis: Duration = 600.milliseconds,
) {
    var dialogVisible =
        supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG) != null
    // Pending "show after delay" job survives subsequent Active emissions so the
    // anti-flash delay isn't restarted on every progress update.
    var pendingShow: Job? = null

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.progressManager.progress.collect { state ->
                when (state) {
                    is ViewModelProgress.Idle -> {
                        pendingShow?.cancel()
                        pendingShow = null
                        dialogVisible = false
                        dismissLoadingDialog()
                    }
                    is ViewModelProgress.Active -> {
                        if (dialogVisible) {
                            showLoadingDialog(
                                message = state.formatMessage(),
                                cancellable = state.cancellable,
                            )
                            if (state.cancellable) wireCancelListener(viewModel)
                        } else if (pendingShow == null) {
                            pendingShow =
                                launch {
                                    delay(delayMillis)
                                    val latest = viewModel.progressManager.progress.value
                                    if (latest is ViewModelProgress.Active) {
                                        dialogVisible = true
                                        showLoadingDialog(
                                            message = latest.formatMessage(),
                                            cancellable = latest.cancellable,
                                        )
                                        if (latest.cancellable) wireCancelListener(viewModel)
                                    }
                                    pendingShow = null
                                }
                        }
                    }
                }
            }
        }
    }
}

private fun AnkiActivity.wireCancelListener(viewModel: HasProgress) {
    supportFragmentManager.executePendingTransactions()
    val fragment =
        supportFragmentManager.findFragmentByTag(LoadingDialogFragment.TAG)
            as? LoadingDialogFragment
    fragment?.dialog?.setOnCancelListener {
        viewModel.progressManager.requestCancel()
    }
}

/**
 * Fragment wrapper that delegates to the host activity.
 *
 * If the host is not an [AnkiActivity], logs a warning and returns — no observer is set up.
 *
 * @throws IllegalStateException if the fragment is not currently attached to an activity
 *   (propagated from [Fragment.requireActivity]).
 *
 * TODO: relax [showLoadingDialog]/[dismissLoadingDialog] to `FragmentActivity` and drop
 *   the cast.
 */
fun Fragment.observeProgress(
    viewModel: HasProgress,
    delayMillis: Duration = 600.milliseconds,
) {
    val activity = requireActivity() as? AnkiActivity
    if (activity == null) {
        Timber.w(
            "observeProgress called from a Fragment hosted by %s, which is not an AnkiActivity; skipping.",
            requireActivity().javaClass.simpleName,
        )
        return
    }
    activity.observeProgress(viewModel, delayMillis)
}

private fun ViewModelProgress.Active.formatMessage(): String? {
    val amount = amount ?: return message
    val formattedAmount = formatAmount(amount)
    return when {
        message.isNullOrEmpty() -> formattedAmount
        else -> "$message$separator$formattedAmount"
    }
}
