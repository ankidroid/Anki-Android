/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import org.json.JSONException;
import org.json.JSONObject;

public class Tags {

	private Collection mCol;
	private JSONObject mTags;
	private boolean mChanged;

    /**
     * Registry save/load
     * ***********************************************************************************************
     */

	public Tags(Collection col) {
		mCol = col;
	}


	public void load(String json) {
        try {
        	mTags = new JSONObject(json);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
        mChanged = false;
	}


    public void flush() {
    	if (mChanged) {
    		mCol.getDb().getDatabase().execSQL("UPDATE col SET tags = " + mTags.toString());
    		mChanged = false;
    	}
    }

    /**
     * Registering and fetching tags
     * ***********************************************************************************************
     */

    public void register(String[] tags) {
    	register(tags, 0);
    }
    public void register(String[] tags, int usn) {
    	// TODO
    }


    // all
    //registernotes
    //allitems
    //save


    /**
     * Bulk addition/removal from notes
     * ***********************************************************************************************
     */

    // bulkadd
    //bulkrem


    /**
     * String-based utilities
     * ***********************************************************************************************
     */

    /** Parse a string and return a list of tags. */
    public static String[] split(String tags) {
        if (tags != null && tags.length() != 0) {
            return tags.split("\\s");
        } else {
            return new String[] {};
        }
    }


    /** Join tags into a single string, with leading and trailing spaces. */
    public static String join(java.util.Collection<String> tags) {
        if (tags == null || tags.size() == 0) {
            return "";
        } else {
            StringBuilder result = new StringBuilder(128);
            result.append(" ");
            for (String tag : tags) {
                result.append(tag).append(" ");
            }
            return result.toString();
        }
    }


    /** Add tags if they don't exist, and canonify */
    public String addToStr(String addtags, String tags) {
    	// TODO
    	return "";
    }


    //remFromStr

    /**
     * List-based utilities
     * ***********************************************************************************************
     */

    /** Strip duplicates and sort. */
    public static TreeSet<String> canonify(String[] tagList) {
    	TreeSet<String> tree = new TreeSet<String>(Arrays.asList(tagList));
    	return tree;
//    	return tree.toArray(new String[tree.size()]);
//        return joinTags(new TreeSet<String>(Arrays.asList(tags)));
    }


    /** True if TAG is in TAGS. Ignore case. */
    public static boolean inList(String tag, List<String> tags) {
        for (String t : tags) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }


    /** True if TAG is in TAGS. Ignore case. */
    public static boolean inList(String tag, String[] tags) {
        for (String t : tags) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }


//    /**
//     * Add tags if they don't exist.
//     * Both parameters are in string format, the tags being separated by space or comma, as in parseTags
//     * 
//     * @param tagStr The new tag(s) that are to be added
//     * @param tags The set of tags where the new ones will be added
//     * @return A string containing the union of tags of the input parameters
//     */
//    public static String addTags(String addtags, String tags) {
//        ArrayList<String> currentTags = new ArrayList<String>(Arrays.asList(parseTags(tags)));
//        for (String tag : parseTags(addtags)) {
//            if (!hasTag(tag, currentTags)) {
//                currentTags.add(tag);
//            }
//        }
//        return canonifyTags((String[]) currentTags.toArray());
//    }

    /**
     * Tag-based selective study
     * ***********************************************************************************************
     */

    // seltaqgnids
    //setdeckfortags


    /**
     * Sync handling
     * ***********************************************************************************************
     */

    // before upload
}