/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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
import android.database.Cursor;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.template.ParsedNode;
import com.ichi2.libanki.template.TemplateError;
import com.ichi2.utils.Assert;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

import static com.ichi2.libanki.Models.AllowEmpty.ONLY_CLOZE;
import static com.ichi2.libanki.Models.AllowEmpty.TRUE;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters",
        "PMD.NPathComplexity","PMD.MethodNamingConventions",
        "PMD.SwitchStmtsShouldHaveDefault","PMD.CollapsibleIfStatements","PMD.EmptyIfStmt"})
public class Models {
    public static final long NOT_FOUND_NOTE_TYPE = -1L;

    @VisibleForTesting
    public static final String REQ_NONE = "none";
    @VisibleForTesting
    public static final String REQ_ANY = "any";
    @VisibleForTesting
    public static final String REQ_ALL = "all";
    @SuppressWarnings("RegExpRedundantEscape") // In Android, } should be escaped
    private static final Pattern fClozePattern1 = Pattern.compile("\\{\\{[^}]*?cloze:(?:[^}]?:)*(.+?)\\}\\}");
    private static final Pattern fClozePattern2 = Pattern.compile("<%cloze:(.+?)%>");
    @SuppressWarnings("RegExpRedundantEscape")
    private static final Pattern fClozeOrdPattern = Pattern.compile("(?si)\\{\\{c(\\d+)::.*?\\}\\}");

    public static final String DEFAULT_MODEL =
              "{'sortf': 0, "
            + "'did': 1, "
            + "'latexPre': \""
            + "\\\\documentclass[12pt]{article}\\n"
            + "\\\\special{papersize=3in,5in}\\n"
            + "\\\\usepackage[utf8]{inputenc}\\n"
            + "\\\\usepackage{amssymb,amsmath}\\n"
            + "\\\\pagestyle{empty}\\n"
            + "\\\\setlength{\\\\parindent}{0in}\\n"
            + "\\\\begin{document}\\n"
            + "\", "
            + "'latexPost': \"\\\\end{document}\", "
            + "'mod': 0, "
            + "'usn': 0, "
            + "'vers': [], " // FIXME: remove when other clients have caught up
            + "'type': "
            + Consts.MODEL_STD
            + ", "
            + "'css': \".card {\\n"
            + " font-family: arial;\\n"
            + " font-size: 20px;\\n"
            + " text-align: center;\\n"
            + " color: black;\\n"
            + " background-color: white;\\n"
            + "}\""
            + "}";

    private static final String defaultField = "{'name': \"\", " + "'ord': null, " + "'sticky': False, " +
    // the following alter editing, and are used as defaults for the template wizard
            "'rtl': False, " + "'font': \"Arial\", " + "'size': 20, " +
            // reserved for future use
            "'media': [] }";

    private static final String defaultTemplate = "{'name': \"\", " + "'ord': null, " + "'qfmt': \"\", "
            + "'afmt': \"\", " + "'did': null, " + "'bqfmt': \"\"," + "'bafmt': \"\"," + "'bfont': \"Arial\"," +
            "'bsize': 12 }";

    // /** Regex pattern used in removing tags from text before diff */
    // private static final Pattern sFactPattern = Pattern.compile("%\\([tT]ags\\)s");
    // private static final Pattern sModelPattern = Pattern.compile("%\\(modelTags\\)s");
    // private static final Pattern sTemplPattern = Pattern.compile("%\\(cardModel\\)s");

    private final Collection mCol;
    private boolean mChanged;
    private HashMap<Long, Model> mModels;

    // BEGIN SQL table entries
    private int mId;
    //private long mCrt = mCol.getTime().intTime();
    //private long mMod = mCol.getTime().intTime();
    //private JSONObject mConf;
    //private String mCss = "";
    //private JSONArray mFields;
    //private JSONArray mTemplates;
    // BEGIN SQL table entries

    // private Decks mDeck;
    // private DB mDb;
    //
    //** Map for compiled Mustache Templates */
    //private Map<String, Template> mCmpldTemplateMap = new HashMap<>();


    //
    // /** Map for convenience and speed which contains FieldNames from current model */
    // private TreeMap<String, Integer> mFieldMap = new TreeMap<String, Integer>();
    //
    // /** Map for convenience and speed which contains Templates from current model */
    // private TreeMap<Integer, JSONObject> mTemplateMap = new TreeMap<Integer, JSONObject>();
    //
    // /** Map for convenience and speed which contains the CSS code related to a Template */
    // private HashMap<Integer, String> mCssTemplateMap = new HashMap<Integer, String>();
    //
    // /**
    // * The percentage chosen in preferences for font sizing at the time when the css for the CardModels related to
    // this
    // * Model was calculated in prepareCSSForCardModels.
    // */
    // private transient int mDisplayPercentage = 0;
    // private boolean mNightMode = false;

    /**
     * Saving/loading registry
     * ***********************************************************************************************
     */

    public Models(Collection col) {
        mCol = col;
    }


