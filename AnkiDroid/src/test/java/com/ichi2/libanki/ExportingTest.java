package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class ExportingTest extends RobolectricTest {
    private Collection col;

    /*****************
     ** Exporting    *
     *****************/
    private void setup() {
        col = mCol;
        Note note = mCol.newNote();
        note.setItem("Front", "foo");
        note.setItem("Back", "bar<br>");
        note.setTagsFromStr("tag, tag2");
        mCol.addNote(note);
        // with a different col
        note = mCol.newNote();
        note.setItem("Front", "baz");
        note.setItem("Back", "qux");
        note.model().put("did", addDeck("new col"));
        mCol.addNote(note);
    }


    /*//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// */
    @Test
    public void empty_test() {
        // A test should occurs in the file, otherwise travis rejects. This remains here until we can uncomment the real tests.
    }


    /* TODO
       @Test
       public void test_export_anki(){
       // create a new col with its own conf to test conf copying
       long did = addDeck("test");
       Deck dobj = mCol.getDecks().get(did);
       long confId = mCol.getDecks().add_config_returning_id("newconf");
       DeckConfig conf = mCol.getDecks().getConf(confId);
       conf.getJSONObject("new").put("perDay", 5);
       mCol.getDecks().save(conf);
       mCol.getDecks().setConf(dobj, confId);
       // export
       AnkiPackageExporter e = AnkiExporter(mCol);
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2");
       newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       // exporting should not have changed conf for original deck
       conf = mCol.getDecks().confForDid(did);
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
       // add a test file to the media folder
       with open(os.path.join(mCol.getMedia().dir(), "今日.mp3"), "w") as note:
       note.write("test");
       Note n = mCol.newNote();
       n.setItem("Front", "[sound:今日.mp3]");
       mCol.addNote(n);
       AnkiPackageExporter e = AnkiPackageExporter(mCol);
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".apkg");
       String newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       }

       @errorsAfterMidnight
       @Test
       public void test_export_anki_due(){
       Note note = mCol.newNote();
       note.setItem("Front","foo");
       mCol.addNote(note);
       mCol.crt -= SECONDS_PER_DAY * 10;
       mCol.flush();
       mCol.getSched().reset();
       Card c = mCol.getSched().getCard();
       mCol.getSched().answerCard(c, 3);
       mCol.getSched().answerCard(c, 3);
       // should have ivl of 1, due on day 11
       assertEquals(1, c.getIvl());
       assertEquals(11, c.getDue());
       assertEquals(10, mCol.getSched().getToday());
       assertEquals(1, c.getDue() - mCol.getSched().getToday());
       // export
       AnkiPackageExporter e = AnkiExporter(mCol);
       e.includeSched = true;
       fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2");
       String newname = str(newname);
       os.close(fd);
       os.unlink(newname);
       e.exportInto(newname);
       // importing into a new deck, the due date should be equivalent
       col2 = mCol;
       imp = Anki2Importer(col2, newname);
       imp.run();
       c = col2.getCard(c.getId());
       col2.getSched().reset();
       assertEquals(1, c.getDue() - col2.getSched().getToday());
       }

       @Test
       public void test_export_textcard(){
       //     e = TextCardExporter(mCol)
       //     Note note = unicode(tempfile.mkstemp(prefix="ankitest")[1])
       //     os.unlink(note)
       //     e.exportInto(note)
       //     e.includeTags = true
       //     e.exportInto(note)
       }

       @Test
       public void test_export_textnote(){
       Collection col = setup1();
       e = TextNoteExporter(mCol);
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
