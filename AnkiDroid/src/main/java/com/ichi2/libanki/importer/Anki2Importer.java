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

import com.ichi2.anki.R;
import com.ichi2.anki.exception.ImportExportException;
import com.ichi2.async.CollectionTask;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Decks;
import com.ichi2.libanki.Media;
import com.ichi2.libanki.Storage;
import com.ichi2.libanki.Utils;

import com.ichi2.utils.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

@SuppressWarnings({"PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions","PMD.ExcessiveMethodLength",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class Anki2Importer extends Importer {

    private static final int GUID = 1;
    private static final int MID = 2;
    private static final int MOD = 3;

    private static final int MEDIAPICKLIMIT = 1024;

    private String mDeckPrefix;
    private boolean mAllowUpdate;
    private boolean mDupeOnSchemaChange;

    private Map<String, Object[]> mNotes;

    /**
     * Since we can't use a tuple as a key in Java, we resort to indexing twice with nested maps.
     * Python: (guid, ord) -> cid
     * Java: guid -> ord -> cid
     */
    @SuppressWarnings("PMD.SingularField")
    private Map<String, Map<Integer, Long>> mCards;
    private Map<Long, Long> mDecks;
    private Map<Long, Long> mModelMap;
    private Map<String, String> mChangedGuids;
    private Map<String, Boolean> mIgnoredGuids;

    private int mDupes;
    private int mAdded;
    private int mUpdated;

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
        } catch (RuntimeException e) {
            Timber.e(e, "RuntimeException while importing");
            throw new ImportExportException(e.getMessage());
        }
    }


    private void _prepareFiles() throws ImportExportException {
        boolean importingV2 = mFile.endsWith(".anki21");
        if (importingV2 && mCol.schedVer() == 1) {
            throw new ImportExportException(mContext.getString(R.string.import_needs_v2));
        }

        mDst = mCol;
        mSrc = Storage.Collection(mContext, mFile);

        if (!importingV2 && mCol.schedVer() != 1) {
            if (mSrc.getDb().queryScalar("select 1 from cards where queue != " + Consts.QUEUE_TYPE_NEW + " limit 1") > 0) {
                mSrc.close(false);
                throw new ImportExportException(mContext.getString(R.string.import_cannot_with_v2));
            }
        }
    }


    private void _import() {
        mDecks = new HashMap<>();
        try {
            // Use transactions for performance and rollbacks in case of error
            mDst.getDb().getDatabase().beginTransaction();
            mDst.getMedia().getDb().getDatabase().beginTransaction();

            if (!TextUtils.isEmpty(mDeckPrefix)) {
                long id = mDst.getDecks().id(mDeckPrefix);
                mDst.getDecks().select(id);
            }
            _prepareTS();
            _prepareModels();
            _importNotes();
            _importCards();
            _importStaticMedia();
            publishProgress(100, 100, 25);
            _postImport();
            publishProgress(100, 100, 50);
            mDst.getDb().getDatabase().setTransactionSuccessful();
            mDst.getMedia().getDb().getDatabase().setTransactionSuccessful();
        } finally {
            mDst.getDb().getDatabase().endTransaction();
            mDst.getMedia().getDb().getDatabase().endTransaction();
        }
        mDst.getDb().execute("vacuum");
        publishProgress(100, 100, 65);
        mDst.getDb().execute("analyze");
        publishProgress(100, 100, 75);
    }


    /**
     * Notes
     * ***********************************************************
     */

    private void _importNotes() {
        // build guid -> (id,mod,mid) hash & map of existing note ids
        mNotes = new HashMap<>();
        Map<Long, Boolean> existing = new HashMap<>();
        Cursor cur = null;
        try {
            cur = mDst.getDb().getDatabase().query("select id, guid, mod, mid from notes", null);
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                String guid = cur.getString(1);
                long mod = cur.getLong(2);
                long mid = cur.getLong(3);
                mNotes.put(guid, new Object[] { id, mod, mid });
                existing.put(id, true);
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        // we may need to rewrite the guid if the model schemas don't match,
        // so we need to keep track of the changes for the card import stage
        mChangedGuids = new HashMap<>();
        // we ignore updates to changed schemas. we need to note the ignored
        // guids, so we avoid importing invalid cards
        mIgnoredGuids = new HashMap<>();
        // iterate over source collection
        ArrayList<Object[]> add = new ArrayList<>();
        ArrayList<Object[]> update = new ArrayList<>();
        ArrayList<Long> dirty = new ArrayList<>();
        int usn = mDst.usn();
        int dupes = 0;
        ArrayList<String> dupesIgnored = new ArrayList<>();
        try {
            cur = mSrc.getDb().getDatabase().query("select * from notes", null);

            // Counters for progress updates
            int total = cur.getCount();
            boolean largeCollection = total > 200;
            int onePercent = total/100;
            int i = 0;

            while (cur.moveToNext()) {
                // turn the db result into a mutable list
                Object[] note = new Object[]{cur.getLong(0), cur.getString(1), cur.getLong(2),
                        cur.getLong(3), cur.getInt(4), cur.getString(5), cur.getString(6),
                        cur.getString(7), cur.getLong(8), cur.getInt(9), cur.getString(10)};
                boolean shouldAdd = _uniquifyNote(note);
                if (shouldAdd) {
                    // ensure id is unique
                    while (existing.containsKey(note[0])) {
                        note[0] = ((Long) note[0]) + 999;
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
                    // a duplicate or changed schema - safe to update?
                    dupes += 1;
                    if (mAllowUpdate) {
                        Object[] n = mNotes.get(note[GUID]);
                        long oldNid = (Long) n[0];
                        long oldMod = (Long) n[1];
                        long oldMid = (Long) n[2];
                        // will update if incoming note more recent
                        if (oldMod < (Long) note[MOD]) {
                            // safe if note types identical
                            if (oldMid == (Long) note[MID]) {
                                // incoming note should use existing id
                                note[0] = oldNid;
                                note[4] = usn;
                                note[6] = _mungeMedia((Long) note[MID], (String) note[6]);
                                update.add(note);
                                dirty.add((Long) note[0]);
                            } else {
                                dupesIgnored.add(String.format("%s: %s",
                                        mCol.getModels().get(oldMid).getString("name"),
                                        ((String) note[6]).replace("\u001f", ",")));
                                mIgnoredGuids.put((String) note[GUID], true);
                            }
                        }
                    }
                }
                i++;
                if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                    // Calls to publishProgress are reasonably expensive due to res.getString()
                    publishProgress(i * 100 / total, 0, 0);
                }
            }
            publishProgress(100, 0, 0);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        if (dupes > 0) {
            //int up = update.size(); // unused upstream as well, leaving for upstream comparison only
            mLog.add(getRes().getString(R.string.import_update_details, update.size(), dupes));
            if (dupesIgnored.size() > 0) {
                mLog.add(getRes().getString(R.string.import_update_ignored));
                // TODO: uncomment this and fix above string if we implement a detailed
                // log viewer dialog type.
                //mLog.addAll(dupesIgnored);
            }
        }
        // export info for calling code
        mDupes = dupes;
        mAdded = add.size();
        mUpdated = update.size();
        // add to col
        mDst.getDb().executeMany("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", add);
        mDst.getDb().executeMany("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", update);
        long[] das = Utils.arrayList2array(dirty);
        mDst.updateFieldCache(das);
        mDst.getTags().registerNotes(das);
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
        // differing schemas and note doesn't exist?
        note[MID] = dstMid;
        if (!mNotes.containsKey(origGuid)) {
            return true;
        }
		// schema changed; don't import
		mIgnoredGuids.put(origGuid, true);
		return false;
    }


    /**
     * Models
     * ***********************************************************
     * Models in the two decks may share an ID but not a schema, so we need to
     * compare the field & template signature rather than just rely on ID. If
     * the schemas don't match, we increment the mid and try again, creating a
     * new model if necessary.
     */

    /** Prepare index of schema hashes. */
    private void _prepareModels() {
        mModelMap = new HashMap<>();
    }


    /** Return local id for remote MID. */
    private long _mid(long srcMid) {
        // already processed this mid?
        if (mModelMap.containsKey(srcMid)) {
            return mModelMap.get(srcMid);
        }
        long mid = srcMid;
        JSONObject srcModel = mSrc.getModels().get(srcMid);
        String srcScm = mSrc.getModels().scmhash(srcModel);
        while (true) {
            // missing from target col?
            if (!mDst.getModels().have(mid)) {
                // copy it over
                JSONObject model = srcModel.deepClone();
                model.put("id", mid);
                model.put("mod", Utils.intTime());
                model.put("usn", mCol.usn());
                mDst.getModels().update(model);
                break;
            }
            // there's an existing model; do the schemas match?
            JSONObject dstModel = mDst.getModels().get(mid);
            String dstScm = mDst.getModels().scmhash(dstModel);
            if (srcScm.equals(dstScm)) {
                // they do; we can reuse this mid
                JSONObject model = srcModel.deepClone();
                model.put("id", mid);
                model.put("mod", Utils.intTime());
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


    /**
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
        JSONObject g = mSrc.getDecks().get(did);
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
            long idInSrc = mSrc.getDecks().id(head);
            _did(idInSrc);
        }
        // create in local
        long newid = mDst.getDecks().id(name);
        // pull conf over
        if (g.has("conf") && g.getLong("conf") != 1) {
            JSONObject conf = mSrc.getDecks().getConf(g.getLong("conf"));
            mDst.getDecks().save(conf);
            mDst.getDecks().updateConf(conf);
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
    }


    /**
     * Cards
     * ***********************************************************
     */

    private void _importCards() {
        // build map of guid -> (ord -> cid) and used id cache
        mCards = new HashMap<>();
        Map<Long, Boolean> existing = new HashMap<>();
        Cursor cur = null;
        try {
            cur = mDst.getDb().getDatabase().query(
                    "select f.guid, c.ord, c.id from cards c, notes f " +
                    "where c.nid = f.id", null);
            while (cur.moveToNext()) {
                String guid = cur.getString(0);
                int ord = cur.getInt(1);
                long cid = cur.getLong(2);
                existing.put(cid, true);
                if (mCards.containsKey(guid)) {
                    mCards.get(guid).put(ord, cid);
                } else {
                    Map<Integer, Long> map = new HashMap<>();
                    map.put(ord, cid);
                    mCards.put(guid, map);
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        // loop through src
        List<Object[]> cards = new ArrayList<>();
        List<Object[]> revlog = new ArrayList<>();
        int cnt = 0;
        int usn = mDst.usn();
        long aheadBy = mSrc.getSched().getToday() - mDst.getSched().getToday();
        try {
            cur = mSrc.getDb().getDatabase().query(
                    "select f.guid, f.mid, c.* from cards c, notes f " +
                    "where c.nid = f.id", null);

            // Counters for progress updates
            int total = cur.getCount();
            boolean largeCollection = total > 200;
            int onePercent = total/100;
            int i = 0;

            while (cur.moveToNext()) {
                Object[] card = new Object[] { cur.getString(0), cur.getLong(1), cur.getLong(2),
                        cur.getLong(3), cur.getLong(4), cur.getInt(5), cur.getLong(6), cur.getInt(7),
                        cur.getInt(8), cur.getInt(9), cur.getLong(10), cur.getLong(11), cur.getLong(12),
                        cur.getInt(13), cur.getInt(14), cur.getInt(15), cur.getLong(16),
                        cur.getLong(17), cur.getInt(18), cur.getString(19) };
                String guid = (String) card[0];
                if (mChangedGuids.containsKey(guid)) {
                    guid = mChangedGuids.get(guid);
                }
                if (mIgnoredGuids.containsKey(guid)) {
                    continue;
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
                System.arraycopy(oc, 2, card, 0, card.length);
                long scid = (Long) card[0];
                // ensure the card id is unique
                while (existing.containsKey(card[0])) {
                    card[0] = (Long) card[0] + 999;
                }
                existing.put((Long) card[0], true);
                // update cid, nid, etc
                card[1] = mNotes.get(guid)[0];
                card[2] = _did((Long) card[2]);
                card[4] = Utils.intTime();
                card[5] = usn;
                // review cards have a due date relative to collection
                if ((Integer) card[7] == 2 || (Integer) card[7] == 3 || (Integer) card[6] == 2) {
                    card[8] = (Long) card[8] - aheadBy;
                }
                // odue needs updating too
                if (((Long) card[14]).longValue() != 0) {
                    card[14] = (Long) card[14] - aheadBy;
                }
                // if odid true, convert card from filtered to normal
                if ((Long) card[15] != 0) {
                    // odid
                    card[15] = 0;
                    // odue
                    card[8] = card[14];
                    card[14] = 0;
                    // queue
                    if ((Integer) card[6] == 1) { // type
                        card[7] = 0;
                    } else {
                        card[7] = card[6];
                    }
                    // type
                    if ((Integer) card[6] == 1) {
                        card[6] = 0;
                    }
                }
                cards.add(card);
                // we need to import revlog, rewriting card ids and bumping usn
                try (Cursor cur2 = mSrc.getDb().getDatabase().query("select * from revlog where cid = " + scid, null)) {
                    while (cur2.moveToNext()) {
                        Object[] rev = new Object[] { cur2.getLong(0), cur2.getLong(1), cur2.getInt(2), cur2.getInt(3),
                                cur2.getLong(4), cur2.getLong(5), cur2.getLong(6), cur2.getLong(7), cur2.getInt(8) };
                        rev[1] = card[0];
                        rev[2] = mDst.usn();
                        revlog.add(rev);
                    }
                }
                cnt += 1;
                i++;
                if (total != 0 && (!largeCollection || i % onePercent == 0)) {
                    publishProgress(100, i * 100 / total, 0);
                }
            }
            publishProgress(100, 100, 0);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        // apply
        mDst.getDb().executeMany("insert or ignore into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", cards);
        mDst.getDb().executeMany("insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", revlog);
        mLog.add(getRes().getString(R.string.import_complete_count, cnt));
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
                _writeDstMedia(fname, _srcMediaData(fname));
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
        for (Pattern p : Media.mRegexps) {
            Matcher m = p.matcher(fields);
            StringBuffer sb = new StringBuffer();
            int fnameIdx = Media.indexOfFname(p);
            while (m.find()) {
                String fname = m.group(fnameIdx);
                BufferedInputStream srcData = _srcMediaData(fname);
                BufferedInputStream dstData = _dstMediaData(fname);
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
        mDst.getConf().put("nextPos", mDst.getDb().queryLongScalar(
                "select max(due)+1 from cards where type = " + Consts.CARD_TYPE_NEW));
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
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
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
            mProgress.publishProgress(new CollectionTask.TaskData(getRes().getString(R.string.import_progress,
                    notesDone, cardsDone, postProcess)));
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
