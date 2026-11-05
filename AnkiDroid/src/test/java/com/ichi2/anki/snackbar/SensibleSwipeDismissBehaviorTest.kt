// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.snackbar

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class SensibleSwipeDismissBehaviorTest {
    private val behavior =
        SensibleSwipeDismissBehavior().ViewDragHelperCallback().apply {
            initialChildLeft = 0
        }

    private val snackbar =
        mock(View::class.java).apply {
            whenever(width).thenReturn(100)
        }

    private fun positionSnackbarAtX(x: Int) {
        whenever(snackbar.left).thenReturn(x)
    }

    @Test fun `Stationary snackbar isn't dismissed if it hasn't traveled`() {
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `Stationary snackbar isn't dismissed if it hasn't traveled far`() {
        positionSnackbarAtX(-5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))

        positionSnackbarAtX(+5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `Stationary snackbar is dismissed if it has traveled far`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, 0f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, 0f))
    }

    @Test fun `slow snackbar near start is not dismissed`() {
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 100f))
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))

        positionSnackbarAtX(+5)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, 100f))
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))
    }

    @Test fun `far snackbar moving slowly back is not dismissed`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, -100f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.DoNotDismiss, behavior.shouldDismiss(snackbar, +100f))
    }

    @Test fun `far snackbar moving to closest edge is dismissed`() {
        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +100f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, -100f))
    }

    @Test fun `Moving snackbar is dismissed if it is moving fast`() {
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +1000000f))

        positionSnackbarAtX(+80)
        assertEquals(Dismiss.ToTheLeft, behavior.shouldDismiss(snackbar, -1000000f))

        positionSnackbarAtX(-80)
        assertEquals(Dismiss.ToTheRight, behavior.shouldDismiss(snackbar, +1000000f))
    }
}
