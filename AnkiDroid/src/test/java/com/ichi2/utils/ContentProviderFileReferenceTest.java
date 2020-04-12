package com.ichi2.utils;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
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

        ContentResolver resolver = mock(ContentResolver.class);
        setupLocalPathResolverReturning(uri, resolver, "/storage/emulated/0/DCIM/Facebook/FB_IMG_1586592500530.jpg");

        ContentProviderFileReference underTest = new ContentProviderFileReference();


        String actual = underTest.extractFilePath(resolver, uri, null);

        String expected = "/storage/emulated/0/DCIM/Facebook/FB_IMG_1586592500530.jpg";
        assertThat("File path should be returned correctly", actual, equalTo(expected));
    }

    @Test
    public void googlePhotosDownloadFileRegressionTest() throws IOException {
        //Note: This creates a temporary file in "java.io.tmpdir", and cleans it up.
        //copied data from a streaming/download "google photos" file into a mock.
        Uri uri = Uri.parse("content://com.google.android.apps.photos.contentprovider/0/1/content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F438342/ORIGINAL/NONE/image%2Fjpeg/1178426345");

        byte[] fileData = new byte[] { 123 };
        String fileName = "IMG_20200330_135308.jpg";
        String actualFilePath = null;
        try {
            ContentResolver resolver = mock(ContentResolver.class);
            setupLocalPathResolverReturning(uri, resolver, null);
            setupDownloadingResolverReturning(uri, resolver, fileName, fileData);

            //Act
            ContentProviderFileReference underTest = new ContentProviderFileReference();
            String tempDirectory = System.getProperty("java.io.tmpdir");
            actualFilePath = underTest.extractFilePath(resolver, uri, tempDirectory);

            //Assert
            String expected = new File(tempDirectory, fileName).getAbsolutePath();
            assertThat("File path should be returned correctly", actualFilePath, equalTo(expected));

            byte[] actualData = Files.readAllBytes(Paths.get(actualFilePath));

            //COULD_BE_BETTER: seems to be no primitive matcher for byte.
            assertThat("Data should be the same", actualData[0], is(fileData[0]));
        } finally {
            if (actualFilePath != null) {
                Files.delete(Paths.get(actualFilePath));
            }
        }
    }


    private void setupDownloadingResolverReturning(Uri uri, ContentResolver resolver, String fileName, byte[] fileData) {
        //Setup filename
        Cursor downloadCursor = mock(Cursor.class);
        String[] input = new String[] { MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE };
        when(resolver.query(eq(uri), eq(input), isNull(), isNull(), isNull())).thenReturn(downloadCursor);
        when(downloadCursor.moveToFirst()).thenReturn(true);
        when(downloadCursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)).thenReturn(0);
        when(downloadCursor.getColumnIndex(MediaStore.MediaColumns.TITLE)).thenReturn(1);

        when(downloadCursor.getString(0)).thenReturn(fileName);
        //Different: No data.
        when(downloadCursor.getString(1)).thenReturn(null);

        try {
            when(resolver.openInputStream(uri)).thenReturn(new ByteArrayInputStream(fileData));
        } catch (FileNotFoundException e) {
            fail();
        }
    }


    private void setupLocalPathResolverReturning(Uri uri, ContentResolver resolver, String result) {
        Cursor localDataCursor = mock(Cursor.class);
        when(resolver.query(eq(uri), eq(new String[] { MediaStore.MediaColumns.DATA }), isNull(), isNull(), isNull())).thenReturn(localDataCursor);
        when(localDataCursor.moveToFirst()).thenReturn(true);
        when(localDataCursor.getColumnIndex(MediaStore.MediaColumns.DATA)).thenReturn(0);
        //Different: No data.
        when(localDataCursor.getString(0)).thenReturn(result);
    }
}
