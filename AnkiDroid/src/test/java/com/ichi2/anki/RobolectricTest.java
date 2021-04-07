package com.ichi2.anki;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.sched.AbstractSched;

public class RobolectricTest extends RobolectricTestBase {

    protected Collection mCol;
    protected Decks mDecks;
    protected Models mModels;
    protected AbstractSched mSched;

    @Override
    public void setUp() {
        super.setUp();
        getCol();
    }


    @Override
    public Collection getCol() {
        mCol = super.getCol();
        mDecks = mCol.getDecks();
        mModels = mCol.getModels();
        mSched = mCol.getSched();
        return mCol;
    }


    @Override
    protected Collection getCol(int version) throws ConfirmModSchemaException {
        mCol = super.getCol(version);
        mSched = mCol.getSched();
        return mCol;
    }
}
