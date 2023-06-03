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

package com.ichi2.libanki

import android.database.SQLException
import androidx.annotation.CheckResult
import com.ichi2.libanki.SortOrder.*
import com.ichi2.libanki.stats.Stats
import com.ichi2.libanki.utils.TimeManager.time
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.jsonObjectIterable
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONObject
import timber.log.Timber
import java.text.Normalizer
import java.util.*
import java.util.regex.Pattern

@RustCleanup("remove this once Java backend is gone")
class Finder(private val col: Collection) {
    /** Return a list of card ids for QUERY  */
    @CheckResult
    fun findCards(query: String, _order: SortOrder): List<Long> {
        return _findCards(query, _order)
    }

    @CheckResult
    private fun _findCards(
        query: String,
        _order: SortOrder
    ): List<Long> {
        val tokens = _tokenize(query)
        val res1 = _where(tokens)
        val preds = res1.first
        val args = res1.second
        val res: MutableList<Long> = ArrayList()
        if (preds == null) {
            return res
        }
        val res2 = _order(_order)
        val order = res2.first
        val rev = res2.second
        val sql = _query(preds, order)
        Timber.v("Search query '%s' is compiled as '%s'.", query, sql)
        try {
            col.db.database.query(sql, args ?: emptyArray()).use { cur ->
                while (cur.moveToNext()) {
                    res.add(cur.getLong(0))
                }
            }
        } catch (e: SQLException) {
            // invalid grouping
            Timber.w(e)
            return ArrayList(0)
        }
        if (rev) {
            Collections.reverse(res)
        }
        return res
    }

    /**
     *
     * @param query the query as in browser search langage.
     * @return card id (at most one by note) of cards satisfying the query.
     */
    @KotlinCleanup("Remove in V16.") // Not in libAnki
    fun findOneCardByNote(query: String): List<Long> {
        return findNotes(query, true)
    }

    /**
     *
     * @param query the query as in browser search langage.
     * @return note of notes satisfying the query.
     */
    fun findNotes(query: String): List<Long> {
        return findNotes(query, false)
    }

    /**
     *
     * @param query the query as in browser search langage.
     * @param returnCid if true, return a single cid of existing card by note. Otherwise return note id.
     * @return note or card id (at most one by note) of notes satisfying the query.
     */
    @KotlinCleanup("Remove 'returnCid' in V16.") // returnCid Not in libAnki
    fun findNotes(query: String, returnCid: Boolean): List<Long> {
        val tokens = _tokenize(query)
        val res1 = _where(tokens)
        val args = res1.second
        val res: MutableList<Long> = ArrayList()
        val preds = res1.first?.let { first ->
            if ("" == first) {
                "1"
            } else {
                "($first)"
            }
        } ?: return res
        val sql: String = if (returnCid) {
            "select min(c.id) from cards c, notes n where c.nid=n.id and $preds group by n.id"
        } else {
            "select distinct(n.id) from cards c, notes n where c.nid=n.id and $preds"
        }
        try {
            col.db.database.query(sql, args ?: emptyArray()).use { cur ->
                while (cur.moveToNext()) {
                    res.add(cur.getLong(0))
                }
            }
        } catch (e: SQLException) {
            Timber.w(e)
            // invalid grouping
            return ArrayList(0)
        }
        return res
    }