    /**
     * Load registry from JSON.
     */
    public void load(String json) {
        mChanged = false;
        mModels = new HashMap<>();
        JSONObject modelarray = new JSONObject(json);
        JSONArray ids = modelarray.names();
        if (ids != null) {
            for (String id: ids.stringIterable()) {
                Model o = new Model(modelarray.getJSONObject(id));
                mModels.put(o.getLong("id"), o);
            }
        }
    }


    /**
     * Mark M modified if provided, and schedule registry flush.
     */
    public void save() {
        save(null, false);
    }


    public void save(Model m) {
        save(m, false);
    }

    /**
     * Save a model
     * @param m model to save
     * @param templates flag which (when true) re-generates the cards for each note which uses the model
     */
    public void save(Model m, boolean templates) {
        if (m != null && m.has("id")) {
            m.put("mod", mCol.getTime().intTime());
            m.put("usn", mCol.usn());
            // TODO: fix empty id problem on _updaterequired (needed for model adding)
            if (templates) {
                _syncTemplates(m);
            }
        }
        mChanged = true;
        // The following hook rebuilds the tree in the Anki Desktop browser -- we don't need it
        // runHook("newModel")
    }


    /**
     * Flush the registry if any models were changed.
     */
    public void flush() {
        if (mChanged) {
            ensureNotEmpty();
            JSONObject array = new JSONObject();
            for (Map.Entry<Long, Model> o : mModels.entrySet()) {
                array.put(Long.toString(o.getKey()), o.getValue());
            }
            ContentValues val = new ContentValues();
            val.put("models", Utils.jsonToString(array));
            mCol.getDb().update("col", val);
            mChanged = false;
        }
    }

    public boolean ensureNotEmpty() {
        if (mModels.isEmpty()) {
            // TODO: Maybe we want to restore all models if we don't have any
            StdModels.BASIC_MODEL.add(mCol);
            return true;
        } else {
            return false;
        }
    }

    /*
      Retrieving and creating models
      ***********************************************************************************************
     */

    /**
     * Get current model.
     * @return The model, or null if not found in the deck and in the configuration.
     */
    public Model current() {
        return current(true);
    }

    /**
     * Get current model.
     * @param forDeck If true, it tries to get the deck specified in deck by mid, otherwise or if the former is not
     *                found, it uses the configuration`s field curModel.
     * @return The model, or null if not found in the deck and in the configuration.
     */
    public Model current(boolean forDeck) {
        Model m = null;
        if (forDeck) {
            m = get(mCol.getDecks().current().optLong("mid", -1));
        }
        if (m == null) {
            m = get(mCol.getConf().optLong("curModel", -1));
        }
        if (m == null) {
            if (!mModels.isEmpty()) {
                m = mModels.values().iterator().next();
            }
        }
        return m;
    }


    public void setCurrent(Model m) {
        mCol.getConf().put("curModel", m.getLong("id"));
        mCol.setMod();
    }


    /** get model with ID, or null. */
    public @Nullable Model get(@NonNull Long id) {
        if (mModels.containsKey(id)) {
            return mModels.get(id);
        } else {
            return null;
        }
    }


    /** get all models */
    public ArrayList<Model> all() {
        return new ArrayList<>(mModels.values());
    }


    /** get model with NAME. */
    public Model byName(String name) {
        for (Model m : mModels.values()) {
            if (m.getString("name").equals(name)) {
                return m;
            }
        }
        return null;
    }


    /** Create a new model, save it in the registry, and return it. */
	// Called `new` in Anki's code. New is a reserved word in java,
	// not in python. Thus the method has to be renamed.
    public Model newModel(String name) {
        // caller should call save() after modifying
        Model m = new Model(DEFAULT_MODEL);
        m.put("name", name);
        m.put("mod", mCol.getTime().intTime());
        m.put("flds", new JSONArray());
        m.put("tmpls", new JSONArray());
        m.put("tags", new JSONArray());
        m.put("id", 0);
        return m;
    }

    // not in anki
    public static boolean isModelNew(Model m) {
        return m.getLong("id") == 0;
    }

    /** Delete model, and all its cards/notes. 
     * @throws ConfirmModSchemaException */
    public void rem(Model m) throws ConfirmModSchemaException {
        mCol.modSchema();
        long id = m.getLong("id");
        boolean current = current().getLong("id") == id;
        // delete notes/cards
        mCol.remCards(mCol.getDb().queryLongList("SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = ?)", id));
        // then the model
        mModels.remove(id);
        save();
        // GUI should ensure last model is not deleted
        if (current) {
            setCurrent(mModels.values().iterator().next());
        }
    }


    public void add(Model m) {
        _setID(m);
        update(m);
        setCurrent(m);
        save(m);
    }


    /** Add or update an existing model. Used for syncing and merging. */
    public void update(Model m) {
        mModels.put(m.getLong("id"), m);
        // mark registry changed, but don't bump mod time
        save();
    }


    private void _setID(Model m) {
        long id = mCol.getTime().intTimeMS();
        while (mModels.containsKey(id)) {
            id = mCol.getTime().intTimeMS();
        }
        m.put("id", id);
    }


    public boolean have(@NonNull Long id) {
        return mModels.containsKey(id);
    }


    public Set<Long> ids() {
        return mModels.keySet();
    }


