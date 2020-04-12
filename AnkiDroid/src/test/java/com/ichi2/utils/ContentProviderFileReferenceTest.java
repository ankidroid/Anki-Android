package com.ichi2.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ContentProviderFileReferenceTest {

    @Test
    public void googlePhotosLocalFileRegressionTest() {
        //copied data from a local "google photos" file into a mock.
        Uri uri = Uri.parse("content://com.google.android.apps.photos.contentprovider/0/1/content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F438342/ORIGINAL/NONE/image%2Fjpeg/1178426345");

        Cursor cursor = mock(Cursor.class);
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.query(eq(uri), any(), isNull(), isNull(), isNull())).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getColumnIndex(MediaStore.MediaColumns.DATA)).thenReturn(0);
        when(cursor.getString(0)).thenReturn("/storage/emulated/0/DCIM/Facebook/FB_IMG_1586592500530.jpg");

        ContentProviderFileReference underTest = new ContentProviderFileReference();


        String actual = underTest.extractFilePath(resolver, uri);

        String expected = "/storage/emulated/0/DCIM/Facebook/FB_IMG_1586592500530.jpg";
        assertThat("File path should be returned correctly", actual, equalTo(expected));
    }
}
