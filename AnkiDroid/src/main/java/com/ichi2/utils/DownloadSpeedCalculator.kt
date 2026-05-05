/*
 * Copyright (c) 2026 Colby Cabrera <colbycabrera.wd@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils

import androidx.annotation.VisibleForTesting

/**
 * Calculates download speed using Exponential Moving Average (EMA) to smooth out fluctuations.
 *
 * @param alpha The smoothing factor. A value between 0 and 1. Higher values give more weight to
 * current measurements. Default is 0.5 (averaging current and last speed).
 */
class DownloadSpeedCalculator(
    private val alpha: Double = 0.5,
) {
    private var lastSmoothedSpeed: Double = 0.0
    private var lastBytesDownloaded: Long = 0
    private var lastTimeChecked: Long = 0

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
        if (lastTimeChecked in 1..<currentTime) {
            val timeDiff = currentTime - lastTimeChecked
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
    @VisibleForTesting
    fun reset() {
        lastSmoothedSpeed = 0.0
        lastBytesDownloaded = 0
        lastTimeChecked = 0
    }
}
