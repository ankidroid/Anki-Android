///***************************************************************************************
// * Copyright (c) 2011 Norbert Nagold <norbert.ngaold@gmail.com>                         *
// *                                                                                      *
// * This program is free software; you can redistribute it and/or modify it under        *
// * the terms of the GNU General Public License as published by the Free Software        *
// * Foundation; either version 3 of the License, or (at your option) any later           *
// * version.                                                                             *
// *                                                                                      *
// * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
// * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
// * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
// *                                                                                      *
// * You should have received a copy of the GNU General Public License along with         *
// * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
// ****************************************************************************************/
//
//package com.ichi2.libanki;
//
//import java.util.ArrayList;
//
//import com.ichi2.anki2.R;
//import com.ichi2.anki.R.string;
//
//import android.content.res.Resources;
//import android.database.Cursor;
//import android.database.SQLException;
//
//public class Upgrade {
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
