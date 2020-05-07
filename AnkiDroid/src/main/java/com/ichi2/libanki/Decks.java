/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
 * Copyright (c) 2018 Chris Williams <chris@chrispwill.com>                             *
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
import android.text.TextUtils;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.DeckRenameException;
import com.ichi2.libanki.exception.NoSuchDeckException;

import com.ichi2.utils.DeckComparator;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

// fixmes:
// - make sure users can't set grad interval < 1

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes",
        "PMD.MethodNamingConventions","PMD.AvoidReassigningParameters","PMD.SimplifyBooleanReturns"})
public class Decks {


    public static final String defaultDeck = ""
            + "{"
                + "'newToday': [0, 0]," // currentDay, count
                + "'revToday': [0, 0],"
                + "'lrnToday': [0, 0],"
                + "'timeToday': [0, 0]," // time in ms
                + "'conf': 1,"
                + "'usn': 0,"
                + "'desc': \"\","
                + "'dyn': 0," // anki uses int/bool interchangably here
                + "'collapsed': False,"
                // added in beta11
                + "'extendNew': 10,"
                + "'extendRev': 50"
            + "}";

    private static final String defaultDynamicDeck = ""
            + "{"
                + "'newToday': [0, 0],"
                + "'revToday': [0, 0],"
                + "'lrnToday': [0, 0],"
                + "'timeToday': [0, 0],"
                + "'collapsed': False,"
                + "'dyn': 1,"
                + "'desc': \"\","
                + "'usn': 0,"
                + "'delays': null,"
                + "'separate': True,"
                // list of (search, limit, order); we only use first element for now
                + "'terms': [[\"\", 100, 0]],"
                + "'resched': True,"
                + "'return': True" // currently unused
            + "}";

    public static final String defaultConf = ""
            + "{"
                + "'name': \"Default\","
                + "'new': {"
                    + "'delays': [1, 10],"
                    + "'ints': [1, 4, 7]," // 7 is not currently used
                    + "'initialFactor': "+Consts.STARTING_FACTOR+","
                    + "'separate': True,"
                    + "'order': " + Consts.NEW_CARDS_DUE + ","
                    + "'perDay': 20,"
                    // may not be set on old decks
                    + "'bury': True"
                + "},"
                + "'lapse': {"
                    + "'delays': [10],"
                    + "'mult': 0,"
                    + "'minInt': 1,"
                    + "'leechFails': 8,"
                    // type 0=suspend, 1=tagonly
                    + "'leechAction': " + Consts.LEECH_SUSPEND
                + "},"
                + "'rev': {"
                    + "'perDay': 100,"
                    + "'ease4': 1.3,"
                    + "'fuzz': 0.05,"
                    + "'minSpace': 1," // not currently used
                    + "'ivlFct': 1,"
                    + "'maxIvl': 36500,"
                    // may not be set on old decks
                    + "'bury': True"
                + "},"
                + "'maxTaken': 60,"
                + "'timer': 0,"
                + "'autoplay': True,"
                + "'replayq': True,"
                + "'mod': 0,"
                + "'usn': 0"
            +"}";


    private Collection mCol;
    private HashMap<Long, JSONObject> mDecks;
    private HashMap<Long, JSONObject> mDconf;
    private boolean mChanged;


    /**
     * Registry save/load
     * ***********************************************************
     */

    public Decks(Collection col) {
        mCol = col;
    }


    public void load(String decks, String dconf) {
        mDecks = new HashMap<>();
        mDconf = new HashMap<>();
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
        for (int i = 0; ids != null && i < ids.length(); i++) {
            String id = ids.getString(i);
            mDconf.put(Long.parseLong(id), confarray.getJSONObject(id));
        }
        mChanged = false;
    }


    public void save() {
        save(null);
    }


    /**
     * Can be called with either a deck or a deck configuration.
     */
    public void save(JSONObject g) {
        if (g != null) {
            g.put("mod", Utils.intTime());
            g.put("usn", mCol.usn());
        }
        mChanged = true;
    }


    public void flush() {
        ContentValues values = new ContentValues();
        if (mChanged) {
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
            mCol.getDb().update("col", values);
            mChanged = false;
        }
    }


