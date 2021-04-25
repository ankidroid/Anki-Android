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

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.DeckRenameException;
import com.ichi2.anki.exception.FilteredAncestor;

import com.ichi2.utils.DeckComparator;
import com.ichi2.utils.DeckNameComparator;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;
import com.ichi2.utils.SyncStatus;

import net.ankiweb.rsdroid.RustCleanup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import static com.ichi2.libanki.Consts.DECK_STD;
import static com.ichi2.utils.CollectionUtils.addAll;

// fixmes:
// - make sure users can't set grad interval < 1

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes",
        "PMD.MethodNamingConventions","PMD.AvoidReassigningParameters","PMD.SimplifyBooleanReturns"})
public class Decks {

    // Invalid id, represents an id on an unfound deck
    public static final long NOT_FOUND_DECK_ID = -1L;
    

    //not in libAnki
    @SuppressWarnings("WeakerAccess")
    public static final String DECK_SEPARATOR = "::";

    public static final String DEFAULT_DECK = ""
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

    public static final String DEFAULT_CONF = ""
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
                    + "'bury': False"
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
                    + "'bury': False"
                + "},"
                + "'maxTaken': 60,"
                + "'timer': 0,"
                + "'autoplay': True,"
                + "'replayq': True,"
                + "'mod': 0,"
                + "'usn': 0"
            +"}";


    private final Collection mCol;
    private HashMap<Long, Deck> mDecks;
    private HashMap<Long, DeckConfig> mDconf;
    // Never access mNameMap directly. Uses byName
    private NameMap mNameMap;
    private boolean mChanged;



    /**
     * A tool to quickly access decks from name. Ensure that names get properly normalized so that difference in
     * name unicode normalization or upper/lower case, is ignored during deck search.
     */
    private static class NameMap {
        private final HashMap<String, Deck> mNameMap;


        /**
         * @param size The expected number of deck to keep
         */
        private NameMap(int size) {
            mNameMap = new HashMap<>(size);
        }


        /**
         * @param decks The collection of decks we want to get access quickly
         * @return A name map, allowing to get decks from name
         */
        public static NameMap constructor(java.util.Collection<Deck> decks) {
            NameMap map = new NameMap(2 * decks.size());
            for (Deck deck: decks) {
                map.add(deck);
            }
            return map;
        }


        /**
         * @param name A name of deck to get
         * @return The deck with this name if it exists, null otherwise.
         */
        public synchronized Deck get(String name) {
            String normalized = normalizeName(name);
            Deck deck = mNameMap.get(normalized);
            if (deck == null) {
                return null;
            }
            String foundName = deck.getString("name");
            if (!equalName(name, foundName)) {
                AnkiDroidApp.sendExceptionReport("We looked for deck \"" + name + "\" and instead got deck \"" + foundName + "\".", "Decks - byName");
            }
            return deck;
        }


        /**
         * @param g Add a deck. Allow from its name to get quick access to the deck.
         */
        public synchronized void add(Deck g) {
            String name = g.getString("name");
            mNameMap.put(name, g);
            // Normalized name is also added because it's required to use it in by name.
            // Non normalized is kept for Parent
            mNameMap.put(normalizeName(name), g);
        }


        /**
           Remove name from nameMap if it is equal to expectedDeck.

           It is possible that another deck has been given name `name`,
           in which case we don't want to remove it from nameMap.

           E.g. if A is renamed to A::B and A::B already existed and get
           renamed to A::B::B, then we don't want to remove A::B from
           nameMap when A::B is renamed to A::B::B, since A::B is
           potentially the correct value.
        */
        public synchronized void remove(String name, JSONObject expectedDeck) {
            String[] names = new String[] {name, normalizeName(name)};
            for (String name_: names) {
                JSONObject currentDeck = mNameMap.get(name_);
                if (currentDeck != null && currentDeck.getLong("id") == expectedDeck.getLong("id")) {
                    /* Remove name from mapping only if it still maps to
                     * expectedDeck. I.e. no other deck had been given this
                     * name yet. */
                    mNameMap.remove(name_);
                }
            }
        }

    }

