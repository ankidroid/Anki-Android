package com.ichi2.anki;

import com.ichi2.libanki.DB;

import org.junit.After;
import org.junit.Before;

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

public class RobolectricTest {

    @Before
    public void setUp() {
        // If you want to see the Android logging (from Timber), you need to set it up here
        //ShadowLog.stream = System.out;

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
}
