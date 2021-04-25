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
public class Pow implements TreeElement {
    private final TopLevelParser parser;


    public enum PowType {ATOM, ATOM_POW_FACTOR, ATOM_SQRT_FACTOR, INVALID}


    private PowType powType = PowType.INVALID;
    private Atom atom;
    private Factor factor;


    public Pow(String powString, TopLevelParser parser) {
        this.parser = parser;
        if (!TopLevelParser.stringHasValidBrackets(powString)) {
            this.powType = PowType.INVALID;
            return;
        }

        boolean isReady = initAsAtom(powString);
        if (!isReady) {
            isReady = initAsAtomPowFactor(powString);
        }
        if (!isReady) {
            isReady = initAsAtomSqrtFactor(powString);
        }
        if (!isReady) {
            this.powType = PowType.INVALID;
        }
    }


    private boolean initAsAtom(String powString) {
        Atom atom = new Atom(powString, parser);
        boolean isValidAtom = atom.getAtomType() != Atom.AtomType.INVALID;
        if (isValidAtom) {
            this.powType = PowType.ATOM;
            this.atom = atom;
            return true;
        }
        return false;
    }


    private boolean initAsAtomPowFactor(String powString) {
        int opPos = powString.indexOf("^");
        if (opPos > 0) {
            String leftAtomString = powString.substring(0, opPos);
            String rightFactorString = powString.substring(opPos + 1);
            if (!TopLevelParser.stringHasValidBrackets(leftAtomString) || !TopLevelParser.stringHasValidBrackets(rightFactorString)) {
                return false;
            }
            Atom leftAtom = new Atom(leftAtomString, parser);
            boolean isValidAtom = leftAtom.getAtomType() != Atom.AtomType.INVALID;
            if (isValidAtom) {
                Factor rightFactor = new Factor(rightFactorString, parser);
                boolean isValidFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;
                if (isValidFactor) {
                    this.powType = PowType.ATOM_POW_FACTOR;
                    this.atom = leftAtom;
                    this.factor = rightFactor;
                    return true;
                }
            }
        }

        return false;
    }


    private boolean initAsAtomSqrtFactor(String powString) {
        int opPos = powString.indexOf("**");
        if (opPos > 0) {
            String leftAtomString = powString.substring(0, opPos);
            String rightFactorString = powString.substring(opPos + 2);
            if (!TopLevelParser.stringHasValidBrackets(leftAtomString) || !TopLevelParser.stringHasValidBrackets(rightFactorString)) {
                return false;
            }
            Atom leftAtom = new Atom(leftAtomString, parser);
            boolean isValidAtom = leftAtom.getAtomType() != Atom.AtomType.INVALID;
            if (isValidAtom) {
                Factor rightFactor = new Factor(rightFactorString, parser);
                boolean isValidFactor = rightFactor.getFactorType() != Factor.FactorType.INVALID;
                if (isValidFactor) {
                    this.powType = PowType.ATOM_SQRT_FACTOR;
                    this.atom = leftAtom;
                    this.factor = rightFactor;
                    return true;
                }
            }
        }

        return false;
    }


    @Override
    public double getValue() throws ExpressionFormatException {
        switch (powType) {
            case ATOM:
                return atom.getValue();
            case ATOM_POW_FACTOR:
                return Math.pow(atom.getValue(), factor.getValue());
            case ATOM_SQRT_FACTOR:
                return Math.pow(atom.getValue(), 1.0 / factor.getValue());
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom expression");
        }
    }


    @Override
    public boolean isVariable() throws ExpressionFormatException {
        switch (powType) {
            case ATOM:
                return atom.isVariable();
            case ATOM_POW_FACTOR:
            case ATOM_SQRT_FACTOR:
                return atom.isVariable() || factor.isVariable();
            case INVALID:
            default:
                throw new ExpressionFormatException("cannot parse Atom expression");
        }
    }


    public PowType getPowType() {
        return powType;
    }
}
