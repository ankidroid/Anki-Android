/****************************************************************************************
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.tests.libanki;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.Suppress;

import com.ichi2.anki.BackupManager;
import com.ichi2.anki.tests.Shared;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Note;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;


/**
 * Unit tests for {@link Media}.
 */
public class MediaTest extends AndroidTestCase {
    public void testAdd() throws IOException {
        // open new empty collection
        Collection d = Shared.getEmptyCol(getContext());
        File dir = Shared.getTestDir(getContext());
        BackupManager.removeDir(dir);
        dir.mkdirs();
        File path = new File(dir, "foo.jpg");
        FileOutputStream os;
        os = new FileOutputStream(path, false);
        os.write("hello".getBytes());
        os.close();
        // new file, should preserve name
        String r = d.getMedia().addFile(path);
        assertEquals("foo.jpg", r);
        // adding the same file again should not create a duplicate
        assertEquals("foo.jpg", d.getMedia().addFile(path));
        // but if it has a different md5, it should
        os = new FileOutputStream(path, false);
        os.write("world".getBytes());
        os.close();
        assertEquals("foo (1).jpg", d.getMedia().addFile(path));
    }


    public void testStrings() throws IOException {
        Collection d = Shared.getEmptyCol(getContext());
        Long mid = d.getModels().getModels().entrySet().iterator().next().getKey();
        List<String> expected;
        List<String> actual;

        expected = Arrays.asList();
        actual = d.getMedia().filesInStr(mid, "aoeu");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg");
        actual = d.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg", "bar.jpg");
        actual = d.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg");
        actual = d.getMedia().filesInStr(mid, "aoeu<img src=foo.jpg style=bar>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("one", "two");
        actual = d.getMedia().filesInStr(mid, "<img src=one><img src=two>");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg");
        actual = d.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\">ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg", "fo");
        actual = d.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.mp3");
        actual = d.getMedia().filesInStr(mid, "aou[sound:foo.mp3]aou");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        assertEquals("aoeu", d.getMedia().strip("aoeu"));
        assertEquals("aoeuaoeu", d.getMedia().strip("aoeu[sound:foo.mp3]aoeu"));
        assertEquals("aoeu", d.getMedia().strip("a<img src=yo>oeu"));
        assertEquals("aoeu", d.getMedia().escapeImages("aoeu"));
        assertEquals("<img src='http://foo.com'>", d.getMedia().escapeImages("<img src='http://foo.com'>"));
        assertEquals("<img src=\"foo%20bar.jpg\">", d.getMedia().escapeImages("<img src=\"foo bar.jpg\">"));
    }

    public void testDeckIntegration() throws IOException {
        Collection d = Shared.getEmptyCol(getContext());
        // create a media dir
        d.getMedia().dir();
        // Put a file into it
        File file = new File(Shared.getTestDir(getContext()), "fake.png");
        file.createNewFile();
        d.getMedia().addFile(file);
        // add a note which references it
        Note f = d.newNote();
        f.setItem("Front", "one");
        f.setItem("Back", "<img src='fake.png'>");
        d.addNote(f);
        // and one which references a non-existent file
        f = d.newNote();
        f.setItem("Front", "one");
        f.setItem("Back", "<img src='fake2.png'>");
        d.addNote(f);
        // and add another file which isn't used
        FileOutputStream os;
        os = new FileOutputStream(new File(d.getMedia().dir(), "foo.jpg"), false);
        os.write("test".getBytes());
        os.close();
        // check media
        List<List<String>> ret = d.getMedia().check();
        List<String> expected;
        List<String> actual;
        expected = Arrays.asList("fake2.png");
        actual = ret.get(0);
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        expected = Arrays.asList("foo.jpg");
        actual = ret.get(1);
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
    }


    @Suppress
    private List<String> added(Collection d) {
        return d.getMedia().getDb().queryColumn(String.class, "select fname from media where csum is not null", 0);
    }

    @Suppress
    private List<String> removed(Collection d) {
        return d.getMedia().getDb().queryColumn(String.class, "select fname from media where csum is null", 0);
    }

    public void testChanges() throws IOException {
        Collection d = Shared.getEmptyCol(getContext());
        assertTrue(d.getMedia()._changed() != null);
        assertTrue(added(d).size() == 0);
        assertTrue(removed(d).size() == 0);
        // add a file
        File dir = Shared.getTestDir(getContext());
        File path = new File(dir, "foo.jpg");
        FileOutputStream os;
        os = new FileOutputStream(path, false);
        os.write("hello".getBytes());
        os.close();
        path = new File(d.getMedia().dir(), d.getMedia().addFile(path));
        // should have been logged
        d.getMedia().findChanges();
        assertTrue(added(d).size() > 0);
        assertTrue(removed(d).size() == 0);
        // if we modify it, the cache won't notice
        os = new FileOutputStream(path, true);
        os.write("world".getBytes());
        os.close();
        assertTrue(added(d).size() == 1);
        assertTrue(removed(d).size() == 0);
        // but if we add another file, it will
        path = new File(path.getAbsolutePath()+"2");
        os = new FileOutputStream(path, true);
        os.write("yo".getBytes());
        os.close();
        d.getMedia().findChanges(true);
        assertTrue(added(d).size() == 2);
        assertTrue(removed(d).size() == 0);
        // deletions should get noticed too
        path.delete();
        d.getMedia().findChanges(true);
        assertTrue(added(d).size() == 1);
        assertTrue(removed(d).size() == 1);
    }


    public void testIllegal() throws IOException {
        Collection d = Shared.getEmptyCol(getContext());
        String aString = "a:b|cd\\e/f\0g*h";
        String good = "abcdefgh";
        assertTrue(d.getMedia().stripIllegal(aString).equals(good));
        for (int i = 0; i < aString.length(); i++) {
            char c = aString.charAt(i);
            boolean bad = d.getMedia().hasIllegal("something" + c + "morestring");
            if (bad) {
                assertTrue(good.indexOf(c) == -1);
            } else {
                assertTrue(good.indexOf(c) != -1);
            }
        }
    }
}
