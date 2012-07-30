/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.util.Log;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;

public class Anki2Importer {

	Collection mCol;
	String mFile;
	int mTotal;
	ArrayList<String> mLog;
	long mTs;
	boolean mNeedCards = true;
	String mDeckPrefix = null;

	Collection mDst;
	Collection mSrc;

	HashMap<String, Object[]> mNotes;
	HashMap<String, HashMap<Integer, Long>> mCards;
	HashMap<Long, Long> mDecks;
	HashMap<Long, Long> mModelMap;

	public Anki2Importer (Collection col, String file) {
		mCol = col;
		mFile = file;
		mTotal = 0;
		mLog = new ArrayList<String>();
	}


	public int run() {
		try	{
			// extract the deck from the zip file
			String fileDir = AnkiDroidApp.getCurrentAnkiDroidDirectory(AnkiDroidApp.getInstance().getBaseContext()) + "/tmpzip";
			Utils.unzip(mFile, fileDir);
			// from anki2.py
			_prepareFiles(fileDir + "/collection.anki2");
			int cnt = -1;
			try {
				cnt = _import();
			} finally {
				// do not close collection but close only db (in order not to confuse access counting in storage.java
				AnkiDatabaseManager.closeDatabase(mSrc.getPath());
			}
			// import media
			JSONObject media = new JSONObject(Utils.convertStreamToString(new FileInputStream(fileDir + "/media")));
			String mediaDir = mCol.getMedia().getDir() + "/";
			JSONArray names = media.names();
			if (names != null) {
				for (int i = 0; i < names.length(); i++) {
					String n = names.getString(i);
					String o = media.getString(n);
					File of = new File(mediaDir + o);
					if (!of.exists()) {
						File newFile = new File(fileDir + "/" + n);
						newFile.renameTo(of);
					}
				}
			}
			// delete tmp dir
			File dir = new File(fileDir);
			BackupManager.removeDir(dir);
			return cnt;
         } catch (IOException e) {
        	 return -1;
	     } catch (JSONException e) {
	    	 return -1;
		}
	}

	private void _prepareFiles(String src) {
		mDst = mCol;
		mSrc = Storage.Collection(src);
	}

	private int _import() {
		mDecks = new HashMap<Long, Long>();
		if (mDeckPrefix != null) {
			long id = mDst.getDecks().id(mDeckPrefix);
			mDst.getDecks().select(id);
		}
		Log.i(AnkiDroidApp.TAG, "Import - preparing");
		_prepareTS();
		_prepareModels();
		Log.i(AnkiDroidApp.TAG, "Import - importing notes");
		_importNotes();
		Log.i(AnkiDroidApp.TAG, "Import - importing cards");
		int cnt = _importCards();
//		_importMedia();
		Log.i(AnkiDroidApp.TAG, "Import - finishing");
		_postImport();
		// LIBANKI: vacuum and analyze is done in DeckTask
		return cnt;
	}

	/** timestamps */

	private void _prepareTS() {
		mTs = Utils.maxID(mDst.getDb());
	}

	private long ts() {
		mTs++;
		return mTs;
	}

	/** Notes */
	// should note new for wizard

