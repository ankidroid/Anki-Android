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
import com.wildplot.android.parsing.ExpressionFormatException;
import com.wildplot.android.parsing.TreeElement;

import timber.log.Timber;

@SuppressLint("NonPublicNonStaticFieldName")
public class NumberAtom implements TreeElement {

    private Atom.AtomType atomType = Atom.AtomType.NUMBER;
    private Double value;


    public NumberAtom(String factorString) {
        try {
            this.value = Double.parseDouble(factorString);
        } catch (NumberFormatException e) {
            Timber.w(e);
            atomType = Atom.AtomType.INVALID;
        }

    }


    public Atom.AtomType getAtomType() {
        return atomType;
    }


    @Override
    public double getValue() throws ExpressionFormatException {
        if (atomType != Atom.AtomType.INVALID) {
            return value;
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }


    @Override
    public boolean isVariable() throws ExpressionFormatException {
        if (atomType != Atom.AtomType.INVALID) {
            return false;
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }
}
