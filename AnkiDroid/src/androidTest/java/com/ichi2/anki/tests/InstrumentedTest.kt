/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2020 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.tests

import android.content.Context
import android.os.Build
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.utils.EnsureAllFilesAccessRule
import com.ichi2.annotations.DuplicatedCode
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Note
import com.ichi2.libanki.utils.TimeManager
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.BackendException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.fail

abstract class InstrumentedTest {
    internal val col: Collection
        get() = CollectionHelper.instance.getColUnsafe(testContext)!!

    @get:Throws(IOException::class)
    protected val emptyCol: Collection
        get() = Shared.getEmptyCol()

    @get:Rule
    val ensureAllFilesAccessRule = EnsureAllFilesAccessRule()

    /**
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     * emptied on every invocation of this method so it is suitable to use at the start of each test.
     * Only add files (and not subdirectories) to this directory.
     */
    protected val testDir: File
        get() = Shared.getTestDir(testContext)
    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    companion object {
        /**
         * This is how google detects emulators in flutter and how react-native does it in the device info module
         * https://github.com/react-native-community/react-native-device-info/blob/bb505716ff50e5900214fcbcc6e6434198010d95/android/src/main/java/com/learnium/RNDeviceInfo/RNDeviceModule.java#L185
         * @return boolean true if the execution environment is most likely an emulator
         */
        fun isEmulator(): Boolean {
            return (
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("generic") ||
                    Build.FINGERPRINT.startsWith("unknown") ||
                    Build.HARDWARE.contains("goldfish") ||
                    Build.HARDWARE.contains("ranchu") ||
                    Build.MODEL.contains("google_sdk") ||
                    Build.MODEL.contains("Emulator") ||
                    Build.MODEL.contains("Android SDK built for x86") ||
                    Build.MANUFACTURER.contains("Genymotion") ||
                    Build.PRODUCT.contains("sdk_google") ||
                    Build.PRODUCT.contains("google_sdk") ||
                    Build.PRODUCT.contains("sdk") ||
                    Build.PRODUCT.contains("sdk_x86") ||
                    Build.PRODUCT.contains("vbox86p") ||
                    Build.PRODUCT.contains("emulator") ||
                    Build.PRODUCT.contains("simulator")
            )
        }
    }

    @Before
    fun runBeforeEachTest() {
        closeAndroidNotRespondingDialog()
        // resolved issues with the collection being reused if useInMemoryDatabase is false
        CollectionHelper.instance.setColForTests(null)
    }

    @After
    fun runAfterEachTest() {
        try {
            if (CollectionHelper.instance.colIsOpenUnsafe()) {
                CollectionHelper
                    .instance
                    .getColUnsafe(InstrumentationRegistry.getInstrumentation().targetContext)!!
                    .debugEnsureNoOpenPointers()
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            CollectionHelper.instance.closeCollection("InstrumentedTest: End")
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
            disableNullCollection()
        }
        runBlocking { CollectionManager.discardBackend() }
    }

    /** Restore regular collection behavior  */
    private fun disableNullCollection() {
        CollectionHelper.setInstanceForTesting(CollectionHelper())
        CollectionManager.emulateOpenFailure = false
    }

    // Instrumented tests can fail if there's a "App not responding"
    // System dialog blocking our test from proceeding
    //
    // See: https://stackoverflow.com/questions/39457305/android-testing-waited-for-the-root-of-the-view-hierarchy-to-have-window-focus/54203607#54203607
    private fun closeAndroidNotRespondingDialog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var waitButton = device.findObject(UiSelector().textContains("Wait"))
        // There may be multiple dialogs
        while (waitButton.exists()) {
            waitButton.click()
            waitButton = device.findObject(UiSelector().textContains("Wait"))
        }
    }

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    protected fun Card.moveToReviewQueue() {
        this.queue = Consts.QUEUE_TYPE_REV
        this.type = Consts.CARD_TYPE_REV
        this.due = 0
        this.col.updateCard(this, true)
    }

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    internal fun addNoteUsingBasicModel(
        front: String = "Front",
        back: String = "Back",
    ): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    @DuplicatedCode("This is copied from RobolectricTest. This will be refactored into a shared library later")
    private fun addNoteUsingModelName(
        name: String,
        vararg fields: String,
    ): Note {
        val model =
            col.notetypes.byName(name)
                ?: throw IllegalArgumentException("Could not find model '$name'")
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(model)
        for ((i, field) in fields.withIndex()) {
            n.setField(i, field)
        }
        check(col.addNote(n) != 0) { "Could not add note: {${fields.joinToString(separator = ", ")}}" }
        return n
    }

    protected fun ViewInteraction.checkWithTimeout(
        viewAssertion: ViewAssertion,
        retryWaitTimeInMilliseconds: Long = 100,
        maxWaitTimeInMilliseconds: Long = TimeUnit.SECONDS.toMillis(10),
    ) {
        val startTime = TimeManager.time.intTimeMS()

        while (TimeManager.time.intTimeMS() - startTime < maxWaitTimeInMilliseconds) {
            try {
                check(viewAssertion)
                return
            } catch (e: Throwable) {
                Thread.sleep(retryWaitTimeInMilliseconds)
            }
        }

        fail("View assertion was not true within $maxWaitTimeInMilliseconds milliseconds")
    }
}