    /**
     * Deck save/load
     * ***********************************************************
     */

    public Long id(String name) {
        return id(name, true);
    }


    public Long id(String name, boolean create) {
        return id(name, create, defaultDeck);
    }


    public Long id(String name, String type) {
        return id(name, true, type);
    }


    /**
     * Add a deck with NAME. Reuse deck if already exists. Return id as int.
     */
    public Long id(String name, boolean create, String type) {
        name = name.replace("\"", "");
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        JSONObject deck = byName(name);
        if (deck != null) {
            return deck.getLong("id");
        }
        if (!create) {
            return null;
        }
        if (name.contains("::")) {
            // not top level; ensure all parents exist
            name = _ensureParents(name);
        }
        JSONObject g;
        long id;
        g = new JSONObject(type);
        g.put("name", name);
        while (true) {
            id = Utils.intTime(1000);
            if (!mDecks.containsKey(id)) {
                break;
            }
        }
        g.put("id", id);
        mDecks.put(id, g);
        save(g);
        maybeAddToActive();
        //runHook("newDeck"); // TODO
        return id;
    }


    public void rem(long did) {
        rem(did, false);
    }


    public void rem(long did, boolean cardsToo) {
        rem(did, cardsToo, true);
    }


    /**
     * Remove the deck. If cardsToo, delete any cards inside.
     */
    public void rem(long did, boolean cardsToo, boolean childrenToo) {
        if (did == 1) {
            // we won't allow the default deck to be deleted, but if it's a
            // child of an existing deck then it needs to be renamed
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
            // deleting a cramming deck returns cards to their previous deck
            // rather than deleting the cards
            mCol.getSched().emptyDyn(did);
            if (childrenToo) {
                for (long id : children(did).values()) {
                    rem(id, cardsToo, false);
                }
            }
        } else {
            // delete children first
            if (childrenToo) {
                // we don't want to delete children when syncing
                for (long id : children(did).values()) {
                    rem(id, cardsToo, false);
                }
            }
            // delete cards too?
            if (cardsToo) {
                // don't use cids(), as we want cards in cram decks too
                ArrayList<Long> cids = mCol.getDb().queryColumn(Long.class,
                                                                "SELECT id FROM cards WHERE did = ? OR odid = ?", 0, new Object[] {did, did});
                mCol.remCards(Utils.arrayList2array(cids));
            }
        }
        // delete the deck and add a grave
        mDecks.remove(did);
        // ensure we have an active deck
        if (active().contains(did)) {
            select(mDecks.keySet().iterator().next());
        }
        save();
    }


    public ArrayList<String> allNames() {
        return allNames(true);
    }


    /**
     * An unsorted list of all deck names.
     */
    public ArrayList<String> allNames(boolean dyn) {
        ArrayList<String> list = new ArrayList<>();
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
        return list;
    }


    /**
     * A list of all decks.
     */
    public ArrayList<JSONObject> all() {
        ArrayList<JSONObject> decks = new ArrayList<>();
        for (JSONObject deck : mDecks.values()) {
            decks.add(deck);
        }
        return decks;
    }


    /**
     * Return the same deck list from all() but sorted using a comparator that ensures the same
     * sorting order for decks as the desktop client.
     *
     * This method does not exist in the original python module but *must* be used for any user
     * interface components that display a deck list to ensure the ordering is consistent.
     */
    public ArrayList<JSONObject> allSorted() {
        ArrayList<JSONObject> decks = all();
        Collections.sort(decks, DeckComparator.instance);
        return decks;
    }


    public Long[] allIds() {
        return mDecks.keySet().toArray(new Long[mDecks.keySet().size()]);
    }


    public void collpase(long did) {
        JSONObject deck = get(did);
        deck.put("collapsed", !deck.getBoolean("collapsed"));
        save(deck);
    }


    public void collapseBrowser(long did) {
        JSONObject deck = get(did);
        boolean collapsed = deck.optBoolean("browserCollapsed", false);
        deck.put("browserCollapsed", !collapsed);
        save(deck);
    }


    /**
     * Return the number of decks.
     */
    public int count() {
        return mDecks.size();
    }

    /** Obtains the deck from the DeckID, or default if the deck was not found */
    @CheckResult
    public @NonNull JSONObject get(long did) {
        return get(did, true);
    }

