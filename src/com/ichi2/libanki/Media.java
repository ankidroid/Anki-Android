/****************************************************************************************
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

package com.ichi2.libanki;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.ichi2.anki.AnkiDroidApp;

/**
 * Class with static functions related with media handling (images and sounds).
 */
public class Media {
    // TODO: Javadoc.

    private static final Pattern mMediaRegexps[] = {
        Pattern.compile("(?i)(\\[sound:([^]]+)\\])"),
        Pattern.compile("(?i)(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")
    };
    private static final Pattern regPattern = Pattern.compile("\\((\\d+)\\)$");

    // File Handling
    // *************

    /**
     * Copy PATH to MEDIADIR, and return new filename.
     * If a file with the same md5sum exists in the DB, return that.
     * If a file with the same name exists, return a unique name.
     * This does not modify the media table.
     *
     * @param deck The deck whose media we are dealing with
     * @param path The path and filename of the media file we are adding
     * @return The new filename.
     */
    public static String copyToMedia(Deck deck, String path) {
        // See if have duplicate contents
        String newpath = null;
        Cursor cursor = null;
        try {
            cursor = deck.getDB().getDatabase().rawQuery("SELECT filename FROM media WHERE originalPath = '" +
                    Utils.fileChecksum(path) + "'", null);
            if (cursor.moveToNext()) {
                newpath = cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (newpath == null) {
            File file = new File(path);
            String base = file.getName();
            String mdir = deck.mediaDir(true);
            newpath = uniquePath(mdir, base);
            if (!file.renameTo(new File(newpath))) {
                Log.e(AnkiDroidApp.TAG, "Couldn't move media file " + path + " to " + newpath);
            }
        }
        return newpath.substring(newpath.lastIndexOf("/") + 1);
    }


    /**
     * Makes sure the filename of the media is unique.
     * If the filename matches an existing file, then a counter of the form " (x)" is appended before the media file
     * extension, where x = 1, 2, 3... as needed so that the filename is unique.
     *
     * @param dir The path to the media file, excluding the filename
     * @param base The filename of the file without the path
     */
    private static String uniquePath(String dir, String base) {
        // Remove any dangerous characters
        base = base.replaceAll("[][<>:/\\&", "");
        // Find a unique name
        int extensionOffset = base.lastIndexOf(".");
        String root = base.substring(0, extensionOffset);
        String ext = base.substring(extensionOffset);
        File file = null;
        while (true) {
            file = new File(dir, root + ext);
            if (!file.exists()) {
                break;
            }
            Matcher regMatcher = regPattern.matcher(root);
            if (!regMatcher.find()) {
                root = root + " (1)";
            } else {
                int num = Integer.parseInt(regMatcher.group(1));
                root = root.substring(regMatcher.start()) + " (" + num + ")";
            }
        }
        return dir + "/" + root + ext;
    }


    // DB Routines
    // ***********

    /**
     * Updates the field size of a media record.
     * The field size is used to store the count of how many times is this media referenced in question and answer
     * fields of the cards in the deck.
     *
     * @param deck The deck that contains the media we are dealing with
     * @param file The full path of the media in question
     */
    public static void updateMediaCount(Deck deck, String file) {
        updateMediaCount(deck, file, 1);
    }
    public static void updateMediaCount(Deck deck, String file, int count) {
        String mdir = deck.mediaDir();
        if (deck.getDB().queryScalar("SELECT 1 FROM media WHERE filename = '" + file + "'") == 1l) {
            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE,
                        "UPDATE media SET size = size + %d, created = %f WHERE filename = '%s'",
                        count, Utils.now(), file));
        } else if (count > 0) {
            String sum = Utils.fileChecksum(file);
//            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO media " +
//                    "(id, filename, size, created, originalPath, description) " +
//                    "VALUES (%d, '%s', %d, %f, '%s', '')", Utils.genID(), file, count, Utils.now(), sum));
        }
    }


