/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class StorageRustTest : JvmTest() {

    @Test
    @Config(qualifiers = "en")
    fun testModelCount() {
        val modelNames = col.notetypes.all().map { x -> x.getString("name") }
        MatcherAssert.assertThat(
            modelNames,
            Matchers.containsInAnyOrder(
                "Basic",
                "Basic (and reversed card)",
                "Cloze",
                "Basic (type in the answer)",
                "Basic (optional reversed card)",
                "Image Occlusion"
            )
        )
    }
}
