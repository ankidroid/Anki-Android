/****************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>                        *
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
import android.text.TextUtils;

import android.util.Pair;

import com.ichi2.async.CancelListener;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.ProgressSender;
import com.ichi2.utils.HashUtil;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import timber.log.Timber;

import static com.ichi2.async.CancelListener.isCancelled;
import static com.ichi2.async.ProgressSender.publishProgress;
import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

@SuppressWarnings({"PMD.ExcessiveClassLength", "PMD.AvoidThrowingRawExceptionTypes","PMD.AvoidReassigningParameters","PMD.NPathComplexity","PMD.MethodNamingConventions"})
@RustCleanup("remove this once Java backend is gone")
public class Finder {

    private static final Pattern fPropPattern = Pattern.compile("(^.+?)(<=|>=|!=|=|<|>)(.+?$)");
    private static final Pattern fNidsPattern = Pattern.compile("[^0-9,]");
    private static final Pattern fMidPattern = Pattern.compile("[^0-9]");

    private final Collection mCol;


    public Finder(Collection col) {
        mCol = col;
    }


    /** Return a list of card ids for QUERY */
    @CheckResult
    public List<Long> findCards(String query, SortOrder _order) {
        return findCards(query, _order, null);
    }

    @CheckResult
    public List<Long> findCards(String query, SortOrder _order, CollectionTask.PartialSearch task) {
        return _findCards(query, _order, task, task == null ? null : task.getProgressSender());
    }

    @CheckResult
    private List<Long> _findCards(String query, SortOrder _order, CancelListener cancellation, ProgressSender<Long> progress) {
        String[] tokens = _tokenize(query);
        Pair<String, String[]> res1 = _where(tokens);
        String preds = res1.first;
        String[] args = res1.second;
        List<Long> res = new ArrayList<>();
        if (preds == null) {
            return res;
        }
        Pair<String, Boolean> res2 = _order(_order);
        String order = res2.first;
        boolean rev = res2.second;
        String sql = _query(preds, order);
        Timber.v("Search query '%s' is compiled as '%s'.", query, sql);
        try (Cursor cur = mCol.getDb().getDatabase().query(sql, args)) {
            while (cur.moveToNext()) {
                if (isCancelled(cancellation)) {
                    return new ArrayList<>(0);
                }
                res.add(cur.getLong(0));
                publishProgress(progress, cur.getLong(0));
            }
        } catch (SQLException e) {
            // invalid grouping
            Timber.w(e);
            return new ArrayList<>(0);
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
        List<Long> res = new ArrayList<>();
        if (preds == null) {
            return res;
        }
        if ("".equals(preds)) {
            preds = "1";
        } else {
            preds = "(" + preds + ")";
        }
        String sql = "select distinct(n.id) from cards c, notes n where c.nid=n.id and " + preds;
        try (Cursor cur = mCol.getDb().getDatabase().query(sql, args)) {
            while (cur.moveToNext()) {
                res.add(cur.getLong(0));
            }
        } catch (SQLException e) {
            Timber.w(e);
            // invalid grouping
            return new ArrayList<>(0);
        }
        return res;
    }


    /**
     * Tokenizing
     * ***********************************************************
     */

    public String[] _tokenize(String query) {
        char inQuote = 0;
        List<String> tokens = new ArrayList<>();
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
                } else if (tokens.isEmpty() || !"-".equals(tokens.get(tokens.size() - 1))) {
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
        return tokens.toArray(new String[tokens.size()]);
    }


    /*
      Query building
      ***********************************************************
     */

    /**
     * LibAnki creates a dictionary and operates on it with an inner function inside _where().
     * AnkiDroid combines the two in this class instead.
     */
    public static class SearchState {
        public boolean isnot;
        public boolean isor;
        public boolean join;
        public String q = "";
        public boolean bad;
        
        public void add(String txt) {
            add(txt, true);
        }

        public void add(String txt, boolean wrap) {
            // failed command?
            if (TextUtils.isEmpty(txt)) {
                // if it was to be negated then we can just ignore it
                if (isnot) {
                    isnot = false;
                } else {
                    bad = true;
                }
                return;
            } else if ("skip".equals(txt)) {
                return;
            }
            // do we need a conjunction?
            if (join) {
                if (isor) {
                    q += " or ";
                    isor = false;
                } else {
                    q += " and ";
                }
            }
            if (isnot) {
                q += " not ";
                isnot = false;
            }
            if (wrap) {
                txt = "(" + txt + ")";
            }
            q += txt;
            join = true;
        }
    }


    private Pair<String, String[]> _where(String[] tokens) {
        // state and query
        SearchState s = new SearchState();
        List<String> args = new ArrayList<>();
        for (String token : tokens) {
            if (s.bad) {
                return new Pair<>(null, null);
            }
            // special tokens
            if ("-".equals(token)) {
                s.isnot = true;
            } else if ("or".equalsIgnoreCase(token)) {
                s.isor = true;
            } else if ("(".equals(token)) {
                s.add(token, false);
                s.join = false;
            } else if (")".equals(token)) {
                s.q += ")";
                // commands
            } else if (token.contains(":")) {
                String[] spl = token.split(":", 2);
                String cmd = spl[0].toLowerCase(Locale.ROOT);
                String val = spl[1];

                switch (cmd) {
                    case "added":
                        s.add(_findAdded(val));
                        break;
                    case "card":
                        s.add(_findTemplate(val));
                        break;
                    case "deck":
                        s.add(_findDeck(val));
                        break;
                    case "flag":
                        s.add(_findFlag(val));
                        break;
                    case "mid":
                        s.add(_findMid(val));
                        break;
                    case "nid":
                        s.add(_findNids(val));
                        break;
                    case "cid":
                        s.add(_findCids(val));
                        break;
                    case "note":
                        s.add(_findModel(val));
                        break;
                    case "prop":
                        s.add(_findProp(val));
                        break;
                    case "rated":
                        s.add(_findRated(val));
                        break;
                    case "tag":
                        s.add(_findTag(val, args));
                        break;
                    case "dupe":
                        s.add(_findDupes(val));
                        break;
                    case "is":
                        s.add(_findCardState(val));
                        break;
                    default:
                        s.add(_findField(cmd, val));
                        break;
                }
            // normal text search
            } else {
                s.add(_findText(token, args));
            }
        }
        if (s.bad) {
            return new Pair<>(null, null);
        }
        return new Pair<>(s.q, args.toArray(new String[args.size()]));
    }


    /**
     * @param preds A sql predicate, or empty string, with c a card, n its note
     * @param order A part of a query, ordering element of table Card, with c a card, n its note
     * @return A query to return all card ids satifying the predicate and in the given order
     */
    private static String _query(String preds, String order) {
        // can we skip the note table?
        String sql;
        if (!preds.contains("n.") && !order.contains("n.")) {
            sql = "select c.id from cards c where ";
        } else {
            sql = "select c.id from cards c, notes n where c.nid=n.id and ";
        }
        // combine with preds
        if (!TextUtils.isEmpty(preds)) {
            sql += "(" + preds + ")";
        } else {
            sql += "1";
        }
        // order
        if (!TextUtils.isEmpty(order)) {
            sql += " " + order;
        }
        return sql;
    }


    /**
     * Ordering
     * ***********************************************************
     */

    /*
     * NOTE: In the python code, _order() follows a code path based on:
     * - Empty order string (no order)
     * - order = False (no order)
     * - Non-empty order string (custom order)
     * - order = True (built-in order)
     * The python code combines all code paths in one function. In Java, we must overload the method
     * in order to consume either a String (no order, custom order) or a Boolean (no order, built-in order).
     */

    @NonNull
    private Pair<String, Boolean> _order(SortOrder order) {

        if (order instanceof SortOrder.NoOrdering) {
            return new Pair<>("", false);
        }
        if (order instanceof SortOrder.AfterSqlOrderBy) {
            String query = ((SortOrder.AfterSqlOrderBy) order).getCustomOrdering();
            if (TextUtils.isEmpty(query)) {
                return _order(new SortOrder.NoOrdering());
            } else {
                // custom order string provided
                return new Pair<>(" order by " + query, false);
            }
        }
        if (order instanceof SortOrder.UseCollectionOrdering) {
            // use deck default
            String type = mCol.get_config_string("sortType");
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
                    sort = "c.type == " + Consts.CARD_TYPE_NEW + ", c.factor";
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
            boolean sortBackwards = mCol.get_config_boolean("sortBackwards");
            return new Pair<>(" ORDER BY " + sort, sortBackwards);
        }
        throw new IllegalStateException("unhandled order type: " + order);
    }


    /**
     * Commands
     * ***********************************************************
     */

    private String _findTag(String val, List<String> args) {
        if ("none".equals(val)) {
            return "n.tags = \"\"";
        }
        val = val.replace("*", "%");
        if (!val.startsWith("%")) {
            val = "% " + val;
        }
        if (!val.endsWith("%") || val.endsWith("\\%")) {
            val += " %";
        }
        args.add(val);
        return "n.tags like ? escape '\\'";
    }


    private String _findCardState(String val) {
        int n;
        if ("review".equals(val) || "new".equals(val) || "learn".equals(val)) {
            if ("review".equals(val)) {
                n = 2;
            } else if ("new".equals(val)) {
                n = 0;
            } else {
                return "queue IN (1, " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")";
            }
            return "type = " + n;
        } else if ("suspended".equals(val)) {
            return "c.queue = " + Consts.QUEUE_TYPE_SUSPENDED;
        } else if ("buried".equals(val)) {
            return "c.queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ")";
        } else if ("due".equals(val)) {
            return "(c.queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and c.due <= " + mCol.getSched().getToday() +
                    ") or (c.queue = " + Consts.QUEUE_TYPE_LRN + " and c.due <= " + mCol.getSched().getDayCutoff() + ")";
        } else {
            return null;
        }
    }

    private String _findFlag(String val) {
        int flag;
        switch (val) {
        case "0":
            flag = 0;
            break;
        case "1":
            flag = 1;
            break;
        case "2":
            flag = 2;
            break;
        case "3":
            flag = 3;
            break;
        case "4":
            flag = 4;
            break;
        case "5":
            flag = 5;
            break;
        case "6":
            flag = 6;
            break;
        case "7":
            flag = 7;
            break;
        default:
            return null;
        }
        int mask = 0b111; // 2**3 -1 in Anki
        return "(c.flags & "+mask+") == " + flag;
    }

    private String _findRated(String val) {
        // days(:optional_ease)
        String[] r = val.split(":");
        int days;
        try {
            days = Integer.parseInt(r[0]);
        } catch (NumberFormatException e) {
            Timber.w(e);
            return null;
        }
        days = Math.min(days, 31);
        // ease
        String ease = "";
        if (r.length > 1) {
            if (!Arrays.asList("1", "2", "3", "4").contains(r[1])) {
                return null;
            }
            ease = "and ease=" + r[1];
        }
        long cutoff = (mCol.getSched().getDayCutoff() - SECONDS_PER_DAY * days) * 1000;
        return "c.id in (select cid from revlog where id>" + cutoff + " " + ease + ")";
    }


    private String _findAdded(String val) {
        int days;
        try {
            days = Integer.parseInt(val);
        } catch (NumberFormatException e) {
            Timber.w(e);
            return null;
        }
        long cutoff = (mCol.getSched().getDayCutoff() - SECONDS_PER_DAY * days) * 1000;
        return "c.id > " + cutoff;
    }


    private String _findProp(String _val) {
        // extract
        Matcher m = fPropPattern.matcher(_val);
        if (!m.matches()) {
            return null;
        }
        String prop = m.group(1).toLowerCase(Locale.ROOT);
        String cmp = m.group(2);
        String sval = m.group(3);
        int val;
        // is val valid?
        try {
            if ("ease".equals(prop)) {
                // LibAnki does this below, but we do it here to avoid keeping a separate float value.
                val = (int)(Double.parseDouble(sval) * 1000);
            } else {
                val = Integer.parseInt(sval);
            }
        } catch (NumberFormatException e) {
            Timber.w(e);
            return null;
        }
        // is prop valid?
        if (!Arrays.asList("due", "ivl", "reps", "lapses", "ease").contains(prop)) {
            return null;
        }
        // query
        String q = "";
        if ("due".equals(prop)) {
            val += mCol.getSched().getToday();
            // only valid for review/daily learning
            q = "(c.queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")) and ";
        } else if ("ease".equals(prop)) {
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
            return null;
        }
        return "n.id in (" + val + ")";
    }


    private String _findCids(String val) {
        if (fNidsPattern.matcher(val).find()) {
            return null;
        }
        return "c.id in (" + val + ")";
    }


    private String _findMid(String val) {
        if (fMidPattern.matcher(val).find()) {
            return null;
        }
        return "n.mid = " + val;
    }


    private String _findModel(String val) {
        LinkedList<Long> ids = new LinkedList<>();
        for (JSONObject m : mCol.getModels().all()) {
            String modelName = m.getString("name");
            modelName = Normalizer.normalize(modelName, Normalizer.Form.NFC);
            if (modelName.equalsIgnoreCase(val)) {
                ids.add(m.getLong("id"));
            }
        }
        return "n.mid in " + Utils.ids2str(ids);
    }


    private List<Long> dids(Long did) {
        if (did == null) {
            return null;
        }
        java.util.Collection<Long> children = mCol.getDecks().children(did).values();
        List<Long> res = new ArrayList<>(children.size() + 1);
        res.add(did);
        res.addAll(children);
        return res;
    }


    public String _findDeck(String val) {
        // if searching for all decks, skip
        if ("*".equals(val)) {
            return "skip";
            // deck types
        } else if ("filtered".equals(val)) {
            return "c.odid";
        }
        List<Long> ids = null;
        // current deck?
        if ("current".equalsIgnoreCase(val)) {
            ids = dids(mCol.getDecks().selected());
        } else if (!val.contains("*")) {
            // single deck
            ids = dids(mCol.getDecks().id_for_name(val));
        } else {
            // wildcard
            ids = dids(mCol.getDecks().id_for_name(val));
            if (ids == null) {
                ids = new ArrayList<>();
                val = val.replace("*", ".*");
                val = val.replace("+", "\\+");

                for (Deck d : mCol.getDecks().all()) {
                    String deckName = d.getString("name");
                    deckName = Normalizer.normalize(deckName, Normalizer.Form.NFC);
                    if (deckName.matches("(?i)" + val)) {
                        for (long id : dids(d.getLong("id"))) {
                            if (!ids.contains(id)) {
                                ids.add(id);
                            }
                        }
                    }
                }
            }
        }
        if (ids == null || ids.isEmpty()) {
            return null;
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
            Timber.w(e);
            num = null;
        }
        if (num != null) {
            return "c.ord = " + num;
        }
        // search for template names
        List<String> lims = new ArrayList<>();
        for (Model m : mCol.getModels().all()) {
            JSONArray tmpls = m.getJSONArray("tmpls");
            for (JSONObject t: tmpls.jsonObjectIterable()) {
                String templateName = t.getString("name");
                Normalizer.normalize(templateName, Normalizer.Form.NFC);
                if (templateName.equalsIgnoreCase(val)) {
                    if (m.isCloze()) {
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
        return TextUtils.join(" or ", lims.toArray(new String[lims.size()]));
    }


    private String _findField(String field, String val) {
        /*
         * We need two expressions to query the cards: One that will use JAVA REGEX syntax and another
         * that should use SQLITE LIKE clause syntax.
         */
        String sqlVal = val
                .replace("%","\\%") // For SQLITE, we escape all % signs
                .replace("*","%"); // And then convert the * into non-escaped % signs

        /*
         * The following three lines make sure that only _ and * are valid wildcards.
         * Any other characters are enclosed inside the \Q \E markers, which force
         * all meta-characters in between them to lose their special meaning
         */
        String javaVal = val
                    .replace("_","\\E.\\Q")
                    .replace("*","\\E.*\\Q");
        /*
         * For the pattern, we use the javaVal expression that uses JAVA REGEX syntax
         */
        Pattern pattern = Pattern.compile("\\Q" + javaVal + "\\E", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // find models that have that field
        Map<Long, Object[]> mods = HashUtil.HashMapInit(mCol.getModels().count());
        for (JSONObject m : mCol.getModels().all()) {
            JSONArray flds = m.getJSONArray("flds");
            for (JSONObject f: flds.jsonObjectIterable()) {
                String fieldName = f.getString("name");
                fieldName = Normalizer.normalize(fieldName, Normalizer.Form.NFC);
                if (fieldName.equalsIgnoreCase(field)) {
                    mods.put(m.getLong("id"), new Object[] { m, f.getInt("ord") });
                }
            }
        }
        if (mods.isEmpty()) {
            // nothing has that field
            return null;
        }
        LinkedList<Long> nids = new LinkedList<>();
        try (Cursor cur = mCol.getDb().query(
                "select id, mid, flds from notes where mid in " +
                        Utils.ids2str(new LinkedList<>(mods.keySet())) +
                        " and flds like ? escape '\\'",  "%" + sqlVal + "%")) {
            /*
             * Here we use the sqlVal expression, that is required for LIKE syntax in sqllite.
             * There is no problem with special characters, because only % and _ are special
             * characters in this syntax.
             */

            while (cur.moveToNext()) {
                String[] flds = Utils.splitFields(cur.getString(2));
                int ord = (Integer)mods.get(cur.getLong(1))[1];
                String strg = flds[ord];
                if (pattern.matcher(strg).matches()) {
                    nids.add(cur.getLong(0));
                }
            }
        }
        if (nids.isEmpty()) {
            return "0";
        }
        return "n.id in " + Utils.ids2str(nids);
    }


    private String _findDupes(String val) {
        // caller must call stripHTMLMedia on passed val
        String[] split = val.split(",", 1);
        if (split.length != 2) {
            return null;
        }
        String mid = split[0];
        val = split[1];
        String csum = Long.toString(Utils.fieldChecksumWithoutHtmlMedia(val));
        List<Long> nids = new ArrayList<>();
        try (Cursor cur = mCol.getDb().query(
                "select id, flds from notes where mid=? and csum=?",
                mid, csum)) {
            long nid = cur.getLong(0);
            String flds = cur.getString(1);
            if (Utils.stripHTMLMedia(Utils.splitFields(flds)[0]).equals(val)) {
                nids.add(nid);
            }
        }
        return "n.id in " +  Utils.ids2str(nids);
    }


    /*
      Find and replace
      ***********************************************************
     */

    /**
     * Find and replace fields in a note
     *
     * @param col The collection to search into.
     * @param nids The cards to be searched for.
     * @param src The original text to find.
     * @param dst The text to change to.
     * @return Number of notes with fields that were updated.
     */
    public static int findReplace(Collection col, List<Long> nids, String src, String dst) {
        return findReplace(col, nids, src, dst, false, null, true);
    }

    /**
     * Find and replace fields in a note
     *
     * @param col The collection to search into.
     * @param nids The cards to be searched for.
     * @param src The original text to find.
     * @param dst The text to change to.
     * @param regex If true, the src is treated as a regex. Default = false.
     * @return Number of notes with fields that were updated.
     */
    public static int findReplace(Collection col, List<Long> nids, String src, String dst, boolean regex) {
        return findReplace(col, nids, src, dst, regex, null, true);
    }

    /**
     * Find and replace fields in a note
     *
     * @param col The collection to search into.
     * @param nids The cards to be searched for.
     * @param src The original text to find.
     * @param dst The text to change to.
     * @param field Limit the search to specific field. If null, it searches all fields.
     * @return Number of notes with fields that were updated.
     */
    public static int findReplace(Collection col, List<Long> nids, String src, String dst, String field) {
        return findReplace(col, nids, src, dst, false, field, true);
    }

    /**
     * Find and replace fields in a note
     *
     * @param col The collection to search into.
     * @param nids The cards to be searched for.
     * @param src The original text to find.
     * @param dst The text to change to.
     * @param isRegex If true, the src is treated as a regex. Default = false.
     * @param field Limit the search to specific field. If null, it searches all fields.
     * @param fold If true the search is case-insensitive. Default = true.
     * @return Number of notes with fields that were updated. */
    public static int findReplace(Collection col, List<Long> nids, String src, String dst, boolean isRegex,
            String field, boolean fold) {
        Map<Long, Integer> mmap = new HashMap<>();
        if (field != null) {
            for (JSONObject m : col.getModels().all()) {
                JSONArray flds = m.getJSONArray("flds");
                for (JSONObject f: flds.jsonObjectIterable()) {
                    if (f.getString("name").equalsIgnoreCase(field)) {
                        mmap.put(m.getLong("id"), f.getInt("ord"));
                    }
                }
            }
            if (mmap.isEmpty()) {
                return 0;
            }
        }
        // find and gather replacements
        if (!isRegex) {
            src = Pattern.quote(src);
            dst = dst.replace("\\", "\\\\");
        }
        if (fold) {
            src = "(?i)" + src;
        }
        Pattern regex = Pattern.compile(src);

        ArrayList<Object[]> d = new ArrayList<>(nids.size());
        String snids = Utils.ids2str(nids);
        Map<Long, java.util.Collection<Long>> midToNid = HashUtil.HashMapInit(col.getModels().count());
        try (Cursor cur = col.getDb().query(
                "select id, mid, flds from notes where id in " + snids)) {
            while (cur.moveToNext()) {
                long mid = cur.getLong(1);
                String flds = cur.getString(2);
                String origFlds = flds;
                // does it match?
                String[] sflds = Utils.splitFields(flds);
                if (field != null) {
                    if (!mmap.containsKey(mid)) {
                        // note doesn't have that field
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
                    if (!midToNid.containsKey(mid)) {
                        midToNid.put(mid, new ArrayList<>());
                    }
                    midToNid.get(mid).add(nid);
                    d.add(new Object[] { flds, col.getTime().intTime(), col.usn(), nid }); // order based on query below
                }
            }
        }
        if (d.isEmpty()) {
            return 0;
        }
        // replace
        col.getDb().executeMany("update notes set flds=?,mod=?,usn=? where id=?", d);
        for (Map.Entry<Long, java.util.Collection<Long>> entry : midToNid.entrySet()) {
            long mid = entry.getKey();
            java.util.Collection<Long> nids_ = entry.getValue();
            col.updateFieldCache(nids_);
            col.genCards(nids_, mid);
        }
        return d.size();
    }


    /**
     * Find duplicates
     * ***********************************************************
     * @param col  The collection
     * @param fields A map from some note type id to the ord of the field fieldName
     * @param mid a note type id
     * @param fieldName A name, assumed to be the name of a field of some note type
     * @return The ord of the field fieldName in the note type whose id is mid. null if there is no such field. Save the information in fields
     */

    public static Integer ordForMid(Collection col, Map<Long, Integer> fields, long mid, String fieldName) {
        if (!fields.containsKey(mid)) {
            JSONObject model = col.getModels().get(mid);
            JSONArray flds = model.getJSONArray("flds");
            for (int c = 0; c < flds.length(); c++) {
                JSONObject f = flds.getJSONObject(c);
                if (f.getString("name").equalsIgnoreCase(fieldName)) {
                    fields.put(mid, c);
                    return c;
                }
            }
            fields.put(mid, null);
        }
        return fields.get(mid);
    }


    public static List<Pair<String, List<Long>>> findDupes(Collection col, String fieldName) {
        return findDupes(col, fieldName, "");
    }


    /**
     * @param col       the collection
     * @param fieldName a name of a field of some note type(s)
     * @param search A search query, as in the browser
     * @return List of Pair("dupestr", List[nids]), with nids note satisfying the search query, and having a field fieldName with value duepstr. Each list has at least two elements.
     */
    public static List<Pair<String, List<Long>>> findDupes(Collection col, String fieldName, String search) {
        // limit search to notes with applicable field name
    	if (!TextUtils.isEmpty(search)) {
            search = "(" + search + ") ";
    	}
        search += "'" + fieldName + ":*'";
        // go through notes
        List<Long> nids = col.findNotes(search);
        Map<String, List<Long>> vals = HashUtil.HashMapInit(nids.size());
        List<Pair<String, List<Long>>> dupes = new ArrayList<>(nids.size());
        Map<Long, Integer> fields = new HashMap<>();
        try (Cursor cur = col.getDb().query(
                "select id, mid, flds from notes where id in " + Utils.ids2str(col.findNotes(search)))) {
            while (cur.moveToNext()) {
                long nid = cur.getLong(0);
                long mid = cur.getLong(1);
                String[] flds = Utils.splitFields(cur.getString(2));
                Integer ord = ordForMid(col, fields, mid, fieldName);
                if (ord == null) {
                    continue;
                }
                String val = flds[ord];
                val = Utils.stripHTMLMedia(val);
                // empty does not count as duplicate
                if (TextUtils.isEmpty(val)) {
                    continue;
                }
                if (!vals.containsKey(val)) {
                    vals.put(val, new ArrayList<>());
                }
                vals.get(val).add(nid);
                if (vals.get(val).size() == 2) {
                    dupes.add(new Pair<>(val, vals.get(val)));
                }
            }
        }
        return dupes;
    }
}
