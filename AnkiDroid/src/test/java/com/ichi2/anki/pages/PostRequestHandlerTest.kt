// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 voczi <dev@voczi.com>

package com.ichi2.anki.pages

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStreamReader
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class PostRequestHandlerTest : RobolectricTest() {
    @Test
    fun `All backend typescript functions should be handled`() {
        // TODO: Reset this after Anki 26.09 is merged
        assertThat(
            "Mapping exists for every TS backend function call",
            typescriptFunctionsUsedByBackend - (collectionMethods + uiMethods).keys,
            empty(),
        )
    }

    @Test
    fun `saveCustomColours does not throw`() =
        runTest {
            // saveCustomColours is a FrontendService which is not implemented, but should not throw
            assertNotNull(handleCollectionPostRequest("saveCustomColours", byteArrayOf()))
        }

    /**
     * Auto-generated list of all typescript funcs created & packaged during backend build
     */
    private val typescriptFunctionsUsedByBackend: List<String> =
        InputStreamReader(targetContext.assets.open("backend/ts_funcs.txt")).use {
            it.readLines().also { lines ->
                assertThat("Stored Typescript functions", lines, not(empty()))
            }
        }
}
