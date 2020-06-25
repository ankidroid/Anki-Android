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
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.compat.customtabs.CustomTabActivityHelper;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Models;

import com.ichi2.libanki.Note;
import com.ichi2.libanki.sched.AbstractSched;
import com.ichi2.libanki.sched.Sched;
import com.ichi2.libanki.sched.SchedV2;
import com.ichi2.utils.JSONException;
import com.ichi2.utils.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLog;

import java.util.ArrayList;

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import timber.log.Timber;

import static android.os.Looper.getMainLooper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.robolectric.Shadows.shadowOf;

public class RobolectricTest {

    private ArrayList<ActivityController> controllersForCleanup = new ArrayList<>();

    protected void saveControllerForCleanup(ActivityController controller) {
        controllersForCleanup.add(controller);
    }

    @Before
    public void setUp() {
        // If you want to see the Android logging (from Timber), you need to set it up here
        ShadowLog.stream = System.out;

        // Robolectric can't handle our default sqlite implementation of requery, it needs the framework
        DB.setSqliteOpenHelperFactory(new FrameworkSQLiteOpenHelperFactory());

        //Reset static variable for custom tabs failure.
        CustomTabActivityHelper.resetFailed();

        //See: #6140 - This global ideally shouldn't exist, but it will cause crashes if set.
        DialogHandler.discardMessage();
    }

    @After
    public void tearDown() {

        // If you don't clean up your ActivityControllers you will get OOM errors
        for (ActivityController controller : controllersForCleanup) {
            Timber.d("Calling destroy on controller %s", controller.get().toString());
            try {
                controller.destroy();
            } catch (Exception e) {
                // Any exception here is likely because the test code already destroyed it, which is fine
                // No exception here should halt test execution since tests are over anyway.
            }
        }
        controllersForCleanup.clear();

        // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
        CollectionHelper.getInstance().closeCollection(false, "RoboelectricTest: End");

        // After every test, make sure the sqlite implementation is set back to default
        DB.setSqliteOpenHelperFactory(null);

        //called on each AnkiDroidApp.onCreate(), and spams the build
        //there is no onDestroy(), so call it here.
        Timber.uprootAll();
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

    // Robolectric needs some help sometimes in form of a manual kick, then a wait, to stabilize UI activity
    protected void advanceRobolectricLooper() {
        shadowOf(getMainLooper()).idle();
        try { Thread.sleep(500); } catch (Exception e) { Timber.e(e); }

    }


    protected Context getTargetContext() {
        return ApplicationProvider.getApplicationContext();
    }


    protected String getResourceString(int res) {
        return getTargetContext().getString(res);
    }

    protected String getQuantityString(int res, int quantity, Object... formatArgs) {
        return getTargetContext().getResources().getQuantityString(res, quantity, formatArgs);
    }


    protected Collection getCol() {
        return CollectionHelper.getInstance().getCol(getTargetContext());
    }


    protected JSONObject getCurrentDatabaseModelCopy(String modelName) throws JSONException {
        Models collectionModels = getCol().getModels();
        return new JSONObject(collectionModels.byName(modelName).toString().trim());
    }

    protected <T extends AnkiActivity> T startActivityNormallyOpenCollectionWithIntent(Class<T> clazz, Intent i) {
        ActivityController<T> controller = Robolectric.buildActivity(clazz, i)
                .create().start().resume().visible();
        saveControllerForCleanup(controller);
        return controller.get();
    }

    protected Note addNoteUsingBasicModel(String front, String back) {
        return addNoteUsingModelName("Basic", front, back);
    }

    protected Note addNoteUsingModelName(String name, String... fields) {
        JSONObject model = getCol().getModels().byName(name);
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
        JSONObject model = getCol().getModels().newModel(name);
        for (int i = 0; i < fields.length; i++) {
            addField(model, fields[i]);
        }
        model.put(FlashCardsContract.CardTemplate.QUESTION_FORMAT, qfmt);
        model.put(FlashCardsContract.CardTemplate.ANSWER_FORMAT, afmt);
        getCol().getModels().add(model);
        getCol().getModels().flush();
        return name;
    }


    private void addField(JSONObject model, String name) {
        Models models = getCol().getModels();

        try {
            models.addField(model, models.newField(name));
        } catch (ConfirmModSchemaException e) {
            throw new RuntimeException(e);
        }
    }

    protected long addDeck(String deckName) {
        return getCol().getDecks().id(deckName, true);
    }

    protected long addDynamicDeck(String name) {
        return getCol().getDecks().newDyn(name);
    }

    protected void ensureCollectionLoadIsSynchronous() {
        //HACK: We perform this to ensure that onCollectionLoaded is performed synchronously when startLoadingCollection
        //is called.
        getCol();
    }


    protected SchedV2 upgradeToSchedV2() {
        getCol().getConf().put("schedVer", 2);
        getCol().setMod();
        CollectionHelper.getInstance().closeCollection(true, "upgradeToSchedV2");

        AbstractSched sched = getCol().getSched();
        //Sched inherits from schedv2...
        assertThat("sched should be v2", !(sched instanceof Sched));

        return (SchedV2) sched;
    }
}
