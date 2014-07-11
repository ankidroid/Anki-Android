package com.wildplot.android.parsing.AtomTypes;

import com.wildplot.android.parsing.Atom;
import com.wildplot.android.parsing.ExpressionFormatException;
import com.wildplot.android.parsing.TreeElement;

/**
 * @author Michael Goldbach
 */
public class NumberAtom implements TreeElement {
    public Atom.AtomType getAtomType() {
        return atomType;
    }

    private Atom.AtomType atomType = Atom.AtomType.NUMBER;
    private Double value;

    public NumberAtom(String factorString) {
        try {
            this.value = Double.parseDouble(factorString);
        } catch (NumberFormatException e) {
            atomType = Atom.AtomType.INVALID;
        }

    }

    @Override
    public double getValue() throws ExpressionFormatException{
        if (atomType != Atom.AtomType.INVALID)
            return value;
        else
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
    }

    @Override
    public boolean isVariable() throws ExpressionFormatException{
        if (atomType != Atom.AtomType.INVALID)
            return false;
        else
            throw new ExpressionFormatException("Number is Invalid, cannot parse");
    }
}
