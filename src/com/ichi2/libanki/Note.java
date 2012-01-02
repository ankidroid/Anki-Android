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
    private int mDid;
    private int mMid;
    private int mMod;
    private int mUsn;
    private boolean mNewlyAdded;
    private String[] mTags;
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
            	mDid = model.getInt("did");
            	mMid = model.getInt("id");
    			mTags = new String[]{""};
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
            cursor = mCol.getDb().getDatabase().rawQuery(
            		"SELECT guid, mid, did, mod, usn, tags, flds, flags, data FROM notes WHERE id = " + mId, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Notes.load(): No result from query.");
                return;
            }
            mGuId = cursor.getString(0);
            mMid = cursor.getInt(1);
    		mDid = cursor.getInt(2); 
    		mMod = cursor.getInt(3); 
    		mUsn = cursor.getInt(4);
            mTags = mCol.getTags().split(cursor.getString(5));
            mFields = Utils.splitFields(cursor.getString(6));
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
    	String sfld = Utils.stripHTML(mFields[mCol.getModels().sortIdx(mModel)]);
    	String tags = stringTags();
    	int csum = Utils.fieldChecksum(mFields[0]);
    	mCol.getDb().getDatabase().execSQL("INSERT OR REPLACE INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{mId, mGuId, mMid, mDid, mMod, mUsn, tags, joinedFields(), sfld, csum, mFlags, mData});
    	mCol.getTags().register(mTags);
    	_postFlush();
    }


    public String joinedFields() {
    	return Utils.joinFields(mFields);
    }


    public Card[] cards() {
    	ArrayList<Card> cards = new ArrayList<Card>();
    	Cursor cur = null;
        try {
        	cur = mCol.getDb().getDatabase().rawQuery("SELECT id FROM cards WHERE nid = " + mId + " ORDER BY ord", null);
        	while (cur.moveToNext()) {
        		cards.add(mCol.getCard(cur.getInt(0)));
        	}
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return (Card[]) cards.toArray();
    }


    public JSONObject model() {
    	return mModel;
    }

    // updatecarddids

    /**
     * Dict interface
     * ***********************************************************************************************
     */

    public HashSet<String> keys() {
    	return (HashSet<String>) mFMap.keySet();
    }


    public String[] values() {
        return mFields;
    }


    public HashMap<String, String> items() {
    	HashMap<String, String> m = new HashMap<String, String>();
		try {
			for (Object[] e : mFMap.values()) {
				m.put(((JSONObject)e[1]).getString("name"), mFields[(Integer)e[0]]);
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


    //setitem

    /**
     * Tags
     * ***********************************************************************************************
     */

    public boolean hasTag(String tag) {
    	return mCol.getTags().inList(tag, mTags);
    }


    public String stringTags() {
    	return mCol.getTags().join(mCol.getTags().canonify(mTags));
    }


    public void delTag(String tag) {
    	//TODO
//    	List<String> other = new ArrayList<String>();
//    	for (String t : mTags) {
//    		if (!t.toLowerCase().equals(tag.toLowerCase())) {
//    			other.add(t);
//    		}
//    	}
//    	mTags = (String[]) other.toArray();
    }


    public void addTag(String tag) {
    	// TODO
    	// duplicates will be stripped on save
//    	String[] oldtags = mTags;
//    	mTags = new String[oldtags.length + 1];
//    	for (int i = 0; i < mTags.length - 1; i++) {
//    		mTags[i] = oldtags[i];
//    	}
//    	mTags[mTags.length - 1] = tag;
    }


//    public void setTags(String tags) {
//    	mTags = Utils.parseTags(tags);
//    }

    /**
     * Unique/duplicate checks
     * ***********************************************************************************************
     */

    /** 1 if first is empty; 2 if first is duplicate, 0 otherwise */
    public int dupeOrEmpty() {
    	String val = mFields[0];
    	if (val.trim().length() == 0) {
    		return 1;
    	}
    	int csum = Utils.fieldChecksum(val);
    	// find any matching csums and compare
    	for (String flds : mCol.getDb().queryColumn(String.class, "SELECT flds FROM notes WHERE csum = " + csum + " AND id != " + (mId != 0 ? mId : 0) + " and mid = " + mMid, 0)) {
    		if (Utils.splitFields(flds)[0].equals(mFields[0])) {
    			return 2;
    		}
    	}
    	return 0;
    }
   
    /**
     * Unique/duplicate checks
     * ***********************************************************************************************
     */

    /** have we been added yet? */
    public void _preFlush() {
    	mNewlyAdded = mCol.getDb().queryScalar("SELECT 1 FROM cards WHERE nid = " + mId) == 0;
    }


    /** generate missing cards */
    public void _postFlush() {
    	//TODO
    }
    
    
    
    
    
    
    
    
    
    

    public int getMid() {
    	return mMid;
    }
    
    
    
    public void setId(int id) {
        mId = id;
    }


    /**
     * @return the mId
     */
    public long getId() {
        return mId;
    }


    public int getDid() {
        return mDid;
    }


    public String[] getFields() {
    	return mFields;
    }




//
//    public LinkedList<Card> getUpdatedRelatedCards() {
//        // TODO return instances of each card that is related to this fact
//        LinkedList<Card> returnList = new LinkedList<Card>();
//
//        Cursor cardsCursor = AnkiDatabaseManager.getDatabase(mDeck.getDeckPath()).getDatabase().rawQuery(
//                "SELECT id, factId FROM cards " + "WHERE factId = " + mId, null);
//
//        while (cardsCursor.moveToNext()) {
//            Card newCard = new Card(mDeck);
//            newCard.fromDB(cardsCursor.getLong(0));
//            newCard.loadTags();
//            HashMap<String, String> newQA = CardModel.formatQA(this, newCard.getCardModel(), newCard.splitTags());
//            newCard.setQuestion(newQA.get("question"));
//            newCard.setAnswer(newQA.get("answer"));
//
//            returnList.add(newCard);
//        }
//        if (cardsCursor != null) {
//            cardsCursor.close();
//        }
//
//        return returnList;
//    }
//
//
//    public void setModified() {
//        setModified(false, null, true);
//    }
//    public void setModified(boolean textChanged) {
//        setModified(textChanged, null, true);
//    }
//    public void setModified(boolean textChanged, Deck deck) {
//        setModified(textChanged, deck, true);
//    }
//    public void setModified(boolean textChanged, Deck deck, boolean media) {
//        mModified = Utils.now();
//        if (textChanged) {
//            assert (deck != null);
//            mCache = "";
//            StringBuilder str = new StringBuilder(1024);
//            for (Field f : getFields()) {
//                str.append(f.getValue()).append(" ");
//            }
//            mCache = str.toString();
//            mCache.substring(0, mCache.length() - 1);
//            mCache = Utils.stripHTMLMedia(mCache);
//            Log.d(AnkiDroidApp.TAG, "cache = " + mCache);
//            for (Card card : getUpdatedRelatedCards()) {
//                card.setModified();
//                card.toDB();
//                // card.rebuildQA(deck);
//            }
//        }
//    }
}
