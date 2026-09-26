// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.ContextMenuOption
import com.ichi2.anki.libanki.Consts
import com.ichi2.testutils.launchFragment
import com.ichi2.testutils.uninitializeField
import org.junit.Test

class CustomStudyDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun extendNewLimit() =
        withSubDialog(ContextMenuOption.EXTEND_NEW) {
            captureScreen("extend_new_limit")
        }

    @Test
    fun extendReviewLimit() =
        withSubDialog(ContextMenuOption.EXTEND_REV) {
            captureScreen("extend_review_limit")
        }

    private fun withSubDialog(
        option: ContextMenuOption,
        action: () -> Unit,
    ) {
        addNoteToDeck(Consts.DEFAULT_DECK_ID, count = 5)

        uninitializeField<CustomStudyDialog>("deferredDefaults")
        launchFragment<CustomStudyDialog>(CustomStudyDialog.createInstance(Consts.DEFAULT_DECK_ID).arguments).use {
            advanceRobolectricLooper()
        }

        val subDialogArgs = CustomStudyDialog.createSubDialog(Consts.DEFAULT_DECK_ID, option).arguments
        launchFragment<CustomStudyDialog>(subDialogArgs).use {
            advanceRobolectricLooper()
            action()
        }
    }
}