    /*
      Tools ***********************************************************************************************
     */

    /** Note ids for M */
    public ArrayList<Long> nids(Model m) {
        return mCol.getDb().queryLongList("SELECT id FROM notes WHERE mid = ?", m.getLong("id"));
    }

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @return The number of notes with that model.
     */
    public int useCount(Model m) {
        return mCol.getDb().queryScalar("select count() from notes where mid = ?", m.getLong("id"));
    }

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @param ord The index of the card template
     * @return The number of notes with that model.
     */
    public int tmplUseCount(Model m, int ord) {
        return mCol.getDb().queryScalar("select count() from cards, notes where cards.nid = notes.id and notes.mid = ? and cards.ord = ?", m.getLong("id"), ord);
    }

    /*
      Copying ***********************************************************************************************
     */

    /** Copy, save and return. */
    public Model copy(Model m) {
        Model m2 = m.deepClone();        
        m2.put("name", m2.getString("name") + " copy");
        add(m2);
        return m2;
    }


    /**
     * Fields ***********************************************************************************************
     */

    public JSONObject newField(String name) {
        JSONObject f = new JSONObject(defaultField);
        f.put("name", name);
        return f;
    }


    /** "Mapping of field name -> (ord, field). */
    @NonNull
    public static Map<String, Pair<Integer, JSONObject>> fieldMap(@NonNull Model m) {
        JSONArray flds = m.getJSONArray("flds");
        // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
        Map<String, Pair<Integer, JSONObject>> result = new HashMap<>(flds.length());
        for (JSONObject f: flds.jsonObjectIterable()) {
            result.put(f.getString("name"), new Pair<>(f.getInt("ord"), f));
        }
        return result;
    }


    public int sortIdx(Model m) {
        return m.getInt("sortf");
    }


    public void setSortIdx(Model m, int idx) throws ConfirmModSchemaException{
        mCol.modSchema();
        m.put("sortf", idx);
        mCol.updateFieldCache(nids(m));
        save(m);
    }


    private void _addField(Model m, JSONObject field) {
        // do the actual work of addField. Do not check whether model
        // is not new.
		JSONArray flds = m.getJSONArray("flds");
		flds.put(field);
		m.put("flds", flds);
		_updateFieldOrds(m);
		save(m);
		_transformFields(m, new TransformFieldAdd());
    }

    public void addField(Model m, JSONObject field) throws ConfirmModSchemaException {
        // only mod schema if model isn't new
        // this is Anki's addField.
        if (!isModelNew(m)) {
            mCol.modSchema();
        }
        _addField(m, field);
    }

    public void addFieldInNewModel(Model m, JSONObject field) {
        // similar to Anki's addField; but thanks to assumption that
        // model is new, it never has to throw
        // ConfirmModSchemaException.
        Assert.that(isModelNew(m), "Model was assumed to be new, but is not");
        _addField(m, field);
    }

    public void addFieldModChanged(Model m, JSONObject field) {
        // similar to Anki's addField; but thanks to assumption that
        // mod is already changed, it never has to throw
        // ConfirmModSchemaException.
        Assert.that(mCol.schemaChanged(), "Mod was assumed to be already changed, but is not");
        _addField(m, field);
    }

    static class TransformFieldAdd implements TransformFieldVisitor {
        @Override
        public String[] transform(String[] fields) {
            String[] f = new String[fields.length + 1];
            System.arraycopy(fields, 0, f, 0, fields.length);
            f[fields.length] = "";
            return f;
        }
    }


    public void remField(Model m, JSONObject field) throws ConfirmModSchemaException {
        mCol.modSchema();
        JSONArray flds = m.getJSONArray("flds");
        JSONArray flds2 = new JSONArray();
        int idx = -1;
        for (int i = 0; i < flds.length(); ++i) {
            if (field.equals(flds.getJSONObject(i))) {
                idx = i;
                continue;
            }
            flds2.put(flds.getJSONObject(i));
        }
        m.put("flds", flds2);
        int sortf = m.getInt("sortf");
        if (sortf >= m.getJSONArray("flds").length()) {
            m.put("sortf", sortf - 1);
        }
        _updateFieldOrds(m);
        _transformFields(m, new TransformFieldDelete(idx));
        if (idx == sortIdx(m)) {
            // need to rebuild
            mCol.updateFieldCache(nids(m));
        }
        renameField(m, field, null);

    }

    static class TransformFieldDelete implements TransformFieldVisitor {
        private final int mIdx;


        public TransformFieldDelete(int _idx) {
            mIdx = _idx;
        }


        @Override
        public String[] transform(String[] fields) {
            ArrayList<String> fl = new ArrayList<>(Arrays.asList(fields));
            fl.remove(mIdx);
            return fl.toArray(new String[fl.size()]);
        }
    }


