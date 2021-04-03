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

package com.ichi2.anki;

import android.content.Context;
import android.content.Intent;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.dialogs.DialogHandler;
import com.ichi2.anki.dialogs.utils.FragmentTestActivity;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.FilteredAncestor;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.ForegroundTaskManager;
import com.ichi2.async.SingleTaskManager;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskManager;
import com.ichi2.compat.customtabs.CustomTabActivityHelper;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.CollectionGetter;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;

import com.ichi2.libanki.Note;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.sched.Sched;
import com.ichi2.libanki.sched.SchedV2;
import com.ichi2.testutils.MockTime;
import com.ichi2.utils.BooleanGetter;
import com.ichi2.utils.JSONException;

import net.ankiweb.rsdroid.BackendException;
import net.ankiweb.rsdroid.testing.RustBackendLoader;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ichi2.utils.InMemorySQLiteOpenHelperFactory;

import androidx.fragment.app.DialogFragment;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import timber.log.Timber;

import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.robolectric.Shadows.shadowOf;

public class RobolectricTest implements CollectionGetter {

    private static boolean mBackground = true;

    private final ArrayList<ActivityController<?>> controllersForCleanup = new ArrayList<>();

    protected void saveControllerForCleanup(ActivityController<?> controller) {
        controllersForCleanup.add(controller);
    }

    protected boolean useInMemoryDatabase() {
        return true;
    }

    @Before
    public void setUp() {

        RustBackendLoader.init();

        // If you want to see the Android logging (from Timber), you need to set it up here
        ShadowLog.stream = System.out;

        // Robolectric can't handle our default sqlite implementation of requery, it needs the framework
        DB.setSqliteOpenHelperFactory(getHelperFactory());
        // But, don't use the helper unless useLegacyHelper is true
        Storage.setUseBackend(!useLegacyHelper());
        Storage.setUseInMemory(useInMemoryDatabase());

        //Reset static variable for custom tabs failure.
        CustomTabActivityHelper.resetFailed();

        //See: #6140 - This global ideally shouldn't exist, but it will cause crashes if set.
        DialogHandler.discardMessage();

        // BUG: We do not reset the MetaDB
        MetaDB.closeDB();
    }


    protected boolean useLegacyHelper() {
        return false;
    }


    @NonNull
    protected SupportSQLiteOpenHelper.Factory getHelperFactory() {
        if (useInMemoryDatabase()) {
            Timber.w("Using in-memory database for test. Collection should not be re-opened");
            return new InMemorySQLiteOpenHelperFactory();
        } else {
            return new FrameworkSQLiteOpenHelperFactory();
        }
    }


    @After
    public void tearDown() {

        // If you don't clean up your ActivityControllers you will get OOM errors
        for (ActivityController<?> controller : controllersForCleanup) {
            Timber.d("Calling destroy on controller %s", controller.get().toString());
            try {
                controller.destroy();
            } catch (Exception e) {
                // Any exception here is likely because the test code already destroyed it, which is fine
                // No exception here should halt test execution since tests are over anyway.
            }
        }
        controllersForCleanup.clear();

        try {
            if (CollectionHelper.getInstance().colIsOpen()) {
                CollectionHelper.getInstance().getCol(getTargetContext()).getBackend().debugEnsureNoOpenPointers();
            }
            // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
            CollectionHelper.getInstance().closeCollection(false, "RoboelectricTest: End");
        } catch (BackendException ex) {
            if ("CollectionNotOpen".equals(ex.getMessage())) {
                Timber.w(ex, "Collection was already disposed - may have been a problem");
            } else {
                throw ex;
            }
        } finally {
            // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
            disableNullCollection();

            // After every test, make sure the sqlite implementation is set back to default
            DB.setSqliteOpenHelperFactory(null);

            //called on each AnkiDroidApp.onCreate(), and spams the build
            //there is no onDestroy(), so call it here.
            Timber.uprootAll();

            runTasksInBackground();
        }
    }


    /**
     * Ensure that each task in backgrounds are executed immediately instead of being queued.
     * This may help debugging test without requiring to guess where `advanceRobolectricLooper` are needed.
     */
    public void runTasksInForeground() {
        TaskManager.setTaskManager(new ForegroundTaskManager(this));
        mBackground = false;
    }


    /**
     * Set back the standard background process
     */
    public void runTasksInBackground() {
        TaskManager.setTaskManager(new SingleTaskManager());
        mBackground = true;
    }


    protected void clickDialogButton(DialogAction button, boolean checkDismissed) {
        MaterialDialog dialog = (MaterialDialog)ShadowDialog.getLatestDialog();
        dialog.getActionButton(button).performClick();
        if (checkDismissed) {
            Assert.assertTrue("Dialog not dismissed?", shadowOf(dialog).hasBeenDismissed());
        }
    }