    /**
     * Registry save/load
     * ***********************************************************
     */

    public Decks(Collection col) {
        mCol = col;
    }


    public void load(String decks, String dconf) {
        JSONObject decksarray = new JSONObject(decks);
        JSONArray ids = decksarray.names();
        mDecks = new HashMap<>(decksarray.length());
        for (String id: ids.stringIterable()) {
            Deck o = new Deck(decksarray.getJSONObject(id));
            long longId = Long.parseLong(id);
            mDecks.put(longId, o);
        }
        mNameMap = NameMap.constructor(mDecks.values());
        JSONObject confarray = new JSONObject(dconf);
        ids = confarray.names();
        mDconf = new HashMap<>(confarray.length());
        if (ids != null) {
            for (String id : ids.stringIterable()) {
                mDconf.put(Long.parseLong(id), new DeckConfig(confarray.getJSONObject(id)));
            }
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
            g.put("mod", mCol.getTime().intTime());
            g.put("usn", mCol.usn());
        }
        mChanged = true;
    }


    public void flush() {
        ContentValues values = new ContentValues();
        if (mChanged) {
            JSONObject decksarray = new JSONObject();
            for (Map.Entry<Long, Deck> d : mDecks.entrySet()) {
                decksarray.put(Long.toString(d.getKey()), d.getValue());
            }
            values.put("decks", Utils.jsonToString(decksarray));
            JSONObject confarray = new JSONObject();
            for (Map.Entry<Long, DeckConfig> d : mDconf.entrySet()) {
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

    public Long id_for_name(String name) {
        name = usable_name(name);
        Deck deck = byName(name);
        if (deck != null) {
            return deck.getLong("id");
        }
        return null;
    }

    public Long id(String name) throws FilteredAncestor {
        return id(name, DEFAULT_DECK);
    }

    public Long id_safe(String name) {
        return id_safe(name, DEFAULT_DECK);
    }

    private String usable_name(String name) {
        name = strip(name);
        name = name.replace("\"", "");
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        return name;
    }

    /**
     * Add a deck with NAME. Reuse deck if already exists. Return id as int.
     */
    public Long id(String name, String type) throws FilteredAncestor {
        name = usable_name(name);
        Long id = id_for_name(name);
        if (id != null) {
            return id;
        }
        if (name.contains("::")) {
            // not top level; ensure all parents exist
            name = _ensureParents(name);
        }
        return id_create_name_valid(name, type);
    }


    /**
     * @param name A name, assuming it's not a deck name, all ancestors exists and are not filtered
     * @param type The json encoding of the deck, except for name and id
     * @return the deck's id
     */
    private Long id_create_name_valid(String name, String type) {
        Long id;
        Deck g = new Deck(type);
        g.put("name", name);
        do {
            id = mCol.getTime().intTimeMS();
        } while (mDecks.containsKey(id));
        g.put("id", id);
        mDecks.put(id, g);
        save(g);
        maybeAddToActive();
        mNameMap.add(g);
        //runHook("newDeck"); // TODO
        return id;
    }


    /**
     * Same as id, but rename ancestors if filtered to avoid failure
     */
    public Long id_safe(String name, String type)  {
        name = usable_name(name);
        Long id = id_for_name(name);
        if (id != null) {
            return id;
        }
        if (name.contains("::")) {
            // not top level; ensure all parents exist
            name = _ensureParentsNotFiltered(name);
        }
        return id_create_name_valid(name, type);
    }


    public void rem(long did) {
        rem(did, true);
    }


    public void rem(long did, boolean cardsToo) {
        rem(did, cardsToo, true);
    }


    /**
     * Remove the deck. If cardsToo, delete any cards inside.
     */
    public void rem(long did, boolean cardsToo, boolean childrenToo) {
        Deck deck = get(did, false);
        if (did == 1) {
            // we won't allow the default deck to be deleted, but if it's a
            // child of an existing deck then it needs to be renamed
            if (deck != null && deck.getString("name").contains("::")) {
                deck.put("name", "Default");
                save(deck);
            }
            return;
        }
        // log the removal regardless of whether we have the deck or not
        mCol._logRem(new long[] { did }, Consts.REM_DECK);
        // do nothing else if doesn't exist
        if (deck == null) {
            return;
        }
        if (deck.isDyn()) {
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
                ArrayList<Long> cids = mCol.getDb().queryLongList("SELECT id FROM cards WHERE did = ? OR odid = ?", did, did);
                mCol.remCards(cids);
            }
        }
        // delete the deck and add a grave
        mDecks.remove(did);
        mNameMap.remove(deck.getString("name"), deck);
        // ensure we have an active deck
        if (active().contains(did)) {
            select(mDecks.keySet().iterator().next());
        }
        save();
    }


    public List<String> allNames() {
        return allNames(true);
    }


    /**
     * An unsorted list of all deck names.
     */
    public List<String> allNames(boolean dyn) {
        List<String> list = new ArrayList<>(mDecks.size());
        if (dyn) {
            for (Deck x : mDecks.values()) {
                list.add(x.getString("name"));
            }
        } else {
            for (Deck x : mDecks.values()) {
                if (x.isStd()) {
                    list.add(x.getString("name"));
                }
            }
        }
        return list;
    }


    /**
     * A list of all decks.
     */
    public List<Deck> all() {
        return new ArrayList<>(mDecks.values());
    }


    /**
     * Return the same deck list from all() but sorted using a comparator that ensures the same
     * sorting order for decks as the desktop client.
     *
     * This method does not exist in the original python module but *must* be used for any user
     * interface components that display a deck list to ensure the ordering is consistent.
     */
    public List<Deck> allSorted() {
        List<Deck> decks = all();
        Collections.sort(decks, DeckComparator.INSTANCE);
        return decks;
    }

    @VisibleForTesting
    public List<String> allSortedNames() {
        List<String> names = allNames();
        Collections.sort(names, DeckNameComparator.INSTANCE);
        return names;
    }


    public Set<Long> allIds() {
        return mDecks.keySet();
    }


    public void collapse(long did) {
        Deck deck = get(did);
        deck.put("collapsed", !deck.getBoolean("collapsed"));
        save(deck);
    }


    public void collapseBrowser(long did) {
        Deck deck = get(did);
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
    public @NonNull Deck get(long did) {
        return get(did, true);
    }


    @CheckResult
    public Deck get(long did, boolean _default) {
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
    public @Nullable Deck byName(String name) {
        return mNameMap.get(name);
    }


    /**
     * Add or update an existing deck. Used for syncing and merging.
     */
    public void update(Deck g) {
        long id = g.getLong("id");
        JSONObject oldDeck = get(id, false);
        if (oldDeck != null) {
            // In case where another update got the name
            // `oldName`, it would be a mistake to remove it from nameMap
            mNameMap.remove(oldDeck.getString("name"), oldDeck);
        }
        mNameMap.add(g);
        mDecks.put(g.getLong("id"), g);
        maybeAddToActive();
        // mark registry changed, but don't bump mod time
        save();
    }

    /**
     * Rename deck prefix to NAME if not exists. Updates children.
     */
    public void rename(Deck g, String newName) throws DeckRenameException {
        newName = strip(newName);
        // make sure target node doesn't already exist
        Deck deckWithThisName = byName(newName);
        if (deckWithThisName != null) {
            if (deckWithThisName.getLong("id") != g.getLong("id")) {
                throw new DeckRenameException(DeckRenameException.ALREADY_EXISTS);
            }
            /* else: We are renaming the deck to the "same"
             * name. I.e. case may varie, normalization may be
             * different, but anki essentially consider that the name
             * did not change. We still need to run the remaining of
             * the code in order do this change. */
        }
        // ensure we have parents and none is a filtered deck
        try {
            newName = _ensureParents(newName);
        } catch (FilteredAncestor filteredSubdeck) {
            throw new DeckRenameException(DeckRenameException.FILTERED_NOSUBDECKS);
        }
        // rename children
        String oldName = g.getString("name");
        for (Deck grp : all()) {
            String grpOldName = grp.getString("name");
            if (grpOldName.startsWith(oldName + "::")) {
                String grpNewName = grpOldName.replaceFirst(Pattern.quote(oldName + "::"), newName + "::");
                // In Java, String.replaceFirst consumes a regex so we need to quote the pattern to be safe
                mNameMap.remove(grpOldName, grp);
                grp.put("name", grpNewName);
                mNameMap.add(grp);
                save(grp);
            }
        }
        mNameMap.remove(oldName, g);
        // adjust name
        g.put("name", newName);
        // ensure we have parents again, as we may have renamed parent->child
        // No ancestor can be filtered after renaming
        newName = _ensureParentsNotFiltered(newName);
        mNameMap.add(g);
        save(g);
        // renaming may have altered active did order
        maybeAddToActive();
    }


    /* Buggy implementation. Keep as first draft if we want to use it again
    public void renameForDragAndDrop(Long draggedDeckDid, Long ontoDeckDid) throws DeckRenameException {
        Deck draggedDeck = get(draggedDeckDid);
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
    */


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
            if (! Utils.equals(ancestorDeckPath[i], descendantDeckPath[i])) {
                return false;
            }
        }
        return true;
    }


    private static final HashMap<String, String[]> pathCache = new HashMap<>();
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
     *
     * @param name The name whose parents should exists
     * @return The name, with potentially change in capitalization and unicode normalization, so that the parent's name corresponds to an existing deck.
     * @throws FilteredAncestor if a parent is filtered
     */
    @VisibleForTesting
    protected String _ensureParents(String name) throws FilteredAncestor {
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
            Deck deck = get(did);
            if (deck.isDyn()) {
                throw new FilteredAncestor(s);
            }
        }
        name = s + "::" + path[path.length - 1];
        return name;
    }


    /**
     * Similar as ensure parent, to use when the method can't fail and it's better to allow more change to ancestor's names.
     * @param name The name whose parents should exists
     * @return The name similar to input, changed as required, and as little as required, so that no ancestor is filtered and the parent's name is an existing deck.
     */
    @VisibleForTesting
    protected  String _ensureParentsNotFiltered(String name) {
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
            long did = id_safe(s);
            Deck deck = get(did);
            s = name(did);
            while (deck.isDyn()) {
                s = s + "'";
                // fetch or create
                did = id_safe(s);
                // get original case
                s = name(did);
                deck = get(did);

            }
        }
        name = s + "::" + path[path.length - 1];
        return name;
    }


