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

import com.wildplot.android.parsing.Atom.AtomType
import com.wildplot.android.parsing.ExpressionFormatException
import com.wildplot.android.parsing.TopLevelParser
import com.wildplot.android.parsing.TreeElement
import java.util.regex.Pattern

class VariableAtom(private val varName: String, private val parser: TopLevelParser) : TreeElement {
    var atomType = AtomType.NUMBER

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = if (atomType !== AtomType.INVALID) {
            parser.getVarVal(varName)
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }

    override val isVariable: Boolean
        get() = true

    init {
        val p = Pattern.compile("[^a-zA-Z0-9]")
        val hasSpecialChar = p.matcher(varName).find()
        if (hasSpecialChar || varName.length <= 0) {
            atomType = AtomType.INVALID
        }
    }
}
