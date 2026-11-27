// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Snowiee <xenonnn4w@gmail.com>

package com.ichi2.anki.utils

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.multiprofile.ProfileId
import timber.log.Timber

/**
 * Wrapper around [ShortcutManagerCompat] to handle platform-specific issues.
 */
object ShortcutUtils {
    /**
     * Batch size for shortcut operations to avoid [android.os.TransactionTooLargeException].
     * Android's Binder IPC has a 1MB transaction limit.
     */
    private const val BATCH_SIZE = 100

    /**
     * The ID of the pinned shortcut for [deckId]: `1` in Default and `p_1a2b3c4d_1` in other
     * profiles, so profiles don't replace each other's shortcut for the same deck ID.
     */
    fun deckShortcutId(
        deckId: DeckId,
        profileId: ProfileId,
    ): String = if (profileId.isDefault()) deckId.toString() else "${profileId.value}_$deckId"

    /**
     * Disables shortcuts by their IDs, handling large lists by batching to avoid
     * [android.os.TransactionTooLargeException].
     *
     * @param context The context to use
     * @param shortcutIds List of shortcut IDs to disable
     * @param disabledMessage Message shown when user tries to use a disabled shortcut
     */
    fun disableShortcuts(
        context: Context,
        shortcutIds: List<String>,
        disabledMessage: CharSequence,
    ) {
        if (shortcutIds.isEmpty()) return

        shortcutIds.chunked(BATCH_SIZE).forEach { batch ->
            runCatchingWithReport(
                "Failed to disable shortcuts batch",
            ) { ShortcutManagerCompat.disableShortcuts(context, batch, disabledMessage) }
                .onFailure { e -> Timber.w(e, "Failed to disable shortcuts batch") }
        }
    }
}
