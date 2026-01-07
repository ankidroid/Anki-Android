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
package com.ichi2.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.utils.ImportUtils.FileImporter
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportUtilsTest : RobolectricTest() {
    @Test
    fun cjkNamesAreConvertedToUnicode() {
        // NOTE: I don't know whether this still needs to exist, but it was added as this previously crashes
        // and I would have added a regression without checking the history.
        // https://github.com/ankidroid/Anki-Android/commit/ed06954c8c678024e2fce25c19bd6cdaf0120260#diff-8eefa7f7b20c936f007c934965238520R58

        val inputFileName = "好.apkg"

        val actualFilePath = importValidFile(inputFileName)

        assertThat("Unicode character should be stripped", actualFilePath, not(containsString("好")))
        assertThat("Unicode character should be urlencoded", actualFilePath, endsWith("%E5%A5%BD.apkg"))
    }

    @Test
    fun fileNamesAreLimitedTo100Chars() {
        // #6137 - We URLEncode due to the above. Therefore: 好 -> %E5%A5%BD
        // This caused filenames to be too long.
        val inputFileName = "好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好好.apkg"

        val actualFilePath = importValidFile(inputFileName)

        assertThat(actualFilePath, endsWith(".apkg"))
        assertThat(actualFilePath, containsString("..."))
        // Obtain the filename from the path
        assertThat(actualFilePath, containsString("%E5%A5%BD"))
        val fileName = actualFilePath.substring(actualFilePath.indexOf("%E5%A5%BD"))
        assertThat(fileName.length, lessThanOrEqualTo(100))
    }

    private fun importValidFile(fileName: String): String {
        val testFileImporter = TestFileImporter(fileName)
        val intent = getValidClipDataUri(fileName)
        testFileImporter.handleFileImport(targetContext, intent)

        // COULD_BE_BETTER: Strip off the file path
        return testFileImporter.cacheFileName
    }

    @Test
    fun collectionApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.apkg"))
    }

    @Test
    fun collectionColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("collection.colpkg"))
    }

    @Test
    fun deckApkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.apkg"))
    }

    @Test
    fun deckColPkgIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deckName.colpkg"))
    }

    @Test
    fun nullIsNotValidPackage() {
        assertFalse(ImportUtils.isValidPackageName(null))
    }

    @Test
    fun docxIsNotValidForImport() {
        assertFalse(ImportUtils.isValidPackageName("test.docx"))
    }

    @Test
    fun emptyStringIsNotValidPackage() {
        assertFalse(ImportUtils.isValidPackageName(""))
    }

    @Test
    fun blankStringIsNotValidPackage() {
        assertFalse(ImportUtils.isValidPackageName("   "))
    }

    @Test
    fun fileWithoutExtensionIsNotValid() {
        assertFalse(ImportUtils.isValidPackageName("collection"))
    }

    @Test
    fun fileWithOnlyDotIsNotValid() {
        assertFalse(ImportUtils.isValidPackageName("."))
    }

    @Test
    fun apkgWithUppercaseExtensionIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deck.APKG"))
    }

    @Test
    fun colpkgWithMixedCaseExtensionIsValid() {
        assertTrue(ImportUtils.isValidPackageName("deck.ColPkg"))
    }

    @Test
    fun apkgWithMultipleDotsInNameIsValid() {
        assertTrue(ImportUtils.isValidPackageName("my.deck.name.apkg"))
    }

    @Test
    fun colpkgWithMultipleDotsInNameIsValid() {
        assertTrue(ImportUtils.isValidPackageName("my.deck.name.colpkg"))
    }

    @Test
    fun fileWithTrailingSpacesIsNotValid() {
        assertFalse(ImportUtils.isValidPackageName("deck.apkg "))
    }

    @Test
    fun fileWithLeadingSpacesIsNotValid() {
        assertFalse(ImportUtils.isValidPackageName(" deck.apkg"))
    }

    @Test
    fun wrongExtensionIsNotValid() {
        assertFalse(ImportUtils.isValidPackageName("deck.zip"))
        assertFalse(ImportUtils.isValidPackageName("deck.txt"))
        assertFalse(ImportUtils.isValidPackageName("deck.pdf"))
    }

    @Test
    fun filenameWithSpecialCharactersIsValidIfExtensionCorrect() {
        assertTrue(ImportUtils.isValidPackageName("deck@#$%.apkg"))
        assertTrue(ImportUtils.isValidPackageName("deck(1).colpkg"))
    }

    @Test
    fun veryLongFilenameWithValidExtension() {
        val longName = "a".repeat(500) + ".apkg"
        assertTrue(ImportUtils.isValidPackageName(longName))
    }

    @Test
    fun filenameWithPathSeparatorsIsValidIfExtensionCorrect() {
        assertTrue(ImportUtils.isValidPackageName("/path/to/deck.apkg"))
        assertTrue(ImportUtils.isValidPackageName("C:\\path\\to\\deck.colpkg"))
    }

    @Test
    fun filenameEncodingPreservesExtension() {
        val inputFileName = "測試.apkg"
        val actualFilePath = importValidFile(inputFileName)

        assertThat("Extension should be preserved", actualFilePath, endsWith(".apkg"))
        assertThat("Filename should be URL encoded", actualFilePath, containsString("%"))
    }

    @Test
    fun extremelyLongUnicodeFilenameIsHandled() {
        val inputFileName = "好".repeat(100) + ".apkg"
        val actualFilePath = importValidFile(inputFileName)

        assertThat("Should have ellipsis for truncation", actualFilePath, containsString("..."))
        assertThat("Extension should be preserved", actualFilePath, endsWith(".apkg"))

        val fileName = actualFilePath.substring(actualFilePath.lastIndexOf('/') + 1)
        assertThat("Filename should be within limit", fileName.length, lessThanOrEqualTo(100))
    }

    @Test
    fun mixedUnicodeAndAsciiFilename() {
        val inputFileName = "My好Deck好.apkg"
        val actualFilePath = importValidFile(inputFileName)

        assertThat("Should preserve ASCII characters", actualFilePath, containsString("My"))
        assertThat("Should preserve ASCII characters", actualFilePath, containsString("Deck"))
        assertThat("Should URL encode unicode", actualFilePath, containsString("%"))
        assertThat("Extension should be preserved", actualFilePath, endsWith(".apkg"))
    }

    @Test
    fun filenameWithDotsAndUnicode() {
        val inputFileName = "my.好.deck.apkg"
        val actualFilePath = importValidFile(inputFileName)

        assertThat("Extension should be preserved", actualFilePath, endsWith(".apkg"))
        assertThat("Should contain URL encoded unicode", actualFilePath, containsString("%"))
    }

    @Test
    fun colpkgFilenameLengthLimiting() {
        val inputFileName = "好".repeat(50) + ".colpkg"
        val actualFilePath = importValidFile(inputFileName)

        assertThat("Extension should be preserved", actualFilePath, endsWith(".colpkg"))
        val fileName = actualFilePath.substring(actualFilePath.lastIndexOf('/') + 1)
        assertThat("Filename should be within limit", fileName.length, lessThanOrEqualTo(100))
    }

    @CheckResult
    private fun getValidClipDataUri(fileName: String): Intent {
        val i = Intent()
        i.clipData = clipDataUriFromFile(fileName)
        return i
    }

    private fun clipDataUriFromFile(fileName: String): ClipData {
        val item = ClipData.Item("content://$fileName".toUri())
        val description = ClipDescription("", arrayOf())
        return ClipData(description, item)
    }

    class TestFileImporter(
        private val fileName: String?,
    ) : FileImporter() {
        lateinit var cacheFileName: String
            private set

        override fun copyFileToCache(
            context: Context,
            data: Uri,
            tempPath: String,
        ) = run {
            cacheFileName = tempPath
            CacheFileResult.Success(tempPath)
        }

        override fun getFileNameFromContentProvider(
            context: Context,
            data: Uri,
        ): String? = fileName
    }
}
