/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2021 Kael Madar <itsybitsyspider@madarhome.com>
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
package com.ichi2.anki.cardviewer

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.function.Consumer

// PERF:
// Theoretically should be able to get away with not using this, but it requires WebResourceRequest (easy to mock)
// and URLUtil.guessFileName (static - likely harder)
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class MissingImageHandlerTest {
    private lateinit var mSut: MissingImageHandler
    private var mTimesCalled = 0
    private lateinit var mFileNames: MutableList<String?>

    @Before
    fun before() {
        mFileNames = ArrayList()
        mSut = MissingImageHandler()
    }

    private fun defaultHandler(): Consumer<String?> {
        return Consumer { f: String? ->
            mTimesCalled++
            mFileNames.add(f)
        }
    }

    @Test
    fun firstTimeOnNewCardSends() {
        processFailure(getValidRequest("example.jpg"))
        assertThat(mTimesCalled, equalTo(1))
        assertThat(mFileNames, contains("example.jpg"))
    }

    @Test
    fun twoCallsOnSameSideCallsOnce() {
        processFailure(getValidRequest("example.jpg"))
        processFailure(getValidRequest("example2.jpg"))
        assertThat(mTimesCalled, equalTo(1))
        assertThat(mFileNames, contains("example.jpg"))
    }

    @Test
    fun callAfterFlipIsShown() {
        processFailure(getValidRequest("example.jpg"))
        mSut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        assertThat(mTimesCalled, equalTo(2))
        assertThat(mFileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun thirdCallIsIgnored() {
        processFailure(getValidRequest("example.jpg"))
        mSut.onCardSideChange()
        processFailure(getValidRequest("example2.jpg"))
        mSut.onCardSideChange()
        processFailure(getValidRequest("example3.jpg"))
        assertThat(mTimesCalled, equalTo(2))
        assertThat(mFileNames, contains("example.jpg", "example2.jpg"))
    }

    @Test
    fun invalidRequestIsIgnored() {
        val invalidRequest = getInvalidRequest("example.jpg")
        processFailure(invalidRequest)
        assertThat(mTimesCalled, equalTo(0))
    }

    private fun processFailure(invalidRequest: WebResourceRequest, consumer: Consumer<String?> = defaultHandler()) {
        mSut.processFailure(invalidRequest, consumer)
    }

    private fun processMissingSound(file: File?, onFailure: Consumer<String?>) {
        mSut.processMissingSound(file, onFailure)
    }

    private fun processInefficientImage(onFailure: Runnable) {
        mSut.processInefficientImage(onFailure)
    }

    @Test
    fun uiFailureDoesNotCrash() {
        processFailure(getValidRequest("example.jpg")) { throw RuntimeException("expected") }
        assertThat("Irrelevant assert to stop lint warnings", mTimesCalled, equalTo(0))
    }

    @Test
    fun testMissingSound_NullFile() {
        processMissingSound(null, defaultHandler())
        assertThat(mTimesCalled, equalTo(0))
    }

    @Test
    fun testThirdSoundIsIgnored() {
        // Tests that the third call to processMissingSound is ignored
        val handler = defaultHandler()
        processMissingSound(File("example.wav"), handler)
        mSut.onCardSideChange()
        processMissingSound(File("example2.wav"), handler)
        mSut.onCardSideChange()
        processMissingSound(File("example3.wav"), handler)
        assertThat(mTimesCalled, equalTo(2))
        assertThat(mFileNames, contains("example.wav", "example2.wav"))
    }

    @Test
    fun testMissingSound_ExceptionCaught() {
        assertDoesNotThrow { processMissingSound(File("example.wav")) { throw RuntimeException("expected") } }
    }

    @Test
    fun testInefficientImage() {
        // Tests that the runnable passed to processInefficientImage only runs once
        class RunTest : Runnable {
            var nTimesRun = 0
                private set

            override fun run() {
                nTimesRun++
            }
        }

        val runnableTest = RunTest()
        processInefficientImage(runnableTest)
        processInefficientImage(runnableTest)
        assertThat(runnableTest.nTimesRun, equalTo(1))
    }

    private fun getValidRequest(fileName: String): WebResourceRequest {
        // actual URL on Android 9
        val url = "file:///storage/emulated/0/AnkiDroid/collection.media/$fileName"
        return getWebResourceRequest(url)
    }

    private fun getInvalidRequest(fileName: String): WebResourceRequest {
        // no collection.media in the URL
        val url = "file:///storage/emulated/0/AnkiDroid/$fileName"
        return getWebResourceRequest(url)
    }

    private fun getWebResourceRequest(url: String): WebResourceRequest {
        return object : WebResourceRequest {
            override fun getUrl(): Uri {
                return Uri.parse(url)
            }

            override fun isForMainFrame(): Boolean {
                return false
            }

            override fun isRedirect(): Boolean {
                return false
            }

            override fun hasGesture(): Boolean {
                return false
            }

            override fun getMethod(): String? {
                return null
            }

            override fun getRequestHeaders(): Map<String, String>? {
                return null
            }
        }
    }
}
