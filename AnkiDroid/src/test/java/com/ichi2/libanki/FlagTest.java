package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class FlagTest extends RobolectricTest {
    /*****************
     ** Flags        *
     *****************/

    @Test
    public void test_flags() {
        Collection col = getCol();
        Note n = col.newNote();
        n.setItem("Front", "one");
        n.setItem("Back", "two");
        int cnt = col.addNote(n);
        Card c = n.cards().get(0);

        // make sure higher bits are preserved
        int origBits = 0b101 << 3;
        c.setFlag(origBits);
        c.flush();
        // no flags to start with
        assertEquals(0, c.userFlag());
        assertEquals(1, col.findCards("flag:0").size());
        assertEquals(0, col.findCards("flag:1").size());
        // set flag 2
        col.setUserFlag(2, new long[] {c.getId()});
        c.load();
        assertEquals(2, c.userFlag());
        // assertEquals(origBits, c.flags & origBits);TODO: create direct access to real flag value
        assertEquals(0, col.findCards("flag:0").size());
        assertEquals(1, col.findCards("flag:2").size());
        assertEquals(0, col.findCards("flag:3").size());
        // change to 3
        col.setUserFlag(3, new long[] {c.getId()});
        c.load();
        assertEquals(3, c.userFlag());
        // unset
        col.setUserFlag(0, new long[] {c.getId()});
        c.load();
        assertEquals(0, c.userFlag());

        // should work with Cards method as well
        c.setUserFlag(2);
        assertEquals(2, c.userFlag());
        c.setUserFlag(3);
        assertEquals(3, c.userFlag());
        c.setUserFlag(0);
        assertEquals(0, c.userFlag());
    }
}
