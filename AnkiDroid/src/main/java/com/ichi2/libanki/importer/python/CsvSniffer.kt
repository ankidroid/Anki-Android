/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.

 This file incorporates work covered by the following copyright and permission notice.
 Please see the file LICENSE in this directory for full details

 Ported from https://github.com/python/cpython/blob/a74eea238f5baba15797e2e8b570d153bc8690a7/Lib/csv.py#L159

 */
package com.ichi2.libanki.importer.python

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.ichi2.libanki.importer.CsvException
import com.ichi2.utils.HashUtil.HashMapInit
import com.ichi2.utils.KotlinCleanup
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

@SuppressLint("NonPublicNonStaticFieldName")
@RequiresApi(Build.VERSION_CODES.O) // Regex group(str)
@KotlinCleanup("fix IDE lint issues")
class CsvSniffer {
    private val preferred: CharArray

    init {
        // in case there is more than one possible delimiter
        preferred = charArrayOf(',', '\t', ';', ' ', ':')
    }

    fun sniff(sample: String, delimiters: CharArray?): CsvDialect {
        val delimiterList = toList(delimiters)
        val result = _guess_quote_and_delimiter(sample, delimiterList)
        val quotechar = result.quotechar
        val doublequote = result.doublequote
        var delimiter = result.delimiter
        var skipinitialspace = result.skipinitialspace
        if (delimiter == '\u0000') {
            val g = _guess_delimiter(sample, delimiterList)
            delimiter = g.delimiter
            skipinitialspace = g.skipinitialspace
        }
        if (delimiter == '\u0000') {
            throw CsvException("Could not determine delimiter")
        }
        @KotlinCleanup("use a scope function")
        val dialect = CsvDialect("sniffed")
        dialect.mDoublequote = doublequote
        dialect.mDelimiter = delimiter
        // _csv.reader won't accept a quotechar of ''
        dialect.mQuotechar = if (quotechar == '\u0000') '"' else quotechar
        dialect.mSkipInitialSpace = skipinitialspace
        return dialect
    }

    @KotlinCleanup("could be further simplified: return if/else, use delimiters.toList()")
    private fun toList(delimiters: CharArray?): List<Char> {
        if (delimiters == null) {
            return ArrayList(0)
        }
        val ret = ArrayList<Char>(delimiters.size)
        for (delimiter in delimiters) {
            ret.add(delimiter)
        }
        return ret
    }