    public void moveField(Model m, JSONObject field, int idx) throws ConfirmModSchemaException {
        mCol.modSchema();
        JSONArray flds = m.getJSONArray("flds");
        ArrayList<JSONObject> l = new ArrayList<>(flds.length());
        int oldidx = -1;
        for (int i = 0; i < flds.length(); ++i) {
            l.add(flds.getJSONObject(i));
            if (field.equals(flds.getJSONObject(i))) {
                oldidx = i;
                if (idx == oldidx) {
                    return;
                }
            }
        }
        // remember old sort field
        String sortf = Utils.jsonToString(m.getJSONArray("flds").getJSONObject(m.getInt("sortf")));
        // move
        l.remove(oldidx);
        l.add(idx, field);
        m.put("flds", new JSONArray(l));
        // restore sort field
        flds = m.getJSONArray("flds");
        for (int i = 0; i < flds.length(); ++i) {
            if (Utils.jsonToString(flds.getJSONObject(i)).equals(sortf)) {
                m.put("sortf", i);
                break;
            }
        }
        _updateFieldOrds(m);
        save(m);
        _transformFields(m, new TransformFieldMove(idx, oldidx));

    }

    static class TransformFieldMove implements TransformFieldVisitor {
        private final int mIdx;
        private final int mOldidx;


        public TransformFieldMove(int _idx, int _oldidx) {
            mIdx = _idx;
            mOldidx = _oldidx;
        }


        @Override
        public String[] transform(String[] fields) {
            String val = fields[mOldidx];
            ArrayList<String> fl = new ArrayList<>(Arrays.asList(fields));
            fl.remove(mOldidx);
            fl.add(mIdx, val);
            return fl.toArray(new String[fl.size()]);
        }
    }


    public void renameField(Model m, JSONObject field, String newName) throws ConfirmModSchemaException {
        mCol.modSchema();
        String pat = String.format("\\{\\{([^{}]*)([:#^/]|[^:#/^}][^:}]*?:|)%s\\}\\}",
                                   Pattern.quote(field.getString("name")));
        if (newName == null) {
            newName = "";
        }
        String repl = "{{$1$2" + newName + "}}";

        JSONArray tmpls = m.getJSONArray("tmpls");
        for (JSONObject t: tmpls.jsonObjectIterable()) {
            for (String fmt : new String[] { "qfmt", "afmt" }) {
                if (!"".equals(newName)) {
                    t.put(fmt, t.getString(fmt).replaceAll(pat, repl));
                } else {
                    t.put(fmt, t.getString(fmt).replaceAll(pat, ""));
                }
            }
        }
        field.put("name", newName);
        save(m);
    }


    public void _updateFieldOrds(JSONObject m) {
        JSONArray flds = m.getJSONArray("flds");
        for (int i = 0; i < flds.length(); i++) {
            JSONObject f = flds.getJSONObject(i);
            f.put("ord", i);
        }
    }

    interface TransformFieldVisitor {
        String[] transform(String[] fields);
    }


    public void _transformFields(Model m, TransformFieldVisitor fn) {
        // model hasn't been added yet?
        if (isModelNew(m)) {
            return;
        }
        ArrayList<Object[]> r = new ArrayList<>();

        try (Cursor cur = mCol.getDb()
                .query("select id, flds from notes where mid = ?", m.getLong("id"))) {
            while (cur.moveToNext()) {
                r.add(new Object[] {
                        Utils.joinFields(fn.transform(Utils.splitFields(cur.getString(1)))),
                        mCol.getTime().intTime(), mCol.usn(), cur.getLong(0)});
            }
        }
        mCol.getDb().executeMany("update notes set flds=?,mod=?,usn=? where id = ?", r);
    }


    /**
     * Templates ***********************************************************************************************
     */

    public static JSONObject newTemplate(String name) {
        JSONObject t = new JSONObject(defaultTemplate);
        t.put("name", name);
        return t;
    }


    /** Note: should col.genCards() afterwards. */
    private void _addTemplate(Model m, JSONObject template) {
        // do the actual work of addTemplate. Do not consider whether
        // model is new or not.
        JSONArray tmpls = m.getJSONArray("tmpls");
        tmpls.put(template);
        m.put("tmpls", tmpls);
        _updateTemplOrds(m);
        save(m);
    }

    /** @throws ConfirmModSchemaException */
    public void addTemplate(Model m, JSONObject template) throws ConfirmModSchemaException {
        //That is Anki's addTemplate method
        if (!isModelNew(m)) {
            mCol.modSchema();
        }
        _addTemplate(m, template);
    }

    public void addTemplateInNewModel(Model m, JSONObject template)  {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(isModelNew(m), "Model was assumed to be new, but is not");
        _addTemplate(m, template);
    }

    public void addTemplateModChanged(Model m, JSONObject template)  {
        // similar to addTemplate, but doesn't throw exception;
        // asserting the model is new.
        Assert.that(mCol.schemaChanged(), "Mod was assumed to be already changed, but is not");
        _addTemplate(m, template);
    }