    /**
     * Get the current dialog text. Will return null if no dialog visible *or* if you check for dismissed and it has been dismissed
     *
     * @param checkDismissed true if you want to check for dismissed, will return null even if dialog exists but has been dismissed
     */
    protected String getDialogText(boolean checkDismissed) {
        MaterialDialog dialog = (MaterialDialog)ShadowDialog.getLatestDialog();
        if (dialog == null || dialog.getContentView() == null) {
            return null;
        }

        if (shadowOf(dialog).hasBeenDismissed()) {
            Timber.e("The latest dialog has already been dismissed.");
            return null;
        }

        return dialog.getContentView().getText().toString();
    }

    // Robolectric needs a manual advance with the new PAUSED looper mode
    public static void advanceRobolectricLooper() {
        if (!mBackground) {
            return;
        }
        shadowOf(getMainLooper()).runToEndOfTasks();
        shadowOf(getMainLooper()).idle();
        shadowOf(getMainLooper()).runToEndOfTasks();
    }
    // Robolectric needs some help sometimes in form of a manual kick, then a wait, to stabilize UI activity
    public static void advanceRobolectricLooperWithSleep() {
        if (!mBackground) {
            return;
        }
        advanceRobolectricLooper();
        try { Thread.sleep(500); } catch (Exception e) { Timber.e(e); }
        advanceRobolectricLooper();
    }

    /** This can probably be implemented in a better manner */
    protected static void waitForAsyncTasksToComplete() {
        advanceRobolectricLooperWithSleep();
    }


