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
import com.ichi2.libanki.backend.exception.DeckRenameException;

import com.ichi2.utils.DeckComparator;
import com.ichi2.utils.HashUtil;
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
public class Decks extends DeckManager {

    /** Invalid id, represents an id on an unfound deck */
    public static final long NOT_FOUND_DECK_ID = -1L;
    /** Configuration saving the current deck */
    public static final String CURRENT_DECK = "curDeck";
    /** Configuration saving the set of active decks (i.e. current decks and its descendants) */
    public static final String ACTIVE_DECKS = "activeDecks";


    //not in libAnki
    @SuppressWarnings("WeakerAccess")
    public static final String DECK_SEPARATOR = "::";

    public static final String DEFAULT_DECK = ""
            + "{"
                + "\"newToday\": [0, 0]," // currentDay, count
                + "\"revToday\": [0, 0],"
                + "\"lrnToday\": [0, 0],"
                + "\"timeToday\": [0, 0]," // time in ms
                + "\"conf\": 1,"
                + "\"usn\": 0,"
                + "\"desc\": \"\","
                + "\"dyn\": 0," // anki uses int/bool interchangably here
                + "\"collapsed\": false,"
                + "\"browserCollapsed\": false,"
                // added in beta11
                + "\"extendNew\": 0,"
                + "\"extendRev\": 0"
            + "}";

    private static final String defaultDynamicDeck = ""
            + "{"
                + "\"newToday\": [0, 0],"
                + "\"revToday\": [0, 0],"
                + "\"lrnToday\": [0, 0],"
                + "\"timeToday\": [0, 0],"
                + "\"collapsed\": false,"
                + "\"dyn\": 1,"
                + "\"desc\": \"\","
                + "\"usn\": 0,"
                + "\"delays\": null,"
                + "\"separate\": true,"
                // list of (search, limit, order); we only use first element for now
                + "\"terms\": [[\"\", 100, 0]],"
                + "\"resched\": true,"
                + "\"previewDelay\": 10,"
                + "\"browserCollapsed\": false"
            + "}";

    public static final String DEFAULT_CONF = ""
            + "{"
                + "\"name\": \"Default\","
                + "\"dyn\": false," // previously optional. Default was false
                + "\"new\": {"
                    + "\"delays\": [1, 10],"
                    + "\"ints\": [1, 4, 7]," // 7 is not currently used
                    + "\"initialFactor\": "+Consts.STARTING_FACTOR+","
                    + "\"order\": " + Consts.NEW_CARDS_DUE + ","
                    + "\"perDay\": 20,"
                    // may not be set on old decks
                    + "\"bury\": false"
                + "},"
                + "\"lapse\": {"
                    + "\"delays\": [10],"
                    + "\"mult\": 0,"
                    + "\"minInt\": 1,"
                    + "\"leechFails\": 8,"
                    // type 0=suspend, 1=tagonly
                    + "\"leechAction\": " + Consts.LEECH_TAGONLY
                + "},"
                + "\"rev\": {"
                    + "\"perDay\": 200,"
                    + "\"ease4\": 1.3,"
                    + "\"hardFactor\": 1.2,"
                    + "\"ivlFct\": 1,"
                    + "\"maxIvl\": 36500,"
                    // may not be set on old decks
                    + "\"bury\": false"
                + "},"
                + "\"maxTaken\": 60,"
                + "\"timer\": 0,"
                + "\"autoplay\": true,"
                + "\"replayq\": true,"
                + "\"mod\": 0,"
                + "\"usn\": 0"
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
            mNameMap = HashUtil.HashMapInit(size);
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


