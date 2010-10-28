/***************************************************************************************
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
