package com.ichi2.anki.jsaddons

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import junit.framework.TestCase.*
import org.apache.commons.compress.archivers.ArchiveException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class NpmPackageTgzExtractTest : InstrumentedTest() {
    var NPM_ADDON_TGZ_PACKAGE_NAME = "valid-ankidroid-js-addon-test-1.0.0.tgz"

    @get:Rule
    var mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /**
     * Test if the file is valid GZip file
     * @throws IOException
     */

    @Test
    @Throws(IOException::class)
    fun isGzipTest() {
        var tempPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)
        assertTrue(NpmPackageTgzExtract.isGzip(File(tempPath)))

        // testing .apkg file
        tempPath = Shared.getTestFilePath(testContext, "media.apkg")
        assertFalse(NpmPackageTgzExtract.isGzip(File(tempPath)))
    }

    /**
     * Test if extracted file exists in the output folder.
     * The current test will extract in .tgz in following structure
     * tempAddonDir
     * - package
     * - index.js
     * - README.md
     * - package.json
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun extractTarGzipToAddonFolderTest() {
        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)

        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        NpmPackageTgzExtract.extractTarGzipToAddonFolder(File(tgzPath), tempAddonDir)

        // test if package folder exists
        val packagePath = File(tempAddonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertTrue(File(indexJsPath.toString()).exists())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertTrue(File(readmePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())
    }

    /**
     * Test if .tar file unTar successfully to temp folder
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun unTarTest() {
        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)

        // first unGzip .tgz file to .tar
        val unGzipFile = NpmPackageTgzExtract.unGzip(File(tgzPath), tempAddonDir)

        // unTar .tar file to temp folder, it is same as extract of files to tempAddonDir
        NpmPackageTgzExtract.unTar(unGzipFile, tempAddonDir)

        // test if package folder exists
        val packagePath = File(tempAddonDir, "package")
        assertTrue(File(packagePath.toString()).exists())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertTrue(File(indexJsPath.toString()).exists())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertTrue(File(readmePath.toString()).exists())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertTrue(File(packageJsonPath.toString()).exists())
    }

    /**
     * Test if .tgz file successfully unGzipped
     * i.e. .tgz changed to .tar file
     *
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun unGzipTest() {
        val tempAddonDir = File(Files.createTempDirectory("AnkiDroid-addons").toString())
        val tgzPath = Shared.getTestFilePath(testContext, NPM_ADDON_TGZ_PACKAGE_NAME)
        val unGzipFile = NpmPackageTgzExtract.unGzip(File(tgzPath), tempAddonDir)

        // test if unGzip successfully return tar file
        assertTrue(File(unGzipFile.toString()).exists())

        // test if .tgz file changed to .tar file
        assertTrue(File(unGzipFile.toString()).absolutePath.endsWith(".tar"))
    }
}
