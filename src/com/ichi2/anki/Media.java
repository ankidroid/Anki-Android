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

package com.ichi2.anki;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Random;

/**
 * Class with static functions related with media handling (images and sounds).
 */
public class Media {
    // TODO: Javadoc.

    private int mId;
    private String mFilename;
    // Reused as reference count
    private int mCount;
    // Treated as modification date, not creation date
    private double mCreated;
    // Reused as md5sum. Empty string if file doesn't exist on disk
    private String mOriginal = "";
    // Older versions stored original filename here, so we'll leave it for now in case we add a feature to rename media
    // back to its original name. In the future we may want to zero this to save space
    private String mDescription = "";

    private static final Pattern mMediaRegexps[] = {
        Pattern.compile("(\\[sound:([^]]+)\\])"),
        Pattern.compile("(<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>)")
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
            cursor = deck.getDB().getDatabase().rawQuery("SELECT filename FROM media WHERE originalPath = " +
                    Utils.fileChecksum(path), null);
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
            newpath = uniquePAth(mdir, base);
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
    private String uniquePath(String dir, base) {
        // Remove any dangerous characters
        base = base.replaceAll("[][<>:/\\", "");
        // Find a unique name
        int extensionOffset = base.lastIndexOf(".")
        String root = base.substring(0, extensionOffset);
        String ext = base.substring(extensionOffset);
        File file = null;
        while (1) {
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
    public void updateMediaCount(Deck deck, String file) {
        updateMediaCount(deck, file, 1);
    }
    public void updateMediaCount(Deck deck, String file, int count) {
        String mdir = deck.mediaDir();
        if (deck.getDB().queryScalar("SELECT 1 FROM media WHERE filename = " + file) == 1l) {
            deck.getDB().execSQL(String.format(Utils.ENGLISH_LOCALE, "UPDATE media SET size = size + %d, " +
                       "created = %f WHERE filename = %s", count, Utils.now(), file));
        } else if (count > 0) {
            String sum = Utils.fileChecksum(file);
            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO media " +
                    "(id, filename, size, created, originalPath, description) " +
                    "VALUES (%d, %s, %d, %f, %s, '')", genID(), file, count, Utils.now(), sum));
        }
    }


    /**
     * Deletes from media table any entries that are not referenced in question or answer of any card.
     *
     * @param deck The deck that this operation will be performed on
     */
    public void removeUnusedMedia(Deck deck) {
        ArrayList<Long> ids = deck.getDB().queryColumn(Long.class, "SELECT id FROM media WHERE size = 0", 0);
        for (Long id : ids) {
            deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "INSERT INTO mediaDeleted " +
                        "VALUES (%d, %f)", id.longValue(), Utils.now()));
        }
        deck.getDB().getDatabase().execSQL("DELETE FROM media WHERE size = 0");
    }

    // String manipulation
    // *******************

    public static Arraylist<String> mediaFiles(String string) {
        return mediaFiles(string, false);
    }
    public static Arraylist<String> mediaFiles(String string, boolean remote) {
        boolean isLocal = false;
        ArrayList<String> l = new ArrayList<String>();
        for (Pattern reg : mMediaRegexps) {
            Matcher m = reg.matcher(string);
            while (m.find()) {
                isLocal = m.group(2).toLowerCase.matches("(https?|ftp)://.*");
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
            Matcher m = reg.matcher(string);
            txt = replaceAll("");
        }
        return txt;
    }



}
