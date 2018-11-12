package com.ichi2.anki;

import com.ichi2.libanki.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ReviewerTest extends RobolectricTest {

    @Test
    public void verifyStartupNoCollection() {
        Reviewer reviewer = Robolectric.setupActivity(NullCollectionReviewer.class);
        assertNull("Collection should have been null", reviewer.getCol());
    }

    @Test
    public void verifyNormalStartup() {
        Reviewer reviewer = Robolectric.setupActivity(Reviewer.class);
        assertNotNull("Collection should be non-null", reviewer.getCol());
    }
}

class NullCollectionReviewer extends Reviewer {
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
