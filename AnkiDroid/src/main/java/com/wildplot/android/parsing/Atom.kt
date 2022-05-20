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

import com.wildplot.android.parsing.AtomTypes.FunctionXAtom;
import com.wildplot.android.parsing.AtomTypes.FunctionXYAtom;
import com.wildplot.android.parsing.AtomTypes.MathFunctionAtom;
import com.wildplot.android.parsing.AtomTypes.NumberAtom;
import com.wildplot.android.parsing.AtomTypes.VariableAtom;
import com.wildplot.android.parsing.AtomTypes.XVariableAtom;
import com.wildplot.android.parsing.AtomTypes.YVariableAtom;

@SuppressLint("NonPublicNonStaticFieldName")
public class Atom implements TreeElement {
    private final TopLevelParser parser;



    public enum AtomType {VARIABLE, NUMBER, EXP_IN_BRACKETS, FUNCTION_MATH, FUNCTION_X, FUNCTION_X_Y, INVALID}



    private AtomType atomType = AtomType.INVALID;
    private TreeElement atomObject;
    private Expression expression;


    public Atom(String atomString, TopLevelParser parser) {
        this.parser = parser;

        if (!TopLevelParser.stringHasValidBrackets(atomString)) {
            this.atomType = AtomType.INVALID;
            return;
        }

        boolean isValid = initAsExpInBrackets(atomString);
        if (!isValid) {
            isValid = initAsFunctionMath(atomString);
        }
        if (!isValid) {
            isValid = initAsFunctionX(atomString);
        }
        if (!isValid) {
            isValid = initAsFunctionXY(atomString);
        }
        if (!isValid) {
            isValid = initAsNumber(atomString);
        }
        if (!isValid) {
            isValid = initAsXVariable(atomString);
        }
        if (!isValid) {
            isValid = initAsYVariable(atomString);
        }
        if (!isValid) {
            isValid = initAsVariable(atomString);
        }
        if (!isValid) {
            this.atomType = AtomType.INVALID;
        }

    }


    private boolean initAsExpInBrackets(String atomString) {
        if (atomString.length() > 0 && atomString.charAt(0) == '(' && atomString.charAt(atomString.length() - 1) == ')') {
            String expressionString = atomString.substring(1, atomString.length() - 1);
            Expression expressionInBrackets = new Expression(expressionString, parser);
            boolean isValidExpressionInBrackets = expressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
            if (isValidExpressionInBrackets) {
                this.expression = expressionInBrackets;
                this.atomType = AtomType.EXP_IN_BRACKETS;
                return true;
            }
        }

        return false;
    }


    private boolean initAsFunctionMath(String atomString) {
        MathFunctionAtom mathFunctionAtom = new MathFunctionAtom(atomString, parser);
        boolean isValidMathFunction = mathFunctionAtom.getMathType() != MathFunctionAtom.MathType.INVALID;
        if (isValidMathFunction) {
            this.atomType = AtomType.FUNCTION_MATH;
            this.atomObject = mathFunctionAtom;
            return true;
        }

        return false;
    }


    private boolean initAsFunctionX(String atomString) {
        FunctionXAtom functionXAtom = new FunctionXAtom(atomString, parser);
        boolean isValidFunctionXAtom = functionXAtom.getAtomType() != AtomType.INVALID;
        if (isValidFunctionXAtom) {
            this.atomType = AtomType.FUNCTION_X;
            this.atomObject = functionXAtom;
            return true;
        }

        return false;
    }


    private boolean initAsFunctionXY(String atomString) {
        FunctionXYAtom functionXYAtom = new FunctionXYAtom(atomString, parser);
        boolean isValidFunctionXYAtom = functionXYAtom.getAtomType() != AtomType.INVALID;
        if (isValidFunctionXYAtom) {
            this.atomType = AtomType.FUNCTION_X_Y;
            this.atomObject = functionXYAtom;
            return true;
        }

        return false;
    }


    private boolean initAsNumber(String atomString) {
        NumberAtom numberAtom = new NumberAtom(atomString);
        boolean isValidNumberAtom = numberAtom.getAtomType() != AtomType.INVALID;
        if (isValidNumberAtom) {
            this.atomType = numberAtom.getAtomType();
            this.atomObject = numberAtom;
            return true;
        }
        return false;
    }


    private boolean initAsXVariable(String atomString) {
        if (atomString.equals(parser.getxName())) {
            this.atomType = AtomType.VARIABLE;
            this.atomObject = new XVariableAtom(parser);
            return true;
        }

        return false;
    }


    private boolean initAsYVariable(String atomString) {
        if (atomString.equals(parser.getyName())) {
            this.atomType = AtomType.VARIABLE;
            this.atomObject = new YVariableAtom(parser);
            return true;
        }

        return false;
    }


    private boolean initAsVariable(String atomString) {
        VariableAtom variableAtom = new VariableAtom(atomString, parser);
        boolean isValidVariableAtom = variableAtom.getAtomType() != AtomType.INVALID;
        if (isValidVariableAtom) {
            this.atomType = variableAtom.getAtomType();
            this.atomObject = variableAtom;
            return true;
        }
        return false;
    }


    public AtomType getAtomType() {
        return atomType;
    }


    public double getValue() throws ExpressionFormatException {
        switch (atomType) {
            case EXP_IN_BRACKETS:
                return expression.getValue();
            case VARIABLE:
            case NUMBER:
            case FUNCTION_MATH:
            case FUNCTION_X:
            case FUNCTION_X_Y:
                return atomObject.getValue();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom object");
        }
    }


    @Override
    public boolean isVariable() throws ExpressionFormatException {
        switch (atomType) {
            case EXP_IN_BRACKETS:
                return expression.isVariable();
            case VARIABLE:
            case NUMBER:
            case FUNCTION_MATH:
            case FUNCTION_X:
            case FUNCTION_X_Y:
                return atomObject.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom object");
        }
    }
}
