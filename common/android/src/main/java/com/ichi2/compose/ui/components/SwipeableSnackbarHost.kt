// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** A snackbar host that also supports dismissing messages with a swipe in either direction. */
@Composable
fun SwipeableSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val touch = remember(hostState.currentSnackbarData) { mutableStateOf<SnackbarTouch?>(null) }
    SnackbarHost(hostState, modifier, touch = touch) { data ->
        key(data) {
            SwipeToDismissBox(
                // Keep the transparent margin outside the gesture handlers.
                modifier =
                    Modifier.padding(12.dp).pointerInput(data, touch) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val press = SnackbarTouch(pressed = true)
                            try {
                                if (hostState.currentSnackbarData === data) touch.value = press
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                if (touch.value === press) touch.value = SnackbarTouch(pressed = false)
                            }
                        }
                    },
                state = rememberSwipeToDismissBoxState(),
                backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) {
                AnkiDroidSnackbar(data)
            }
        }
    }
}