    @CheckResult
    public JSONObject get(long did, boolean _default) {
        if (mDecks.containsKey(did)) {
            return mDecks.get(did);
        } else if (_default) {
            return mDecks.get(1L);
        } else {
            return null;
        }
    }


    /**
     * Get deck with NAME, ignoring case.
     */
    @CheckResult
    public @Nullable JSONObject byName(String name) {
        for (JSONObject m : mDecks.values()) {
            if (equalName(m.getString("name"),name)) {
                return m;
            }
        }
        return null;
    }


    /**
     * Add or update an existing deck. Used for syncing and merging.
     */
    public void update(JSONObject g) {
        mDecks.put(g.getLong("id"), g);
        maybeAddToActive();
        // mark registry changed, but don't bump mod time
        save();
    }


    /**
     * Rename deck prefix to NAME if not exists. Updates children.
     */
    public void rename(JSONObject g, String newName) throws DeckRenameException {
        // make sure target node doesn't already exist
        if (byName(newName) != null) {
            throw new DeckRenameException(DeckRenameException.ALREADY_EXISTS);
        }
        // ensure we have parents
        newName = _ensureParents(newName);
        // make sure we're not nesting under a filtered deck
        if (newName.contains("::")) {
            List<String> parts = Arrays.asList(path(newName));
            String newParent = TextUtils.join("::", parts.subList(0, parts.size() - 1));
            if (byName(newParent).getInt("dyn") != 0) {
                throw new DeckRenameException(DeckRenameException.FILTERED_NOSUBDEKCS);
            }
        }
        // rename children
        String oldName = g.getString("name");
        for (JSONObject grp : all()) {
            if (grp.getString("name").startsWith(oldName + "::")) {
                // In Java, String.replaceFirst consumes a regex so we need to quote the pattern to be safe
                grp.put("name", grp.getString("name").replaceFirst(Pattern.quote(oldName + "::"),
                                                                   newName + "::"));
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
    }


    public void renameForDragAndDrop(Long draggedDeckDid, Long ontoDeckDid) throws DeckRenameException {
        JSONObject draggedDeck = get(draggedDeckDid);
        String draggedDeckName = draggedDeck.getString("name");
        String ontoDeckName = get(ontoDeckDid).getString("name");

        String draggedBasename = basename(draggedDeckName);
        if (ontoDeckDid == null) {
            if (!draggedBasename.equals(draggedDeckName)) {
                rename(draggedDeck, draggedBasename);
            }
        } else if (_canDragAndDrop(draggedDeckName, ontoDeckName)) {
            rename(draggedDeck, ontoDeckName + "::" + draggedBasename);
        }
    }


    private boolean _canDragAndDrop(String draggedDeckName, String ontoDeckName) {
        if (draggedDeckName.equals(ontoDeckName)
                || _isParent(ontoDeckName, draggedDeckName)
                || _isAncestor(draggedDeckName, ontoDeckName)) {
            return false;
        } else {
            return true;
        }
    }


    private boolean _isParent(String parentDeckName, String childDeckName) {
        String[] parentDeckPath = path(parentDeckName);
        String[] childDeckPath = path(childDeckName);

        if (parentDeckPath.length + 1 != childDeckPath.length) {
            return false;
        }

        for (int i = 0; i < parentDeckPath.length; i++) {
            if (! parentDeckPath[i].equals(childDeckPath[i])) {
                return false;
            }
        }
        return true;
    }


    private boolean _isAncestor(String ancestorDeckName, String descendantDeckName) {
        String[] ancestorDeckPath = path(ancestorDeckName);
        String[] descendantDeckPath = path(descendantDeckName);
        if (ancestorDeckPath.length > descendantDeckPath.length) {
            return false;
        }

        for (int i = 0; i < ancestorDeckPath.length; i++) {
            if (ancestorDeckPath[i] != descendantDeckPath[i]) {
                return false;
            }
        }
        return true;
    }


    private static HashMap<String, String[]> pathCache = new HashMap();
    public static String[] path(String name) {
        if (!pathCache.containsKey(name)) {
            pathCache.put(name, name.split("::", -1));
        }
        return pathCache.get(name);
    }

    public static String basename(String name) {
        String[] path = path(name);
        return path[path.length - 1];
    }


    /**
     * Ensure parents exist, and return name with case matching parents.
     */
    public String _ensureParents(String name) {
        String s = "";
        String[] path = path(name);
        if (path.length < 2) {
            return name;
        }
        for(int i = 0; i < path.length - 1; i++) {
            String p = path[i];
            if (TextUtils.isEmpty(s)) {
                s += p;
            } else {
                s += "::" + p;
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
     * Deck configurations
     * ***********************************************************
     */


    /**
     * A list of all deck config.
     */
    public ArrayList<JSONObject> allConf() {
        ArrayList<JSONObject> confs = new ArrayList<>();
        for (JSONObject c : mDconf.values()) {
            confs.add(c);
        }
        return confs;
    }


    public JSONObject confForDid(long did) {
        JSONObject deck = get(did, false);
        assert deck != null;
        if (deck.has("conf")) {
            JSONObject conf = getConf(deck.getLong("conf"));
            conf.put("dyn", 0);
            return conf;
        }
        // dynamic decks have embedded conf
        return deck;
    }


    public JSONObject getConf(long confId) {
        return mDconf.get(confId);
    }


    public void updateConf(JSONObject g) {
        mDconf.put(g.getLong("id"), g);
        save();
    }


    public long confId(String name) {
        return confId(name, defaultConf);
    }


    /**
     * Create a new configuration and return id.
     */
    public long confId(String name, String cloneFrom) {
        JSONObject c;
        long id;
        c = new JSONObject(cloneFrom);
        while (true) {
            id = Utils.intTime(1000);
            if (!mDconf.containsKey(id)) {
                break;
            }
        }
        c.put("id", id);
        c.put("name", name);
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
        mCol.modSchema();
        mDconf.remove(id);
        for (JSONObject g : all()) {
            // ignore cram decks
            if (!g.has("conf")) {
                continue;
            }
            if (g.getString("conf").equals(Long.toString(id))) {
                g.put("conf", 1);
                save(g);
            }
        }
    }


    public void setConf(JSONObject grp, long id) {
        grp.put("conf", id);
        save(grp);
    }


    public List<Long> didsForConf(JSONObject conf) {
        List<Long> dids = new ArrayList<>();
        for(JSONObject deck : mDecks.values()) {
            if (deck.has("conf") && deck.getLong("conf") == conf.getLong("id")) {
                dids.add(deck.getLong("id"));
            }
        }
        return dids;
    }


    public void restoreToDefault(JSONObject conf) {
        int oldOrder = conf.getJSONObject("new").getInt("order");
        JSONObject _new = new JSONObject(defaultConf);
        _new.put("id", conf.getLong("id"));
        _new.put("name", conf.getString("name"));
        mDconf.put(conf.getLong("id"), _new);
        save(_new);
        // if it was previously randomized, resort
        if (oldOrder == 0) {
            mCol.getSched().resortConf(_new);
        }
    }


    /**
     * Deck utils
     * ***********************************************************
     */


    public String name(long did) {
        return name(did, false);
    }


    public String name(long did, boolean _default) {
        JSONObject deck = get(did, _default);
        if (deck != null) {
            return deck.getString("name");
        }
        return "[no deck]";
    }


    public String nameOrNone(long did) {
        JSONObject deck = get(did, false);
        if (deck != null) {
            return deck.getString("name");
        }
        return null;
    }


    public void setDeck(long[] cids, long did) {
        mCol.getDb().execute("update cards set did=?,usn=?,mod=? where id in " + Utils.ids2str(cids),
                new Object[] { did, mCol.usn(), Utils.intTime() });
    }


    private void maybeAddToActive() {
        // reselect current deck, or default if current has disappeared
        JSONObject c = current();
        select(c.getLong("id"));
    }


    public Long[] cids(long did) {
        return cids(did, false);
    }


    public Long[] cids(long did, boolean children) {
        if (!children) {
            return Utils.list2ObjectArray(mCol.getDb().queryColumn(Long.class, "select id from cards where did=?", 0, new Object[] {did}));
        }
        List<Long> dids = new ArrayList<>();
        dids.add(did);
        for(Map.Entry<String, Long> entry : children(did).entrySet()) {
            dids.add(entry.getValue());
        }
        return Utils.list2ObjectArray(mCol.getDb().queryColumn(Long.class,
                "select id from cards where did in " + Utils.ids2str(Utils.arrayList2array(dids)), 0));
    }

    private void _recoverOrphans() {
        Long[] dids = allIds();
        boolean mod = mCol.getDb().getMod();
        mCol.getDb().execute("update cards set did = 1 where did not in " + Utils.ids2str(dids));
        mCol.getDb().setMod(mod);
    }

    private void _checkDeckTree() {
        ArrayList<JSONObject> decks = allSorted();
        Set<String> names = new HashSet<String>();

        for (JSONObject deck: decks) {
            // two decks with the same name?
            if (names.contains(normalizeName(deck.getString("name")))) {
                Timber.i("fix duplicate deck name %s", deck.getString("name"));
                deck.put("name", deck.getString("name") + Utils.intTime(1000));
                save(deck);
            }

            // ensure no sections are blank
            if (deck.getString("name").indexOf("::::") != -1) {
                Timber.i("fix deck with missing sections %s", deck.getString("name"));
                deck.put("name", "recovered"+Utils.intTime(1000));
                save(deck);
            }

            // immediate parent must exist
            String immediateParent = parent(deck.getString("name"));
            if (immediateParent != null && !names.contains(normalizeName(immediateParent))) {
                Timber.i("fix deck with missing parent %s", deck.getString("name"));
                _ensureParents(deck.getString("name"));
                names.add(normalizeName(immediateParent));
            }
            names.add(normalizeName(deck.getString("name")));
        }
    }

    public void checkIntegrity() {
        _recoverOrphans();
        _checkDeckTree();
    }


    /**
     * Deck selection
     * ***********************************************************
     */


    /**
     * The currently active dids. Make sure to copy before modifying.
     */
    public LinkedList<Long> active() {
        JSONArray ja = mCol.getConf().getJSONArray("activeDecks");
        LinkedList<Long> result = new LinkedList<>();
        for (int i = 0; i < ja.length(); i++) {
            result.add(ja.getLong(i));
        }
        return result;
    }


    /**
     * The currently selected did.
     */
    public long selected() {
        return mCol.getConf().getLong("curDeck");
    }


    public JSONObject current() {
        return get(selected());
    }


    /**
     * Select a new branch.
     */
    public void select(long did) {
        String name = mDecks.get(did).getString("name");

        // current deck
        mCol.getConf().put("curDeck", Long.toString(did));
        // and active decks (current + all children)
        TreeMap<String, Long> actv = children(did); // Note: TreeMap is already sorted
        actv.put(name, did);
        JSONArray ja = new JSONArray();
        for (Long n : actv.values()) {
            ja.put(n);
        }
        mCol.getConf().put("activeDecks", ja);
        mCol.setMod();
    }


    /**
     * All children of did as nodes of (key:name, value:id)
     *
     * TODO: There is likely no need for this collection to be a TreeMap. This method should not
     * need to sort on behalf of select().
     */
    public TreeMap<String, Long> children(long did) {
        String name;
        name = get(did).getString("name");
        TreeMap<String, Long> actv = new TreeMap<>();
        for (JSONObject g : all()) {
            if (g.getString("name").startsWith(name + "::")) {
                actv.put(g.getString("name"), g.getLong("id"));
            }
        }
        return actv;
    }



    private void gather(HashMap<Long, HashMap> node, List<Long> arr) {
        for (Long did : node.keySet()) {
            HashMap child = node.get(did);
            arr.add(did);
            gather(child, arr);
        }
    }

    public List<Long> childDids(Long did, HashMap<Long, HashMap> childMap) {
        List<Long> arr = new ArrayList<>();
        gather(childMap.get(did), arr);
        return arr;
    }


    public HashMap<Long, HashMap> childMap() {
        HashMap<String, JSONObject> nameMap = nameMap();

        HashMap<Long, HashMap> childMap = new HashMap<>();

        // Go through all decks, sorted by name
        ArrayList<JSONObject> decks = all();

        Collections.sort(decks, DeckComparator.instance);

        for (JSONObject deck : decks) {
            HashMap node = new HashMap();
            childMap.put(deck.getLong("id"), node);

            List<String> parts = Arrays.asList(path(deck.getString("name")));
            if (parts.size() > 1) {
                String immediateParent = TextUtils.join("::", parts.subList(0, parts.size() - 1));
                long pid = nameMap.get(immediateParent).getLong("id");
                childMap.get(pid).put(deck.getLong("id"), node);
            }
        }

        return childMap;
    }


    /**
     * All parents of did.
     */
    public List<JSONObject> parents(long did) {
        // get parent and grandparent names
        return parents(did, null);
    }

    public List<JSONObject> parents(long did, HashMap<String, JSONObject> nameMap) {
        // get parent and grandparent names
        List<String> parents = new ArrayList<>();
        List<String> parts = Arrays.asList(path(get(did).getString("name")));
        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            if (parents.size() == 0) {
                parents.add(part);
            } else {
                parents.add(parents.get(parents.size() - 1) + "::" + part);
            }
        }
        // convert to objects
        List<JSONObject> oParents = new ArrayList<>();
        for (int i = 0; i < parents.size(); i++) {
            String parentName = parents.get(i);
            JSONObject deck;
            if (nameMap == null) {
                deck = get(id(parentName));
            } else {
                deck = nameMap.get(parentName);
            }
            oParents.add(i, deck);
        }
        return oParents;
    }


    public HashMap<String, JSONObject> nameMap() {
        HashMap<String, JSONObject> map = new HashMap<>();

        for (JSONObject object : mDecks.values()) {
            map.put(object.getString("name"), object);
        }

        return map;
    }


    /**
     * Sync handling
     * ***********************************************************
     */


    public void beforeUpload() {
        for (JSONObject d : all()) {
            d.put("usn", 0);
        }
        for (JSONObject c : allConf()) {
            c.put("usn", 0);
        }
        save();
    }


    /**
     * Dynamic decks
     ***************************************************************/


    /**
     * Return a new dynamic deck and set it as the current deck.
     */
    public long newDyn(String name) {
        long did = id(name, defaultDynamicDeck);
        select(did);
        return did;
    }


    public boolean isDyn(long did) {
        return get(did).getInt("dyn") != 0;
    }

    /*
     * ******************************
     * utils methods
     * **************************************
     */
    private static HashMap<String, String> normalized = new HashMap<String, String>();
    public static String normalizeName(String name) {
        if (!normalized.containsKey(name)) {
            normalized.put(name, Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase());
        }
        return normalized.get(name);
    }

    public static boolean equalName(String name1, String name2) {
        return normalizeName(name1).equals(normalizeName(name2));
    }

    /*
    * ***********************************************************
    * The methods below are not in LibAnki.
    * ***********************************************************
    */

    public static String parent(String deckName) {
        // method parent, from sched's method deckDueList in python
        List<String> parts = Arrays.asList(path(deckName));
        if (parts.size() < 2) {
            return null;
        } else {
            parts = parts.subList(0, parts.size() - 1);
            return TextUtils.join("::", parts);
        }
    }

    public String getActualDescription() {
        return current().optString("desc","");
    }


    public HashMap<Long, JSONObject> getDecks() {
        return mDecks;
    }

    public Long[] allDynamicDeckIds() {
        ArrayList<Long> validValues = new ArrayList<>();
        for (Long did : allIds()) {
            if (isDyn(did)) {
                validValues.add(did);
            }
        }
        return validValues.toArray(new Long[0]);
    }

    private JSONObject getDeckOrFail(long deckId) throws NoSuchDeckException {
        JSONObject deck = get(deckId, false);
        if (deck == null) {
            throw new NoSuchDeckException(deckId);
        }
        return deck;
    }

    public boolean hasDeckOptions(long deckId) throws NoSuchDeckException {
        return getDeckOrFail(deckId).has("conf");
    }


    public void removeDeckOptions(long deckId) throws NoSuchDeckException {
        getDeckOrFail(deckId).remove("conf");
    }

    public static boolean isDynamic(JSONObject deck) {
        return deck.getInt("dyn") != 0;
    }
}
