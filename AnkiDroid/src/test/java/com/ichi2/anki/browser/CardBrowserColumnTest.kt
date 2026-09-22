// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import timber.log.Timber
import kotlin.test.assertNotNull

/** @see CardBrowserColumn */
@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserColumnTest : JvmTest() {
    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic // required for initParameters
        fun initParameters(): Collection<Array<Any>> =
            CardBrowserColumn.entries
                .map { arrayOf(it) }
    }

    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField // required for Parameter
    var columnParam: CardBrowserColumn? = null
    private val column get() = columnParam!!

    @Test
    fun ensureAllColumnsMapped() {
        val collectionColumns = col.backend.allBrowserColumns()
        Timber.w("%s", collectionColumns.joinToString { it.key })
        assertNotNull(collectionColumns.find(column))
    }
}