    /**
     * Tokenizing
     * ***********************************************************
     */
    fun _tokenize(query: String): Array<String> {
        var inQuote = 0.toChar()
        val tokens: MutableList<String> = ArrayList()
        var token = ""
        for (i in 0 until query.length) {
            // quoted text
            val c = query[i]
            if (c == '\'' || c == '"') {
                if (inQuote.code != 0) {
                    if (c == inQuote) {
                        inQuote = 0.toChar()
                    } else {
                        token += c
                    }
                } else if (token.length != 0) {
                    // quotes are allowed to start directly after a :
                    if (token.endsWith(":")) {
                        inQuote = c
                    } else {
                        token += c
                    }
                } else {
                    inQuote = c
                }
                // separator
            } else if (c == ' ') {
                if (inQuote.code != 0) {
                    token += c
                } else if (token.length != 0) {
                    // space marks token finished
                    tokens.add(token)
                    token = ""
                }
                // nesting
            } else if (c == '(' || c == ')') {
                if (inQuote.code != 0) {
                    token += c
                } else {
                    if (c == ')' && token.length != 0) {
                        tokens.add(token)
                        token = ""
                    }
                    tokens.add(c.toString())
                }
                // negation
            } else if (c == '-') {
                if (token.length != 0) {
                    token += c
                } else if (tokens.isEmpty() || "-" != tokens[tokens.size - 1]) {
                    tokens.add("-")
                }
                // normal character
            } else {
                token += c
            }
        }
        // if we finished in a token, add it
        if (token.length != 0) {
            tokens.add(token)
        }
        return tokens.toTypedArray()
    }
    /*
      Query building
      ***********************************************************
     */
    /**
     * LibAnki creates a dictionary and operates on it with an inner function inside _where().
     * AnkiDroid combines the two in this class instead.
     */
    class SearchState {
        var isnot = false
        var isor = false
        var join = false
        var q: String? = ""
        var bad = false
        fun add(txt: String?, wrap: Boolean = true) {
            // failed command?
            @Suppress("NAME_SHADOWING")
            var txt = txt
            if (txt.isNullOrEmpty()) {
                // if it was to be negated then we can just ignore it
                if (isnot) {
                    isnot = false
                } else {
                    bad = true
                }
                return
            } else if ("skip" == txt) {
                return
            }
            // do we need a conjunction?
            if (join) {
                if (isor) {
                    q += " or "
                    isor = false
                } else {
                    q += " and "
                }
            }
            if (isnot) {
                q += " not "
                isnot = false
            }
            if (wrap) {
                txt = "($txt)"
            }
            q += txt
            join = true
        }
    }

