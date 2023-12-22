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

package com.ichi2.anki

import android.content.Context
import android.content.DialogInterface.*
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.utils.FragmentTestActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.async.*
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.testutils.*
import com.ichi2.utils.InMemorySQLiteOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.json.JSONException
import org.junit.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import timber.log.Timber

open class RobolectricTest : AndroidTest {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.wait(timeMs: Long) = (this as Object).wait(timeMs)

    private val mControllersForCleanup = ArrayList<ActivityController<*>>()

    protected fun saveControllerForCleanup(controller: ActivityController<*>) {
        mControllersForCleanup.add(controller)
    }

    protected open fun useInMemoryDatabase(): Boolean {
        return true
    }

    open val disableCollectionLogFile = true

    @get:Rule
    val mTaskScheduler = TaskSchedulerRule()

    /** Allows [com.ichi2.testutils.Flaky] to annotate tests in subclasses */
    @get:Rule
    val ignoreFlakyTests = IgnoreFlakyTestsInCIRule()

    @Before
    @CallSuper
    open fun setUp() {
        TimeManager.resetWith(MockTime(2020, 7, 7, 7, 0, 0, 0, 10))

        ChangeManager.clearSubscribers()

        // resolved issues with the collection being reused if useInMemoryDatabase is false
        CollectionHelper.instance.setColForTests(null)

        maybeSetupBackend()

        // disable the collection log file for a speed boost & reduce log output
        CollectionManager.disableLogFile = disableCollectionLogFile
        // See the Android logging (from Timber)
        ShadowLog.stream =
            System.out
                // Filters for non-Timber sources. Prefer filtering in RobolectricDebugTree if possible
                // LifecycleMonitor: not needed as we already use registerActivityLifecycleCallbacks for logs
                // W/ShadowLegacyPath: android.graphics.Path#op() not supported yet.
                .filter("^(?!(W/ShadowLegacyPath|D/LifecycleMonitor)).*$")

        Storage.setUseInMemory(useInMemoryDatabase())

        // Reset static variable for custom tabs failure.
        CustomTabActivityHelper.resetFailed()

        // See: #6140 - This global ideally shouldn't exist, but it will cause crashes if set.
        DialogHandler.discardMessage()

        // BUG: We do not reset the MetaDB
        MetaDB.closeDB()
    }

    protected open fun useLegacyHelper(): Boolean {
        return false
    }

    protected fun getHelperFactory(): SupportSQLiteOpenHelper.Factory =
        if (useInMemoryDatabase()) {
            Timber.w("Using in-memory database for test. Collection should not be re-opened")
            InMemorySQLiteOpenHelperFactory()
        } else {
            FrameworkSQLiteOpenHelperFactory()
        }

    @After
    @CallSuper
    open fun tearDown() {
        // If you don't clean up your ActivityControllers you will get OOM errors
        for (controller in mControllersForCleanup) {
            Timber.d("Calling destroy on controller %s", controller.get().toString())
            try {
                controller.destroy()
            } catch (e: Exception) {
                // Any exception here is likely because the test code already destroyed it, which is fine
                // No exception here should halt test execution since tests are over anyway.
            }
        }
        mControllersForCleanup.clear()

        try {
            if (CollectionHelper.instance.colIsOpenUnsafe()) {
                CollectionHelper.instance.getColUnsafe(targetContext)!!.debugEnsureNoOpenPointers()
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            CollectionHelper.instance.closeCollection("RobolectricTest: End")
        } catch (ex: BackendException) {
            if ("CollectionNotOpen" == ex.message) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
            disableNullCollection()

            // called on each AnkiDroidApp.onCreate(), and spams the build
            // there is no onDestroy(), so call it here.
            Timber.uprootAll()

            TimeManager.reset()
        }
        Dispatchers.resetMain()
        runBlocking { CollectionManager.discardBackend() }
    }

    protected fun clickMaterialDialogButton(
        button: WhichButton,
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ) {
        val dialog = ShadowDialog.getLatestDialog() as MaterialDialog
        dialog.getActionButton(button).performClick()
        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", Shadows.shadowOf(dialog).hasBeenDismissed())
        }
    }