    /*
      Deck configurations
      ***********************************************************
     */


    /**
     * A list of all deck config.
     */
    public ArrayList<DeckConfig> allConf() {
        return new ArrayList<>(mDconf.values());
    }


    public DeckConfig confForDid(long did) {
        Deck deck = get(did, false);
        assert deck != null;
        if (deck.has("conf")) {
            DeckConfig conf = getConf(deck.getLong("conf"));
            if (conf == null) {
                // fall back on default
                conf = getConf(1L);
            }
            conf.put("dyn", DECK_STD);
            return conf;
        }
        // dynamic decks have embedded conf
        return new DeckConfig(deck);
    }


    public DeckConfig getConf(long confId) {
        return mDconf.get(confId);
    }


    public void updateConf(DeckConfig g) {
        mDconf.put(g.getLong("id"), g);
        save();
    }


    public long confId(String name) {
        return confId(name, DEFAULT_CONF);
    }


    /**
     * Create a new configuration and return id.
     */
    public long confId(String name, String cloneFrom) {
        long id;
        DeckConfig c = new DeckConfig(cloneFrom);
        do {
            id = mCol.getTime().intTimeMS();
        } while (mDconf.containsKey(id));
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
        for (Deck g : all()) {
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


    public void setConf(Deck grp, long id) {
        grp.put("conf", id);
        save(grp);
    }


    public List<Long> didsForConf(DeckConfig conf) {
        List<Long> dids = new ArrayList<>();
        for(Deck deck : mDecks.values()) {
            if (deck.has("conf") && deck.getLong("conf") == conf.getLong("id")) {
                dids.add(deck.getLong("id"));
            }
        }
        return dids;
    }


    public void restoreToDefault(DeckConfig conf) {
        int oldOrder = conf.getJSONObject("new").getInt("order");
        DeckConfig _new = mCol.getBackend().new_deck_config_legacy();
        _new.put("id", conf.getLong("id"));
        _new.put("name", conf.getString("name"));

        updateConf(_new);
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
        Deck deck = get(did, _default);
        if (deck != null) {
            return deck.getString("name");
        }
        return "[no deck]";
    }


    public String nameOrNone(long did) {
        Deck deck= get(did, false);
        if (deck != null) {
            return deck.getString("name");
        }
        return null;
    }


    public void setDeck(long[] cids, long did) {
        mCol.getDb().execute("update cards set did=?,usn=?,mod=? where id in " + Utils.ids2str(cids),
                did, mCol.usn(), mCol.getTime().intTime());
    }


    private void maybeAddToActive() {
        // reselect current deck, or default if current has disappeared
        Deck c = current();
        select(c.getLong("id"));
    }


    public Long[] cids(long did) {
        return cids(did, false);
    }


    public Long[] cids(long did, boolean children) {
        if (!children) {
            return Utils.list2ObjectArray(mCol.getDb().queryLongList("select id from cards where did=?", did));
        }
        java.util.Collection<Long> values = children(did).values();
        List<Long> dids = new ArrayList<>(values.size() + 1);
        dids.add(did);
        dids.addAll(values);
        return Utils.list2ObjectArray(mCol.getDb().queryLongList("select id from cards where did in " + Utils.ids2str(dids)));
    }


    private static final Pattern spaceAroundSeparator = Pattern.compile("\\s*::\\s*");
    @VisibleForTesting
    static String strip(String deckName) {
        //Ends of components are either the ends of the deck name, or near the ::.
        //Deal with all spaces around ::
        deckName = spaceAroundSeparator.matcher(deckName).replaceAll("::");
        //Deal with spaces at start/end of the deck name.
        deckName = deckName.trim();
        return deckName;
    }

    private void _recoverOrphans() {
        boolean mod = mCol.getDb().getMod();
        SyncStatus.ignoreDatabaseModification(() -> mCol.getDb().execute("update cards set did = 1 where did not in " + Utils.ids2str(allIds())));
        mCol.getDb().setMod(mod);
    }

    private void _checkDeckTree() {
        List<Deck> decks = allSorted();
        Map<String, Deck> names = new HashMap<>(decks.size());

        for (Deck deck: decks) {
            String deckName = deck.getString("name");

            /* With 2.1.28, anki started strips whitespace of deck name.  This method paragraph is here for
              compatibility while we wait for rust.  It should be executed before other changes, because both "FOO "
              and "FOO" will be renamed to the same name, and so this will need to be renamed again in case of
              duplicate.*/
            String strippedName = strip(deckName);
            if (!deckName.equals(strippedName)) {
                mNameMap.remove(deckName, deck);
                deckName = strippedName;
                deck.put("name", deckName);
                mNameMap.add(deck);
                save(deck);
            }

            // ensure no sections are blank
            if ("".equals(deckName)) {
                Timber.i("Fix deck with empty name");
                mNameMap.remove(deckName, deck);
                deckName = "blank";
                deck.put("name", "blank");
                mNameMap.add(deck);
                save(deck);
            }

            if (deckName.contains("::::")) {
                Timber.i("fix deck with missing sections %s", deck.getString("name"));
                mNameMap.remove(deckName, deck);
                do {
                    deckName = deck.getString("name").replace("::::", "::blank::");
                    // We may need to iterate, in order to replace "::::::" and adding to "blank" in it.
                } while (deckName.contains("::::"));
                deck.put("name", deckName);
                mNameMap.add(deck);
                save(deck);
            }

            // two decks with the same name?
            Deck homonym = names.get(normalizeName(deckName));
            if (homonym != null) {
                Timber.i("fix duplicate deck name %s", deckName);
                do {
                    deckName += "+";
                    deck.put("name", deckName);
                } while (names.containsKey(normalizeName(deckName)));
                mNameMap.add(deck);
                mNameMap.add(homonym); // Ensuring both names are correctly in mNameMap
                save(deck);
            }

            // immediate parent must exist
            String immediateParent = parent(deckName);
            if (immediateParent != null && !names.containsKey(normalizeName(immediateParent))) {
                Timber.i("fix deck with missing parent %s", deckName);
                Deck parent = byName(immediateParent);
                _ensureParentsNotFiltered(deckName);
                names.put(normalizeName(immediateParent), parent);
            }
            names.put(normalizeName(deckName), deck);
        }
    }

    public void checkIntegrity() {
        _recoverOrphans();
        _checkDeckTree();
    }


    /*
      Deck selection
      ***********************************************************
     */


    /**
     * The currently active dids. Make sure to copy before modifying.
     */
    public LinkedList<Long> active() {
        JSONArray activeDecks = mCol.getConf().getJSONArray("activeDecks");
        LinkedList<Long> result = new LinkedList<>();
        addAll(result, activeDecks.longIterable());
        return result;
    }


    /**
     * The currently selected did.
     */
    public long selected() {
        return mCol.getConf().getLong("curDeck");
    }


    public Deck current() {
        return get(selected());
    }


    /**
     * Select a new branch.
     */
    public void select(long did) {
        String name = mDecks.get(did).getString("name");

        // current deck
        mCol.getConf().put("curDeck", did);
        // and active decks (current + all children)
        TreeMap<String, Long> actv = children(did); // Note: TreeMap is already sorted
        actv.put(name, did);
        JSONArray activeDecks = new JSONArray();
        for (Long n : actv.values()) {
            activeDecks.put(n);
        }
        mCol.getConf().put("activeDecks", activeDecks);
        mCol.setMod();
    }


    /**
     * All children of did as nodes of (key:name, value:id)
     *
     * TODO: There is likely no need for this collection to be a TreeMap. This method should not
     * need to sort on behalf of select().
     */
    public TreeMap<String, Long> children(long did) {
        String name = get(did).getString("name");
        TreeMap<String, Long> actv = new TreeMap<>();
        for (Deck g : all()) {
            if (g.getString("name").startsWith(name + "::")) {
                actv.put(g.getString("name"), g.getLong("id"));
            }
        }
        return actv;
    }

    public static class Node extends HashMap<Long, Node> {}

    private void gather(Node node, List<Long> arr) {
        for (Map.Entry<Long, Node> entry : node.entrySet()) {
            Node child = entry.getValue();
            arr.add(entry.getKey());
            gather(child, arr);
        }
    }

    public List<Long> childDids(long did, Node childMap) {
        List<Long> arr = new ArrayList<>();
        gather(childMap.get(did), arr);
        return arr;
    }


    public Node childMap() {

        Node childMap = new Node();

        // Go through all decks, sorted by name
        List<Deck> decks = all();

        Collections.sort(decks, DeckComparator.INSTANCE);

        for (Deck deck : decks) {
            Node node = new Node();
            childMap.put(deck.getLong("id"), node);

            List<String> parts = Arrays.asList(path(deck.getString("name")));
            if (parts.size() > 1) {
                String immediateParent = TextUtils.join("::", parts.subList(0, parts.size() - 1));
                long pid = byName(immediateParent).getLong("id");
                childMap.get(pid).put(deck.getLong("id"), node);
            }
        }

        return childMap;
    }

    /**
     * @return Names of ancestors of parents of name.
     */
    private String[] parentsNames(String name) {
        String[] parts = path(name);
        String[] parentsNames = new String[parts.length - 1];
        // Top level names have no parent, so it returns an empty list.
        // So the array size is 1 less than the number of parts.
        String prefix = "";
        for (int i = 0; i < parts.length - 1; i++) {
            prefix += parts[i];
            parentsNames[i] = prefix;
            prefix += "::";
        }
        return parentsNames;
    }

    /**
     * All parents of did.
     */
    public List<Deck> parents(long did) {
        // get parent and grandparent names
        String[] parents = parentsNames(get(did).getString("name"));
        // convert to objects
        List<Deck> oParents = new ArrayList<>(parents.length);
        for (int i = 0; i < parents.length; i++) {
            String parentName = parents[i];
            Deck deck = mNameMap.get(parentName);
            oParents.add(i, deck);
        }
        return oParents;
    }


    /**
     * Sync handling
     * ***********************************************************
     */


    public void beforeUpload() {
        boolean changed_decks = Utils.markAsUploaded(all());
        boolean changed_conf = Utils.markAsUploaded(allConf());
        if (changed_decks || changed_conf) {
            // shouldSave should always be called on both lists, for
            // its side effect. Thus the disjunction should not be
            // directly applied to the methods.
            save();
        }
    }


    /*
      Dynamic decks
     */


    /**
     * Return a new dynamic deck and set it as the current deck.
     */
    public long newDyn(String name) throws FilteredAncestor {
        long did = id(name, defaultDynamicDeck);
        select(did);
        return did;
    }


    public boolean isDyn(long did) {
        return get(did).isDyn();
    }

    /*
     * ******************************
     * utils methods
     * **************************************
     */
    private static final HashMap<String, String> normalized = new HashMap<>();
    public static String normalizeName(String name) {
        if (!normalized.containsKey(name)) {
            normalized.put(name, Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT));
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

    public static boolean isValidDeckName(@Nullable String deckName) {
        return deckName != null && !deckName.trim().isEmpty();
    }


    private static final HashMap<String, String> sParentCache = new HashMap<>();
    public static String parent(String deckName) {
        // method parent, from sched's method deckDueList in python
        if (!sParentCache.containsKey(deckName)) {
            List<String> parts = Arrays.asList(path(deckName));
            if (parts.size() < 2) {
                sParentCache.put(deckName, null);
            } else {
                parts = parts.subList(0, parts.size() - 1);
                String parentName = TextUtils.join("::", parts);
                sParentCache.put(deckName, parentName);
            }
        }
        return sParentCache.get(deckName);
    }

    public String getActualDescription() {
        return current().optString("desc","");
    }

    @Deprecated
    @RustCleanup("This exists in Rust as DecksDictProxy, but its usage is warned against")
    public HashMap<Long, Deck> getDecks() {
        return mDecks;
    }

    public Long[] allDynamicDeckIds() {
        Set<Long> ids = allIds();
        ArrayList<Long> validValues = new ArrayList<>(ids.size());
        for (Long did : ids) {
            if (isDyn(did)) {
                validValues.add(did);
            }
        }
        return validValues.toArray(new Long[0]);
    }

    public static boolean isDynamic(Collection col, long deckId) {
        return Decks.isDynamic(col.getDecks().get(deckId));
    }

    public static boolean isDynamic(Deck deck) {
        return deck.isDyn();
    }

    /** Retruns the fully qualified name of the subdeck, or null if unavailable */
    @Nullable
    public String getSubdeckName(long did, @Nullable String subdeckName) {
        if (TextUtils.isEmpty(subdeckName)) {
            return null;
        }
        String newName = subdeckName.replaceAll("\"", "");
        if (TextUtils.isEmpty(newName)) {
            return null;
        }
        Deck deck = get(did, false);
        if (deck == null) {
            return null;
        }
        return deck.getString("name") + DECK_SEPARATOR + subdeckName;
    }
}