    private fun _where(tokens: Array<String>): Pair<String?, Array<String>?> {
        // state and query
        val s = SearchState()
        val args: MutableList<String> = ArrayList()
        for (token in tokens) {
            if (s.bad) {
                return Pair(null, null)
            }
            // special tokens
            if ("-" == token) {
                s.isnot = true
            } else if ("or".equals(token, ignoreCase = true)) {
                s.isor = true
            } else if ("(" == token) {
                s.add(token, false)
                s.join = false
            } else if (")" == token) {
                s.q += ")"
                // commands
            } else if (token.contains(":")) {
                val spl = token.split(":".toRegex(), 2).toTypedArray()
                val cmd = spl[0].lowercase()
                val `val` = spl[1]
                when (cmd) {
                    "added" -> s.add(_findAdded(`val`))
                    "card" -> s.add(_findTemplate(`val`))
                    "deck" -> s.add(_findDeck(`val`))
                    "flag" -> s.add(_findFlag(`val`))
                    "mid" -> s.add(_findMid(`val`))
                    "nid" -> s.add(_findNids(`val`))
                    "cid" -> s.add(_findCids(`val`))
                    "note" -> s.add(_findModel(`val`))
                    "prop" -> s.add(_findProp(`val`))
                    "rated" -> s.add(_findRated(`val`))
                    "tag" -> s.add(_findTag(`val`, args))
                    "dupe" -> s.add(_findDupes(`val`))
                    "is" -> s.add(_findCardState(`val`))
                    else -> s.add(_findField(cmd, `val`))
                }
                // normal text search
            } else {
                s.add(_findText(token, args))
            }
        }
        return if (s.bad) {
            Pair(null, null)
        } else {
            Pair(s.q, args.toTypedArray())
        }
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
    private fun _order(order: SortOrder): Pair<String, Boolean> {
        if (order is NoOrdering) {
            return Pair("", false)
        }
        if (order is AfterSqlOrderBy) {
            val query = order.customOrdering
            return if (query.isEmpty()) {
                _order(NoOrdering())
            } else {
                // custom order string provided
                Pair(" order by $query", false)
            }
        }
        if (order is UseCollectionOrdering) {
            // use deck default
            val type = col.get_config_string("sortType")
            var sort: String? = null
            if (type.startsWith("note")) {
                if (type.startsWith("noteCrt")) {
                    sort = "n.id, c.ord"
                } else if (type.startsWith("noteMod")) {
                    sort = "n.mod, c.ord"
                } else if (type.startsWith("noteFld")) {
                    sort = "n.sfld COLLATE NOCASE, c.ord"
                }
            } else if (type.startsWith("card")) {
                if (type.startsWith("cardMod")) {
                    sort = "c.mod"
                } else if (type.startsWith("cardReps")) {
                    sort = "c.reps"
                } else if (type.startsWith("cardDue")) {
                    sort = "c.type, c.due"
                } else if (type.startsWith("cardEase")) {
                    sort = "c.type == " + Consts.CARD_TYPE_NEW + ", c.factor"
                } else if (type.startsWith("cardLapses")) {
                    sort = "c.lapses"
                } else if (type.startsWith("cardIvl")) {
                    sort = "c.ivl"
                }
            }
            if (sort == null) {
                // deck has invalid sort order; revert to noteCrt
                sort = "n.id, c.ord"
            }
            val sortBackwards = col.get_config_boolean("sortBackwards")
            return Pair(" ORDER BY $sort", sortBackwards)
        }
        throw IllegalStateException("unhandled order type: $order")
    }

    /**
     * Commands
     * ***********************************************************
     */
    private fun _findTag(`val`: String, args: MutableList<String>): String {
        @Suppress("NAME_SHADOWING")
        var `val` = `val`
        if ("none" == `val`) {
            return "n.tags = \"\""
        }
        `val` = `val`.replace("*", "%")
        if (!`val`.startsWith("%")) {
            `val` = "% $`val`"
        }
        if (!`val`.endsWith("%") || `val`.endsWith("\\%")) {
            args.add("$`val` %")
        } else {
            args.add(`val`)
        }
        // match descendants
        if (`val`.endsWith("::")) {
            args.add("$`val`%")
        } else {
            args.add("$`val`::%")
        }
        return "((n.tags like ? escape '\\') or (n.tags like ? escape '\\'))"
    }

    private fun _findCardState(`val`: String): String? {
        val n: Int
        return if ("review" == `val` || "new" == `val` || "learn" == `val`) {
            n = if ("review" == `val`) {
                2
            } else if ("new" == `val`) {
                0
            } else {
                return "queue IN (1, " + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")"
            }
            "type = $n"
        } else if ("suspended" == `val`) {
            "c.queue = " + Consts.QUEUE_TYPE_SUSPENDED
        } else if ("buried" == `val`) {
            "c.queue in (" + Consts.QUEUE_TYPE_SIBLING_BURIED + ", " + Consts.QUEUE_TYPE_MANUALLY_BURIED + ")"
        } else if ("due" == `val`) {
            "(c.queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ") and c.due <= " + col.sched.today() +
                ") or (c.queue = " + Consts.QUEUE_TYPE_LRN + " and c.due <= " + col.sched.dayCutoff + ")"
        } else {
            null
        }
    }

    private fun _findFlag(`val`: String): String? {
        val flag: Int
        flag = when (`val`) {
            "0" -> 0
            "1" -> 1
            "2" -> 2
            "3" -> 3
            "4" -> 4
            "5" -> 5
            "6" -> 6
            "7" -> 7
            else -> return null
        }
        val mask = 7 // 2**3 -1 in Anki
        return "(c.flags & $mask) == $flag"
    }

