package com.ichi2.anki;

import com.ichi2.libanki.Collection;

/**
 * Test support class that returns a null collection every time for getCol()
 */
public class NullCollectionReviewer extends Reviewer {
    @Override
    public Collection getCol() {
        return null;
    }
    @Override
    public boolean colIsOpen() {
        return false;
    }
    @Override
    protected void onCollectionLoaded(Collection col) {
        // it's not fair to expect the classes under test to handle this when we return null for getCol()
    }
}
