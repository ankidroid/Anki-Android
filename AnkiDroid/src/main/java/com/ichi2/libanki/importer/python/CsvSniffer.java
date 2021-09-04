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

package com.ichi2.libanki.importer.python;

import android.annotation.SuppressLint;
import android.os.Build;

import com.ichi2.libanki.importer.CsvException;
import com.ichi2.utils.HashUtil;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

@SuppressLint("NonPublicNonStaticFieldName")
@RequiresApi(Build.VERSION_CODES.O) // Regex group(str)
public class CsvSniffer {


    private final char[] preferred;


    public CsvSniffer() {
        // in case there is more than one possible delimiter
        preferred = new char[] {',', '\t', ';', ' ', ':'};
    }



    public CsvDialect sniff(String sample, char[] delimiters) {

        List<Character> delimiterList = toList(delimiters);
        GuessQuoteAndDelimiter result = _guess_quote_and_delimiter(sample, delimiterList);
        char quotechar = result.quotechar;
        boolean doublequote = result.doublequote;
        char delimiter = result.delimiter;
        boolean skipinitialspace = result.skipinitialspace;

        if (delimiter == '\0') {
            Guess g = _guess_delimiter(sample, delimiterList);
            delimiter = g.delimiter;
            skipinitialspace = g.skipinitialspace;
        }

        if (delimiter == '\0') {
            throw new CsvException("Could not determine delimiter");
        }

        CsvDialect dialect = new CsvDialect("sniffed");

        dialect.mDoublequote = doublequote;
        dialect.mDelimiter = delimiter;
        // _csv.reader won't accept a quotechar of ''
        dialect.mQuotechar = quotechar == '\0' ? '"' : quotechar;
        dialect.mSkipInitialSpace = skipinitialspace;

        return dialect;
    }


    private List<Character> toList(@Nullable char[] delimiters) {
        if (delimiters == null) {
            return new ArrayList<>(0);
        }
        ArrayList<Character> ret = new ArrayList<>(delimiters.length);
        for (char delimiter : delimiters) {
            ret.add(delimiter);
        }
        return ret;
    }