    private fun _findRated(`val`: String): String? {
        // days(:optional_ease)
        val r = `val`.split(":".toRegex()).toTypedArray()
        var days: Int
        days = try {
            r[0].toInt()
        } catch (e: NumberFormatException) {
            Timber.w(e)
            return null
        }
        days = Math.min(days, 31)
        // ease
        var ease = ""
        if (r.size > 1) {
            if (!listOf("1", "2", "3", "4").contains(r[1])) {
                return null
            }
            ease = "and ease=" + r[1]
        }
        val cutoff = (col.sched.dayCutoff - Stats.SECONDS_PER_DAY * days) * 1000
        return "c.id in (select cid from revlog where id>$cutoff $ease)"
    }

    private fun _findAdded(`val`: String): String? {
        val days: Int
        days = try {
            `val`.toInt()
        } catch (e: NumberFormatException) {
            Timber.w(e)
            return null
        }
        val cutoff = (col.sched.dayCutoff - Stats.SECONDS_PER_DAY * days) * 1000
        return "c.id > $cutoff"
    }

    private fun _findProp(_val: String): String? {
        // extract
        val m = fPropPattern.matcher(_val)
        if (!m.matches()) {
            return null
        }
        var prop = m.group(1)!!.lowercase()
        val cmp = m.group(2)
        val sval = m.group(3)!!
        var `val`: Int
        // is val valid?
        `val` = try {
            if ("ease" == prop) {
                // LibAnki does this below, but we do it here to avoid keeping a separate float value.
                (sval.toDouble() * 1000).toInt()
            } else {
                sval.toInt()
            }
        } catch (e: NumberFormatException) {
            Timber.w(e)
            return null
        }
        // is prop valid?
        if (!listOf("due", "ivl", "reps", "lapses", "ease").contains(prop)) {
            return null
        }
        // query
        var q = ""
        if ("due" == prop) {
            `val` += col.sched.today()
            // only valid for review/daily learning
            q =
                "(c.queue in (" + Consts.QUEUE_TYPE_REV + "," + Consts.QUEUE_TYPE_DAY_LEARN_RELEARN + ")) and "
        } else if ("ease" == prop) {
            prop = "factor"
            // already done: val = int(val*1000)
        }
        q += "($prop $cmp $`val`)"
        return q
    }

    private fun _findText(`val`: String, args: MutableList<String>): String {
        @Suppress("NAME_SHADOWING")
        var `val` = `val`
        `val` = `val`.replace("*", "%")
        args.add("%$`val`%")
        args.add("%$`val`%")
        return "(n.sfld like ? escape '\\' or n.flds like ? escape '\\')"
    }

    private fun _findNids(`val`: String): String? {
        return if (fNidsPattern.matcher(`val`).find()) {
            null
        } else {
            "n.id in ($`val`)"
        }
    }

    private fun _findCids(`val`: String): String? {
        return if (fNidsPattern.matcher(`val`).find()) {
            null
        } else {
            "c.id in ($`val`)"
        }
    }

    private fun _findMid(`val`: String): String? {
        return if (fMidPattern.matcher(`val`).find()) {
            null
        } else {
            "n.mid = $`val`"
        }
    }

    private fun _findModel(`val`: String): String {
        val ids = LinkedList<Long>()
        for (m in col.models.all(col)) {
            var modelName = m.getString("name")
            modelName = Normalizer.normalize(modelName, Normalizer.Form.NFC)
            if (modelName.equals(`val`, ignoreCase = true)) {
                ids.add(m.getLong("id"))
            }
        }
        return "n.mid in " + Utils.ids2str(ids)
    }

    private fun dids(did: Long?): MutableList<Long>? {
        if (did == null) {
            return null
        }
        val children: kotlin.collections.Collection<Long> = col.decks.children(did).values
        val res: MutableList<Long> = ArrayList(children.size + 1)
        res.add(did)
        res.addAll(children)
        return res
    }

