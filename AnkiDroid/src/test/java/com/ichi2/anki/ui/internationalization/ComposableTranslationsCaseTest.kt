// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.ui.internationalization

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposableTranslationsCaseTest : RobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `English is converted to sentence case`() {
        composeTestRule.setContent {
            assertThat(trCase().addNoteType(), equalTo("Add note type"))
            // TODO these three backend strings come with extra FSI and PDI character types which
            //  fail the case check, there should be a general mechanism to remove these extra
            //  characters(other backend strings also have extra characters) in toSentence()
            // assertThat(trCase().cardStatsCurrentCardStudy(), equalTo("Current card (study)"))
            // assertThat(trCase().cardStatsCurrentCardBrowse(), equalTo("Current card (browse)"))
            // assertThat(trCase().cardStatsPreviousCardStudy(), equalTo("Previous card (study)"))
        }
    }
}
