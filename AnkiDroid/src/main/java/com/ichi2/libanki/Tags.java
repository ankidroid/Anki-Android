/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;


/**
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for notes.

This class differs from the python version by keeping the in-memory tag cache as a TreeMap
instead of a JSONObject. It is much more convenient to work with a TreeMap in Java, but there
may be a performance penalty in doing so (on startup and shutdown).
 */
@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes"})
public class Tags {

    private static final Pattern sCanonify = Pattern.compile("[\"']");

    private Collection mCol;
    private TreeMap<String, Integer> mTags = new TreeMap<>();
    private boolean mChanged;


    /**
     * Registry save/load
     * ***********************************************************
     */

    public Tags(Collection col) {
        mCol = col;
    }


    public void load(String json) {
        try {
            JSONObject tags = new JSONObject(json);
            Iterator<?> i = tags.keys();
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
            val.put("tags", Utils.jsonToString(tags));
            // TODO: the database update call here sets mod = true. Verify if this is intended.
            mCol.getDb().update("col", val);
            mChanged = false;
        }
    }


    /**
     * Registering and fetching tags
     * ***********************************************************
     */

    /** Given a list of tags, add any missing ones to tag registry. */
    public void register(Iterable<String> tags) {
        register(tags, null);
    }


    public void register(Iterable<String> tags, Integer usn) {
        //boolean found = false;
        for (String t : tags) {
            if (!mTags.containsKey(t)) {
                mTags.put(t, usn == null ? mCol.usn() : usn);
                mChanged = true;
            }
        }
        //if (found) {
        //    runHook("newTag"); // TODO
        //}
    }


    public List<String> all() {
        List<String> list = new ArrayList<>();
        list.addAll(mTags.keySet());
        return list;
    }


    public void registerNotes() {
        registerNotes(null);
    }


