package com.ichi2.anki;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Collection;

public class RobolectricTest extends RobolectricTestBase {

    protected Collection mCol;

    @Override
    public void setUp() {
        super.setUp();
        getCol();
    }


    @Override
    public Collection getCol() {
        mCol = super.getCol();
        return mCol;
    }
}
