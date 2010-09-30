
package com.ichi2.anki;

import android.database.SQLException;

import java.util.HashMap;
import java.util.Set;

public class AnkiDatabaseManager {

    private static HashMap<String, AnkiDb> sAnkiDatabases = new HashMap<String, AnkiDb>();


    public static AnkiDb getDatabase(String pathDB) throws SQLException {
        // If the DB is already opened
        if (sAnkiDatabases.containsKey(pathDB)) {
            return sAnkiDatabases.get(pathDB);
        }

        // If a connection to the desired DB does not exist, we create it
        AnkiDb ankiDB = new AnkiDb(pathDB);

        // Insert the new DB to the map of opened DBs
        sAnkiDatabases.put(pathDB, ankiDB);

        return ankiDB;
    }


    public static void closeDatabase(String pathDB) {
        AnkiDb ankiDB = sAnkiDatabases.remove(pathDB);
        if (ankiDB != null) {
            ankiDB.closeDatabase();
        }
    }


    public static void closeAllDatabases() {
        Set<String> databases = sAnkiDatabases.keySet();
        for (String pathDB : databases) {
            AnkiDatabaseManager.closeDatabase(pathDB);
        }
    }


    public static boolean isDatabaseOpen(String pathDB) {
        if (sAnkiDatabases.containsKey(pathDB)) {
            return true;
        }

        return false;
    }
}
