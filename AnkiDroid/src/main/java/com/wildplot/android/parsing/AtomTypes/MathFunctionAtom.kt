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
@file:Suppress("PackageName") // AtomTypes: copied from wildplot library

package com.wildplot.android.parsing.AtomTypes

import com.wildplot.android.parsing.Expression
import com.wildplot.android.parsing.ExpressionFormatException
import com.wildplot.android.parsing.TopLevelParser
import com.wildplot.android.parsing.TreeElement
import kotlin.Throws
import kotlin.math.*

class MathFunctionAtom(funcString: String, private val parser: TopLevelParser) : TreeElement {
    enum class MathType {
        SIN, COS, TAN, SQRT, ACOS, ASIN, ATAN, SINH, COSH, LOG, LN, INVALID
    }

    var mathType = MathType.INVALID
        private set
    private var expression: Expression? = null
    private var hasSavedValue = false
    private var savedValue = 0.0
    private fun init(funcString: String): Boolean {
        val leftBracket = funcString.indexOf("(")
        val rightBracket = funcString.lastIndexOf(")")
        if (leftBracket > 1 && rightBracket > leftBracket + 1) {
            val funcName = funcString.substring(0, leftBracket)
            val expressionString = funcString.substring(leftBracket + 1, rightBracket)
            val expressionInBrackets = Expression(expressionString, parser)
            val isValidExpression =
                expressionInBrackets.expressionType != Expression.ExpressionType.INVALID
            if (isValidExpression) {
                when (funcName) {
                    "sin" -> mathType = MathType.SIN
                    "cos" -> mathType = MathType.COS
                    "tan" -> mathType = MathType.TAN
                    "sqrt" -> mathType = MathType.SQRT
                    "acos" -> mathType = MathType.ACOS
                    "asin" -> mathType = MathType.ASIN
                    "atan" -> mathType = MathType.ATAN
                    "sinh" -> mathType = MathType.SINH
                    "cosh" -> mathType = MathType.COSH
                    "log", "lg" -> mathType = MathType.LOG
                    "ln" -> mathType = MathType.LN
                    else -> {
                        mathType = MathType.INVALID
                        return false
                    }
                }
                expression = expressionInBrackets
                return true
            }
        }
        return false
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = if (hasSavedValue) {
            savedValue
        } else {
            when (mathType) {
                MathType.SIN -> sin(expression!!.value)
                MathType.COS -> cos(expression!!.value)
                MathType.TAN -> tan(expression!!.value)
                MathType.SQRT -> sqrt(expression!!.value)
                MathType.ACOS -> acos(expression!!.value)
                MathType.ASIN -> asin(expression!!.value)
                MathType.ATAN -> atan(expression!!.value)
                MathType.SINH -> sinh(expression!!.value)
                MathType.COSH -> cosh(expression!!.value)
                MathType.LOG -> log10(expression!!.value)
                MathType.LN -> ln(expression!!.value)
                MathType.INVALID -> throw ExpressionFormatException("Number is Invalid, cannot parse")
            }
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = if (mathType != MathType.INVALID) {
            expression!!.isVariable
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }

    init {
        val isValid = init(funcString)
        if (!isValid) {
            mathType = MathType.INVALID
        }
        if (isValid && !isVariable) {
            savedValue = value
            hasSavedValue = true
        }
    }
}
