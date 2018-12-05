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

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Models;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLog;

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;

public class RobolectricTest {

    @Before
    public void setUp() {
        // If you want to see the Android logging (from Timber), you need to set it up here
        ShadowLog.stream = System.out;

        // Robolectric can't handle our default sqlite implementation of requery, it needs the framework
        DB.setSqliteOpenHelperFactory(new FrameworkSQLiteOpenHelperFactory());
    }

    @After
    public void tearDown() {
        // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
        CollectionHelper.getInstance().closeCollection(false);

        // After every test, make sure the sqlite implementation is set back to default
        DB.setSqliteOpenHelperFactory(null);
    }


    protected void clickDialogButton(DialogAction button) {
        MaterialDialog dialog = (MaterialDialog)ShadowDialog.getLatestDialog();
        dialog.getActionButton(button).performClick();
    }


    protected String getDialogText() {
        MaterialDialog dialog = (MaterialDialog)ShadowDialog.getLatestDialog();
        return dialog == null || dialog.getContentView() == null ? null : dialog.getContentView().getText().toString();
    }


    protected Context getTargetContext() {
        return ApplicationProvider.getApplicationContext();
    }


    protected String getResourceString(int res) {
        return getTargetContext().getString(res);
    }


    protected Collection getCol() {
        return CollectionHelper.getInstance().getCol(getTargetContext());
    }


    protected JSONObject getCurrentDatabaseModelCopy(String modelName) throws JSONException {
        Models collectionModels = getCol().getModels();
        return new JSONObject(collectionModels.byName(modelName).toString().trim());
    }
}
