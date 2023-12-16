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

class MutableTime : MockTime {
    private var mFrozen = false

    constructor(time: Long) : super(time)
    constructor(time: Long, step: Int) : super(time, step)
    constructor(
        year: Int,
        month: Int,
        date: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int,
        milliseconds: Int,
        step: Int,
    ) : super(year, month, date, hourOfDay, minute, second, milliseconds, step)

    fun getInternalTimeMs() = time

    fun setFrozen(value: Boolean) {
        mFrozen = value
    }

    override fun intTimeMS(): Long {
        return if (mFrozen) {
            super.time
        } else {
            super.intTimeMS()
        }
    }
}
