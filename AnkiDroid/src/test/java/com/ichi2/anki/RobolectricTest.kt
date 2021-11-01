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
import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import androidx.annotation.CheckResult
import androidx.annotation.NonNull
import androidx.fragment.app.DialogFragment
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.utils.FragmentTestActivity
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.async.*
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.sched.Sched
import com.ichi2.libanki.sched.SchedV2
import com.ichi2.testutils.MockTime
import com.ichi2.testutils.TaskSchedulerRule
import com.ichi2.utils.Computation
import com.ichi2.utils.InMemorySQLiteOpenHelperFactory
import com.ichi2.utils.JSONException
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.testing.RustBackendLoader
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLog
import timber.log.Timber
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

open class RobolectricTest : CollectionGetter {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Any.wait(timeMs: Long) = (this as Object).wait(timeMs)

    private val mControllersForCleanup = ArrayList<ActivityController<*>>()

    protected fun saveControllerForCleanup(controller: ActivityController<*>) {
        mControllersForCleanup.add(controller)
    }

    protected open fun useInMemoryDatabase(): Boolean {
        return true
    }

    @Rule
    @JvmField
    val mTaskScheduler = TaskSchedulerRule()

    @Before
    open fun setUp() {
        if (mTaskScheduler.shouldRunInForeground()) {
            runTasksInForeground()
        } else {
            runTasksInBackground()
        }

        RustBackendLoader.init()

        // If you want to see the Android logging (from Timber), you need to set it up here
        ShadowLog.stream = System.out

        // Robolectric can't handle our default sqlite implementation of requery, it needs the framework
        DB.setSqliteOpenHelperFactory(getHelperFactory())
        // But, don't use the helper unless useLegacyHelper is true
        Storage.setUseBackend(!useLegacyHelper())
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

    @NonNull
    protected fun getHelperFactory(): SupportSQLiteOpenHelper.Factory {
        if (useInMemoryDatabase()) {
            Timber.w("Using in-memory database for test. Collection should not be re-opened")
            return InMemorySQLiteOpenHelperFactory()
        } else {
            return FrameworkSQLiteOpenHelperFactory()
        }
    }

    @After
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
            if (CollectionHelper.getInstance().colIsOpen()) {
                CollectionHelper.getInstance().getCol(targetContext).getBackend().debugEnsureNoOpenPointers()
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            CollectionHelper.getInstance().closeCollection(false, "RoboelectricTest: End")
        } catch (ex: BackendException) {
            if ("CollectionNotOpen".equals(ex.message)) {
                Timber.w(ex, "Collection was already disposed - may have been a problem")
            } else {
                throw ex
            }
        } finally {
            // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
            disableNullCollection()

            // After every test, make sure the sqlite implementation is set back to default
            DB.setSqliteOpenHelperFactory(null)

            // called on each AnkiDroidApp.onCreate(), and spams the build
            // there is no onDestroy(), so call it here.
            Timber.uprootAll()
        }
    }

    /**
     * Ensure that each task in backgrounds are executed immediately instead of being queued.
     * This may help debugging test without requiring to guess where `advanceRobolectricLooper` are needed.
     */
    fun runTasksInForeground() {
        TaskManager.setTaskManager(ForegroundTaskManager(this))
        mBackground = false
    }

    /**
     * Set back the standard background process
     */
    fun runTasksInBackground() {
        TaskManager.setTaskManager(SingleTaskManager())
        mBackground = true
    }

