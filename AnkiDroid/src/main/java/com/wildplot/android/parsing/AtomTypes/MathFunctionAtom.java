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
package com.wildplot.android.parsing.AtomTypes;

import android.annotation.SuppressLint;

import com.wildplot.android.parsing.Expression;
import com.wildplot.android.parsing.ExpressionFormatException;
import com.wildplot.android.parsing.TopLevelParser;
import com.wildplot.android.parsing.TreeElement;


@SuppressLint("NonPublicNonStaticFieldName")
public class MathFunctionAtom implements TreeElement {

    private final TopLevelParser parser;

    public enum MathType {SIN, COS, TAN, SQRT, ACOS, ASIN, ATAN, SINH, COSH, LOG, LN, INVALID}

    private MathType mathType = MathType.INVALID;
    private Expression expression;
    private boolean hasSavedValue = false;
    private double savedValue = 0;


    public MathFunctionAtom(String funcString, TopLevelParser parser) {
        this.parser = parser;
        boolean isValid = init(funcString);
        if (!isValid) {
            this.mathType = MathType.INVALID;
        }
        if (isValid && !isVariable()) {
            savedValue = getValue();
            hasSavedValue = true;
        }
    }


    private boolean init(String funcString) {
        int leftBracket = funcString.indexOf("(");
        int rightBracket = funcString.lastIndexOf(")");
        if (leftBracket > 1 && rightBracket > leftBracket + 1) {
            String funcName = funcString.substring(0, leftBracket);
            String expressionString = funcString.substring(leftBracket + 1, rightBracket);
            Expression expressionInBrackets = new Expression(expressionString, parser);
            boolean isValidExpression = expressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
            if (isValidExpression) {
                switch (funcName) {
                    case "sin":
                        this.mathType = MathType.SIN;
                        break;
                    case "cos":
                        this.mathType = MathType.COS;
                        break;
                    case "tan":
                        this.mathType = MathType.TAN;
                        break;
                    case "sqrt":
                        this.mathType = MathType.SQRT;
                        break;
                    case "acos":
                        this.mathType = MathType.ACOS;
                        break;
                    case "asin":
                        this.mathType = MathType.ASIN;
                        break;
                    case "atan":
                        this.mathType = MathType.ATAN;
                        break;
                    case "sinh":
                        this.mathType = MathType.SINH;
                        break;
                    case "cosh":
                        this.mathType = MathType.COSH;
                        break;
                    case "log":
                    case "lg":
                        this.mathType = MathType.LOG;
                        break;
                    case "ln":
                        this.mathType = MathType.LN;
                        break;
                    default:
                        this.mathType = MathType.INVALID;
                        return false;
                }
                this.expression = expressionInBrackets;
                return true;


            }

        }

        return false;
    }


    @Override
    public double getValue() throws ExpressionFormatException {
        if (hasSavedValue) {
            return savedValue;
        }

        switch (mathType) {
            case SIN:
                return Math.sin(expression.getValue());
            case COS:
                return Math.cos(expression.getValue());
            case TAN:
                return Math.tan(expression.getValue());
            case SQRT:
                return Math.sqrt(expression.getValue());
            case ACOS:
                return Math.acos(expression.getValue());
            case ASIN:
                return Math.asin(expression.getValue());
            case ATAN:
                return Math.atan(expression.getValue());
            case SINH:
                return Math.sinh(expression.getValue());
            case COSH:
                return Math.cosh(expression.getValue());
            case LOG:
                return Math.log10(expression.getValue());
            case LN:
                return Math.log(expression.getValue());
            case INVALID:
            default:
                throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }


    @Override
    public boolean isVariable() {
        if (mathType != MathType.INVALID) {

            return expression.isVariable();
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }


    public MathType getMathType() {
        return mathType;
    }
}
