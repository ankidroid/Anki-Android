/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
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

package com.ichi2.compat;

import com.ichi2.anki.TestUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.utils.FileOperation.getFileResource;

@RunWith(AndroidJUnit4.class)
@Config(sdk = { 21, 26 })
public class CompatCopyFileTest {

    @Test
    public void testCopyFileToStream() throws Exception {
        String resourcePath = getFileResource("path-traversal.zip");
        File copy = File.createTempFile("testCopyFileToStream", ".zip");
        copy.deleteOnExit();
        FileOutputStream outputStream = new FileOutputStream(copy.getCanonicalPath());
        CompatHelper.getCompat().copyFile(resourcePath, outputStream);
        outputStream.close();
        Assert.assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.getCanonicalPath()));
    }


    @Test
    public void testCopyStreamToFile() throws Exception {
        String resourcePath = getFileResource("path-traversal.zip");
        File copy = File.createTempFile("testCopyStreamToFile", ".zip");
        copy.deleteOnExit();
        CompatHelper.getCompat().copyFile(resourcePath, copy.getCanonicalPath());
        Assert.assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.getCanonicalPath()));
    }


    @Test
    public void testCopyErrors() throws Exception {
        String resourcePath = getFileResource("path-traversal.zip");
        File copy = File.createTempFile("testCopyStreamToFile", ".zip");
        copy.deleteOnExit();

        // Try copying from a bogus file
        try {
            CompatHelper.getCompat().copyFile(new FileInputStream(""), copy.getCanonicalPath());
            Assert.fail("Should have caught an exception");
        } catch (FileNotFoundException e) {
            // This is expected
        }

        // Try copying to a closed stream
        try {
            FileOutputStream outputStream = new FileOutputStream(copy.getCanonicalPath());
            outputStream.close();
            CompatHelper.getCompat().copyFile(resourcePath, outputStream);
            Assert.fail("Should have caught an exception");
        } catch (IOException e) {
            // this is expected
        }

        // Try copying from a closed stream
        try {
            InputStream source = new URL(resourcePath).openStream();
            source.close();
            CompatHelper.getCompat().copyFile(source, copy.getCanonicalPath());
            Assert.fail("Should have caught an exception");
        } catch (IOException e) {
            // this is expected
        }

        // Try copying to a bogus file
        try {
            CompatHelper.getCompat().copyFile(resourcePath, "");
            Assert.fail("Should have caught an exception");
        } catch (Exception e) {
            // this is expected
        }
    }
}