/***************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2016 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.R;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DB;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Deck;
import com.ichi2.utils.HashUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.libanki.Consts.CARD_TYPE_LRN;
import static com.ichi2.libanki.Consts.CARD_TYPE_NEW;
import static com.ichi2.libanki.Consts.CARD_TYPE_REV;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_DAY_LEARN_RELEARN;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_NEW;
import static com.ichi2.libanki.Consts.QUEUE_TYPE_REV;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class Anki2Importer extends Importer {

    private static final int MEDIAPICKLIMIT = 1024;

    private final String mDeckPrefix;
    private final boolean mAllowUpdate;
    private boolean mDupeOnSchemaChange;

    private static class NoteTriple {
        public final long mNid;
        public final long mMid;
        public final long mMod;
        public NoteTriple(long nid, long mod, long mid) {
            mNid = nid;
            mMod = mod;
            mMid = mid;
        }
    }
    private Map<String, NoteTriple> mNotes;

    private Map<Long, Long> mDecks;
    private Map<Long, Long> mModelMap;
    private Set<String> mIgnoredGuids;

    private int mDupes;
    private int mAdded;
    private int mUpdated;

    /** If importing SchedV1 into SchedV2 we need to reset the learning cards */
    private boolean mMustResetLearning;

    public Anki2Importer(Collection col, String file) {
        super(col, file);
        mNeedMapper = false;
        mDeckPrefix = null;
        mAllowUpdate = true;
        mDupeOnSchemaChange = false;
    }


    @Override
    public void run() throws ImportExportException {
        publishProgress(0, 0, 0);
        try {
            _prepareFiles();
            try {
                _import();
            } finally {
                mSrc.close(false);
            }
        } catch (Exception e) {
            Timber.e(e, "Exception while importing");
            throw new ImportExportException(e.getMessage());
        }
    }


    private void _prepareFiles() {
        boolean importingV2 = mFile.endsWith(".anki21");
        this.mMustResetLearning = false;

        mDst = mCol;
        mSrc = Storage.Collection(mContext, mFile);

        if (!importingV2 && mCol.schedVer() != 1) {
            // any scheduling included?
            if (mSrc.getDb().queryScalar("select 1 from cards where queue != " + QUEUE_TYPE_NEW + " limit 1") > 0) {
                this.mMustResetLearning = true;
            }
        }
    }


    private void _import() {
        mDecks = HashUtil.HashMapInit(mSrc.getDecks().count());
        try {
            // Use transactions for performance and rollbacks in case of error
            mDst.getDb().getDatabase().beginTransaction();
            mDst.getMedia().getDb().getDatabase().beginTransaction();

            if (!TextUtils.isEmpty(mDeckPrefix)) {
                long id = mDst.getDecks().id_safe(mDeckPrefix);
                mDst.getDecks().select(id);
            }
            Timber.i("Preparing Import");
            _prepareTS();
            _prepareModels();
            Timber.i("Importing notes");
            _importNotes();
            Timber.i("Importing Cards");
            _importCards();
            Timber.i("Importing Media");
            _importStaticMedia();
            publishProgress(100, 100, 25);
            Timber.i("Performing post-import");
            _postImport();
            publishProgress(100, 100, 50);
            mDst.getDb().getDatabase().setTransactionSuccessful();
            mDst.getMedia().getDb().getDatabase().setTransactionSuccessful();
        } catch (Exception err) {
            Timber.e(err, "_import() exception");
            throw err;
        } finally {
            // endTransaction throws about invalid transaction even when you check first!
            DB.safeEndInTransaction(mDst.getDb());
            DB.safeEndInTransaction(mDst.getMedia().getDb());
        }
        Timber.i("Performing vacuum/analyze");
        try {
            mDst.getDb().execute("vacuum");
        } catch (Exception e) {
            Timber.w(e);
            // This is actually not fatal but can fail since vacuum takes so much space
            // Allow the import to succeed but recommend the user run check database
            mLog.add(getRes().getString(R.string.import_succeeded_but_check_database, e.getLocalizedMessage()));
        }
        publishProgress(100, 100, 65);
        try {
            mDst.getDb().execute("analyze");
        } catch (Exception e) {
            Timber.w(e);
            // This is actually not fatal but can fail
            // Allow the import to succeed but recommend the user run check database
            mLog.add(getRes().getString(R.string.import_succeeded_but_check_database, e.getLocalizedMessage()));
        }
        publishProgress(100, 100, 75);
    }


    /**
     * Notes
     * ***********************************************************
     */

    private void _importNotes() {
        int noteCount = mDst.noteCount();
        // build guid -> (id,mod,mid) hash & map of existing note ids
        mNotes = HashUtil.HashMapInit(noteCount);
        Set<Long> existing = HashUtil.HashSetInit(noteCount);
        try (Cursor cur = mDst.getDb().query("select id, guid, mod, mid from notes")) {
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                String guid = cur.getString(1);
                long mod = cur.getLong(2);
                long mid = cur.getLong(3);
                mNotes.put(guid, new NoteTriple(id, mod, mid));
                existing.add(id);
            }
        }
        // we ignore updates to changed schemas. we need to note the ignored
        // guids, so we avoid importing invalid cards
        mIgnoredGuids = new HashSet<>();
        // iterate over source collection
        int nbNoteToImport = mSrc.noteCount();
        ArrayList<Object[]> add = new ArrayList<>(nbNoteToImport);
        int totalAddCount = 0;
        final int thresExecAdd = 1000;
        ArrayList<Object[]> update = new ArrayList<>(nbNoteToImport);
        int totalUpdateCount = 0;
        final int thresExecUpdate = 1000;
        ArrayList<Long> dirty = new ArrayList<>(nbNoteToImport);
        int totalDirtyCount = 0;
        final int thresExecDirty = 1000;
        int usn = mDst.usn();
        int dupes = 0;
        ArrayList<String> dupesIgnored = new ArrayList<>(nbNoteToImport);
        mDst.getDb().getDatabase().beginTransaction();
        try (Cursor cur = mSrc.getDb().getDatabase().query("select id, guid, mid, mod, tags, flds, sfld, csum, flags, data  from notes", null)) {
            // Counters for progress updates
            int total = cur.getCount();
            boolean largeCollection = total > 200;
            int onePercent = total/100;
            int i = 0;

            while (cur.moveToNext()) {
                // turn the db result into a mutable list
                long nid = cur.getLong(0);
                String guid = cur.getString(1);
                long mid = cur.getLong(2);
                long mod = cur.getLong(3);
                String tags = cur.getString(4);
                String flds = cur.getString(5);
                String sfld = cur.getString(6);
                long csum = cur.getLong(7);
                int flag = cur.getInt(8);
                String data = cur.getString(9);

                Pair<Boolean, Long> shouldAddAndNewMid = _uniquifyNote(guid, mid);
                boolean shouldAdd = shouldAddAndNewMid.first;
                mid = shouldAddAndNewMid.second;
                if (shouldAdd) {
                    // ensure nid is unique
                    while (existing.contains(nid)) {
                        nid += 999;
                    }
                    existing.add(nid);
                    // bump usn
                    // update media references in case of dupes
                    flds = _mungeMedia(mid, flds);
                    add.add(new Object[]{nid, guid, mid, mod, usn, tags, flds, sfld, csum, flag, data});
                    dirty.add(nid);
                    // note we have the added guid
                    mNotes.put(guid, new NoteTriple(nid, mod, mid));
                } else {
                    // a duplicate or changed schema - safe to update?
                    dupes += 1;
                    if (mAllowUpdate) {
                        NoteTriple n = mNotes.get(guid);
                        long oldNid = n.mNid;
                        long oldMod = n.mMod;
                        long oldMid = n.mMid;
                        // will update if incoming note more recent
                        if (oldMod < mod) {
                            // safe if note types identical
                            if (oldMid == mid) {
                                // incoming note should use existing id
                                nid = oldNid;
                                flds = _mungeMedia(mid, flds);
                                update.add(new Object[]{nid, guid, mid, mod, usn, tags, flds, sfld, csum, flag, data});
                                dirty.add(nid);
                            } else {
                                dupesIgnored.add(String.format("%s: %s",
                                        mCol.getModels().get(oldMid).getString("name"),
                                        flds.replace('\u001f', ',')));
                                mIgnoredGuids.add(guid);
                            }
                        }
                    }
                }
                i++;

                // add to col partially, so as to avoid OOM
                if (add.size() >= thresExecAdd) {
                    totalAddCount  += add.size();
                    addNotes(add);
                    add.clear();
                    Timber.d("add notes: %d", totalAddCount);
                }
                // add to col partially, so as to avoid OOM
                if (update.size() >= thresExecUpdate){
                    totalUpdateCount  += update.size();
                    updateNotes(update);
                    update.clear();
                    Timber.d("update notes: %d", totalUpdateCount);
                }
                // add to col partially, so as to avoid OOM
                if (dirty.size() >= thresExecDirty) {
                    totalDirtyCount  += dirty.size();
                    mDst.updateFieldCache(dirty);
                    mDst.getTags().registerNotes(dirty);
                    dirty.clear();
                    Timber.d("dirty notes: %d", totalDirtyCount);
                }

                if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                    // Calls to publishProgress are reasonably expensive due to res.getString()
                    publishProgress(i * 100 / total, 0, 0);
                }
            }
            publishProgress(100, 0, 0);

            // summarize partial add/update/dirty results for total values
            totalAddCount += add.size();
            totalUpdateCount += update.size();
            totalDirtyCount += dirty.size();

            if (dupes > 0) {
                mLog.add(getRes().getString(R.string.import_update_details, totalUpdateCount, dupes));
                if (!dupesIgnored.isEmpty()) {
                    mLog.add(getRes().getString(R.string.import_update_ignored));
                }
            }
            // export info for calling code
            mDupes = dupes;
            mAdded = totalAddCount;
            mUpdated = totalUpdateCount;
            Timber.d("add notes total:    %d", totalAddCount);
            Timber.d("update notes total: %d", totalUpdateCount);
            Timber.d("dirty notes total:  %d", totalDirtyCount);
            // add to col (for last chunk)
            addNotes(add);
            add.clear();
            updateNotes(update);
            update.clear();
            mDst.getDb().getDatabase().setTransactionSuccessful();

        } finally {
            DB.safeEndInTransaction(mDst.getDb());
        }

        mDst.updateFieldCache(dirty);
        mDst.getTags().registerNotes(dirty);
    }

    private void addNotes(List<Object[]> add) {
        mDst.getDb().executeManyNoTransaction("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", add);
    }

    private void updateNotes(List<Object[]> update) {
        mDst.getDb().executeManyNoTransaction("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", update);
    }

    // determine if note is a duplicate, and adjust mid and/or guid as required
    // returns true if note should be added and its mid
    private Pair<Boolean, Long> _uniquifyNote(@NonNull String origGuid, long srcMid) {
        long dstMid = _mid(srcMid);
        // duplicate Schemas?
        if (srcMid == dstMid) {
            return new Pair<>(!mNotes.containsKey(origGuid), srcMid);
        }
        // differing schemas and note doesn't exist?
        if (!mNotes.containsKey(origGuid)) {
            return new Pair<>(true, dstMid);
        }
		// schema changed; don't import
		mIgnoredGuids.add(origGuid);
		return new Pair<>(false, dstMid);
    }

    /*
      Models
      ***********************************************************
      Models in the two decks may share an ID but not a schema, so we need to
      compare the field & template signature rather than just rely on ID. If
      the schemas don't match, we increment the mid and try again, creating a
      new model if necessary.
     */

    /** Prepare index of schema hashes. */
    private void _prepareModels() {
        mModelMap = HashUtil.HashMapInit(mSrc.getModels().count());
    }


    /** Return local id for remote MID. */
    private long _mid(long srcMid) {
        // already processed this mid?
        if (mModelMap.containsKey(srcMid)) {
            return mModelMap.get(srcMid);
        }
        long mid = srcMid;
        Model srcModel = mSrc.getModels().get(srcMid);
        String srcScm = mSrc.getModels().scmhash(srcModel);
        while (true) {
            // missing from target col?
            if (!mDst.getModels().have(mid)) {
                // copy it over
                Model model = srcModel.deepClone();
                model.put("id", mid);
                model.put("mod", mCol.getTime().intTime());
                model.put("usn", mCol.usn());
                mDst.getModels().update(model);
                break;
            }
            // there's an existing model; do the schemas match?
            Model dstModel = mDst.getModels().get(mid);
            String dstScm = mDst.getModels().scmhash(dstModel);
            if (srcScm.equals(dstScm)) {
                // they do; we can reuse this mid
                Model model = srcModel.deepClone();
                model.put("id", mid);
                model.put("mod", mCol.getTime().intTime());
                model.put("usn", mCol.usn());
                mDst.getModels().update(model);
                break;
            }
            // as they don't match, try next id
            mid += 1;
        }
        // save map and return new mid
        mModelMap.put(srcMid, mid);
        return mid;
    }


    /*
     * Decks
     * ***********************************************************
     */

    /** Given did in src col, return local id. */
    private long _did(long did) {
        // already converted?
        if (mDecks.containsKey(did)) {
            return mDecks.get(did);
        }
        // get the name in src
        Deck g = mSrc.getDecks().get(did);
        String name = g.getString("name");
        // if there's a prefix, replace the top level deck
        if (!TextUtils.isEmpty(mDeckPrefix)) {
            List<String> parts = Arrays.asList(Decks.path(name));
            String tmpname = TextUtils.join("::", parts.subList(1, parts.size()));
            name = mDeckPrefix;
            if (!TextUtils.isEmpty(tmpname)) {
                name += "::" + tmpname;
            }
        }
        // Manually create any parents so we can pull in descriptions
        String head = "";
        List<String> parents = Arrays.asList(Decks.path(name));
        for (String parent : parents.subList(0, parents.size() -1)) {
            if (!TextUtils.isEmpty(head)) {
                head += "::";
            }
            head += parent;
            long idInSrc = mSrc.getDecks().id_safe(head);
            _did(idInSrc);
        }
        // create in local
        long newid = mDst.getDecks().id_safe(name);
        // pull conf over
        if (g.has("conf") && g.getLong("conf") != 1) {
            DeckConfig conf = mSrc.getDecks().getConf(g.getLong("conf"));
            mDst.getDecks().save(conf);
            mDst.getDecks().updateConf(conf);
            Deck g2 = mDst.getDecks().get(newid);
            g2.put("conf", g.getLong("conf"));
            mDst.getDecks().save(g2);
        }
        // save desc
        Deck deck = mDst.getDecks().get(newid);
        deck.put("desc", g.getString("desc"));
        mDst.getDecks().save(deck);
        // add to deck map and return
        mDecks.put(did, newid);
        return newid;
    }


    /**
     * Cards
     * ***********************************************************
     */

    private void _importCards() {
        if (mMustResetLearning) {
            try {
                mSrc.changeSchedulerVer(2);
            } catch (ConfirmModSchemaException e) {
                throw new RuntimeException("Changing the scheduler of an import should not cause schema modification", e);
            }
        }
        // build map of guid -> (ord -> cid) and used id cache
        /*
         * Since we can't use a tuple as a key in Java, we resort to indexing twice with nested maps.
         * Python: (guid, ord) -> cid
         * Java: guid -> ord -> cid
         */
        int nbCard = mDst.cardCount();
        Map<String, Map<Integer, Long>> cardsByGuid = HashUtil.HashMapInit(nbCard);
        Set<Long> existing = HashUtil.HashSetInit(nbCard);
        try (Cursor cur = mDst.getDb().query(
                    "select f.guid, c.ord, c.id from cards c, notes f " +
                    "where c.nid = f.id")) {
            while (cur.moveToNext()) {
                String guid = cur.getString(0);
                int ord = cur.getInt(1);
                long cid = cur.getLong(2);
                existing.add(cid);
                if (cardsByGuid.containsKey(guid)) {
                    cardsByGuid.get(guid).put(ord, cid);
                } else {
                    Map<Integer, Long> map = new HashMap<>(); // The size is at most the number of card type in the note type.
                    map.put(ord, cid);
                    cardsByGuid.put(guid, map);
                }
            }
        }
        // loop through src
        int nbCardsToImport = mSrc.cardCount();
        List<Object[]> cards = new ArrayList<>(nbCardsToImport);
        int totalCardCount = 0;
        final int thresExecCards = 1000;
        List<Object[]> revlog = new ArrayList<>(mSrc.getSched().logCount());
        int totalRevlogCount = 0;
        final int thresExecRevlog = 1000;
        int usn = mDst.usn();
        long aheadBy = mSrc.getSched().getToday() - mDst.getSched().getToday();
        mDst.getDb().getDatabase().beginTransaction();
        try (Cursor cur = mSrc.getDb().query(
                    "select f.guid, c.id, c.did, c.ord, c.type, c.queue, c.due, c.ivl, c.factor, c.reps, c.lapses, c.left, c.odue, c.odid, c.flags, c.data from cards c, notes f " +
                    "where c.nid = f.id")) {

            // Counters for progress updates
            int total = cur.getCount();
            boolean largeCollection = total > 200;
            int onePercent = total/100;
            int i = 0;

            while (cur.moveToNext()) {
                String guid = cur.getString(0);
                long cid = cur.getLong(1);
                long scid = cid; // To keep track of card id in source
                long did = cur.getLong(2);
                int ord = cur.getInt(3);
                @Consts.CARD_TYPE int type = cur.getInt(4);
                @Consts.CARD_QUEUE int queue = cur.getInt(5);
                long due = cur.getLong(6);
                long ivl = cur.getLong(7);
                long factor = cur.getLong(8);
                int reps = cur.getInt(9);
                int lapses = cur.getInt(10);
                int left = cur.getInt(11);
                long odue = cur.getLong(12);
                long odid = cur.getLong(13);
                int flags = cur.getInt(14);
                String data = cur.getString(15);

                if (mIgnoredGuids.contains(guid)) {
                    continue;
                }
                // does the card's note exist in dst col?
                if (!mNotes.containsKey(guid)) {
                    continue;
                }
                NoteTriple dnid = mNotes.get(guid);
                // does the card already exist in the dst col?
                if (cardsByGuid.containsKey(guid) && cardsByGuid.get(guid).containsKey(ord)) {
                    // fixme: in future, could update if newer mod time
                    continue;
                }
                // ensure the card id is unique
                while (existing.contains(cid)) {
                    cid += 999;
                }
                existing.add(cid);
                // update cid, nid, etc
                long nid = mNotes.get(guid).mNid;
                did = _did(did);
                long mod = mCol.getTime().intTime();
                // review cards have a due date relative to collection
                if (queue == QUEUE_TYPE_REV || queue == QUEUE_TYPE_DAY_LEARN_RELEARN || type == CARD_TYPE_REV) {
                    due -= aheadBy;
                }
                // odue needs updating too
                if (odue != 0) {
                    odue -= aheadBy;
                }
                // if odid true, convert card from filtered to normal
                if (odid != 0) {
                    // odid
                    odid = 0;
                    // odue
                    due = odue;
                    odue = 0;
                    // queue
                    if (type == CARD_TYPE_LRN) { // type
                        queue = QUEUE_TYPE_NEW;
                    } else {
                        queue = type;
                    }
                    // type
                    if (type == CARD_TYPE_LRN) {
                        type = CARD_TYPE_NEW;
                    }
                }
                cards.add(new Object[]{cid, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data});
                // we need to import revlog, rewriting card ids and bumping usn
                try (Cursor cur2 = mSrc.getDb().query("select * from revlog where cid = " + scid)) {
                    while (cur2.moveToNext()) {
                        Object[] rev = new Object[] { cur2.getLong(0), cur2.getLong(1), cur2.getInt(2), cur2.getInt(3),
                                cur2.getLong(4), cur2.getLong(5), cur2.getLong(6), cur2.getLong(7), cur2.getInt(8) };
                        rev[1] = cid;
                        rev[2] = mDst.usn();
                        revlog.add(rev);
                    }
                }
                i++;
                // apply card changes partially
                if (cards.size() >= thresExecCards) {
                    totalCardCount += cards.size();
                    insertCards(cards);
                    cards.clear();
                    Timber.d("add cards: %d", totalCardCount);
                }
                // apply revlog changes partially
                if (revlog.size() >= thresExecRevlog) {
                    totalRevlogCount += revlog.size();
                    insertRevlog(revlog);
                    revlog.clear();
                    Timber.d("add revlog: %d", totalRevlogCount);
                }

                if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                    publishProgress(100, i * 100 / total, 0);
                }
            }
            publishProgress(100, 100, 0);

            // count total values
            totalCardCount += cards.size();
            totalRevlogCount += revlog.size();
            Timber.d("add cards total:  %d", totalCardCount);
            Timber.d("add revlog total: %d", totalRevlogCount);
            // apply (for last chunk)
            insertCards(cards);
            cards.clear();
            insertRevlog(revlog);
            revlog.clear();
            mLog.add(getRes().getString(R.string.import_complete_count, totalCardCount));
            mDst.getDb().getDatabase().setTransactionSuccessful();
        } finally {
            DB.safeEndInTransaction(mDst.getDb());
        }
    }

    private void insertCards(List<Object[]> cards) {
        mDst.getDb().executeManyNoTransaction("insert or ignore into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", cards);
    }

    private void insertRevlog(List<Object[]> revlog) {
        mDst.getDb().executeManyNoTransaction("insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", revlog);
    }


    /**
     * Media
     * ***********************************************************
     */

    // note: this func only applies to imports of .anki2. For .apkg files, the
    // apkg importer does the copying
    private void _importStaticMedia() {
        // Import any '_foo' prefixed media files regardless of whether
        // they're used on notes or not
        String dir = mSrc.getMedia().dir();
        if (!new File(dir).exists()) {
            return;
        }
        for (File f : new File(dir).listFiles()) {
            String fname = f.getName();
            if (fname.startsWith("_") && ! mDst.getMedia().have(fname)) {
                try (BufferedInputStream data = _srcMediaData(fname)) {
                    _writeDstMedia(fname, data);
                } catch (IOException e) {
                    Timber.w(e, "Failed to close stream");
                }
            }
        }
    }


    private BufferedInputStream _mediaData(String fname, String dir) {
        if (dir == null) {
            dir = mSrc.getMedia().dir();
        }
        String path = new File(dir, fname).getAbsolutePath();
        try {
            return new BufferedInputStream(new FileInputStream(path), MEDIAPICKLIMIT * 2);
        } catch (IOException e) {
            Timber.w(e);
            return null;
        }
    }


    /**
     * Data for FNAME in src collection.
     */
    protected BufferedInputStream _srcMediaData(String fname) {
        return _mediaData(fname, mSrc.getMedia().dir());
    }


    /**
     * Data for FNAME in dst collection.
     */
    private BufferedInputStream _dstMediaData(String fname) {
        return _mediaData(fname, mDst.getMedia().dir());
    }


    private void _writeDstMedia(String fname, BufferedInputStream data) {
        try {
            String path = new File(mDst.getMedia().dir(), Utils.nfcNormalized(fname)).getAbsolutePath();
            Utils.writeToFile(data, path);
            // Mark file addition to media db (see note in Media.java)
            mDst.getMedia().markFileAdd(fname);
        } catch (IOException e) {

            // the user likely used subdirectories
            Timber.e(e, "Error copying file %s.", fname);

            // If we are out of space, we should re-throw
            if (e.getCause() != null && e.getCause().getMessage().contains("No space left on device")) {
                // we need to let the user know why we are failing
                Timber.e("We are out of space, bubbling up the file copy exception");
                throw new RuntimeException(e);
            }
        }
    }


    // running splitFields() on every note is fairly expensive and actually not necessary
    private String _mungeMedia(long mid, String fields) {
        for (Pattern p : Media.REGEXPS) {
            Matcher m = p.matcher(fields);
            StringBuffer sb = new StringBuffer();
            int fnameIdx = Media.indexOfFname(p);
            while (m.find()) {
                String fname = m.group(fnameIdx);
                try (BufferedInputStream srcData = _srcMediaData(fname);
                     BufferedInputStream dstData = _dstMediaData(fname)) {
                    if (srcData == null) {
                        // file was not in source, ignore
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                        continue;
                    }
                    // if model-local file exists from a previous import, use that
                    String[] split = Utils.splitFilename(fname);
                    String name = split[0];
                    String ext = split[1];

                    String lname = String.format(Locale.US, "%s_%s%s", name, mid, ext);
                    if (mDst.getMedia().have(lname)) {
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0).replace(fname, lname)));
                        continue;
                    } else if (dstData == null || compareMedia(srcData, dstData)) { // if missing or the same, pass unmodified
                        // need to copy?
                        if (dstData == null) {
                            _writeDstMedia(fname, srcData);
                        }
                        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                        continue;
                    }
                    // exists but does not match, so we need to dedupe
                    _writeDstMedia(lname, srcData);
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0).replace(fname, lname)));
                } catch (IOException e) {
                    Timber.w(e, "Failed to close stream");
                }
            }
            m.appendTail(sb);
            fields = sb.toString();
        }
        return fields;
    }


    /**
     * Post-import cleanup
     * ***********************************************************
     */

    private void _postImport() {
        for (long did : mDecks.values()) {
            mCol.getSched().maybeRandomizeDeck(did);
        }
        // make sure new position is correct
        mDst.set_config("nextPos", mDst.getDb().queryLongScalar(
                "select max(due)+1 from cards where type = " + CARD_TYPE_NEW));
        mDst.save();
    }


    /**
     * The methods below are not in LibAnki.
     * ***********************************************************
     */


    private boolean compareMedia(BufferedInputStream lhis, BufferedInputStream rhis) {
        byte[] lhbytes = _mediaPick(lhis);
        byte[] rhbytes = _mediaPick(rhis);
        return Arrays.equals(lhbytes, rhbytes);
    }


    /**
     * Return the contents of the given input stream, limited to Anki2Importer.MEDIAPICKLIMIT bytes This is only used
     * for comparison of media files with the limited resources of mobile devices
     */
    private byte[] _mediaPick(BufferedInputStream is) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(MEDIAPICKLIMIT * 2);
            byte[] buf = new byte[MEDIAPICKLIMIT];
            int readLen;
            int readSoFar = 0;
            is.mark(MEDIAPICKLIMIT * 2);
            while (true) {
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
        } catch (IOException e) {
            Timber.w(e);
            return null;
        }
    }


    /**
     * @param notesDone Percentage of notes complete.
     * @param cardsDone Percentage of cards complete.
     * @param postProcess Percentage of remaining tasks complete.
     */
    protected void publishProgress(int notesDone, int cardsDone, int postProcess) {
        if (mProgress != null) {
            mProgress.publishProgress(getRes().getString(R.string.import_progress,
                    notesDone, cardsDone, postProcess));
        }
    }


    /* The methods below are only used for testing. */

    public void setDupeOnSchemaChange(boolean b) {
        mDupeOnSchemaChange = b;
    }


    public int getDupes() {
        return mDupes;
    }


    public int getAdded() {
        return mAdded;
    }


    public int getUpdated() {
        return mUpdated;
    }
}
