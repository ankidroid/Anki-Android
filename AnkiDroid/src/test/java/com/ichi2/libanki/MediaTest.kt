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
import com.ichi2.testutils.AnkiAssert.assertEqualsArrayList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaTest : RobolectricTest() {
    /*****************
     ** Media        *
     *****************/
    // copying files to media directory

    /* TODO: media
       @Test
       public void test_add(){
       Collection col = getCol();
       String dir = tempfile.mkdtemp(prefix="anki");
       String path = os.path.join(dir, "foo.jpg");
       with open(path, "w") as note:
       note.write("hello");
       // new file, should preserve name
       assertEquals("foo.jpg", col.getMedia().addFile(path));
       // adding the same file again should not create a duplicate
       assertEquals("foo.jpg", col.getMedia().addFile(path));
       // but if it has a different sha1, it should
       with open(path, "w") as note:
       note.write("world");
       assertEquals("foo-7c211433f02071597741e6ff5a8ea34789abbf43.jpg", col.getMedia().addFile(path));
       } */
    @Test
    fun test_strings() {
        val mid = col.models.current(col)!!.getLong("id")
        assertEquals(0, col.media.filesInStr(mid, "aoeu").size.toLong())
        assertEqualsArrayList(arrayOf("foo.jpg"), col.media.filesInStr(mid, "aoeu<img src='foo.jpg'>ao"))
        assertEqualsArrayList(arrayOf("foo.jpg"), col.media.filesInStr(mid, "aoeu<img src='foo.jpg' style='test'>ao"))
        assertEqualsArrayList(arrayOf("foo.jpg", "bar.jpg"), col.media.filesInStr(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao"))
        assertEqualsArrayList(arrayOf("foo.jpg"), col.media.filesInStr(mid, "aoeu<img src=foo.jpg style=bar>ao"))
        assertEqualsArrayList(arrayOf("one", "two"), col.media.filesInStr(mid, "<img src=one><img src=two>"))
        assertEqualsArrayList(arrayOf("foo.jpg"), col.media.filesInStr(mid, "aoeu<img src=\"foo.jpg\">ao"))
        assertEqualsArrayList(
            arrayOf("foo.jpg", "fo"),
            col.media.filesInStr(mid, "aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao")
        )
        assertEqualsArrayList(arrayOf("foo.mp3"), col.media.filesInStr(mid, "aou[sound:foo.mp3]aou"))
        assertEquals("aoeu", col.media.strip("aoeu"))
        assertEquals("aoeuaoeu", col.media.strip("aoeu[sound:foo.mp3]aoeu"))
        assertEquals("aoeu", col.media.strip("a<img src=yo>oeu"))
        assertEquals("aoeu", Media.escapeImages("aoeu"))
        assertEquals("<img src='http://foo.com'>", Media.escapeImages("<img src='http://foo.com'>"))
        assertEquals("<img src=\"foo%20bar.jpg\">", Media.escapeImages("<img src=\"foo bar.jpg\">"))
    }

    /* TODO: file
     @Test public void test_deckIntegration(){
     Collection col = getCol();
     // create a media dir
     col.getMedia().dir();
     // put a file into it
     file = str(os.path.join(testDir, "support/fake.png"));
     col.getMedia().addFile(file);
     // add a note which references it
     Note note = col.newNote();
     note.setItem("Front","one");
     note.setItem("Back","<img src='fake.png'>");
     col.addNote(note);
     // and one which references a non-existent file
     Note note = col.newNote();
     note.setItem("Front","one");
     note.setItem("Back","<img src='fake2.png'>");
     col.addNote(note);
     // and add another file which isn't used
     with open(os.path.join(col.getMedia().dir(), "foo.jpg"), "w") as note:
     note.write("test");
     // check media
     ret = col.getMedia().check();
     assertEqualsArrayList(new String [] {"fake2.png"}, ret.missing);
     assertEqualsArrayList(new String [] {"foo.jpg"}, ret.unused);
     }
     */
}