    fun _findDeck(`val`: String): String? {
        // if searching for all decks, skip
        @Suppress("NAME_SHADOWING")
        var `val` = `val`
        if ("*" == `val`) {
            return "skip"
            // deck types
        } else if ("filtered" == `val`) {
            return "c.odid"
        }
        var ids: MutableList<Long>?
        // current deck?
        if ("current".equals(`val`, ignoreCase = true)) {
            ids = dids(col.decks.selected())
        } else if (!`val`.contains("*")) {
            // single deck
            ids = dids(col.decks.id_for_name(`val`))
        } else {
            // wildcard
            ids = dids(col.decks.id_for_name(`val`))
            if (ids == null) {
                ids = ArrayList()
                `val` = `val`.replace("*", ".*")
                `val` = `val`.replace("+", "\\+")
                for (d in col.decks.all()) {
                    var deckName = d.getString("name")
                    deckName = Normalizer.normalize(deckName, Normalizer.Form.NFC)
                    if (deckName.matches("(?i)$`val`".toRegex())) {
                        for (id in dids(d.getLong("id"))!!) {
                            if (!ids.contains(id)) {
                                ids.add(id)
                            }
                        }
                    }
                }
            }
        }
        if (ids == null || ids.isEmpty()) {
            return null
        }
        val sids = Utils.ids2str(ids)
        return "c.did in $sids or c.odid in $sids"
    }

    private fun _findTemplate(`val`: String): String {
        // were we given an ordinal number?
        val num: Int? = try {
            `val`.toInt() - 1
        } catch (e: NumberFormatException) {
            Timber.w(e)
            null
        }
        if (num != null) {
            return "c.ord = $num"
        }
        // search for template names
        val lims: MutableList<String> = ArrayList()
        for (m in col.models.all(col)) {
            val tmpls = m.getJSONArray("tmpls")
            for (t in tmpls.jsonObjectIterable()) {
                val templateName = t.getString("name")
                Normalizer.normalize(templateName, Normalizer.Form.NFC)
                if (templateName.equals(`val`, ignoreCase = true)) {
                    if (m.isCloze) {
                        // if the user has asked for a cloze card, we want
                        // to give all ordinals, so we just limit to the
                        // model instead
                        lims.add("(n.mid = " + m.getLong("id") + ")")
                    } else {
                        lims.add(
                            "(n.mid = " + m.getLong("id") + " and c.ord = " +
                                t.getInt("ord") + ")"
                        )
                    }
                }
            }
        }
        return lims.toTypedArray().joinToString(" or ")
    }

