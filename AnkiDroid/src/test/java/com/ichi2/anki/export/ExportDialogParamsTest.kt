/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.export

import com.ichi2.anki.dialogs.ExportDialogParams
import com.ichi2.anki.export.ExportType.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class ExportDialogParamsTest {
    @Test
    fun includeSchedulingDefaultValue() {
        assertIncludeScheduling(ExportDeck(1), false)
        assertIncludeScheduling(ExportCards(listOf(1, 2)), false)
        assertIncludeScheduling(ExportNotes(listOf(1, 2)), false)
        assertIncludeScheduling(ExportCollection, true)
    }

    private fun assertIncludeScheduling(
        type: ExportType,
        expected: Boolean,
    ) {
        val includeScheduling = ExportDialogParams(message = "", exportType = type).includeScheduling
        assertThat("${type.javaClass.simpleName}: includeScheduling", includeScheduling, equalTo(expected))
    }
}
