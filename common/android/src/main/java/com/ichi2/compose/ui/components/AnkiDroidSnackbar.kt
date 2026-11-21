// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable

@Composable
internal fun AnkiDroidSnackbar(data: SnackbarData) {
    Snackbar(data)
}
