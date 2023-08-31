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
import com.wildplot.android.parsing.Expression
import com.wildplot.android.parsing.ExpressionFormatException
import com.wildplot.android.parsing.TopLevelParser
import com.wildplot.android.parsing.TreeElement
import java.util.regex.Pattern
import kotlin.Throws

/**
 * @author Michael Goldbach
 */
class FunctionXYAtom(funcString: String, private val parser: TopLevelParser) : TreeElement {
    var atomType = AtomType.FUNCTION_X
        private set
    private var expressionLeft: Expression? = null
    private var expressionRight: Expression? = null
    private var funcName: String? = null
    private fun init(funcString: String): Boolean {
        val leftBracket = funcString.indexOf("(")
        val rightBracket = funcString.lastIndexOf(")")
        var comma = funcString.indexOf(",") // see if there even is a comma
        if (leftBracket > 1 && rightBracket > leftBracket + 1 && comma > leftBracket && comma < rightBracket) {
            // test all commas
            for (i in leftBracket + 1 until rightBracket) {
                if (funcString[i] == ',') {
                    comma = i
                }
                val funcName = funcString.substring(0, leftBracket)
                val p = Pattern.compile("[^a-zA-Z0-9]")
                val hasSpecialChar = p.matcher(funcName).find()
                if (!hasSpecialChar && funcName.length > 0) {
                    val leftExpressionString = funcString.substring(leftBracket + 1, comma)
                    val rightExpressionString = funcString.substring(comma + 1, rightBracket)
                    val leftExpressionInBrackets = Expression(leftExpressionString, parser)
                    val rightExpressionInBrackets = Expression(rightExpressionString, parser)
                    val isValidLeftExpression =
                        leftExpressionInBrackets.expressionType != Expression.ExpressionType.INVALID
                    val isValidRightExpression =
                        rightExpressionInBrackets.expressionType != Expression.ExpressionType.INVALID
                    if (isValidLeftExpression && isValidRightExpression) {
                        atomType = AtomType.FUNCTION_X
                        this.funcName = funcName
                        expressionLeft = leftExpressionInBrackets
                        expressionRight = rightExpressionInBrackets
                        return true
                    }
                } else {
                    atomType = AtomType.INVALID
                    return false
                }
            }
        }
        return false
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = if (atomType !== AtomType.INVALID) {
            parser.getFuncVal(funcName!!, expressionLeft!!.value, expressionRight!!.value)
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = if (atomType !== AtomType.INVALID) {
            expressionLeft!!.isVariable || expressionRight!!.isVariable
        } else {
            throw ExpressionFormatException("Number is Invalid, cannot parse")
        }

    init {
        val isValid = init(funcString)
        if (!isValid) {
            atomType = AtomType.INVALID
        }
    }
}
