// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ichi2.anki.R

@Composable
internal fun rememberDownloadSizeRange(
    downloadedBytes: Long,
    totalBytes: Long,
): String {
    if (totalBytes <= 0 && downloadedBytes <= 0) return ""

    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val downloadedFmt =
        remember(downloadedBytes, configuration) {
            Formatter.formatFileSize(context, downloadedBytes)
        }

    if (totalBytes <= 0) return downloadedFmt

    val totalFmt =
        remember(totalBytes, configuration) {
            Formatter.formatFileSize(context, totalBytes)
        }
    return stringResource(R.string.progress_amount_bytes, downloadedFmt, totalFmt)
}
