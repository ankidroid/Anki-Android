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

package com.ichi2.anki.compat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.compat.CompatHelper
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CompatNormalizeTest : InstrumentedTest() {
    @Test
    fun normalize() {
        fun assertEqual(l: Locale, str: String) {
            val normalized = CompatHelper.compat.normalize(l)
            assertThat(normalized.toLanguageTag(), equalTo(str))
        }

        assertEqual(Locale("en", "GB"), "en-GB")
        assertEqual(Locale("es", "MX"), "es-MX")
        assertEqual(Locale("spa", "MEX"), "es-MX")
        assertEqual(Locale("fil", "PH"), "fil-PH")
        // TBC
        assertEqual(Locale("ar", ""), "ar")
    }
}
