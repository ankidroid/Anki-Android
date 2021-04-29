package com.ichi2.libanki.importer;

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Pair;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.template.ParsedNode;
import com.ichi2.libanki.utils.StringUtils;
import com.ichi2.utils.Assert;
import com.ichi2.utils.HtmlUtils;
import com.ichi2.utils.JSONObject;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import static com.ichi2.libanki.Consts.NEW_CARDS_RANDOM;
import static com.ichi2.libanki.Utils.fieldChecksum;
import static com.ichi2.libanki.Utils.guid64;
import static com.ichi2.libanki.Utils.joinFields;
import static com.ichi2.libanki.Utils.splitFields;
import static com.ichi2.libanki.importer.NoteImporter.ImportMode.ADD_MODE;
import static com.ichi2.libanki.importer.NoteImporter.ImportMode.IGNORE_MODE;
import static com.ichi2.libanki.importer.NoteImporter.ImportMode.UPDATE_MODE;

// Ported from https://github.com/ankitects/anki/blob/50fdf9b03dec33c99a501f332306f378db5eb4ea/pylib/anki/importing/noteimp.py
// Aside from 9f676dbe0b2ad9b87a3bf89d7735b4253abd440e, which allows empty notes.
public class NoteImporter extends Importer {

    private boolean mNeedMapper = true;
    private boolean mNeedDelimiter = false;
    private boolean mAllowHTML = false;
    private ImportMode mImportMode = UPDATE_MODE;
    /** Note: elements can be null */
    @Nullable
    private List<String> mMapping;
    @Nullable
    private final String mTagModified ;


    private final Model mModel;

    /** _tagsMapped in python */
    private boolean mTagsMapped;

    /** _fmap in Python */
    private Map<String, Pair<Integer, JSONObject>> mFMap;

    /** _nextID in python */
    private long mNextId;
    private ArrayList<Long> mIds;
    private boolean mEmptyNotes;
    private int mUpdateCount;
    private List<ParsedNode> mTemplateParsed;


    public NoteImporter(Collection col, String file) {
        super(col, file);
        this.mModel = col.getModels().current();
        this.mTemplateParsed = mModel.parsedNodes();
        this.mMapping = null;
        this.mTagModified = null;
        this.mTagsMapped = false;
    }


    @Override
    public void run() {
        Assert.that(mMapping != null);
        Assert.that(!mMapping.isEmpty());
        List<ForeignNote> c = foreignNotes();
        importNotes(c);
    }



    /** The number of fields.*/
    protected int fields() {
        return 0;
    }


    public void initMapping() {
        List<String> flds = mModel.getFieldsNames();
        // truncate to provided count
        flds = flds.subList(0, Math.min(flds.size(), fields()));
        // if there's room left, add tags
        if (fields() > flds.size()) {
            flds.add("_tags");
        }
        // and if there's still room left, pad
        int iterations = fields() - flds.size();
        for (int i = 0; i < iterations; i++) {
            flds.add(null);
        }
        mMapping = flds;
    }


    boolean mappingOk() {
        return mMapping.contains(mModel.getJSONArray("flds").getJSONObject(0).getString("name"));
    }


    @NonNull
    protected List<ForeignNote> foreignNotes() {
        return new ArrayList<>();
    }

    /** Open file and ensure it's in the right format. */
    protected void open() {
        // intentionally empty
    }


    /** Closes the open file. */
    protected void close() {
        // intentionally empty
    }


