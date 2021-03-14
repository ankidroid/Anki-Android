package com.ichi2.anki.exception;

public class FilteredAncestor extends Exception {
    private final String mFilteredAncestorName;
    public FilteredAncestor(String filteredAncestorName) {
        this.mFilteredAncestorName = filteredAncestorName;
    }

    public String getFilteredAncestorName() {
        return mFilteredAncestorName;
    }

}
