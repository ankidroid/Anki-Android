package com.ichi2.libanki;

import junit.framework.Assert;

//import net.lachlanmckee.timberjunit.TimberTestRule;

//import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UtilsTest {

    // This depends on a Timber upgrade that should be pursued separately
    //@Rule
    //public TimberTestRule logAllAlwaysRule = TimberTestRule.logAllAlways();

    @Test
    public void testZipWithPathTraversal() {

        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource("path-traversal.zip");
        File file = new File(resource.getPath());
        try {
            ZipFile zipFile = new ZipFile(file);
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry ze2 = (ZipEntry) zipEntries.nextElement();
                Utils.unzipFiles(zipFile, "/tmp", new String[]{ze2.getName()}, null);
            }
            Assert.fail("Expected an IOException");
        }
        catch (IOException e) {
            Assert.assertEquals(e.getMessage(), "File is outside extraction target directory.");
        }
    }

    @Test
    public void testInvalidPaths() {
        try {
            File tmpDir = new File("/tmp");
            Assert.assertFalse(Utils.isInside(new File(tmpDir, "../foo"), tmpDir));
            Assert.assertFalse(Utils.isInside(new File(tmpDir, "/tmp/one/../../../foo"), tmpDir));
        } catch (IOException ioe) {
            Assert.fail("Unexpected exception: " + ioe);
        }
    }

    @Test
    public void testValidPaths() {
        try {
            File tmpDir = new File("/tmp");
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "test/file/path/no/parent"), tmpDir));
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "/tmp/absolute/path"), tmpDir));
            Assert.assertTrue(Utils.isInside(new File(tmpDir, "test/file/../../"), tmpDir));
        } catch (IOException ioe) {
            Assert.fail("Unexpected exception: " + ioe);
        }
    }
}