    /** Convert each card into a note, apply attributes and add to col. */
    public void importNotes(List<ForeignNote> notes) {
        Assert.that(mappingOk());
        // note whether tags are mapped
        mTagsMapped = false;
        for (String f : mMapping) {
            if ("_tags".equals(f)) {
                mTagsMapped = true;
                break;
            }
        }
        // gather checks for duplicate comparison
        HashMap<Long, List<Long>> csums = new HashMap<>();
        try (Cursor c = mCol.getDb().query("select csum, id from notes where mid = ?", mModel.getLong("id"))) {
            while (c.moveToNext()) {
                long csum = c.getLong(0);
                long id = c.getLong(1);
                if (csums.containsKey(csum)) {
                    csums.get(csum).add(id);
                } else {
                    csums.put(csum, new ArrayList<>(Collections.singletonList(id)));
                }
            }
        }

        HashSet<String> firsts = new HashSet<>(notes.size());
        int fld0index = mMapping.indexOf(mModel.getJSONArray("flds").getJSONObject(0).getString("name"));
        mFMap = Models.fieldMap(mModel);
        mNextId = mCol.getTime().timestampID(mCol.getDb(), "notes");
        // loop through the notes
        List<Object[]> updates = new ArrayList<>(notes.size());
        List<String> updateLog = new ArrayList<>(notes.size());
        // PORT: Translations moved closer to their sources
        List<Object[]> _new = new ArrayList<>();
        mIds = new ArrayList<>();
        mEmptyNotes = false;
        int dupeCount = 0;
        List<String> dupes = new ArrayList<>(notes.size());
        for (ForeignNote n : notes) {
            for (int c = 0; c < n.mFields.size(); c++) {
                if (!this.mAllowHTML) {
                    n.mFields.set(c, HtmlUtils.escape(n.mFields.get(c)));
                }
                n.mFields.set(c, n.mFields.get(c).trim());
                if (!this.mAllowHTML) {
                    n.mFields.set(c, n.mFields.get(c).replace("\n", "<br>"));
                }
            }
            String fld0 = n.mFields.get(fld0index);
            long csum = fieldChecksum(fld0);
            // first field must exist
            if (fld0 == null || fld0.length() == 0) {
                getLog().add(getString(R.string.note_importer_error_empty_first_field, TextUtils.join(" ", n.mFields)));
                continue;
            }
            // earlier in import?
            if (firsts.contains(fld0) && mImportMode != ADD_MODE) {
                // duplicates in source file; log and ignore
                getLog().add(getString(R.string.note_importer_error_appeared_twice, fld0));
                continue;
            }
            firsts.add(fld0);
            // already exists?
            boolean found = false;
            if (csums.containsKey(csum)) {
                // csum is not a guarantee; have to check
                for (Long id : csums.get(csum)) {
                    String flds = mCol.getDb().queryString("select flds from notes where id = ?", id);
                    String[] sflds = splitFields(flds);
                    if (fld0.equals(sflds[0])) {
                        // duplicate
                        found = true;
                        if (mImportMode == UPDATE_MODE) {
                            Object[] data = updateData(n, id, sflds);
                            if (data != null && data.length > 0) {
                                updates.add(data);
                                updateLog.add(getString(R.string.note_importer_error_first_field_matched, fld0));
                                dupeCount += 1;
                                found = true;
                            }
                        } else if (mImportMode == IGNORE_MODE) {
                            dupeCount += 1;
                        } else if (mImportMode == ADD_MODE) {
                            // allow duplicates in this case
                            if (!dupes.contains(fld0)) {
                                // only show message once, no matter how many
                                // duplicates are in the collection already
                                updateLog.add(getString(R.string.note_importer_error_added_duplicate_first_field, fld0));
                                dupes.add(fld0);
                            }
                            found = false;
                        }
                    }
                }
            }
            // newly add
            if (!found) {
                Object[] data = newData(n);
                if (data != null && data.length > 0) {
                    _new.add(data);
                    // note that we've seen this note once already
                    firsts.add(fld0);
                }
            }
        }
        addNew(_new);
        addUpdates(updates);
        // make sure to update sflds, etc
        mCol.updateFieldCache(mIds);
        // generate cards
        if (!mCol.genCards(mIds, mModel).isEmpty()) {
            this.getLog().add(0, getString(R.string.note_importer_empty_cards_found));
        }


        // we randomize or order here, to ensure that siblings
        // have the same due#
        long did = mCol.getDecks().selected();
        DeckConfig conf = mCol.getDecks().confForDid(did);
        // in order due?
        if (conf.getJSONObject("new").getInt("order") == NEW_CARDS_RANDOM) {
            mCol.getSched().randomizeCards(did);
        }
        String part1 = getQuantityString(R.plurals.note_importer_notes_added, _new.size());
        String part2 = getQuantityString(R.plurals.note_importer_notes_updated, mUpdateCount);
        int unchanged;
        if (mImportMode == UPDATE_MODE) {
            unchanged = dupeCount - mUpdateCount;
        } else if (mImportMode == IGNORE_MODE) {
            unchanged = dupeCount;
        } else {
            unchanged = 0;
        }
        String part3 = getQuantityString(R.plurals.note_importer_notes_unchanged, unchanged);
        mLog.add(String.format("%s, %s, %s.", part1, part2, part3));
        mLog.addAll(updateLog);
        if (mEmptyNotes) {
            mLog.add(getString(R.string.note_importer_error_empty_notes));
        }
        mTotal = mIds.size();
    }

    @Nullable
    private Object[] newData(ForeignNote n) {
        long id = mNextId;
        mNextId++;
        mIds.add(id);
        if (!processFields(n)) {
            return null;
        }
        return new Object[] {
                id,
                guid64(),
                mModel.getLong("id"),
                mCol.getTime().intTime(),
                mCol.usn(),
                mCol.getTags().join(n.mTags),
                n.fieldsStr,
                "",
                "",
                0,
                ""
        };
    }

    private void addNew(List<Object[]> rows) {
        mCol.getDb().executeMany("insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", rows);
    }


