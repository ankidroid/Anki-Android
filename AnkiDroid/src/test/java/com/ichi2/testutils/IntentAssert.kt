/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.testutils

import android.content.Intent
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import kotlin.test.assertNotNull

@KotlinCleanup("`is` -> equalTo")
object IntentAssert {
    @JvmStatic
    fun doesNotHaveExtra(intent: Intent, extraKey: String?) {
        val keySet = assertNotNull(intent.extras).keySet()
        assertThat(String.format("Intent should not have extra '%s'", extraKey), keySet, not(hasItem(extraKey)))
    }

    @JvmStatic
    fun hasExtra(intent: Intent, extraKey: String?, value: Long) {
        val keySet = assertNotNull(intent.extras).keySet()
        assertThat(String.format("Intent should have extra '%s'", extraKey), keySet, hasItem(extraKey))

        assertThat(intent.getLongExtra(extraKey, -1337), `is`(value))
    }
}
