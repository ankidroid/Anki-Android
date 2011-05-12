/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONException;

import com.ichi2.anki.AnkiDroidApp;

/**
 * Anki fact.
 * A fact is a single piece of information, made up of a number of fields.
 * See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Facts
 * 
 * Cache: a HTML-stripped amalgam of the field contents, so we can perform
 * searches of marked up text in a reasonable time.
 */
public class Fact {

    private int mId;
    private int mMId;
    private int mGId;
    private int mCrt;
    private int mMod;
    private String[] mTags;
    private String[] mFields;
    private String mData = "";
    
    private Model mModel;
    private Deck mDeck;
    private TreeMap<String, Integer> mFMap;


    public Fact(Deck deck, Model model) {
    	this(deck, model, 0);
    }

    public Fact(Deck deck, int id) {
    	this(deck, null, id);
    }

    // Generate fact object from its ID
    public Fact(Deck deck, Model model, int id) {
        mDeck = deck;
        mId = id;
        if (mId != 0) {
        	fromDb(id);
        } else if (model != null) {
        	mModel = model;
        	try {
				mGId = mDeck.defaultGroup(model.getConf().getInt("gid"));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			mMId = model.getId();
			mCrt = Utils.intNow();
			mMod = mCrt;
			mTags = new String[]{""};
	        mFMap = mModel.fieldMap();
			mFields = new String[mFMap.size()];
			for (int i = 0; i < mFields.length; i++) {
				mFields[i] = "";
			}
			mData = "";
        }
    }


    private boolean fromDb(int id) {
       Cursor cursor = null;
        try {
            cursor = mDeck.getDB().getDatabase().rawQuery(
            		"SELECT mid, gid, crt, mod, tags, flds, data FROM facts WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Fact.java (constructor): No result from query.");
                return false;
            }
            mMId = cursor.getInt(0);
            mGId = cursor.getInt(1);
            mCrt = cursor.getInt(2);
            mMod = cursor.getInt(3);
            mTags = Utils.parseTags(cursor.getString(4));
            mFields = Utils.splitFields(cursor.getString(5));
            mData = cursor.getString(6);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        mModel = mDeck.getModel(mMId);
        mFMap = mModel.fieldMap();
        return true;
    }


    public void flush() {
    	mMod = Utils.intNow();
    	// facts table
    	StringBuilder sb = new StringBuilder();
    	sb.append("INSERT OR REPLACE INTO facts VALUES (");
    	sb.append(mId).append(", ");
    	sb.append(mMId).append(", ");
    	sb.append(mGId).append(", ");
    	sb.append(mCrt).append(", ");
    	sb.append(mMod).append(", ");
    	sb.append("\"").append(stringTags()).append("\", ");
    	sb.append("\"").append(joinedFields()).append("\", ");
    	sb.append("\"").append(mFields[mModel.sortIdx()]).append("\", ");
    	sb.append("\"").append(mData).append("\")");
    	Cursor cur = null;
        try {
        	cur = mDeck.getDB().getDatabase().rawQuery(sb.toString(), null);
        	if (cur.moveToFirst()) {
        		mId = cur.getInt(0);    		
        	}
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
    	// updateFieldChecksums();
    	// mDeck.registerTags(mTags);
    }


    public String joinedFields() {
    	return Utils.joinFields(mFields);
    }


    public Card[] getCards() {
    	ArrayList<Card> cards = new ArrayList<Card>();
    	Cursor cur = null;
        try {
        	cur = mDeck.getDB().getDatabase().rawQuery("SELECT id FROM cards WHERE fid = " + mId, null);
        	while (cur.moveToNext()) {
        		cards.add(mDeck.getCard(cur.getInt(0)));
        	}
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return (Card[]) cards.toArray();
    }


    public Model getModel() {
    	return mModel;
    }

    /**
     * Dict interface
     * ***********************************************************************************************
     */


    /**
     * @return the fields
     */
    public String[] getFields() {
        return mFields;
    }


    /**
     * set the value of a field
     */
    public void setFields(String name, String value) {
        mFields[_fieldOrd(name)] = value;
    }


    public int _fieldOrd(String key) {
        return mFMap.get(key);
    }


    public String fieldName(int ord) {
    	for (Entry<String, Integer> e : mFMap.entrySet()) {
    		if (e.getValue() == ord) {
    			return e.getKey();
    		}
    	}
    	return null;
    }


    /**
     * Tags
     * ***********************************************************************************************
     */

    public boolean hasTag(String tag) {
    	return Utils.hasTag(tag, Arrays.asList(mTags));
    }


    public String stringTags() {
    	return Utils.canonifyTags(mTags);
    }


    public void delTag(String tag) {
    	List<String> other = new ArrayList<String>();
    	for (String t : mTags) {
    		if (!t.toLowerCase().equals(tag.toLowerCase())) {
    			other.add(t);
    		}
    	}
    	mTags = (String[]) other.toArray();
    }


    public void addTag(String tag) {
    	// duplicates will be stripped on save
    	String[] oldtags = mTags;
    	mTags = new String[oldtags.length + 1];
    	for (int i = 0; i < mTags.length - 1; i++) {
    		mTags[i] = oldtags[i];
    	}
    	mTags[mTags.length - 1] = tag;
    }


    public void setTags(String tags) {
    	mTags = Utils.parseTags(tags);
    }

    /**
     * Unique/duplicate checks
     * ***********************************************************************************************
     */

  
    
    
    public void setId(int id) {
        mId = id;
    }


    /**
     * @return the mId
     */
    public int getId() {
        return mId;
    }


    /**
     * @return the mModelId
     */
    public int getMId() {
        return mMId;
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