    protected Context getTargetContext() {
        try {
            return ApplicationProvider.getApplicationContext();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("No instrumentation registered!")) {
                // Explicitly ignore the inner exception - generates line noise
                throw new IllegalStateException("Annotate class: '" + getClass().getSimpleName() + "' with '@RunWith(AndroidJUnit4.class)'");
            }
            throw e;
        }

    }


    protected String getResourceString(int res) {
        return getTargetContext().getString(res);
    }

    protected String getQuantityString(int res, int quantity, Object... formatArgs) {
        return getTargetContext().getResources().getQuantityString(res, quantity, formatArgs);
    }


    /** A collection. Created one second ago, not near cutoff time.
    * Each time time is checked, it advance by 10 ms. Not enough to create any change visible to user, but ensure
     * we don't get two equal time.*/
    public Collection getCol() {
        MockTime time = new MockTime(2020, 7, 7, 7, 0, 0, 0, 10);
        return CollectionHelper.getInstance().getCol(getTargetContext(), time);
    }


    protected MockTime getCollectionTime() {
        return (MockTime) getCol().getTime();
    }

    /** Call this method in your test if you to test behavior with a null collection */
    protected void enableNullCollection() {
        CollectionHelper.LazyHolder.INSTANCE = new CollectionHelper() {
            @Override
            public Collection getCol(Context context) {
                return null;
            }
        };
    }

    /** Restore regular collection behavior */
    protected void disableNullCollection() {
        CollectionHelper.LazyHolder.INSTANCE = new CollectionHelper();
    }

    protected Model getCurrentDatabaseModelCopy(String modelName) throws JSONException {
        Models collectionModels = getCol().getModels();
        return new Model(collectionModels.byName(modelName).toString().trim());
    }

    protected static <T extends AnkiActivity> T startActivityNormallyOpenCollectionWithIntent(RobolectricTest testClass, Class<T> clazz, Intent i) {
        ActivityController<T> controller = Robolectric.buildActivity(clazz, i)
                .create().start().resume().visible();
        advanceRobolectricLooperWithSleep();
        advanceRobolectricLooperWithSleep();
        testClass.saveControllerForCleanup(controller);
        return controller.get();
    }

    protected <T extends AnkiActivity> T startActivityNormallyOpenCollectionWithIntent(Class<T> clazz, Intent i) {
        return startActivityNormallyOpenCollectionWithIntent(this, clazz, i);
    }

    protected Note addNoteUsingBasicModel(String front, String back) {
        return addNoteUsingModelName("Basic", front, back);
    }

    protected Note addRevNoteUsingBasicModelDueToday(String front, String back) {
        Note note = addNoteUsingBasicModel(front, back);
        Card card = note.firstCard();
        card.setQueue(Consts.QUEUE_TYPE_REV);
        card.setType(Consts.CARD_TYPE_REV);
        card.setDue(getCol().getSched().getToday());
        return note;
    }

    protected Note addNoteUsingBasicAndReversedModel(String front, String back) {
        return addNoteUsingModelName("Basic (and reversed card)", front, back);
    }

    protected Note addNoteUsingBasicTypedModel(String front, String back) {
        return addNoteUsingModelName("Basic (type in the answer)", front, back);
    }

    protected Note addNoteUsingModelName(String name, String... fields) {
        Model model = getCol().getModels().byName(name);
        //PERF: if we modify newNote(), we can return the card and return a Pair<Note, Card> here.
        //Saves a database trip afterwards.
        if (model == null) {
            throw new IllegalArgumentException(String.format("Could not find model '%s'", name));
        }
        Note n = getCol().newNote(model);
        for(int i = 0; i < fields.length; i++) {
            n.setField(i, fields[i]);
        }
        if (getCol().addNote(n) == 0) {
            throw new IllegalStateException(String.format("Could not add note: {%s}", String.join(", ", fields)));
        }
        return n;
    }


    protected String addNonClozeModel(String name, String[] fields, String qfmt, String afmt) {
        Model model = getCol().getModels().newModel(name);
        for (String field : fields) {
            addField(model, field);
        }
        model.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, qfmt);
        model.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, afmt);
        getCol().getModels().add(model);
        getCol().getModels().flush();
        return name;
    }


    private void addField(Model model, String name) {
        Models models = getCol().getModels();

        try {
            models.addField(model, models.newField(name));
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException(e);
        }
    }

    protected long addDeck(String deckName) {
        try {
            return getCol().getDecks().id(deckName);
        } catch (FilteredAncestor filteredAncestor) {
            throw new RuntimeException(filteredAncestor);
        }
    }

    protected long addDynamicDeck(String name) {
        try {
            return getCol().getDecks().newDyn(name);
        } catch (FilteredAncestor filteredAncestor) {
            throw new RuntimeException(filteredAncestor);
        }
    }

    protected void ensureCollectionLoadIsSynchronous() {
        //HACK: We perform this to ensure that onCollectionLoaded is performed synchronously when startLoadingCollection
        //is called.
        getCol();
    }


    protected SchedV2 upgradeToSchedV2() throws ConfirmModSchemaException {
        getCol().changeSchedulerVer(2);

        AbstractSched sched = getCol().getSched();
        //Sched inherits from schedv2...
        assertThat("sched should be v2", !(sched instanceof Sched));

        return (SchedV2) sched;
    }


    protected synchronized <Progress, Result extends BooleanGetter> void waitFortask(CollectionTask.Task<Progress, Result> task, int timeoutMs) throws InterruptedException {
        boolean[] completed = new boolean[] { false };
        TaskListener<Progress, Result> listener = new TaskListener<Progress, Result>() {
            @Override
            public void onPreExecute() {

            }


            @Override
            public void onPostExecute(Result result) {

                if (result == null || !result.getBoolean()) {
                    throw new IllegalArgumentException("Task failed");
                }
                completed[0] = true;
                synchronized (RobolectricTest.this) {
                    RobolectricTest.this.notify();
                }
            }
        };
        TaskManager.launchCollectionTask(task, listener);
        advanceRobolectricLooper();

        wait(timeoutMs);
        advanceRobolectricLooper();

        if (!completed[0]) {
            throw new IllegalStateException(String.format("Task %s didn't finish in %d ms", task.getClass(), timeoutMs));
        }
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
    public <T> void assumeThat(T actual, Matcher<T> matcher) {
        advanceRobolectricLooperWithSleep();
        Assume.assumeThat(actual, matcher);
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
    public <T> void assumeThat(String message, T actual, Matcher<T> matcher) {
        advanceRobolectricLooperWithSleep();
        Assume.assumeThat(message, actual, matcher);
    }



    /**
     * If called with an expression evaluating to {@code false}, the test will halt and be ignored.
     *
     * @param b If <code>false</code>, the method will attempt to stop the test and ignore it by
     * throwing {@link AssumptionViolatedException}.
     * @param message A message to pass to {@link AssumptionViolatedException}.
     */
    public void assumeTrue(String message, boolean b) {
        advanceRobolectricLooperWithSleep();
        Assume.assumeTrue(message, b);
    }

    public void equalFirstField(Card expected, Card obtained) {
        assertThat(obtained.note().getFields()[0], is(expected.note().getFields()[0]));
    }


    @NonNull
    @CheckResult
    protected FragmentTestActivity openDialogFragmentUsingActivity(DialogFragment menu) {
        Intent startActivityIntent = new Intent(getTargetContext(), FragmentTestActivity.class);

        FragmentTestActivity activity = startActivityNormallyOpenCollectionWithIntent(FragmentTestActivity.class, startActivityIntent);

        activity.showDialogFragment(menu);

        return activity;
    }

    protected Card getCard() {
        Card card = getCol().getSched().getCard();
        advanceRobolectricLooperWithSleep();
        return card;
    }

}