    /**
     * Removing a template
     *
     * @return False if removing template would leave orphan notes.
     * @throws ConfirmModSchemaException 
     */
    public boolean remTemplate(Model m, JSONObject template) throws ConfirmModSchemaException {
        if (m.getJSONArray("tmpls").length() <= 1) {
            return false;
        }
        // find cards using this template
        JSONArray tmpls = m.getJSONArray("tmpls");
        int ord = -1;
        for (int i = 0; i < tmpls.length(); ++i) {
            if (tmpls.getJSONObject(i).equals(template)) {
                ord = i;
                break;
            }
        }

        if (ord == -1) {
            throw new IllegalArgumentException("Invalid template proposed for delete");
        }
        // the code in "isRemTemplateSafe" was in place here in libanki. It is extracted to a method for reuse
        List<Long> cids = getCardIdsForModel(m.getLong("id"), new int[]{ord});
        if (cids == null) {
            Timber.d("remTemplate getCardIdsForModel determined it was unsafe to delete the template");
            return false;
        }

        // ok to proceed; remove cards
        Timber.d("remTemplate proceeding to delete the template and %d cards", cids.size());
        mCol.modSchema();
        mCol.remCards(cids);
        // shift ordinals
        mCol.getDb()
            .execute(
                     "update cards set ord = ord - 1, usn = ?, mod = ? where nid in (select id from notes where mid = ?) and ord > ?",
                     mCol.usn(), mCol.getTime().intTime(), m.getLong("id"), ord);
        tmpls = m.getJSONArray("tmpls");
        JSONArray tmpls2 = new JSONArray();
        for (int i = 0; i < tmpls.length(); ++i) {
            if (template.equals(tmpls.getJSONObject(i))) {
                continue;
            }
            tmpls2.put(tmpls.getJSONObject(i));
        }
        m.put("tmpls", tmpls2);
        _updateTemplOrds(m);
        save(m);
        Timber.d("remTemplate done working");
        return true;
    }


    /**
     * Extracted from remTemplate so we can test if removing templates is safe without actually removing them
     * This method will either give you all the card ids for the ordinals sent in related to the model sent in *or*
     * it will return null if the result of deleting the ordinals is unsafe because it would leave notes with no cards
     *
     * @param modelId long id of the JSON model
     * @param ords array of ints, each one is the ordinal a the card template in the given model
     * @return null if deleting ords would orphan notes, long[] of related card ids to delete if it is safe
     */
    public @Nullable List<Long> getCardIdsForModel(long modelId, int[] ords) {
        String cardIdsToDeleteSql = "select c2.id from cards c2, notes n2 where c2.nid=n2.id and n2.mid = ? and c2.ord  in " + Utils.ids2str(ords);
        List<Long> cids = mCol.getDb().queryLongList(cardIdsToDeleteSql, modelId);
        //Timber.d("cardIdsToDeleteSql was ' %s' and got %s", cardIdsToDeleteSql, Utils.ids2str(cids));
        Timber.d("getCardIdsForModel found %s cards to delete for model %s and ords %s", cids.size(), modelId, Utils.ids2str(ords));

        // all notes with this template must have at least two cards, or we could end up creating orphaned notes
        String noteCountPreDeleteSql = "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?)";
        int preDeleteNoteCount = mCol.getDb().queryScalar(noteCountPreDeleteSql, modelId);
        Timber.d("noteCountPreDeleteSql was '%s'", noteCountPreDeleteSql);
        Timber.d("preDeleteNoteCount is %s", preDeleteNoteCount);
        String noteCountPostDeleteSql = "select count(distinct(nid)) from cards where nid in (select id from notes where mid = ?) and ord not in " + Utils.ids2str(ords);
        Timber.d("noteCountPostDeleteSql was '%s'", noteCountPostDeleteSql);
        int postDeleteNoteCount = mCol.getDb().queryScalar(noteCountPostDeleteSql, modelId);
        Timber.d("postDeleteNoteCount would be %s", postDeleteNoteCount);

        if (preDeleteNoteCount != postDeleteNoteCount) {
            Timber.d("There will be orphan notes if these cards are deleted.");
            return null;
        }
        Timber.d("Deleting these cards will not orphan notes.");
        return cids;
    }


    public static void _updateTemplOrds(Model m) {
        JSONArray tmpls = m.getJSONArray("tmpls");
        for (int i = 0; i < tmpls.length(); i++) {
            JSONObject f = tmpls.getJSONObject(i);
            f.put("ord", i);
        }
    }


    public void moveTemplate(Model m, JSONObject template, int idx) {
        JSONArray tmpls = m.getJSONArray("tmpls");
        int oldidx = -1;
        ArrayList<JSONObject> l = new ArrayList<>();
        HashMap<Integer, Integer> oldidxs = new HashMap<>();
        for (int i = 0; i < tmpls.length(); ++i) {
            if (tmpls.getJSONObject(i).equals(template)) {
                oldidx = i;
                if (idx == oldidx) {
                    return;
                }
            }
            JSONObject t = tmpls.getJSONObject(i);
            oldidxs.put(t.hashCode(), t.getInt("ord"));
            l.add(t);
        }
        l.remove(oldidx);
        l.add(idx, template);
        m.put("tmpls", new JSONArray(l));
        _updateTemplOrds(m);
        // generate change map - We use StringBuilder
        StringBuilder sb = new StringBuilder();
        tmpls = m.getJSONArray("tmpls");
        for (int i = 0; i < tmpls.length(); ++i) {
            JSONObject t = tmpls.getJSONObject(i);
            sb.append("when ord = ").append(oldidxs.get(t.hashCode())).append(" then ").append(t.getInt("ord"));
            if (i != tmpls.length() - 1) {
                sb.append(" ");
            }
        }
        // apply
        save(m);
        mCol.getDb().execute("update cards set ord = (case " + sb +
                             " end),usn=?,mod=? where nid in (select id from notes where mid = ?)",
                             mCol.usn(), mCol.getTime().intTime(), m.getLong("id"));
    }

