// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

import com.ichi2.anki.common.time.TimeManager
import org.json.JSONObject
import timber.log.Timber

/**
 * Active unlock sessions: for each unlocked [BlockTarget], the epoch-millisecond
 * timestamp at which the unlock expires.
 *
 * State is cached in memory and written through to preferences on every change,
 * so it survives process death, accessibility-service rebinds and reboots.
 */
object UnlockStore {
    private var cache: MutableMap<String, Long>? = null

    @Synchronized
    fun grant(
        target: BlockTarget,
        durationMs: Long,
        nowMs: Long = TimeManager.time.intTimeMS(),
    ) {
        val sessions = load()
        sessions[target.key] = nowMs + durationMs
        prune(sessions, nowMs)
        persist(sessions)
    }

    @Synchronized
    fun isUnlocked(
        target: BlockTarget,
        nowMs: Long = TimeManager.time.intTimeMS(),
    ): Boolean = (load()[target.key] ?: 0L) > nowMs

    /** The soonest moment an active unlock expires, or null when nothing is unlocked. */
    @Synchronized
    fun earliestActiveExpiry(nowMs: Long = TimeManager.time.intTimeMS()): Long? = load().values.filter { it > nowMs }.minOrNull()

    @Synchronized
    fun revokeAll() {
        cache = mutableMapOf()
        persist(emptyMap())
    }

    private fun load(): MutableMap<String, Long> {
        cache?.let { return it }
        val sessions = mutableMapOf<String, Long>()
        val json = BlockerPrefs.unlockSessionsJson
        if (json != null) {
            try {
                val obj = JSONObject(json)
                for (key in obj.keys()) {
                    sessions[key] = obj.getLong(key)
                }
            } catch (e: Exception) {
                Timber.w(e, "Blocker: discarding malformed unlock sessions")
            }
        }
        cache = sessions
        return sessions
    }

    private fun prune(
        sessions: MutableMap<String, Long>,
        nowMs: Long,
    ) {
        sessions.entries.removeAll { it.value <= nowMs }
    }

    private fun persist(sessions: Map<String, Long>) {
        BlockerPrefs.unlockSessionsJson = JSONObject(sessions as Map<*, *>).toString()
    }
}
