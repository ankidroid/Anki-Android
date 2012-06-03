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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;

public class Tags {

	private Collection mCol;
	private TreeMap<String, Integer> mTags = new TreeMap<String, Integer>();
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
        	JSONObject tags = new JSONObject(json);
        	Iterator i = tags.keys();
        	while (i.hasNext()) {
        		String t = (String) i.next();
        		mTags.put(t, tags.getInt(t));
        	}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
        mChanged = false;
	}


    public void flush() {
    	if (mChanged) {
    		JSONObject tags = new JSONObject();
    		for (Map.Entry<String, Integer> t : mTags.entrySet()) {
    			try {
					tags.put(t.getKey(), t.getValue());
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
    		}
    		ContentValues val = new ContentValues();
    		val.put("tags", tags.toString());
    		mCol.getDb().update("col", val);
    		mChanged = false;
    	}
    }

    /**
     * Registering and fetching tags
     * ***********************************************************************************************
     */

    /** Given a list of tags, add any missing ones to tag registry. */
    public void register(ArrayList<String> tags) {
    	register(tags, 0);
    }
    public void register(ArrayList<String> tags, int usn) {
    	// case is stored as received, so user can create different case
    	// versions of the same tag if they ignore the qt autocomplete.
    	for (String t : tags) {
    		if (!mTags.containsKey(t)) {
    			mTags.put(t, usn == 0 ? mCol.usn() : usn);
    			mChanged = true;
    		}
    	}
    }

    public String[] all() {
    	String[] tags = new String[mTags.size()];
    	int i = 0;
    	for (String t : mTags.keySet()) {
    		tags[i++] = t;
    	}
    	return tags;
    }

    public void registerNotes() {
    	registerNotes(null);
    }
    /** Add any missing tags from notes to the tags list. */
    public void registerNotes(long[] nids) {
    	// when called without an argument, the old list is cleared first.
    	String lim;
    	if (nids != null) {
    		lim = " WHERE id IN " + Utils.ids2str(nids);
    	} else {
    		lim = "";
    		mTags.clear();
    		mChanged = true;
    	}
    	ArrayList<String> tags = new ArrayList<String>();
    	Cursor cursor = null;
    	try {
    		cursor = mCol.getDb().getDatabase().rawQuery("SELECT DISTINCT tags FROM notes", null);
    		while (cursor.moveToNext()) {
    			for (String t : cursor.getString(0).split("\\s")) {
    				if (t.length() > 0) {
    					tags.add(t);
    				}
    			}
    		}
    	} finally {
    		if (cursor != null) {
    			cursor.close();
    		}
    	}
    	register(tags);
    }

    public TreeMap<String, Integer> allItems() {
    	return mTags;
    }

    public void save() {
    	mChanged = true;
    }

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
    public ArrayList<String> split(String tags) {
    	ArrayList<String> list = new ArrayList<String>();
        if (tags != null && tags.length() != 0) {
        	for (String s : tags.split("\\s")) {
        		if (s.length() > 0) {
            		list.add(s);        			
        		}
        	}
        }
        return list;
    }


    /** Join tags into a single string, with leading and trailing spaces. */
    public String join(java.util.Collection<String> tags) {
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
    public TreeSet<String> canonify(ArrayList<String> tagList) {
    	TreeSet<String> tree = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    	tree.addAll(tagList);
    	return tree;
    }


    /** True if TAG is in TAGS. Ignore case. */
    public boolean inList(String tag, List<String> tags) {
        for (String t : tags) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }


    /** True if TAG is in TAGS. Ignore case. */
    public boolean inList(String tag, String[] tags) {
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

    // seltagnids
    //setdeckfortags


    /**
     * Sync handling
     * ***********************************************************************************************
     */

	public void beforeUpload() {
		for (String k : mTags.keySet()) {
			mTags.put(k, 0);
		}
		save();
	}

}
