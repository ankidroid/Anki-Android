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

import com.wildplot.android.parsing.Atom;
import com.wildplot.android.parsing.Expression;
import com.wildplot.android.parsing.ExpressionFormatException;
import com.wildplot.android.parsing.TopLevelParser;
import com.wildplot.android.parsing.TreeElement;

import java.util.regex.Pattern;

/**
 * @author Michael Goldbach
 */
@SuppressLint("NonPublicNonStaticFieldName")
public class FunctionXYAtom implements TreeElement {

    private Atom.AtomType atomType = Atom.AtomType.FUNCTION_X;
    private final TopLevelParser parser;
    private Expression expressionLeft;
    private Expression expressionRight;
    private String funcName;


    public FunctionXYAtom(String funcString, TopLevelParser parser) {
        this.parser = parser;

        boolean isValid = init(funcString);
        if (!isValid) {
            this.atomType = Atom.AtomType.INVALID;
        }
    }


    private boolean init(String funcString) {
        int leftBracket = funcString.indexOf("(");
        int rightBracket = funcString.lastIndexOf(")");
        int comma = funcString.indexOf(",");    //see if there even is a comma
        if (leftBracket > 1 && rightBracket > leftBracket + 1 && comma > leftBracket && comma < rightBracket) {
            //test all commas
            for (int i = leftBracket + 1; i < rightBracket; i++) {
                if (funcString.charAt(i) == ',') {
                    comma = i;
                }
                String funcName = funcString.substring(0, leftBracket);

                Pattern p = Pattern.compile("[^a-zA-Z0-9]");
                boolean hasSpecialChar = p.matcher(funcName).find();
                if (!hasSpecialChar && (funcName.length() > 0)) {

                    String leftExpressionString = funcString.substring(leftBracket + 1, comma);
                    String rightExpressionString = funcString.substring(comma + 1, rightBracket);
                    Expression leftExpressionInBrackets = new Expression(leftExpressionString, parser);
                    Expression rightExpressionInBrackets = new Expression(rightExpressionString, parser);
                    boolean isValidLeftExpression = leftExpressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
                    boolean isValidRightExpression = rightExpressionInBrackets.getExpressionType() != Expression.ExpressionType.INVALID;
                    if (isValidLeftExpression && isValidRightExpression) {
                        this.atomType = Atom.AtomType.FUNCTION_X;
                        this.funcName = funcName;
                        this.expressionLeft = leftExpressionInBrackets;
                        this.expressionRight = rightExpressionInBrackets;
                        return true;
                    }
                } else {
                    this.atomType = Atom.AtomType.INVALID;
                    return false;
                }
            }
        }

        return false;
    }



    public Atom.AtomType getAtomType() {
        return atomType;
    }


    @Override
    public double getValue() throws ExpressionFormatException {
        if (atomType != Atom.AtomType.INVALID) {

            return parser.getFuncVal(funcName, expressionLeft.getValue(), expressionRight.getValue());
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }


    @Override
    public boolean isVariable() throws ExpressionFormatException {
        if (atomType != Atom.AtomType.INVALID) {

            return expressionLeft.isVariable() || expressionRight.isVariable();
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }
}
