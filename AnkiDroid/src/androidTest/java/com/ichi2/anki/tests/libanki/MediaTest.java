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

import android.Manifest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.ichi2.anki.BackupManager;
import com.ichi2.anki.tests.InstrumentedTest;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Note;
import com.ichi2.libanki.exception.EmptyMediaException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Unit tests for {@link Media}.
 */
@RunWith(AndroidJUnit4.class)
public class MediaTest extends InstrumentedTest {

    private Collection mTestCol;

    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Before
    public void setUp() throws IOException {
        mTestCol = getEmptyCol();
    }

    @After
    public void tearDown() {
        mTestCol.close();
    }

    @Test
    public void testAdd() throws IOException, EmptyMediaException {
        // open new empty collection
        File dir = getTestDir();
        BackupManager.removeDir(dir);
        assertTrue(dir.mkdirs());
        File path = new File(dir, "foo.jpg");
        FileOutputStream os = new FileOutputStream(path, false);
        os.write("hello".getBytes());
        os.close();
        // new file, should preserve name
        String r = mTestCol.getMedia().addFile(path);
        assertEquals("foo.jpg", r);
        // adding the same file again should not create a duplicate
        assertEquals("foo.jpg", mTestCol.getMedia().addFile(path));
        // but if it has a different md5, it should
        os = new FileOutputStream(path, false);
        os.write("world".getBytes());
        os.close();
        assertNotEquals("foo.jpg", mTestCol.getMedia().addFile(path));
    }

    @Test
    public void testAddEmptyFails() throws IOException {
        // open new empty collection
        File dir = getTestDir();
        BackupManager.removeDir(dir);
        assertTrue(dir.mkdirs());
        File path = new File(dir, "foo.jpg");
        assertTrue(path.createNewFile());

        // new file, should preserve name
        try {
            mTestCol.getMedia().addFile(path);
            fail("exception should be thrown");
        } catch (EmptyMediaException mediaException) {
            // all good
        }
    }


    @Test
    public void testStrings() {
        Long mid = mTestCol.getModels().getModels().entrySet().iterator().next().getKey();

        List<String> expected = Collections.emptyList();
        List<String> actual = mTestCol.getMedia().filesInStr(mid, "aoeu");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Collections.singletonList("foo.jpg");
        actual = mTestCol.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg", "bar.jpg");
        actual = mTestCol.getMedia().filesInStr(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Collections.singletonList("foo.jpg");
        actual = mTestCol.getMedia().filesInStr(mid, "aoeu<img src=foo.jpg style=bar>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("one", "two");
        actual = mTestCol.getMedia().filesInStr(mid, "<img src=one><img src=two>");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Collections.singletonList("foo.jpg");
        actual = mTestCol.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\">ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Arrays.asList("foo.jpg", "fo");
        actual = mTestCol.getMedia().filesInStr(mid, "aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        expected = Collections.singletonList("foo.mp3");
        actual = mTestCol.getMedia().filesInStr(mid, "aou[sound:foo.mp3]aou");
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());

        assertEquals("aoeu", mTestCol.getMedia().strip("aoeu"));
        assertEquals("aoeuaoeu", mTestCol.getMedia().strip("aoeu[sound:foo.mp3]aoeu"));
        assertEquals("aoeu", mTestCol.getMedia().strip("a<img src=yo>oeu"));
        assertEquals("aoeu", Media.escapeImages("aoeu"));
        assertEquals("<img src='http://foo.com'>", Media.escapeImages("<img src='http://foo.com'>"));
        assertEquals("<img src=\"foo%20bar.jpg\">", Media.escapeImages("<img src=\"foo bar.jpg\">"));
    }

    @Test
    public void testDeckIntegration() throws IOException, EmptyMediaException {
        // create a media dir
        mTestCol.getMedia().dir();
        // Put a file into it
        File file = createNonEmptyFile("fake.png");
        mTestCol.getMedia().addFile(file);
        // add a note which references it
        Note f = mTestCol.newNote();
        f.setField(0, "one");
        f.setField(1, "<img src='fake.png'>");
        mTestCol.addNote(f);
        // and one which references a non-existent file
        f = mTestCol.newNote();
        f.setField(0, "one");
        f.setField(1, "<img src='fake2.png'>");
        mTestCol.addNote(f);
        // and add another file which isn't used
        FileOutputStream os = new FileOutputStream(new File(mTestCol.getMedia().dir(), "foo.jpg"), false);
        os.write("test".getBytes());
        os.close();
        // check media
        List<List<String>> ret = mTestCol.getMedia().check();
        List<String> expected = Collections.singletonList("fake2.png");
        List<String> actual = ret.get(0);
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
        expected = Collections.singletonList("foo.jpg");
        actual = ret.get(1);
        actual.retainAll(expected);
        assertEquals(expected.size(), actual.size());
    }

    private List<String> added(Collection d) {
        return d.getMedia().getDb().queryStringList("select fname from media where csum is not null");
    }

    private List<String> removed(Collection d) {
        return d.getMedia().getDb().queryStringList("select fname from media where csum is null");
    }

    @Test
    public void testChanges() throws IOException, EmptyMediaException {
        assertNotNull(mTestCol.getMedia()._changed());
        assertEquals(0, added(mTestCol).size());
        assertEquals(0, removed(mTestCol).size());
        // add a file
        File dir = getTestDir();
        File path = new File(dir, "foo.jpg");
        FileOutputStream os = new FileOutputStream(path, false);
        os.write("hello".getBytes());
        os.close();
        path = new File(mTestCol.getMedia().dir(), mTestCol.getMedia().addFile(path));
        // should have been logged
        mTestCol.getMedia().findChanges();
        assertThat(added(mTestCol).size(), is(greaterThan(0)));
        assertEquals(0, removed(mTestCol).size());
        // if we modify it, the cache won't notice
        os = new FileOutputStream(path, true);
        os.write("world".getBytes());
        os.close();
        assertEquals(1, added(mTestCol).size());
        assertEquals(0, removed(mTestCol).size());
        // but if we add another file, it will
        path = new File(path.getAbsolutePath()+"2");
        os = new FileOutputStream(path, true);
        os.write("yo".getBytes());
        os.close();
        mTestCol.getMedia().findChanges(true);
        assertEquals(2, added(mTestCol).size());
        assertEquals(0, removed(mTestCol).size());
        // deletions should get noticed too
        assertTrue(path.delete());
        mTestCol.getMedia().findChanges(true);
        assertEquals(1, added(mTestCol).size());
        assertEquals(1, removed(mTestCol).size());
    }


    @Test
    public void testIllegal() {
        String aString = "a:b|cd\\e/f\0g*h\\[i\\]j";
        String good = "abcdefghij";
        assertEquals(good, mTestCol.getMedia().stripIllegal(aString));
        for (int i = 0; i < aString.length(); i++) {
            char c = aString.charAt(i);
            boolean bad = mTestCol.getMedia().hasIllegal("something" + c + "morestring");
            if (bad) {
                assertEquals(-1, good.indexOf(c));
            } else {
                assertNotEquals(-1, good.indexOf(c));
            }
        }
    }

    protected File createNonEmptyFile(String fileName) throws IOException {
        File file = new File(getTestDir(), fileName);
        try (FileOutputStream os = new FileOutputStream(file, false)) {
            os.write("a".getBytes());
        }
        return file;
    }
}
