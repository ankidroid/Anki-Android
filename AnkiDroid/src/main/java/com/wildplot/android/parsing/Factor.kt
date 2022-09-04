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

class Factor(factorString: String, private val parser: TopLevelParser) : TreeElement {
    enum class FactorType {
        PLUS_FACTOR, MINUS_FACTOR, POW, INVALID
    }

    var factorType = FactorType.INVALID
        private set
    private var factor: Factor? = null
    private var pow: Pow? = null
    private fun initAsPlusFactor(factorString: String): Boolean {
        if (factorString.length > 0 && factorString[0] == '+') {
            val leftSubString = factorString.substring(1)
            val leftFactor = Factor(leftSubString, parser)
            val isValidFactor = leftFactor.factorType != FactorType.INVALID
            if (isValidFactor) {
                factorType = FactorType.PLUS_FACTOR
                factor = leftFactor
                return true
            }
        }
        return false
    }

    private fun initAsMinusFactor(factorString: String): Boolean {
        if (factorString.length > 0 && factorString[0] == '-') {
            val leftSubString = factorString.substring(1)
            val leftFactor = Factor(leftSubString, parser)
            val isValidFactor = leftFactor.factorType != FactorType.INVALID
            if (isValidFactor) {
                factorType = FactorType.MINUS_FACTOR
                factor = leftFactor
                return true
            }
        }
        return false
    }

    private fun initAsPow(factorString: String): Boolean {
        val pow = Pow(factorString, parser)
        val isValidPow = pow.powType !== Pow.PowType.INVALID
        if (isValidPow) {
            factorType = FactorType.POW
            this.pow = pow
            return true
        }
        return false
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = when (factorType) {
            FactorType.PLUS_FACTOR -> factor!!.value
            FactorType.MINUS_FACTOR -> -factor!!.value
            FactorType.POW -> pow!!.value
            FactorType.INVALID -> throw ExpressionFormatException("cannot parse expression at factor level")
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = when (factorType) {
            FactorType.PLUS_FACTOR, FactorType.MINUS_FACTOR -> factor!!.isVariable
            FactorType.POW -> pow!!.isVariable
            FactorType.INVALID -> throw ExpressionFormatException("cannot parse expression at factor level")
        }

    init {
        if (!TopLevelParser.stringHasValidBrackets(factorString)) {
            factorType = FactorType.INVALID
        } else {
            var isReady = initAsPlusFactor(factorString)
            if (!isReady) {
                isReady = initAsMinusFactor(factorString)
            }
            if (!isReady) {
                isReady = initAsPow(factorString)
            }
            if (!isReady) {
                factorType = FactorType.INVALID
            }
        }
    }
}
