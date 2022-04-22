/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

@RunWith(AndroidJUnit4.class)
public class ImportUtilsTest extends RobolectricTest {

    @Test
    public void cjkNamesAreConvertedToUnicode() {
        //NOTE: I don't know whether this still needs to exist, but it was added as this previously crashes
        //and I would have added a regression without checking the history.
        //https://github.com/ankidroid/Anki-Android/commit/ed06954c8c678024e2fce25c19bd6cdaf0120260#diff-8eefa7f7b20c936f007c934965238520R58

        String inputFileName = "好.apkg";

        String actualFilePath = importValidFile(inputFileName);

        assertThat("Unicode character should be stripped", actualFilePath, not(containsString("好")));
        assertThat("Unicode character should be urlencoded", actualFilePath, endsWith("%E5%A5%BD.apkg"));
    }

    @Test
    public void fileNamesAreLimitedTo100Chars() {
        //#6137 - We URLEncode due to the above. Therefore: 好 -> %E5%A5%BD
        //This caused filenames to be too long.
        String inputFileName = "好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好.apkg";

        String actualFilePath = importValidFile(inputFileName);

        assertThat(actualFilePath, endsWith(".apkg"));
        assertThat(actualFilePath, containsString("..."));
        //Obtain the filename from the path
        assertThat(actualFilePath, containsString("%E5%A5%BD"));
        String fileName = actualFilePath.substring(actualFilePath.indexOf("%E5%A5%BD"));
        assertThat(fileName.length(), lessThanOrEqualTo(100));
    }

    private String importValidFile(String fileName) {
        TestFileImporter testFileImporter = new TestFileImporter(fileName);
        Intent intent = getValidClipDataUri(fileName);
        testFileImporter.handleFileImport(getTargetContext(), intent);
        String cacheFileName = testFileImporter.getCacheFileName();

        if (cacheFileName == null) {
            throw new IllegalStateException("No filename created");
        }

        //COULD_BE_BETTER: Strip off the file path
        return cacheFileName;
    }


    @Test
    public void collectionApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.apkg"));
    }

    @Test
    public void collectionColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.colpkg"));
    }

    @Test
    public void deckApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.apkg"));
    }

    @Test
    public void deckColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.colpkg"));
    }

    @Test
    public void nullIsNotValidPackage() {
        assertFalse(ImportUtils.isValidPackageName(null));
    }

    @Test
    public void docxIsNotValidForImport() {
        assertFalse(ImportUtils.isValidPackageName("test.docx"));
    }


    @CheckResult
    private Intent getValidClipDataUri(String fileName) {
        Intent i = new Intent();
        i.setClipData(clipDataUriFromFile(fileName));
        return i;
    }


    @NonNull
    private ClipData clipDataUriFromFile(String fileName) {
        ClipData.Item item = new ClipData.Item(Uri.parse("content://" + fileName));
        ClipDescription description = new ClipDescription("", new String[] {});
        return new ClipData(description, item);
    }


    @SuppressWarnings("WeakerAccess")
    public static class TestFileImporter extends ImportUtils.FileImporter {
        private String mCacheFileName;
        private final String mFileName;

        public TestFileImporter(String fileName) {
            this.mFileName = fileName;
        }

        @Override
        protected boolean copyFileToCache(Context context, Uri data, String tempPath) {
            this.mCacheFileName = tempPath;
            return true;
        }


        @Nullable
        @Override
        protected String getFileNameFromContentProvider(Context context, Uri data) {
            return mFileName;
        }


        public String getCacheFileName() {
            return mCacheFileName;
        }
    }
}
