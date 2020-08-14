package com.ichi2.libanki.sched;

import com.ichi2.libanki.Collection;

public class DeckDueTreeRoot extends DeckDueTreeNode {

    public DeckDueTreeRoot(Collection col) {
        super(col, "", -1, 0, 0, 0);
    }


    @Override
    public String getDeckNameComponent(int part) {
        throw new UnsupportedOperationException();
    }


    @Override
    public String getLastDeckNameComponent() {
        throw new UnsupportedOperationException();
    }


    @Override
    public long getDid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDepth() {
        return -1;
    }

    // Limit should not be applied at top level
    @Override
    protected void applyLimit(boolean addRev) {}
}