    protected fun clickDialogButton(button: DialogAction?, checkDismissed: Boolean) {
        val dialog = ShadowDialog.getLatestDialog() as MaterialDialog
        dialog.getActionButton(button!!).performClick()
        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", Shadows.shadowOf(dialog).hasBeenDismissed())
        }
    }

    /**
     * Get the current dialog text. Will return null if no dialog visible *or* if you check for dismissed and it has been dismissed
     *
     * @param checkDismissed true if you want to check for dismissed, will return null even if dialog exists but has been dismissed
     */
    protected fun getDialogText(checkDismissed: Boolean): String? {
        val dialog: MaterialDialog? = ShadowDialog.getLatestDialog() as MaterialDialog
        if (dialog == null || dialog.contentView == null) {
            return null
        }
        if (checkDismissed && Shadows.shadowOf(dialog).hasBeenDismissed()) {
            Timber.e("The latest dialog has already been dismissed.")
            return null
        }
        return dialog.contentView!!.text.toString()
    }

    // Robolectric needs a manual advance with the new PAUSED looper mode
    companion object {
        private var mBackground = true

        // Robolectric needs a manual advance with the new PAUSED looper mode
        @JvmStatic
        fun advanceRobolectricLooper() {
            if (!mBackground) {
                return
            }
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        }

        // Robolectric needs some help sometimes in form of a manual kick, then a wait, to stabilize UI activity
        @JvmStatic
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
        @JvmStatic
        protected fun waitForAsyncTasksToComplete() {
            advanceRobolectricLooperWithSleep()
        }

        @JvmStatic
        protected fun <T : AnkiActivity?> startActivityNormallyOpenCollectionWithIntent(testClass: RobolectricTest, clazz: Class<T>?, i: Intent?): T {
            val controller = Robolectric.buildActivity(clazz, i)
                .create().start().resume().visible()
            advanceRobolectricLooperWithSleep()
            advanceRobolectricLooperWithSleep()
            testClass.saveControllerForCleanup(controller)
            return controller.get()
        }
    }

    protected val targetContext: Context
        get() {
            return try {
                ApplicationProvider.getApplicationContext()
            } catch (e: IllegalStateException) {
                if (e.message != null && e.message!!.startsWith("No instrumentation registered!")) {
                    // Explicitly ignore the inner exception - generates line noise
                    throw IllegalStateException("Annotate class: '" + javaClass.simpleName + "' with '@RunWith(AndroidJUnit4.class)'")
                }
                throw e
            }
        }

    protected fun getPreferences(): SharedPreferences {
        return AnkiDroidApp.getSharedPrefs(targetContext)
    }

    protected fun getResourceString(res: Int): String {
        return targetContext.getString(res)
    }

    protected fun getQuantityString(res: Int, quantity: Int, vararg formatArgs: Any?): String {
        return targetContext.resources.getQuantityString(res, quantity, *formatArgs)
    }

    /** A collection. Created one second ago, not near cutoff time.
     * Each time time is checked, it advance by 10 ms. Not enough to create any change visible to user, but ensure
     * we don't get two equal time. */
    override fun getCol(): Collection {
        val time = MockTime(2020, 7, 7, 7, 0, 0, 0, 10)
        return CollectionHelper.getInstance().getCol(targetContext, time)
    }

    protected val collectionTime: MockTime
        get() = col.time as MockTime

    /** Call this method in your test if you to test behavior with a null collection  */
    protected fun enableNullCollection() {
        CollectionHelper.LazyHolder.INSTANCE = object : CollectionHelper() {
            override fun getCol(context: Context): Collection? {
                return null
            }
        }
    }

    /** Restore regular collection behavior  */
    protected fun disableNullCollection() {
        CollectionHelper.LazyHolder.INSTANCE = CollectionHelper()
    }

    @Throws(JSONException::class)
    protected fun getCurrentDatabaseModelCopy(modelName: String?): Model {
        val collectionModels = col.models
        return Model(collectionModels.byName(modelName!!).toString().trim { it <= ' ' })
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

    protected fun addNoteUsingBasicModel(front: String?, back: String?): Note {
        return addNoteUsingModelName("Basic", front, back)
    }

    protected fun addRevNoteUsingBasicModelDueToday(front: String?, back: String?): Note {
        val note = addNoteUsingBasicModel(front, back)
        val card = note.firstCard()
        card.queue = Consts.QUEUE_TYPE_REV
        card.type = Consts.CARD_TYPE_REV
        card.due = col.sched.today.toLong()
        return note
    }

    protected fun addNoteUsingBasicAndReversedModel(front: String?, back: String?): Note {
        return addNoteUsingModelName("Basic (and reversed card)", front, back)
    }

    protected fun addNoteUsingBasicTypedModel(front: String?, back: String?): Note {
        return addNoteUsingModelName("Basic (type in the answer)", front, back)
    }

    protected fun addNoteUsingModelName(name: String?, vararg fields: String?): Note {
        val model = col.models.byName((name)!!)
            ?: throw IllegalArgumentException(String.format("Could not find model '%s'", name))
        // PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        // Saves a database trip afterwards.
        val n = col.newNote(model)
        for (i in 0 until fields.size) {
            n.setField(i, fields[i])
        }
        check(col.addNote(n) != 0) { String.format("Could not add note: {%s}", fields.joinToString(separator = ", ")) }
        return n
    }

    protected fun addNonClozeModel(name: String, fields: Array<String>, qfmt: String?, afmt: String?): String {
        val model = col.models.newModel(name)
        for (field in fields) {
            addField(model, field)
        }
        val t = Models.newTemplate("Card 1")
        t.put("qfmt", qfmt)
        t.put("afmt", afmt)
        col.models.addTemplateInNewModel(model, t)
        col.models.add(model)
        col.models.flush()
        return name
    }

    private fun addField(model: Model, name: String) {
        val models = col.models
        try {
            models.addField(model, models.newField(name))
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

    @Throws(ConfirmModSchemaException::class)
    protected fun upgradeToSchedV2(): SchedV2 {
        col.changeSchedulerVer(2)
        val sched = col.sched
        // Sched inherits from schedv2...
        MatcherAssert.assertThat("sched should be v2", sched !is Sched)
        return sched as SchedV2
    }

    @Synchronized
    @Throws(InterruptedException::class)
    protected fun <Progress, Result : Computation<*>?> waitFortask(task: TaskDelegate<Progress, Result>, timeoutMs: Int) {
        val completed = booleanArrayOf(false)
        val listener: TaskListener<Progress, Result> = object : TaskListener<Progress, Result>() {
            override fun onPreExecute() {}
            override fun onPostExecute(result: Result) {
                require(!(result == null || !result.succeeded())) { "Task failed" }
                completed[0] = true
                val RobolectricTest = ReentrantLock()
                val condition = RobolectricTest.newCondition()
                RobolectricTest.withLock { condition.signal() }
                // synchronized(this@RobolectricTest) { this@RobolectricTest.notify() }
            }
        }
        TaskManager.launchCollectionTask(task, listener)
        advanceRobolectricLooper()
        wait(timeoutMs.toLong())
        advanceRobolectricLooper()
        if (!completed[0]) { throw IllegalStateException(String.format("Task %s didn't finish in %d ms", task.javaClass, timeoutMs)) }
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
        MatcherAssert.assertThat(obtained.note().fields[0], Matchers.`is`(expected.note().fields[0]))
    }

    @NonNull
    @CheckResult
    protected fun openDialogFragmentUsingActivity(menu: DialogFragment?): FragmentTestActivity {
        val startActivityIntent = Intent(targetContext, FragmentTestActivity::class.java)
        val activity = startActivityNormallyOpenCollectionWithIntent(FragmentTestActivity::class.java, startActivityIntent)
        activity.showDialogFragment(menu)
        return activity
    }

    protected val card: Card?
        get() {
            val card = col.sched.card
            advanceRobolectricLooperWithSleep()
            return card
        }
}
