// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith

/** Test of [CardBrowserFragmentViewModel] */
@RunWith(AndroidJUnit4::class)
class CardBrowserFragmentViewModelTest : RobolectricTest() {
    @Test
    fun `openDeckSelectionDialog emits from flowOfSearchForDecks`() =
        runTest {
            val viewModel = CardBrowserFragmentViewModel()

            viewModel.flowOfSearchForDecks.test {
                viewModel.openDeckSelectionDialog()
                awaitItem()
            }
        }
}