    /** Add any missing tags from notes to the tags list. */
    public void registerNotes(long[] nids) {
        // when called with a null argument, the old list is cleared first.
        String lim;
        if (nids != null) {
            lim = " WHERE id IN " + Utils.ids2str(nids);
        } else {
            lim = "";
            mTags.clear();
            mChanged = true;
        }
        List<String> tags = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = mCol.getDb().getDatabase().query("SELECT DISTINCT tags FROM notes"+lim, null);
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(0));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        HashSet<String> tagSet = new HashSet<>();
        for (String s : split(TextUtils.join(" ", tags))) {
            tagSet.add(s);
        }
        register(tagSet);
    }


    public Set<Map.Entry<String, Integer>> allItems() {
        return mTags.entrySet();
    }


    public void save() {
        mChanged = true;
    }


    /**
    * byDeck returns the tags of the cards in the deck
    * @param did the deck id
    * @param children whether to include the deck's children
    * @return a list of the tags
    */
    public ArrayList<String> byDeck(long did, boolean children) {
        String sql;
        if (children) {
            ArrayList<Long> dids = new ArrayList<>();
            dids.add(did);
            for (long id : mCol.getDecks().children(did).values()) {
                dids.add(id);
            }
            sql = "SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did IN " + Utils.ids2str(Utils.arrayList2array(dids));
        } else {
            sql = "SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did = " + did;
        }
        List<String> tags = mCol.getDb().queryColumn(String.class, sql, 0);
        // Cast to set to remove duplicates
        // Use methods used to get all tags to parse tags here as well.
        return new ArrayList<>(new HashSet<>(split(TextUtils.join(" ", tags))));
    }


    /**
     * Bulk addition/removal from notes
     * ***********************************************************
     */

    /**
     * FIXME: This method must be fixed before it is used. See note below.
     * Add/remove tags in bulk. TAGS is space-separated.
     *
     * @param ids The cards to tag.
     * @param tags List of tags to add/remove. They are space-separated.
     */
    public void bulkAdd(List<Long> ids, String tags) {
        bulkAdd(ids, tags, true);
    }


    /**
     * FIXME: This method must be fixed before it is used. Its behaviour is currently incorrect.
     * This method is currently unused in AnkiDroid so it will not cause any errors in its current state.
     *
     * @param ids The cards to tag.
     * @param tags List of tags to add/remove. They are space-separated.
     * @param add True/False to add/remove.
     */
    public void bulkAdd(List<Long> ids, String tags, boolean add) {
        List<String> newTags = split(tags);
        if (newTags == null || newTags.isEmpty()) {
            return;
        }
        // cache tag names
        register(newTags);
        // find notes missing the tags
        String l;
        if (add) {
            l = "tags not ";
        } else {
            l = "tags ";
        }
        StringBuilder lim = new StringBuilder();
        for (String t : newTags) {
            if (lim.length() != 0) {
                lim.append(" or ");
            }
            lim.append(l).append("like '% ").append(t).append(" %'");
        }
        Cursor cur = null;
        List<Long> nids = new ArrayList<>();
        ArrayList<Object[]> res = new ArrayList<>();
        try {
            cur = mCol
                    .getDb()
                    .getDatabase()
                    .query("select id, tags from notes where id in " + Utils.ids2str(ids) +
                            " and (" + lim + ")", null);
            if (add) {
                while (cur.moveToNext()) {
                    nids.add(cur.getLong(0));
                    res.add(new Object[] { addToStr(tags, cur.getString(1)), Utils.intNow(), mCol.usn(), cur.getLong(0) });
                }
            } else {
                while (cur.moveToNext()) {
                    nids.add(cur.getLong(0));
                    res.add(new Object[] { remFromStr(tags, cur.getString(1)), Utils.intNow(), mCol.usn(),
                            cur.getLong(0) });
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        // update tags
        mCol.getDb().executeMany("update notes set tags=:t,mod=:n,usn=:u where id = :id", res);
    }


    public void bulkRem(List<Long> ids, String tags) {
        bulkAdd(ids, tags, false);
    }


    /**
     * String-based utilities
     * ***********************************************************
     */

    /** Parse a string and return a list of tags. */
    public List<String> split(String tags) {
        ArrayList<String> list = new ArrayList<>();
        for (String s : tags.replace('\u3000', ' ').split("\\s")) {
            if (s.length() > 0) {
                list.add(s);
            }
        }
        return list;
    }


    /** Join tags into a single string, with leading and trailing spaces. */
    public String join(java.util.Collection<String> tags) {
        if (tags == null || tags.size() == 0) {
            return "";
        } else {
            String joined = TextUtils.join(" ", tags);
            return String.format(Locale.US, " %s ", joined);
        }
    }


    /** Add tags if they don't exist, and canonify */
    public String addToStr(String addtags, String tags) {
        List<String> currentTags = split(tags);
        for (String tag : split(addtags)) {
            if (!inList(tag, currentTags)) {
                currentTags.add(tag);
            }
        }
        return join(canonify(currentTags));
    }


    /** Delete tags if they don't exist. */
    public String remFromStr(String deltags, String tags) {
        List<String> currentTags = split(tags);
        for (String tag : split(deltags)) {
            List<String> remove = new ArrayList<>();
            for (String tx: currentTags) {
                if (tag.equalsIgnoreCase(tx)) {
                    remove.add(tx);
                }
            }
            // remove them
            for (String r : remove) {
                currentTags.remove(r);
            }
        }
        return join(currentTags);
    }


    /**
     * List-based utilities
     * ***********************************************************
     */

    /** Strip duplicates, adjust case to match existing tags, and sort. */
    public TreeSet<String> canonify(List<String> tagList) {
        // NOTE: The python version creates a list of tags, puts them into a set, then sorts them. The TreeSet
        // used here already guarantees uniqueness and sort order, so we return it as-is without those steps.
        TreeSet<String> strippedTags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String t : tagList) {
            String s = sCanonify.matcher(t).replaceAll("");
            for (String existingTag : mTags.keySet()) {
                if (s.equalsIgnoreCase(existingTag)) {
                    s = existingTag;
                }
            }
            strippedTags.add(s);
        }
        return strippedTags;
    }


    /** True if TAG is in TAGS. Ignore case. */
    public boolean inList(String tag, Iterable<String> tags) {
        for (String t : tags) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Sync handling
     * ***********************************************************
     */

    public void beforeUpload() {
        for (String k : mTags.keySet()) {
            mTags.put(k, 0);
        }
        save();
    }

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */


    /** Add a tag to the collection. We use this method instead of exposing mTags publicly.*/
    public void add(String key, Integer value) {
        mTags.put(key, value);
    }
}
