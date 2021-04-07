package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;


@RunWith(AndroidJUnit4.class)
public class StatsTest extends RobolectricTest {

    /*****************
     ** Stats        *
     *****************/
    @Test
    public void empty_test() {
        // A test should occurs in the file, otherwise travis rejects. This remains here until we can uncomment the real tests.
    }
    /* TODO put in Collection
       @Test
       public void test_stats() throws Exception {
       Note note = mCol.newNote();
       note.setItem("Front","foo");
       mCol.addNote(note);
       Card c = note.cards().get(0);
       // card stats
       assertTrue(mCol.cardStats(c));
       mCol.reset();
       c = mSched.getCard();
       mSched.answerCard(c, 3);
       mSched.answerCard(c, 2);
       assertTrue(mCol.cardStats(c));
       }

       @Test
       public void test_graphs_empty() throws Exception {
       assertTrue(mCol.stats().report());
       }


       @Test
       public void test_graphs() throws Exception {
       dir = tempfile.gettempdir();
       g = mCol.stats();
       rep = g.report();
       with open(os.path.join(dir, "test.html"), "w", encoding="UTF-8") as note:
       note.write(rep);
       return;
       } */
}
