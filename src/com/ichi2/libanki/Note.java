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

import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import com.ichi2.anki.AnkiDroidApp;

public class Note {

	private Collection mCol;

	private long mId;
	private String mGuId;
	private JSONObject mModel;
	private long mDid;
	private long mMid;
	private int mMod;
	private int mUsn;
	private boolean mNewlyAdded;
	private ArrayList<String> mTags;
	private String[] mFields;
	private String mData = "";
	private int mFlags;

	private TreeMap<String, Object[]> mFMap;
	private int mScm;

	public Note(Collection col, long id) {
		this(col, null, id);
	}

	public Note(Collection col, JSONObject model) {
		this(col, model, 0);
	}

	public Note(Collection col, JSONObject model, long id) {
		mCol = col;
		if (id != 0) {
			mId = id;
			load();
		} else {
			mId = Utils.timestampID(mCol.getDb(), "notes");
			mGuId = Utils.guid64();
			mModel = model;
			try {
				mDid = model.getLong("did");
				mMid = model.getLong("id");
				mTags = new ArrayList<String>();
				mFields = new String[model.getJSONArray("flds").length()];
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			for (int i = 0; i < mFields.length; i++) {
				mFields[i] = "";
			}
			mData = "";
			mFMap = mCol.getModels().fieldMap(mModel);
			mScm = mCol.getScm();
		}
	}

	private void load() {
		Cursor cursor = null;
		try {
			cursor = mCol
					.getDb()
					.getDatabase()
					.rawQuery(
							"SELECT guid, mid, did, mod, usn, tags, flds, flags, data FROM notes WHERE id = "
									+ mId, null);
			if (!cursor.moveToFirst()) {
				Log.w(AnkiDroidApp.TAG, "Notes.load(): No result from query.");
				return;
			}
			mGuId = cursor.getString(0);
			mMid = cursor.getLong(1);
			mDid = cursor.getLong(2);
			mMod = cursor.getInt(3);
			mUsn = cursor.getInt(4);
			mFields = Utils.splitFields(cursor.getString(6));
			mTags = mCol.getTags().split(cursor.getString(5));
			mData = cursor.getString(7);
			mScm = mCol.getScm();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		mModel = mCol.getModels().get(mMid);
		mFMap = mCol.getModels().fieldMap(mModel);
	}

	public void flush() {
		flush(0);
	}

	public void flush(int mod) {
		_preFlush();
		mMod = mod != 0 ? mod : Utils.intNow();
		mUsn = mCol.getUsn();
		String sfld = Utils
				.stripHTML(mFields[mCol.getModels().sortIdx(mModel)]);
		String tags = stringTags();
		long csum = Utils.fieldChecksum(mFields[0]);
		mCol.getDb()
				.getDatabase()
				.execSQL(
						"INSERT OR REPLACE INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
						new Object[] { mId, mGuId, mMid, mDid, mMod, mUsn,
								tags, joinedFields(), sfld, csum, mFlags, mData });
		mCol.getTags().register(mTags);
		_postFlush();
	}

	public String joinedFields() {
		return Utils.joinFields(mFields);
	}

	public ArrayList<Card> cards() {
		ArrayList<Card> cards = new ArrayList<Card>();
		Cursor cur = null;
		try {
			cur = mCol
					.getDb()
					.getDatabase()
					.rawQuery(
							"SELECT id FROM cards WHERE nid = " + mId
									+ " ORDER BY ord", null);
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

	public void updateCardDids() {
		for (Card c : cards()) {
			if (c.getDid() != mDid && c.template().has("did")) {
				c.setDid(mDid);
				c.flush();
			}
		}
	}

	/**
	 * Dict interface
	 * ***********************************************************
	 * ************************************
	 */

	public HashSet<String> keys() {
		HashSet<String> keys = new HashSet<String>();
		for (String s : mFMap.keySet()) {
			keys.add(s);
		}
		return keys;
	}

	public String[] values() {
		return mFields;
	}

	public HashMap<String, String> items() {
		HashMap<String, String> m = new HashMap<String, String>();
		try {
			for (Object[] e : mFMap.values()) {
				m.put(((JSONObject) e[1]).getString("name"),
						mFields[(Integer) e[0]]);
			}
		} catch (JSONException e1) {
			throw new RuntimeException(e1);
		}
		return m;
	}

	public int _fieldOrd(String key) {
		return (Integer) mFMap.get(key)[0];
	}

	public String _getitem__(String key) {
		return mFields[_fieldOrd(key)];
	}

	public void __setitem__(String key, String value) {
		mFields[_fieldOrd(key)] = value;
	}

	/**
	 * Tags
	 * *********************************************************************
	 * **************************
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
		for (int i = 0; i < mTags.size(); i++) {
			if (mTags.get(i).equalsIgnoreCase(tag)) {
				mTags.remove(i);
			}
		}
	}

	public void addTag(String tag) {
		mTags.add(tag);
	}

	/** LIBANKI: not in libanki */
	public ArrayList<String> getTags() {
		return mTags;
	}

	/** LIBANKI: not in libanki */
	public void clearTags() {
		mTags.clear();
	}

	/**
	 * Unique/duplicate checks
	 * **************************************************
	 * *********************************************
	 */

	/** 1 if first is empty; 2 if first is duplicate, 0 otherwise */
	public int dupeOrEmpty() {
		return dupeOrEmpty(mFields[0]);
	}
	public int dupeOrEmpty(String val) {
		if (val.trim().length() == 0) {
			return 1;
		}
		long csum = Utils.fieldChecksum(val);
		// find any matching csums and compare
		for (String flds : mCol.getDb().queryColumn(
				String.class,
				"SELECT flds FROM notes WHERE csum = " + csum + " AND id != "
						+ (mId != 0 ? mId : 0) + " AND mid = " + mMid, 0)) {
			if (Utils.splitFields(flds)[0].equals(val)) {
				return 2;
			}
		}
		return 0;
	}

	/**
	 * Flushing cloze notes
	 * **************************************************
	 * *********************************************
	 */

	public void _preFlush() {
		// have we been added yet?
		mNewlyAdded = mCol.getDb().queryScalar(
				"SELECT 1 FROM cards WHERE nid = " + mId, false) == 0;
	}

	public void _postFlush() {
		// generate missing cards
		if (!mNewlyAdded) {
			// TODO
		}
	}

	public long getMid() {
		return mMid;
	}

	public void setId(long id) {
		mId = id;
	}

	/**
	 * @return the mId
	 */
	public long getId() {
		return mId;
	}

	public long getDid() {
		return mDid;
	}

	public void setDid(long did) {
		mDid = did;
	}

	public Collection getCol() {
		return mCol;
	}

}
