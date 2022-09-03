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
import com.wildplot.android.parsing.TopLevelParser;
import com.wildplot.android.parsing.TreeElement;

import java.util.regex.Pattern;


@SuppressLint("NonPublicNonStaticFieldName")
public class VariableAtom implements TreeElement {

    private Atom.AtomType atomType = Atom.AtomType.NUMBER;
    private final TopLevelParser parser;
    private final String varName;


    public VariableAtom(String factorString, TopLevelParser parser) {
        this.parser = parser;
        this.varName = factorString;
        Pattern p = Pattern.compile("[^a-zA-Z0-9]");
        boolean hasSpecialChar = p.matcher(varName).find();
        if (!hasSpecialChar && varName.length() > 0) {
            return;
        }
        this.atomType = Atom.AtomType.INVALID;
    }


    public Atom.AtomType getAtomType() {
        return atomType;
    }


    @Override
    public double getValue() {

        if (atomType != Atom.AtomType.INVALID) {

            return parser.getVarVal(varName);
        } else {
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
        }
    }


    @Override
    public boolean isVariable() {
        return true;
    }
}