	private void _importNotes() {
		// build guid -> (id, mod, mid) hash
		mNotes = new HashMap<String, Object[]>();
		HashMap<Long, Boolean> existing = new HashMap<Long, Boolean>();
        Cursor cursor = null;
        try {
            cursor = mDst.getDb().getDatabase().rawQuery("SELECT id, guid, mod, mid FROM notes", null);
            while (cursor.moveToNext()) {
            	long id = cursor.getLong(0);
            	mNotes.put(cursor.getString(1), new Object[]{id, cursor.getLong(2), cursor.getLong(3)});
            	existing.put(id, true);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // iterate over source collection
        ArrayList<Object[]> add = new ArrayList<Object[]>();
        ArrayList<Long> dirty = new ArrayList<Long>();
        int usn = mDst.usn();
        try {
            cursor = mSrc.getDb().getDatabase().rawQuery("SELECT * FROM notes", null);
            while (cursor.moveToNext()) {
            	Object[] note = new Object[]{cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), cursor.getInt(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getLong(8), cursor.getInt(9), cursor.getString(10)};
            	// missing from local col?
            	if (!mNotes.containsKey(note[1])) {
            		// get corresponding local model
            		long lmid = _mid((Long) note[2]);
            		// ensure id is unique
            		while (existing.containsKey(note[0])) {
            			note[0] = ((Long)note[0]) + 999;
            		}
            		existing.put((Long) note[0], true);
            		// rewrite internal ids, models etc.
            		note[2] = lmid;
            		note[3] = Utils.intNow();
            		note[4] = usn;
            		add.add(note);
            		dirty.add((Long) note[0]);
            		// note we have the added note
            		mNotes.put((String) note[1], new Object[]{note[0], note[3], note[2]});
            	} else {
            		// not yet implemented
            	}
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        mDst.getDb().executeMany("INSERT OR REPLACE INTO NOTES VALUES (?,?,?,?,?,?,?,?,?,?,?)", add);
        long[] dis = Utils.arrayList2array(dirty);
        mDst.updateFieldCache(dis);
        mDst.getTags().registerNotes(dis);
	}
	
	/** Models */
	// Models in the two decks may share an ID but not a schema, so we need to
    // compare the field & template signature rather than just rely on ID. If
    // we created a new model on a conflict then multiple imports would end up
    // with lots of models however, so we store a list of "alternate versions"
    // of a model in the model, so that importing a model is idempotent.

	/* Prepare index of schema hashes */
	private void _prepareModels() {
		mModelMap = new HashMap<Long, Long>();
	}
	
	/* Return local id for remote MID */
	private long _mid(long mid) {
		try {
			// already processed this mid?
			if (mModelMap.containsKey(mid)) {
				return mModelMap.get(mid);
			}
			JSONObject src = new JSONObject(mSrc.getModels().get(mid).toString());
			if (!mNeedCards) {
				src.put("needWizard", 1);
			}
			// if it doesn't exist, we'll copy it over, preserving id
			if (!mDst.getModels().have(mid)) {
				mDst.getModels().update(src);
				// if we're importing with a prefix, make the model default to it
				if (mDeckPrefix != null) {
					src.put("did", mDst.getDecks().current().getLong("id"));
					// and give it a unique name if it's not a shared deck
					if (!mDeckPrefix.equals("shared")) {
						src.put("name", src.getString("name") + " (" + mDeckPrefix + ")");
					}
				}
				// make sure to bump usn
				mDst.getModels().save(src);
				mModelMap.put(mid, mid);
				return mid;
			}
			// if it does exist, do the schema match?
			JSONObject dst = mDst.getModels().get(mid);
			long shash = mSrc.getModels().scmhash(src);
			long dhash = mSrc.getModels().scmhash(dst);
			if (shash == dhash) {
				// reuse without modification
				mModelMap.put(mid, mid);
				return mid;
			}
			// try any alternative versions
			JSONArray vers = dst.getJSONArray("vers");
			for (int i = 0; i < vers.length(); i++) {
				JSONObject m = mDst.getModels().get(vers.getLong(i));
				if (mDst.getModels().scmhash(m) == shash) {
					// valid alternate found; use that
					mModelMap.put(mid, m.getLong("id"));
					return m.getLong("id");
				}
			}
			// need to add a new alternate version, with new id
			mDst.getModels().add(src);
			vers.put(src.getLong("id"));
			dst.put("vers", vers);
			mDst.getModels().save(dst);
			return src.getLong("id");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/** Decks */

	/* Given did in src col, return local id */
	private long _did(long did) {
		try {
			// already converted?
			if (mDecks.containsKey(did)) {
				return mDecks.get(did);
			}
			// get the name in src
			JSONObject g = mSrc.getDecks().get(did);
			String name = g.getString("name");
			// if there's a prefix, replace the top level deck
			if (mDeckPrefix != null) {
				String[] tmpname = name.split("::");
				name = mDeckPrefix;
				if (tmpname.length > 1) {
					for (int i = 0; i < tmpname.length - 2; i++) {
						name += "::" + tmpname[i + 1];
					}
				}
			}
			// create in local
			long newid = mDst.getDecks().id(name);
			// pull conf over
			if (g.has("conf") && g.getLong("conf") != 1) {
				mDst.getDecks().updateConf(mSrc.getDecks().getConf(g.getLong("conf")));
				JSONObject g2 = mDst.getDecks().get(newid);
				g2.put("conf", g.getLong("conf"));
				mDst.getDecks().save(g2);
			}
			// add to deck map and return
			mDecks.put(did, newid);
			return newid;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/** Cards */

	private int _importCards() {
		if (!mNeedCards) {
			return 0;
		}
		// build map of (guid, ord) -> cid and used id cache
		mCards = new HashMap<String, HashMap<Integer, Long>>();
		HashMap<Long, Boolean> existing = new HashMap<Long, Boolean>();
        Cursor cursor = null;
        try {
            cursor = mDst.getDb().getDatabase().rawQuery("SELECT f.guid, c.ord, c.id FROM cards c, notes f WHERE c.nid = f.id", null);
            while (cursor.moveToNext()) {
            	long cid = cursor.getLong(2);
            	existing.put(cid, true);
            	String guid = cursor.getString(0);
            	int ord = cursor.getInt(1);
            	if (mCards.containsKey(guid)) {
            		mCards.get(guid).put(ord, cid);
            	} else {
            		HashMap<Integer, Long> map = new HashMap<Integer, Long>();
            		map.put(ord, cid);
            		mCards.put(guid, map);
            	}
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // loop through src
        ArrayList<Object[]> cards = new ArrayList<Object[]>();
        ArrayList<Object[]> revlog = new ArrayList<Object[]>();
        int cnt = 0;
        int usn = mDst.usn();
        long aheadBy = mSrc.getSched().getToday() - mDst.getSched().getToday();
        try {
            cursor = mSrc.getDb().getDatabase().rawQuery("SELECT f.guid, f.mid, c.* FROM cards c, notes f WHERE c.nid = f.id", null);
            while (cursor.moveToNext()) {
            	Object[] card = new Object[]{cursor.getString(0), cursor.getLong(1),
            			cursor.getLong(2), cursor.getLong(3), cursor.getLong(4), 
            			cursor.getInt(5), cursor.getLong(6), cursor.getInt(7), 
            			cursor.getInt(8), cursor.getInt(9), cursor.getLong(10), 
            			cursor.getLong(11), cursor.getLong(12), cursor.getInt(13), 
            			cursor.getInt(14), cursor.getInt(15), cursor.getLong(16), 
            			cursor.getLong(17), cursor.getInt(18), cursor.getString(19) };
            	String guid = (String) card[0];
            	// does the card's note exist in dst col?
            	if (!mNotes.containsKey(guid)) {
            		continue;
            	}
            	Object[] dnid = mNotes.get(guid);
            	// does the card already exist in the dst col?
            	int ord = (Integer) card[5];
            	if (mCards.containsKey(guid) && mCards.get(guid).containsKey(ord)) {
            		// fixme: in future, could update if newer mod time
            		continue;
            	}
            	// doesn't exist. strip off note info, and save src id for later
            	Object[] oc = card;
            	card = new Object[oc.length - 2];
            	for (int i = 0; i < card.length; i++) {
            		card[i] = oc[i + 2];
            	}
            	long scid = (Long) card[0];
            	// ensure the card id is unique
            	while (existing.containsKey(card[0])) {
            		card[0] = (Long) card[0] + 999;
            	}
            	existing.put((Long) card[0], true);
            	// update cid, nid, etc
            	card[1] = mNotes.get(guid)[0];
            	card[2] = _did((Long) card[2]);
            	card[4] = Utils.intNow();
            	card[5] = usn;
            	// review cards have a due date relative to collection
            	if ((Integer)card[7] == 2 || (Integer)card[7] == 3) {
            		card[8] = (Long) card[8] - aheadBy;
            	}
            	cards.add(card);
            	// we need to import revlog, rewriting card ids and bumping usn
            	Cursor cur2 = null;
                try {
                    cur2 = mDst.getDb().getDatabase().rawQuery("SELECT * FROM revlog WHERE cid = " + scid, null);
                    while (cur2.moveToNext()) {
                    	 Object[] rev = new Object[]{cur2.getLong(0), cur2.getLong(1),
                    			cur2.getInt(2), cur2.getInt(3), cur2.getLong(4),
                    			cur2.getLong(5), cur2.getLong(6), cur2.getLong(7),
                    			cur2.getInt(8)};
                    	 rev[1] = card[0];
                    	 rev[2] = mDst.usn();
                    	 revlog.add(rev);
                    }
                } finally {
                    if (cur2 != null) {
                        cur2.close();
                    }
                }
                cnt += 1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // apply
        mDst.getDb().executeMany("INSERT OR IGNORE INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", cards);
        mDst.getDb().executeMany("INSERT OR IGNORE INTO revlog VALUES (?,?,?,?,?,?,?,?,?)", revlog);
        return cnt;
	}

	/** post-import cleanup */

	private void _postImport() {
		try {
			if (mNeedCards) {
				// make sure new position is correct
				mDst.getConf().put("nextPos", mDst.getDb().queryLongScalar("SELECT max(due) + 1 FROM cards WHERE type = 0", false));
			} else {
				// newly added models will have been flagged with needWizard=1; we
				// need to mark reused models with needWizard=2 so the new cards
				// can be generated
				for (long mid : mModelMap.values()) {
					JSONObject m = mDst.getModels().get(mid);
					if (!m.has("needWizard") || m.getInt("needWizard") == 0) {
						m.put("needWizard", 2);
						mDst.getModels().save(m);
					}
				}
			}
			mDst.save();		
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}
}
