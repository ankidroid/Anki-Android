// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.dialogs.dismissLoadingDialog
import com.ichi2.anki.dialogs.showLoadingDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Blocks touches and shows/dismisses a loading dialog driven by [viewModel]'s progress flow.
 * [delayMillis] defers the initial show so quick operations don't flash a dialog.
 * [resolveMessage] maps the ViewModel's message to the text shown in the dialog.
 */
fun <M : Any> AnkiActivity.observeProgress(
    viewModel: HasProgress<M>,
    delayMillis: Duration = 600.milliseconds,
    resolveMessage: Context.(M) -> String,
) = observeProgressFor(this, viewModel, delayMillis, resolveMessage)

private fun <M : Any> AnkiActivity.observeProgressFor(
    owner: LifecycleOwner,
    viewModel: HasProgress<M>,
    delayMillis: Duration,
    resolveMessage: Context.(M) -> String,
) {
    val dialogTag = "${LoadingDialogFragment.TAG}:${viewModel.javaClass.name}"
    var dialogVisible = supportFragmentManager.findFragmentByTag(dialogTag) != null
    var blockingTouches = false

    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Pending "show after delay" job survives subsequent Active emissions so the
            // anti-flash delay isn't restarted on every progress update. Kept inside the
            // block: a job cancelled on stop must not linger into the next start.
            var pendingShow: Job? = null
            val progress =
                viewModel.progressManager.progress.onCompletion {
                    if (blockingTouches) {
                        blockingTouches = false
                        window.unblockTouches()
                    }
                }
            progress.collect { state ->
                when (state) {
                    is ViewModelProgress.Idle -> {
                        pendingShow?.cancel()
                        pendingShow = null
                        dialogVisible = false
                        dismissLoadingDialog(dialogTag)
                        if (blockingTouches) {
                            blockingTouches = false
                            window.unblockTouches()
                        }
                    }
                    is ViewModelProgress.Active -> {
                        if (!blockingTouches) {
                            blockingTouches = true
                            window.blockTouches()
                        }
                        if (dialogVisible) {
                            showLoadingDialog(
                                message = formatMessage(state, resolveMessage),
                                cancellable = state.cancellable,
                                tag = dialogTag,
                            )
                            if (state.cancellable) wireCancelListener(viewModel, dialogTag)
                        } else if (pendingShow == null) {
                            pendingShow =
                                launch {
                                    delay(delayMillis)
                                    val latest = viewModel.progressManager.progress.value
                                    if (latest is ViewModelProgress.Active) {
                                        dialogVisible = true
                                        showLoadingDialog(
                                            message = formatMessage(latest, resolveMessage),
                                            cancellable = latest.cancellable,
                                            tag = dialogTag,
                                        )
                                        if (latest.cancellable) wireCancelListener(viewModel, dialogTag)
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

private val touchBlockCounts = WeakHashMap<Window, Int>()

internal fun Window.blockTouches() {
    val count = touchBlockCounts[this] ?: 0
    if (count == 0) addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    touchBlockCounts[this] = count + 1
}

internal fun Window.unblockTouches() {
    val count = touchBlockCounts[this] ?: return
    if (count > 1) {
        touchBlockCounts[this] = count - 1
        return
    }
    touchBlockCounts.remove(this)
    clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
}

private fun AnkiActivity.wireCancelListener(
    viewModel: HasProgress<*>,
    dialogTag: String,
) {
    supportFragmentManager.executePendingTransactions()
    val fragment =
        supportFragmentManager.findFragmentByTag(dialogTag)
            as? LoadingDialogFragment
    fragment?.dialog?.setOnCancelListener {
        viewModel.progressManager.requestCancel()
    }
}

/**
 * Follows this fragment's lifecycle, showing the dialog and blocking touches on the host activity.
 *
 * If the host is not an [AnkiActivity], logs a warning and returns — no observer is set up.
 *
 * @throws IllegalStateException if the fragment is not currently attached to an activity
 *   (propagated from [Fragment.requireActivity]).
 *
 * TODO: relax [showLoadingDialog]/[dismissLoadingDialog] to `FragmentActivity` and drop
 *   the cast.
 */
fun <M : Any> Fragment.observeProgress(
    viewModel: HasProgress<M>,
    delayMillis: Duration = 600.milliseconds,
    resolveMessage: Context.(M) -> String,
) {
    val activity = requireActivity() as? AnkiActivity
    if (activity == null) {
        Timber.w(
            "observeProgress called from a Fragment hosted by %s, which is not an AnkiActivity; skipping.",
            requireActivity().javaClass.simpleName,
        )
        return
    }
    activity.observeProgressFor(this, viewModel, delayMillis, resolveMessage)
}

private fun <M : Any> Context.formatMessage(
    state: ViewModelProgress.Active<M>,
    resolveMessage: Context.(M) -> String,
): String? {
    val text = state.message?.let { resolveMessage(it) }
    val amount = state.amount ?: return text
    val formattedAmount = state.formatAmount(amount)
    return when {
        text.isNullOrEmpty() -> formattedAmount
        else -> "$text${state.separator}$formattedAmount"
    }
}
