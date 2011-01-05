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

    // File Handling
    // *************
    
    public static String copyToMedia(Deck deck, String path) {
    }

    // String manipulation
    // *******************

    public static Arraylist<String> mediaFiles(String string) {
        return mediaFiles(string, false);
    }
    public static Arraylist<String> mediaFiles(String string, boolean remote) {
        boolean isLocal = false;
        ArrayList<String> l = new ArrayList<String>();
        for (String reg : mMediaRegexps) {
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



}