    /**
     *  Looks for text enclosed between two identical quotes
     *  (the probable quotechar) which are preceded and followed
     *  by the same character (the probable delimiter).
     *  For example:
     *                   ,'some text',
     *  The quote with the most wins, same with the delimiter.
     *  If there is no quotechar the delimiter can't be determined
     *  this way.
     */
    private GuessQuoteAndDelimiter _guess_quote_and_delimiter(String data, List<Character> delimiters) {
        ArrayList<String> regexes = new ArrayList<>(4);
        regexes.add("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"']).*?\\k<quote>\\k<delim>"); // ,".*?",
        regexes.add("(?:^|\\n)(?<quote>[\"']).*?\\k<quote>(?<delim>[^\\w\\n\"'])(?<space> ?)");  //  ".*?",
        regexes.add("(?<delim>[^\\w\\n\"'])(?<space> ?)(?<quote>[\"']).*?\\k<quote>(?:$|\\n)");  // ,".*?"
        regexes.add("(?:^|\\n)(?<quote>[\"']).*?\\k<quote>(?:$|\\n)");                           //  ".*?" (no delim, no space)


        List<Group> matches = new ArrayList<>();

        for(String regex : regexes) {
            Pattern p = Pattern.compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
            Matcher m = p.matcher(data);
            while (m.find()) {
                Group g = new Group();
                g.delim = getCharOrNull(m, "delim");
                g.quote = getCharOrNull(m, "quote");
                g.space = m.group("space");
                matches.add(g);
            }
            if (!matches.isEmpty()) {
                break;
            }
        }
        if (matches.isEmpty()) {
            return new GuessQuoteAndDelimiter('\0', false, '\0', false);
        }


        Map<Character, Integer> quotes = HashUtil.HashMapInit(matches.size());
        Map<Character, Integer> delims = new HashMap<>();
        int spaces = 0;
        for (Group m : matches) {
            char key = m.quote;
            if (key != '\0') {
                quotes.put(key, quotes.getOrDefault(key, 0) + 1);
            }

            key = m.delim;

            if (key != '\0' && (delimiters == null || delimiters.isEmpty() || delimiters.contains(key))) {
                delims.put(key, delims.getOrDefault(key, 0) + 1);
            }

            if (m.space != null && m.space.length() > 0) {
                spaces += 1;
            }
        }

        Character quotechar = max(quotes);

        Character delim;
        boolean skipinitialspace;
        if (!delims.isEmpty()) {
            delim = max(delims);
            skipinitialspace = delims.get(delim) == spaces;
            if (delim == '\n') { // most likely a file with a single column
                delim = '\0';
            }
        } else {
            // there is *no* delimiter, it's a single column of quoted data
            delim = '\0';
            skipinitialspace = false;
        }


        // if we see an extra quote between delimiters, we've got a
        // double quoted format
        String regex = String.format("((%s)|^)\\W*%s[^%s\\n]*%s[^%s\\n]*%s\\W*((%s)|$)", delim, quotechar, delim, quotechar, delim, quotechar, delim);
        Pattern dq_regexp = Pattern.compile(regex, Pattern.MULTILINE);


        boolean doublequote = dq_regexp.matcher(data).find();

        return new GuessQuoteAndDelimiter(quotechar, doublequote, delim, skipinitialspace);
    }


    private char getCharOrNull(Matcher m, String delim) {
        String group = m.group(delim);
        if (group == null || group.length() == 0) {
            return '\0';
        }
        return group.charAt(0);
    }


    /**
     * The delimiter /should/ occur the same number of times on
     * each row. However, due to malformed data, it may not. We don't want
     * an all or nothing approach, so we allow for small variations in this
     * number.
     *   1) build a table of the frequency of each character on every line.
     *   2) build a table of frequencies of this frequency (meta-frequency?),
     *      e.g.  'x occurred 5 times in 10 rows, 6 times in 1000 rows,
     *      7 times in 2 rows'
     *   3) use the mode of the meta-frequency to determine the /expected/
     *      frequency for that character
     *   4) find out how often the character actually meets that goal
     *   5) the character that best meets its goal is the delimiter
     * For performance reasons, the data is evaluated in chunks, so it can
     * try and evaluate the smallest portion of the data possible, evaluating
     * additional chunks as necessary.
     */
    private Guess _guess_delimiter(String input, List<Character> delimiters) {

        // remove falsey values
        String[] samples = input.split("\n");
        List<String> data = new ArrayList<>(samples.length);
        for (String s : samples) {
            if (s == null || s.length() == 0) {
                continue;
            }
            data.add(s);
        }

        char[] ascii = new char[128]; // 7-bit ASCII
        for(char i = 0; i < 128; i++) {
            ascii[i] = i;
        }

        // build frequency tables
        int chunkLength = Math.min(10, data.size());
        int iteration = 0;
        Map<Character, Map<Integer, Integer>> charFrequency = new HashMap<>();
        Map<Character, Tuple> modes = new HashMap<>();
        Map<Character, Tuple> delims = new HashMap<>();
        int start = 0;
        int end = chunkLength;

        while (start < data.size()) {
            iteration++;
            for (String line : data.subList(start, end)) {
                for (char c : ascii) {
                    Map<Integer, Integer> metaFrequency = charFrequency.getOrDefault(c, new HashMap<>());
                    // must count even if frequency is 0
                    int freq = countInString(line, c);
                    // value is the mode
                    metaFrequency.put(freq, metaFrequency.getOrDefault(freq, 0) + 1);
                    charFrequency.put(c, metaFrequency);
                }
            }
            for (Map.Entry<Character, Map<Integer, Integer>> e : charFrequency.entrySet()) {
                char c = e.getKey();
                Set<Map.Entry<Integer, Integer>> bareList = e.getValue().entrySet();

                List<Tuple> items = new ArrayList<>(bareList.size());

                for (Map.Entry<Integer, Integer> entry : bareList) {
                    items.add(new Tuple(entry));
                }

                if (items.size() == 1 && items.get(0).second == 0) {
                    continue;
                }

                // get the mode of the frequencies
                if (items.size() > 1) {
                    modes.put(c, maxSecond(items));
                    // adjust the mode - subtract the sum of all
                    // other frequencies
                    Tuple toRemove = modes.get(c);
                    items.remove(toRemove);
                    modes.put(c, new Tuple(toRemove.first, toRemove.second - sumSecond(items)));
                } else {
                    modes.put(c, items.get(0));
                }
            }

            // build a list of possible delimiters
            Set<Map.Entry<Character, Tuple>> modeList = modes.entrySet();
            float total = Math.min(chunkLength * iteration, data.size());
            // (rows of consistent data) / (number of rows) = 100%
            double consistency = 1.0;
            // minimum consistency threshold
            double threshold = 0.9;
            while (delims.isEmpty() && consistency >= threshold) {
                for (Map.Entry<Character, Tuple> entry : modeList) {
                    Tuple value = entry.getValue();
                    if (value.first > 0 && value.second > 0) {
                        if (((double) value.second / total) >= consistency && (delimiters == null || delimiters.contains(entry.getKey()))) {
                            delims.put(entry.getKey(), value);
                        }
                    }
                }
                consistency -= 0.01;
            }

            if (delims.size() == 1) {
                Character delim = new ArrayList<>(delims.keySet()).get(0);
                boolean skipinitialspace = countInString(data.get(0), delim) == countInString(data.get(0), delim + " ");
                return new Guess(delim, skipinitialspace);
            }
            // analyze another chunkLength lines
            start = end;
            end += chunkLength;
        }

        if (delims.isEmpty()) {
            return new Guess('\0', false);
        }

        // if there's more than one, fall back to a 'preferred' list
        if (delims.size() > 1) {
            for (char d : preferred) {
                if (delims.containsKey(d)) {
                    boolean skipinitialspace = countInString(data.get(0), d) == countInString(data.get(0), d + " ");
                    return new Guess(d, skipinitialspace);
                }
            }
        }

        // nothing else indicates a preference, pick the character that
        // dominates(?)
        ArrayList<Map.Entry<Tuple, Character>> items = new ArrayList<>(delims.size());
        for(Map.Entry<Character, Tuple> i : delims.entrySet()) {
            items.add(new AbstractMap.SimpleEntry<>(i.getValue(), i.getKey()));
        }
        items.sort((o1, o2) -> {
            int compare = Integer.compare(o1.getKey().first, o2.getKey().first);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(o1.getKey().second, o2.getKey().second);
        });
        char delim = items.get(items.size() - 1).getValue();

        boolean skipinitialspace = countInString(data.get(0), delim) == countInString(data.get(0), delim + " ");
        return new Guess(delim, skipinitialspace);

    }


    private int sumSecond(List<Tuple> items) {
        int total = 0;
        for (Tuple item : items) {
            total += item.second;
        }
        return total;
    }


    private <T> T max(Map<T, Integer> histogram) {
        T max = null;
        int maximum = 0;
        for (Map.Entry<T, Integer> entry : histogram.entrySet()) {
            if (entry.getValue() > maximum) {
                maximum = entry.getValue();
                max = entry.getKey();
            }
        }
        return max;
    }


    /** max(items, key = lambda x:x[1]) */
    private Tuple maxSecond(List<Tuple> items) {
        // items = [(1,1), (2,1)]
        // pp(max(items, key = lambda x:x[1]))
        // (1,1) - the first is picked, so use > max
        int max = 0;
        Tuple bestMax = null;
        for (Tuple item : items) {
            if (item.second > max) {
                bestMax = item;
                max = item.second;
            }
        }
        return bestMax;
    }


    private static class Tuple {
        public final int first;
        public final int second;


        public Tuple(Integer key, Integer value) {
            first = key;
            second = value;
        }


        public Tuple(Map.Entry<Integer, Integer> entry) {
            this(entry.getKey(), entry.getValue());
        }
    }

    private static int countInString(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    private static int countInString(String haystack, String needle) {
        int idx = 0;
        int count = 0;

        while (idx != -1) {
            idx = haystack.indexOf(needle, idx);
            if (idx != -1) {
                count++;
                idx += needle.length();
            }
        }
        return count;
    }

    protected static class GuessQuoteAndDelimiter extends Guess {
        public final char quotechar;
        public final boolean doublequote;


        public GuessQuoteAndDelimiter(char quotechar, boolean doublequote, char delimiter, boolean skipinitialspace) {
            super(delimiter, skipinitialspace);
            this.quotechar = quotechar;
            this.doublequote = doublequote;
        }
    }

    protected static class Group {
        public char quote;
        public char delim;
        public String space;
    }

    protected static class Guess {
        public final char delimiter;
        public final boolean skipinitialspace;


        public Guess(char delimiter, boolean skipinitialspace) {
            this.delimiter = delimiter;
            this.skipinitialspace = skipinitialspace;
        }
    }
}
