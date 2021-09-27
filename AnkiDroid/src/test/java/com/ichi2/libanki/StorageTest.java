/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki;

import android.database.Cursor;

import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.testutils.JsonUtils;
import com.ichi2.utils.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import androidx.core.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/** Regression test for Rust */
@RunWith(AndroidJUnit4.class)
public class StorageTest extends RobolectricTest {

    @Override
    protected boolean useLegacyHelper() {
        return true;
    }


    @Override
    public void setUp() {
        Storage.setUseBackend(false);
        super.setUp();
    }


    @Test
    public void compareNewDatabases() {

        CollectionData expected = getResults();

        // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
        CollectionHelper.getInstance().closeCollection(false, "compareNewDatabases");

        // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
        disableNullCollection();

        Storage.setUseBackend(true);

        CollectionData actual = getResults();

        actual.assertEqualTo(expected);
    }


    protected void remove(JSONObject actualJson, JSONObject expectedJson, String key) {
        actualJson.remove(key);
        expectedJson.remove(key);
    }


    protected void renameKeys(JSONObject actualJson) {
        List<Pair<String, String>> keys = new ArrayList<>();
        Iterator<String> keyIt = actualJson.keys();
        while (keyIt.hasNext()) {
            String name = keyIt.next();
            keys.add(new Pair<>(name, actualJson.getJSONObject(name).getString("name")));
        }

        keys.sort(Comparator.comparing(x -> x.second));

        for (int i = 0; i < keys.size(); i++) {
            String keyName = keys.get(i).first;
            actualJson.put(Integer.toString(i+i), actualJson.get(keyName));
            actualJson.remove(keyName);
        }

    }


    protected CollectionData getResults() {
        CollectionData results = new CollectionData();
        try (Cursor c = getCol().getDb().query("select * from col")) {
            c.moveToFirst();


            for (int i = 0; i < c.getColumnCount(); i++) {
                results.loadV11(i, c.getString(i));
            }
        }
        return results;
    }

    public class CollectionData {
        String mId;
        String mCrt;
        String mMod;
        String mScm;
        String mVer;
        String mDty;
        String mUsn;
        String mLs;
        String mConf;
        String mModels;
        String mDecks;
        String mDConf;
        String mTags;


        public void loadV11(int i, String string) {
            switch (i) {
                case 0: mId = string; return;
                case 1: mCrt = string; return;
                case 2: mMod = string; return;
                case 3: mScm = string; return;
                case 4: mVer = string; return;
                case 5: mDty = string; return;
                case 6: mUsn = string; return;
                case 7: mLs = string; return;
                case 8: mConf = string; return;
                case 9: mModels = string; return;
                case 10: mDecks = string; return;
                case 11: mDConf = string; return;
                case 12: mTags = string; return;
                default: throw new IllegalStateException("unknown i: " + i);
            }
        }


        public void assertEqualTo(CollectionData expected) {
            assertThat(this.mId, equalTo(expected.mId));
            // ignore due to timestamp: mCrt
            // ignore due to timestamp: mMod
            // ignore due to timestamp: mScm
            assertThat(this.mVer, equalTo(expected.mVer));
            assertThat(this.mDty, equalTo(expected.mDty));
            assertThat(this.mUsn, equalTo(expected.mUsn));
            assertThat(this.mLs, equalTo(expected.mLs));

            assertConfEqual(expected);

            assertModelsEqual(expected);

            assertThat(this.mDecks, equalTo(expected.mDecks));
            assertThat(this.mDConf, equalTo(expected.mDConf));
            assertThat(this.mTags, equalTo(expected.mTags));
        }


        protected void assertModelsEqual(CollectionData expected) {
            JSONObject actualJson = new JSONObject(this.mModels);
            JSONObject expectedJson = new JSONObject(expected.mModels);

            renameKeys(actualJson);
            renameKeys(expectedJson);

            for (String k : actualJson) {
                remove(actualJson.getJSONObject(k), expectedJson.getJSONObject(k), "id");
            }

            assertThat(actualJson.toString(4), is(expectedJson.toString(4)));
        }


        protected void assertConfEqual(CollectionData expectedData) {
            JSONObject actualJson = new JSONObject(this.mConf);
            JSONObject expectedJson = new JSONObject(expectedData.mConf);

            remove(actualJson, expectedJson, "curModel");
            remove(actualJson, expectedJson, "creationOffset");
            remove(actualJson, expectedJson, "localOffset");

            String actual = JsonUtils.toOrderedString(actualJson);
            String expected = JsonUtils.toOrderedString(expectedJson);
            assertThat(actual, is(expected));
        }
    }

}
