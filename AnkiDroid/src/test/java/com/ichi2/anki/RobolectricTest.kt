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
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.async.*
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.backend.exception.DeckRenameException
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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

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

        // If you want to see the Android logging (from Timber), you need to set it up here
        ShadowLog.stream = System.out

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

    protected fun clickMaterialDialogButton(button: WhichButton, @Suppress("SameParameterValue") checkDismissed: Boolean) {
        val dialog = ShadowDialog.getLatestDialog() as MaterialDialog
        dialog.getActionButton(button).performClick()
        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", Shadows.shadowOf(dialog).hasBeenDismissed())
        }
    }

    /**
     * Click on a dialog button for an AlertDialog dialog box. Replaces the above helper.
     */
    protected fun clickAlertDialogButton(button: Int, @Suppress("SameParameterValue") checkDismissed: Boolean) {
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
    protected fun getMaterialDialogText(@Suppress("SameParameterValue") checkDismissed: Boolean): String? {
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
    protected fun getAlertDialogText(@Suppress("SameParameterValue") checkDismissed: Boolean): String? {
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
        protected fun <T : AnkiActivity?> startActivityNormallyOpenCollectionWithIntent(testClass: RobolectricTest, clazz: Class<T>?, i: Intent?): T {
            if (AbstractFlashcardViewer::class.java.isAssignableFrom(clazz!!)) {
                // fixes 'Don't know what to do with dataSource...' inside Sounds.kt
                // solution from https://github.com/robolectric/robolectric/issues/4673
                ShadowMediaPlayer.setMediaInfoProvider {
                    ShadowMediaPlayer.MediaInfo(1, 0)
                }
            }
            val controller = Robolectric.buildActivity(clazz, i)
                .create().start().resume().visible()
            advanceRobolectricLooperWithSleep()
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

    protected fun getQuantityString(res: Int, quantity: Int, vararg formatArgs: Any): String {
        return targetContext.resources.getQuantityString(res, quantity, *formatArgs)
    }

    /** A collection. Created one second ago, not near cutoff time.
     * Each time time is checked, it advance by 10 ms. Not enough to create any change visible to user, but ensure
     * we don't get two equal time. */
    val col: Collection
        get() = try {
            CollectionHelper.instance.getColUnsafe(targetContext)!!
        } catch (e: UnsatisfiedLinkError) {
            throw RuntimeException("Failed to load collection. Did you call super.setUp()?", e)
        }

    protected val collectionTime: MockTime
        get() = TimeManager.time as MockTime

    /** Call this method in your test if you to test behavior with a null collection  */
    protected fun enableNullCollection() {
        CollectionManager.closeCollectionBlocking()
        CollectionHelper.setInstanceForTesting(object : CollectionHelper() {
            @Synchronized
            override fun getColUnsafe(context: Context?): Collection? = null
        })
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

    protected fun <T : AnkiActivity?> startActivityNormallyOpenCollectionWithIntent(clazz: Class<T>?, i: Intent?): T {
        return startActivityNormallyOpenCollectionWithIntent(this, clazz, i)
    }

    protected inline fun <reified T : AnkiActivity?> startRegularActivity(): T {
        return startRegularActivity(null)
    }

    protected inline fun <reified T : AnkiActivity?> startRegularActivity(i: Intent? = null): T {
        return startActivityNormallyOpenCollectionWithIntent(T::class.java, i)
    }

    protected fun addNoteUsingBasicModel(front: String, back: String): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    protected fun addRevNoteUsingBasicModelDueToday(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        val note = addNoteUsingBasicModel(front, back)
        val card = note.firstCard()
        card.queue = Consts.QUEUE_TYPE_REV
        card.type = Consts.CARD_TYPE_REV
        card.due = col.sched.today.toLong()
        return note
    }

    protected fun addNoteUsingBasicAndReversedModel(front: String, back: String): Note {
        return addNoteUsingModelName("Basic (and reversed card)", front, back)
    }

    protected fun addNoteUsingBasicTypedModel(@Suppress("SameParameterValue") front: String, @Suppress("SameParameterValue") back: String): Note {
        return addNoteUsingModelName("Basic (type in the answer)", front, back)
    }

    protected fun addNoteUsingModelName(name: String?, vararg fields: String): Note {
        val model = col.notetypes.byName((name)!!)
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

    protected fun addNonClozeModel(name: String, fields: Array<String>, qfmt: String?, afmt: String?): String {
        val model = col.notetypes.newModel(name)
        for (field in fields) {
            col.notetypes.addFieldInNewModel(model, col.notetypes.newField(field))
        }
        val t = Notetypes.newTemplate("Card 1")
        t.put("qfmt", qfmt)
        t.put("afmt", afmt)
        col.notetypes.addTemplateInNewModel(model, t)
        col.notetypes.add(model)
        return name
    }

    private fun addField(notetype: NotetypeJson, name: String) {
        val models = col.notetypes
        try {
            models.addField(notetype, models.newField(name))
        } catch (e: ConfirmModSchemaException) {
            throw RuntimeException(e)
        }
    }

    protected fun addDeck(deckName: String?): Long {
        return try {
            col.decks.id(deckName!!)
        } catch (filteredAncestor: DeckRenameException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    protected fun addDynamicDeck(name: String?): Long {
        return try {
            col.decks.newDyn(name!!)
        } catch (filteredAncestor: DeckRenameException) {
            throw RuntimeException(filteredAncestor)
        }
    }

    protected fun ensureCollectionLoadIsSynchronous() {
        // HACK: We perform this to ensure that onCollectionLoaded is performed synchronously when startLoadingCollection
        // is called.
        col
    }

    /**
     * Call to assume that <code>actual</code> satisfies the condition specified by <code>matcher</code>.
     * If not, the test halts and is ignored.
     * Example:
     * <pre>:
     *   assumeThat(1, is(1)); // passes
     *   foo(); // will execute
     *   assumeThat(0, is(1)); // assumption failure! test halts
     *   int x = 1 / 0; // will never execute
     * </pre>
     *
     * @param <T> the static type accepted by the matcher (this can flag obvious compile-time problems such as {@code assumeThat(1, is("a"))}
     * @param actual the computed value being compared
     * @param matcher an expression, built of {@link Matcher}s, specifying allowed values
     * @see org.hamcrest.CoreMatchers
     * @see org.junit.matchers.JUnitMatchers
     */
    fun <T> assumeThat(actual: T, matcher: Matcher<T>?) {
        advanceRobolectricLooperWithSleep()
        Assume.assumeThat(actual, matcher)
    }

    /**
     * Call to assume that <code>actual</code> satisfies the condition specified by <code>matcher</code>.
     * If not, the test halts and is ignored.
     * Example:
     * <pre>:
     *   assumeThat("alwaysPasses", 1, is(1)); // passes
     *   foo(); // will execute
     *   assumeThat("alwaysFails", 0, is(1)); // assumption failure! test halts
     *   int x = 1 / 0; // will never execute
     * </pre>
     *
     * @param <T> the static type accepted by the matcher (this can flag obvious compile-time problems such as {@code assumeThat(1, is("a"))}
     * @param actual the computed value being compared
     * @param matcher an expression, built of {@link Matcher}s, specifying allowed values
     * @see org.hamcrest.CoreMatchers
     * @see org.junit.matchers.JUnitMatchers
     */
    fun <T> assumeThat(message: String?, actual: T, matcher: Matcher<T>?) {
        advanceRobolectricLooperWithSleep()
        Assume.assumeThat(message, actual, matcher)
    }

    /**
     * If called with an expression evaluating to {@code false}, the test will halt and be ignored.
     *
     * @param b If <code>false</code>, the method will attempt to stop the test and ignore it by
     * throwing {@link AssumptionViolatedException}.
     * @param message A message to pass to {@link AssumptionViolatedException}.
     */
    fun assumeTrue(message: String?, b: Boolean) {
        advanceRobolectricLooperWithSleep()
        Assume.assumeTrue(message, b)
    }

    fun equalFirstField(expected: Card, obtained: Card) {
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
    fun editPreferences(action: SharedPreferences.Editor.() -> Unit) =
        getPreferences().edit(action = action)

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
* Test should execute correctly"""
                )
            }
            throw e
        }
    }

    /** * A wrapper around the standard [kotlinx.coroutines.test.runTest] that
     * takes care of updating the dispatcher used by CollectionManager as well.
     * * An argument could be made for using [StandardTestDispatcher] and
     * explicitly advanced coroutines with advanceUntilIdle(), but there are
     * issues with using it at the moment:
     * * - Any usage of CollectionManager with runBlocking() will hang. tearDown()
     * calls runBlocking() twice, which prevents tests from finishing.
     * - The hang is not limited to the scope of runTest(). Even if the runBlocking
     * calls in tearDown() are selectively moved into this function,
     * when a coroutine test fails, the next regular test
     * that executes after it will call runBlocking(), and it then hangs.
     *
     * A fix for this might require either wrapping all tests in runTest(),
     * or finding some other way to isolate the coroutine and non-coroutine tests
     * on separate threads/processes.
     * */
    fun runTest(
        context: CoroutineContext = EmptyCoroutineContext,
        dispatchTimeoutMs: Long = 60_000L,
        testBody: suspend TestScope.() -> Unit
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runTest(context, dispatchTimeoutMs.milliseconds) {
            CollectionManager.setTestDispatcher(UnconfinedTestDispatcher(testScheduler))
            testBody()
        }
    }
}
