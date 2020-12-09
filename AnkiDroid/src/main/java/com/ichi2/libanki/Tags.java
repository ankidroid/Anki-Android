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

import com.ichi2.utils.JSONObject;

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

import okhttp3.internal.Util;


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

    private final Collection mCol;
    private final TreeMap<String, Integer> mTags = new TreeMap<>();
    private boolean mChanged;


    /**
     * Registry save/load
     * ***********************************************************
     */

    public Tags(Collection col) {
        mCol = col;
    }


    public void load(String json) {
        JSONObject tags = new JSONObject(json);
        for (String t : tags) {
            mTags.put(t, tags.getInt(t));
        }
        mChanged = false;
    }


    public void flush() {
        if (mChanged) {
            JSONObject tags = new JSONObject();
            for (Map.Entry<String, Integer> t : mTags.entrySet()) {
                tags.put(t.getKey(), t.getValue());
            }
            ContentValues val = new ContentValues();
            val.put("tags", Utils.jsonToString(tags));
            // TODO: the database update call here sets mod = true. Verify if this is intended.
            mCol.getDb().update("col", val);
            mChanged = false;
        }
    }


    /*
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
        return new ArrayList<>(mTags.keySet());
    }


    public void registerNotes() {
        registerNotes(null);
    }


    /** Add any missing tags from notes to the tags list. */
    public void registerNotes(java.util.Collection<Long> nids) {
        // when called with a null argument, the old list is cleared first.
        String lim;
        if (nids != null) {
            lim = " WHERE id IN " + Utils.ids2str(nids);
        } else {
            lim = "";
            mTags.clear();
            mChanged = true;
        }
        List<String> tags = new ArrayList<>(mCol.noteCount());
        try (Cursor cursor = mCol.getDb().query("SELECT DISTINCT tags FROM notes" + lim)) {
            while (cursor.moveToNext()) {
                tags.add(cursor.getString(0));
            }
        }
        HashSet<String> tagSet = new HashSet<>(split(TextUtils.join(" ", tags)));
        register(tagSet);
    }


    public Set<Map.Entry<String, Integer>> allItems() {
        return mTags.entrySet();
    }

    public boolean minusOneValue() {
        return mTags.containsValue(-1);
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
        List<String> tags;
        if (children) {
            java.util.Collection<Long> values = mCol.getDecks().children(did).values();
            ArrayList<Long> dids = new ArrayList<>(values.size());
            dids.add(did);
            dids.addAll(values);
            tags = mCol.getDb().queryStringList("SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did IN " + Utils.ids2str(dids));
        } else {
            tags = mCol.getDb().queryStringList("SELECT DISTINCT n.tags FROM cards c, notes n WHERE c.nid = n.id AND c.did = ?", did);
        }
        // Cast to set to remove duplicates
        // Use methods used to get all tags to parse tags here as well.
        return new ArrayList<>(new HashSet<>(split(TextUtils.join(" ", tags))));
    }


    /*
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
        if (add) {
            register(newTags);
        }
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
            t = t.replace("*", "%");
            lim.append(l).append("like '% ").append(t).append(" %'");
        }
        ArrayList<Object[]> res = new ArrayList<>(mCol.getDb().queryScalar("select count() from notes where id in "+ Utils.ids2str(ids) + " and (" + lim + ")"));
        try (Cursor cur = mCol
                .getDb()
                .query("select id, tags from notes where id in " + Utils.ids2str(ids) +
                        " and (" + lim + ")")) {
            if (add) {
                while (cur.moveToNext()) {
                    res.add(new Object[] { addToStr(tags, cur.getString(1)), mCol.getTime().intTime(), mCol.usn(), cur.getLong(0) });
                }
            } else {
                while (cur.moveToNext()) {
                    res.add(new Object[] { remFromStr(tags, cur.getString(1)), mCol.getTime().intTime(), mCol.usn(),
                            cur.getLong(0) });
                }
            }
        }
        // update tags
        mCol.getDb().executeMany("update notes set tags=:t,mod=:n,usn=:u where id = :id", res);
    }


    public void bulkRem(List<Long> ids, String tags) {
        bulkAdd(ids, tags, false);
    }


    /*
     * String-based utilities
     * ***********************************************************
     */

    /** Parse a string and return a list of tags. */
    public ArrayList<String> split(String tags) {
        ArrayList<String> list = new ArrayList<>(tags.length());
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

    // submethod of remFromStr in anki
    public boolean wildcard(String pat, String str) {
        String pat_replaced = Pattern.quote(pat).replace("\\*", ".*");
        return Pattern.compile(pat_replaced, Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(str).matches();
    }

    /** Delete tags if they exist. */
    public String remFromStr(String deltags, String tags) {
        List<String> currentTags = split(tags);
        for (String tag : split(deltags)) {
            List<String> remove = new ArrayList<>(); // Usually not a lot of tags are removed simultaneously.
            // So don't put initial capacity
            for (String tx: currentTags) {
                if (tag.equalsIgnoreCase(tx) || wildcard(tag, tx)) {
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


    /*
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
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : mTags.entrySet()) {
            if (entry.getValue() != 0) {
                mTags.put(entry.getKey(), 0);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
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
