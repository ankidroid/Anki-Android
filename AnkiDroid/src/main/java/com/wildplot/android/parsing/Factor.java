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
public class Factor implements TreeElement {
    private final TopLevelParser parser;



    public enum FactorType {PLUS_FACTOR, MINUS_FACTOR, POW, INVALID}



    private FactorType factorType = FactorType.INVALID;
    private Factor factor;
    private Pow pow;


    public Factor(String factorString, TopLevelParser parser) {
        this.parser = parser;
        if (!TopLevelParser.stringHasValidBrackets(factorString)) {
            this.factorType = FactorType.INVALID;
            return;
        }

        boolean isReady = initAsPlusFactor(factorString);
        if (!isReady) {
            isReady = initAsMinusFactor(factorString);
        }
        if (!isReady) {
            isReady = initAsPow(factorString);
        }
        if (!isReady) {
            this.factorType = FactorType.INVALID;
        }
    }


    private boolean initAsPlusFactor(String factorString) {
        if (factorString.length() > 0 && factorString.charAt(0) == '+') {
            String leftSubString = factorString.substring(1);
            Factor leftFactor = new Factor(leftSubString, parser);
            boolean isValidFactor = leftFactor.getFactorType() != FactorType.INVALID;
            if (isValidFactor) {
                this.factorType = FactorType.PLUS_FACTOR;
                this.factor = leftFactor;
                return true;
            }
        }

        return false;
    }


    private boolean initAsMinusFactor(String factorString) {
        if (factorString.length() > 0 && factorString.charAt(0) == '-') {
            String leftSubString = factorString.substring(1);
            Factor leftFactor = new Factor(leftSubString, parser);
            boolean isValidFactor = leftFactor.getFactorType() != FactorType.INVALID;
            if (isValidFactor) {
                this.factorType = FactorType.MINUS_FACTOR;
                this.factor = leftFactor;
                return true;
            }
        }

        return false;
    }


    private boolean initAsPow(String factorString) {
        Pow pow = new Pow(factorString, parser);
        boolean isValidPow = pow.getPowType() != Pow.PowType.INVALID;
        if (isValidPow) {
            this.factorType = FactorType.POW;
            this.pow = pow;
            return true;
        }
        return false;
    }


    public FactorType getFactorType() {
        return factorType;
    }


    public double getValue() throws ExpressionFormatException {
        switch (factorType) {
            case PLUS_FACTOR:
                return factor.getValue();
            case MINUS_FACTOR:
                return -factor.getValue();
            case POW:
                return pow.getValue();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse expression at factor level");
        }

    }


    @Override
    public boolean isVariable() throws ExpressionFormatException {
        switch (factorType) {
            case PLUS_FACTOR:
            case MINUS_FACTOR:
                return factor.isVariable();
            case POW:
                return pow.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse expression at factor level");
        }
    }


}
