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

package com.ichi2.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.AnkiActivity

/**
 * A function which returns a [FragmentManager].
 * This is an adapter, accepting either be a [Fragment] or an [AnkiActivity], even though they share
 * no interfaces in common for fragment managers
 */
@FunctionalInterface
fun interface FragmentManagerSupplier {
    fun getFragmentManager(): FragmentManager
}

fun Fragment.asFragmentManagerSupplier() = FragmentManagerSupplier { this.childFragmentManager }

fun AnkiActivity.asFragmentManagerSupplier() = FragmentManagerSupplier { this.supportFragmentManager }