    /**
     * Click on a dialog button for an AlertDialog dialog box. Replaces the above helper.
     */
    protected fun clickAlertDialogButton(
        button: Int,
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ) {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog

        dialog.getButton(button).performClick()
        // Need to run UI thread tasks to actually run the onClickHandler
        ShadowLooper.runUiThreadTasks()

        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", Shadows.shadowOf(dialog).hasBeenDismissed())
        }
    }

    /**
     * Get the current dialog text. Will return null if no dialog visible *or* if you check for dismissed and it has been dismissed
     *
     * @param checkDismissed true if you want to check for dismissed, will return null even if dialog exists but has been dismissed
     */
    protected fun getMaterialDialogText(
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ): String? {
        val dialog: MaterialDialog = ShadowDialog.getLatestDialog() as MaterialDialog
        if (checkDismissed && Shadows.shadowOf(dialog).hasBeenDismissed()) {
            Timber.e("The latest dialog has already been dismissed.")
            return null
        }
        return dialog.view.contentLayout.findViewById<TextView>(com.afollestad.materialdialogs.R.id.md_text_message).text.toString()
    }

    /**
     * Get the current dialog text for AlertDialogs (which are replacing MaterialDialogs). Will return null if no dialog visible
     * *or* if you check for dismissed and it has been dismissed
     *
     * @param checkDismissed true if you want to check for dismissed, will return null even if dialog exists but has been dismissed
     * TODO: Rename to getDialogText when all MaterialDialogs are changed to AlertDialogs
     */
    protected fun getAlertDialogText(
        @Suppress("SameParameterValue") checkDismissed: Boolean,
    ): String? {
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        if (checkDismissed && Shadows.shadowOf(dialog).hasBeenDismissed()) {
            Timber.e("The latest dialog has already been dismissed.")
            return null
        }
        val messageViewWithinDialog = dialog.findViewById<TextView>(android.R.id.message)
        Assert.assertFalse(messageViewWithinDialog == null)

        return messageViewWithinDialog?.text?.toString()
    }

    // Robolectric needs a manual advance with the new PAUSED looper mode
    companion object {
        private var mBackground = true

        // Robolectric needs a manual advance with the new PAUSED looper mode
        fun advanceRobolectricLooper() {
            if (!mBackground) {
                return
            }
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        }

        /**
         * * Causes all of the [Runnable]s that have been scheduled to run while advancing the clock to the start time of the last scheduled Runnable.
         * * Executes all posted tasks scheduled before or at the current time
         *
         * Supersedes and will eventually replace [advanceRobolectricLooper] and [advanceRobolectricLooperWithSleep]
         */
        fun advanceRobolectricUiLooper() {
            Shadows.shadowOf(Looper.getMainLooper()).apply {
                runToEndOfTasks()
                idle()
                // CardBrowserTest:browserIsInMultiSelectModeWhenSelectingAll failed on Windows CI
                // This line was added and may or may not make a difference
                runToEndOfTasks()
            }
        }

        // Robolectric needs some help sometimes in form of a manual kick, then a wait, to stabilize UI activity
        fun advanceRobolectricLooperWithSleep() {
            if (!mBackground) {
                return
            }
            advanceRobolectricLooper()
            try {
                Thread.sleep(500)
            } catch (e: Exception) {
                Timber.e(e)
            }
            advanceRobolectricLooper()
        }

        /** This can probably be implemented in a better manner  */
        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun waitForAsyncTasksToComplete() {
            advanceRobolectricLooperWithSleep()
        }

        @JvmStatic // Using protected members which are not @JvmStatic in the superclass companion is unsupported yet
        protected fun <T : AnkiActivity?> startActivityNormallyOpenCollectionWithIntent(
            testClass: RobolectricTest,
            clazz: Class<T>?,
            i: Intent?,
        ): T {
            if (AbstractFlashcardViewer::class.java.isAssignableFrom(clazz!!)) {
                // fixes 'Don't know what to do with dataSource...' inside Sounds.kt
                // solution from https://github.com/robolectric/robolectric/issues/4673
                ShadowMediaPlayer.setMediaInfoProvider {
                    ShadowMediaPlayer.MediaInfo(1, 0)
                }
            }
            val controller =
                Robolectric.buildActivity(clazz, i)
                    .create().start().resume().visible()
            advanceRobolectricLooperWithSleep()
            testClass.saveControllerForCleanup(controller)
            return controller.get()
        }
    }

    val targetContext: Context
        get() {
            return try {
                ApplicationProvider.getApplicationContext()
            } catch (e: IllegalStateException) {
                if (e.message != null && e.message!!.startsWith("No instrumentation registered!")) {
                    // Explicitly ignore the inner exception - generates line noise
                    throw IllegalStateException("Annotate class: '${javaClass.simpleName}' with '@RunWith(AndroidJUnit4.class)'")
                }
                throw e
            }
        }

    /**
     * Returns an instance of [SharedPreferences] using the test context
     * @see [editPreferences] for editing
     */
    fun getPreferences(): SharedPreferences {
        return targetContext.sharedPrefs()
    }

    protected fun getResourceString(res: Int): String {
        return targetContext.getString(res)
    }

    protected fun getQuantityString(
        res: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String {
        return targetContext.resources.getQuantityString(res, quantity, *formatArgs)
    }

    /** A collection. Created one second ago, not near cutoff time.
     * Each time time is checked, it advance by 10 ms. Not enough to create any change visible to user, but ensure
     * we don't get two equal time. */
    override val col: Collection
        get() =
            try {
                CollectionHelper.instance.getColUnsafe(targetContext)!!
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Failed to load collection. Did you call super.setUp()?", e)
            }

    protected val collectionTime: MockTime
        get() = TimeManager.time as MockTime

    /** Call this method in your test if you to test behavior with a null collection  */
    protected fun enableNullCollection() {
        CollectionManager.closeCollectionBlocking()
        CollectionHelper.setInstanceForTesting(
            object : CollectionHelper() {
                @Synchronized
                override fun getColUnsafe(context: Context?): Collection? = null
            },
        )
        CollectionManager.emulateOpenFailure = true
    }

    /** Restore regular collection behavior  */
    protected fun disableNullCollection() {
        CollectionHelper.setInstanceForTesting(CollectionHelper())
        CollectionManager.emulateOpenFailure = false
    }

    @Throws(JSONException::class)
    protected fun getCurrentDatabaseModelCopy(modelName: String): NotetypeJson {
        val collectionModels = col.notetypes
        return NotetypeJson(collectionModels.byName(modelName).toString().trim { it <= ' ' })
    }

    protected fun <T : AnkiActivity?> startActivityNormallyOpenCollectionWithIntent(
        clazz: Class<T>?,
        i: Intent?,
    ): T {
        return startActivityNormallyOpenCollectionWithIntent(this, clazz, i)
    }

    protected inline fun <reified T : AnkiActivity?> startRegularActivity(): T {
        return startRegularActivity(null)
    }

    protected inline fun <reified T : AnkiActivity?> startRegularActivity(i: Intent? = null): T {
        return startActivityNormallyOpenCollectionWithIntent(T::class.java, i)
    }

    /**
     * Call to assume that <code>actual</code> satisfies the condition specified by <code>matcher</code>.
     * If not, the test halts and is ignored.
     * Example:
     * ```kotlin
     *   assumeThat(1, is(1));  // passes
     *   foo();                 // will execute
     *   assumeThat(0, is(1));  // assumption failure! test halts
     *   int x = 1 / 0;         // will never execute
     * ```
     *
     * @param <T> the static type accepted by the matcher (this can flag obvious compile-time problems such as `assumeThat(1, equalTo("a"))`)
     * @param actual the computed value being compared
     * @param matcher an expression, built from [Matchers][Matcher], specifying allowed values
     * @see org.hamcrest.CoreMatchers
     * @see org.junit.matchers.JUnitMatchers
     */
    fun <T> assumeThat(
        actual: T,
        matcher: Matcher<T>?,
    ) {
        Assume.assumeThat(actual, matcher)
    }

    /**
     * Call to assume that `actual` satisfies the condition specified by <code>matcher</code>.
     * If not, the test halts and is ignored.
     * Example:
     * ```kotlin
     *   assumeThat("alwaysPasses", 1, equalTo(1)); // passes
     *   foo();                                     // will execute
     *   assumeThat("alwaysFails", 0, equalTo(1));  // assumption failure! test halts
     *   int x = 1 / 0;                             // will never execute
     * ```
     *
     * @param <T> the static type accepted by the matcher (this can flag obvious compile-time problems such as `assumeThat(1, equalTo("a"))`
     * @param actual the computed value being compared
     * @param matcher an expression, built from [Matchers][Matcher], specifying allowed values
     * @see org.hamcrest.CoreMatchers
     * @see org.junit.matchers.JUnitMatchers
     */
    fun <T> assumeThat(
        message: String?,
        actual: T,
        matcher: Matcher<T>?,
    ) {
        Assume.assumeThat(message, actual, matcher)
    }

    /**
     * If called with an expression evaluating to `false`, the test will halt and be ignored.
     *
     * @param b If `false`, the method will attempt to stop the test and ignore it by
     * throwing [AssumptionViolatedException]
     * @param message A message to pass to [AssumptionViolatedException]
     */
    fun assumeTrue(
        message: String?,
        b: Boolean,
    ) {
        Assume.assumeTrue(message, b)
    }

    fun equalFirstField(
        expected: Card,
        obtained: Card,
    ) {
        MatcherAssert.assertThat(obtained.note().fields[0], Matchers.equalTo(expected.note().fields[0]))
    }

    @CheckResult
    protected fun openDialogFragmentUsingActivity(menu: DialogFragment): FragmentTestActivity {
        val startActivityIntent = Intent(targetContext, FragmentTestActivity::class.java)
        val activity = startActivityNormallyOpenCollectionWithIntent(FragmentTestActivity::class.java, startActivityIntent)
        activity.showDialogFragment(menu)
        return activity
    }

    /**
     * Allows editing of preferences, followed by a call to [apply][SharedPreferences.Editor.apply]:
     *
     * ```
     * editPreferences { putString("key", value) }
     * ```
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun editPreferences(action: SharedPreferences.Editor.() -> Unit) = getPreferences().edit(action = action)

    private fun maybeSetupBackend() {
        try {
            targetContext
        } catch (exc: IllegalStateException) {
            // We must make sure not to load the backend library into a test running outside
            // the Robolectric classloader, or subsequent Robolectric tests that run in this
            // process will be unable to make calls into the backend.
            println("not annotated with junit, not setting up backend")
            return
        }
        try {
            RustBackendLoader.ensureSetup()
        } catch (e: UnsatisfiedLinkError) {
            if (e.message.toString().contains("library load disallowed by system policy")) {
                throw IllegalStateException(
                    """library load disallowed by system policy.
"To fix:
* Run the test such that the "developer cannot be verified" message appears
* Press "OK" on the "Apple cannot check it for malicious software" prompt
* Run the Test Again
* Apple Menu - System Preferences - Security & Privacy - General (tab) - Unlock Settings - Select Allow Anyway". 
    Button is underneath the text: "librsdroid.dylib was blocked from use because it is not from an identified developer"
* Press "OK" on the "Apple cannot check it for malicious software" prompt
* Test should execute correctly""",
                )
            }
            throw e
        }
    }
}