    /**
     * Deletes from media table any entries that are not referenced in question or answer of any card.
     *
     * @param deck The deck that this operation will be performed on
     */
    public static void removeUnusedMedia(Deck deck) {
        ArrayList<Long> ids = deck.getDB().queryColumn(Long.class, "SELECT id FROM media WHERE size = 0", 0);
        for (Long id : ids) {
            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO mediaDeleted " +
                        "VALUES (%d, %f)", id.longValue(), Utils.now()));
        }
        deck.getDB().getDatabase().execSQL("DELETE FROM media WHERE size = 0");
    }

    // String manipulation
    // *******************

    public static ArrayList<String> mediaFiles(String string) {
        return mediaFiles(string, false);
    }
    public static ArrayList<String> mediaFiles(String string, boolean remote) {
        boolean isLocal = false;
        ArrayList<String> l = new ArrayList<String>();
        for (Pattern reg : mMediaRegexps) {
            Matcher m = reg.matcher(string);
            while (m.find()) {
                isLocal = !m.group(2).toLowerCase().matches("(https?|ftp)://");
                if (!remote && isLocal) {
                    l.add(m.group(2));
                } else if (remote && !isLocal) {
                    l.add(m.group(2));
                }
            }
        }
        return l;
    }

    /**
     * Removes references of media from a string.
     *
     * @param txt The string to be cleared of any media references
     * @return The cleared string without any media references
     */
    public static String stripMedia(String txt) {
        for (Pattern reg : mMediaRegexps) {
            txt = reg.matcher(txt).replaceAll("");
        }
        return txt;
    }

    // Rebuilding DB
    // *************

    /**
     * Rebuilds the reference counts, potentially deletes unused media files,
     *
     * @param deck The deck to perform the operation on
     * @param delete If true, then unused (unreferenced in question/answer fields) media files will be deleted
     * @param dirty If true, then the modified field of deck will be updated
     * @return Nothing, but the original python code returns a list of unreferenced media files and a list
     * of missing media files (referenced in question/answer fields, but with the actual files missing)
     */
    public static void rebuildMediaDir(Deck deck) {
        rebuildMediaDir(deck, false, true);
    }
    public static void rebuildMediaDir(Deck deck, boolean delete) {
        rebuildMediaDir(deck, delete, true);
    }
    public static void rebuildMediaDir(Deck deck, boolean delete, boolean dirty) {
        String mdir = deck.mediaDir();
        if (mdir == null) {
            return;
        }
        //Set all ref counts to 0
        deck.getDB().getDatabase().execSQL("UPDATE media SET size = 0");

        // Look through the cards for media references
        Cursor cursor = null;
        String txt = null;
        Map<String, Integer> refs = new HashMap<String, Integer>();
        Set<String> normrefs = new HashSet<String>();
        try {
            cursor = deck.getDB().getDatabase().rawQuery("SELECT question, answer FROM cards", null);
            while (cursor.moveToNext()) {
                for (int i = 0; i < 2; i++) {
                    txt = cursor.getString(i);
                    for (String f : mediaFiles(txt)) {
                        if (refs.containsKey(f)) {
                            refs.put(f, refs.get(f) + 1);
                        } else {
                            refs.put(f, 1);
                            // normrefs.add(Normalizer.normalize(f, Normalizer.Form.NFC));
                            normrefs.add(f);
                        }
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Update ref counts
        for (Entry<String, Integer> entry : refs.entrySet()) {
            updateMediaCount(deck, entry.getKey(), entry.getValue());
        }
        String fname = null;

        //If there is no media dir, then there is nothing to find.
        if(mdir != null) {
            // Find unused media
            Set<String> unused = new HashSet<String>();
            File mdirfile = new File(mdir);
            if (mdirfile.exists()) {
                fname = null;
                for (File f : mdirfile.listFiles()) {
                    if (!f.isFile()) {
                        // Ignore directories
                        continue;
                    }
                    // fname = Normalizer.normalize(f.getName(), Normalizer.Form.NFC);
                    fname = f.getName();
                    if (!normrefs.contains(fname)) {
                        unused.add(fname);
                    }
                }
            }
            // Optionally delete
            if (delete) {
                for (String fn : unused) {
                    File file = new File(mdir + "/" + fn);
                    try {
                        if (!file.delete()) {
                            Log.e(AnkiDroidApp.TAG, "Couldn't delete unused media file " + mdir + "/" + fn);
                        }
                    } catch (SecurityException e) {
                        Log.e(AnkiDroidApp.TAG, "Security exception while deleting unused media file " + mdir + "/" + fn);
                    }
                }
            }
        }
        // Remove entries in db for unused media
        removeUnusedMedia(deck);

        // Check md5s are up to date
        cursor = null;
        String path = null;
        fname = null;
        String md5 = null;
        SQLiteDatabase db = deck.getDB().getDatabase();
        db.beginTransaction();
        try {
            cursor = db.query("media", new String[] {"filename", "created", "originalPath"}, null, null, null, null, null);
            while (cursor.moveToNext()) {
                fname = cursor.getString(0);
                md5 = cursor.getString(2);
                path = mdir + "/" + fname;
                File file = new File(path);
                if (!file.exists()) {
                   if (!md5.equals("")) {
                       db.execSQL(String.format(Utils.ENGLISH_LOCALE,
                               "UPDATE media SET originalPath = '', created = %f where filename = '%s'",
                               Utils.now(), fname));
                   }
                } else {
                    String sum = Utils.fileChecksum(path);
                    if (!md5.equals(sum)) {
                       db.execSQL(String.format(Utils.ENGLISH_LOCALE,
                               "UPDATE media SET originalPath = '%s', created = %f where filename = '%s'",
                               sum, Utils.now(), fname));
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();

        // Update deck and get return info
        if (dirty) {
            deck.flushMod();
        }
        // In contrast to the python code we don't return anything. In the original python code, the return
        // values are used in a function (media.onCheckMediaDB()) that we don't have in AnkiDroid.
    }
}
