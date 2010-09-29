
package com.ichi2.anki;

import android.database.SQLException;

import java.util.HashMap;
import java.util.Set;

public class AnkiDatabaseManager {

    private static HashMap<String, AnkiDb> mAnkiDatabases = new HashMap<String, AnkiDb>();


    public static AnkiDb getDatabase(String pathDB) throws SQLException {
        // If the DB is already opened
        if (mAnkiDatabases.containsKey(pathDB)) {
            return mAnkiDatabases.get(pathDB);
        }

        // If a connection to the desired DB does not exist, we create it
        AnkiDb ankiDB = new AnkiDb(pathDB);

        // Insert the new DB to the map of opened DBs
        mAnkiDatabases.put(pathDB, ankiDB);

        return ankiDB;
    }


    public static void closeDatabase(String pathDB) {
        AnkiDb ankiDB = mAnkiDatabases.remove(pathDB);
        if (ankiDB != null) {
            ankiDB.closeDatabase();
        }
    }


    public static void closeAllDatabases() {
        Set<String> databases = mAnkiDatabases.keySet();
        for (String pathDB : databases) {
            AnkiDatabaseManager.closeDatabase(pathDB);
        }
    }


    public static boolean isDatabaseOpen(String pathDB) {
        if (mAnkiDatabases.containsKey(pathDB)) {
            return true;
        }

        return false;
    }
}
