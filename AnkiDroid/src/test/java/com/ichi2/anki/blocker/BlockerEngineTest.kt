// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Test

class BlockerEngineTest {
    private var enabled = true
    private var blockedApps = setOf(INSTAGRAM)
    private var blockedDomains = setOf("x.com")
    private var ignored = setOf(OWN_APP, LAUNCHER)
    private var unlockedTargets = mutableSetOf<String>()
    private var gateActive = false
    private var nowMs = 100_000L

    private val engine =
        BlockerEngine(
            config = {
                BlockerEngine.Config(
                    enabled = enabled,
                    blockedApps = blockedApps,
                    blockedDomains = blockedDomains,
                    ignoredPackages = ignored,
                )
            },
            isUnlocked = { target, _ -> target.key in unlockedTargets },
            isGateActive = { gateActive },
            clock = { nowMs },
        )

    @Test
    fun `blocked locked app is gated`() {
        assertThat(engine.onForegroundApp(INSTAGRAM), equalTo(BlockTarget.App(INSTAGRAM)))
    }

    @Test
    fun `nothing is gated when disabled`() {
        enabled = false
        assertThat(engine.onForegroundApp(INSTAGRAM), nullValue())
        assertThat(engine.onBrowserHost("x.com"), nullValue())
    }

    @Test
    fun `unblocked app is not gated`() {
        assertThat(engine.onForegroundApp("com.other.app"), nullValue())
    }

    @Test
    fun `ignored packages are never gated even if blocklisted`() {
        blockedApps = setOf(OWN_APP, LAUNCHER)
        assertThat(engine.onForegroundApp(OWN_APP), nullValue())
        assertThat(engine.onForegroundApp(LAUNCHER), nullValue())
    }

    @Test
    fun `unlocked app is not gated until expiry`() {
        unlockedTargets += BlockTarget.App(INSTAGRAM).key
        assertThat(engine.onForegroundApp(INSTAGRAM), nullValue())
        unlockedTargets.clear()
        assertThat(engine.onForegroundApp(INSTAGRAM), notNullValue())
    }

    @Test
    fun `no gate while another gate is active`() {
        gateActive = true
        assertThat(engine.onForegroundApp(INSTAGRAM), nullValue())
    }

    @Test
    fun `cooldown suppresses gating right after a gate closes`() {
        engine.noteGateClosed()
        assertThat(engine.onForegroundApp(INSTAGRAM), nullValue())
        nowMs += BlockerEngine.GATE_COOLDOWN_MS - 1
        assertThat(engine.onForegroundApp(INSTAGRAM), nullValue())
        nowMs += 1
        assertThat(engine.onForegroundApp(INSTAGRAM), notNullValue())
    }

    @Test
    fun `domain matching covers exact host and subdomains only`() {
        assertThat(engine.onBrowserHost("x.com"), equalTo(BlockTarget.Domain("x.com")))
        assertThat(engine.onBrowserHost("mobile.x.com"), equalTo(BlockTarget.Domain("x.com")))
        assertThat(engine.onBrowserHost("notx.com"), nullValue())
        assertThat(engine.onBrowserHost("x.com.evil.org"), nullValue())
    }

    @Test
    fun `domain target carries the matched pattern so the unlock covers subdomains`() {
        unlockedTargets += BlockTarget.Domain("x.com").key
        assertThat(engine.onBrowserHost("mobile.x.com"), nullValue())
    }

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val OWN_APP = "com.ichi2.anki.debug"
        private const val LAUNCHER = "com.launcher.home"
    }
}