    @SuppressWarnings("PMD.UnusedLocalVariable") // unused upstream as well
    private void _syncTemplates(Model m) {
        ArrayList<Long> rem = mCol.genCards(nids(m), m);
    }


    /*
      Model changing ***********************************************************************************************
     */

    /**
     * Change a model
     * @param m The model to change.
     * @param nid The notes that the change applies to.
     * @param newModel For replacing the old model with another one. Should be self if the model is not changing
     * @param fmap Map for switching fields. This is ord->ord and there should not be duplicate targets
     * @param cmap Map for switching cards. This is ord->ord and there should not be duplicate targets
     * @throws ConfirmModSchemaException 
     */
    public void change(Model m, long nid, Model newModel, Map<Integer, Integer> fmap, Map<Integer, Integer> cmap) throws ConfirmModSchemaException {
        mCol.modSchema();
        assert (newModel.getLong("id") == m.getLong("id")) || (fmap != null && cmap != null);
        if (fmap != null) {
            _changeNote(nid, newModel, fmap);
        }
        if (cmap != null) {
            _changeCards(nid, m, newModel, cmap);
        }
        mCol.genCards(nid, newModel);
    }

    private void _changeNote(long nid, Model newModel, Map<Integer, Integer> map) {
        int nfields = newModel.getJSONArray("flds").length();
        long mid = newModel.getLong("id");
        String sflds = mCol.getDb().queryString("select flds from notes where id = ?", nid);
        String[] flds = Utils.splitFields(sflds);
        Map<Integer, String> newflds = new HashMap<>(map.size());

        for (Entry<Integer, Integer> entry : map.entrySet()) {
            newflds.put(entry.getValue(), flds[entry.getKey()]);
        }
        List<String> flds2 = new ArrayList<>(nfields);
        for (int c = 0; c < nfields; ++c) {
            if (newflds.containsKey(c)) {
                flds2.add(newflds.get(c));
            } else {
                flds2.add("");
            }
        }
        String joinedFlds = Utils.joinFields(flds2.toArray(new String[flds2.size()]));
        mCol.getDb().execute("update notes set flds=?,mid=?,mod=?,usn=? where id = ?", joinedFlds, mid, mCol.getTime().intTime(), mCol.usn(), nid);
        mCol.updateFieldCache(new long[] {nid});
    }

    private void _changeCards(long nid, Model oldModel, Model newModel, Map<Integer, Integer> map) {
        List<Object[]> d = new ArrayList<>();
        List<Long> deleted = new ArrayList<>();
        int omType = oldModel.getInt("type");
        int nmType = newModel.getInt("type");
        int nflds = newModel.getJSONArray("tmpls").length();
        try (Cursor cur = mCol.getDb().query(
                    "select id, ord from cards where nid = ?", nid)) {
            while (cur.moveToNext()) {
                // if the src model is a cloze, we ignore the map, as the gui doesn't currently
                // support mapping them
                Integer newOrd;
                long cid = cur.getLong(0);
                int ord = cur.getInt(1);
                if (omType == Consts.MODEL_CLOZE) {
                    newOrd = cur.getInt(1);
                    if (nmType != Consts.MODEL_CLOZE) {
                        // if we're mapping to a regular note, we need to check if
                        // the destination ord is valid
                        if (nflds <= ord) {
                            newOrd = null;
                        }
                    }
                } else {
                    // mapping from a regular note, so the map should be valid
                    newOrd = map.get(ord);
                }
                if (newOrd != null) {
                    d.add(new Object[] { newOrd, mCol.usn(), mCol.getTime().intTime(), cid });
                } else {
                    deleted.add(cid);
                }
            }
        }
        mCol.getDb().executeMany("update cards set ord=?,usn=?,mod=? where id=?", d);
        mCol.remCards(deleted);
    }

    /*
      Schema hash ***********************************************************************************************
     */

    /** Return a hash of the schema, to see if models are compatible. */
    public String scmhash(Model m) {
        StringBuilder s = new StringBuilder();
        JSONArray flds = m.getJSONArray("flds");
        for (JSONObject fld: flds.jsonObjectIterable()) {
            s.append(fld.getString("name"));
        }
        JSONArray tmpls = m.getJSONArray("tmpls");
        for (JSONObject t: tmpls.jsonObjectIterable()) {
            s.append(t.getString("name"));
        }
        return Utils.checksum(s.toString());
    }


    /**
     * Required field/text cache
     * ***********************************************************************************************
     */

