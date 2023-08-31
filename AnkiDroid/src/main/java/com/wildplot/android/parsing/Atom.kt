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

package com.wildplot.android.parsing

import com.wildplot.android.parsing.Atom.AtomType.*
import com.wildplot.android.parsing.AtomTypes.*

class Atom(private val parser: TopLevelParser) : TreeElement {
    var atomType: AtomType = INVALID
    private lateinit var atomObject: TreeElement
    private lateinit var expression: Expression

    enum class AtomType {
        VARIABLE, NUMBER, EXP_IN_BRACKETS, FUNCTION_MATH, FUNCTION_X, FUNCTION_X_Y, INVALID
    }

    constructor(atomString: String, parser: TopLevelParser) : this(parser) {
        val isValid: Boolean = TopLevelParser.stringHasValidBrackets(atomString) &&
            (
                initAsExpInBrackets(atomString) ||
                    initAsFunctionMath(atomString) ||
                    initAsFunctionX(atomString) ||
                    initAsFunctionXY(atomString) ||
                    initAsNumber(atomString) ||
                    initAsXVariable(atomString) ||
                    initAsYVariable(atomString) ||
                    initAsVariable(atomString)
                )
        if (!isValid) {
            atomType = INVALID
        }
    }

    private fun initAsExpInBrackets(atomString: String): Boolean {
        return if (atomString.isNotEmpty() && atomString.startsWith('(') && atomString.endsWith(')')) {
            val expressionString = atomString.substring(1, atomString.length - 1)
            val expressionInBrackets = Expression(expressionString, parser)
            if (expressionInBrackets.expressionType != Expression.ExpressionType.INVALID) {
                expression = expressionInBrackets
                atomType = EXP_IN_BRACKETS
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    private fun initAsFunctionMath(atomString: String): Boolean {
        val mathFunctionAtom = MathFunctionAtom(atomString, parser)
        return if (mathFunctionAtom.mathType != MathFunctionAtom.MathType.INVALID) {
            atomType = FUNCTION_MATH
            atomObject = mathFunctionAtom
            true
        } else {
            false
        }
    }

    private fun initAsFunctionX(atomString: String): Boolean {
        val functionXAtom = FunctionXAtom(atomString, parser)
        return if (functionXAtom.atomType != INVALID) {
            atomType = FUNCTION_X
            atomObject = functionXAtom
            true
        } else {
            false
        }
    }

    private fun initAsFunctionXY(atomString: String): Boolean {
        val functionXYAtom = FunctionXYAtom(atomString, parser)
        return if (functionXYAtom.atomType != INVALID) {
            atomType = FUNCTION_X_Y
            atomObject = functionXYAtom
            true
        } else {
            false
        }
    }

    private fun initAsNumber(atomString: String): Boolean {
        val numberAtom = NumberAtom(atomString)
        return if (numberAtom.getAtomType() !== INVALID) {
            atomType = numberAtom.getAtomType()
            atomObject = numberAtom
            true
        } else {
            false
        }
    }

    private fun initAsXVariable(atomString: String): Boolean {
        return if (atomString == parser.getxName()) {
            atomType = VARIABLE
            atomObject = XVariableAtom(parser)
            true
        } else {
            false
        }
    }

    private fun initAsYVariable(atomString: String): Boolean {
        return if (atomString == parser.getyName()) {
            atomType = VARIABLE
            atomObject = YVariableAtom(parser)
            true
        } else {
            false
        }
    }

    private fun initAsVariable(atomString: String): Boolean {
        val variableAtom = VariableAtom(atomString, parser)
        return if (variableAtom.atomType !== INVALID) {
            atomType = variableAtom.atomType
            atomObject = variableAtom
            true
        } else {
            false
        }
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = when (atomType) {
            EXP_IN_BRACKETS -> expression.value
            VARIABLE, NUMBER, FUNCTION_MATH, FUNCTION_X, FUNCTION_X_Y -> atomObject.value
            INVALID -> throw ExpressionFormatException("Cannot parse Atom object")
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = when (atomType) {
            EXP_IN_BRACKETS -> expression.isVariable
            VARIABLE, NUMBER, FUNCTION_MATH, FUNCTION_X, FUNCTION_X_Y -> atomObject.isVariable
            INVALID -> throw ExpressionFormatException("Cannot parse Atom object")
        }
}