    private Object[] updateData(ForeignNote n, long id, String[] sflds) {
        mIds.add(id);
        if (!processFields(n, sflds)) {
            return null;
        }
        String tags;
        if (mTagsMapped) {
            tags = mCol.getTags().join(n.mTags);
            return new Object[] {mCol.getTime().intTime(), mCol.usn(), n.fieldsStr, tags, id, n.fieldsStr, tags };
        } else if (mTagModified != null) {
            tags = mCol.getDb().queryString("select tags from notes where id = ?", id);
            List<String> tagList = mCol.getTags().split(tags);
            tagList.addAll(StringUtils.splitOnWhitespace(mTagModified));
            tags = mCol.getTags().join(tagList);
            return new Object[] {mCol.getTime().intTime(), mCol.usn(), n.fieldsStr, tags, id, n.fieldsStr };
        } else {
            // This looks inconsistent but is fine, see: addUpdates
            return new Object[] {mCol.getTime().intTime(), mCol.usn(), n.fieldsStr, id, n.fieldsStr };
        }
    }


    private void addUpdates(List<Object[]> rows) {
        int changes = mCol.getDb().queryScalar("select total_changes()");
        if (mTagsMapped) {
            mCol.getDb().executeMany(
                    "update notes set mod = ?, usn = ?, flds = ?, tags = ? " +
                    "where id = ? and (flds != ? or tags != ?)",
                    rows
                    );
        } else if (mTagModified != null) {
            mCol.getDb().executeMany(
                    "update notes set mod = ?, usn = ?, flds = ?, tags = ? " +
                    "where id = ? and flds != ?",
                    rows
            );

        } else {
            mCol.getDb().executeMany(
                    "update notes set mod = ?, usn = ?, flds = ? " +
                    "where id = ? and flds != ?",
                    rows
            );
        }
        int changes2 = mCol.getDb().queryScalar("select total_changes()");
        mUpdateCount = changes2 - changes;
    }


    private boolean processFields(ForeignNote note) {
        return processFields(note, null);
    }

    private boolean processFields(ForeignNote note, @Nullable String[] fields) {
        if (fields == null) {
            int length = mModel.getJSONArray("flds").length();
            fields = new String[length];
            for (int i = 0; i < length; i++) {
                fields[i] = "";
            }
        }
        for (Map.Entry<Integer, String> entry : enumerate(mMapping)) {
            if (entry.getValue() == null) {
                continue;
            }
            int c = entry.getKey();
            if (entry.getValue().equals("_tags")) {
                note.mTags.addAll(mCol.getTags().split(note.mFields.get(c)));
            } else {
                Integer sidx = mFMap.get(entry.getValue()).first;
                fields[sidx] = note.mFields.get(c);
            }
        }
        note.fieldsStr = joinFields(fields);
        ArrayList<Integer> ords = Models.availOrds(mModel, fields, mTemplateParsed, Models.AllowEmpty.TRUE);
        if (ords.isEmpty()) {
            mEmptyNotes = true;
            return false;
        }
        return true;
    }



    /** Not in libAnki */

    private <T> List<Map.Entry<Integer, T>> enumerate(List<T> list) {
        List<Map.Entry<Integer, T>> ret = new ArrayList<>(list.size());
        int index = 0;
        for (T el : list) {
            ret.add(new AbstractMap.SimpleEntry<>(index, el));
            index++;
        }
        return ret;
    }


    public int getTotal() {
        return mTotal;
    }

    public void setImportMode(ImportMode mode) {
        this.mImportMode = mode;
    }


    private String getQuantityString(@PluralsRes int res, int quantity) {
        return AnkiDroidApp.getAppResources().getQuantityString(res, quantity, quantity);
    }


    @NonNull
    protected String getString(@StringRes int res) {
        return AnkiDroidApp.getAppResources().getString(res);
    }


    @NonNull
    protected String getString(int res, @NonNull Object... formatArgs) {
        return AnkiDroidApp.getAppResources().getString(res, formatArgs);
    }


    public void setAllowHtml(boolean allowHtml) {
        this.mAllowHTML = allowHtml;
    }


    public enum ImportMode {
        /** update if first field matches existing note */
        UPDATE_MODE, //0
        /** ignore if first field matches existing note */
        IGNORE_MODE, //1
        /** ADD_MODE: import even if first field matches existing note */
        ADD_MODE, //2
    }

    /** A temporary object storing fields and attributes. */
    public static class ForeignNote {
        public final List<String> mFields = new ArrayList<>();
        public final List<String> mTags = new ArrayList<>();
        public Object deck = new Object();
        public String fieldsStr = "";
    }

    public static class ForeignCard {
        public final long mDue = 0;
        public final int mIvl = 1;
        public final int mFactor = Consts.STARTING_FACTOR;
        public final int mReps = 0;
        public final int mLapses = 0;
    }

    private static class Triple {

        public final long mNid;
        public final Integer mOrd;
        public final ForeignCard mCard;


        public Triple(long nid, Integer ord, ForeignCard card) {

            this.mNid = nid;
            this.mOrd = ord;
            this.mCard = card;
        }
    }
}