    @Override
    public void load(@NonNull String decks, @NonNull String dconf) {
        JSONObject decksarray = new JSONObject(decks);
        JSONArray ids = decksarray.names();
        mDecks = HashUtil.HashMapInit(decksarray.length());
        if (ids != null) {
            for (String id : ids.stringIterable()) {
                Deck o = new Deck(decksarray.getJSONObject(id));
                long longId = Long.parseLong(id);
                mDecks.put(longId, o);
            }
        }
        mNameMap = NameMap.constructor(mDecks.values());
        JSONObject confarray = new JSONObject(dconf);
        ids = confarray.names();
        mDconf = HashUtil.HashMapInit(confarray.length());
        if (ids != null) {
            for (String id : ids.stringIterable()) {
                mDconf.put(Long.parseLong(id), new DeckConfig(confarray.getJSONObject(id), DeckConfig.Source.DECK_CONFIG));
            }
        }
        mChanged = false;
    }


    /** {@inheritDoc} */
    @Override
    public void save() {
        save((JSONObject) null);
    }

    /** {@inheritDoc} */
    @Override
    public void save(@NonNull Deck g) {
        save((JSONObject) g);
    }

    /** {@inheritDoc} */
    @Override
    public void save(@NonNull DeckConfig g) {
        save((JSONObject) g);
    }

    private void save(JSONObject g) {
        if (g != null) {
            g.put("mod", mCol.getTime().intTime());
            g.put("usn", mCol.usn());
        }
        mChanged = true;
    }


    @Override
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

    @Nullable
    @Override
    public Long id_for_name(@NonNull String name) {
        name = usable_name(name);
        Deck deck = byName(name);
        if (deck != null) {
            return deck.getLong("id");
        }
        return null;
    }

    @Override
    public long id(@NonNull String name) throws DeckRenameException {
        return id(name, DEFAULT_DECK);
    }

    private String usable_name(@NonNull String name) {
        name = strip(name);
        name = name.replace("\"", "");
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        return name;
    }

