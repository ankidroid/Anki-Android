/***************************************************************************************
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>                          *
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

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;

import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

/**
 * Singleton which opens, stores, and closes the reference to the Collection.
 */
public class CollectionHelper {

    // Collection instance belonging to sInstance
    private Collection mCollection;
    // Path to collection, cached for the reopenCollection() method
    private String mPath;
    // Name of anki2 file
    public static final String COLLECTION_FILENAME = "collection.anki2";

    /**
     * Prevents {@link com.ichi2.async.CollectionLoader} from spuriously re-opening the {@link Collection}.
     *
     * <p>Accessed only from synchronized methods.
     */
    private boolean mCollectionLocked;
    public synchronized void lockCollection() {
        mCollectionLocked = true;
    }
    public synchronized void unlockCollection() {
        mCollectionLocked = false;
    }
    public synchronized boolean isCollectionLocked() {
        return mCollectionLocked;
    }


    /**
     * Lazy initialization holder class idiom. High performance and thread safe way to create singleton.
     */
    private static class LazyHolder {
        private static final CollectionHelper INSTANCE = new CollectionHelper();
    }

    /**
     * @return Singleton instance of the helper class
     */
    public static CollectionHelper getInstance() {
        return LazyHolder.INSTANCE;
    }


    /**
     * Get the single instance of the {@link Collection}, creating it if necessary  (lazy initialization).
     * @param context context which can be used to get the setting for the path to the Collection
     * @return instance of the Collection
     */
    public synchronized Collection getCol(Context context) {
        // Open collection
        String path = getCollectionPath(context);
        if (!colIsOpen()) {
            // Check that the directory has been created and initialized
            try {
                initializeAnkiDroidDirectory(getParentDirectory(path));
                mPath = path;
            } catch (StorageAccessException e) {
                Timber.e(e, "Could not initialize AnkiDroid directory");
                return null;
            }
            // Open the database
            Timber.i("openCollection: %s", path);
            mCollection = Storage.Collection(context, path, false, true);
        }
        return mCollection;
    }

    /**
     * Call getCol(context) inside try / catch statement.
     * Send exception report and return null if there was an exception.
     * @param context
     * @return
     */
    public synchronized Collection getColSafe(Context context) {
        try {
            return getCol(context);
        } catch (Exception e) {
            AnkiDroidApp.sendExceptionReport(e, "CollectionHelper.getColSafe");
            return null;
        }
    }

    /**
     * Close the {@link Collection}, optionally saving
     * @param save whether or not save before closing
     */
    public synchronized void closeCollection(boolean save) {
        Timber.i("closeCollection");
        if (mCollection != null) {
            mCollection.close(save);
        }
    }

    /**
     * @return Whether or not {@link Collection} and its child database are open.
     */
    public boolean colIsOpen() {
        return mCollection != null && mCollection.getDb() != null &&
                mCollection.getDb().getDatabase() != null && mCollection.getDb().getDatabase().isOpen();
    }

    /**
     * Create the AnkiDroid directory if it doesn't exist and add a .nomedia file to it if needed.
     *
     * The AnkiDroid directory is a user preference stored under the "deckPath" key, and a sensible
     * default is chosen if the preference hasn't been created yet (i.e., on the first run).
     *
     * The presence of a .nomedia file indicates to media scanners that the directory must be
     * excluded from their search. We need to include this to avoid media scanners including
     * media files from the collection.media directory. The .nomedia file works at the directory
     * level, so placing it in the AnkiDroid directory will ensure media scanners will also exclude
     * the collection.media sub-directory.
     *
     * @param path  Directory to initialize
     * @throws StorageAccessException If no write access to directory
     */
    public static synchronized void initializeAnkiDroidDirectory(String path) throws StorageAccessException {
        // Create specified directory if it doesn't exit
        File dir = new File(path);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new StorageAccessException("Failed to create AnkiDroid directory");
        }
        if (!dir.canWrite()) {
            throw new StorageAccessException("No write access to AnkiDroid directory");
        }
        // Add a .nomedia file to it if it doesn't exist
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                throw new StorageAccessException("Failed to create .nomedia file");
            }
        }
    }

    /**
     * Try to access the current AnkiDroid directory
     * @return whether or not dir is accessible
     * @param context to get directory with
     */
    public static boolean isCurrentAnkiDroidDirAccessible(Context context) {
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory(context));
            return true;
        } catch (StorageAccessException e) {
            return false;
        }
    }


    /**
     * Get the absolute path to a directory that is suitable to be the default starting location
     * for the AnkiDroid folder. This is a folder named "AnkiDroid" at the top level of the
     * external storage directory.
     * @return the folder path
     */
    public static String getDefaultAnkiDroidDirectory() {
        return new File(Environment.getExternalStorageDirectory(), "AnkiDroid").getAbsolutePath();
    }

    /**
     *
     * @return the path to the actual {@link Collection} file
     */
    public static String getCollectionPath(Context context) {
        return new File(getCurrentAnkiDroidDirectory(context), COLLECTION_FILENAME).getAbsolutePath();
    }


    /**
     * @return the absolute path to the AnkiDroid directory.
     */
    public static String getCurrentAnkiDroidDirectory(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        return preferences.getString("deckPath", getDefaultAnkiDroidDirectory());
    }

    /**
     * Get parent directory given the {@link Collection} path.
     * @param path path to AnkiDroid collection
     * @return path to AnkiDroid folder
     */
    private static String getParentDirectory(String path) {
        return new File(path).getParentFile().getAbsolutePath();
    }

    /**
     * Check if we have permission to access the external storage
     * @param context
     * @return
     */
    public static boolean hasStorageAccessPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }
}
