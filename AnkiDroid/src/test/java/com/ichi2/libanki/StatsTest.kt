/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsTest : RobolectricTest() {
    /*****************
     * Stats        *
     */
    @Test
    fun empty_test() {
        // A test should occurs in the file, otherwise travis rejects. This remains here until we can uncomment the real tests.
    } /* TODO put in Collection
       @Test
       public void test_stats() throws Exception {
       Collection col = getCol();
       Note note = col.newNote();
       note.setItem("Front","foo");
       col.addNote(note);
       Card c = note.cards().get(0);
       // card stats
       assertTrue(col.cardStats(c));
       col.reset();
       c = col.getSched().getCard();
       col.getSched().answerCard(c, 3);
       col.getSched().answerCard(c, 2);
       assertTrue(col.cardStats(c));
       }

       @Test
       public void test_graphs_empty() throws Exception {
       Collection col = getCol();
       assertTrue(col.stats().report());
       }


       @Test
       public void test_graphs() throws Exception {
       dir = tempfile.gettempdir();
       Collection col = getCol();
       g = col.stats();
       rep = g.report();
       with open(os.path.join(dir, "test.html"), "w", encoding="UTF-8") as note:
       note.write(rep);
       return;
       } */
}
