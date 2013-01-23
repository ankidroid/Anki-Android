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
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Models {
    private static final Pattern fClozePattern1 = Pattern.compile("(?:\\{\\{|<%)cloze:(.+?)(?:\\}\\}|%>)");
    private static final Pattern fClozePattern2 = Pattern.compile("\\{\\{c(\\d+)::.+?\\}\\}");

    public static final String defaultModel = "{'sortf': 0, " + "'did': 1, " + "'latexPre': \""
            + "\\\\documentclass[12pt]{article} " + "\\\\special{papersize=3in,5in} "
            + "\\\\usepackage[utf8]{inputenc} " + "\\\\usepackage{amssymb,amsmath} " + "\\\\pagestyle{empty} "
            + "\\\\setlength{\\\\parindent}{0in} " + "\\\\begin{document} " + "\", "
            + "'latexPost': \"\\\\end{document}\", " + "'mod': 0, " + "'usn': 0, " 
            + "'vers': [], " // FIXME: remove when other clients have caught up 
            + "'type': "
            + Sched.MODEL_STD + ", " + "'css': \" .card {" + "font-familiy: arial; " + "font-size: 20px; "
            + "text-align: center; " + "color:black; " + "background-color: white; }\"" + "}";

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

    private Collection mCol;
    private boolean mChanged;
    private HashMap<Long, JSONObject> mModels;

    // BEGIN SQL table entries
    private int mId;
    private String mName = "";
    private long mCrt = Utils.intNow();
    private long mMod = Utils.intNow();
    private JSONObject mConf;
    private String mCss = "";
    private JSONArray mFields;
    private JSONArray mTemplates;
    // BEGIN SQL table entries

    // private Decks mDeck;
    // private AnkiDb mDb;
    //
    /** Map for compiled Mustache Templates */
    private Map<String, Template> mCmpldTemplateMap = new HashMap<String, Template>();


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
        mModels = new HashMap<Long, JSONObject>();
        try {
            JSONObject modelarray = new JSONObject(json);
            JSONArray ids = modelarray.names();
            if (ids != null) {
                for (int i = 0; i < ids.length(); i++) {
                    String id = ids.getString(i);
                    JSONObject o = modelarray.getJSONObject(id);
                    mModels.put(o.getLong("id"), o);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Mark M modified if provided, and schedule registry flush.
     */
    public void save() {
        save(null, false);
    }


    public void save(JSONObject m) {
        save(m, false);
    }


    public void save(JSONObject m, boolean templates) {
        if (m != null && m.has("id")) {
            try {
                m.put("mod", Utils.intNow());
                m.put("usn", mCol.usn());
                // TODO: fix empty id problem on _updaterequired (needed for model adding)
                if (m.getLong("id") != 0) {
                    _updateRequired(m);
                }
                if (templates) {
                    _syncTemplates(m);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        mChanged = true;
        // runHook("newModel")
    }


    /**
     * Flush the registry if any models were changed.
     */
    public void flush() {
        if (mChanged) {
            JSONObject array = new JSONObject();
            try {
                for (Map.Entry<Long, JSONObject> o : mModels.entrySet()) {
                    array.put(Long.toString(o.getKey()), o.getValue());
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            ContentValues val = new ContentValues();
            val.put("models", Utils.jsonToString(array));
            mCol.getDb().update("col", val);
            mChanged = false;
        }
    }


    /**
     * Retrieving and creating models
     * ***********************************************************************************************
     */

    /**
     * Get current model.
     * @return The JSONObject of the model, or null if not found in the deck and in the configuration.
     */
    public JSONObject current() {
        return current(true);
    }

    /**
     * Get current model.
     * @param forDeck If true, it tries to get the deck specified in deck by mid, otherwise or if the former is not
     *                found, it uses the configuration`s field curModel.
     * @return The JSONObject of the model, or null if not found in the deck and in the configuration.
     */
    public JSONObject current(boolean forDeck) {
        JSONObject m = null;
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


    public void setCurrent(JSONObject m) {
        try {
            mCol.getConf().put("curModel", m.get("id"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        mCol.setMod();
    }


    /** get model with ID, or none. */
    public JSONObject get(long id) {
        if (mModels.containsKey(id)) {
            return mModels.get(id);
        } else {
            return null;
        }
    }


    /** get all models */
    public ArrayList<JSONObject> all() {
        ArrayList<JSONObject> models = new ArrayList<JSONObject>();
        Iterator<JSONObject> it = mModels.values().iterator();
        while (it.hasNext()) {
            models.add(it.next());
        }
        return models;
    }


    /** get model with NAME. */
    public JSONObject byName(String name) {
        for (JSONObject m : mModels.values()) {
            try {
                if (m.getString("name").equals(name)) {
                    return m;
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }


    /** Create a new model, save it in the registry, and return it. */
    public JSONObject newModel(String name) {
        // caller should call save() after modifying
        JSONObject m;
        try {
            m = new JSONObject(defaultModel);
            m.put("name", name);
            m.put("mod", Utils.intNow());
            m.put("flds", new JSONArray());
            m.put("tmpls", new JSONArray());
            m.put("tags", new JSONArray());
            m.put("id", 0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return m;
    }


    /** Delete model, and all its cards/notes. */
    public void rem(JSONObject m) {
        mCol.modSchema();
        try {
            long id = m.getLong("id");
            boolean current = current().getLong("id") == id;
            // delete notes/cards
            mCol.remCards(Utils.arrayList2array(mCol.getDb().queryColumn(Long.class,
                    "SELECT id FROM cards WHERE nid IN (SELECT id FROM notes WHERE mid = " + id + ")", 0)));
            // then the model
            mModels.remove(id);
            save();
            // GUI should ensure last model is not deleted
            if (current) {
                setCurrent(mModels.values().iterator().next());
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void add(JSONObject m) {
        _setID(m);
        update(m);
        setCurrent(m);
        save(m);
    }


    /** Add or update an existing model. Used for syncing and merging. */
    public void update(JSONObject m) {
        try {
            mModels.put(m.getLong("id"), m);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        // mark registry changed, but don't bump mod time
        save();
    }


    private void _setID(JSONObject m) {
        long id = Utils.intNow(1000);
        while (mModels.containsKey(id)) {
            id = Utils.intNow(1000);
        }
        try {
            m.put("id", id);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean have(long id) {
        return mModels.containsKey(id);
    }


    public long[] ids() {
        Iterator<Long> it = mModels.keySet().iterator();
        long[] ids = new long[mModels.size()];
        int i = 0;
        while (it.hasNext()) {
            ids[i] = it.next();
            i++;
        }
        return ids;
    }


    /**
     * Tools ***********************************************************************************************
     */

    /** Note ids for M */
    public ArrayList<Long> nids(JSONObject m) {
        try {
            return mCol.getDb().queryColumn(Long.class, "SELECT id FROM notes WHERE mid = " + m.getLong("id"), 0);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Number of notes using m
     * @param m The model to the count the notes of.
     * @return The number of notes with that model.
     */
    public int useCount(JSONObject m) {
        try {
            return mCol.getDb().queryScalar("select count() from notes where mid = " + m.getLong("id"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copying ***********************************************************************************************
     */

    /** Copy, save and return. */
    public JSONObject copy(JSONObject m) {
        JSONObject m2 = null;
        try {
            m2 = new JSONObject(Utils.jsonToString(m));
            m2.put("name", m2.getString("name") + " copy");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        add(m2);
        return m2;
    }


    /**
     * Fields ***********************************************************************************************
     */

    public JSONObject newField(String name) {
        JSONObject f;
        try {
            f = new JSONObject(defaultField);
            f.put("name", name);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return f;
    }


    /** "Mapping of field name -> (ord, field). */
    public Map<String, Pair<Integer, JSONObject>> fieldMap(JSONObject m) {
        JSONArray ja;
        try {
            ja = m.getJSONArray("flds");
            // TreeMap<Integer, String> map = new TreeMap<Integer, String>();
            Map<String, Pair<Integer, JSONObject>> result = new HashMap<String, Pair<Integer, JSONObject>>();
            for (int i = 0; i < ja.length(); i++) {
                JSONObject f = ja.getJSONObject(i);
                result.put(f.getString("name"), new Pair<Integer, JSONObject>(f.getInt("ord"), f));
            }
            return result;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public ArrayList<String> fieldNames(JSONObject m) {
        JSONArray ja;
        try {
            ja = m.getJSONArray("flds");
            ArrayList<String> names = new ArrayList<String>();
            for (int i = 0; i < ja.length(); i++) {
                names.add(ja.getJSONObject(i).getString("name"));
            }
            return names;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }


    public int sortIdx(JSONObject m) {
        try {
            return m.getInt("sortf");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    // public int setSortIdx(JSONObject m, int idx) {
    // try {
    // mCol.modSchema();
    // m.put("sortf", idx);
    // mCol.updateFieldCache(nids(m));
    // save(m);
    // } catch (JSONException e) {
    // throw new RuntimeException(e);
    // }
    // }

    public void addField(JSONObject m, JSONObject field) {
        // only mod schema if model isn't new
        try {
            if (m.getLong("id") != 0) {
                mCol.modSchema();
            }
            JSONArray ja = m.getJSONArray("flds");
            ja.put(field);
            m.put("flds", ja);
            _updateFieldOrds(m);
            save(m);
            _transformFields(m, new TransformFieldAdd());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    class TransformFieldAdd implements TransformFieldVisitor {
        @Override
        public String[] transform(String[] fields) {
            String[] f = new String[fields.length + 1];
            System.arraycopy(fields, 0, f, 0, fields.length);
            f[fields.length] = "";
            return f;
        }
    }


    public void remField(JSONObject m, JSONObject field) {
        mCol.modSchema();
        try {
            JSONArray ja = m.getJSONArray("flds");
            JSONArray ja2 = new JSONArray();
            int idx = -1;
            for (int i = 0; i < ja.length(); ++i) {
                if (field.equals(ja.getJSONObject(i))) {
                    idx = i;
                    continue;
                }
                ja2.put(ja.get(i));
            }
            m.put("flds", ja2);
            int sortf = m.getInt("sortf");
            if (sortf >= m.getJSONArray("flds").length()) {
                m.put("sortf", sortf - 1);
            }
            _updateFieldOrds(m);
            _transformFields(m, new TransformFieldDelete(idx));
            if (idx == sortIdx(m)) {
                // need to rebuild
                mCol.updateFieldCache(Utils.toPrimitive(nids(m)));
            }
            renameField(m, field, null);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    class TransformFieldDelete implements TransformFieldVisitor {
        private int idx;


        public TransformFieldDelete(int _idx) {
            idx = _idx;
        }


        @Override
        public String[] transform(String[] fields) {
            ArrayList<String> fl = new ArrayList<String>(Arrays.asList(fields));
            fl.remove(idx);
            return fl.toArray(new String[] {});
        }
    }


    public void moveField(JSONObject m, JSONObject field, int idx) {
        mCol.modSchema();
        try {
            JSONArray ja = m.getJSONArray("flds");
            ArrayList<JSONObject> l = new ArrayList<JSONObject>();
            int oldidx = -1;
            for (int i = 0; i < ja.length(); ++i) {
                l.add(ja.getJSONObject(i));
                if (field.equals(ja.getJSONObject(i))) {
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
            ja = m.getJSONArray("flds");
            for (int i = 0; i < ja.length(); ++i) {
                if (Utils.jsonToString(ja.getJSONObject(i)).equals(sortf)) {
                    m.put("sortf", i);
                    break;
                }
            }
            _updateFieldOrds(m);
            save(m);
            _transformFields(m, new TransformFieldMove(idx, oldidx));
            renameField(m, field, null);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    class TransformFieldMove implements TransformFieldVisitor {
        private int idx;
        private int oldidx;


        public TransformFieldMove(int _idx, int _oldidx) {
            idx = _idx;
            oldidx = _oldidx;
        }


        @Override
        public String[] transform(String[] fields) {
            String val = fields[oldidx];
            ArrayList<String> fl = new ArrayList<String>(Arrays.asList(fields));
            fl.remove(oldidx);
            fl.add(idx, val);
            return fl.toArray(new String[] {});
        }
    }


    public void renameField(JSONObject m, JSONObject field, String newName) {
        mCol.modSchema();
        try {
            String pat = String.format("\\{\\{([:#^/]|[^:#/^}][^:}]*?:|)%s\\}\\}",
                    Pattern.quote(field.getString("name")));
            if (newName == null) {
                newName = "";
            }
            String repl = "{{$1" + newName + "}}";

            JSONArray tmpls = m.getJSONArray("tmpls");
            for (int i = 0; i < tmpls.length(); ++i) {
                JSONObject t = tmpls.getJSONObject(i);
                for (String fmt : new String[] { "qfmt", "afmt" }) {
                    if (!newName.equals("")) {
                        t.put(fmt, t.getString(fmt).replaceAll(pat, repl));
                    } else {
                        t.put(fmt, t.getString(fmt).replaceAll(pat, ""));
                    }
                }
            }
            field.put("name", newName);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save(m);
    }


    public void _updateFieldOrds(JSONObject m) {
        JSONArray ja;
        try {
            ja = m.getJSONArray("flds");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject f = ja.getJSONObject(i);
                f.put("ord", i);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    interface TransformFieldVisitor {
        public String[] transform(String[] fields);
    }


    public void _transformFields(JSONObject m, TransformFieldVisitor fn) {
        // model hasn't been added yet?
        try {
            if (m.getLong("id") == 0) {
                return;
            }
            ArrayList<Object[]> r = new ArrayList<Object[]>();
            Cursor cur = null;

            try {
                cur = mCol.getDb().getDatabase()
                        .rawQuery("select id, flds from notes where mid = " + m.getLong("id"), null);
                while (cur.moveToNext()) {
                    r.add(new Object[] {
                            Utils.joinFields((String[]) fn.transform(Utils.splitFields(cur.getString(1)))),
                            Utils.intNow(), mCol.usn(), cur.getLong(0) });
                }
            } finally {
                if (cur != null) {
                    cur.close();
                }
            }
            mCol.getDb().executeMany("update notes set flds=?,mod=?,usn=? where id = ?", r);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Templates ***********************************************************************************************
     */

    public JSONObject newTemplate(String name) {
        JSONObject t;
        try {
            t = new JSONObject(defaultTemplate);
            t.put("name", name);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return t;
    }


    /** Note: should col.genCards() afterwards. */
    public void addTemplate(JSONObject m, JSONObject template) {
        try {
            if (m.getLong("id") != 0) {
                mCol.modSchema();
            }
            JSONArray ja = m.getJSONArray("tmpls");
            ja.put(template);
            m.put("tmpls", ja);
            _updateTemplOrds(m);
            save(m);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Removing a template
     * 
     * @return False if removing template would leave orphan notes.
     */
    public boolean remTemplate(JSONObject m, JSONObject template) {
        try {
            assert (m.getJSONArray("tmpls").length() > 1);
            // find cards using this template
            JSONArray ja = m.getJSONArray("tmpls");
            int ord = -1;
            for (int i = 0; i < ja.length(); ++i) {
                if (ja.get(i).equals(template)) {
                    ord = i;
                    break;
                }
            }
            String sql = "select c.id from cards c, notes f where c.nid=f.id and mid = " +
                    m.getLong("id") + " and ord = " + ord;
            long[] cids = Utils.toPrimitive(mCol.getDb().queryColumn(Long.class, sql, 0));
            // all notes with this template must have at least two cards, or we could end up creating orphaned notes
            sql = "select nid, count() from cards where nid in (select nid from cards where id in " +
                    Utils.ids2str(cids) + ") group by nid having count() < 2 limit 1";
            if (mCol.getDb().queryScalar(sql, false) != 0) {
                return false;
            }
            // ok to proceed; remove cards
            mCol.modSchema();
            mCol.remCards(cids);
            // shift ordinals
            mCol.getDb()
                    .execute(
                            "update cards set ord = ord - 1, usn = ?, mod = ? where nid in (select id from notes where mid = ?) and ord > ?",
                            new Object[] { mCol.usn(), Utils.intNow(), m.getLong("id"), ord });
            JSONArray tmpls = m.getJSONArray("tmpls");
            JSONArray ja2 = new JSONArray();
            for (int i = 0; i < tmpls.length(); ++i) {
                if (template.equals(tmpls.getJSONObject(i))) {
                    continue;
                }
                ja2.put(tmpls.get(i));
            }
            m.put("tmpls", ja2);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        _updateTemplOrds(m);
        save(m);
        return true;
    }


    public void _updateTemplOrds(JSONObject m) {
        JSONArray ja;
        try {
            ja = m.getJSONArray("tmpls");
            for (int i = 0; i < ja.length(); i++) {
                JSONObject f = ja.getJSONObject(i);
                f.put("ord", i);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public void moveTemplate(JSONObject m, JSONObject template, int idx) {
        try {
            JSONArray ja = m.getJSONArray("tmpls");
            int oldidx = -1;
            ArrayList<JSONObject> l = new ArrayList<JSONObject>();
            HashMap<Integer, Integer> oldidxs = new HashMap<Integer, Integer>();
            for (int i = 0; i < ja.length(); ++i) {
                if (ja.get(i).equals(template)) {
                    oldidx = i;
                    if (idx == oldidx) {
                        return;
                    }
                }
                JSONObject t = ja.getJSONObject(i);
                oldidxs.put(t.hashCode(), t.getInt("ord"));
                l.add(t);
            }
            l.remove(oldidx);
            l.add(idx, template);
            m.put("tmpls", new JSONArray(l));
            _updateTemplOrds(m);
            // generate change map - We use StringBuilder
            StringBuilder sb = new StringBuilder();
            ja = m.getJSONArray("tmpls");
            for (int i = 0; i < ja.length(); ++i) {
                JSONObject t = ja.getJSONObject(i);
                sb.append("when ord = ").append(oldidxs.get(t.hashCode())).append(" then ").append(t.getInt("ord"));
                if (i != ja.length() - 1) {
                    sb.append(" ");
                }
            }
            // apply
            save(m);
            mCol.getDb().execute("update cards set ord = (case " + sb.toString() +
            		" end),usn=?,mod=? where nid in (select id from notes where mid = ?)",
                    new Object[] { mCol.usn(), Utils.intNow(), m.getLong("id") });
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private void _syncTemplates(JSONObject m) {
        ArrayList<Long> rem = mCol.genCards(Utils.arrayList2array(nids(m)));
    }


    // public TreeMap<Integer, JSONObject> getTemplates() {
    // return mTemplateMap;
    // }
    //
    //
    // public JSONObject getTemplate(int ord) {
    // return mTemplateMap.get(ord);
    // }

    /**
     * Get a compiled template, create it if missing or if args != null
     * 
     * @param modelId
     * @param ord
     * @param args Pass it as [qfmt, afmt] to use custom format, or [] to use the format from model
     * @return
     */
    public Template getCmpldTemplate(String format) {
        if (!mCmpldTemplateMap.containsKey(format)) {
            mCmpldTemplateMap.put(format, Mustache.compiler().compile(format));
        }

        return mCmpldTemplateMap.get(format);
    }


    // not in libanki
    // Handle fields fetched from templates and any anki-specific formatting
    protected static final String clozeReg = "\\{\\{c%s::(.*?)(::(.*?))?\\}\\}";

    protected static class fieldParser implements Mustache.VariableFetcher {
        private Map<String, String> _fields;


        public fieldParser(Map<String, String> fields) {
            _fields = fields;
        }


        public Object get(Object ctx, String tag_name) throws Exception {
            if (tag_name.length() == 0) {
                return null;
            }
            String txt = _fields.get(tag_name);
            if (txt != null) {
                return txt;
            }

            // field modifiers
            String[] parts = tag_name.split(":", 3);
            String mod = null, extra = null, tag = null;
            if (parts.length == 1 || parts[0].equals("")) {
                return null;
            } else if (parts.length == 2) {
                mod = parts[0];
                tag = parts[1];
            } else if (parts.length == 3) {
                mod = parts[0];
                extra = parts[1];
                tag = parts[2];
            }

            txt = _fields.get(tag);

            Log.d(AnkiDroidApp.TAG, "Processing field modifier " + mod + ": extra = " + extra + ", field " + tag + " = " + txt);

            // built-in modifiers
            if (mod.equals("text")) {
                // strip html
                if (txt != null && txt.length() > 0) {
                    return Utils.stripHTML(txt);
                }
                return "";
            } else if (mod.equals("type")) {
                // type answer field; convert it to [[type:...]] for the gui code to process
                return "[[" + tag_name + "]]";
            } else if (mod.equals("cq") || mod.equals("ca")) {
                // cloze deletion
                if (txt != null && txt.length() != 0 && extra != null && extra.length() != 0) {
                    return clozeText(txt, extra, mod.charAt(1));
                } else {
                    return "";
                }
            } else {
                // hook-based field modifier
                if (txt == null) {
                    txt = (String) AnkiDroidApp.getHooks().runFilter("fmod_" + mod, "", extra, AnkiDroidApp.getAppResources(), tag, tag_name);
                } else {
                    txt = (String) AnkiDroidApp.getHooks().runFilter("fmod_" + mod, txt, extra, AnkiDroidApp.getAppResources(), tag, tag_name);
                }
                if (txt == null) {
                    return "{unknown field " + tag_name + "}";
                }
                return txt;
            }
        }


        private static String clozeText(String txt, String ord, char type) {
            Matcher m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(txt);
            if (!m.find()) {
                return "";
            }
            // replace chozen cloze with type
            if (type == 'q') {
                if (m.group(3) != null && m.group(3).length() != 0) {
                    txt = m.replaceAll("<span class=cloze>[$3]</span>");
                } else {
                    txt = m.replaceAll("<span class=cloze>[...]</span>");
                }
            } else {
                txt = m.replaceAll("<span class=cloze>$1</span>");
            }
            // and display other clozes normally
            return txt.replaceAll(String.format(Locale.US, clozeReg, ".*?"), "$1");
        }
    }


    /**
     * Model changing ***********************************************************************************************
     */

    /**
     * Change a model
     * @param m The model to change.
     * @param nids The list of notes that the change applies to.
     * @param newModel For replacing the old model with another one. Should be self if the model is not changing
     * @param fmap Field map for switching fields. This is ord->ord and there should not be duplicate targets 
     * @param cmap Field map for switching fields. This is ord->ord and there should not be duplicate targets
     */
    public void change(JSONObject m, long[] nids, JSONObject newModel, Map<Integer, Integer> fmap, Map<Integer, Integer> cmap) {
        mCol.modSchema();
        try {
            assert (newModel.getLong("id") == m.getLong("id")) || (fmap != null && cmap != null);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (fmap != null) {
            _changeNotes(nids, newModel, fmap);
        }
        if (cmap != null) {
            _changeCards(nids, m, newModel, cmap);
        }
        mCol.genCards(nids);
    }
    
    private void _changeNotes(long[] nids, JSONObject newModel, Map<Integer, Integer> map) {
        List<Object[]> d = new ArrayList<Object[]>();
        int nfields;
        long mid;
        try {
            nfields = newModel.getJSONArray("flds").length();
            mid = newModel.getLong("id");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(
                    "select id, flds from notes where id in ".concat(Utils.ids2str(nids)), null);
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                String[] flds = Utils.splitFields(cur.getString(1));
                Map<Integer, String> newflds = new HashMap<Integer, String>();
                
                for (Integer old : map.keySet()) {
                    newflds.put(map.get(old), flds[old]);
                }
                List<String> flds2 = new ArrayList<String>();
                for (int c = 0; c < nfields; ++c) {
                    if (newflds.containsKey(c)) {
                        flds2.add(newflds.get(c));
                    } else {
                        flds2.add("");
                    }
                }
                String joinedFlds = Utils.joinFields(flds2.toArray(new String[]{}));
                d.add(new Object[] { joinedFlds, mid, Utils.intNow(), mCol.usn(), nid });
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        mCol.getDb().executeMany("update notes set flds=?,mid=?,mod=?,usn=? where id = ?", d);
        mCol.updateFieldCache(nids);
    }
    
    private void _changeCards(long[] nids, JSONObject oldModel, JSONObject newModel, Map<Integer, Integer> map) {
        List<Object[]> d = new ArrayList<Object[]>();
        List<Long> deleted = new ArrayList<Long>();
        Cursor cur = null;
        int omType;
        int nmType;
        int nflds;
        try {
            omType = oldModel.getInt("type");
            nmType = newModel.getInt("type");
            nflds = newModel.getJSONArray("tmpls").length();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        try {
            cur = mCol.getDb().getDatabase().rawQuery(
                    "select id, ord from cards where nid in ".concat(Utils.ids2str(nids)), null);
            while (cur.moveToNext()) {
                // if the src model is a cloze, we ignore the map, as the gui doesn't currently
                // support mapping them
                Integer newOrd;
                long cid = cur.getLong(0); 
                int ord = cur.getInt(1);
                if (omType == Sched.MODEL_CLOZE) {
                    newOrd = cur.getInt(1);
                    if (nmType != Sched.MODEL_CLOZE) {
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
                    d.add(new Object[] { newOrd, mCol.usn(), Utils.intNow(), cid });
                } else {
                    deleted.add(cid);
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        mCol.getDb().executeMany("update cards set ord=?,usn=?,mod=? where id=?", d);
        mCol.remCards(Utils.toPrimitive(deleted));
    }

    /**
     * Schema hash ***********************************************************************************************
     */

    /** Return a hash of the schema, to see if models are compatible. */
    public String scmhash(JSONObject m) {
        String s = "";
        try {
        	JSONArray flds = m.getJSONArray("flds");
            for (int i = 0; i < flds.length(); ++i) {
                s += flds.getJSONObject(i).getString("name");
            }
            JSONArray tmpls = m.getJSONArray("tmpls");
            for (int i = 0; i < tmpls.length(); ++i) {
            	JSONObject t = tmpls.getJSONObject(i);
                s += t.getString("name");
                s += t.getString("qfmt");
                s += t.getString("afmt");
           }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return Utils.checksum(s);
    }


    /**
     * Required field/text cache
     * ***********************************************************************************************
     */

    private void _updateRequired(JSONObject m) {
        try {
            if (m.getInt("type") == Sched.MODEL_CLOZE) {
                // nothing to do
                return;
            }
            JSONArray req = new JSONArray();
            ArrayList<String> flds = new ArrayList<String>();
            JSONArray fields;
            fields = m.getJSONArray("flds");
            for (int i = 0; i < fields.length(); i++) {
                flds.add(fields.getJSONObject(i).getString("name"));
            }
            JSONArray templates = m.getJSONArray("tmpls");
            for (int i = 0; i < templates.length(); i++) {
                JSONObject t = templates.getJSONObject(i);
                Object[] ret = _reqForTemplate(m, flds, t);
                JSONArray r = new JSONArray();
                r.put(t.getInt("ord"));
                r.put(ret[0]);
                r.put(ret[1]);
                req.put(r);
            }
            m.put("req", req);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    private Object[] _reqForTemplate(JSONObject m, ArrayList<String> flds, JSONObject t) {
        try {
            ArrayList<String> a = new ArrayList<String>();
            ArrayList<String> b = new ArrayList<String>();
            for (String f : flds) {
                a.add("ankiflag");
                b.add("");
            }
            Object[] data;
            data = new Object[] { 1l, 1l, m.getLong("id"), 1l, t.getInt("ord"), "",
                    Utils.joinFields(a.toArray(new String[a.size()])) };
            String full = mCol._renderQA(data).get("q");
            data = new Object[] { 1l, 1l, m.getLong("id"), 1l, t.getInt("ord"), "",
                    Utils.joinFields(b.toArray(new String[b.size()])) };
            String empty = mCol._renderQA(data).get("q");
            // if full and empty are the same, the template is invalid and there is no way to satisfy it
            if (full.equals(empty)) {
                return new Object[] { "none", new JSONArray(), new JSONArray() };
            }
            String type = "all";
            JSONArray req = new JSONArray();
            ArrayList<String> tmp = new ArrayList<String>();
            for (int i = 0; i < flds.size(); i++) {
                tmp.clear();
                tmp.addAll(a);
                tmp.set(i, "");
                data[6] = Utils.joinFields(tmp.toArray(new String[tmp.size()]));
                // if no field content appeared, field is required
                if (!mCol._renderQA(data, new ArrayList<String>()).get("q").contains("ankiflag")) {
                    req.put(i);
                }
            }
            if (req.length() > 0) {
                return new Object[] { type, req };
            }
            // if there are no required fields, switch to any mode
            type = "any";
            req = new JSONArray();
            for (int i = 0; i < flds.size(); i++) {
                tmp.clear();
                tmp.addAll(b);
                tmp.set(i, "1");
                data[6] = Utils.joinFields(tmp.toArray(new String[tmp.size()]));
                // if not the same as empty, this field can make the card non-blank
                if (!mCol._renderQA(data).get("q").equals(empty)) {
                    req.put(i);
                }
            }
            return new Object[] { type, req };
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /** Given a joined field string, return available template ordinals */
    public ArrayList<Integer> availOrds(JSONObject m, String flds) {
        try {
            if (m.getInt("type") == Sched.MODEL_CLOZE) {
                return _availClozeOrds(m, flds);
            }
            String[] fields = Utils.splitFields(flds);
            for (String f : fields) {
                f = f.trim();
            }
            ArrayList<Integer> avail = new ArrayList<Integer>();
            JSONArray reqArray = m.getJSONArray("req");
            for (int i = 0; i < reqArray.length(); i++) {
                JSONArray sr = reqArray.getJSONArray(i);

                int ord = sr.getInt(0);
                String type = sr.getString(1);
                JSONArray req = sr.getJSONArray(2);

                if (type.equals("none")) {
                    // unsatisfiable template
                    continue;
                } else if (type.equals("all")) {
                    // AND requirement?
                    boolean ok = true;
                    for (int j = 0; j < req.length(); j++) {
                        int idx = req.getInt(j);
                        if (fields[idx] == null || fields[idx].length() == 0) {
                            // missing and was required
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                } else if (type.equals("any")) {
                    // OR requirement?
                    boolean ok = false;
                    for (int j = 0; j < req.length(); j++) {
                        int idx = req.getInt(j);
                        if (fields[idx] != null && fields[idx].length() != 0) {
                            // missing and was required
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                }
                avail.add(ord);
            }
            return avail;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    public ArrayList<Integer> _availClozeOrds(JSONObject m, String flds) {
        return _availClozeOrds(m, flds, true);
    }


    public ArrayList<Integer> _availClozeOrds(JSONObject m, String flds, boolean allowEmpty) {
        String[] sflds = Utils.splitFields(flds);
        Map<String, Pair<Integer, JSONObject>> map = fieldMap(m);
        Set<Integer> ords = new HashSet<Integer>();
        Matcher matcher1 = null;
        try {
            matcher1 = fClozePattern1.matcher(m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt"));
            // Libanki makes two finds for each case of the cloze tags, but we embed both in the pattern.
            // Please note, that this approach is not 100% correct, as we allow cases like {{cloze:...%>
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        while (matcher1.find()) {
            String fname = matcher1.group(1);
            if (!map.containsKey(fname)) {
                continue;
            }
            int ord = map.get(fname).first;
            Matcher matcher2 = fClozePattern2.matcher(sflds[ord]);
            while (matcher2.find()) {
                ords.add(Integer.parseInt(matcher2.group(1)) - 1);
            }
        }
        if (ords.contains(-1)) {
            ords.remove(-1);
        }
        if (ords.isEmpty() && allowEmpty) {
            // empty clozes use first ord
            return new ArrayList<Integer>(Arrays.asList(new Integer[] { 0 }));
        }
        return new ArrayList<Integer>(ords);
    }


    /**
     * Sync handling ***********************************************************************************************
     */

    public void beforeUpload() {
        try {
            for (JSONObject m : all()) {
                m.put("usn", 0);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        save();
    }


    /**
     * Routines from Stdmodels.py
     * ***********************************************************************************************
     */

    public static JSONObject addBasicModel(Collection col) {
        return addBasicModel(col, "Basic");
    }


    public static JSONObject addBasicModel(Collection col, String name) {
        Models mm = col.getModels();
        JSONObject m = mm.newModel(name);
        JSONObject fm = mm.newField("Front");
        mm.addField(m, fm);
        fm = mm.newField("Back");
        mm.addField(m, fm);
        JSONObject t = mm.newTemplate("Card 1");
        try {
            t.put("qfmt", "{{Front}}");
            t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Back}}");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        mm.addTemplate(m, t);
        mm.add(m);
        return m;
    }

    /* Forward & Reverse */
    
    public static JSONObject addForwardReverse(Collection col) {
    	String name = "Basic (and reversed card)";
        Models mm = col.getModels();
        JSONObject m = addBasicModel(col);
        try {
            m.put("name", name);
            JSONObject t = mm.newTemplate("Card 2");
            t.put("qfmt", "{{Back}}");
            t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Front}}");
            mm.addTemplate(m, t);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return m;
    }

    
    /* Forward & Optional Reverse */
    
    public static JSONObject addForwardOptionalReverse(Collection col) {
    	String name = "Basic (optional reversed card)";
        Models mm = col.getModels();
        JSONObject m = addBasicModel(col);
        try {
            m.put("name", name);
            JSONObject fm = mm.newField("Add Reverse");
            mm.addField(m, fm);
            JSONObject t = mm.newTemplate("Card 2");
            t.put("qfmt", "{{#Add Reverse}}{{Back}}{{/Add Reverse}}");
            t.put("afmt", "{{FrontSide}}\n\n<hr id=answer>\n\n{{Front}}");
            mm.addTemplate(m, t);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return m;
    }

    
    public static JSONObject addClozeModel(Collection col) {
        Models mm = col.getModels();
        JSONObject m = mm.newModel("Cloze");
        try {
            m.put("type", Sched.MODEL_CLOZE);
            String txt = "Text";
            JSONObject fm = mm.newField(txt);
            mm.addField(m, fm);
            fm = mm.newField("Extra");
            mm.addField(m, fm);
            JSONObject t = mm.newTemplate("Cloze");
            String fmt = "{{cloze:" + txt + "}}";
            m.put("css", m.getString("css") + ".cloze {" + "font-weight: bold;" + "color: blue;" + "}");
            t.put("qfmt", fmt);
            t.put("afmt", fmt + "<br>\n{{Extra}}");
            mm.addTemplate(m, t);
            mm.add(m);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return m;
    }


    /**
     * Other stuff NOT IN LIBANKI
     * ***********************************************************************************************
     */

    public void setChanged() {
        mChanged = true;
    }


    public HashMap<Long, HashMap<Integer, String>> getTemplateNames() {
        HashMap<Long, HashMap<Integer, String>> result = new HashMap<Long, HashMap<Integer, String>>();
        for (JSONObject m : mModels.values()) {
            JSONArray templates;
            try {
                templates = m.getJSONArray("tmpls");
                HashMap<Integer, String> names = new HashMap<Integer, String>();
                for (int i = 0; i < templates.length(); i++) {
                    JSONObject t = templates.getJSONObject(i);
                    names.put(t.getInt("ord"), t.getString("name"));
                }
                result.put(m.getLong("id"), names);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }


    /**
     * @return the ID
     */
    public int getId() {
        return mId;
    }


    /**
     * @return the name
     */
    public String getName() {
        return mName;
    }


    public HashMap<Long, JSONObject> getModels() {
        return mModels;
    }

    /** Validate model entries. */
	public boolean validateModel() {
		Iterator<Entry<Long, JSONObject>> iterator = mModels.entrySet().iterator();
		while (iterator.hasNext()) {
			if (!validateBrackets(iterator.next().getValue())) {			
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
