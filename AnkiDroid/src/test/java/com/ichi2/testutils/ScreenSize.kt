/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import org.robolectric.RuntimeEnvironment
import timber.log.Timber

/** [block] runs with an `xlarge` Runtime qualifier */
fun withTabletUi(block: () -> Unit) = withQualifier("xlarge", block)

fun withQualifier(
    newQualifier: String,
    block: () -> Unit,
) {
    val qualifiers = RuntimeEnvironment.getQualifiers()
    try {
        Timber.d("Adding '$newQualifier' to qualifiers $qualifiers")
        RuntimeEnvironment.setQualifiers("+$newQualifier")
        block()
    } finally {
        Timber.d("Resetting qualifiers to $qualifiers")
        RuntimeEnvironment.setQualifiers(qualifiers)
    }
}
