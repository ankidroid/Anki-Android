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

import com.ichi2.anki.exception.ConfirmModSchemaException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

/**
 * A deck stores all of the cards and scheduling information. It is saved in a file with a name ending in .anki See
 * http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Deck
 */
public class Decks {

    // private TreeMap<Integer, Model> mModelCache;
    // private TreeMap<Integer, JSONObject> mGroupConfCache;

    public static final String defaultDeck = "{" + "'newToday': [0, 0], " + // currentDay, count
            "'revToday': [0, 0], " + "'lrnToday': [0, 0], " + "'timeToday': [0, 0], " + // time in ms
            "'conf': 1, " + "'usn': 0, " + "'desc': \"\", 'dyn': 0, 'collapsed': False, " +
            // added in beta11
            "'extendNew': 10, 'extendRev': 50 }";

    private static final String defaultDynamicDeck = "{" + "'newToday': [0, 0], " + // currentDay, count
            "'revToday': [0, 0], " + "'lrnToday': [0, 0], " + "'timeToday': [0, 0], " + // time in ms
            "'collapsed': False, 'dyn': 1, 'desc': \"\", 'usn': 0, 'delays': null, 'separate': True, " +
            // list of (search, limit, order); we only use first element for now
            "'terms': [[\"\", 100, 0]], 'resched': True, " +
            // currently unused
            "'return': True }";

    // default group conf
    public static final String defaultConf = "{"
            + "'name': \"Default\","
            + "'new': {"
            + "'delays': [1, 10], "
            + "'ints': [1, 4, 7], "
            + // 7
              // is
              // not
              // currently
              // used
            "'initialFactor': 2500, " + "'separate': True, " + "'order': " + Consts.NEW_CARDS_DUE + ", "
            + "'perDay': 20, 'bury': True }, " + "'lapse': {" + "'delays': [10], " + "'mult': 0, " + "'minInt': 1, "
            + "'leechFails': 8, "
            + "'leechAction': 0 }, "
            // type 0=suspend, 1=tagonly
            + "'rev': { " + "'perDay': 100, " + "'ease4': 1.3, " + "'fuzz': 0.05, " + "'minSpace': 1, "
            + "'ivlFct': 1, " + "'maxIvl': 36500 }, " + "'maxTaken': 60, " + "'timer': 0, " + "'autoplay': True, 'replayq': True, "
            + "'mod': 0, " + "'usn': 0 }";

