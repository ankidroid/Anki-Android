/****************************************************************************************
 * Copyright (c) 2014 Michael Goldbach <michael@wildplot.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.wildplot.android.parsing.AtomTypes

import com.ichi2.utils.KotlinCleanup
import com.wildplot.android.parsing.Atom.AtomType
import com.wildplot.android.parsing.ExpressionFormatException
import com.wildplot.android.parsing.TreeElement
import timber.log.Timber
import java.lang.NumberFormatException
import kotlin.Throws

class NumberAtom(factorString: String) : TreeElement {

    private var atomType = AtomType.NUMBER
    private var valueField: Double? = null

    init {
        try {
            valueField = factorString.toDouble()
        } catch (e: NumberFormatException) {
            Timber.w(e)
            atomType = AtomType.INVALID
        }
    }

    @KotlinCleanup("Make atomType val with private setter.")
    fun getAtomType(): AtomType {
        return atomType
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = if (atomType != AtomType.INVALID) {
            valueField!!
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = if (atomType != AtomType.INVALID) {
            false
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }
}
