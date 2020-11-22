/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static android.content.ContentResolver.SCHEME_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class) // needs a URI instance
public class ContentResolverUtilTest {

    @Test
    public void testViaQueryWorking() {
        Uri uri = Uri.parse("http://example.com/test.jpeg");
        ContentResolver mock = mock(ContentResolver.class);

        setQueryReturning(mock, cursorReturning("filename_from_cursor.jpg"));

        String filename = ContentResolverUtil.getFileName(mock, uri);

        assertThat(filename, is("filename_from_cursor.jpg"));
    }

    @Test
    public void testViaMimeType() {
        // #7748: Query can fail on some phones, so fall back to MIME
        // values obtained via: content://com.google.android.inputmethod.latin.fileprovider/content/tenor_gif/tenor_gif187746302992141903.gif
        Uri uri = mock(Uri.class);
        when(uri.getScheme()).thenReturn(SCHEME_CONTENT);

        ContentResolver mock = mock(ContentResolver.class);
        setQueryThrowing(mock, new SQLiteException("no such column: _display_name (code 1 SQLITE_ERROR[1]): , " +
                "while compiling: SELECT _display_name FROM ClipboardImageTable WHERE (id=855) ORDER BY _data"));

        when(mock.getType(any())).thenReturn("image/gif");
        // required for Robolectric
        Shadows.shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypMapping("gif", "image/gif");

        String filename = ContentResolverUtil.getFileName(mock, uri);


        // maybe we could do better here, but general guidance is to not parse the uri string
        assertThat(filename, is("image.gif"));
    }


    @NonNull
    protected Cursor cursorReturning(@SuppressWarnings("SameParameterValue") String value) {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getString(0)).thenReturn(value);
        return cursor;
    }


    protected void setQueryReturning(ContentResolver mock, Cursor cursorToReturn) {
        when(mock.query(any(), any(), any(), any(), any())).thenReturn(cursorToReturn);
    }

    protected void setQueryThrowing(ContentResolver mock, Throwable ex) {
        when(mock.query(any(), any(), any(), any(), any())).thenThrow(ex);
    }
}
