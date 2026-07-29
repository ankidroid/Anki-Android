// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.content.Context
import android.content.Intent
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

/**
 * In-process mediator between the blocker's detection side
 * ([BlockerAccessibilityService]) and its gate side ([GateActivity]). Both run
 * in the same process, so a singleton is sufficient — matching the codebase's
 * convention of `object` singletons over DI.
 */
object BlockerController {
    /** True while a [GateActivity] is on screen; prevents gate-on-gate storms. */
    @Volatile
    var isGateActive: Boolean = false

    /** The live service instance, set while the accessibility service is bound. */
    @Volatile
    var service: BlockerAccessibilityService? = null

    fun grantUnlock(target: BlockTarget) {
        val minutes = BlockerPrefs.unlockMinutes
        UnlockStore.grant(target, durationMs = minutes.minutes.inWholeMilliseconds)
        Timber.i("Blocker: unlocked %s for %d minutes", target.key, minutes)
        service?.onUnlockGranted()
    }

    /**
     * Called whenever a gate leaves the screen: starts the cooldown that stops the
     * closing gate from re-triggering itself, and schedules a re-check so returning
     * straight to the blocked app during that cooldown is still caught.
     */
    fun noteGateClosed() {
        service?.engine?.noteGateClosed()
        service?.schedulePostGateRecheck()
    }

    /**
     * Tells the running service that the blocklist or master toggle changed, so it
     * re-applies its event filter. Must be called after any change to
     * [BlockerPrefs.blockedApps], [BlockerPrefs.blockedDomains] or
     * [BlockerPrefs.isEnabled] — without it the service keeps filtering on the
     * configuration it read when it started, and new entries are ignored.
     */
    fun notifyConfigChanged() {
        service?.onConfigChanged()
    }

    /**
     * The gate was dismissed without earning an unlock: route the user away from
     * the blocked target so the gate isn't simply bypassed. Blocked apps are left
     * for the home screen; blocked websites are backed off in the browser.
     */
    fun onGateAbandoned(
        context: Context,
        target: BlockTarget,
    ) {
        Timber.i("Blocker: gate abandoned for %s", target.key)
        val liveService = service
        if (target is BlockTarget.Domain && liveService != null) {
            liveService.performBackForAbandonedDomain()
            return
        }
        val home =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(home)
    }
}
