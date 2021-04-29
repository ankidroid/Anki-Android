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
public class Expression implements TreeElement {
    private final TopLevelParser parser;



    public enum ExpressionType {EXP_PLUS_TERM, EXP_MINUS_TERM, TERM, INVALID}



    private ExpressionType expressionType = ExpressionType.INVALID;
    private Expression expression = null;
    private Term term = null;


    public Expression(String expressionString, TopLevelParser parser) {
        this.parser = parser;
        if (!TopLevelParser.stringHasValidBrackets(expressionString)) {
            this.expressionType = ExpressionType.INVALID;
            return;
        }

        boolean isReady = initAsExpPlusOrMinusTerm(expressionString);
        if (!isReady) {
            isReady = initAsTerm(expressionString);
        }
        if (!isReady) {
            this.expressionType = ExpressionType.INVALID;
        }

    }


    private boolean initAsExpPlusOrMinusTerm(String expressionString) {
        int bracketChecker = 0;
        for (int i = 0; i < expressionString.length(); i++) {
            if (expressionString.charAt(i) == '(') {
                bracketChecker++;
            }
            if (expressionString.charAt(i) == ')') {
                bracketChecker--;
            }

            if ((expressionString.charAt(i) == '+' || expressionString.charAt(i) == '-') && bracketChecker == 0) {
                String leftSubString = expressionString.substring(0, i);
                if (!TopLevelParser.stringHasValidBrackets(leftSubString)) {
                    continue;
                }
                Expression leftExpression = new Expression(leftSubString, parser);
                boolean isValidFirstPartExpression = leftExpression.getExpressionType() != ExpressionType.INVALID;

                if (!isValidFirstPartExpression) {
                    continue;
                }

                String rightSubString = expressionString.substring(i + 1);
                if (!TopLevelParser.stringHasValidBrackets(rightSubString)) {
                    continue;
                }

                Term rightTerm = new Term(rightSubString, parser);
                boolean isValidSecondPartTerm = rightTerm.getTermType() != Term.TermType.INVALID;

                if (isValidSecondPartTerm) {
                    if (expressionString.charAt(i) == '+') {
                        this.expressionType = ExpressionType.EXP_PLUS_TERM;
                    } else {
                        this.expressionType = ExpressionType.EXP_MINUS_TERM;
                    }

                    this.expression = leftExpression;
                    this.term = rightTerm;
                    return true;
                }

            }
        }
        return false;
    }


    private boolean initAsTerm(String expressionString) {
        if (!TopLevelParser.stringHasValidBrackets(expressionString)) {
            return false;
        }
        Term term = new Term(expressionString, parser);
        boolean isValidTerm = term.getTermType() != Term.TermType.INVALID;
        if (isValidTerm) {
            this.expressionType = ExpressionType.TERM;
            this.term = term;
            return true;
        }
        return false;
    }


    public ExpressionType getExpressionType() {
        return expressionType;
    }


    public double getValue() throws ExpressionFormatException {
        switch (expressionType) {
            case EXP_PLUS_TERM:
                return expression.getValue() + term.getValue();
            case EXP_MINUS_TERM:
                return expression.getValue() - term.getValue();
            case TERM:
                return term.getValue();
            default:
            case INVALID:
                throw new ExpressionFormatException("could not parse Expression");
        }
    }


    @Override
    public boolean isVariable() {
        switch (expressionType) {
            case EXP_PLUS_TERM:
            case EXP_MINUS_TERM:
                return expression.isVariable() || term.isVariable();
            case TERM:
                return term.isVariable();
            default:
            case INVALID:
                throw new ExpressionFormatException("could not parse Expression");
        }
    }

}
