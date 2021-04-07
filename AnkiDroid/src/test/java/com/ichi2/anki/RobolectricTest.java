package com.ichi2.anki;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Models;

public class RobolectricTest extends RobolectricTestBase {

    protected Collection mCol;
    protected Decks mDecks;
    protected Models mModels;

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
        return mCol;
    }
}
