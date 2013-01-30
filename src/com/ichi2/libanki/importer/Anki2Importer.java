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

import android.content.res.Resources;
import android.database.Cursor;
import android.util.Log;
import com.google.gson.stream.JsonReader;
import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.R;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class Anki2Importer {

	Collection mCol;
	ZipFile mZip;
	int mTotal;
	ArrayList<String> mLog;
	long mTs;
	String mDeckPrefix = null;
    DeckTask.ProgressCallback mProgress;
    Resources mResources;

	Collection mDst;
	Collection mSrc;
    String mDstMediaDir;

	HashMap<String, Object[]> mNotes;
	HashMap<String, HashMap<Integer, Long>> mCards;
	HashMap<Long, Long> mDecks;
	HashMap<Long, Long> mModelMap;
    HashMap<String, String> mChangedGuids;
    private HashMap<String, String> nameToNum;

    private static final int GUID = 1;
    private static final int MID = 2;

    private static final int MEDIAPICKLIMIT = 1024;

    private static final String CHECKMARK = "\u2714";

	public Anki2Importer (Collection col, String file, DeckTask.ProgressCallback progressCallback) throws IOException {
		mCol = col;
		mZip = new ZipFile(new File(file), ZipFile.OPEN_READ);
		mTotal = 0;
		mLog = new ArrayList<String>();
        mProgress = progressCallback;
        if (mProgress != null) {
            mResources = mProgress.getResources();
        }
        nameToNum = new HashMap<String, String>();
    }

    private void publishProgress(boolean unpacking, int notesDone, int cardsDone, boolean cleanup) {
        if (mProgress != null && mResources != null) {
            mProgress.publishProgress(new DeckTask.TaskData(mResources.getString(R.string.import_add_progress,
                    (unpacking ? CHECKMARK: "-"), notesDone, cardsDone, (cleanup ? CHECKMARK: "-"))));
        }
    }

    public int run() {
        publishProgress(false, 0, 0, false);
        try {
            // extract the deck from the zip file
            String tempDir = AnkiDroidApp.getCurrentAnkiDroidDirectory() + "/tmpzip";
            // from anki2.py
            String colFile = tempDir + "/collection.anki2";
            if (!Utils.unzipFiles(mZip, tempDir, new String[]{"collection.anki2", "media"}, null) ||
                    !(new File(colFile)).exists() || !Storage.Collection(colFile).validCollection()) {
                return -2;
            }

            // we need the media dict in advance, and we'll need a map of fname number to use during the import
            File mediaMapFile = new File(tempDir, "media");
            HashMap<String, String> numToName = new HashMap<String, String>();
            if (mediaMapFile.exists()) {
                JsonReader jr = new JsonReader(new FileReader(mediaMapFile));
                jr.beginObject();
                String name;
                String num;
                while (jr.hasNext()) {
                    num = jr.nextName();
                    name = jr.nextString();
                    nameToNum.put(name, num);
                    numToName.put(num, name);
                }
                jr.endObject();
                jr.close();
            }

            _prepareFiles(colFile);
            publishProgress(true, 0, 0, false);
            int cnt = -1;
            try {
                cnt = _import();
            } finally {
                // do not close collection but close only db (in order not to confuse access counting in storage.java
                AnkiDatabaseManager.closeDatabase(mSrc.getPath());
            }
            // import static media
            String mediaDir = mCol.getMedia().getDir();
            if (nameToNum.size() != 0) {
                for (Map.Entry<String, String> entry : nameToNum.entrySet()) {
                    String file = entry.getKey();
                    String c = entry.getValue();
                    if (!file.startsWith("_") && !file.startsWith("latex-")) {
                        continue;
                    }
                    File of = new File(mediaDir, file);
                    if (!of.exists()) {
                        Utils.unzipFiles(mZip, mediaDir, new String[]{c}, numToName);
                    }
                }
            }
            mZip.close();
            // delete tmp dir
            File dir = new File(tempDir);
            BackupManager.removeDir(dir);
            publishProgress(true, 100, 100, true);
            return cnt;
        } catch (RuntimeException e) {
            Log.e(AnkiDroidApp.TAG, "RuntimeException while importing ", e);
            return -1;
        } catch (IOException e) {
            Log.e(AnkiDroidApp.TAG, "IOException while importing ", e);
            return -1;
        }
    }

	private void _prepareFiles(String src) {
		mDst = mCol;
        mDstMediaDir = mDst.getMedia().getDir() + File.separator;
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
        publishProgress(true, 100, 100, false);
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
		// build guid -> (id,mod,mid) hash & map of existing note ids
		mNotes = new HashMap<String, Object[]>();
		HashMap<Long, Boolean> existing = new HashMap<Long, Boolean>();
        Cursor cursor = null;
        try {
            // "SELECT id, guid, mod, mid FROM notes"
            cursor = mDst.getDb().getDatabase().query("notes", new String[]{"id", "guid", "mod", "mid"}, null, null, null, null, null);
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
        // we may need to rewrite the guid if the model schemas don't match,
        // so we need to keep track of the changes for the card import stage
        mChangedGuids = new HashMap<String, String>();
        // iterate over source collection
        ArrayList<Object[]> add = new ArrayList<Object[]>();
        ArrayList<Long> dirty = new ArrayList<Long>();
        int usn = mDst.usn();
        int dupes = 0;
        try {
            // "SELECT * FROM notes"
            cursor = mSrc.getDb().getDatabase().query("notes",
                    new String[]{"id", "guid", "mid", "mod", "usn", "tags", "flds", "sfld", "csum", "flags", "data"},
                    null, null, null, null, null);
            int total = cursor.getCount();
            int i = 0;
            while (cursor.moveToNext()) {
            	Object[] note = new Object[]{cursor.getLong(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3), cursor.getInt(4), cursor.getString(5), cursor.getString(6), cursor.getString(7), cursor.getLong(8), cursor.getInt(9), cursor.getString(10)};
            	boolean shouldAdd = _uniquifyNote(note);
            	if (shouldAdd) {
            		// ensure id is unique
            		while (existing.containsKey(note[0])) {
            			note[0] = ((Long)note[0]) + 999;
            		}
            		existing.put((Long) note[0], true);
            		// bump usn
            		note[4] = usn;
            		// update media references in case of dupes
            		note[6] = _mungeMedia((Long) note[MID], (String) note[6]);
            		add.add(note);
            		dirty.add((Long) note[0]);
            		// note we have the added guid
            		mNotes.put((String) note[GUID], new Object[]{note[0], note[3], note[MID]});
            	} else {
            		dupes += 1;
//            		// update existing note - not yet tested; for post 2.0
//            		boolean newer = note[3] > mod;
//            		if (mAllowUpdate && _mid(mid) == mid && newer) {
//            			note[0] = localNid;
//            			note[4] = usn;
//            			add.add(note);
//            			dirty.add(note[0]);
//            		}
            	}
                ++i;
                publishProgress(true, i * 100 / total, 0, false);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (dupes != 0) {
        	// TODO: notify about dupes
        }
        // add to col
        mDst.getDb().executeMany("INSERT OR REPLACE INTO NOTES VALUES (?,?,?,?,?,?,?,?,?,?,?)", add);
        long[] dis = Utils.arrayList2array(dirty);
        mDst.updateFieldCache(dis);
        mDst.getTags().registerNotes(dis);
	}
	
	
	// determine if note is a duplicate, and adjust mid and/or guid as required
	// returns true if note should be added
	
	private boolean _uniquifyNote(Object[] note) {
		String origGuid = (String) note[GUID];
		long srcMid = (Long) note[MID];
		long dstMid = _mid(srcMid);
		// duplicate Schemas?
		if (srcMid == dstMid) {
			return !mNotes.containsKey(origGuid);
		}
		// differing schemas
		note[MID] = dstMid;
		if (!mNotes.containsKey(origGuid)) {
			return true;
		}
		// as the schemas differ and we already have a note with a different note type, this note needs a new guid
		while (true) {
			note[GUID] = Utils.incGuid((String)note[GUID]);
			mChangedGuids.put(origGuid, (String) note[GUID]);
			// if we don't have an existing guid, we can add
			if (!mNotes.containsKey((String)note[GUID])) {
				return true;
			}
			// if the existing guid shares the same mid, we can reuse
			if (dstMid == (Long) mNotes.get((String)note[GUID])[MID]) {
				return false;
			}
		}
	}


	/** Models */
	// Models in the two decks may share an ID but not a schema, so we need to
    // compare the field & template signature rather than just rely on ID. If
	// the schemas don't match, we increment the mid and try again, creating a
	// new model if necessary.

	/* Prepare index of schema hashes */
	private void _prepareModels() {
		mModelMap = new HashMap<Long, Long>();
	}
	
	/* Return local id for remote MID */
	private long _mid(long srcMid) {
		try {
			// already processed this mid?
			if (mModelMap.containsKey(srcMid)) {
				return mModelMap.get(srcMid);
			}
			long mid = srcMid;
			JSONObject srcModel = new JSONObject(Utils.jsonToString(mSrc.getModels().get(srcMid)));
			String srcScm = mSrc.getModels().scmhash(srcModel);
			while (true) {
				// missing from target col?
				if (!mDst.getModels().have(mid)) {
					// copy it over
					JSONObject model = new JSONObject(Utils.jsonToString(srcModel));
					model.put("id", mid);
                    model.put("mod", Utils.intNow());
                    model.put("usn", mCol.usn());
					mDst.getModels().update(model);
					break;
				}
				// there's an existing model; do the schemas match?
				JSONObject dstModel = new JSONObject(Utils.jsonToString(mDst.getModels().get(mid)));
				String dstScm = mDst.getModels().scmhash(dstModel);
				if (srcScm.equals(dstScm)) {
					// they do; we can reuse this mid
					break;
				}
				// as they don't match, try next id
				mid += 1;
			}
			mModelMap.put(srcMid, mid);
			return mid;
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
            // Manually create any parents so we can pull in descriptions
            String head = "";
            String[] parents = name.split("::");
            for (int i = 0; i < parents.length - 1; ++i) {
                if (head.length() > 0) {
                    head = head.concat("::");
                }
                head = head.concat(parents[i]);
                long idInSrc = mSrc.getDecks().id(head);
                _did(idInSrc);
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
			// save desc
			JSONObject deck = mDst.getDecks().get(newid);
			deck.put("desc", g.getString("desc"));
			mDst.getDecks().save(deck);
			// add to deck map and return
			mDecks.put(did, newid);
			return newid;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/** Cards */

	private int _importCards() {
		// build map of (guid, ord) -> cid and used id cache
		mCards = new HashMap<String, HashMap<Integer, Long>>();
		HashMap<Long, Boolean> existing = new HashMap<Long, Boolean>();
        Cursor cursor = null;
        try {
            // "SELECT f.guid, c.ord, c.id FROM cards c, notes f WHERE c.nid = f.id"
            cursor = mDst.getDb().getDatabase().query("cards c, notes f", new String[]{"f.guid", "c.ord", "c.id"},
                    "c.nid = f.id", null, null, null, null);
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
            cursor = mSrc.getDb().getDatabase().rawQuery(
                    "SELECT f.guid, f.mid, c.* FROM cards c, notes f WHERE c.nid = f.id", null);
            int total = cursor.getCount();
            int ci = 0;
            while (cursor.moveToNext()) {
            	Object[] card = new Object[]{cursor.getString(0), cursor.getLong(1),
            			cursor.getLong(2), cursor.getLong(3), cursor.getLong(4), 
            			cursor.getInt(5), cursor.getLong(6), cursor.getInt(7), 
            			cursor.getInt(8), cursor.getInt(9), cursor.getLong(10), 
            			cursor.getLong(11), cursor.getLong(12), cursor.getInt(13), 
            			cursor.getInt(14), cursor.getInt(15), cursor.getLong(16), 
            			cursor.getLong(17), cursor.getInt(18), cursor.getString(19) };
            	String guid = (String) card[0];
            	if (mChangedGuids.containsKey(guid)) {
            		guid = mChangedGuids.get(guid);
            	}
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
            	if ((Integer)card[7] == 2 || (Integer)card[7] == 3 || (Integer)card[6] == 2) {
            		card[8] = (Long) card[8] - aheadBy;
            	}
            	// if odid true, convert card from filtered to normal
            	if ((Long)card[15] != 0) {
            		// odid
            		card[15] = 0;
            		// odue
            		card[8] = card[14];
            		card[14] = 0;
            		// queue
            		if ((Integer)card[6] == 1) { // type
            			card[7] = 0;
            		} else {
            			card[7] = card[6];
            		}
            		// type
            		if ((Integer)card[6] == 1) {
            			card[6] = 0;
            		}
            	}
            	cards.add(card);
            	// we need to import revlog, rewriting card ids and bumping usn
            	Cursor cur2 = null;
                try {
//                    "SELECT * FROM revlog WHERE cid = ?"
                    cur2 = mDst.getDb().getDatabase().query("revlog",
                            new String[] {"id", "cid", "usn", "ease", "ivl", "lastIvl", "factor", "time", "type"},
                            "cid = ?", new String[]{Long.toString(scid)}, null, null, null);
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
                ++ci;
                publishProgress(true, 100, ci * 100 / total, false);
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

	private String _mungeMedia(long mid, String fields) {
		String[] fs = Utils.splitFields(fields);

        for (int i = 0; i < fs.length; ++i) {
            for (Pattern p : Media.fMediaRegexps) {
                Matcher m = p.matcher(fs[i]);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String fname = m.group(2);
                    BufferedInputStream srcData = _srcMediaData(fname);
                    BufferedInputStream dstData = _dstMediaData(fname);
                    if (srcData == null) {
                        // file was not in source, ignore
                        m.appendReplacement(sb, m.group(0));
                        continue;
                    }
                    // if model-local file exists from a previous import, use that
                    int extPos = fname.lastIndexOf(".");
                    if (extPos <= 0) {
                        extPos = fname.length();
                    }
                    String lname = String.format(Locale.US, "%s_%d%s", fname.substring(0, extPos), mid, fname.substring(extPos));
                    if (mDst.getMedia().have(lname)) {
                        m.appendReplacement(sb, m.group(0).replace(fname, lname));
                        continue;
                    } else if (dstData == null || compareMedia(srcData, dstData)) { // if missing or the same, pass unmodified
                        // need to copy?
                        if (dstData == null) {
                            _writeDstMedia(fname, srcData);
                        }
                        m.appendReplacement(sb, m.group(0));
                        continue;
                    }
                    // exists but does not match, so we need to dedupe
                    _writeDstMedia(lname, srcData);
                    m.appendReplacement(sb, m.group(0).replace(fname, lname));
                }
                m.appendTail(sb);
                fs[i] = sb.toString();
            }
        }
		return fields;
	}

    private boolean compareMedia(BufferedInputStream lhis, BufferedInputStream rhis) {
        byte[] lhbytes = _mediaPick(lhis);
        byte[] rhbytes = _mediaPick(rhis);
        boolean result = Arrays.equals(lhbytes, rhbytes);
        return result;
    }

    /**
     * Return the contents of the given input stream, limited to Anki2Importer.MEDIAPICKLIMIT bytes
     * This is only used for comparison of media files with the limited resources of mobile devices
     */
    byte[] _mediaPick(BufferedInputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(MEDIAPICKLIMIT * 2);
            byte[] buf = new byte[MEDIAPICKLIMIT];
            int readLen;
            int readSoFar = 0;
            is.mark(MEDIAPICKLIMIT * 2);
            while(true) {
                readLen = is.read(buf);
                baos.write(buf);
                if (readLen == -1) {
                    break;
                }
                readSoFar += readLen;
                if (readSoFar > MEDIAPICKLIMIT) {
                    break;
                }
            }
            is.reset();
            byte[] result = new byte[MEDIAPICKLIMIT];
            System.arraycopy(baos.toByteArray(), 0, result, 0, Math.min(baos.size(), MEDIAPICKLIMIT));
            return result;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private BufferedInputStream _mediaData(String fname, String dir) {
        try {
            return new BufferedInputStream(new FileInputStream(new File(dir, fname)), MEDIAPICKLIMIT * 2);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    /**
     * Data for FNAME in src collection.
     * @return A string containing the contents of fname, limited to Anki2Importer.MEDIAPICKLIMIT bytes
     */
    private BufferedInputStream _srcMediaData(String fname) {
        if (nameToNum.containsKey(fname)) {
            try {
                return new BufferedInputStream(mZip.getInputStream(mZip.getEntry(nameToNum.get(fname))));
            } catch (IOException e) {
                Log.e(AnkiDroidApp.TAG, "Could not extract media file " + fname + "from mZip file.");
            }
        }
        return null;
    }

    /**
     * Data for FNAME in src collection.
     * @return A string containing the contents of fname, limited to Anki2Importer.MEDIAPICKLIMIT bytes
     */
    private BufferedInputStream _dstMediaData(String fname) {
        return _mediaData(fname, mDst.getMedia().getDir());
    }

    private void _writeDstMedia(String fname, BufferedInputStream is) {
        try {
            Utils.writeToFile(is, mDstMediaDir + fname);
        } catch (IOException e) {
            // the user likely used subdirectories
            Log.e(AnkiDroidApp.TAG, String.format(Locale.US,
                    "Anki2Importer._writeDstMedia: error copying file to %s (%s), ignoring and continuing.", fname,
                    e.getMessage()));
        }
    }

	/** post-import cleanup */

	private void _postImport() {
		try {
			// make sure new position is correct
			mDst.getConf().put("nextPos", mDst.getDb().queryLongScalar("SELECT max(due) + 1 FROM cards WHERE type = 0", false));
			mDst.save();		
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}
}
