/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import android.database.Cursor;

import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class Note implements Cloneable {

    private Collection mCol;

    private long mId;
    private String mGuId;
    private JSONObject mModel;
    private long mMid;
    private List<String> mTags;
    private String[] mFields;
    private int mFlags;
    private String mData;
    private Map<String, Pair<Integer, JSONObject>> mFMap;
    private long mScm;
    private int mUsn;
    private long mMod;
    private boolean mNewlyAdded;

    
    public Note(Collection col, Long id) {
        this(col, null, id);
    }


    public Note(Collection col, JSONObject model) {
        this(col, model, null);
    }


    public Note(Collection col, JSONObject model, Long id) {
        assert !(model != null && id != null);
        mCol = col;
        if (id != null) {
            mId = id;
            load();
        } else {
            mId = Utils.timestampID(mCol.getDb(), "notes");
            mGuId = Utils.guid64();
            mModel = model;
            try {
                mMid = model.getLong("id");
                mTags = new ArrayList<>();
                mFields = new String[model.getJSONArray("flds").length()];
                Arrays.fill(mFields, "");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mFlags = 0;
            mData = "";
            mFMap = mCol.getModels().fieldMap(mModel);
            mScm = mCol.getScm();
        }
    }


    public void load() {
        Cursor cursor = null;
        try {
            cursor = mCol.getDb().getDatabase()
                    .rawQuery("SELECT guid, mid, mod, usn, tags, flds, flags, data FROM notes WHERE id = " + mId, null);
            if (!cursor.moveToFirst()) {
                throw new RuntimeException("Notes.load(): No result from query for note " + mId);
            }
            mGuId = cursor.getString(0);
            mMid = cursor.getLong(1);
            mMod = cursor.getLong(2);
            mUsn = cursor.getInt(3);
            mTags = mCol.getTags().split(cursor.getString(4));
            mFields = Utils.splitFields(cursor.getString(5));
            mFlags = cursor.getInt(6);
            mData = cursor.getString(7);
            mModel = mCol.getModels().get(mMid);
            mFMap = mCol.getModels().fieldMap(mModel);
            mScm = mCol.getScm();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    /*
     * If fields or tags have changed, write changes to disk.
     */
    public void flush() {
    	flush(null);
    }

    public void flush(Long mod) {
        flush(mod, true);
    }

    public void flush(Long mod, boolean changeUsn) {
        assert mScm == mCol.getScm();
        _preFlush();
        if (changeUsn) {
            mUsn = mCol.usn();
        }
        String sfld = Utils.stripHTMLMedia(mFields[mCol.getModels().sortIdx(mModel)]);
        String tags = stringTags();
        String fields = joinedFields();
        if (mod == null && mCol.getDb().queryScalar(
                "select 1 from notes where id = ? and tags = ? and flds = ?",
                new String[]{Long.toString(mId), tags, fields}) > 0) {
            return;
        }
        long csum = Utils.fieldChecksum(mFields[0]);
        mMod = mod != null ? mod : Utils.intNow();
        mCol.getDb().execute("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
                new Object[] { mId, mGuId, mMid, mMod, mUsn, tags, fields, sfld, csum, mFlags, mData });
        mCol.getTags().register(mTags);
        _postFlush();
    }


    public String joinedFields() {
        return Utils.joinFields(mFields);
    }


    public ArrayList<Card> cards() {
        ArrayList<Card> cards = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase()
                    .rawQuery("SELECT id FROM cards WHERE nid = " + mId + " ORDER BY ord", null);
            while (cur.moveToNext()) {
                cards.add(mCol.getCard(cur.getLong(0)));
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return cards;
    }


    public JSONObject model() {
        return mModel;
    }


    /**
     * Dict interface
     * ***********************************************************
     */

    public String[] keys() {
        return (String[])mFMap.keySet().toArray();
    }


    public String[] values() {
        return mFields;
    }


    public String[][] items() {
        // TODO: Revisit this method. The field order returned differs from Anki.
        // The items here are only used in the note editor, so it's a low priority.
        String[][] result = new String[mFMap.size()][2];
        for (String fname : mFMap.keySet()) {
            int i = mFMap.get(fname).first;
            result[i][0] = fname;
            result[i][1] = mFields[i];
        }
        return result;
    }


    private int _fieldOrd(String key) {
        return mFMap.get(key).first;
    }


    public String getItem(String key) {
        return mFields[_fieldOrd(key)];
    }


    public void setItem(String key, String value) {
        mFields[_fieldOrd(key)] = value;
    }
    
    public boolean contains(String key) {
    	return mFMap.containsKey(key);
    }


    /**
     * Tags
     * ***********************************************************
     */

    public boolean hasTag(String tag) {
        return mCol.getTags().inList(tag, mTags);
    }


    public String stringTags() {
        return mCol.getTags().join(mCol.getTags().canonify(mTags));
    }


    public void setTagsFromStr(String str) {
        mTags = mCol.getTags().split(str);
    }


    public void delTag(String tag) {
        List<String> rem = new LinkedList<>();
        for (String t : mTags) {
            if (t.equalsIgnoreCase(tag)) {
                rem.add(t);
            }
        }
        for (String r : rem) {
            mTags.remove(r);
        }
    }


    /*
     *  duplicates will be stripped on save
     */
    public void addTag(String tag) {
        mTags.add(tag);
    }


    /**
     * Unique/duplicate check
     * ***********************************************************
     */

    /**
     * 
     * @return 1 if first is empty; 2 if first is a duplicate, null otherwise.
     */
    public Integer dupeOrEmpty() {
        String val = mFields[0];
        if (val.trim().length() == 0) {
            return 1;
        }
        long csum = Utils.fieldChecksum(val);
        // find any matching csums and compare
        for (String flds : mCol.getDb().queryColumn(
                String.class,
                "SELECT flds FROM notes WHERE csum = " + csum + " AND id != " + (mId != 0 ? mId : 0) + " AND mid = "
                        + mMid, 0)) {
            if (Utils.stripHTMLMedia(
                    Utils.splitFields(flds)[0]).equals(Utils.stripHTMLMedia(mFields[0]))) {
                return 2;
            }
        }
        return null;
    }


    /**
     * Flushing cloze notes
     * ***********************************************************
     */

    /*
     * have we been added yet?
     */
    private void _preFlush() {
        mNewlyAdded = mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE nid = " + mId) == 0;
    }


    /*
     * generate missing cards
     */
    private void _postFlush() {
        if (!mNewlyAdded) {
            mCol.genCards(new long[] { mId });
        }
    }

    /*
     * ***********************************************************
     * The methods below are not in LibAnki.
     * ***********************************************************
     */

    public long getMid() {
        return mMid;
    }


    /**
     * @return the mId
     */
    public long getId() {
        // TODO: Conflicting method name and return value. Reconsider.
        return mId;
    }


    public Collection getCol() {
        return mCol;
    }


    public String getSFld() {
        return mCol.getDb().queryString("SELECT sfld FROM notes WHERE id = " + mId);
    }


    public String[] getFields() {
        return mFields;
    }


    public void setField(int index, String value) {
        mFields[index] = value;
    }


    public long getMod() {
        return mMod;
    }


    public Note clone() {
        try {
            return (Note)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> getTags() {
        return mTags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Note note = (Note) o;

        return mId == note.mId;
    }

    @Override
    public int hashCode() {
        return (int) (mId ^ (mId >>> 32));
    }
}
