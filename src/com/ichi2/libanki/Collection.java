/****************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General private License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General private License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General private License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// Anki maintains a cache of used tags so it can quickly present a list of tags
// for autocomplete and in the browser. For efficiency, deletions are not
// tracked, so unused tags can only be removed from the list with a DB check.
//
// This module manages the tag cache and tags for notes.

public class Collection {

	private AnkiDb mDb;
	private boolean mServer;
	private double mLastSave;
	private Media mMedia;
	private Decks mDecks;
	private Models mModels;
	private Tags mTags;

	private Sched mStdSched;
    private Sched mSched;

	private int mSessionStartReps;
    private int mSessionRepLimit;
    private double mSessionStartTime;
    private double mLastSessionStart;
    private double mSessionTimeLimit;
    private boolean mUndoEnabled;

    private int mRepsToday;

    // BEGIN: SQL table columns
    private int mCrt;
    private int mMod;
    private int mScm;
    private boolean mDty;
    private int mUsn;
    private int mLs;
    private JSONObject mConf;
    // END: SQL table columns

    // other options
    private static final String defaultConf = "{" +
    		// review options
    	    "'activeDecks': [1], " +
    	    "'curDeck': 1, " +
    	    "'newSpread': " + Sched.NEW_CARDS_DISTRIBUTE + ", " +
    	    "'collapseTime': 1200, " +
    	    "'timeLim': 0, " +
    	    "'estTimes': True, " +
    	    "'dueCounts': True, " +
    	    // other config
    	    "'curModel': None, " +
    	    "'nextPos': 1, " +
    	    "'sortType': \"noteFld\", " +
    	    "'sortBackwards': False, }";

    private static Collection sCurrentCollection;

    public static synchronized Collection openCollection(String path) {
    	AnkiDb ankiDB = AnkiDatabaseManager.getDatabase(path);
    	sCurrentCollection = new Collection(ankiDB);
    	return sCurrentCollection;
    }


    public Collection(AnkiDb db) {
		this(db, false);
	}
	public Collection(AnkiDb db, boolean server) {
    	mDb = db;
    	mServer = server;
    	mLastSave = Utils.now();
    	clearUndo();
//    	mMedia = new Media(this);
    	mModels = new Models(this);
    	mDecks = new Decks(this);
    	mTags = new Tags(this);
    	load();
    	if (mCrt != 0) {
    		// TODO:
    	}
    	mUndoEnabled = false;
    	mSessionStartReps = 0;
        mSessionStartTime = 0;
        mLastSessionStart = 0;
        mStdSched = new Sched(this);
        mSched = mStdSched;
        // check for improper shutdown
        cleanup();
    }


	public static Collection currentCollection() {
		return sCurrentCollection;
	}


	public String name() {
		String n = "";
		// TODO:
		return n;
	}


    /**
     * DB-related
     * ***********************************************************************************************
     */

    public boolean load() {
    	Cursor cursor = null;
        try {
            // Read in deck table columns
            cursor = mDb.getDatabase().rawQuery("SELECT crt, mod, scm, dty, usn, ls, conf, " +
            		"models, decks, dconf, tags FROM col", null);
            if (!cursor.moveToFirst()) {
                return false;
            }
            mCrt = cursor.getInt(0);
            mMod = cursor.getInt(1);
            mScm = cursor.getInt(2);
            mDty = cursor.getInt(3) == 1;
            mUsn = cursor.getInt(4);
            mLs = cursor.getInt(5);
            try {
	            mConf = new JSONObject(cursor.getString(6));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
            mModels.load(cursor.getString(7));
            mDecks.load(cursor.getString(8), cursor.getString(9));
            mTags.load(cursor.getString(10));
            return true;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    /**
     * Mark DB modified.
     * DB operations and the deck/tag/model managers do this automatically, so this
	 * is only necessary if you modify properties of this object or the conf dict.
     */
    public void setMod() {
    	mDb.setMod(true);
    }


    public void flush() {
    	flush(0);
    }
    /**
     * Flush state to DB, updating mod time.
     */
    public void flush(int mod) {
        Log.i(AnkiDroidApp.TAG, "flush - Saving information to DB...");
        mMod = (mod == 0 ? Utils.intNow() : mod);
        ContentValues values = new ContentValues();
        values.put("crt", mCrt);
        values.put("mod", mMod);
        values.put("scm", mScm);
        values.put("dty", mDty ? 1 : 0);
        values.put("usn", mUsn);
        values.put("ls", mLs);
        values.put("conf", mConf.toString());
        mDb.getDatabase().update("col", values, null, null);
    }


    /**
     * Flush, commit DB, and take out another write lock.
     */
    public synchronized void save() {
    	save(null, 0);
    }
    public synchronized void save(String name, int mod) {
    	// let the managers conditionally flush
    	mModels.flush();
    	mDecks.flush();
    	mTags.flush();
    	// and flush deck + bump mod if db has been changed
    	if (mDb.getMod()) {
    		flush(mod);
//    		mDb.commit();
    		// TODO:
    		mDb.setMod(false);
    	}
		// TODO:
    }


    // autosave?


    public void lock() {
    	// TODO:
    }


    /**
     * Disconnect from DB.
     */
    public synchronized void close() {
    	close(true);
    }
    public synchronized void close(boolean save) {
//        if (wait) {
//        	// Wait for any thread working on the deck to finish.
//        	DeckTask.waitToFinish(); 
//        }
        if (mDb != null) {
            cleanup();
            if (save) {
            	save();
            } else {
            	rollback();
            }
            if (!mServer) {
            	// TODO:
            }
//          AnkiDatabaseManager.closeDatabase();
//            mDb.close();
            mDb = null;
//            mMedia.close();
        }
    }


    public void reopen() {
    	// TODO:
    }


    public void rollback() {
    	// TODO:
    }


    public void modSchema() {
    	// TODO:
    }


    public void schemaChanged() {
    	// TODO:
    }


    /**
     * Signal there are temp. suspended cards that need cleaning up on close.
     */
    public void setDirty() {
        mDty = true;
    }

    
    /**
     * Unsuspend any temporarily suspended cards.
     */
    private void cleanup() {
        if (mDty) {
        	mStdSched.onClose();
        	mDty = false;
        }
    }

    public int usn() {
    	if (mServer) {
    		return mUsn;
    	} else {
    		return -1;
    	}
    }


    public void beforeUpload() {
    	// TODO:
    }

    /**
     * Object creation helpers
     * ***********************************************************************************************
     */

    public Card getCard(long id) {
    	return new Card(this, id);
    }


    public Note getNote(long id) {
    	return new Note(this, id);
    }


    /**
     * Utils
     * ***********************************************************************************************
     */

    public int nextID(String type) {
    	type = "next" + type.toUpperCase();
    	int id;
    	try {
        	id = mConf.getInt(type);
    	} catch (JSONException e) {
    		id = 1;
    	}
    	try {
			mConf.put(type, id + 1);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    	return id;
    }


    /**
     * Rebuild the queue and reload data after DB modified.
     */
    public void reset() {
    	mSched.reset();
    }

    /**
     * Deletion logging
     * ***********************************************************************************************
     */

    public void _logRem(long[] ids, int type) {
    	for (long id : ids) {
    		ContentValues values = new ContentValues();
            values.put("usn", usn());
            values.put("oid", id);
            values.put("type", type);
    		mDb.getDatabase().insert("graves", null, values);
    	}
    }

    /**
     * Notes
     * ***********************************************************************************************
     */

    public int noteCount() {
    	return (int) mDb.queryScalar("SELECT count() FROM notes");
    }


    /**
     * Return a new note with the current model.
     */
    public Note newNote(long modelId) {
        return new Note(this, mModels.current());
    }


    /**
     * Add a note to the collection. Return number of new cards.
     */
    public int addNote(Note note) {
    	// check we have card models available, then save
    	JSONObject[] cms = findTemplates(note);
    	if (cms.length == 0) {
    		return 0;
    	}
    	note.flush();
    	// deck conf governs which of these are used
    	int due = nextID("pos");
    	// add cards
    	int ncards = 0;
    	for (JSONObject template : cms) {
    		_newCard(note, template, due);
    		ncards += 1;
    	}
    	return ncards;
    }


    public void remNotes(long[] ids) {
    	ArrayList<Long> list = mDb.queryColumn(long.class, "SELECT id FROM cards WHERE nid in " + Utils.ids2str(ids), 0);
    	long[] cids = new long[list.size()];
    	int i = 0;
    	for (long l : list) {
    		cids[i++] = l;
    	}
    	remCards(cids);
    }


    /**
     * Bulk delete facts by ID. Don't call this directly.
     */
    private void _remNotes(long[] ids) {
    	if (ids.length == 0) {
    		return;
    	}
		String strids = Utils.ids2str(ids);
		// we need to log these independently of cards, as one side may have more card templates
		_logRem(ids, Sched.REM_NOTE);
		mDb.getDatabase().execSQL("DELETE FROM notes WHERE id IN " + strids);
    }


    /**
     * Card creation
     * ***********************************************************************************************
     */

    /**
     * @return (active), non-empty templates.
     */
    private JSONObject[] findTemplates(Note note) {
    	ArrayList<JSONObject> ok = new ArrayList<JSONObject>();
    	JSONObject model = note.model();
    	int[] avail = mModels.availOrds(model, Utils.joinFields(note.getFields()));
//    	for (Map.Entry<Integer, JSONObject> t : model.getTemplates().entrySet()) {
    		// TODO:
//    		JSONObject template = t.getValue();
//    		try {
//				if (template.getString("actv").toLowerCase().equals("true") || !checkActive) {
//					QAData data = new QAData(1, 1, model.getId(), 1, template.getInt("ord"), "", fact.joinedFields());
//					HashMap<String, String> now = _renderQA(model, null, data);
//					data.mFields = Utils.joinFields(new String[fact.getFields().length]);
//					HashMap<String, String> empty = _renderQA(model, null, data);
//					if (now.get("q").equals(empty.get("q"))) {
//						continue;
//					}
//					if (!template.getString("emptyAns").toLowerCase().toLowerCase().equals("true")) {
//						if (now.get("a").equals(empty.get("a"))) {
//							continue;
//						}    				
//					}
//					ok.add(template);
//				}
//			} catch (JSONException e) {
//				throw new RuntimeException(e);
//			}
//    	}
    	return (JSONObject[]) ok.toArray(new JSONObject[ok.size()]);
    }


    /**
     * Generate cards for non-empty templates, return ids to remove.
     */
    public int[] genCards(int[] nids) {
    	// build map of (nid,ord) so we don't create dupes
    	String snids = Utils.ids2str(nids);
    	// TODO:
//    	rem = new int[];
//    	return rem;
    	return null;
    }
    

    // previewCards


    /**
     * Create a new card.
     */
    private Card _newCard(Note note, JSONObject template, int due) {
    	return _newCard(note, template, due, true);
    }
    private Card _newCard(Note note, JSONObject template, int due, boolean flush) {
    	Card card = new Card(this);
    	card.setNid(note.getId());
    	try {
			card.setOrd(template.getInt("ord"));
			int did = template.getInt("did");
	    	card.setDid(did != 0 ? did : note.getDid());
		} catch (JSONException e) {
			new RuntimeException(e);
		}
    	card.setDue(_dueForDid(card.getDid(), due));
    	if (flush) {
    		card.flush();
    	}
    	return card;
    }


    public int _dueForDid(long did, int due) {
    	JSONObject conf = mDecks.confForDid(did);
    	// in order due?
    	try {
    		if (conf.getJSONObject("new").getInt("order") == Sched.NEW_CARDS_DUE) {
    			return due;
    		} else {
    			// random mode; seed with note ts so all cards of this note get the same random number
    			Random r = new Random();
    			r.setSeed(due);
    			return r.nextInt(2^32) + 1;
    		}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Cards
     * ***********************************************************************************************
     */

    public boolean isEmpty() {
    	return mDb.queryScalar("SELECT 1 FROM cards LIMIT 1", false) == 0;
    }


    public int cardCount() {
    	return mDb.queryScalar("SELECT count() FROM cards");
    }


    /**
     * Bulk delete cards by ID.
     */
    public void remCards(long[] ids) {
    	if (ids.length == 0) {
    		return;
    	}
    	String sids = Utils.ids2str(ids);
    	ArrayList<Long> list = mDb.queryColumn(long.class, "SELECT nid FROM cards WHERE id in " + sids, 0);
    	long[] nids = new long[list.size()];
    	int i = 0;
    	for (long l : list) {
    		nids[i++] = l;
    	}
    	// remove cards
    	_logRem(ids, Sched.REM_CARD);
    	mDb.getDatabase().execSQL("DELETE FROM cards WHERE id IN " + sids);
        mDb.getDatabase().execSQL("DELETE FROM revlog WHERE cid IN " + sids);
        // then notes
    	list = mDb.queryColumn(long.class, "SELECT nid FROM notes WHERE id IN " + Utils.ids2str(nids) + " AND id NOT IN (SELECT nid FROM cards)", 0);
    	nids = new long[list.size()];
    	_remNotes(nids);
    }


    public void remEmptyCards(int[] ids) {
    	if (ids.length == 0) {
    		return;
    	}
    	// TODO
    }


    /**
     * Field checksums and sorting fields
     * ***********************************************************************************************
     */

    // _fieldData

    // updateFieldCache

    /**
     * Q/A generation
     * ***********************************************************************************************
     */

    public ArrayList<HashMap<String, String>> renderQA() {
    	return renderQA(null, "card");
    }
    public ArrayList<HashMap<String, String>> renderQA(int[] ids, String type) {
    	String where;
    	if (type.equals("card")) {
    		where = "AND c.id IN " + Utils.ids2str(ids);
    	} else if (type.equals("fact")) {
    		where = "AND f.id IN " + Utils.ids2str(ids);
    	} else if (type.equals("model")) {
    		where = "AND m.id IN " + Utils.ids2str(ids);
    	} else if (type.equals("all")) {
    		where = "";
    	} else {
    		throw new RuntimeException();
    	}
        ArrayList<HashMap<String, String>> result = new ArrayList<HashMap<String, String>>();
        for (Object[] row : _qaData(where)) {
        	result.add(_renderQA(row));
        }
        return result;
    }

    /**
     * Returns hash of id, question, answer.
     */
	public HashMap<String, String> _renderQA(Object[] data) {
		// data is [cid, nid, mid, did, ord, tags, flds]
		// unpack fields and create dict
    	String[] flist = Utils.splitFields((String)data[6]);
    	Map<String, String> fields = new HashMap<String, String>();
    	long modelId = (Long)data[2];
    	JSONObject model = mModels.get(modelId);
    	for (Map.Entry<String, Object[]> f : mModels.fieldMap(model).entrySet()) {
        	fields.put(f.getKey(), flist[(Integer) f.getValue()[0]]);
        }
        fields.put("Tags", (String)data[5]);
        try {
			fields.put("Type", (String)model.get("name"));
	        fields.put("Deck", mDecks.name((Long)data[3]));
            JSONObject template = model.getJSONArray("tmpls").getJSONObject((Integer)data[4]);
			fields.put("Card", template.getString("name"));
	        // render q & a
	        HashMap<String, String> d = new HashMap<String, String>();
	        d.put("id", Long.toString((Long) data[0]));
	        d.put("q", mModels.getCmpldTemplate(modelId, (Integer)data[4])[0].execute(fields));
	        d.put("a", mModels.getCmpldTemplate(modelId, (Integer)data[4])[1].execute(fields));
	        // TODO: runfilter
	        return d;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }


    /**
     * Return [cid, nid, mid, did, ord, tags, flds] db query
     */
    public ArrayList<Object[]> _qaData() {
    	return _qaData("");
    }
    public ArrayList<Object[]> _qaData(String where) {
        ArrayList<Object[]> data = new ArrayList<Object[]>();
    	Cursor cur = null;
    	try {
            cur = mDb.getDatabase().rawQuery("SELECT c.id, n.id, n.mid, c.did, c.ord, " +
            		 "n.tags, n.flds FROM cards c, notes n WHERE c.nid == n.id " + where, null);
            while (cur.moveToNext()) {
            	data.add(new Object[]{cur.getInt(0), cur.getInt(1), cur.getInt(2), cur.getInt(3), cur.getInt(4), cur.getString(5), cur.getString(6)});
            }
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return data;
    }
    
    /**
     * Finding cards
     * ***********************************************************************************************
     */

    // findcards
    // findreplace
    // findduplicates
    
    /**
     * Stats
     * ***********************************************************************************************
     */

    // cardstats
    // stats
    
    /**
     * Timeboxing
     * ***********************************************************************************************
     */

    public void startTimebox() {
    	mLastSessionStart = mSessionStartTime;
    	mSessionStartTime = Utils.now();
    	mSessionStartReps = mRepsToday;
    }


    public void stopTimebox() {
    	mSessionStartTime = 0;
    }


    public double timeboxStarted() {
    	return mSessionStartTime;
    }


    public boolean timeboxReached() {
    	if (mSessionStartTime == 0) {
    		// not started
    		return false;
    	}
    	if (mSessionTimeLimit != 0 && Utils.now() > (mSessionStartTime + mSessionTimeLimit)) {
    		return true;
    	}
    	if (mSessionRepLimit != 0 && mSessionRepLimit <= mRepsToday - mSessionStartReps) {
    		return true;
    	}
    	return false;
    }
    
    /**
     * Schedulers and cramming
     * ***********************************************************************************************
     */

    /**
     * True if scheduler changed.
     */
     public boolean stdSched() {
    	if (!mSched.getName().equals("std")) {
    		cleanup();
    		mSched = mStdSched;
    		return true;
    	} else {
    		return false;
    	}
    }

     public void cramDecks() {
     	cramDecks("mod DESC", 0, 0);
     }
     public void cramDecks(String order, int min, int max) {
     	stdSched();
     	// TODO
// 		mSched = new CramScheduler(this, order, min, max);
     }

     /**
      * Undo
      * ***********************************************************************************************
      */



     /**
      * [type, data]
      * type 1 = review; type 2 = checkpoint
      */
     public void clearUndo() {
//     	mUndoQueue.clear();
     }


     public boolean undoAvailable() {
     	return false;//!mUndoQueue.isEmpty();
     }


     public void undo() {
//     	if (mUndoQueue.getLast().mType == 1) {
//     		_undoReview();
//     	} else {
////     		_undoOp();
//     	}
     }
     
     public void markReview(Card card) {
//     	mUndoQueue.add(new UndoRow(1, new Card(this, card.getId())));
     }


     public void _undoReview() {
//     	Card card = mUndoQueue.removeLast().mCard;
//     	// write old data
//     	card.flushSched();
//     	// and delete revlog entry
//     	Cursor cur = null;
//     	int last = 0;
//     	try {
//             cur = getDB().getDatabase().rawQuery("SELECT time FROM revlog WHERE id = " + card.getId() + " ORDER BY time DESC LIMIT 1", null);
//             if (cur.moveToFirst()) {
//             	last = cur.getInt(0);
//             }
// 		} finally {
//             if (cur != null && !cur.isClosed()) {
//                 cur.close();
//             }
//         }
// 		getDB().getDatabase().execSQL("DELETE FROM revlog WHERE time = " + last);
     }

     
     /**
      * DB maintenance
      * ***********************************************************************************************
      */

     // fix integrity

     public long optimize() {
//     	File file = new File(mDeckPath);
// 		long size = file.length();
//     	commitToDB();
    	Log.i(AnkiDroidApp.TAG, "executing VACUUM statement");
        mDb.getDatabase().execSQL("VACUUM");
 		Log.i(AnkiDroidApp.TAG, "executing ANALYZE statement");
        mDb.getDatabase().execSQL("ANALYZE");
//         file = new File(mDeckPath);
//         size -= file.length();
        return 0;//size;
     }

    /**
     * Getters/Setters
     * ***********************************************************************************************
     */

    public AnkiDb getDb() {
    	return mDb;
    }
    

    public Decks getDecks() {
    	return mDecks;
    }
	
	
    public Models getModels() {
    	return mModels;
    }
	
	
    public JSONObject getConf() {
    	return mConf;
    }


    public int getScm() {
    	return mScm;
    }


    public int getUsn() {
    	return mUsn;
    }


    public Tags getTags() {
    	return mTags;
    }


    public int getCrt() {
    	return mCrt;
    }
   

    public Sched getSched() {
    	return mSched;
    }
    
    
    
    
//    
//    /**
//     * Models
//     * ***********************************************************************************************
//     */
//
//    public Model currentModel() {
//    	try {
//			return getModel(mConf.getInt("currentModelId"));
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//    }
//
//
//    public LinkedHashMap<Integer, Model> models() {
//        Cursor cursor = null;
//        LinkedHashMap<Integer, Model> mods = new LinkedHashMap<Integer, Model>();
//        try {
//            cursor = getDB().getDatabase().rawQuery("SELECT id FROM models", null);
//            while (cursor.moveToNext()) {
//            	int id = cursor.getInt(0);
//            	mods.put(id, getModel(id));
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//    	return mods;
//    }




    
 

	
	
	
	
	
	
	
	
	
	
	
	
	
	
}