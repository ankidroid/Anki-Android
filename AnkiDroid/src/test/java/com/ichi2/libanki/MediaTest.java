package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.testutils.AnkiAssert.assertEqualsArrayList;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MediaTest extends RobolectricTest {
    /*****************
     ** Media        *
     *****************/
    // copying files to media folder

    /* TODO: media
       @Test
       public void test_add(){
       String dir = tempfile.mkdtemp(prefix="anki");
       String path = os.path.join(dir, "foo.jpg");
       with open(path, "w") as note:
       note.write("hello");
       // new file, should preserve name
       assertEquals("foo.jpg", mCol.getMedia().addFile(path));
       // adding the same file again should not create a duplicate
       assertEquals("foo.jpg", mCol.getMedia().addFile(path));
       // but if it has a different sha1, it should
       with open(path, "w") as note:
       note.write("world");
       assertEquals("foo-7c211433f02071597741e6ff5a8ea34789abbf43.jpg", mCol.getMedia().addFile(path));
       } */
    @Test
    public void test_strings() {
        long mid = mModels.current().getLong("id");
        assertEquals(0, mCol.getMedia().filesInStr(mid, "aoeu").size());
        assertEqualsArrayList(new String[] {"foo.jpg"}, mCol.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'>ao"));
        assertEqualsArrayList(new String[] {"foo.jpg"}, mCol.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg' style='test'>ao"));
        assertEqualsArrayList(new String[] {"foo.jpg", "bar.jpg"}, mCol.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao"));
        assertEqualsArrayList(new String[] {"foo.jpg"}, mCol.getMedia().filesInStr(mid, "aoeu<img src=foo.jpg style=bar>ao"));
        assertEqualsArrayList(new String[] {"one", "two"}, mCol.getMedia().filesInStr(mid, "<img src=one><img src=two>"));
        assertEqualsArrayList(new String[] {"foo.jpg"}, mCol.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\">ao"));
        assertEqualsArrayList(new String[] {"foo.jpg", "fo"},
                mCol.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao"));
        assertEqualsArrayList(new String[] {"foo.mp3"}, mCol.getMedia().filesInStr(mid, "aou[sound:foo.mp3]aou"));
        assertEquals("aoeu", mCol.getMedia().strip("aoeu"));
        assertEquals("aoeuaoeu", mCol.getMedia().strip("aoeu[sound:foo.mp3]aoeu"));
        assertEquals("aoeu", mCol.getMedia().strip("a<img src=yo>oeu"));
        assertEquals("aoeu", mCol.getMedia().escapeImages("aoeu"));
        assertEquals("<img src='http://foo.com'>", mCol.getMedia().escapeImages("<img src='http://foo.com'>"));
        assertEquals("<img src=\"foo%20bar.jpg\">", mCol.getMedia().escapeImages("<img src=\"foo bar.jpg\">"));
    }

    /* TODO: file
     @Test public void test_deckIntegration(){
     // create a media dir
     mCol.getMedia().dir();
     // put a file into it
     file = str(os.path.join(testDir, "support/fake.png"));
     mCol.getMedia().addFile(file);
     // add a note which references it
     Note note = mCol.newNote();
     note.setItem("Front","one");
     note.setItem("Back","<img src='fake.png'>");
     mCol.addNote(note);
     // and one which references a non-existent file
     Note note = mCol.newNote();
     note.setItem("Front","one");
     note.setItem("Back","<img src='fake2.png'>");
     mCol.addNote(note);
     // and add another file which isn't used
     with open(os.path.join(mCol.getMedia().dir(), "foo.jpg"), "w") as note:
     note.write("test");
     // check media
     ret = mCol.getMedia().check();
     assertEqualsArrayList(new String [] {"fake2.png"}, ret.missing);
     assertEqualsArrayList(new String [] {"foo.jpg"}, ret.unused);
     }
     */
}
