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

import com.ichi2.utils.HashUtil.HashMapInit
import com.wildplot.android.rendering.interfaces.Function2D
import com.wildplot.android.rendering.interfaces.Function3D
import java.util.regex.Pattern

class TopLevelParser(expressionString: String, parserRegister: HashMap<String, TopLevelParser>) :
    Function2D, Function3D, Cloneable {
    private val parserRegister: HashMap<String, TopLevelParser>
    private val varMap = HashMapInit<String, Double>(2) // Number form initVarMap
    var x = 0.0
    var y = 0.0
    private val expression: Expression
    val isValid: Boolean
    private var expressionString: String
    private var xName = "x"
    private var yName = "y"

    init {
        initVarMap()
        this.parserRegister = parserRegister
        this.expressionString = expressionString
        val isValidExpressionString = initExpressionString()
        expression = Expression(this.expressionString, this)
        isValid =
            expression.expressionType != Expression.ExpressionType.INVALID && isValidExpressionString
    }

    private fun initVarMap() {
        varMap["e"] = Math.E
        varMap["pi"] = Math.PI
    }

    private fun initExpressionString(): Boolean {
        expressionString = expressionString.replace(" ", "")
        val equalPosition = expressionString.indexOf("=")
        if (equalPosition >= 1) {
            val leftStatement = expressionString.substring(0, equalPosition)
            expressionString = expressionString.substring(equalPosition + 1)
            val commaPos = leftStatement.indexOf(",")
            val leftBracketPos = leftStatement.indexOf("(")
            val rightBracketPos = leftStatement.indexOf(")")
            if (leftBracketPos > 0 && rightBracketPos > leftBracketPos + 1) {
                val funcName = leftStatement.substring(0, leftBracketPos)
                val p = Pattern.compile("[^a-zA-Z0-9]")
                var hasSpecialChar = p.matcher(funcName).find()
                if (hasSpecialChar) {
                    return false
                }
                if (commaPos == -1) {
                    val xVarName = leftStatement.substring(leftBracketPos + 1, rightBracketPos)
                    hasSpecialChar = p.matcher(xVarName).find()
                    if (hasSpecialChar) {
                        return false
                    }
                    xName = xVarName
                } else {
                    val xVarName = leftStatement.substring(leftBracketPos + 1, commaPos)
                    hasSpecialChar = p.matcher(xVarName).find()
                    if (hasSpecialChar) {
                        return false
                    }
                    val yVarName = leftStatement.substring(commaPos + 1, rightBracketPos)
                    hasSpecialChar = p.matcher(yVarName).find()
                    if (hasSpecialChar) {
                        return false
                    }
                    xName = xVarName
                    yName = yVarName
                }
            } else {
                return false
            }
        }
        return true
    }

    fun getVarVal(varName: String): Double {
        return varMap[varName]!!
    }

    override fun f(x: Double): Double {
        this.x = x
        return if (isValid) {
            expression.value
        } else {
            throw ExpressionFormatException("illegal Expression, cannot parse and return value")
        }
    }

    override fun f(x: Double, y: Double): Double {
        this.x = x
        this.y = y
        return if (isValid) {
            expression.value
        } else {
            throw ExpressionFormatException("illegal Expression, cannot parse and return value")
        }
    }

    fun getFuncVal(funcName: String, xVal: Double): Double {
        val funcParser = parserRegister[funcName]
        return funcParser!!.f(xVal)
    }

    fun getFuncVal(funcName: String, xVal: Double, yVal: Double): Double {
        val funcParser = parserRegister[funcName]
        return funcParser!!.f(xVal, yVal)
    }

    fun getxName(): String {
        return xName
    }

    fun getyName(): String {
        return yName
    }

    companion object {
        fun stringHasValidBrackets(string: String): Boolean {
            val finalBracketCheck = string.replace("\\(".toRegex(), "").length - string.replace(
                "\\)".toRegex(),
                ""
            ).length
            if (finalBracketCheck != 0) {
                return false
            }
            var bracketOpeningCheck = 0
            for (i in 0 until string.length) {
                if (string[i] == '(') {
                    bracketOpeningCheck++
                }
                if (string[i] == ')') {
                    bracketOpeningCheck--
                }
                if (bracketOpeningCheck < 0) {
                    return false
                }
            }
            return true
        }
    }
}
