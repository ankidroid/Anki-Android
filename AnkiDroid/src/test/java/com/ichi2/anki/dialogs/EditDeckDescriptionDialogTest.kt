// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.res.Configuration
import android.graphics.Rect
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.Companion.ARG_DECK_ID
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.ichi2.anki.common.android.R as CommonR

/**
 * Tests [EditDeckDescriptionDialog]
 */
@RunWith(AndroidJUnit4::class)
class EditDeckDescriptionDialogTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun `rotation uses the background insets for the new screen height`() {
        // DeckPicker handles rotation itself, so the dialog is not recreated.
        // Material insets the dialog by 80dp vertically on screens at least 480dp tall, else 10dp
        runDialogScenario { dialog ->
            val portrait = Configuration(dialog.resources.configuration)
            val landscape =
                Configuration(portrait).apply {
                    screenWidthDp = 914
                    screenHeightDp = 411
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            assertThat("portrait", dialog.verticalInset, equalTo(80.dp.toPx(dialog.requireContext())))

            dialog.changeConfiguration(landscape)
            assertThat("landscape", dialog.verticalInset, equalTo(10.dp.toPx(dialog.requireContext())))

            dialog.changeConfiguration(portrait)
            assertThat("portrait again", dialog.verticalInset, equalTo(80.dp.toPx(dialog.requireContext())))
        }
    }

    /** Updates the resources, as the dialog reads them, then notifies it */
    private fun EditDeckDescriptionDialog.changeConfiguration(configuration: Configuration) {
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        onConfigurationChanged(configuration)
    }

    private val EditDeckDescriptionDialog.verticalInset: Int
        get() {
            val padding = Rect()
            requireDialog()
                .window!!
                .decorView.background
                .getPadding(padding)
            return padding.top
        }

    private fun runDialogScenario(block: (EditDeckDescriptionDialog) -> Unit) {
        FragmentScenario
            .launch(
                EditDeckDescriptionDialog::class.java,
                bundleOf(ARG_DECK_ID to DEFAULT_DECK_ID),
                CommonR.style.Theme_Light,
            ).use { scenario ->
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.onFragment(block)
            }
    }
}