    /**
     * Add a deck with NAME. Reuse deck if already exists. Return id as int.
     */
    public long id(@NonNull String name, @NonNull String type) throws DeckRenameException {
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
    private long id_create_name_valid(@NonNull String name, @NonNull String type) {
        long id;
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


    /** {@inheritDoc} */
    @Override
    public long id_safe(@NonNull String name, @NonNull String type)  {
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

    /** {@inheritDoc} */
    @Override
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

    /** {@inheritDoc} */
    @NonNull
    @Override
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


    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<Deck> all() {
        return new ArrayList<>(mDecks.values());
    }


    @NonNull
    @Override
    public Set<Long> allIds() {
        return mDecks.keySet();
    }


    @Override
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


    /** {@inheritDoc} */
    @Override
    public int count() {
        return mDecks.size();
    }

    @Override
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


    /** {@inheritDoc} */
    @Override
    @CheckResult
    public @Nullable Deck byName(String name) {
        return mNameMap.get(name);
    }


    /** {@inheritDoc} */
    @Override
    public void update(@NonNull Deck g) {
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

    /** {@inheritDoc} */
    @Override
    public void rename(@NonNull Deck g, @NonNull String newName) throws DeckRenameException {
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
        newName = _ensureParents(newName);

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
     * @throws DeckRenameException if a parent is filtered
     */
    @NonNull
    @VisibleForTesting
    protected String _ensureParents(@NonNull String name) throws DeckRenameException {
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
                throw DeckRenameException.filteredAncestor(name, s);
            }
        }
        name = s + "::" + path[path.length - 1];
        return name;
    }


    /** {@inheritDoc} */
    @NonNull
    @VisibleForTesting
    protected String _ensureParentsNotFiltered(String name) {
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


    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<DeckConfig> allConf() {
        return new ArrayList<>(mDconf.values());
    }


    @NonNull
    @Override
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
        return new DeckConfig(deck, DeckConfig.Source.DECK_EMBEDDED);
    }


    @Override
    public DeckConfig getConf(long confId) {
        return mDconf.get(confId);
    }


    @Override
    public void updateConf(@NonNull DeckConfig g) {
        mDconf.put(g.getLong("id"), g);
        save();
    }

    /** {@inheritDoc} */
    @Override
    public long confId(@NonNull String name, @NonNull String cloneFrom) {
        long id;
        DeckConfig c = new DeckConfig(cloneFrom, DeckConfig.Source.DECK_CONFIG);
        do {
            id = mCol.getTime().intTimeMS();
        } while (mDconf.containsKey(id));
        c.put("id", id);
        c.put("name", name);
        mDconf.put(id, c);
        save(c);
        return id;
    }


    /** {@inheritDoc} */
    @Override
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


    @Override
    public void setConf(@NonNull Deck grp, long id) {
        grp.put("conf", id);
        save(grp);
    }


    @NonNull
    @Override
    public List<Long> didsForConf(DeckConfig conf) {
        List<Long> dids = new ArrayList<>();
        for(Deck deck : mDecks.values()) {
            if (deck.has("conf") && deck.getLong("conf") == conf.getLong("id")) {
                dids.add(deck.getLong("id"));
            }
        }
        return dids;
    }


    @Override
    public void restoreToDefault(@NonNull DeckConfig conf) {
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

    @NonNull
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


    @NonNull
    @Override
    public List<Long> cids(long did, boolean children) {
        if (!children) {
            return mCol.getDb().queryLongList("select id from cards where did=?", did);
        }
        java.util.Collection<Long> values = children(did).values();
        List<Long> dids = new ArrayList<>(values.size() + 1);
        dids.add(did);
        dids.addAll(values);
        return mCol.getDb().queryLongList("select id from cards where did in " + Utils.ids2str(dids));
    }


    private static final Pattern spaceAroundSeparator = Pattern.compile("\\s*::\\s*");
    @VisibleForTesting
    static String strip(@NonNull String deckName) {
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
        Map<String, Deck> names = HashUtil.HashMapInit(decks.size());

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

    @Override
    public void checkIntegrity() {
        _recoverOrphans();
        _checkDeckTree();
    }


    /*
      Deck selection
      ***********************************************************
     */


    /** {@inheritDoc} */
    @NonNull
    @Override
    public LinkedList<Long> active() {
        JSONArray activeDecks = mCol.get_config_array(ACTIVE_DECKS);
        LinkedList<Long> result = new LinkedList<>();
        addAll(result, activeDecks.longIterable());
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public long selected() {
        return mCol.get_config_long(CURRENT_DECK);
    }


    @NonNull
    @Override
    public Deck current() {
        if (get(selected()) == null || !mDecks.containsKey(selected())) {
            select(Consts.DEFAULT_DECK_ID); // Select default deck if the selected deck is null
        }
        return get(selected());
    }


    /** {@inheritDoc} */
    @Override
    public void select(long did) {
        String name = mDecks.get(did).getString("name");

        // current deck
        mCol.set_config(CURRENT_DECK, did);
        // and active decks (current + all children)
        TreeMap<String, Long> actv = children(did); // Note: TreeMap is already sorted
        actv.put(name, did);
        JSONArray activeDecks = new JSONArray();
        for (Long n : actv.values()) {
            activeDecks.put(n);
        }

        mCol.set_config(ACTIVE_DECKS, activeDecks);
    }


    /** {@inheritDoc} */
    @NonNull
    @Override
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

    @NonNull
    @Override
    public List<Long> childDids(long did, Node childMap) {
        List<Long> arr = new ArrayList<>();
        gather(childMap.get(did), arr);
        return arr;
    }


    @NonNull
    @Override
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

    /** {@inheritDoc} */
    @NonNull
    @Override
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


    @Override
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


    /** {@inheritDoc} */
    @Override
    public long newDyn(String name) throws DeckRenameException {
        long did = id(name, defaultDynamicDeck);
        select(did);
        return did;
    }


    @Override
    public boolean isDyn(long did) {
        return get(did).isDyn();
    }


    @Override
    public void update_active() {
        // intentionally blank
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

    @VisibleForTesting
    @RustCleanup("This exists in Rust as DecksDictProxy, but its usage is warned against")
    public HashMap<Long, Deck> getDecks() {
        return mDecks;
    }

    public static boolean isDynamic(Collection col, long deckId) {
        return Decks.isDynamic(col.getDecks().get(deckId));
    }

    public static boolean isDynamic(Deck deck) {
        return deck.isDyn();
    }
}
