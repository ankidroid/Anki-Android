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

import com.ichi2.utils.JSONObject;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;


@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.MethodNamingConventions"})
public class Note implements Cloneable {

    private final Collection mCol;

    private final long mId;
    private String mGuId;
    private Model mModel;
    private long mMid;
    private ArrayList<String> mTags;
    private String[] mFields;
    private int mFlags;
    private String mData;
    private Map<String, Pair<Integer, JSONObject>> mFMap;
    private long mScm;
    private int mUsn;
    private long mMod;
    private boolean mNewlyAdded;

    
    public Note(@NonNull Collection col, @NonNull Long id) {
        mCol = col;
        mId = id;
        load();
    }


    public Note(@NonNull Collection col, @NonNull Model model) {
        mCol = col;
        mId = mCol.getTime().timestampID(mCol.getDb(), "notes");
        mGuId = Utils.guid64();
        mModel = model;
        mMid = model.getLong("id");
        mTags = new ArrayList<>();
        mFields = new String[model.getJSONArray("flds").length()];
        Arrays.fill(mFields, "");
        mFlags = 0;
        mData = "";
        mFMap = Models.fieldMap(mModel);
        mScm = mCol.getScm();
    }


    public void load() {
        Timber.d("load()");
        try (Cursor cursor = mCol.getDb()
                .query("SELECT guid, mid, mod, usn, tags, flds, flags, data FROM notes WHERE id = ?", mId)) {
            if (!cursor.moveToFirst()) {
                throw new WrongId(mId, "note");
            }
            mGuId = cursor.getString(0);
            mMid = cursor.getLong(1);
            mMod = cursor.getLong(2);
            mUsn = cursor.getInt(3);
            mTags = new ArrayList<>(mCol.getTags().split(cursor.getString(4)));
            mFields = Utils.splitFields(cursor.getString(5));
            mFlags = cursor.getInt(6);
            mData = cursor.getString(7);
            mModel = mCol.getModels().get(mMid);
            mFMap = Models.fieldMap(mModel);
            mScm = mCol.getScm();
        }
    }

    public void reloadModel() {
        mModel = mCol.getModels().get(mMid);
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
        Pair<String, Long> csumAndStrippedFieldField = Utils.sfieldAndCsum(mFields, getCol().getModels().sortIdx(mModel));
        String sfld = csumAndStrippedFieldField.first;
        String tags = stringTags();
        String fields = joinedFields();
        if (mod == null && mCol.getDb().queryScalar(
                "select 1 from notes where id = ? and tags = ? and flds = ?",
                Long.toString(mId), tags, fields) > 0) {
            return;
        }
        long csum = csumAndStrippedFieldField.second;
        mMod = mod != null ? mod : mCol.getTime().intTime();
        mCol.getDb().execute("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
                mId, mGuId, mMid, mMod, mUsn, tags, fields, sfld, csum, mFlags, mData);
        mCol.getTags().register(mTags);
        _postFlush();
    }


    public String joinedFields() {
        return Utils.joinFields(mFields);
    }


    public int numberOfCards() {
        return (int) mCol.getDb().queryLongScalar("SELECT count() FROM cards WHERE nid = ?", mId);
    }

    public List<Long> cids() {
        return mCol.getDb().queryLongList("SELECT id FROM cards WHERE nid = ? ORDER BY ord", mId);
    }

    public ArrayList<Card> cards() {
        ArrayList<Card> cards = new ArrayList<>(cids().size());
        for (long cid : cids()) {
            // each getCard access database. This is inneficient.
            // Seems impossible to solve without creating a constructor of a list of card.
            // Not a big trouble since most note have a small number of cards.
            cards.add(mCol.getCard(cid));
        }
        return cards;
    }

    /** The first card, assuming it exists.*/
    public Card firstCard() {
        return mCol.getCard(mCol.getDb().queryLongScalar("SELECT id FROM cards WHERE nid = ? ORDER BY ord LIMIT 1", mId));
    }


    public Model model() {
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
        Pair<Integer, JSONObject> fieldPair = mFMap.get(key);
        if (fieldPair == null) {
            throw new IllegalArgumentException(String.format("No field named '%s' found", key));
        }
        return fieldPair.first;
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
        mTags = new ArrayList<>(mCol.getTags().split(str));
    }


    public void delTag(String tag) {
        List<String> rem = new ArrayList<>(mTags.size());
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

    public void addTags(AbstractSet<String> tags) {
        mTags.addAll(tags);
    }


    /**
     * Unique/duplicate check
     * ***********************************************************
     */

    public enum DupeOrEmpty {
        CORRECT,
        EMPTY,
        DUPE,
    }
    /**
     * 
     * @return whether it has no content, dupe first field, or nothing remarkable.
     */
    public DupeOrEmpty dupeOrEmpty() {
        String val = mFields[0];
        if (val.trim().length() == 0) {
            return DupeOrEmpty.EMPTY;
        }
        Pair<String, Long> csumAndStrippedFieldField = Utils.sfieldAndCsum(mFields, 0);
        long csum = csumAndStrippedFieldField.second;
        // find any matching csums and compare
        String strippedFirstField = csumAndStrippedFieldField.first;
        for (String flds : mCol.getDb().queryStringList(
                "SELECT flds FROM notes WHERE csum = ? AND id != ? AND mid = ?",
                csum, (mId), mMid)) {
            if (Utils.stripHTMLMedia(
                    Utils.splitFields(flds)[0]).equals(strippedFirstField)) {
                return DupeOrEmpty.DUPE;
            }
        }
        return DupeOrEmpty.CORRECT;
    }


    /**
     * Flushing cloze notes
     * ***********************************************************
     */

    /*
     * have we been added yet?
     */
    private void _preFlush() {
        mNewlyAdded = mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE nid = ?", mId) == 0;
    }


    /*
     * generate missing cards
     */
    private void _postFlush() {
        if (!mNewlyAdded) {
            mCol.genCards(mId, mModel);
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


    @VisibleForTesting
    public String getGuId() {
        return mGuId;
    }


    public Collection getCol() {
        return mCol;
    }


    public String getSFld() {
        return mCol.getDb().queryString("SELECT sfld FROM notes WHERE id = ?", mId);
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

    public int getUsn() {
        return mUsn;
    }

    public Note clone() {
        try {
            return (Note)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }


    public ArrayList<String> getTags() {
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


    public static class ClozeUtils {
        private static final Pattern mClozeRegexPattern = Pattern.compile("\\{\\{c(\\d+)::");

        /**
         * Calculate the next number that should be used if inserting a new cloze deletion.
         * Per the manual the next number should be greater than any existing cloze deletion
         * even if there are gaps in the sequence, and regardless of existing cloze ordering
         *
         * @param fieldValues Iterable of field values that may contain existing cloze deletions
         * @return the next index that a cloze should be inserted at
         */
        public static int getNextClozeIndex(Iterable<String> fieldValues) {

            int highestClozeId = 0;
            // Begin looping through the fields
            for (String fieldLiteral : fieldValues) {
                // Begin searching in the current field for cloze references
                Matcher matcher = mClozeRegexPattern.matcher(fieldLiteral);
                while (matcher.find()) {
                    int detectedClozeId = Integer.parseInt(matcher.group(1));
                    if (detectedClozeId > highestClozeId) {
                        highestClozeId = detectedClozeId;
                    }
                }
            }
            return highestClozeId + 1;
        }
    }
}