    /**
     * Looks for text enclosed between two identical quotes
     * (the probable quotechar) which are preceded and followed
     * by the same character (the probable delimiter).
     * For example:
     * ,'some text',
     * The quote with the most wins, same with the delimiter.
     * If there is no quotechar the delimiter can't be determined
     * this way.
     */
    private fun _guess_quote_and_delimiter(data: String, delimiters: List<Char>?): GuessQuoteAndDelimiter {
        val regexes = ArrayList<String>(4)
        regexes.add("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"']).*?\\k<quote>\\k<delim>") // ,".*?",
        regexes.add("(?:^|\\n)(?<quote>[\"']).*?\\k<quote>(?<delim>[^\\w\\n\"'])(?<space> ?)") //  ".*?",
        regexes.add("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"']).*?\\k<quote>(?:$|\\n)") // ,".*?"
        regexes.add("(?:^|\\n)(?<quote>[\"']).*?\\k<quote>(?:$|\\n)") //  ".*?" (no delim, no space)
        val matches: MutableList<Group> = ArrayList()
        for (regex in regexes) {
            val p = Pattern.compile(regex, Pattern.MULTILINE or Pattern.DOTALL)
            val m = p.matcher(data)
            while (m.find()) {
                val g = Group()
                g.delim = getCharOrNull(m, "delim")
                g.quote = getCharOrNull(m, "quote")
                g.space = m.group("space")
                matches.add(g)
            }
            if (!matches.isEmpty()) {
                break
            }
        }
        if (matches.isEmpty()) {
            return GuessQuoteAndDelimiter('\u0000', false, '\u0000', false)
        }
        val quotes: MutableMap<Char, Int> = HashMapInit(matches.size)
        val delims: MutableMap<Char, Int> = HashMap()
        var spaces = 0
        for (m in matches) {
            var key = m.quote
            if (key != '\u0000') {
                quotes[key] = quotes.getOrDefault(key, 0) + 1
            }
            key = m.delim
            if (key != '\u0000' && (delimiters == null || delimiters.isEmpty() || delimiters.contains(key))) {
                delims[key] = delims.getOrDefault(key, 0) + 1
            }
            if (m.space != null && m.space!!.length > 0) {
                spaces += 1
            }
        }
        val quotechar = max(quotes)!!
        var delim: Char
        val skipinitialspace: Boolean
        if (!delims.isEmpty()) {
            delim = max(delims)!!
            skipinitialspace = delims[delim] == spaces
            if (delim == '\n') { // most likely a file with a single column
                delim = '\u0000'
            }
        } else {
            // there is *no* delimiter, it's a single column of quoted data
            delim = '\u0000'
            skipinitialspace = false
        }

        // if we see an extra quote between delimiters, we've got a
        // double quoted format
        val regex = String.format(
            "((%s)|^)\\W*%s[^%s\\n]*%s[^%s\\n]*%s\\W*((%s)|$)",
            delim,
            quotechar,
            delim,
            quotechar,
            delim,
            quotechar,
            delim
        )
        val dq_regexp = Pattern.compile(regex, Pattern.MULTILINE)
        val doublequote = dq_regexp.matcher(data).find()
        return GuessQuoteAndDelimiter(quotechar, doublequote, delim, skipinitialspace)
    }

    @KotlinCleanup("method name?! the method can't return null")
    private fun getCharOrNull(m: Matcher, delim: String): Char {
        val group = m.group(delim)
        return if (group == null || group.length == 0) {
            '\u0000'
        } else group[0]
    }

    /**
     * The delimiter /should/ occur the same number of times on
     * each row. However, due to malformed data, it may not. We don't want
     * an all or nothing approach, so we allow for small variations in this
     * number.
     * 1) build a table of the frequency of each character on every line.
     * 2) build a table of frequencies of this frequency (meta-frequency?),
     * e.g.  'x occurred 5 times in 10 rows, 6 times in 1000 rows,
     * 7 times in 2 rows'
     * 3) use the mode of the meta-frequency to determine the /expected/
     * frequency for that character
     * 4) find out how often the character actually meets that goal
     * 5) the character that best meets its goal is the delimiter
     * For performance reasons, the data is evaluated in chunks, so it can
     * try and evaluate the smallest portion of the data possible, evaluating
     * additional chunks as necessary.
     */
    private fun _guess_delimiter(input: String, delimiters: List<Char>?): Guess {

        // remove falsey values
        val samples = input.split("\n").toTypedArray()
        val data: MutableList<String> = ArrayList(samples.size)
        for (s in samples) {
            if (s.length == 0) {
                continue
            }
            data.add(s)
        }
        val ascii = CharArray(128) // 7-bit ASCII
        for (i in 0..127) {
            ascii[i] = i.toChar()
        }

        // build frequency tables
        val chunkLength = Math.min(10, data.size)
        var iteration = 0
        val charFrequency: MutableMap<Char, MutableMap<Int, Int>> = HashMap()
        val modes: MutableMap<Char, Tuple> = HashMap()
        val delims: MutableMap<Char, Tuple> = HashMap()
        var start = 0
        var end = chunkLength
        while (start < data.size) {
            iteration++
            for (line in data.subList(start, end)) {
                for (c in ascii) {
                    val metaFrequency = charFrequency.getOrDefault(c, HashMap())
                    // must count even if frequency is 0
                    val freq = countInString(line, c)
                    // value is the mode
                    metaFrequency[freq] = metaFrequency.getOrDefault(freq, 0) + 1
                    charFrequency[c] = metaFrequency
                }
            }
            for ((c, value) in charFrequency) {
                val bareList = value.entries
                val items: MutableList<Tuple> = ArrayList(bareList.size)
                for (entry in bareList) {
                    items.add(Tuple(entry))
                }
                if (items.size == 1 && items[0].second == 0) {
                    continue
                }

                // get the mode of the frequencies
                if (items.size > 1) {
                    val toRemove = maxSecond(items)
                    // adjust the mode - subtract the sum of all
                    // other frequencies
                    items.remove(toRemove)
                    modes[c] = Tuple(toRemove!!.first, toRemove.second - sumSecond(items))
                } else {
                    modes[c] = items[0]
                }
            }

            // build a list of possible delimiters
            val modeList: Set<Map.Entry<Char, Tuple>> = modes.entries
            val total = Math.min(chunkLength * iteration, data.size).toFloat()
            // (rows of consistent data) / (number of rows) = 100%
            var consistency = 1.0
            // minimum consistency threshold
            val threshold = 0.9
            while (delims.isEmpty() && consistency >= threshold) {
                for ((key, value) in modeList) {
                    if (value.first > 0 && value.second > 0) {
                        if (value.second.toDouble() / total >= consistency && (delimiters == null || delimiters.contains(key))) {
                            delims[key] = value
                        }
                    }
                }
                consistency -= 0.01
            }
            if (delims.size == 1) {
                val delim = ArrayList(delims.keys)[0]
                val skipinitialspace = countInString(data[0], delim) == countInString(
                    data[0], "$delim "
                )
                return Guess(delim, skipinitialspace)
            }
            // analyze another chunkLength lines
            start = end
            end += chunkLength
        }
        if (delims.isEmpty()) {
            return Guess('\u0000', false)
        }

        // if there's more than one, fall back to a 'preferred' list
        if (delims.size > 1) {
            for (d in preferred) {
                if (delims.containsKey(d)) {
                    val skipinitialspace = countInString(data[0], d) == countInString(
                        data[0], "$d "
                    )
                    return Guess(d, skipinitialspace)
                }
            }
        }

        // nothing else indicates a preference, pick the character that
        // dominates(?)
        val items = ArrayList<Map.Entry<Tuple, Char>>(delims.size)
        for ((key, value) in delims) {
            items.add(AbstractMap.SimpleEntry(value, key))
        }
        items.sortWith(
            kotlin.Comparator { o1: Map.Entry<Tuple, Char>, o2: Map.Entry<Tuple, Char> ->
                val compare = o1.key.first.compareTo(o2.key.first)
                if (compare != 0) {
                    compare
                } else {
                    o1.key.second.compareTo(o2.key.second)
                }
            }
        )
        val delim = items[items.size - 1].value
        val skipinitialspace = countInString(data[0], delim) == countInString(
            data[0], "$delim "
        )
        return Guess(delim, skipinitialspace)
    }

    private fun sumSecond(items: List<Tuple?>): Int {
        var total = 0
        for (item in items) {
            total += item!!.second
        }
        return total
    }

    private fun <T> max(histogram: Map<T, Int>): T? {
        var max: T? = null
        var maximum = 0
        for ((key, value) in histogram) {
            if (value > maximum) {
                maximum = value
                max = key
            }
        }
        return max
    }

    /** max(items, key = lambda x:x[1])  */
    private fun maxSecond(items: List<Tuple?>): Tuple? {
        // items = [(1,1), (2,1)]
        // pp(max(items, key = lambda x:x[1]))
        // (1,1) - the first is picked, so use > max
        var max = 0
        var bestMax: Tuple? = null
        for (item in items) {
            if (item!!.second > max) {
                bestMax = item
                max = item.second
            }
        }
        return bestMax
    }

    private class Tuple(val first: Int, val second: Int) {
        constructor(entry: Map.Entry<Int, Int>) : this(entry.key, entry.value) {}
    }

    protected class GuessQuoteAndDelimiter(
        val quotechar: Char,
        val doublequote: Boolean,
        delimiter: Char,
        skipinitialspace: Boolean
    ) : Guess(delimiter, skipinitialspace)

    @KotlinCleanup("check: values were assigned by the migration tool, seems ok from where class it's used")
    protected class Group {
        var quote = 0.toChar()
        var delim = 0.toChar()
        var space: String? = null
    }

    protected open class Guess(val delimiter: Char, val skipinitialspace: Boolean)

    companion object {
        @JvmStatic
        private fun countInString(s: String, c: Char): Int {
            var count = 0
            for (i in 0 until s.length) {
                if (s[i] == c) {
                    count++
                }
            }
            return count
        }

        @JvmStatic
        private fun countInString(haystack: String, needle: String): Int {
            var idx = 0
            var count = 0
            while (idx != -1) {
                idx = haystack.indexOf(needle, idx)
                if (idx != -1) {
                    count++
                    idx += needle.length
                }
            }
            return count
        }
    }
}