    @SuppressWarnings("PMD.UnusedLocalVariable") // 'String f' is unused upstream as well
    private Object[] _reqForTemplate(Model m, List<String> flds, JSONObject t) {
        int nbFields = flds.size();
        String[] a = new String[nbFields];
        String[] b = new String[nbFields];
        Arrays.fill(a, "ankiflag");
        Arrays.fill(b, "");
        int ord = t.getInt("ord");
        String full = mCol._renderQA(1L, m, 1L, ord, "", a, 0).get("q");
        String empty = mCol._renderQA(1L, m, 1L, ord, "", b, 0).get("q");
        // if full and empty are the same, the template is invalid and there is no way to satisfy it
        if (full.equals(empty)) {
            return new Object[] { REQ_NONE, new JSONArray(), new JSONArray() };
        }
        String type = REQ_ALL;
        JSONArray req = new JSONArray();
        for (int i = 0; i < flds.size(); i++) {
            a[i] = "";
            // if no field content appeared, field is required
            if (!mCol._renderQA(1L, m, 1L, ord, "", a, 0).get("q").contains("ankiflag")) {
                req.put(i);
            }
            a[i] = "ankiflag";
        }
        if (req.length() > 0) {
            return new Object[] { type, req };
        }
        // if there are no required fields, switch to any mode
        type = REQ_ANY;
        req = new JSONArray();
        for (int i = 0; i < flds.size(); i++) {
            b[i] = "1";
            // if not the same as empty, this field can make the card non-blank
            if (!mCol._renderQA(1L, m, 1L, ord, "", b, 0).get("q").equals(empty)) {
                req.put(i);
            }
            b[i] = "";
        }
        return new Object[] { type, req };
    }


    /**
     * @param m A note type
     * @param ord a card type number of this model
     * @param sfld Fields of a note of this model. (Not trimmed)
     * @return Whether this card is empty
     */
    public static boolean emptyCard(Model m, int ord, String[] sfld) throws TemplateError {
        if (m.isCloze()) {
            // For cloze, getting the list of cloze numbes is linear in the size of the template
            // So computing the full list is almost as efficient as checking for a particular number
            return !_availClozeOrds(m, sfld, false).contains(ord);
        }
        return emptyStandardCard(m.getJSONArray("tmpls").getJSONObject(ord), m.nonEmptyFields(sfld));
    }

    /**
     * @return Whether the standard card is empty
     */
    public static boolean emptyStandardCard(JSONObject tmpl, Set<String> nonEmptyFields) throws TemplateError {
        return ParsedNode.parse_inner(tmpl.getString("qfmt")).template_is_empty(nonEmptyFields);
    }


    /**
     * Whether to allow empty note to generate a card. When importing a deck, this is useful to be able to correct it. When doing "check card" it avoids to delete empty note.
     * By default, it is allowed for cloze type but not for standard type.
     */
    public enum AllowEmpty {
        TRUE,
        FALSE,
        ONLY_CLOZE;

        /**
         * @param allowEmpty a Boolean representing whether empty note should be allowed. Null is understood as default
         * @return AllowEmpty similar to the boolean
         */
        public static @NonNull AllowEmpty fromBoolean(@Nullable Boolean allowEmpty) {
            if (allowEmpty == null) {
                return Models.AllowEmpty.ONLY_CLOZE;
            } else if (allowEmpty == true) {
                return Models.AllowEmpty.TRUE;
            } else {
                return Models.AllowEmpty.FALSE;
            }
        }
    }

    /**
     * @param m A model
     * @param sfld Fields of a note
     * @param nodes Nodes used for parsing the variaous templates. Null for cloze
     * @param allowEmpty whether to always return an ord, even if all cards are actually empty
     * @return The index of the cards that are generated. For cloze cards, if no card is generated, then {0} */
    public static ArrayList<Integer> availOrds(Model m, String[] sfld, List<ParsedNode> nodes, @NonNull AllowEmpty allowEmpty) {
        if (m.getInt("type") == Consts.MODEL_CLOZE) {
            return _availClozeOrds(m, sfld, allowEmpty == TRUE || allowEmpty == ONLY_CLOZE);
        }
        return _availStandardOrds(m, sfld, nodes, allowEmpty == TRUE);
    }

    public static ArrayList<Integer> availOrds(Model m, String[] sfld) {
        return availOrds(m, sfld, ONLY_CLOZE);
    }

    public static ArrayList<Integer> availOrds(Model m, String[] sfld, @NonNull AllowEmpty allowEmpty) {
        if (m.isCloze()) {
            return _availClozeOrds(m, sfld, allowEmpty == TRUE || allowEmpty == ONLY_CLOZE);
        }
        return _availStandardOrds(m, sfld, allowEmpty == TRUE);
    }

    public static ArrayList<Integer> _availStandardOrds(Model m, String[] sfld) {
        return _availStandardOrds(m, sfld, false);
    }

    public static ArrayList<Integer> _availStandardOrds(Model m, String[] sfld, boolean allowEmpty) {
        return _availStandardOrds(m, sfld, m.parsedNodes(), allowEmpty);
    }

