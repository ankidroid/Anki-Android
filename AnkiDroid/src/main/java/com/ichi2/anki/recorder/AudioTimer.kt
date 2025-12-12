/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.recorder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A timer utility for audio recording and playback
 *
 * It manages three independent update loops on the provided [kotlinx.coroutines.CoroutineScope]:
 * 1. **UI Timer (~16ms):** High-frequency updates for smooth text counters (e.g., 00:01.45).
 * 2. **Waveform (~50ms):** Medium-frequency updates for visualizers.
 * 3. **Notification (~1000ms):** Optional low-frequency updates for system notifications.
 *
 * @param scope A lifecycle-aware scope (e.g., `lifecycleScope`) which ensures timers are automatically cancelled when the UI is destroyed.
 * @param onTimerTick Lambda invoked every ~16ms with the precise [kotlin.time.Duration] elapsed.
 * @param onAudioTick Lambda invoked every ~50ms to trigger waveform visualization updates.
 * @param onNotificationTick Optional lambda invoked every 1 second. If null, this loop is not started.
 */
class AudioTimer(
    private val scope: CoroutineScope,
    private val onTimerTick: (Duration) -> Unit,
    private val onAudioTick: () -> Unit,
    private val onNotificationTick: ((Duration) -> Unit)? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var timerJob: Job? = null
    private var accumulatedDuration: Duration = Duration.ZERO
    private var sessionStartTime: TimeMark? = null

    fun start() {
        if (timerJob?.isActive == true) return

        sessionStartTime = timeSource.markNow()

        timerJob =
            scope.launch {
                // UI Tick
                launch {
                    while (isActive) {
                        onTimerTick(calculateDuration())
                        delay(16)
                    }
                }

                // Waveform Tick
                launch {
                    while (isActive) {
                        onAudioTick()
                        delay(50)
                    }
                }

                // Notification Tick
                onNotificationTick?.let { notificationCallback ->
                    launch {
                        while (isActive) {
                            // Delay first to avoid immediate double-fire on start
                            delay(1000)
                            if (isActive) notificationCallback(calculateDuration())
                        }
                    }
                }
            }
    }

    fun start(fromDuration: Duration) {
        timerJob?.cancel()
        timerJob = null

        accumulatedDuration = fromDuration
        sessionStartTime = null

        start()
    }

    fun pause() {
        accumulatedDuration = calculateDuration()
        timerJob?.cancel()
        timerJob = null
        sessionStartTime = null
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        accumulatedDuration = Duration.ZERO
        sessionStartTime = null
        onTimerTick(Duration.ZERO)
    }

    private fun calculateDuration(): Duration {
        // Total = (Saved Time) + (Time since start button pressed)
        val currentSession = sessionStartTime?.elapsedNow() ?: Duration.ZERO
        return accumulatedDuration + currentSession
    }
}
