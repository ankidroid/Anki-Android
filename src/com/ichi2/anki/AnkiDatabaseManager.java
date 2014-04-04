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

import java.util.HashMap;
import java.util.Set;

public class AnkiDatabaseManager {

    private static HashMap<String, AnkiDb> sAnkiDatabases = new HashMap<String, AnkiDb>();


    /* Prevent class from being instantiated */
    private AnkiDatabaseManager() {
    }


    /**
     * Get a reference over an Anki database, creating the connection if needed.
     *
     * @param pathDB the path to the database.
     * @return the Anki database.
     */
    public static AnkiDb getDatabase(String pathDB) {

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


    /**
     * Close connection to a given database.
     *
     * @param pathDB the path to the database to close.
     */
    public static void closeDatabase(String pathDB) {
        AnkiDb ankiDB = sAnkiDatabases.remove(pathDB);
        if (ankiDB != null) {
            ankiDB.closeDatabase();
        }
    }


    /**
     * Close connections to all opened databases. XXX Currently unused.
     */
    public static void closeAllDatabases() {
        Set<String> databases = sAnkiDatabases.keySet();
        for (String pathDB : databases) {
            AnkiDatabaseManager.closeDatabase(pathDB);
        }
    }


    /**
     * Check if there is a valid connection to the given database.
     *
     * @param pathDB the path to the database we want to check.
     * @return True if the database is already opened, false otherwise.
     */
    public static boolean isDatabaseOpen(String pathDB) {
        return sAnkiDatabases.containsKey(pathDB);
    }
}
