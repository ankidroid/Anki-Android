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

class Term(termString: String, private val parser: TopLevelParser) : TreeElement {
    enum class TermType {
        TERM_MUL_FACTOR, TERM_DIV_FACTOR, FACTOR, INVALID
    }

    var termType = TermType.INVALID
        private set
    private var factor: Factor? = null
    private var term: Term? = null
    private fun initAsTermMulOrDivFactor(termString: String): Boolean {
        var bracketChecker = 0
        for (i in 0 until termString.length) {
            if (termString[i] == '(') {
                bracketChecker++
            }
            if (termString[i] == ')') {
                bracketChecker--
            }
            if ((termString[i] == '*' || termString[i] == '/') && bracketChecker == 0) {
                val leftSubString = termString.substring(0, i)
                if (!TopLevelParser.stringHasValidBrackets(leftSubString)) {
                    continue
                }
                val leftTerm = Term(leftSubString, parser)
                val isValidFirstPartTerm = leftTerm.termType != TermType.INVALID
                if (!isValidFirstPartTerm) {
                    continue
                }
                val rightSubString = termString.substring(i + 1)
                if (!TopLevelParser.stringHasValidBrackets(rightSubString)) {
                    continue
                }
                val rightFactor = Factor(rightSubString, parser)
                val isValidSecondPartFactor = rightFactor.factorType != Factor.FactorType.INVALID
                if (isValidSecondPartFactor) {
                    if (termString[i] == '*') {
                        termType = TermType.TERM_MUL_FACTOR
                    } else {
                        termType = TermType.TERM_DIV_FACTOR
                    }
                    term = leftTerm
                    factor = rightFactor
                    return true
                }
            }
        }
        return false
    }

    private fun initAsFactor(termString: String): Boolean {
        val factor = Factor(termString, parser)
        val isValidTerm = factor.factorType != Factor.FactorType.INVALID
        if (isValidTerm) {
            termType = TermType.FACTOR
            this.factor = factor
            return true
        }
        return false
    }

    @get:Throws(ExpressionFormatException::class)
    override val value: Double
        get() = when (termType) {
            TermType.TERM_MUL_FACTOR -> term!!.value * factor!!.value
            TermType.TERM_DIV_FACTOR -> term!!.value / factor!!.value
            TermType.FACTOR -> factor!!.value
            TermType.INVALID -> throw ExpressionFormatException("could not parse Term")
        }

    @get:Throws(ExpressionFormatException::class)
    override val isVariable: Boolean
        get() = when (termType) {
            TermType.TERM_MUL_FACTOR, TermType.TERM_DIV_FACTOR -> term!!.isVariable || factor!!.isVariable
            TermType.FACTOR -> factor!!.isVariable
            TermType.INVALID -> throw ExpressionFormatException("could not parse Term")
        }

    init {
        if (!TopLevelParser.stringHasValidBrackets(termString)) {
            termType = TermType.INVALID
        } else {
            var isReady = initAsTermMulOrDivFactor(termString)
            if (!isReady) {
                isReady = initAsFactor(termString)
            }
            if (!isReady) {
                termType = TermType.INVALID
            }
        }
    }
}