    private fun _findField(field: String, `val`: String): String? {
        /*
         * We need two expressions to query the cards: One that will use JAVA REGEX syntax and another
         * that should use SQLITE LIKE clause syntax.
         */
        val sqlVal = `val`
            .replace("%", "\\%") // For SQLITE, we escape all % signs
            .replace("*", "%") // And then convert the * into non-escaped % signs

        /*
         * The following three lines make sure that only _ and * are valid wildcards.
         * Any other characters are enclosed inside the \Q \E markers, which force
         * all meta-characters in between them to lose their special meaning
         */
        val javaVal = `val`
            .replace("_", "\\E.\\Q")
            .replace("*", "\\E.*\\Q")
        /*
         * For the pattern, we use the javaVal expression that uses JAVA REGEX syntax
         */
        val pattern = Pattern.compile("\\Q$javaVal\\E", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

        // find models that have that field
        val mods: MutableMap<Long, Array<Any>> = HashMapInit(col.models.count(col))
        for (m in col.models.all(col)) {
            val flds = m.getJSONArray("flds")
            for (f in flds.jsonObjectIterable()) {
                var fieldName = f.getString("name")
                fieldName = Normalizer.normalize(fieldName, Normalizer.Form.NFC)
                if (fieldName.equals(field, ignoreCase = true)) {
                    mods[m.getLong("id")] = arrayOf(m, f.getInt("ord"))
                }
            }
        }
        if (mods.isEmpty()) {
            // nothing has that field
            return null
        }
        val nids = LinkedList<Long>()
        col.db.query(
            "select id, mid, flds from notes where mid in " +
                Utils.ids2str(LinkedList(mods.keys)) +
                " and flds like ? escape '\\'",
            "%$sqlVal%"
        ).use { cur ->
            /*
             * Here we use the sqlVal expression, that is required for LIKE syntax in sqllite.
             * There is no problem with special characters, because only % and _ are special
             * characters in this syntax.
             */
            while (cur.moveToNext()) {
                val flds = Utils.splitFields(cur.getString(2))
                val ord = mods[cur.getLong(1)]!![1] as Int
                val strg = flds[ord]
                if (pattern.matcher(strg).matches()) {
                    nids.add(cur.getLong(0))
                }
            }
        }
        return if (nids.isEmpty()) {
            "0"
        } else {
            "n.id in " + Utils.ids2str(nids)
        }
    }

    private fun _findDupes(`val`: String): String? {
        // caller must call stripHTMLMedia on passed val
        @Suppress("NAME_SHADOWING")
        var `val` = `val`
        val split = `val`.split(",".toRegex(), 1).toTypedArray()
        if (split.size != 2) {
            return null
        }
        val mid = split[0]
        `val` = split[1]
        val csum = java.lang.Long.toString(Utils.fieldChecksumWithoutHtmlMedia(`val`))
        val nids: MutableList<Long> = ArrayList()
        col.db.query(
            "select id, flds from notes where mid=? and csum=?",
            mid,
            csum
        ).use { cur ->
            val nid = cur.getLong(0)
            val flds = cur.getString(1)
            if (Utils.stripHTMLMedia(Utils.splitFields(flds)[0]) == `val`) {
                nids.add(nid)
            }
        }
        return "n.id in " + Utils.ids2str(nids)
    }

    companion object {
        private val fPropPattern = Pattern.compile("(^.+?)(<=|>=|!=|=|<|>)(.+?$)")
        private val fNidsPattern = Pattern.compile("[^0-9,]")
        private val fMidPattern = Pattern.compile("[^0-9]")

        /**
         * @param preds A sql predicate, or empty string, with c a card, n its note
         * @param order A part of a query, ordering element of table Card, with c a card, n its note
         * @return A query to return all card ids satifying the predicate and in the given order
         */
        private fun _query(preds: String, order: String): String {
            // can we skip the note table?
            var sql: String
            sql = if (!preds.contains("n.") && !order.contains("n.")) {
                "select c.id from cards c where "
            } else {
                "select c.id from cards c, notes n where c.nid=n.id and "
            }
            // combine with preds
            sql += if (preds.isNotEmpty()) {
                "($preds)"
            } else {
                "1"
            }
            // order
            if (order.isNotEmpty()) {
                sql += " $order"
            }
            return sql
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
        fun findReplace(
            col: Collection,
            nids: List<Long?>,
            src: String,
            dst: String,
            isRegex: Boolean = false,
            field: String? = null,
            fold: Boolean = true
        ): Int {
            @Suppress("NAME_SHADOWING")
            var src = src

            @Suppress("NAME_SHADOWING")
            var dst = dst
            val mmap: MutableMap<Long, Int> = HashMap()
            if (field != null) {
                for (m in col.models.all(col)) {
                    val flds = m.getJSONArray("flds")
                    for (f in flds.jsonObjectIterable()) {
                        if (f.getString("name").equals(field, ignoreCase = true)) {
                            mmap[m.getLong("id")] = f.getInt("ord")
                        }
                    }
                }
                if (mmap.isEmpty()) {
                    return 0
                }
            }
            // find and gather replacements
            if (!isRegex) {
                src = Pattern.quote(src)
                dst = dst.replace("\\", "\\\\")
            }
            if (fold) {
                src = "(?i)$src"
            }
            val regex = Pattern.compile(src)
            val d = ArrayList<Array<Any>>(nids.size)
            val snids = Utils.ids2str(nids)
            val midToNid: MutableMap<Long, MutableCollection<Long>> =
                HashMapInit(col.models.count(col))
            col.db.query(
                "select id, mid, flds from notes where id in $snids"
            ).use { cur ->
                while (cur.moveToNext()) {
                    val mid = cur.getLong(1)
                    var flds = cur.getString(2)
                    val origFlds = flds
                    // does it match?
                    val sflds = Utils.splitFields(flds)
                    if (field != null) {
                        if (!mmap.containsKey(mid)) {
                            // note doesn't have that field
                            continue
                        }
                        val ord = mmap[mid]!!
                        sflds[ord] = regex.matcher(sflds[ord]).replaceAll(dst)
                    } else {
                        for (i in sflds.indices) {
                            sflds[i] = regex.matcher(sflds[i]).replaceAll(dst)
                        }
                    }
                    flds = Utils.joinFields(sflds)
                    if (flds != origFlds) {
                        val nid = cur.getLong(0)
                        if (!midToNid.containsKey(mid)) {
                            midToNid[mid] = ArrayList()
                        }
                        midToNid[mid]!!.add(nid)
                        d.add(
                            arrayOf(
                                flds,
                                time.intTime(),
                                col.usn(),
                                nid
                            )
                        ) // order based on query below
                    }
                }
            }
            if (d.isEmpty()) {
                return 0
            }
            // replace
            col.db.executeMany("update notes set flds=?,mod=?,usn=? where id=?", d)
            for ((mid, nids_) in midToNid) {
                col.updateFieldCache(nids_)
                col.genCards(nids_, mid)
            }
            return d.size
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
        fun ordForMid(
            col: Collection,
            fields: MutableMap<Long?, Int?>,
            mid: Long,
            fieldName: String?
        ): Int? {
            if (!fields.containsKey(mid)) {
                val model: JSONObject? = col.models.get(col, mid)
                val flds = model!!.getJSONArray("flds")
                for (c in 0 until flds.length()) {
                    val f = flds.getJSONObject(c)
                    if (f.getString("name").equals(fieldName, ignoreCase = true)) {
                        fields[mid] = c
                        return c
                    }
                }
                fields[mid] = null
            }
            return fields[mid]
        }

        /**
         * @param col       the collection
         * @param fieldName a name of a field of some note type(s)
         * @param search A search query, as in the browser
         * @return List of Pair("dupestr", List[nids]), with nids note satisfying the search query, and having a field fieldName with value duepstr. Each list has at least two elements.
         */
        fun findDupes(
            col: Collection,
            fieldName: String?,
            search: String? = ""
        ): List<Pair<String, List<Long>>> {
            // limit search to notes with applicable field name
            @Suppress("NAME_SHADOWING")
            var search = search
            search = col.buildFindDupesString(fieldName!!, search!!)
            // go through notes
            val nids = col.findNotes(search)
            val vals: MutableMap<String, MutableList<Long>> = HashMapInit(nids.size)
            val dupes: MutableList<Pair<String, List<Long>>> = ArrayList(nids.size)
            val fields: MutableMap<Long?, Int?> = HashMap()
            col.db.query(
                "select id, mid, flds from notes where id in " + Utils.ids2str(col.findNotes(search))
            ).use { cur ->
                while (cur.moveToNext()) {
                    val nid = cur.getLong(0)
                    val mid = cur.getLong(1)
                    val flds = Utils.splitFields(cur.getString(2))
                    val ord = ordForMid(col, fields, mid, fieldName) ?: continue
                    var `val` = flds[ord]
                    `val` = Utils.stripHTMLMedia(`val`)
                    // empty does not count as duplicate
                    if (`val`.isEmpty()) {
                        continue
                    }
                    if (!vals.containsKey(`val`)) {
                        vals[`val`] = ArrayList()
                    }
                    vals[`val`]!!.add(nid)
                    if (vals[`val`]!!.size == 2) {
                        dupes.add(Pair(`val`, vals[`val`]!!))
                    }
                }
            }
            return dupes
        }
    }
}
