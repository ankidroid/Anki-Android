// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import timber.log.Timber

/**
 * Watches foreground-app changes (and, when website blocking is configured, the
 * browser address bar) and launches the [GateActivity] when a blocked target
 * comes to the front without an active unlock.
 *
 * The service never touches the Anki collection: all collection work happens in
 * [GateActivity]. Event delivery is restricted via [applyServiceTuning] to the
 * blocked apps (plus browsers when needed), so the service stays idle for all
 * other device usage.
 *
 * Re-gating happens two ways: any delivered event after an unlock expired
 * triggers a fresh gate, and [rearmExpiryTimer] handles apps that emit no
 * events (e.g. fullscreen video) by checking the foreground window when the
 * earliest unlock expires.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private val ignoredPackages = mutableSetOf<String>()

    internal val engine =
        BlockerEngine(
            config = {
                BlockerEngine.Config(
                    enabled = BlockerPrefs.isEnabled,
                    blockedApps = BlockerPrefs.blockedApps,
                    blockedDomains = BlockerPrefs.blockedDomains,
                    ignoredPackages = ignoredPackages,
                )
            },
            isUnlocked = { target, nowMs -> UnlockStore.isUnlocked(target, nowMs) },
            isGateActive = { BlockerController.isGateActive },
            clock = { TimeManager.time.intTimeMS() },
        )

    private val handler = Handler(Looper.getMainLooper())
    private val expiryCheck = Runnable { onExpiryCheck() }
    private var lastUrlProbeUptimeMs = 0L

    /**
     * Re-tunes only when a key this service depends on changed: AnkiDroid writes
     * unrelated preferences often, and every [setServiceInfo] call causes the
     * system to rebind the service.
     */
    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in watchedPreferenceKeys()) {
                applyServiceTuning()
                rearmExpiryTimer()
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("Blocker: accessibility service connected")
        ignoredPackages.clear()
        ignoredPackages += BuildConfig.APPLICATION_ID
        ignoredPackages += SYSTEM_UI_PACKAGE
        resolveDefaultLauncher()?.let { ignoredPackages += it }
        AnkiDroidApp.sharedPrefs().registerOnSharedPreferenceChangeListener(prefsListener)
        BlockerController.service = this
        applyServiceTuning()
        rearmExpiryTimer()
    }

    override fun onDestroy() {
        AnkiDroidApp.sharedPrefs().unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        BlockerController.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                engine.onForegroundApp(packageName)?.let(::launchGate)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val browser = SupportedBrowsers.byPackage(event.packageName?.toString()) ?: return
                val nowUptime = SystemClock.uptimeMillis()
                if (nowUptime - lastUrlProbeUptimeMs < URL_PROBE_MIN_INTERVAL_MS) return
                lastUrlProbeUptimeMs = nowUptime
                probeBrowserUrl(browser)
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        // nothing to interrupt: gating is stateless per event
    }

    /** Called after an unlock is granted, to schedule the expiry re-check. */
    fun onUnlockGranted() = rearmExpiryTimer()

    /**
     * Backs the browser off a blocked page after its gate was abandoned. Slightly
     * delayed so the gate's finish animation completes and the browser is the
     * window receiving the back action.
     *
     * The foreground app is re-checked at that point: if closing the gate revealed
     * something other than a browser, no back action is sent, so an unrelated app
     * never receives a stray back press.
     */
    fun performBackForAbandonedDomain() {
        handler.postDelayed({
            val foregroundPackage = rootInActiveWindow?.packageName?.toString()
            if (SupportedBrowsers.byPackage(foregroundPackage) != null) {
                Timber.i("Blocker: leaving blocked page in %s", foregroundPackage)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                Timber.i("Blocker: browser no longer in foreground, skipping back action")
            }
        }, BACK_AFTER_ABANDON_DELAY_MS)
    }

    private fun probeBrowserUrl(browser: SupportedBrowser) {
        val root = rootInActiveWindow ?: return
        val host =
            browser.urlBarViewIds
                .firstNotNullOfOrNull { viewId ->
                    root
                        .findAccessibilityNodeInfosByViewId(viewId)
                        ?.firstOrNull()
                        ?.text
                        ?.toString()
                }?.let(SupportedBrowsers::parseHost) ?: return
        engine.onBrowserHost(host)?.let(::launchGate)
    }

    private fun launchGate(target: BlockTarget) {
        Timber.i("Blocker: gating %s", target.key)
        startActivity(GateActivity.getIntent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Checks the foreground window when the earliest unlock expires, so staying
     * inside an event-quiet app (fullscreen video) cannot outlast the unlock.
     */
    private fun rearmExpiryTimer() {
        handler.removeCallbacks(expiryCheck)
        val nextExpiryMs = UnlockStore.earliestActiveExpiry() ?: return
        val delayMs = (nextExpiryMs - TimeManager.time.intTimeMS() + EXPIRY_SLACK_MS).coerceAtLeast(0)
        handler.postDelayed(expiryCheck, delayMs)
    }

    private fun onExpiryCheck() {
        val foregroundPackage = rootInActiveWindow?.packageName?.toString()
        if (foregroundPackage != null) {
            engine.onForegroundApp(foregroundPackage)?.let(::launchGate)
            SupportedBrowsers.byPackage(foregroundPackage)?.let(::probeBrowserUrl)
        }
        rearmExpiryTimer()
    }

    /**
     * Restricts event delivery to the blocked apps (plus supported browsers when
     * website blocking is configured) — the main battery lever: the system then
     * never wakes this service for anything else. Content-change events (the
     * high-volume stream) are only requested when websites are actually blocked.
     * With nothing to watch, our own (ignored) package is used as a harmless
     * placeholder, since a null/empty filter would mean "all apps".
     */
    private fun applyServiceTuning() {
        val info = serviceInfo ?: return
        val blockedApps = BlockerPrefs.blockedApps
        val watchBrowsers = BlockerPrefs.isEnabled && BlockerPrefs.blockedDomains.isNotEmpty()
        info.eventTypes =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            (if (watchBrowsers) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
        val watched =
            buildSet {
                addAll(blockedApps)
                if (watchBrowsers) addAll(SupportedBrowsers.packageNames)
            }
        info.packageNames =
            if (watched.isEmpty()) {
                arrayOf(BuildConfig.APPLICATION_ID)
            } else {
                watched.toTypedArray()
            }
        serviceInfo = info
        Timber.i(
            "Blocker: accessibility filter set to %d apps (browser watching: %b)",
            watched.size,
            watchBrowsers,
        )
    }

    private fun watchedPreferenceKeys(): Set<String> =
        setOf(
            getString(R.string.blocker_enabled_key),
            getString(R.string.blocker_blocked_apps_key),
            getString(R.string.blocker_blocked_domains_key),
            getString(R.string.blocker_unlock_sessions_key),
        )

    private fun resolveDefaultLauncher(): String? =
        packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo
            ?.packageName

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val URL_PROBE_MIN_INTERVAL_MS = 500L
        private const val EXPIRY_SLACK_MS = 250L
        private const val BACK_AFTER_ABANDON_DELAY_MS = 300L
    }
}
