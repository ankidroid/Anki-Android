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
import com.ichi2.utils.JSONObject;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import androidx.core.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
    public void compareNewDatabases() throws JSONException {

        List<Object> expected = getResults();

        // If you don't tear down the database you'll get unexpected IllegalStateExceptions related to connections
        CollectionHelper.getInstance().closeCollection(false, "compareNewDatabases");

        // After every test make sure the CollectionHelper is no longer overridden (done for null testing)
        disableNullCollection();

        Storage.setUseBackend(true);

        List<Object> actual = getResults();


        for (int i = 0; i < expected.size(); i++) {
            if (i == 1 || i == 2 || i == 3) {
                continue;
            }
            if (i == 8) {
                JSONObject actualJson = new JSONObject(actual.get(i).toString());
                JSONObject expectedJson = new JSONObject(expected.get(i).toString());

                remove(actualJson, expectedJson, "curModel");
                remove(actualJson, expectedJson, "creationOffset");
                remove(actualJson, expectedJson, "localOffset");

                assertThat(actualJson.toString(), is(expectedJson.toString()));
                continue;
            }

            if (i == 9) {
                JSONObject actualJson = new JSONObject(actual.get(i).toString());
                JSONObject expectedJson = new JSONObject(expected.get(i).toString());

                renameKeys(actualJson);
                renameKeys(expectedJson);

                for (String k : actualJson) {
                    remove(actualJson.getJSONObject(k), expectedJson.getJSONObject(k), "id");
                }

                assertThat(actualJson.toString(4), is(expectedJson.toString(4)));
                continue;
            }

            assertThat(Integer.toString(i), actual.get(i), is(expected.get(i)));
        }
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

        keys.sort((x,y) -> x.second.compareTo(y.second));

        for (int i = 0; i < keys.size(); i++) {
            String keyName = keys.get(i).first;
            actualJson.put(Integer.toString(i+i), actualJson.get(keyName));
            actualJson.remove(keyName);
        }

    }


    protected List<Object> getResults() {
        List<Object> results = new ArrayList<>();
        try (Cursor c = getCol().getDb().query("select * from col")) {
            c.moveToFirst();


            for (int i = 0; i < c.getColumnCount(); i++) {
                results.add(c.getString(i));
            }
        }
        return results;
    }

}