    public static final class DeckListComparator implements Comparator<JSONObject> {
        public int compare(JSONObject o1, JSONObject o2) {
            try {
                return o1.getString("name").compareTo(o2.getString("name"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Collection mCol;
    private HashMap<Long, JSONObject> mDecks;
    private HashMap<Long, JSONObject> mDconf;
    private boolean mChanged;


    /**
     * Registry save/load *******************************************************
     * ****************************************
     */

    public Decks(Collection col) {
        mCol = col;
    }


    public void load(String decks, String dconf) {
        mDecks = new HashMap<Long, JSONObject>();
        mDconf = new HashMap<Long, JSONObject>();
        try {
            JSONObject decksarray = new JSONObject(decks);
            JSONArray ids = decksarray.names();
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.getString(i);
                JSONObject o = decksarray.getJSONObject(id);
                long longId = Long.parseLong(id);
                mDecks.put(longId, o);
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


    /** Can be called with either a deck or a deck configuration. */
    public void save(JSONObject g) {
        if (g != null) {
            try {
                g.put("mod", Utils.intNow());
                g.put("usn", mCol.usn());
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        mChanged = true;
    }


    public void flush() {
        ContentValues values = new ContentValues();
        if (mChanged) {
            try {
                JSONObject decksarray = new JSONObject();
                for (Map.Entry<Long, JSONObject> d : mDecks.entrySet()) {
                    decksarray.put(Long.toString(d.getKey()), d.getValue());
                }
                values.put("decks", Utils.jsonToString(decksarray));
                JSONObject confarray = new JSONObject();
                for (Map.Entry<Long, JSONObject> d : mDconf.entrySet()) {
                    confarray.put(Long.toString(d.getKey()), d.getValue());
                }
                values.put("dconf", Utils.jsonToString(confarray));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            mCol.getDb().update("col", values);
            mChanged = false;
        }
    }


    /**
     * Deck save/load *********************************************************** ************************************
     */

    public long id(String name) {
        return id(name, true);
    }


    /** Add a deck with NAME. Reuse deck if already exists. Return id as int. */
    public long id(String name, boolean create) {
        return id(name, create, defaultDeck);
    }


    public long id(String name, boolean create, String type) {
        name = name.replace("\"", "");
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
        if (name.matches(".*::.*")) {
            // not top level; ensure all parents exist
            name = _ensureParents(name);
        }
        JSONObject g;
        long id;
        try {
            g = new JSONObject(type);
            g.put("name", name);
            id = Utils.intNow(1000);
            while (mDecks.containsKey(id)) {
                id = Utils.intNow();
            }
            g.put("id", id);
            mDecks.put(id, g);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save(g);
        maybeAddToActive();
        return id;
    }


    public void rem(long did) {
        rem(did, false);
    }


    /** Remove the deck. If cardsToo, delete any cards inside. */
    public void rem(long did, boolean cardsToo) {
        rem(did, cardsToo, true);
    }


    public void rem(long did, boolean cardsToo, boolean childrenToo) {
        try {
            if (did == 1) {
            	// we won't allow the default deck to be deleted, but if it's a child of an existing deck then it needs to be renamed
            	JSONObject deck = get(did);
            	if (deck.getString("name").contains("::")) {
            		deck.put("name", "Default");
            		save(deck);
            	}
                return;
            }
            // log the removal regardless of whether we have the deck or not
            mCol._logRem(new long[] { did }, Consts.REM_DECK);
            // do nothing else if doesn't exist
            if (!mDecks.containsKey(did)) {
                return;
            }
            JSONObject deck = get(did);
            if (deck.getInt("dyn") != 0) {
                // deleting a cramming deck returns cards to their previous deck rather than deleting the cards
                mCol.getSched().emptyDyn(did);
                if (childrenToo) {
                    for (long id : children(did).values()) {
                        rem(id, cardsToo);
                    }
                }
            } else {
                // delete children first
                if (childrenToo) {
                    // we don't want to delete children when syncing
                    for (long id : children(did).values()) {
                        rem(id, cardsToo);
                    }
                }
                // delete cards too?
                if (cardsToo) {
                    // don't use cids(), as we want cards in cram decks too
                    ArrayList<Long> cids = mCol.getDb().queryColumn(Long.class,
                            "SELECT id FROM cards WHERE did = " + did + " OR odid = " + did, 0);
                    mCol.remCards(Utils.arrayList2array(cids));
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // delete the deck and add a grave
        mDecks.remove(did);
        // ensure we have an active deck
        if (active().contains(did)) {
            select((long) (mDecks.keySet().iterator().next()));
        }
        save();
    }


    /** An unsorted list of all deck names. */
    public ArrayList<String> allNames() {
        return allNames(true);
    }


    public ArrayList<String> allNames(boolean dyn) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            if (dyn) {
                for (JSONObject x : mDecks.values()) {
                    list.add(x.getString("name"));
                }
            } else {
                for (JSONObject x : mDecks.values()) {
                    if (x.getInt("dyn") == 0) {
                        list.add(x.getString("name"));
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return list;
    }


    /**
     * A list of all decks.
     */
    public ArrayList<JSONObject> all() {
        ArrayList<JSONObject> decks = new ArrayList<JSONObject>();
        Iterator<JSONObject> it = mDecks.values().iterator();
        while (it.hasNext()) {
            decks.add(it.next());
        }
        return decks;
    }


    // LIBANKI: not in libanki
    public ArrayList<JSONObject> allSorted() {
        ArrayList<JSONObject> decks = all();
        Collections.sort(decks, new DeckListComparator());
        return decks;
    }


    public long[] allIds() {
        Iterator<Long> it = mDecks.keySet().iterator();
        long[] ids = new long[mDecks.size()];
        int i = 0;
        while (it.hasNext()) {
            ids[i] = it.next();
            i++;
        }
        return ids;
    }

    /**
     * Return the number of decks.
     */
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
            JSONObject d = mDecks.get(1);
            if (d == null) {
                d = mDecks.values().iterator().next();
            }
            return d;
        } else {
            return null;
        }
    }

    /** Get deck by NAME. */
    public JSONObject byName(String name) {
		try {
			for (JSONObject m : mDecks.values()) {
				if (m.get("name").equals(name)) {
					return m;
				}
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return null;
    }

    /** Add or update an existing deck. Used for syncing and merging. */
    public void update(JSONObject g) {
        try {
            mDecks.put(g.getLong("id"), g);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        maybeAddToActive();
        // mark registry changed, but don't bump mod time
        save();
    }


    /** Rename deck prefix to NAME if not exists. Updates children. */
    public boolean rename(JSONObject g, String newName) {
        // make sure target node doesn't already exist
        if (allNames().contains(newName) || newName.length() == 0) {
            return false;
        }
        // ensure we have parents
        newName = _ensureParents(newName);
        // rename children
        String oldName;
        try {
            oldName = g.getString("name");
            for (JSONObject grp : all()) {
                if (grp.getString("name").startsWith(oldName + "::")) {
                    String on = grp.getString("name");
                    // unlike the related code in libanki python the following replaceFirst call works with regex
                    // pattern, so we need to escape the oldName:: with \Q, \E
                    String nn = on.replaceFirst("\\Q" + oldName + "::\\E", newName + "::");
                    grp.put("name", nn);
                    save(grp);
                }
            }
            // adjust name
            g.put("name", newName);
            // ensure we have parents again, as we may have renamed parent->child
            newName = _ensureParents(newName);
            save(g);
            // renaming may have altered active did order
            maybeAddToActive();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return true;
    }


    /** Ensure parents exist, and return name with case matching parents. */
    public String _ensureParents(String name) {
        String s = "";
        String[] path = name.split("::");
        if (path.length < 2) {
            return name;
        }
        for (int i = 0; i < path.length - 1; i++) {
            if (i == 0) {
                s = path[0];
            } else {
                s = s + "::" + path[i];
            }
            // fetch or create
            long did = id(s);
            // get original case
            s = name(did);
        }
        name = s + "::" + path[path.length - 1];
        return name;
    }


    /**
     * Deck configurations ******************************************************
     * *****************************************
     */

    /** A list of all deck config. */
    public ArrayList<JSONObject> allConf() {
        ArrayList<JSONObject> confs = new ArrayList<JSONObject>();
        for (JSONObject c : mDconf.values()) {
            confs.add(c);
        }
        return confs;
    }


    public JSONObject confForDid(long did) {
        JSONObject deck = get(did);
        if (deck.has("conf")) {
            try {
                JSONObject conf = getConf(deck.getLong("conf"));
                conf.put("dyn", 0);
                return conf;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        // dynamic decks have embedded conf
        return deck;
    }


    public JSONObject getConf(long confId) {
        return mDconf.get(confId);
    }


    public void updateConf(JSONObject g) {
        try {
            mDconf.put(g.getLong("id"), g);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save();
    }


    /**
     * Create a new configuration and return id. Uses defaultConf as template.
     *
     * @param name Name of the new configuration
     * @return The id of the new configuration
     */
    public long confId(String name) {
        return confId(name, defaultConf);
    }


    /**
     * Create a new configuration and return id.
     *
     * @param name Name of the new configuration
     * @param cloneFrom Optional parameter to copy configuration from
     * @return The id of the new configuration
     */
    public long confId(String name, String cloneFrom) {
        JSONObject c;
        long id;
        try {
            c = new JSONObject(cloneFrom);
            while (true) {
                id = Utils.intNow(1000);
                if (!mDconf.containsKey(Long.valueOf(id))) {
                    break;
                }
            }
            c.put("id", id);
            c.put("name", name);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        mDconf.put(id, c);
        save(c);
        return id;
    }

    /**
     * Remove a configuration and update all decks using it.
     * @throws ConfirmModSchemaException 
     */
    public void remConf(long id) throws ConfirmModSchemaException {
        assert id != 1;
        mCol.modSchema(true);
        mDconf.remove(Long.valueOf(id));
        for (JSONObject g : all()) {
            // ignore cram decks
            if (!g.has("conf")) {
                continue;
            }
            try {
                if (g.getString("conf").equals(Long.toString(id))) {
                    g.put("conf", 1);
                    save(g);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void setConf(JSONObject deck, long id) {
        try {
            deck.put("conf", id);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save(deck);
    }


    public ArrayList<Long> didsForConf(JSONObject conf) {
        ArrayList<Long> dids = new ArrayList<Long>();
        Iterator<JSONObject> it = mDecks.values().iterator();
        try {
            while (it.hasNext()) {
                JSONObject deck = it.next();
                if (deck.has("conf") && deck.getLong("conf") == conf.getLong("id")) {
                    dids.add(deck.getLong("id"));
                }
            }
            return dids;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void restoreToDefault(JSONObject conf) {
        try {
            int oldOrder = conf.getJSONObject("new").getInt("order");
            JSONObject newConf = new JSONObject(defaultConf);
            newConf.put("id", conf.getLong("id"));
            newConf.put("name", conf.getString("name"));
            mDconf.put(conf.getLong("id"), newConf);
            save(newConf);
            // if it was previously randomized, resort
            if (oldOrder == 0) {
                mCol.getSched().resortConf(newConf);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deck utils *************************************************************** ********************************
     */

    public String name(long did) {
        return name(did, false);
    }


    public String name(long did, boolean def) {
        try {
            JSONObject deck = get(did, def);
            if (deck != null) {
                return deck.getString("name");
            }
            return "[no deck]";
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public String nameOrNone(long did) {
        JSONObject deck = get(did, false);
        if (deck != null) {
            try {
                return deck.getString("name");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    public void setDeck(long[] cids, long did) {
        mCol.getDb().execute("UPDATE cards SET did = ?, usn = ?, mod = ? WHERE id IN " + Utils.ids2str(cids),
                new Object[] { did, mCol.usn(), Utils.intNow() });
    }


    private void maybeAddToActive() {
        // reselect current deck, or default if current has disappeared
        JSONObject c = current();
        try {
            select(c.getLong("id"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public long[] cids(long did) {
        return cids(did, false);
    }


    public long[] cids(long did, boolean children) {
        String sql;
        if (children) {
            ArrayList<Long> dids = new ArrayList<Long>();
            dids.add(did);
            for (long id : children(did).values()) {
                dids.add(id);
            }
            sql = "SELECT id FROM cards WHERE did IN " + Utils.ids2str(Utils.arrayList2array(dids));
        } else {
            sql = "SELECT id FROM cards WHERE did = " + did;
        }
        ArrayList<Long> cids = mCol.getDb().queryColumn(Long.class, sql, 0);
        long[] result = new long[cids.size()];
        for (int i = 0; i < cids.size(); i++) {
            result[i] = cids.get(i);
        }
        return result;
    }


    public void recoverOrphans() {
        boolean mod = mCol.getDb().getMod();
        mCol.getDb().execute("UPDATE cards SET did = 1 WHERE did NOT IN " + Utils.ids2str(allIds()));
        mCol.getDb().setMod(mod);
    }


    /**
     * Deck selection *********************************************************** ************************************
     */

    /* The currrently active dids. MAke sure to copy before modifying */
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
            JSONArray ja = new JSONArray();
            for (Long n : actv.values()) {
                ja.put(n);
            }
            mCol.getConf().put("activeDecks", ja);
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
        // get parent and grandparent names
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
                list.add(get(id(deckpath, false)));
            }
            return list;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Sync handling ************************************************************ ***********************************
     */

    public void beforeUpload() {
        try {
            for (JSONObject d : all()) {
                d.put("usn", 0);
            }
            for (JSONObject c : allConf()) {
                c.put("usn", 0);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save();
    }


    /**
     * Deck utils
     ***************************************************************/

    /* Return a new dynamic deck and set it as the current deck. */
    public long newDyn(String name) {
        long did = id(name, true, defaultDynamicDeck);
        select(did);
        return did;
    }


    public boolean isDyn(long did) {
        try {
            return get(did).getInt("dyn") != 0;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public String getActualDescription() {
    	return current().optString("desc","");
    }


    // /**
    // * Yes counts
    // * @return todayAnswers, todayNoAnswers, matureAnswers, matureNoAnswers
    // */
    // public int[] yesCounts() {
    // int dayStart = (getSched().mDayCutoff - 86400) * 10000;
    // int todayAnswers = (int)
    // getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " +
    // dayStart);
    // int todayNoAnswers = (int)
    // getDB().queryScalar("SELECT count() FROM revlog WHERE time >= " +
    // dayStart + " AND ease = 1");
    // int matureAnswers = (int)
    // getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21");
    // int matureNoAnswers = (int)
    // getDB().queryScalar("SELECT count() FROM revlog WHERE lastivl >= 21 AND ease = 1");
    // return new int[] { todayAnswers, todayNoAnswers, matureAnswers,
    // matureNoAnswers };
    // }
    //
    //
    // /**
    // * Yes rates for today's and mature cards
    // * @return todayRate, matureRate
    // */
    // public double[] yesRates() {
    // int[] counts = yesCounts();
    // return new double[] { 1 - (double)counts[1]/counts[0], 1 -
    // (double)counts[3]/counts[2] };
    // }
    //
    //
    // // Media
    // // *****
    //
    // /**
    // * Return the media directory if exists, none if couldn't be created.
    // *
    // * @param create If true it will attempt to create the folder if it
    // doesn't exist
    // * @param rename This is used to simulate the python with create=None that
    // is only used when renaming the mediaDir
    // * @return The path of the media directory
    // */
    // public String mediaDir() {
    // return mediaDir(false, false);
    // }
    // public String mediaDir(boolean create) {
    // return mediaDir(create, false);
    // }
    // public String mediaDir(boolean create, boolean rename) {
    // String dir = null;
    // File mediaDir = null;
    // if (mDeckPath != null && !mDeckPath.equals("")) {
    // Log.i(AnkiDroidApp.TAG, "mediaDir - mediaPrefix = " + mMediaPrefix);
    // if (mMediaPrefix != null) {
    // dir = mMediaPrefix + "/" + mDeckName + ".media";
    // } else {
    // dir = mDeckPath.replaceAll("\\.anki$", ".media");
    // }
    // if (rename) {
    // // Don't create, but return dir
    // return dir;
    // }
    // mediaDir = new File(dir);
    // if (!mediaDir.exists() && create) {
    // try {
    // if (!mediaDir.mkdir()) {
    // Log.e(AnkiDroidApp.TAG, "Couldn't create media directory " + dir);
    // return null;
    // }
    // } catch (SecurityException e) {
    // Log.e(AnkiDroidApp.TAG,
    // "Security restriction: Couldn't create media directory " + dir);
    // return null;
    // }
    // }
    // }
    //
    // if (dir == null) {
    // return null;
    // } else {
    // if (!mediaDir.exists() || !mediaDir.isDirectory()) {
    // return null;
    // }
    // }
    // Log.i(AnkiDroidApp.TAG, "mediaDir - mediaDir = " + dir);
    // return dir;
    // }
    //
    // public String getMediaPrefix() {
    // return mMediaPrefix;
    // }
    // public void setMediaPrefix(String mediaPrefix) {
    // mMediaPrefix = mediaPrefix;
    // }
    // //
    // //
    // //
    // // private boolean hasLaTeX() {
    // // Cursor cursor = null;
    // // try {
    // // cursor = getDB().getDatabase().rawQuery(
    // // "SELECT Id FROM fields WHERE " +
    // // "(value like '%[latex]%[/latex]%') OR " +
    // // "(value like '%[$]%[/$]%') OR " +
    // // "(value like '%[$$]%[/$$]%') LIMIT 1 ", null);
    // // if (cursor.moveToFirst()) {
    // // return true;
    // // }
    // // } finally {
    // // if (cursor != null) {
    // // cursor.close();
    // // }
    // // }
    // // return false;
    // // }

    public HashMap<Long, JSONObject> getDconf() {
        return mDconf;
    }


    public HashMap<Long, JSONObject> getDecks() {
        return mDecks;
    }
}
