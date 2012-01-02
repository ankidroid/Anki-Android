/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
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
import android.database.SQLException;
import android.os.DropBoxManager.Entry;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.async.DeckTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Decks {
	
//    private TreeMap<Integer, Model> mModelCache;
//    private TreeMap<Integer, JSONObject> mGroupConfCache;

	private static final String defaultDeck = "{" +  
    		"'newToday': [0, 0], " + // currentDay, count
    		"'revToday': [0, 0], " +
    		"'lrnToday': [0, 0], " +
    		"'timeToday': [0, 0], " + // time in ms
    		"'conf': 1, " +
    		"'usn': 0, " +
    		"'desc': \"\", }";


	
    // default group conf
    private static final String defaultConf = "{" +
    		"'new': {" +
    	    	"'delays': [1, 10], " +
    	    	"'ints': [1, 4, 7], " + // 7 is not currently used
    	    	"'initialFactor': 2500, " +
    	    	"'separate': True, " +
    	    	"'order': " + Sched.NEW_CARDS_DUE + ", " +
    			"'perDay': 20, }, " +
    	    "'lapse': {" +
    	    	"'delays': [1, 10], " +
    	    	"'mult': 0, " +
    	    	"'minInt': 1, " +
    	    	"'leechFails': 8, " +
    	    	"'leechAction': 0, }, " + // type 0=suspend, 1=tagonly
    	    "'cram': { " +
    	    	"'delays': [1, 5, 10], " +
    	    	"'resched': True, " +
    	    	"'reset': True, " +
    	    	"'mult': 0, " +
    	    	"'minInt': 1, }, " +
    	    "'rev': { " +
    	    	"'perDay': 100" +
    	    	"'ease4': 1.3, " +
    	    	"'fuzz': 0.05, " +
    	    	"'minSpace': 1, " +
    	    	"'fi': [10, 10], " +
    	    	"'order': " + Sched.REV_CARDS_RANDOM + "}, " +
	    	"'maxTaken': 60, " +
    		"'timer': 0, " +
    		"'autoplay': True, " +
    		"'mod': 0, " +
    		"'usn': 0, }";


	private Collection mCol;
	private HashMap<Long, JSONObject> mDecks;
	private HashMap<String, Long> mDeckIds;
	private HashMap<Long, JSONObject> mDconf;
	private boolean mChanged;

    /**
     * Registry save/load
     * ***********************************************************************************************
     */

	public Decks(Collection col) {
		mCol = col;
	}


	public void load(String decks, String dconf) {
    	mDecks = new HashMap<Long, JSONObject>();
    	mDeckIds = new HashMap<String, Long>();
    	mDconf = new HashMap<Long, JSONObject>();
        try {
        	JSONObject decksarray = new JSONObject(decks);
        	JSONArray ids = decksarray.names();
        	for (int i = 0; i < ids.length(); i++) {
        		String id = ids.getString(i);
        		JSONObject o = decksarray.getJSONObject(id);
        		long longId = Long.parseLong(id);
        		mDecks.put(longId, o);
        		mDeckIds.put(o.getString("name"), longId);
        	}
        	JSONObject confarray = new JSONObject(dconf);
        	ids = confarray.names();
        	for (int i = 0; i < ids.length(); i++) {
        		String id = ids.getString(i);
        		mDconf.put(Long.parseLong(id), confarray.getJSONObject(id));
        	}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
        mChanged = false;
	}


	public void save() {
		save(null);
	}
	public void save(JSONObject g) {
		// TODO
	}


	public void flush() {
        ContentValues values = new ContentValues();
        values.put("decks", mDecks.toString());
        values.put("dconf", mDconf.toString());
		mCol.getDb().getDatabase().update("col", values, null, null);
		mChanged = false;
	}

    /**
     * Deck save/load
     * ***********************************************************************************************
     */

	public long id(String name) {
		return id(name, true);
	}
    /** Add a deck with NAME. Reuse deck if already exists. Return id as int. */
	public long id(String name, boolean create) {
		name = name.replaceAll("\'", "").replace("\"", "");
		for (Map.Entry<Long, JSONObject> g : mDecks.entrySet()) {
			try {
				if (g.getValue().getString("name").equalsIgnoreCase(name)) {
					return g.getKey();
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		if (!create) {
			return 0;
		}
		if (name.split("::").length > 1) {
			// not top level; ensure all parents exist
			_ensureParents(name);
		}
		JSONObject g;
		long id;
		try {
			g = new JSONObject(defaultDeck);
			g.put("name", name);
			id = Utils.intNow();
			while (mDecks.containsKey(id)) {
				id = Utils.intNow();
			}
			g.put("id", id);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		mDecks.put(id, g);
		save(g);
		maybeAddToActive();
		return id;
	}


	public void rem(long did) {
		rem(did, false);
	}
	/** Remove the deck. If cardsToo, delete any cards inside. */
	public void rem(long did, boolean cardsToo) {
		if (did == 1) {
			return;
		}
		if (!mDecks.containsKey(did)) {
			return;
		}
		// delete children first
		for (long chDid : mDecks.keySet()) {
			rem(chDid, cardsToo);
		}
		// delete cards too?
		if (cardsToo) {
			mCol.remCards(cids(did));
		}
		// delete the deck and add a grave
		mDecks.remove(did);
		mCol._logRem(new long[]{did}, Sched.REM_DECK);
		// ensure we have an active deck
		if (active().contains(did)) {
			select((long) (mDecks.keySet().iterator().next()));
		}
		save();
	}

	
    /** An unsorted list of all deck names. */
	public String[] allNames() {
		ArrayList<String> list = new ArrayList<String>();
		for (JSONObject o : mDecks.values()) {
			try {
				list.add(o.getString("name"));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		return (String[]) list.toArray();
	}

	
    /**
     * A list of all decks.
     */
	public ArrayList<JSONObject> all() {
		ArrayList<JSONObject> decks = new ArrayList<JSONObject>();
		Iterator<JSONObject> it = mDecks.values().iterator();
		while(it.hasNext()) {
			decks.add(it.next());
		}
		return decks;
	}

	
	public int count() {
		return mDecks.size();
	}


	public JSONObject get(long did) {
		return get(did, true);
	}
	public JSONObject get(long did, boolean defaultvalue) {
		if (mDecks.containsKey(did)) {
			return mDecks.get(did);
		} else if (defaultvalue) {
			return mDecks.get(1);
		} else {
			return null;
		}
	}


	// TODO: update
	// TODO: rename


	private void _ensureParents(String name) {
		String[] path = name.split("::");
		String s = "";
		for (int i = 0; i < path.length - 1; i++) {
			if (i == 0) {
				s = path[0];
			} else {
				s = s + "::" + path[i];
			}
			id(s);
		}
	}

    /**
     * Deck configurations
     * ***********************************************************************************************
     */

    /** A list of all deck config. */
	public ArrayList<JSONObject> allConf() {
		return (ArrayList<JSONObject>) mDconf.values();
	}


	public JSONObject confForDid(long did) {
		try {
			return getConf(get(did).getLong("conf"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	public JSONObject getConf(long confId) {
		return mDconf.get(confId);
	}

	//update conf
	//confid
	//remConf
	//setConf
	//didsforConf
	//restoretodefault

    /**
     * Deck utils
     * ***********************************************************************************************
     */

	public String name(long did) {
		try {
			return get(did).getString("name");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	// setdeck


	private void maybeAddToActive() {
		// reselect current deck, or default if current has disappeared
		JSONObject c = current();
		try {
			select(c.getLong("id"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	//sendhome


	private long[] cids(long did) {
		ArrayList<Long> cids = mCol.getDb().queryColumn(long.class, "SELECT id FROM cards WHERE did = " + did, 0);
		long[] result = new long[cids.size()];
		for (int i = 0; i < cids.size(); i++) {
			result[i] = cids.get(i);
		}
		return result;
	}

    /**
     * Deck selection
     * ***********************************************************************************************
     */

	/* The currrently active dids.*/
	public LinkedList<Long> active() {
		try {
			String actv = mCol.getConf().getString("activeDecks");
			JSONArray ja = new JSONArray(actv);
			LinkedList<Long> result = new LinkedList<Long>();
			for (int i = 0; i < ja.length(); i++) {
				result.add(ja.getLong(i));
			}
			return result;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	/* The currently selected did. */
	public long selected() {
		try {
			return mCol.getConf().getLong("curDeck");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	public JSONObject current() {
		return get(selected());
	}


	/* Select a new branch. */
	public void select(long did) {
		try {
			String name = mDecks.get(did).getString("name");
			// current deck
			mCol.getConf().put("curDeck", Long.toString(did));
			// and active decks (current + all children)
			TreeMap<String, Long> actv = children(did);
			actv.put(name, did);
			mCol.getConf().put("activeDecks", actv.values().toString());
			mChanged = true;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	/* all children of did as (name, id) */
	public TreeMap<String, Long> children(long did) {
		String name;
		try {
			name = get(did).getString("name");
			TreeMap<String, Long> list = new TreeMap<String, Long>();
			for (JSONObject g : all()) {
				if (g.getString("name").startsWith(name + "::")) {
					list.put(g.getString("name"), g.getLong("id"));
				}
			}
			return list;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}


	/* all parents of did */
	public ArrayList<JSONObject> parents(long did) {
		ArrayList<JSONObject> list = new ArrayList<JSONObject>();
		try {			
			String[] path = get(did).getString("name").split("::");
			String deckpath = null;
			for (int i = 0; i < path.length - 1; i++) {
				if (i == 0) {
					deckpath = path[0];
				} else {
					deckpath = deckpath + "::" + path[i];
				}
				list.add(get(mDeckIds.get(deckpath)));
			}
			return list;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

    /**
     * Sync handling
     * ***********************************************************************************************
     */

	// TODO: beforeUpload
    
    



























//
//
//    public static synchronized Decks openDeck(String path) throws SQLException {
//        return openDeck(path, true);
//    }
//    public static synchronized Decks openDeck(String path, boolean rebuild) throws SQLException {
//        Decks deck = new Decks();
//        Log.i(AnkiDroidApp.TAG, "openDeck - Opening database " + path);
//        if (!deck.loadFromDB(AnkiDatabaseManager.getDatabase(path))) {
//        	return null;
//        }
//        Log.i(AnkiDroidApp.TAG, String.format(Utils.ENGLISH_LOCALE, "openDeck - modified: %d currentTime: %d", deck.mMod, Utils.intNow()));
//
//        deck.mDeckPath = path;
//        deck.mDeckName = (new File(path)).getName().replace(".anki", "");
//        if (deck.mCrt == 0) {
//        	// TODO
//        }
//
//        // Initialize Caches
//        deck.mModelCache = new TreeMap<Integer, Model>();
//        deck. mGroupConfCache = new TreeMap<Integer, JSONObject>();
//
//        // TODO: undo - load and clear
//
//        if (rebuild) {
//            deck.mSched.reset();
//        } else {
//        	deck.mSched._resetCounts();
//        }
//
////        deck.initDeckvarsCache();
////
////        if (deck.mVersion < Upgrade.DECK_VERSION) {
////            deck.createMetadata();
////        }
////
//////        deck.mNeedUnpack = false;
//////        if (Math.abs(deck.getUtcOffset() - 1.0) < 1e-9 || Math.abs(deck.getUtcOffset() - 2.0) < 1e-9) {
////        if (Math.abs(deck.getUtcOffset() - 2.0) < 1e-9) {
////            // do the rest later
//////            deck.mNeedUnpack = (Math.abs(deck.getUtcOffset() - 1.0) < 1e-9);
////            // make sure we do this before initVars
////            deck.setUtcOffset();
////            deck.mCreated = Utils.now();
////        }
////
////        deck.initVars();
////        // deck.initTagTables();
////        deck.updateDynamicIndices();
////        // Upgrade to latest version
////        Upgrade.upgradeDeck(deck);
////
////        if (!rebuild) {
////            // Minimal startup for deckpicker: only counts are needed
////            deck.rebuildCounts();
////            return deck;
////        }
////
////        ArrayList<Long> ids = new ArrayList<Long>();
////
////        double oldMod = deck.getModified();
////        // Unsuspend buried/rev early
////        deck.getDB().getDatabase().execSQL("UPDATE cards SET queue = type WHERE queue BETWEEN -3 AND -2");
////        deck.commitToDB();
////        // Rebuild queue
////        deck.reset();
////        // Make sure we haven't accidentally bumped the modification time
////        double dbMod = 0.0;
////        Cursor cur = null;
////        try {
////            cur = deck.getDB().getDatabase().rawQuery("SELECT modified FROM decks", null);
////            if (cur.moveToNext()) {
////                dbMod = cur.getDouble(0);
////            }
////        } finally {
////            if (cur != null && !cur.isClosed()) {
////                cur.close();
////            }
////        }
////        assert Math.abs(dbMod - oldMod) < 1.0e-9;
////        assert deck.mModified == oldMod;
////
//        // Initialize Undo
//        return deck;
//    }
//
//
//
//    /**
//     * Memoizes; call .reset() to reset cache.
//     */
//    public Model getModel(int mid) {
//    	return getModel(mid, true);
//    }
//    public Model getModel(int mid, boolean cache) {
//    	if (cache && mModelCache.containsKey(mid)) {
//    		return mModelCache.get(mid);
//    	}
//    	Model m = new Model(this, mid);
//    	if (cache) {
//    		mModelCache.put(mid, m);
//    	}
//    	return m;
//    }
//
//
//
//    /**
//     * Tags
//     * ***********************************************************************************************
//     */
//
//    public String[] tagList() {
//    	ArrayList<String> tags = getDB().queryColumn(String.class, "SELECT name FROM tags ORDER BY name", 0);
//    	return (String[]) tags.toArray(new String[tags.size()]);
//    }
//
//    /**
//     * Groups
//     * ***********************************************************************************************
//     */
//
//    /**
//     * A list of all group names.
//     */
//    public String[] groups() {
//    	ArrayList<String> names = new ArrayList<String>();
//    	Cursor cur = null;
//    	try {
//			cur = getDB().getDatabase().rawQuery("SELECT name FROM groups ORDER BY name", null);
//            while (cur.moveToNext()) {
//            	names.add(cur.getString(0));
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//        return (String[]) names.toArray();
//    }
//
//
//    public String groupName(int id) {
//    	Cursor cur = null;
//    	try {
//            cur = getDB().getDatabase().rawQuery("SELECT name FROM groups WHERE id = " + id, null);
//            while (cur.moveToNext()) {
//            	return cur.getString(0);
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//        return null;
//    }
//
//    /**
//     * @return the id for NAME, creating if necessary.
//     */
//    public int groupId(String name) {
//    	Cursor cur = null;
//    	try {
//            cur = getDB().getDatabase().rawQuery("SELECT id FROM groups WHERE name = \'" + name + "\'", null);
//            if (cur.moveToNext()) {
//            	return cur.getInt(0);
//            } else {
//            	cur = getDB().getDatabase().rawQuery("INSERT INTO groups VALUES (" + 
//            			nextID("gid") + ", " + Utils.intNow() + ", " + name + ", 1, " +
//            			defaultGroupData, null);
//	            if (cur.moveToLast()) {
//	            	return cur.getInt(0);
//	            } else {
//	    			throw new RuntimeException();
//	            }
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//    }
//
//
//    public int defaultGroup(int id) {
//    	if (id == 1) {
//    		return 1;
//    	}
//    	Cursor cur = null;
//    	try {
//            cur = getDB().getDatabase().rawQuery("SELECT id FROM groups WHERE id = " + id, null);
//            if (cur.moveToNext()) {
//            	return cur.getInt(0);
//            } else {
//            	return 1;
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//    }
//
//
//    public void delGroup(int gid) {
////        modSchema();
//        getDB().getDatabase().execSQL("UPDATE cards SET gid = 1 WHERE gid = " + gid);
//        getDB().getDatabase().execSQL("UPDATE facts SET gid = 1 WHERE gid = " + gid);
//        getDB().getDatabase().execSQL("DELETE FROM groups WHERE gid = " + gid);
//    }
//
//
//    public void setGroup(int[] cids, int gid) {
//    	getDB().getDatabase().execSQL("UPDATE cards SET gid = " + gid + " WHERE id IN " + Utils.ids2str(cids));
//    }
//
//
//    /**
//     * Group configuration
//     * ***********************************************************************************************
//     */
//
//    /**
//     * @return [name, id].
//     */
//    public TreeMap<String, Integer> groupConfs() {
//    	TreeMap<String, Integer> confs = new TreeMap<String, Integer>();
//    	Cursor cur = null;
//    	try {
//            cur = getDB().getDatabase().rawQuery("SELECT name, id FROM gconf ORDER BY name", null);
//            while (cur.moveToNext()) {
//            	confs.put(cur.getString(0), cur.getInt(1));
//            }
//        } finally {
//            if (cur != null && !cur.isClosed()) {
//                cur.close();
//            }
//        }
//        return confs;
//    }
//
//
//    public JSONObject groupConf(int gcid) {
//    	if (!mGroupConfCache.containsKey(gcid)) {
//        	Cursor cur = null;
//        	try {
//                cur = getDB().getDatabase().rawQuery("SELECT conf FROM gconf WHERE id = " + gcid, null);
//                if (cur.moveToFirst()) {
//                	mGroupConfCache.put(gcid, new JSONObject(cur.getString(0)));
//                }
//            } catch (JSONException e) {
//    			throw new RuntimeException(e);
//    		} finally {
//                if (cur != null && !cur.isClosed()) {
//                    cur.close();
//                }
//            }
//    	}
//    	return mGroupConfCache.get(gcid);
//    }
//
//
//    /**
//     * LIBANKI: not in libanki
//     */
//    public void setGroupConf(String groupName, int confId) {
//    	getDB().getDatabase().execSQL("UPDATE groups SET gcid = " + confId + " WHERE name = \"" + groupName + "\"");
//    }
//
//    
//    /**
//     * LIBANKI: not in libanki
//     */
//    public boolean isLimitedByGroup() {
//    	try {
//			return getQconf().getJSONArray("groups").length() > 0;
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//    }
//    
//    
//    /**
//     * Tag-based selective study
//     * ***********************************************************************************************
//     */
//
//
//
//    /**
//     * Finding cards
//     * ***********************************************************************************************
//     */
//
//
//
//
//
//    
//    /**
//     * Statistic Bars
//     * LIBANKI: not in libanki
//     * ***********************************************************************************************
//     */
//
//    /**
//     * Number of mature cards.
//     */
//    private int matureCardCount(boolean limit) {
//    	String lim = "";
//    	if (limit) {
//    		lim = getSched()._groupLimit();
//    	}
//        return (int) getDB().queryScalar("SELECT count() FROM cards WHERE queue = 2 AND ivl >= " + 21 + lim);
//    }
//
//
//    /**
//     * Number of mature cards.
//     */
//    public int totalNewCardCount(boolean limit) {
//    	String lim = "";
//    	if (limit) {
//    		lim = getSched()._groupLimit();
//    	}
//        return (int) getDB().queryScalar("SELECT count() FROM cards WHERE queue = 0" + lim);
//    }
//
//
//    /**
//     * Number of suspended cards.
//     */
//    private int totalSuspendedCardCount(boolean limit) {
//    	String lim = "";
//    	if (limit) {
//    		lim = getSched()._groupLimit();
//    	}
//        return (int) getDB().queryScalar("SELECT count() FROM cards WHERE queue = -1" + lim);
//    }
//
//
//    /**
//     * Completion rates for all and mature cards
//     * @return matureRate, allRate
//     */
//    public double[] completionRates(boolean limit) {
//    	double total = cardCount(limit) - totalSuspendedCardCount(limit);
//    	if (total == 0.0) {
//    		return new double[] {0, 0};
//    	}
//    	double matureRate = (double)matureCardCount(limit) / total;
//    	double allRate = (total - totalNewCardCount(limit)) / total;
//    	return new double[] { matureRate, allRate};
//    }
//
//
//    /**
//     * Yes counts
//     * @return todayAnswers, todayNoAnswers, matureAnswers, matureNoAnswers
//     */
//    public int[] yesCounts() {
//    	int dayStart = (getSched().mDayCutoff - 86400) * 10000;
//    	int todayAnswers = (int) getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " + dayStart);
//    	int todayNoAnswers = (int) getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " + dayStart + " AND ease = 1");
//    	int matureAnswers = (int) getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21");
//    	int matureNoAnswers = (int) getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21 AND ease = 1");
//    	return new int[] { todayAnswers, todayNoAnswers, matureAnswers, matureNoAnswers };
//    }
//
//
//    /**
//     * Yes rates for today's and mature cards
//     * @return todayRate, matureRate
//     */
//    public double[] yesRates() {
//    	int[] counts = yesCounts();
//    	return new double[] { 1 - (double)counts[1]/counts[0], 1 - (double)counts[3]/counts[2] };
//    }
//
////    
/////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************
////********************************************************************************/
////
////
////
////
////    public LinkedHashMap<Long, CardModel> activeCardModels(Fact fact) {
////    	LinkedHashMap<Long, CardModel> activeCM = new LinkedHashMap<Long, CardModel>();
////        for (Map.Entry<Long, CardModel> entry : cardModels(fact).entrySet()) {
////            CardModel cardmodel = entry.getValue();
////            if (cardmodel.isActive()) {
////                // TODO: check for emptiness
////            	activeCM.put(cardmodel.getId(), cardmodel);
////            }
////        }
////        return activeCM;
////    }
////
////    public LinkedHashMap<Long, CardModel> cardModels(Fact fact) {
////    	LinkedHashMap<Long, CardModel> cardModels = new LinkedHashMap<Long, CardModel>();
////        CardModel.fromDb(this, fact.getModelId(), cardModels);
////        return cardModels;
////    }
////
////    public boolean hasKey(String key) {
////        return mDeckVars.containsKey(key);
////    }
////
////    public int getInt(String key) {
////        if (mDeckVars.containsKey(key)) {
////            try {
////                return Integer.parseInt(mDeckVars.get(key));
////            } catch (NumberFormatException e) {
////                Log.w(AnkiDroidApp.TAG, "NumberFormatException: Converting deckvar to int failed, key: \"" + key +
////                        "\", value: \"" + mDeckVars.get(key) + "\"");
////                return 0;
////            }
////        } else {
////            return 0;
////        }
////    }
////
////
////    public double getFloat(String key) {
////        if (mDeckVars.containsKey(key)) {
////            try {
////                return Double.parseDouble(mDeckVars.get(key));
////            } catch (NumberFormatException e) {
////                Log.w(AnkiDroidApp.TAG, "NumberFormatException: Converting deckvar to double failed, key: \"" + key +
////                        "\", value: \"" + mDeckVars.get(key) + "\"");
////                return 0.0;
////            }
////        } else {
////            return 0.0;
////        }
////    }
////
////
////    public boolean getBool(String key) {
////        if (mDeckVars.containsKey(key)) {
////            return mDeckVars.get(key).equals("1");
////        } else {
////            return false;
////        }
////    }
////
////
////    public String getVar(String key) {
////        return mDeckVars.get(key);
////    }
////
////
////    public void setVar(String key, String value) {
////        setVar(key, value, true);
////    }
////
////
////    public void setVar(String key, String value, boolean mod) {
////        try {
////            if (mDeckVars.containsKey(key)) {
////                getDB().getDatabase().execSQL("UPDATE deckVars SET value='" + value + "' WHERE key = '" + key + "'");
////            } else {
////                getDB().getDatabase().execSQL("INSERT INTO deckVars (key, value) VALUES ('" + key + "', '" +
////                        value + "')");
////            }
////            mDeckVars.put(key, value);
////        } catch (SQLException e) {
////            Log.e(AnkiDroidApp.TAG, "setVar: " + e.toString());
////            throw new RuntimeException(e);
////        }
////        if (mod) {
////            setModified();
////        }
////    }
////
////
////    public void setVarDefault(String key, String value) {
////        if (!mDeckVars.containsKey(key)) {
////            setVar(key, value, false);
////        }
////    }
//
//    
//    // Media
//    // *****
//
//    /**
//     * Return the media directory if exists, none if couldn't be created.
//     *
//     * @param create If true it will attempt to create the folder if it doesn't exist
//     * @param rename This is used to simulate the python with create=None that is only used when renaming the mediaDir
//     * @return The path of the media directory
//     */
//    public String mediaDir() {
//        return mediaDir(false, false);
//    }
//    public String mediaDir(boolean create) {
//        return mediaDir(create, false);
//    }
//    public String mediaDir(boolean create, boolean rename) {
//        String dir = null;
//        File mediaDir = null;
//        if (mDeckPath != null && !mDeckPath.equals("")) {
//            Log.i(AnkiDroidApp.TAG, "mediaDir - mediaPrefix = " + mMediaPrefix);
//            if (mMediaPrefix != null) {
//                dir = mMediaPrefix + "/" + mDeckName + ".media";
//            } else {
//                dir = mDeckPath.replaceAll("\\.anki$", ".media");
//            }
//            if (rename) {
//                // Don't create, but return dir
//                return dir;
//            }
//            mediaDir = new File(dir);
//            if (!mediaDir.exists() && create) {
//                try {
//                    if (!mediaDir.mkdir()) {
//                        Log.e(AnkiDroidApp.TAG, "Couldn't create media directory " + dir);
//                        return null;
//                    }
//                } catch (SecurityException e) {
//                    Log.e(AnkiDroidApp.TAG, "Security restriction: Couldn't create media directory " + dir);
//                    return null;
//                }
//            }
//        }
//
//        if (dir == null) {
//            return null;
//        } else {
//            if (!mediaDir.exists() || !mediaDir.isDirectory()) {
//                return null;
//            }
//        }
//        Log.i(AnkiDroidApp.TAG, "mediaDir - mediaDir = " + dir);
//        return dir;
//    }
//
//    public String getMediaPrefix() {
//        return mMediaPrefix;
//    }
//    public void setMediaPrefix(String mediaPrefix) {
//        mMediaPrefix = mediaPrefix;
//    }
////
////
////
////    private boolean hasLaTeX() {
////        Cursor cursor = null;
////        try {
////            cursor = getDB().getDatabase().rawQuery(
////                "SELECT Id FROM fields WHERE " +
////                "(value like '%[latex]%[/latex]%') OR " +
////                "(value like '%[$]%[/$]%') OR " +
////                "(value like '%[$$]%[/$$]%') LIMIT 1 ", null);
////            if (cursor.moveToFirst()) {
////                return true;
////            }
////        } finally {
////            if (cursor != null) {
////                cursor.close();
////            }
////        }
////        return false;
////    }
////
////
////
////    /*
////     * Add stripped HTML cache for sorting/searching. Currently needed as part of the upgradeDeck, the cache is not
////     * really used, yet.
////     */
////    private void updateFieldCache(long[] fids) {
////        HashMap<Long, String> r = new HashMap<Long, String>();
////        Cursor cur = null;
////
////        Log.i(AnkiDroidApp.TAG, "updatefieldCache fids: " + Utils.ids2str(fids));
////        try {
////            cur = getDB().getDatabase().rawQuery(
////                    "SELECT factId, group_concat(value, ' ') FROM fields " + "WHERE factId IN " + Utils.ids2str(fids)
////                            + " GROUP BY factId", null);
////            while (cur.moveToNext()) {
////                String values = cur.getString(1);
////                // if (values.charAt(0) == ' ') {
////                // Fix for a slight difference between how Android SQLite and python sqlite work.
////                // Inconsequential difference in this context, but messes up any effort for automated testing.
////                values = values.replaceFirst("^ *", "");
////                // }
////                r.put(cur.getLong(0), Utils.stripHTMLMedia(values));
////            }
////        } finally {
////            if (cur != null && !cur.isClosed()) {
////                cur.close();
////            }
////        }
////
////        if (r.size() > 0) {
////            getDB().getDatabase().beginTransaction();
////            SQLiteStatement st = getDB().getDatabase().compileStatement("UPDATE facts SET cache=? WHERE id=?");
////            for (Entry<Long, String> entry : r.entrySet()) {
////                st.bindString(1, entry.getValue());
////                st.bindLong(2, entry.getKey().longValue());
////                st.execute();
////            }
////            getDB().getDatabase().setTransactionSuccessful();
////            getDB().getDatabase().endTransaction();
////        }
////    }
////
////
////    public int getETA(int failedCards, int revCards, int newCards, boolean global) {
////    	double left;
////    	double count;
////    	double averageTime;
////    	if (global) {
////			averageTime = mGlobalStats.getAverageTime();		
////		} else {
////    		averageTime = mDailyStats.getAverageTime();
////		}
//// 
////    	double globalYoungNoShare = mGlobalStats.getYoungNoShare();
////
////    	// rev + new cards first, account for failures
////    	count = newCards + revCards;
////    	count *= 1 + globalYoungNoShare;
////    	left = count * averageTime;
////
////    	//failed - higher time per card for higher amount of cards
////    	double failedBaseMulti = 1.5;
////    	double failedMod = 0.07;
////    	double failedBaseCount = 20;
////    	double factor = (failedBaseMulti + (failedMod * (failedCards - failedBaseCount)));
////    	left += failedCards * averageTime * factor;
////        	
////    	return (int) (left / 60);
////    }
////
//    /*
//     * Getters and Setters for deck properties NOTE: The setters flushMod()
//     * *********************************************************
//     */
//
//    public AnkiDb getDB() {
//        // TODO: Make this a reference to a member variable
//        return AnkiDatabaseManager.getDatabase(mDeckPath);
//    }
//
//
//    public String getDeckPath() {
//        return mDeckPath;
//    }
////
////
////    public void setDeckPath(String path) {
////        mDeckPath = path;
////    }
////
////
////    // public String getSyncName() {
////    //     return mSyncName;
////    // }
////
////
////    // public void setSyncName(String name) {
////    //     mSyncName = name;
////    //     flushMod();
////    // }
////
////
////    public int getRevCardOrder() {
////        return mRevCardOrder;
////    }
////
////
////    public void setRevCardOrder(int num) {
////        if (num >= 0) {
////            mRevCardOrder = num;
////            flushMod();
////        }
////    }
////
////
////    public int getNewCardSpacing() {
////        return mNewCardSpacing;
////    }
////
////
////    public void setNewCardSpacing(int num) {
////        if (num >= 0) {
////            mNewCardSpacing = num;
////            flushMod();
////        }
////    }
////
////
//    public int getIntVar(String name) {
//    	return getIntVar(name, 0);
//    }
//    public int getIntVar(String name, boolean qconf) {
//    	return getIntVar(name, 0, qconf);
//    }
//    public int getIntVar(String name, int fallback) {
//    	return getIntVar(name, fallback, false);
//    }
//    public int getIntVar(String name, int fallback, boolean qconf) {
//    	try {
//    		if (qconf) {
//    			return mQconf.getInt(name);
//    		} else {
//    			return mConf.getInt(name);
//    		}
//		} catch (JSONException e) {
//			if (fallback != 0) {
//				return 0;
//			} else {
//				throw new RuntimeException(e);				
//			}
//		}
//    }
//
//
//    public void setIntVar(String name, int num) {
//        if (num >= 0) {
//        	try {
//				mConf.put(name, num);
//			} catch (JSONException e) {
//				throw new RuntimeException(e);
//			}
//            flushMod();
//        }
//    }
//
//
//    public String getStringVar(String name) {
//    	return getStringVar(name, "");
//    }
//    public String getStringVar(String name, String fallback) {
//    	try {
//			return mConf.getString(name);
//		} catch (JSONException e) {
//			if (fallback != null) {
//				return "";
//			} else {
//				throw new RuntimeException(e);				
//			}
//		}
//    }
//
//
//    public void setStringVar(String name, String string) {
//    	try {
//			mConf.put(name, string);
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//        flushMod();
//    }
//
////
////    public boolean getSuspendLeeches() {
////        return getBool("suspendLeeches");
////    }
////
////
////    public void setSuspendLeeches(boolean suspendLeeches) {
////        if (suspendLeeches) {
////            setVar("suspendLeeches", "1");
////        } else {
////            setVar("suspendLeeches", "0");
////        }
////    }
////
////
////    public int getNewCardsPerDay() {
////        return mNewCardsPerDay;
////    }
////
////
////    public void setNewCardsPerDay(int num) {
////        if (num >= 0) {
////            mNewCardsPerDay = num;
////            flushMod();
////            reset();
////        }
////    }
////
////
////    public long getSessionRepLimit() {
////        return mSessionRepLimit;
////    }
////
////
////    public void setSessionRepLimit(long num) {
////        if (num >= 0) {
////            mSessionRepLimit = num;
////            flushMod();
////        }
////    }
////
////
////    public long getSessionTimeLimit() {
////        return mSessionTimeLimit;
////    }
////
////
////    public void setSessionTimeLimit(long num) {
////        if (num >= 0) {
////            mSessionTimeLimit = num;
////            flushMod();
////        }
////    }
////
////
////    /**
////     * @return the failedSoonCount
////     */
////    public int getFailedSoonCount() {
////        return mFailedSoonCount;
////    }
////
////
////    /**
////     * @return the revCount
////     */
////    public int getRevCount() {
////        return mRevCount;
////    }
////
////
////    /**
////     * @return the newCount
////     */
////    public int getNewAvail() {
////        return mNewAvail;
////    }
////
////
////    /**
////     * @return the number of due cards in the deck
////     */
////    public int getDueCount() {
////        return mFailedSoonCount + mRevCount;
////    }
////
////
////    /**
////     * @param cardCount the cardCount to set
////     */
////    public void setCardCount(int cardCount) {
////        mCardCount = cardCount;
////        // XXX: Need to flushmod() ?
////    }
////
////
////    /**
////     * Get the cached total number of cards of the deck.
////     *
////     * @return The number of cards contained in the deck
////     */
////    public int getCardCount() {
////        return mCardCount;
////    }
////
////
////
//    /**
//     * @return the deckName
//     */
//    public String getDeckName() {
//        return mDeckName;
//    }
////
////
////    /**
////     * @return the deck UTC offset in number seconds
////     */
////    public double getUtcOffset() {
////        return mUtcOffset;
////    }
////    public void setUtcOffset() {
////        mUtcOffset = Utils.utcOffset();
////    }
////
////
////    /**
////     * @return the newCount
////     */
////    public int getNewCount() {
////        return mNewCount;
////    }
////
////
//    /**
//     * @return the modified
//     */
//    public int getModified() {
//        return mMod;
//    }
//
//
//    /**
//     * @param lastSync the lastSync to set
//     */
//    public void setLastSync(int lastSync) {
//        mLastSync = lastSync;
//    }
//
//
//    /**
//     * @return the lastSync
//     */
//    public int getLastSync() {
//        return mLastSync;
//    }
////
////
////    /**
////     * @param factCount the factCount to set
////     */
////    public void setFactCount(int factCount) {
////        mFactCount = factCount;
////        // XXX: Need to flushmod() ?
////    }
////
////
////    /**
////     * @return the factCount
////     */
////    public int getFactCount() {
////        return mFactCount;
////    }
////
////
////    /**
////     * @param lastLoaded the lastLoaded to set
////     */
////    public double getLastLoaded() {
////        return mLastLoaded;
////    }
////
////
////    /**
////     * @param lastLoaded the lastLoaded to set
////     */
////    public void setLastLoaded(double lastLoaded) {
////        mLastLoaded = lastLoaded;
////    }
////
////
////    public void setVersion(int version) {
////        mVersion = version;
////    }
////
//    public int getVersion() {
//        return 0;
//    }
////
////
////    public boolean isUnpackNeeded() {
////        return mNeedUnpack;
////    }
////
////    public double getDueCutoff() {
////        return mDueCutoff;
////    }
////
////
////    public String getScheduler() {
////        return mScheduler;
////    }
////
////
////
////    /*
////     * Tags: Querying*****************************
////     */
////
////    /**
////     * Get a map of card IDs to their associated tags (fact, model and template)
////     *
////     * @param where SQL restriction on the query. If empty, then returns tags for all the cards
////     * @return The map of card IDs to an array of strings with 3 elements representing the triad {card tags, model tags,
////     *         template tags}
////     */
////    private HashMap<Long, List<String>> splitTagsList() {
////        return splitTagsList("");
////    }
////
////
////    private HashMap<Long, List<String>> splitTagsList(String where) {
////        Cursor cur = null;
////        HashMap<Long, List<String>> results = new HashMap<Long, List<String>>();
////        try {
////            cur = getDB().getDatabase().rawQuery(
////                    "SELECT cards.id, facts.tags, models.name, cardModels.name "
////                            + "FROM cards, facts, models, cardModels "
////                            + "WHERE cards.factId == facts.id AND facts.modelId == models.id "
////                            + "AND cards.cardModelId = cardModels.id " + where, null);
////            while (cur.moveToNext()) {
////                ArrayList<String> tags = new ArrayList<String>();
////                tags.add(cur.getString(1));
////                tags.add(cur.getString(2));
////                tags.add(cur.getString(3));
////                results.put(cur.getLong(0), tags);
////            }
////        } catch (SQLException e) {
////            Log.e(AnkiDroidApp.TAG, "splitTagsList: Error while retrieving tags from DB: " + e.toString());
////        } finally {
////            if (cur != null && !cur.isClosed()) {
////                cur.close();
////            }
////        }
////        return results;
////    }
////
////
////    /**
////     * Returns all model tags, all template tags and a filtered set of fact tags
////     *
////     * @param where Optional, SQL filter for fact tags. If skipped, returns all fact tags
////     * @return All the distinct individual tags, sorted, as an array of string
////     */
////    public String[] allTags_() {
////        return allTags_("");
////    }
////
////
////    private String[] allTags_(String where) {
////        ArrayList<String> t = new ArrayList<String>();
////        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM facts " + where, 0));
////        t.addAll(getDB().queryColumn(String.class, "SELECT name FROM models", 0));
////        t.addAll(getDB().queryColumn(String.class, "SELECT name FROM cardModels", 0));
////        String joined = Utils.joinTags(t);
////        String[] parsed = Utils.parseTags(joined);
////        List<String> joinedList = Arrays.asList(parsed);
////        TreeSet<String> joinedSet = new TreeSet<String>(joinedList);
////        return joinedSet.toArray(new String[joinedSet.size()]);
////    }
////
////
////    public String[] allUserTags() {
////        return allUserTags("");
////    }
////
////
////    public String[] allUserTags(String where) {
////        ArrayList<String> t = new ArrayList<String>();
////        t.addAll(getDB().queryColumn(String.class, "SELECT tags FROM facts " + where, 0));
////        String joined = Utils.joinTags(t);
////        String[] parsed = Utils.parseTags(joined);
////        List<String> joinedList = Arrays.asList(parsed);
////        TreeSet<String> joinedSet = new TreeSet<String>(joinedList);
////        return joinedSet.toArray(new String[joinedSet.size()]);
////    }
////
////
////    /*
////     * Tags: Caching*****************************
////     */
////
////    public void updateFactTags(long[] factIds) {
////        updateCardTags(Utils.toPrimitive(getDB().queryColumn(Long.class,
////                "SELECT id FROM cards WHERE factId IN " + Utils.ids2str(factIds), 0)));
////    }
////
////
////    public void updateCardTags() {
////        updateCardTags(null);
////    }
////
////
////    public void updateCardTags(long[] cardIds) {
////        HashMap<String, Long> tids = new HashMap<String, Long>();
////        HashMap<Long, List<String>> rows = new HashMap<Long, List<String>>();
////        if (cardIds == null) {
////            getDB().getDatabase().execSQL("DELETE FROM cardTags");
////            getDB().getDatabase().execSQL("DELETE FROM tags");
////            tids = tagIds(allTags_());
////            rows = splitTagsList();
////        } else {
////            Log.i(AnkiDroidApp.TAG, "updateCardTags cardIds: " + Arrays.toString(cardIds));
////            getDB().delete(this, "cardTags", "cardId IN " + Utils.ids2str(cardIds), null);
////            String fids = Utils.ids2str(Utils.toPrimitive(getDB().queryColumn(Long.class,
////                    "SELECT factId FROM cards WHERE id IN " + Utils.ids2str(cardIds), 0)));
////            Log.i(AnkiDroidApp.TAG, "updateCardTags fids: " + fids);
////            tids = tagIds(allTags_("WHERE id IN " + fids));
////            Log.i(AnkiDroidApp.TAG, "updateCardTags tids keys: " + Arrays.toString(tids.keySet().toArray(new String[tids.size()])));
////            Log.i(AnkiDroidApp.TAG, "updateCardTags tids values: " + Arrays.toString(tids.values().toArray(new Long[tids.size()])));
////            rows = splitTagsList("AND facts.id IN " + fids);
////            Log.i(AnkiDroidApp.TAG, "updateCardTags rows keys: " + Arrays.toString(rows.keySet().toArray(new Long[rows.size()])));
////            for (List<String> l : rows.values()) {
////                Log.i(AnkiDroidApp.TAG, "updateCardTags rows values: ");
////                for (String v : l) {
////                    Log.i(AnkiDroidApp.TAG, "updateCardTags row item: " + v);
////                }
////            }
////        }
////
////        ArrayList<HashMap<String, Long>> d = new ArrayList<HashMap<String, Long>>();
////
////        for (Entry<Long, List<String>> entry : rows.entrySet()) {
////        	Long id = entry.getKey();
////            for (int src = 0; src < 3; src++) { // src represents the tag type, fact: 0, model: 1, template: 2
////                for (String tag : Utils.parseTags(entry.getValue().get(src))) {
////                    HashMap<String, Long> ditem = new HashMap<String, Long>();
////                    ditem.put("cardId", id);
////                    ditem.put("tagId", tids.get(tag.toLowerCase()));
////                    ditem.put("src", new Long(src));
////                    Log.i(AnkiDroidApp.TAG, "populating ditem " + src + " " + tag);
////                    d.add(ditem);
////                }
////            }
////        }
////
////        for (HashMap<String, Long> ditem : d) {
////        	ContentValues values = new ContentValues();
////        	values.put("cardId", ditem.get("cardId"));
////        	values.put("tagId", ditem.get("tagId"));
////        	values.put("src",  ditem.get("src"));
////            getDB().insert(this, "cardTags", null, values);
////        }
////	deleteUnusedTags();
////    }
////
////
////    public ArrayList<String[]> getAllCards(String order) {
////    	ArrayList<String[]> allCards = new ArrayList<String[]>();
////
////        Cursor cur = null;
////        try {
////        	cur = getDB().getDatabase().rawQuery("SELECT cards.id, cards.question, cards.answer, " +
////        			"facts.tags, models.tags, cardModels.name, cards.priority FROM cards, facts, " +
////        			"models, cardModels WHERE cards.factId == facts.id AND facts.modelId == models.id " +
////        			"AND cards.cardModelId = cardModels.id ORDER BY " + order, null);
////            while (cur.moveToNext()) {
////            	String[] data = new String[5];
////            	data[0] = Long.toString(cur.getLong(0));
////                String string = Utils.stripHTML(cur.getString(1));
////            	if (string.length() < 55) {
////                    data[1] = string;
////            	} else {
////                    data[1] = string.substring(0, 55) + "...";                   
////            	}
////            	string = Utils.stripHTML(cur.getString(2));
////                if (string.length() < 55) {
////                    data[2] = string;
////                } else {
////                    data[2] = string.substring(0, 55) + "...";                   
////                }
////            	String tags = cur.getString(3);
////           	    if (tags.contains(TAG_MARKED)) {
////           	        data[3] = "1";
////           	    } else {
////           	        data[3] = "0";
////           	    }
////           	    data[4] = tags + " " + cur.getString(4) + " " + cur.getString(5);
////            	if (cur.getString(6).equals("-3")) {
////                    data[3] = data[3] + "1";
////                } else {
////                    data[3] = data[3] + "0";
////                }
////            	allCards.add(data);
////            }
////        } catch (SQLException e) {
////            Log.e(AnkiDroidApp.TAG, "getAllCards: " + e.toString());
////            return null;
////        } finally {
////            if (cur != null && !cur.isClosed()) {
////                cur.close();
////            }
////        }
////    	return allCards;
////    }
////
////
////    /*
////     * Tags: adding/removing in bulk*********************************************************
////     */
////
////    public void deleteUnusedTags() {
////	getDB().delete(this, "tags", "id NOT IN (SELECT DISTINCT tagId FROM cardTags)", null);
////    }
////
////    public ArrayList<String> factTags(long[] factIds) {
////        return getDB().queryColumn(String.class, "SELECT tags FROM facts WHERE id IN " + Utils.ids2str(factIds), 0);
////    }
////
////
////    public void addTag(long factId, String tag) {
////        long[] ids = new long[1];
////        ids[0] = factId;
////        addTag(ids, tag);
////    }
////
////
////    public void addTag(long[] factIds, String tag) {
////        ArrayList<String> factTagsList = factTags(factIds);
////
////        // Create tag if necessary
////        long tagId = tagId(tag, true);
////
////        int nbFactTags = factTagsList.size();
////        for (int i = 0; i < nbFactTags; i++) {
////            String newTags = factTagsList.get(i);
////
////            if (newTags.indexOf(tag) == -1) {
////                if (newTags.length() == 0) {
////                    newTags += tag;
////                } else {
////                    newTags += "," + tag;
////                }
////            }
////            Log.i(AnkiDroidApp.TAG, "old tags = " + factTagsList.get(i));
////            Log.i(AnkiDroidApp.TAG, "new tags = " + newTags);
////
////            if (newTags.length() > factTagsList.get(i).length()) {
////            	ContentValues values = new ContentValues();
////            	values.put("tags", newTags);
////            	values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
////                getDB().update(this, "facts", values, "id = " + factIds[i], null);
////            }
////        }
////
////        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
////                "select id from cards where factId in " + Utils.ids2str(factIds), 0);
////
////        for (String cardId : cardIdList) {
////            try {
////                // Check if the tag already exists
////                getDB().queryScalar(
////                        "SELECT id FROM cardTags WHERE cardId = " + cardId + " and tagId = " + tagId + " and src = "
////                                + Card.TAGS_FACT);
////            } catch (SQLException e) {
////            	ContentValues values = new ContentValues();
////                values.put("cardId", cardId);
////                values.put("tagId", tagId);
////                values.put("src", String.valueOf(Card.TAGS_FACT));
////                getDB().insert(this, "cardTags", null, values);
////            }
////        }
////
////        flushMod();
////    }
////
////
////    public void deleteTag(long factId, String tag) {
////        long[] ids = new long[1];
////        ids[0] = factId;
////        deleteTag(ids, tag);
////    }
////
////
////    public void deleteTag(long[] factIds, String tag) {
////        ArrayList<String> factTagsList = factTags(factIds);
////
////        long tagId = tagId(tag, false);
////
////        int nbFactTags = factTagsList.size();
////        for (int i = 0; i < nbFactTags; i++) {
////            String factTags = factTagsList.get(i);
////            String newTags = factTags;
////
////            int tagIdx = factTags.indexOf(tag);
////            if ((tagIdx == 0) && (factTags.length() > tag.length())) {
////                // tag is the first element of many, remove "tag,"
////                newTags = factTags.substring(tag.length() + 1, factTags.length());
////            } else if ((tagIdx > 0) && (tagIdx + tag.length() == factTags.length())) {
////                // tag is the last of many elements, remove ",tag"
////                newTags = factTags.substring(0, tagIdx - 1);
////            } else if (tagIdx > 0) {
////                // tag is enclosed between other elements, remove ",tag"
////                newTags = factTags.substring(0, tagIdx - 1) + factTags.substring(tag.length(), factTags.length());
////            } else if (tagIdx == 0) {
////                // tag is the only element
////                newTags = "";
////            }
////            Log.i(AnkiDroidApp.TAG, "old tags = " + factTags);
////            Log.i(AnkiDroidApp.TAG, "new tags = " + newTags);
////
////            if (newTags.length() < factTags.length()) {
////            	ContentValues values = new ContentValues();
////                values.put("tags", newTags);
////                values.put("modified", String.format(Utils.ENGLISH_LOCALE, "%f", Utils.now()));
////                getDB().update(this, "facts", values, "id = " + factIds[i], null);
////            }
////        }
////
////        ArrayList<String> cardIdList = getDB().queryColumn(String.class,
////                "select id from cards where factId in " + Utils.ids2str(factIds), 0);
////
////        for (String cardId : cardIdList) {
////        	getDB().delete(this, "cardTags", "cardId = " + cardId + " and tagId = " + tagId + " and src = " + Card.TAGS_FACT, null);
////        }
////
////        // delete unused tags from tags table
////        try {
////            getDB().queryScalar("select id from cardTags where tagId = " + tagId + " limit 1");
////        } catch (SQLException e) {
////        	getDB().delete(this, "tags", "id = " + tagId, null);
////        }
////
////        flushMod();
////    }
////
////    // CSS for all the fields
////    private String rebuildCSS() {
////        StringBuilder css = new StringBuilder(512);
////        Cursor cur = null;
////
////        try {
////            cur = getDB().getDatabase().rawQuery(
////                    "SELECT id, quizFontFamily, quizFontSize, quizFontColour, -1, "
////                            + "features, editFontFamily FROM fieldModels", null);
////            while (cur.moveToNext()) {
////                css.append(_genCSS(".fm", cur));
////            }
////            cur.close();
////            cur = getDB().getDatabase().rawQuery("SELECT id, null, null, null, questionAlign, 0, 0 FROM cardModels",
////                    null);
////            StringBuilder cssAnswer = new StringBuilder(512);
////            while (cur.moveToNext()) {
////                css.append(_genCSS("#cmq", cur));
////                cssAnswer.append(_genCSS("#cma", cur));
////            }
////            css.append(cssAnswer.toString());
////            cur.close();
////            cur = getDB().getDatabase().rawQuery("SELECT id, lastFontColour FROM cardModels", null);
////            while (cur.moveToNext()) {
////                css.append(".cmb").append(Utils.hexifyID(cur.getLong(0))).append(" {background:").append(
////                        cur.getString(1)).append(";}\n");
////            }
////        } finally {
////            if (cur != null && !cur.isClosed()) {
////                cur.close();
////            }
////        }
////        setVar("cssCache", css.toString(), false);
////        addHexCache();
////
////        return css.toString();
////    }
////
////
////    private String _genCSS(String prefix, Cursor row) {
////        StringBuilder t = new StringBuilder(256);
////        long id = row.getLong(0);
////        String fam = row.getString(1);
////        int siz = row.getInt(2);
////        String col = row.getString(3);
////        int align = row.getInt(4);
////        String rtl = row.getString(5);
////        int pre = row.getInt(6);
////        if (fam != null) {
////            t.append("font-family:\"").append(fam).append("\";");
////        }
////        if (siz != 0) {
////            t.append("font-size:").append(siz).append("px;");
////        }
////        if (col != null) {
////            t.append("color:").append(col).append(";");
////        }
////        if (rtl != null && rtl.compareTo("rtl") == 0) {
////            t.append("direction:rtl;unicode-bidi:embed;");
////        }
////        if (pre != 0) {
////            t.append("white-space:pre-wrap;");
////        }
////        if (align != -1) {
////            if (align == 0) {
////                t.append("text-align:center;");
////            } else if (align == 1) {
////                t.append("text-align:left;");
////            } else {
////                t.append("text-align:right;");
////            }
////        }
////        if (t.length() > 0) {
////            t.insert(0, prefix + Utils.hexifyID(id) + " {").append("}\n");
////        }
////        return t.toString();
////    }
////
////
////    private void addHexCache() {
////        ArrayList<Long> ids = getDB().queryColumn(Long.class,
////                "SELECT id FROM fieldModels UNION SELECT id FROM cardModels UNION SELECT id FROM models", 0);
////        JSONObject jsonObject = new JSONObject();
////        for (Long id : ids) {
////            try {
////                jsonObject.put(id.toString(), Utils.hexifyID(id.longValue()));
////            } catch (JSONException e) {
////                Log.e(AnkiDroidApp.TAG, "addHexCache: Error while generating JSONObject: " + e.toString());
////                throw new RuntimeException(e);
////            }
////        }
////        setVar("hexCache", jsonObject.toString(), false);
////    }
////    /*
////     * Undo/Redo*********************************************************
////     */
////
////    private class UndoRow {
////        private String mName;
////        private Long mCardId;
////        private ArrayList<UndoCommand> mUndoCommands;
////
////        UndoRow(String name, Long cardId) {
////            mName = name;
////            mCardId = cardId;
////            mUndoCommands = new ArrayList<UndoCommand>();
////        }
////    }
////
////
////    private class UndoCommand {
////        private String mCommand;
////        private String mTable;
////        private ContentValues mValues;
////        private String mWhereClause;
////
////        UndoCommand(String command, String table, ContentValues values, String whereClause) {
////        	mCommand = command;
////        	mTable = table;
////        	mValues = values;
////        	mWhereClause = whereClause;
////        }
////    }
////
////
////    private void initUndo() {
////        mUndoStack = new Stack<UndoRow>();
////        mRedoStack = new Stack<UndoRow>();
////        mUndoEnabled = true;
////    }
////
////
////    public String undoName() {
////        return mUndoStack.peek().mName;
////    }
////
////
////    public String redoName() {
////        return mRedoStack.peek().mName;
////    }
////
////
////    public boolean undoAvailable() {
////        return (mUndoEnabled && !mUndoStack.isEmpty());
////    }
////
////
////    public boolean redoAvailable() {
////        return (mUndoEnabled && !mRedoStack.isEmpty());
////    }
////
////
////    public void resetUndo() {
////        mUndoStack.clear();
////        mRedoStack.clear();
////    }
////
////
////    private void setUndoBarrier() {
////        if (mUndoStack.isEmpty() || mUndoStack.peek() != null) {
////            mUndoStack.push(null);
////        }
////    }
////
////
////    public void setUndoStart(String name) {
////        setUndoStart(name, 0, false);
////    }
////
////    public void setUndoStart(String name, long cardId) {
////        setUndoStart(name, cardId, false);
////    }
////
////
////    /**
////     * @param reviewEarly set to true for early review
////     */
////    public void setReviewEarly(boolean reviewEarly) {
////        mReviewEarly = reviewEarly;
////    }
////
////
////    private void setUndoStart(String name, long cardId, boolean merge) {
////        if (!mUndoEnabled) {
////            return;
////        }
////        if (merge && !mUndoStack.isEmpty()) {
////            if ((mUndoStack.peek() != null) && (mUndoStack.peek().mName.equals(name))) {
////                // libanki: merge with last entry?
////                return;
////            }
////        }
////        mUndoStack.push(new UndoRow(name, cardId));
////        if (mUndoStack.size() > 20) {
////        	mUndoStack.removeElementAt(0);
////        }
////        mUndoRedoStackToRecord = mUndoStack;
////    }
////
////
////    public void setUndoEnd(String name) {
////        if (!mUndoEnabled) {
////            return;
////        }
////        while (mUndoStack.peek() == null) {
////            mUndoStack.pop(); // Strip off barrier
////        }
////        UndoRow row = mUndoStack.peek();
////        if (row.mUndoCommands.size() == 0) {
////            mUndoStack.pop();
////        } else {
////            mRedoStack.clear();
////        }
////        mUndoRedoStackToRecord = null;
////    }
////
////
////    public boolean recordUndoInformation() {
////    	return mUndoEnabled && (mUndoRedoStackToRecord != null);
////    }
////
////
////    public void addUndoCommand(String command, String table, ContentValues values, String whereClause) {
////    	mUndoRedoStackToRecord.peek().mUndoCommands.add(new UndoCommand(command, table, values, whereClause));
////    }
////
////
////    private long undoredo(Stack<UndoRow> src, Stack<UndoRow> dst, long oldCardId, boolean inReview) {
////        UndoRow row;
////        while (true) {
////            row = src.pop();
////            if (row != null) {
////                break;
////            }
////        }
////        if (inReview) {
////           dst.push(new UndoRow(row.mName, row.mCardId));
////        } else {
////           dst.push(new UndoRow(row.mName, oldCardId));
////        }
////        mUndoRedoStackToRecord = dst;
////        getDB().getDatabase().beginTransaction();
////        try {
////            for (UndoCommand u : row.mUndoCommands) {
////                getDB().execSQL(this, u.mCommand, u.mTable, u.mValues, u.mWhereClause);
////            }
////            getDB().getDatabase().setTransactionSuccessful();
////        } finally {
////        	mUndoRedoStackToRecord = null;
////        	getDB().getDatabase().endTransaction();
////        }
////        if (row.mUndoCommands.size() == 0) {
////        	dst.pop();
////        }
////        mCurrentUndoRedoType = row.mName;
////        return row.mCardId;
////    }
////
////
////    /**
////     * Undo the last action(s). Caller must .reset()
////     */
////    public long undo(long oldCardId, boolean inReview) {
////        long cardId = 0;
////    	if (!mUndoStack.isEmpty()) {
////            cardId = undoredo(mUndoStack, mRedoStack, oldCardId, inReview);
////            commitToDB();
////            reset();
////        }
////        return cardId;
////    }
////
////
////    /**
////     * Redo the last action(s). Caller must .reset()
////     */
////    public long redo(long oldCardId, boolean inReview) {
////        long cardId = 0;
////        if (!mRedoStack.isEmpty()) {
////        	cardId = undoredo(mRedoStack, mUndoStack, oldCardId, inReview);
////            commitToDB();
////            reset();
////        }
////        return cardId;
////    }
////
////
////    public String getUndoType() {
////    	return mCurrentUndoRedoType;
////    }
////
////
////
////
//
//    /*
//     * JSON
//     */
//    public JSONObject bundleJson(JSONObject bundledDeck) {
//        try {
//        	bundledDeck.put("crt", mCrt);
//        	bundledDeck.put("mod", mMod);
//        	bundledDeck.put("scm", mScm);
//        	bundledDeck.put("dty", mDty);
//        	bundledDeck.put("syncName", mSyncName);
//        	bundledDeck.put("lastSync", mLastSync);
//        	bundledDeck.put("qconf", mQconf.toString());
//        	bundledDeck.put("conf", mConf.toString());
//        	bundledDeck.put("data", mData.toString());
//        } catch (JSONException e) {
//            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
//        }
//        return bundledDeck;
//    }
//
//
//    public void updateFromJson(JSONObject deckPayload) {
//        try {
//            // Update deck
//            mId = deckPayload.getInt("id");
//            mCrt = deckPayload.getInt("crt");
//            mMod = deckPayload.getInt("mod");
//            mScm = deckPayload.getInt("scm");
//            mDty = deckPayload.getInt("dty");
//            mSyncName = deckPayload.getString("syncName");
//            mLastSync = deckPayload.getInt("lastSync");
//            try {
//				mQconf= deckPayload.getJSONObject("qConf");
//	            mConf = deckPayload.getJSONObject("conf");
//	            mData = deckPayload.getJSONObject("data");
//			} catch (JSONException e) {
//				throw new RuntimeException(e);
//			}
//            commitToDB();
//        } catch (JSONException e) {
//            Log.i(AnkiDroidApp.TAG, "JSONException = " + e.getMessage());
//        }
//    }
//
////
////    /**
////     * Initialize an empty deck that has just been creating by copying the existing "empty.anki" file.
////     *
////     * From Damien:
////     * Just copying a file is not sufficient - you need to give each model, cardModel and fieldModel new ids as well, and make sure they are all still linked up. If you don't do that, and people modify one model and then import/export one deck into another, the models will be treated as identical even though they have different layouts, and half the cards will end up corrupted.
////     *  It's only the IDs that you have to worry about, and the utcOffset IIRC.
////     */
////    public static synchronized void initializeEmptyDeck(String deckPath) {
////        AnkiDb db = AnkiDatabaseManager.getDatabase(deckPath);
////
////        // Regenerate IDs.
////        long modelId = Utils.genID();
////        db.getDatabase().execSQL("UPDATE models SET id=" + modelId);
////        db.getDatabase().execSQL("UPDATE cardModels SET id=" + Utils.genID() + " where ordinal=0;");
////        db.getDatabase().execSQL("UPDATE cardModels SET id=" + Utils.genID() + " where ordinal=1;");
////        db.getDatabase().execSQL("UPDATE fieldModels SET id=" + Utils.genID() + " where ordinal=0;");
////        db.getDatabase().execSQL("UPDATE fieldModels SET id=" + Utils.genID() + " where ordinal=1;");
////
////        // Update columns that refer to modelId.
////        db.getDatabase().execSQL("UPDATE fieldModels SET modelId=" + modelId);
////        db.getDatabase().execSQL("UPDATE cardModels SET modelId=" + modelId);
////        db.getDatabase().execSQL("UPDATE decks SET currentModelId=" + modelId);
////
////        // Set the UTC offset.
////        db.getDatabase().execSQL("UPDATE decks SET utcOffset=" + Utils.utcOffset());
////    }
////
////
////    public void createMetadata() {
////        // Just create table deckvars for now
////        getDB().getDatabase().execSQL(
////                "CREATE TABLE IF NOT EXISTS deckVars (\"key\" TEXT NOT NULL, value TEXT, " + "PRIMARY KEY (\"key\"))");
////    }
////
////
//// 
////
////
//    public static int getLastModified(String deckPath) {
//        int value;
//        Cursor cursor = null;
//
//        boolean dbAlreadyOpened = AnkiDatabaseManager.isDatabaseOpen(deckPath);
//
//        try {
//            cursor = AnkiDatabaseManager.getDatabase(deckPath).getDatabase().rawQuery(
//                    "SELECT mod" + " FROM deck" + " LIMIT 1", null);
//            if (!cursor.moveToFirst()) {
//                value = -1;
//            } else {
//                value = cursor.getInt(0);
//            }
//        } finally {
//            if (cursor != null && !cursor.isClosed()) {
//                cursor.close();
//            }
//        }
//
//        if (!dbAlreadyOpened) {
//            AnkiDatabaseManager.closeDatabase(deckPath);
//        }
//
//        return value;
//    }
//
//
//
//    public static synchronized int getDeckVersion(String path) throws SQLException {
//        int version = (int) AnkiDatabaseManager.getDatabase(path).queryScalar("SELECT ver FROM deck LIMIT 1");
//        return version;
//    }
//
//
//    public void setModified(double mod) {
//    }
//    public void setLastSync(double mod) {
//    }
//    public void setLastLoaded(double mod) {
//    }
//    public void resetUndo() {
//    	
//    }
//
//    public Sched getSched() {
//    	return mSched;
//    }

}
