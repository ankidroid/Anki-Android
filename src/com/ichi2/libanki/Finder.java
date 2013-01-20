/****************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
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

import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.Pair;
import com.ichi2.upgrade.Upgrade;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Finder {

    public static Pattern allPattern = Pattern
            .compile("(-)?\\'(([^\\'\\\\]|\\\\.)*)\\'|(-)?\"(([^\"\\\\]|\\\\.)*)\"|(-)?([^ ]+)|([ ]+)");
    private static final Pattern fPropPattern = Pattern.compile("(^.+?)(<=|>=|!=|=|<|>)(.+?$)");
    private static final Pattern fNidsPattern = Pattern.compile("[^0-9,]");

    private static final List<String> fValidEases = Arrays.asList(new String[] { "1", "2", "3", "4" });
    private static final List<String> fValidProps = Arrays
            .asList(new String[] { "due", "ivl", "reps", "lapses", "ease" });

    private Collection mCol;


    public Finder(Collection col) {
        mCol = col;
    }


    /** Return a list of card ids for QUERY */
    public List<Long> findCards(String query, String _order) {
        String[] tokens = _tokenize(query);
        Pair<String, String[]> res1 = _where(tokens);
        String preds = res1.first;
        String[] args = res1.second;
        List<Long> res = new ArrayList<Long>();
        if (preds == null) {
            return res;
        }
        Pair<String, Boolean> res2 = _order(_order);
        String order = res2.first;
        boolean rev = res2.second;
        String sql = _query(preds, order, false);
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(sql, args);
            while (cur.moveToNext()) {
                res.add(cur.getLong(0));
            }
        } catch (SQLException e) {
            // invalid grouping
            return new ArrayList<Long>();
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        if (rev) {
            Collections.reverse(res);
        }
        return res;
    }

    /** Return a list of card ids for QUERY */
    public ArrayList<HashMap<String, String>> findCardsForCardBrowser(String query, String _order, HashMap<String, String> deckNames) {
        String[] tokens = _tokenize(query);
        Pair<String, String[]> res1 = _where(tokens);
        String preds = res1.first;
        String[] args = res1.second;
        ArrayList<HashMap<String, String>> res = new ArrayList<HashMap<String, String>>();
        if (preds == null) {
            return res;
        }
        Pair<String, Boolean> res2 = _order(_order);
        String order = res2.first;
        boolean rev = res2.second;
        String sql = _query(preds, order, true);
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(sql, args);
            while (cur.moveToNext()) {
                HashMap<String, String> map = new HashMap<String, String>();
                map.put("id", cur.getString(0));
                map.put("sfld", cur.getString(1));
                map.put("deck", deckNames.get(cur.getString(2)));
                int queue = cur.getInt(3);
                String tags = cur.getString(4);
                map.put("flags", Integer.toString((queue == -1 ? 1 : 0) + (tags.matches(".*[Mm]arked.*") ? 2 : 0)));
                map.put("tags", tags);
                res.add(map);
            }
        } catch (SQLException e) {
            // invalid grouping
            Log.e(AnkiDroidApp.TAG, "Invalid grouping, sql: " + sql);
            return new ArrayList<HashMap<String, String>>();
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        if (rev) {
            Collections.reverse(res);
        }
        return res;
    }

    public List<Long> findNotes(String query) {
        String[] tokens = _tokenize(query);
        Pair<String, String[]> res1 = _where(tokens);
        String preds = res1.first;
        String[] args = res1.second;
        List<Long> res = new ArrayList<Long>();
        if (preds == null) {
            return res;
        }
        if (preds.equals("")) {
            preds = "1";
        } else {
            preds = "(" + preds + ")";
        }
        String sql = "select distinct(n.id) from cards c, notes n where c.nid=n.id and " + preds;
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(sql, args);
            while (cur.moveToNext()) {
                res.add(cur.getLong(0));
            }
        } catch (SQLException e) {
            // invalid grouping
            return new ArrayList<Long>();
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return res;
    }


    // Tokenizing
    // ///////////
    public String[] _tokenize(String query) {
        char inQuote = 0;
        List<String> tokens = new ArrayList<String>();
        String token = "";
        for (int i = 0; i < query.length(); ++i) {
            // quoted text
            char c = query.charAt(i);
            if (c == '\'' || c == '"') {
                if (inQuote != 0) {
                    if (c == inQuote) {
                        inQuote = 0;
                    } else {
                        token += c;
                    }
                } else if (token.length() != 0) {
                    // quotes are allowed to start directly after a :
                    if (token.endsWith(":")) {
                        inQuote = c;
                    } else {
                        token += c;
                    }
                } else {
                    inQuote = c;
                }
                // separator
            } else if (c == ' ') {
                if (inQuote != 0) {
                    token += c;
                } else if (token.length() != 0) {
                    // space marks token finished
                    tokens.add(token);
                    token = "";
                }
                // nesting
            } else if (c == '(' || c == ')') {
                if (inQuote != 0) {
                    token += c;
                } else {
                    if (c == ')' && token.length() != 0) {
                        tokens.add(token);
                        token = "";
                    }
                    tokens.add(String.valueOf(c));
                }
                // negation
            } else if (c == '-') {
                if (token.length() != 0) {
                    token += c;
                } else if (tokens.size() == 0 || !tokens.get(tokens.size() - 1).equals("-")) {
                    tokens.add("-");
                }
                // normal character
            } else {
                token += c;
            }
        }
        // if we finished in a token, add it
        if (token.length() != 0) {
            tokens.add(token);
        }
        return tokens.toArray(new String[] {});
    }

    // Query building
    // ///////////////
    public class SearchState {
        public boolean isnot;
        public boolean isor;
        public boolean join;
        public String q;
        public boolean bad;


        public SearchState() {
            isnot = false;
            isor = false;
            join = false;
            q = "";
            bad = false;
        }
    }


    public Pair<String, String[]> _where(String[] tokens) {
        // state and query
        SearchState s = new SearchState();
        List<String> args = new ArrayList<String>();
        for (String token : tokens) {
            if (s.bad) {
                return new Pair<String, String[]>(null, null);
            }
            // special tokens
            if (token.equals("-")) {
                s.isnot = true;
            } else if (token.toLowerCase().equals("or")) {
                s.isor = true;
            } else if (token.equals("(")) {
                addPred(s, token, false);
                s.join = false;
            } else if (token.equals(")")) {
                s.q += ")";
                // commands
            } else if (token.contains(":")) {
                String[] spl = token.split(":", 2);
                String cmd = spl[0].toLowerCase();
                String val = spl[1];
                if (cmd.equals("tag")) {
                    addPred(s, _findTag(val, args));
                } else if (cmd.equals("is")) {
                    addPred(s, _findCardState(val));
                } else if (cmd.equals("nid")) {
                    addPred(s, _findNids(val));
                } else if (cmd.equals("card")) {
                    addPred(s, _findTemplate(val));
                } else if (cmd.equals("note")) {
                    addPred(s, _findModel(val));
                } else if (cmd.equals("deck")) {
                    addPred(s, _findDeck(val));
                } else if (cmd.equals("prop")) {
                    addPred(s, _findProp(val));
                } else if (cmd.equals("rated")) {
                    addPred(s, _findRated(val));
                } else if (cmd.equals("added")) {
                    addPred(s, _findAdded(val));
                } else {
                    addPred(s, _findField(cmd, val));
                }
                // normal text search
            } else {
                addPred(s, _findText(token, args));
            }
        }
        if (s.bad) {
            return new Pair<String, String[]>(null, null);
        }
        return new Pair<String, String[]>(s.q, args.toArray(new String[] {}));
    }


    private void addPred(SearchState s, String txt) {
        addPred(s, txt, true);
    }


    private void addPred(SearchState s, String txt, boolean wrap) {
        // failed command
        if (txt == null || txt.length() == 0) {
            // if it was to be negated then we can just ignore it
            if (s.isnot) {
                s.isnot = false;
                return;
            } else {
                s.bad = true;
                return;
            }
        } else if (txt.equals("skip")) {
            return;
        }
        // do we need a conjunction
        if (s.join) {
            if (s.isor) {
                s.q += " or ";
                s.isor = false;
            } else {
                s.q += " and ";
            }
        }
        if (s.isnot) {
            s.q += " not ";
            s.isnot = false;
        }
        if (wrap) {
            txt = "(" + txt + ")";
        }
        s.q += txt;
        s.join = true;
    }


    private String _query(String preds, String order, boolean forCardBrowser) {
        // can we skip the note table?
        String sql;
        if (forCardBrowser) {
            sql = "select c.id, n.sfld, c.did, c.queue, n.tags from cards c, notes n where c.nid=n.id and ";
        } else {
            if (!preds.contains("n.") && !order.contains("n.")) {
                sql = "select c.id from cards c where ";
            } else {
                sql = "select c.id from cards c, notes n where c.nid=n.id and ";
            }
        }
        // combine with preds
        if (preds.length() != 0) {
            sql += "(" + preds + ")";
        } else {
            sql += "1";
        }
        // order
        if (order != null && order.length() != 0) {
            sql += " " + order;
        }
        return sql;
    }


    // Ordering
    // /////////

    private Pair<String, Boolean> _order(String order) {
        if (order == null || order.length() == 0) {
            return new Pair<String, Boolean>("", false);
        } else if (!order.equalsIgnoreCase("true")) {
            // custom order string provided
            return new Pair<String, Boolean>(" order by " + order, false);
        }
        // use deck default
        String type;
        try {
            type = mCol.getConf().getString("sortType");
            String sort = null;
            if (type.startsWith("note")) {
                if (type.startsWith("noteCrt")) {
                    sort = "n.id, c.ord";
                } else if (type.startsWith("noteMod")) {
                    sort = "n.mod, c.ord";
                } else if (type.startsWith("noteFld")) {
                    sort = "n.sfld COLLATE NOCASE, c.ord";
                }
            } else if (type.startsWith("card")) {
                if (type.startsWith("cardMod")) {
                    sort = "c.mod";
                } else if (type.startsWith("cardReps")) {
                    sort = "c.reps";
                } else if (type.startsWith("cardDue")) {
                    sort = "c.type, c.due";
                } else if (type.startsWith("cardEase")) {
                    sort = "c.factor";
                } else if (type.startsWith("cardLapses")) {
                    sort = "c.lapses";
                } else if (type.startsWith("cardIvl")) {
                    sort = "c.ivl";
                }
            }
            if (sort == null) {
            	// deck has invalid sort order; revert to noteCrt
            	sort = "n.id, c.ord";
            }
            boolean sortBackwards = Upgrade.upgradeJSONIfNecessary(mCol, mCol.getConf(), "sortBackwards", false);
            return new Pair<String, Boolean>(" ORDER BY " + sort, sortBackwards);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    // Commands
    // /////////

    private String _findTag(String val, List<String> args) {
        if (val.equals("none")) {
            return "n.tags = \"\"";
        }
        val = val.replace("*", "%");
        if (!val.startsWith("%")) {
            val = "% " + val;
        }
        if (!val.endsWith("%")) {
            val += " %";
        }
        args.add(val);
        return "n.tags like ?";
    }


    private String _findCardState(String val) {
        int n;
        if (val.equals("review") || val.equals("new") || val.equals("learn")) {
            if (val.equals("review")) {
                n = 2;
            } else if (val.equals("new")) {
                n = 0;
            } else {
		return "queue IN (1, 3)";
            }
            return "type = " + n;
        } else if (val.equals("suspended")) {
            return "c.queue = -1";
        } else if (val.equals("due")) {
            return "(c.queue in (2,3) and c.due <= " + mCol.getSched().getToday() +
                    ") or (c.queue = 1 and c.due <= " + mCol.getSched().getDayCutoff() + ")";
        } else {
            return null;
        }
    }


    private String _findRated(String val) {
        // days(:optional_ease)
        String[] r = val.split(":");
        int days;
        try {
            days = Integer.parseInt(r[0]);
        } catch (NumberFormatException e) {
            return "";
        }
        days = Math.min(days, 31);
        // ease
        String ease = "";
        if (r.length > 1) {
            if (!fValidEases.contains(r[1])) {
                return "";
            }
            ease = "and ease=" + r[1];
        }
        long cutoff = (mCol.getSched().getDayCutoff() - 86400 * days) * 1000;
        return "c.id in (select cid from revlog where id>" + cutoff + " " + ease + ")";
    }


    private String _findAdded(String val) {
        int days;
        try {
            days = Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return "";
        }
        long cutoff = (mCol.getSched().getDayCutoff() - 86400 * days) * 1000;
        return "c.id > " + cutoff;
    }


    private String _findProp(String _val) {
        // extract
        Matcher m = fPropPattern.matcher(_val);
        if (!m.matches()) {
            return "";
        }
        String prop = m.group(1).toLowerCase();
        String cmp = m.group(2);
        String sval = m.group(3);
        int val;
        // is val valid?
        try {
            if (prop.equals("ease")) {
                // This multiplying and convert to int happens later in libanki, moved it here for efficiency
                val = (int) (Double.parseDouble(sval) * 1000);
            } else {
                val = Integer.parseInt(sval);
            }
        } catch (NumberFormatException e) {
            return "";
        }
        // is prop valid?
        if (!fValidProps.contains(prop)) {
            return "";
        }
        // query
        String q = "";
        if (prop.equals("due")) {
            val += mCol.getSched().getToday();
            // only valid for review/daily learning
            q = "(c.queue in (2,3)) and ";
        } else if (prop.equals("ease")) {
            prop = "factor";
            // already done: val = int(val*1000)
        }
        q += "(" + prop + " " + cmp + " " + val + ")";
        return q;
    }


    private String _findText(String val, List<String> args) {
        val = val.replace("*", "%");
        args.add("%" + val + "%");
        args.add("%" + val + "%");
        return "(n.sfld like ? escape '\\' or n.flds like ? escape '\\')";
    }


    private String _findNids(String val) {
        if (fNidsPattern.matcher(val).find()) {
            return "";
        }
        return "n.id in (" + val + ")";
    }


    private String _findModel(String val) {
        LinkedList<Long> ids = new LinkedList<Long>();
        val = val.toLowerCase();
        try {
            for (JSONObject m : mCol.getModels().all()) {
                if (m.getString("name").toLowerCase().equals(val)) {
                    ids.add(m.getLong("id"));
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return "n.mid in " + Utils.ids2str(ids);
    }


    private List<Long> dids(long did) {
        if (did == 0) {
            return null;
        }
        TreeMap<String, Long> children = mCol.getDecks().children(did);

        List<Long> res = new ArrayList<Long>();
        res.add(did);
        res.addAll(children.values());
        return res;
    }


    public String _findDeck(String val) {
        // if searching for all decks, skip
        if (val.equals("*")) {
            return "skip";
            // deck types
        } else if (val.equals("filtered")) {
            return "c.odid";
        }

        List<Long> ids = null;
        // current deck?
        try {
            if (val.toLowerCase().equals("current")) {
                ids = dids(mCol.getDecks().current().getLong("id"));
            } else if (!val.contains("*")) {
                // single deck
                ids = dids(mCol.getDecks().id(val, false));
            } else {
                // widlcard
                ids = new ArrayList<Long>();
                val = val.replace("*", ".*");
                for (JSONObject d : mCol.getDecks().all()) {
                    if (d.getString("name").matches("(?i)" + val)) {
                        for (long id : dids(d.getLong("id"))) {
                            ids.add(id);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (ids == null || ids.size() == 0) {
            return "";
        }
        String sids = Utils.ids2str(ids);
        return "c.did in " + sids + " or c.odid in " + sids;
    }


    private String _findTemplate(String val) {
        // were we given an ordinal number?
        Integer num = null;
        try {
            num = Integer.parseInt(val) - 1;
        } catch (NumberFormatException e) {
            num = null;
        }
        if (num != null) {
            return "c.ord = " + num;
        }
        // search for template names
        List<String> lims = new ArrayList<String>();
        try {
            for (JSONObject m : mCol.getModels().all()) {
                JSONArray tmpls = m.getJSONArray("tmpls");
                for (int ti = 0; ti < tmpls.length(); ++ti) {
                    JSONObject t = tmpls.getJSONObject(ti);
                    if (t.getString("name").equalsIgnoreCase(val)) {
                        if (m.getInt("type") == Sched.MODEL_CLOZE) {
                            // if the user has asked for a cloze card, we want
                            // to give all ordinals, so we just limit to the
                            // model instead
                            lims.add("(n.mid = " + m.getLong("id") + ")");
                        } else {
                            lims.add("(n.mid = " + m.getLong("id") + " and c.ord = " +
                                    t.getInt("ord") + ")");
                        }
                    }
                }

            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return Utils.join(" or ", lims.toArray(new String[] {}));
    }


    private String _findField(String field, String val) {
        val = val.replace("*", "%");
        // find models that have that field
        Map<Long, Object[]> mods = new HashMap<Long, Object[]>();
        try {
            for (JSONObject m : mCol.getModels().all()) {
                JSONArray flds = m.getJSONArray("flds");
                for (int fi = 0; fi < flds.length(); ++fi) {
                    JSONObject f = flds.getJSONObject(fi);
                    if (f.getString("name").equalsIgnoreCase(field)) {
                        mods.put(m.getLong("id"), new Object[] { m, f.getInt("ord") });
                    }

                }

            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (mods.isEmpty()) {
            // nothing has that field
            return "";
        }
        // gather nids
        // Pattern.quote escapes the meta characters with \Q \E
        String regex = Pattern.quote(val).replace("\\Q_\\E", ".").replace("\\Q%\\E", ".*");
        LinkedList<Long> nids = new LinkedList<Long>();
        Cursor cur = null;
        try {
            cur = mCol.getDb().getDatabase().rawQuery(
                    "select id, mid, flds from notes where mid in " +
                    Utils.ids2str(new LinkedList<Long>(mods.keySet())) +
                    " and flds like ? escape '\\'", new String[] { "%" + val + "%" });
            while (cur.moveToNext()) {
                String[] flds = Utils.splitFields(cur.getString(2));
                int ord = (Integer) mods.get(cur.getLong(1))[1];
                String strg = flds[ord];
                if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(strg).matches()) {
                    nids.add(cur.getLong(0));
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        if (nids.isEmpty()) {
            return "";
        }
        return "n.id in " + Utils.ids2str(nids);
    }


    // Find and Replace
    // /////////////////
    /**
     * Find and replace fields in a note
     * 
     * @param col The collection to search into.
     * @param nids The cards to be searched for.
     * @param src The original text to find.
     * @param dst The text to change to.
     * @param regex If true, the src is treated as a regex. Default = false.
     * @param field Limit the search to specific field. If null, it searches all fields.
     * @param fold If true the search is case-insensitive. Default = true.
     * @return
     */
    public static int findReplace(Collection col, List<Long> nids, String src, String dst) {
        return findReplace(col, nids, src, dst, false, null, true);
    }


    public static int findReplace(Collection col, List<Long> nids, String src, String dst, boolean regex) {
        return findReplace(col, nids, src, dst, regex, null, true);
    }


    public static int findReplace(Collection col, List<Long> nids, String src, String dst, String field) {
        return findReplace(col, nids, src, dst, false, field, true);
    }


    public static int findReplace(Collection col, List<Long> nids, String src, String dst, boolean isRegex,
            String field, boolean fold) {
        Map<Long, Integer> mmap = new HashMap<Long, Integer>();
        if (field != null) {
            try {
                for (JSONObject m : col.getModels().all()) {
                    JSONArray flds = m.getJSONArray("flds");
                    for (int fi = 0; fi < flds.length(); ++fi) {
                        JSONObject f = flds.getJSONObject(fi);
                        if (f.getString("name").equals(field)) {
                            mmap.put(m.getLong("id"), f.getInt("ord"));
                        }
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (mmap.isEmpty()) {
                return 0;
            }
        }
        // find and gather replacements
        if (!isRegex) {
            src = Pattern.quote(src);
        }
        if (fold) {
            src = "(?i)" + src;
        }
        Pattern regex = Pattern.compile(src);

        ArrayList<Object[]> d = new ArrayList<Object[]>();
        String sql = "select id, mid, flds from notes where id in " + Utils.ids2str(nids.toArray(new Long[] {}));
        nids = new ArrayList<Long>();

        Cursor cur = null;
        try {
            cur = col.getDb().getDatabase().rawQuery(sql, null);
            while (cur.moveToNext()) {
                String flds = cur.getString(2);
                String origFlds = flds;
                // does it match?
                String[] sflds = Utils.splitFields(flds);
                if (field != null) {
                    long mid = cur.getLong(1);
                    if (!mmap.containsKey(mid)) {
                        continue;
                    }
                    int ord = mmap.get(mid);
                    sflds[ord] = regex.matcher(sflds[ord]).replaceAll(dst);
                } else {
                    for (int i = 0; i < sflds.length; ++i) {
                        sflds[i] = regex.matcher(sflds[i]).replaceAll(dst);
                    }
                }
                flds = Utils.joinFields(sflds);
                if (!flds.equals(origFlds)) {
                    long nid = cur.getLong(0);
                    nids.add(nid);
                    d.add(new Object[] { flds, Utils.intNow(), col.usn(), nid });
                }

            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        if (d.isEmpty()) {
            return 0;
        }
        // replace
        col.getDb().executeMany("update notes set flds=?,mod=?,usn=? where id=?", d);
        long[] pnids = Utils.toPrimitive(nids);
        col.updateFieldCache(pnids);
        col.genCards(pnids);
        return d.size();
    }


    public List<String> fieldNames(Collection col, boolean downcase) {
        Set<String> fields = new HashSet<String>();
        List<String> names = new ArrayList<String>();
        try {
            for (JSONObject m : col.getModels().all()) {
                JSONArray flds = m.getJSONArray("flds");
                for (int fi = 0; fi < flds.length(); ++fi) {
                    JSONObject f = flds.getJSONObject(fi);
                    if (!fields.contains(f.getString("name").toLowerCase())) {
                        names.add(f.getString("name"));
                        fields.add(f.getString("name").toLowerCase());
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (downcase) {
            return new ArrayList<String>(fields);
        }
        return names;
    }


    // Find Duplicates
    // ////////////////

    public static List<Pair<String, List<Long>>> findDupes(Collection col, String fieldName) {
        return findDupes(col, fieldName, "");
    }


    public static List<Pair<String, List<Long>>> findDupes(Collection col, String fieldName, String search) {
        // limit search to notes with applicable field name
    	if (search != null && search.length() > 0) {
            search = "(" + search + ") ";
    	}
        search += "'" + fieldName + ":*'";
        // go through notes

        String sql = "select id, mid, flds from notes where id in "
                + Utils.ids2str(col.findNotes(search).toArray(new Long[] {}));
        Cursor cur = null;
        Map<Long, Integer> fields = new HashMap<Long, Integer>();
        Map<String, List<Long>> vals = new HashMap<String, List<Long>>();
        List<Pair<String, List<Long>>> dupes = new ArrayList<Pair<String, List<Long>>>();
        try {
            cur = col.getDb().getDatabase().rawQuery(sql, null);
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                long mid = cur.getLong(1);
                String[] flds = Utils.splitFields(cur.getString(2));
                // inlined ordForMid(mid)
                if (!fields.containsKey(mid)) {
                    JSONObject model = col.getModels().get(mid);
                    fields.put(mid, col.getModels().fieldMap(model).get(fieldName).first);
                }
                String val = flds[fields.get(mid)];
                // empty does not count as duplicate
                if (val.equals("")) {
                    continue;
                }
                if (!vals.containsKey(val)) {
                    vals.put(val, new ArrayList<Long>());
                }
                vals.get(val).add(nid);
                if (vals.get(val).size() == 2) {
                    dupes.add(new Pair<String, List<Long>>(val, vals.get(val)));
                }
            }
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return dupes;
    }
}