    /** Given a joined field string and a standard note type, return available template ordinals */
    public static ArrayList<Integer> _availStandardOrds(Model m, String[] sfld, List<ParsedNode> nodes, boolean allowEmpty) {
        Set<String> nonEmptyFields = m.nonEmptyFields(sfld);
        ArrayList<Integer> avail = new ArrayList<>(nodes.size());
        for (int i = 0 ; i < nodes.size(); i++) {
            ParsedNode node = nodes.get(i);
            if (node != null && !node.template_is_empty(nonEmptyFields)) {
                avail.add(i);
            }
        }
        if (allowEmpty && avail.isEmpty()) {
            /* According to anki documentation:
            When adding/importing, if a normal note doesn’t generate any cards, Anki will now add a blank card 1 instead of refusing to add the note. */
            avail.add(0);
        }
        return avail;
    }


    /**
     * @param m A note type with cloze
     * @param sflds The fields of a note of type m. (Assume the size of the array is the number of fields)
     * @return The indexes (in increasing order) of cards that should be generated according to req rules.
     */
    public static ArrayList<Integer> _availClozeOrds(Model m, String[] sflds) {
        return _availClozeOrds(m, sflds, true);
    }

    /**
     * Cache of getNamesOfFieldsContainingCloze
     * Computing hash of string is costly. However, hash is cashed in the string object, so this virtually ensure that
     * given a card type, we don't need to recompute the hash.
     */
    private static final WeakHashMap<String, List<String>> namesOfFieldsContainingClozeCache = new WeakHashMap<>();

    /** The name of all fields that are used as cloze in the question.
     * It is not guaranteed that the field found are actually the name of any field of the note type.*/
    @VisibleForTesting
    protected static List<String> getNamesOfFieldsContainingCloze(String question) {
        if (! namesOfFieldsContainingClozeCache.containsKey(question)) {
            List<String> matches = new ArrayList<>();
            for (Pattern pattern : new Pattern[] {fClozePattern1, fClozePattern2}) {
                Matcher mm = pattern.matcher(question);
                while (mm.find()) {
                    matches.add(mm.group(1));
                }
            }
            namesOfFieldsContainingClozeCache.put(question, matches);
        }
        return namesOfFieldsContainingClozeCache.get(question);
    }

    /**
     * @param m A note type with cloze
     * @param sflds The fields of a note of type m. (Assume the size of the array is the number of fields)
     * @param allowEmpty Whether we allow to generate at least one card even if they are all empty
     * @return The indexes (in increasing order) of cards that should be generated according to req rules.
     * If empty is not allowed, it will contains ord 1.*/
    public static ArrayList<Integer> _availClozeOrds(Model m, String[] sflds, boolean allowEmpty) {
        Map<String, Pair<Integer, JSONObject>> map = fieldMap(m);
        String question = m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt");
        Set<Integer> ords = new HashSet<>();
        List<String> matches = getNamesOfFieldsContainingCloze(question);
        for (String fname : matches) {
            if (!map.containsKey(fname)) {
                continue;
            }
            int ord = map.get(fname).first;
            Matcher mm = fClozeOrdPattern.matcher(sflds[ord]);
            while (mm.find()) {
                ords.add(Integer.parseInt(mm.group(1)) - 1);
            }
        }
        ords.remove(-1);
        if (ords.isEmpty() && allowEmpty) {
            // empty clozes use first ord
            return new ArrayList<>(Collections.singletonList(0));
        }
        return new ArrayList<>(ords);
    }


    /**
     * Sync handling ***********************************************************************************************
     */

    public void beforeUpload() {
        boolean changed = Utils.markAsUploaded(all());
        if (changed) {
            save();
        }
    }


    /**
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    public void setChanged() {
        mChanged = true;
    }


    public HashMap<Long, HashMap<Integer, String>> getTemplateNames() {
        HashMap<Long, HashMap<Integer, String>> result = new HashMap<>(mModels.size());
        for (Model m : mModels.values()) {
            JSONArray templates = m.getJSONArray("tmpls");
            HashMap<Integer, String> names = new HashMap<>(templates.length());
            for (JSONObject t: templates.jsonObjectIterable()) {
                names.put(t.getInt("ord"), t.getString("name"));
            }
            result.put(m.getLong("id"), names);
        }
        return result;
    }


    /**
     * @return the ID
     */
    public int getId() {
        return mId;
    }


    public HashMap<Long, Model> getModels() {
        return mModels;
    }


    /**
     * @return Number of models
     */
    public int count() {
        return mModels.size();
    }

    /** Validate model entries. */
	public boolean validateModel() {
        for (Model model : mModels.values()) {
            if (!validateBrackets(model)) {
                return false;
            }
        }
		return true;
	}

	/** Check if there is a right bracket for every left bracket. */
	private boolean validateBrackets(JSONObject value) {
		String s = value.toString();
		int count = 0;
		boolean inQuotes = false;
		char[] ar = s.toCharArray();
		for (int i = 0; i < ar.length; i++) {
			char c = ar[i];
			// if in quotes, do not count
			if (c == '"' && (i == 0 || (ar[i-1] != '\\'))) {
				inQuotes = !inQuotes;
				continue;
			}
			if (inQuotes) {
				continue;
			}
			switch(c) {
			case '{':
				count++;
				break;
			case '}':
				count--;
				if (count < 0) {
					return false;
				}
				break;
			}
		}
		return (count == 0);
	}

}
