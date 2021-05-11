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
package com.wildplot.android.parsing;


import android.annotation.SuppressLint;

@SuppressLint("NonPublicNonStaticFieldName")
public class Term implements TreeElement {
    private final TopLevelParser parser;



    public enum TermType {TERM_MUL_FACTOR, TERM_DIV_FACTOR, FACTOR, INVALID}



    private TermType termType = TermType.INVALID;
    private Factor factor = null;
    private Term term = null;


    public Term(String termString, TopLevelParser parser) {
        this.parser = parser;

        if (!TopLevelParser.stringHasValidBrackets(termString)) {
            this.termType = TermType.INVALID;
            return;
        }

        boolean isReady = initAsTermMulOrDivFactor(termString);
        if (!isReady) {
            isReady = initAsFactor(termString);
        }
        if (!isReady) {
            this.termType = TermType.INVALID;
        }
    }


    private boolean initAsTermMulOrDivFactor(String termString) {
        int bracketChecker = 0;
        for (int i = 0; i < termString.length(); i++) {
            if (termString.charAt(i) == '(') {
                bracketChecker++;
            }
            if (termString.charAt(i) == ')') {
                bracketChecker--;
            }
            if ((termString.charAt(i) == '*' || termString.charAt(i) == '/') && bracketChecker == 0) {
                String leftSubString = termString.substring(0, i);
                if (!TopLevelParser.stringHasValidBrackets(leftSubString)) {
                    continue;
                }
                Term leftTerm = new Term(leftSubString, parser);
                boolean isValidFirstPartTerm = leftTerm.getTermType() != TermType.INVALID;

                if (!isValidFirstPartTerm) {
                    continue;
                }

                String rightSubString = termString.substring(i + 1);
                if (!TopLevelParser.stringHasValidBrackets(rightSubString)) {
                    continue;
                }
                Factor rightFactor = new Factor(rightSubString, parser);
                boolean isValidSecondPartFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;

                if (isValidSecondPartFactor) {
                    if (termString.charAt(i) == '*') {
                        this.termType = TermType.TERM_MUL_FACTOR;
                    } else {
                        this.termType = TermType.TERM_DIV_FACTOR;
                    }
                    this.term = leftTerm;
                    this.factor = rightFactor;
                    return true;
                }

            }
        }

        return false;
    }


    private boolean initAsFactor(String termString) {
        Factor factor = new Factor(termString, parser);
        boolean isValidTerm = factor.getFactorType() != Factor.FactorType.INVALID;
        if (isValidTerm) {
            this.termType = TermType.FACTOR;
            this.factor = factor;
            return true;
        }
        return false;
    }


    public TermType getTermType() {
        return termType;
    }


    public double getValue() throws ExpressionFormatException {
        switch (termType) {
            case TERM_MUL_FACTOR:
                return term.getValue() * factor.getValue();
            case TERM_DIV_FACTOR:
                return term.getValue() / factor.getValue();
            case FACTOR:
                return factor.getValue();
            case INVALID:
            default:
                throw new ExpressionFormatException("could not parse Term");
        }
    }


    @Override
    public boolean isVariable() {
        switch (termType) {
            case TERM_MUL_FACTOR:
            case TERM_DIV_FACTOR:
                return term.isVariable() || factor.isVariable();
            case FACTOR:
                return factor.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("could not parse Term");
        }
    }


}
