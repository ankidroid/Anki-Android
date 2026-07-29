// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

/**
 * The blocker's gating rules, free of Android framework types so they can be
 * unit-tested on the JVM. Decides, for a foreground app or a browser host,
 * whether a gate should be shown now.
 *
 * All state except the post-gate cooldown timestamp is read through the
 * injected lambdas.
 */
class BlockerEngine(
    private val config: () -> Config,
    private val isUnlocked: (BlockTarget, Long) -> Boolean,
    private val isGateActive: () -> Boolean,
    private val clock: () -> Long,
) {
    data class Config(
        val enabled: Boolean,
        val blockedApps: Set<String>,
        val blockedDomains: Set<String>,
        val ignoredPackages: Set<String>,
    )

    private var lastGateClosedMs = 0L

    /**
     * Starts the post-gate cooldown, so window events from the exit animation of
     * a just-closed gate can't immediately re-trigger it.
     */
    fun noteGateClosed() {
        lastGateClosedMs = clock()
    }

    /** @return the target to gate, or null when [packageName] may be used freely now */
    fun onForegroundApp(packageName: String): BlockTarget.App? {
        val config = config()
        if (!config.enabled) return null
        if (packageName in config.ignoredPackages) return null
        if (packageName !in config.blockedApps) return null
        return BlockTarget.App(packageName).takeIf(::shouldGate)
    }

    /**
     * @return the target to gate for a browser showing [host], or null when it
     *   may be visited freely now. The target carries the matched blocklist
     *   pattern (not the full host), so unlocking `x.com` covers `m.x.com` too.
     */
    fun onBrowserHost(host: String): BlockTarget.Domain? {
        val config = config()
        if (!config.enabled) return null
        val pattern =
            config.blockedDomains.firstOrNull { host == it || host.endsWith(".$it") }
                ?: return null
        return BlockTarget.Domain(pattern).takeIf(::shouldGate)
    }

    private fun shouldGate(target: BlockTarget): Boolean {
        if (isGateActive()) return false
        val now = clock()
        if (now - lastGateClosedMs < GATE_COOLDOWN_MS) return false
        return !isUnlocked(target, now)
    }

    companion object {
        const val GATE_COOLDOWN_MS = 1500L
    }
}
