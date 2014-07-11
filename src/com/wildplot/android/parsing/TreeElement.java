package com.wildplot.android.parsing;

/**
 * Interface for Atom objects
 * @author Michael Goldbach
 * @see com.wildplot.android.parsing.Atom
 */
public interface TreeElement {
    public double getValue();
    public boolean isVariable();
}
