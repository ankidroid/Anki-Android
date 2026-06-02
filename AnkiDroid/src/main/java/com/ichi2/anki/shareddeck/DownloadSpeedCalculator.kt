// SPDX-FileCopyrightText: 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

/**
 * Calculates download speed using Exponential Moving Average (EMA) to smooth out fluctuations.
 *
 * @param alpha The smoothing factor. A value between 0 and 1. Higher values give more weight to
 * current measurements. Default is 0.5 (averaging current and last speed).
 */
class DownloadSpeedCalculator(
    private val alpha: Double = 0.5,
) {
    init {
        require(alpha in 0.0..1.0) { "Alpha must be between 0.0 and 1.0" }
    }

    private var lastSmoothedSpeed: Double = 0.0
    private var lastBytesDownloaded: Long = 0
    private var lastTimeChecked: Long? = null

    /**
     * Updates the calculation with new data points.
     *
     * @param downloadedBytes Total bytes downloaded so far.
     * @param currentTime Current time in milliseconds.
     * @return The smoothed download speed in bytes per second.
     */
    fun update(
        downloadedBytes: Long,
        currentTime: Long,
    ): Double {
        val lastTime = lastTimeChecked
        if (lastTime != null && currentTime > lastTime) {
            val timeDiff = currentTime - lastTime
            val bytesDiff = downloadedBytes - lastBytesDownloaded

            if (bytesDiff >= 0) {
                val instantSpeed = (bytesDiff * 1000.0) / timeDiff
                lastSmoothedSpeed =
                    if (lastSmoothedSpeed == 0.0 && instantSpeed > 0) {
                        // Initialize with the first non-zero speed to avoid a slow ramp up from 0.
                        instantSpeed
                    } else {
                        (lastSmoothedSpeed * (1 - alpha)) + (instantSpeed * alpha)
                    }
            }
        }
        lastBytesDownloaded = downloadedBytes
        lastTimeChecked = currentTime
        return lastSmoothedSpeed
    }

    /**
     * Resets the internal state of the calculator.
     */
    fun reset() {
        lastSmoothedSpeed = 0.0
        lastBytesDownloaded = 0
        lastTimeChecked = null
    }
}
