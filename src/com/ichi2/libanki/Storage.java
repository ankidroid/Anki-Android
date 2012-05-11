/***************************************************************************************
 * Copyright (c) 2011 Norbert Nagold <norbert.ngaold@gmail.com>                         *
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

import java.io.File;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;

import com.ichi2.anki.AnkiDatabaseManager;
import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;

public class Storage {
	String mPath;

	/* Open a new or existing collection. Path must be unicode */
	public static Collection Collection(String path) {
		assert path.endsWith(".anki2");
		File dbFile = new File(path);
        boolean create = !dbFile.exists();
        if (create) {
        	AnkiDroidApp.createDirectoryIfMissing(dbFile.getParentFile());
        }
        // connect
        AnkiDb db = AnkiDatabaseManager.getDatabase(path);
		int ver;
		if (create) {
			ver = _createDB(db);
		} else {
			ver = _upgradeSchema(db);			
		}
		db.execute("PRAGMA temp_store = memory");

		// LIBANKI: sync, journal_mode --> in AnkiDroid done in AnkiDb

		// add db to col and do any remaining upgrades
		Collection col = new Collection(db, path);
		if (ver < Collection.SCHEMA_VERSION) {
			_upgrade(col, ver);
		} else if (create) {
			// add in reverse order so basic is default
			Models.addClozeModel(col);
			Models.addBasicModel(col);
			col.save();
		}
		return col;
		
	}

	private static int _upgradeSchema(AnkiDb db) {
		int ver = db.queryScalar("SELECT ver FROM col");
		if (ver == Collection.SCHEMA_VERSION) {
			return ver;
		}
		// add odid to cards, edue->odue
		if (db.queryScalar("SELECT ver FROM col") == 1) {
			db.execute("ALTER TABLE cards RENAME TO cards2");
			_addSchema(db, false);
			db.execute("insert into cards select id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, edue, 0, flags, data from cards2");
			db.execute("DROP TABLE cards2");
			db.execute("UPDATE col SET var = 2");
			_updateIndices(db);
		}
		// remove did from notes
		if (db.queryScalar("SELECT ver FROM col") == 2) {
			db.execute("ALTER TABLE notes RENAME TO notes2");
			_addSchema(db, false);
			db.execute("insert into notes select id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data from notes2");
			db.execute("DROP TABLE notes2");
			db.execute("UPDATE col SET var = 3");
			_updateIndices(db);
		}
		return ver;
	}

	private static void _upgrade(Collection col, int ver) {
		try {
			if (ver < 3) {
				// new deck properties
				for (JSONObject d : col.getDecks().all()) {
					d.put("dyn", 0);
					d.put("collapsed", false);
					col.getDecks().save(d);
				}
			}
			if (ver < 4) {
				col.modSchema();
				ArrayList<JSONObject> clozes = new ArrayList<JSONObject> (); 
				for (JSONObject m : col.getModels().all()) {
					if (!m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt").contains("{{cloze:")) {
						m.put("type", Sched.MODEL_STD);
					} else {
						clozes.add(m);
					}
				}
				for (JSONObject m : clozes) {
					_upgradeClozeModel(col, m);
				}
				col.getDb().execute("UPDATE col SET ver = 4");
			}
			if (ver < 5) {
				col.getDb().execute("UPDATE cards SET odue = 0 WHERE queue = 2");
				col.getDb().execute("UPDATE col SET ver = 5");
			}
			if (ver < 6) {
				col.modSchema();
				for (JSONObject m : col.getModels().all()) {
					m.put("css", new JSONObject(Models.defaultModel).getString("css"));
					JSONArray ar = m.getJSONArray("tmpls");
					for (int i = 0; i < ar.length(); i++) {
						JSONObject t = ar.getJSONObject(i);
						m.put("css", m.getString("css") + "\n" + t.getString("css").replace(".card ", ".card" + t.getInt("ord") + 1));
						t.remove("css");							
					}
					col.getModels().save(m);
				}
				col.getDb().execute("UPDATE col SET ver = 6");
			}
			if (ver < 7) {
				col.modSchema();
				col.getDb().execute("UPDATE cards SET odue = 0 WHERE (type = 1 OR queue = 2) AND NOT odid");
				col.getDb().execute("UPDATE col SET ver = 7");				
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private static void _upgradeClozeModel(Collection col, JSONObject m) {
		try {
			m.put("type", Sched.MODEL_CLOZE);
			// convert first template
			JSONObject t = m.getJSONArray("tmpls").getJSONObject(0);
			for (String type : new String[]{"qfmt", "afmt"}) {
				t.put(type, t.getString(type).replaceAll("{{cloze:1:(.+?)}}", "{{cloze:$1}}"));
			}
			t.put("name", "Cloze");
			// delete non-cloze cards for the model
			JSONArray ja = m.getJSONArray("tmpls");
			ArrayList<JSONObject> rem = new ArrayList<JSONObject>();
			for (int i = 1; i < ja.length(); i++) {
				JSONObject ta = ja.getJSONObject(i);
				if (!ta.getString("afmt").contains("{{cloze:")) {
					rem.add(ta);
				}
			}
			for (JSONObject r : rem) {
				// TODO: write remtemplate
				col.getModels().remTemplate(m, r);
			}
			JSONArray newArray = new JSONArray();
			newArray.put(ja.get(0));
			m.put("tmpls", newArray);
			col.getModels()._updateTemplOrds(m);
			col.getModels().save(m);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		
	}

	private static int _createDB(AnkiDb db) {
		db.execute("PRAGMA page_size = 4096");
		db.execute("PRAGMA legacy_file_format = 0");
		db.execute("VACUUM");
		_addSchema(db);
		db.execute("ANALYZE");
		return Collection.SCHEMA_VERSION;
	}

	private static void _addSchema(AnkiDb db) {
		_addSchema(db, true);
	}
	private static void _addSchema(AnkiDb db, boolean setColConf) {
		db.execute("create table if not exists col ( "+
    "id              integer primary key, "+
    "crt             integer not null,"+
    "mod             integer not null,"+
    "scm             integer not null,"+
    "ver             integer not null,"+
    "dty             integer not null,"+
    "usn             integer not null,"+
    "ls              integer not null,"+
    "conf            text not null,"+
    "models          text not null,"+
    "decks           text not null,"+
    "dconf           text not null,"+
    "tags            text not null"+
");");
		db.execute("create table if not exists notes ("+
 "   id              integer primary key,"+
  "  guid            text not null,"+
   " mid             integer not null,"+
   " mod             integer not null,"+
   " usn             integer not null,"+
   " tags            text not null,"+
   " flds            text not null,"+
   " sfld            integer not null,"+
   " csum            integer not null,"+
   " flags           integer not null,"+
   " data            text not null"+
");");
		db.execute("create table if not exists cards ("+
 "   id              integer primary key,"+
  "  nid             integer not null,"+
  "  did             integer not null,"+
  "  ord             integer not null,"+
  "  mod             integer not null,"+
   " usn             integer not null,"+
   " type            integer not null,"+
   " queue           integer not null,"+
"    due             integer not null,"+
 "   ivl             integer not null,"+
  "  factor          integer not null,"+
   " reps            integer not null,"+
 "   lapses          integer not null,"+
 "   left            integer not null,"+
 "   odue            integer not null,"+
 "   odid            integer not null,"+
 "   flags           integer not null,"+
 "   data            text not null"+
");");
		db.execute("create table if not exists revlog ("+
 "   id              integer primary key,"+
 "   cid             integer not null,"+
 "   usn             integer not null,"+
 "   ease            integer not null,"+
 "   ivl             integer not null,"+
 "   lastIvl         integer not null,"+
 "   factor          integer not null,"+
 "   time            integer not null,"+
 "   type            integer not null"+
");");
		db.execute("create table if not exists graves ("+
"    usn             integer not null,"+
"    oid             integer not null,"+
"    type            integer not null"+
")");
		db.execute("INSERT OR IGNORE INTO col VALUES(1,0,0," + Collection.SCHEMA_VERSION + "," + Utils.intNow(1000) + ",0,0,0,\'\',\'{}\',\'\',\'\',\'{}\')");
		if (setColConf) {
			_setColVars(db);
		}
	}

	private static void _setColVars(AnkiDb db) {
		try {
			JSONObject g = new JSONObject(Decks.defaultDeck);
			g.put("id", 1);
			g.put("name", "Default");
			g.put("conf", 1);
			g.put("mod", Utils.intNow());
			JSONObject gc = new JSONObject(Decks.defaultConf);
			gc.put("id", 1);
			JSONObject ag = new JSONObject();
			ag.put("1", g);
			JSONObject agc = new JSONObject();
			agc.put("1", gc);
			ContentValues values = new ContentValues();
			values.put("conf", Collection.defaultConf);
			values.put("decks", ag.toString());
			values.put("dconf", agc.toString());
			db.update("col", values);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private static void _updateIndices(AnkiDb db) {
		db.execute("create index if not exists ix_notes_usn on notes (usn);");
		db.execute("create index if not exists ix_cards_usn on cards (usn);");
		db.execute("create index if not exists ix_revlog_usn on revlog (usn);");
		db.execute("create index if not exists ix_cards_nid on cards (nid);");
		db.execute("create index if not exists ix_cards_sched on cards (did, queue, due);");
		db.execute("create index if not exists ix_revlog_cid on revlog (cid);");
		db.execute("create index if not exists ix_notes_csum on notes (csum);)");
	}
	/* Upgrading 
	 * *************************************************************/

//	public void upgrade(String path) {
////		mPath = path;
////		_openDB(path);
////		upgradeSchema();
////		_openCol();
////		_upgradeRest();
////		return mCol;
//	}

	/* Integrity checking
	 * *************************************************************/

//	public boolean check(String path) {
//		AnkiDb db = AnkiDatabaseManager.getDatabase(path);
//		// corrupt?
//		if (!db.queryString("PRAGMA integrity_check").equalsIgnoreCase("ok")) {
//			return false;
//		}
//		// old version?
//		if (db.queryScalar("SELECT version FROM decks") < 65) {
//			return false;
//		}
//		// ensure we have indices for checks below
//		// TODO
//		return true;
//	}

	/* DB/Deck opening
	 * *************************************************************/

	/* Schema upgrade
	 * *************************************************************/

	/* Field munging
	 * *************************************************************/

	/* Template upgrading
	 * *************************************************************/

	/* Media references
	 * *************************************************************/

	/* Inactive templates
	 * *************************************************************/

	/* Conditional templates
	 * *************************************************************/

	/* New due times
	 * *************************************************************/

	/* Post-schema upgrade
	 * *************************************************************/

}
//
//	public static final int DECK_VERSION = 100;
//
//    private static void moveTable(Deck deck, String table) {
//        Cursor cursor = null;
//        String sql = "";
//        try {
//            cursor = deck.getDB().getDatabase().rawQuery(
//                    "SELECT sql FROM sqlite_master WHERE name = \'" + table + "\'", null);
//            while (cursor.moveToNext()) {
//            	sql = cursor.getString(0);
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        deck.getDB().getDatabase().execSQL(sql.replace("TABLE " + table, "TEMPORARY TABLE " + table + "2"));
//        deck.getDB().getDatabase().execSQL("INSERT INTO " + table + "2 SELECT * FROM " + table);
//        deck.getDB().getDatabase().execSQL("DROP TABLE " + table);
//    }
//
//
//    public static int upgradeSchema(Deck deck) {
//    	int ver = 0;
//        try {
//        	ver = (int) deck.getDB().queryScalar("SELECT version FROM deck LIMIT 1");
//        } catch (SQLException e) {
//        	ver = (int) deck.getDB().queryScalar("SELECT version FROM decks LIMIT 1");       	
//        }
//        if (ver < 65) {
//            // Unsupported version
//            deck.upgradeNotes.add(com.ichi2.anki.R.string.deck_upgrade_too_old_version);
//        }
//    	if (ver < 99) {
//           	// fields
//           	deck.getDB().getDatabase().execSQL("ALTER TABLE FIELDS ADD COLUMN ckssum TEXT " +
//            			"NOT NULL DEFAULT \'\'");
//    		// cards
//    		moveTable(deck, "cards");
//        	deck.getDB().getDatabase().execSQL("INSERT INTO cards SELECT id, factId, cardModelId, created, modified, " +
//        			"question, answer, 0, ordinal, 0, relativeDelay, type, lastInterval, interval, " +
//        			"due, factor, reps, successive, noCount FROM cards2");
//        	deck.getDB().getDatabase().execSQL("DROP TABLE cards2");
//        	// tags
//        	//...
//        	
//        	// facts
//        	moveTable(deck, "facts");
//        	// move data across
//        	deck.getDB().getDatabase().execSQL("INSERT OR IGNORE INTO facts id, modelID, created, modified, tags" +
//        			"spaceUntil FROM facts2");
//        	deck.getDB().getDatabase().execSQL("DROP TABLE facts2");
//        	// media
//        	// ...
//        	
//        	//deck
//        	deck.getDB().getDatabase().execSQL("INSERT INTO deck SELECT id, created, modified, 0, 99, currentModelId, " +
//        			"IFNULL(syncName, \"\"), lastSync, utcOffset, newCardOrder, newCardSpacing, newCardsPerDay, revCardOrder, " +
//        					"600, sessionRepLimit, sessionTimeLimit, 1, 16, \'\', \'\', \'\', \'\' FROM decks");
//        	deck.getDB().getDatabase().execSQL("DROP TABLE decks");
//        	// models
//        	moveTable(deck, "models");
//        	deck.getDB().getDatabase().execSQL("INSERT OR IGNORE INTO models SELECT id, created, modified, name, " +
//        			"\'[0.5, 3, 10]\', \'[1, 7, 4]\', \'[0.5, 3, 10]\', \'[1, 7, 4]\', 0, 2.5 FROM models2");
//           	deck.getDB().getDatabase().execSQL("DROP TABLE models2");
//    	}
//    	return ver;
//    }
//
//
//    /**
//     * Add indices to the DB.
//     */
//    private static void updateIndices(Deck deck) {
//        // due counts, failed card queue
//        deck.getDB().getDatabase().execSQL(
//                "CREATE INDEX IF NOT EXISTS ix_cards_queueDue ON cards (queue, " + "due, factId)");
//        // counting cards of a given type
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_type ON cards (type)");
//        // sync summaries
//        // Index on modified, to speed up sync summaries
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_modified ON cards (modified)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_facts_modified ON facts (modified)");
//        // Card spacing
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cards_factId ON cards (factId)");
//        // Fields
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_factId ON fields (factId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_fieldModelId ON fields (fieldModelId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_fields_value ON fields (value)");
//        // Media
//        deck.getDB().getDatabase().execSQL("CREATE UNIQUE INDEX IF NOT EXISTS ix_media_filename ON media (filename)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_media_originalPath ON media (originalPath)");
//        // Deletion tracking
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardsDeleted_cardId ON cardsDeleted (cardId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_modelsDeleted_modelId ON modelsDeleted (modelId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_factsDeleted_factId ON factsDeleted (factId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_mediaDeleted_factId ON mediaDeleted (mediaId)");
//        // Tags
//        String txt = "CREATE UNIQUE INDEX IF NOT EXISTS ix_tags_tag on tags (tag)";
//        try {
//        	deck.getDB().getDatabase().execSQL(txt);
//        } catch (SQLException e) {
//        	deck.getDB().getDatabase().execSQL("DELETE FROM tags WHERE EXISTS (SELECT 1 FROM tags t2 " +
//                    "WHERE tags.tag = t2.tag AND tags.rowid > t2.rowid)");
//        	deck.getDB().getDatabase().execSQL(txt);
//        }
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_tagCard ON cardTags (tagId, cardId)");
//        deck.getDB().getDatabase().execSQL("CREATE INDEX IF NOT EXISTS ix_cardTags_cardId ON cardTags (cardId)");
//    }
//
//    /**
//     * Upgrade deck to latest version. Any comments resulting from the upgrade, should be stored in upgradeNotes, as
//     * R.string.id, successful or not. The idea is to have Deck.java generate the notes from upgrading and not the UI.
//     * Still we need access to a Resources object and it's messy to pass that in openDeck. Instead we store the ids for
//     * the messages and make a separate call from the UI to static upgradeNotesToMessages in order to properly translate
//     * the IDs to messages for viewing. We shouldn't do this directly from the UI, as the messages contain %s variables
//     * that need to be populated from deck values, and it's better to contain the libanki logic to the relevant classes.
//     *
//     * @return True if the upgrade is supported, false if the upgrade needs to be performed by Anki Desktop
//     */
//    public static boolean upgradeDeck(Deck deck) {
//        // Oldest versions in existence are 31 as of 11/07/2010
//        // We support upgrading from 39 and up.
//        // Unsupported are about 135 decks, missing about 6% as of 11/07/2010
//        //
//        double oldmod = deck.getModified();
//        int version = upgradeSchema(deck);
//
//        deck.upgradeNotes = new ArrayList<Integer>();
//        if (version < 100) {
//            // update dynamic indices given we don't use priority anymore
//            String[] oldDynamicIndices = { "intervalDesc", "intervalAsc", "randomOrder", "dueAsc", "dueDesc" };
//            for (String d : oldDynamicIndices) {
//                deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_" + d + "2");
//                deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_" + d);
//            }
//            deck.updateDynamicIndices();
//        	// remove the expensive value cache
//            deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_fields_value");
////            deck.updateAllFieldChecksums();
//        	// this was only used for calculating average factor
//            deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_factor");
//    	  	// remove stats, as it's all in the revlog now
//        	deck.getDB().getDatabase().execSQL("DROP TABLE IF EXISTS stats");
//    	  	// migrate revlog data to new table
//        	deck.getDB().getDatabase().execSQL("INSERT INTO revlog SELECT CAST(time * 1000 AS INT), cardId, ease, reps, lastInterval, nextInterval, nextFactor, CAST(MIN(thinkingTime, 60) * 1000 AS INT), 0 FROM reviewHistory");
//        	deck.getDB().getDatabase().execSQL("DROP TABLE reviewHistory");
//        	// convert old ease0 into ease1
//        	deck.getDB().getDatabase().execSQL("UPDATE revlog SET ease = 1 WHERE ease = 0");
//        	// remove priority index
//        	deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_cards_priority");
//        	// suspended cards don't use ranges anymore
//        	deck.getDB().getDatabase().execSQL("UPDATE cards SET queue -1 WHERE queue BETWEEN -3 AND -1");
//        	deck.getDB().getDatabase().execSQL("UPDATE cards SET queue -2 WHERE queue BETWEEN 3 AND 5");
//        	deck.getDB().getDatabase().execSQL("UPDATE cards SET queue -3 WHERE queue BETWEEN 6 AND 8");
//        	// don't need an index on fieldModelId
//        	deck.getDB().getDatabase().execSQL("DROP INDEX IF EXISTS ix_fields_fieldModelId");
//    		// update schema time
//        	deck.getDB().getDatabase().execSQL(String.format(Utils.ENGLISH_LOCALE, "UPDATE deck SET schemaMod = %f", Utils.now()));
//        	// finally, update indices & optimize 
//        	updateIndices(deck);
//            deck.getDB().getDatabase().execSQL("ANALYZE");
//            deck.setVersion(100);
//            deck.commitToDB();
//    	}
//        assert (deck.getModified() == oldmod);
//        return true;
//    }
//
//
//    public static String upgradeNotesToMessages(Deck deck, Resources res) {
//        String notes = "";
//        for (Integer note : deck.upgradeNotes) {
//            notes = notes.concat(res.getString(note.intValue()) + "\n");
//        }
//        return notes;
//    }
//}
