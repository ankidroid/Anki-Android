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
class ExportingTest : RobolectricTest() {
    private lateinit var mCol: Collection

    /*****************
     * Exporting    *
     */
    private fun setup() {
        mCol = col
        var note = mCol.newNote()
        note.setItem("Front", "foo")
        note.setItem("Back", "bar<br>")
        note.setTagsFromStr("tag, tag2")
        mCol.addNote(note)
        // with a different col
        note = mCol.newNote()
        note.setItem("Front", "baz")
        note.setItem("Back", "qux")
        note.model().put("did", addDeck("new col"))
        mCol.addNote(note)
    }

    /*//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// */
    @Test
    fun empty_test() {
        // A test should occurs in the file, otherwise travis rejects. This remains here until we can uncomment the real tests.
    }

    /* TODO
       @Test
       public void test_export_anki(){
       // create a new col with its own conf to test conf copying
       long did = addDeck("test");
       Deck dobj = col.getDecks().get(did);
       long confId = col.getDecks().add_config_returning_id("newconf");
       DeckConfig conf = col.getDecks().getConf(confId);
       conf.getJSONObject("new").put("perDay", 5);
       col.getDecks().save(conf);
       col.getDecks().setConf(dobj, confId);
       // export
       AnkiPackageExporter e = AnkiExporter(col);
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2");
       newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       // exporting should not have changed conf for original deck
       conf = col.getDecks().confForDid(did);
       assertNotEquals(conf.getLong("id") != 1);
       // connect to new deck
       Collection col2 = aopen(newname);
       assertEquals(2, col2.cardCount());
       // as scheduling was reset, should also revert decks to default conf
       long did = col2.getDecks().id("test", create=false);
       assertTrue(did);
       conf2 = col2.getDecks().confForDid(did);
       assertTrue(conf2.getJSONObject("new").put("perDay",= 20));
       Deck dobj = col2.getDecks().get(did);
       // conf should be 1
       assertTrue(dobj.put("conf",= 1));
       // try again, limited to a deck
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2");
       newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.setDid(1);
       e.exportInto(newname);
       col2 = aopen(newname);
       assertEquals(1, col2.cardCount());
       }

       @Test
       public void test_export_ankipkg(){
       // add a test file to the media directory
       with open(os.path.join(col.getMedia().dir(), "今日.mp3"), "w") as note:
       note.write("test");
       Note n = col.newNote();
       n.setItem("Front", "[sound:今日.mp3]");
       col.addNote(n);
       AnkiPackageExporter e = AnkiPackageExporter(col);
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".apkg");
       String newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       }

       @errorsAfterMidnight
       @Test
       public void test_export_anki_due(){
       Collection col = getCol();
       Note note = col.newNote();
       note.setItem("Front","foo");
       col.addNote(note);
       col.crt -= SECONDS_PER_DAY * 10;
       col.flush();
       col.getSched().reset();
       Card c = col.getSched().getCard();
       col.getSched().answerCard(c, 3);
       col.getSched().answerCard(c, 3);
       // should have ivl of 1, due on day 11
       assertEquals(1, c.getIvl());
       assertEquals(11, c.getDue());
       assertEquals(10, col.getSched().getToday());
       assertEquals(1, c.getDue() - col.getSched().getToday());
       // export
       AnkiPackageExporter e = AnkiExporter(col);
       e.includeSched = true;
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2");
       String newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       // importing into a new deck, the due date should be equivalent
       col2 = getCol();
       imp = Anki2Importer(col2, newname);
       imp.run();
       c = col2.getCard(c.getId());
       col2.getSched().reset();
       assertEquals(1, c.getDue() - col2.getSched().getToday());
       }

       @Test
       public void test_export_textcard(){
       //     e = TextCardExporter(col)
       //     Note note = unicode(tempfile.mkstemp(prefix="ankitest")[1])
       //     os.unlink(note)
       //     e.exportInto(note)
       //     e.includeTags = true
       //     e.exportInto(note)
       }

       @Test
       public void test_export_textnote(){
       Collection col = setup1();
       e = TextNoteExporter(col);
       fd, Note note = tempfile.mkstemp(prefix="ankitest");
       Note note = str(note);
       os.close(fd);
       os.unlink(note);
       e.exportInto(note);
       with open(note) as file:
       assertEquals("foo\tbar<br>\ttag tag2\n", file.readline());
       e.includeTags = false;
       e.includeHTML = false;
       e.exportInto(note);
       with open(note) as file:
       assertEquals("foo\tbar\n", file.readline());
       }

       @Test
       public void test_exporters(){
       assertThat(str(exporters()), containsString("*.apkg"));

    */
}
