/*
 * Diff Match and Patch
 *
 * Copyright 2006 Google Inc.
 * http://code.google.com/p/google-diff-match-patch/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ichi2.utils;

import com.ichi2.compat.CompatHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;


/**
 * Functions for diff, match and patch. Computes the difference between two texts to create a patch. Applies the patch
 * onto another text, allowing for errors.
 *
 * @author fraser@google.com (Neil Fraser) Class containing the diff, match and patch methods. Also contains the
 *         behaviour settings. TODO if possible, remove the merging code, unneeded.
 */
public class DiffEngine {

    // Defaults.
    // Set these on your diff_match_patch instance to override the defaults.

    /**
     * Number of seconds to map a diff before giving up (0 for infinity).
     */
    public float Diff_Timeout = 1.0f;
    /**
     * Cost of an empty edit operation in terms of edit characters.
     */
    public short Diff_EditCost = 4;
    /**
     * The size beyond which the double-ended diff activates. Double-ending is twice as fast, but less accurate.
     */
    public short Diff_DualThreshold = 32;

    /**
     * Internal class for returning results from diff_linesToChars(). Other less paranoid languages just use a
     * three-element array.
     */
    protected static class LinesToCharsResult {
        protected String chars1;
        protected String chars2;
        protected List<String> lineArray;


        protected LinesToCharsResult(String chars1, String chars2, List<String> lineArray) {
            this.chars1 = chars1;
            this.chars2 = chars2;
            this.lineArray = lineArray;
        }
    }

    // DIFF FUNCTIONS

    /**
     * The data structure representing a diff is a Linked list of Diff objects: {Diff(Operation.DELETE, "Hello"),
     * Diff(Operation.INSERT, "Goodbye"), Diff(Operation.EQUAL, " world.")} which means: delete "Hello", add "Goodbye"
     * and keep " world."
     */
    public enum Operation {
        DELETE, INSERT, EQUAL
    }


    /**
     * Find the differences between two texts. Run a faster slightly less optimal diff This method allows the
     * 'checklines' of diff_main() to be optional. Most of the time checklines is wanted, so default to true.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return Linked List of Diff objects.
     */
    public LinkedList<DiffAction> diff_main(String text1, String text2) {
        return diff_main(text1, text2, true);
    }


    /**
     * Find the differences between two texts. Simplifies the problem by stripping any common prefix or suffix off the
     * texts before diffing.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param checklines Speedup flag. If false, then don't run a line-level diff first to identify the changed areas.
     *            If true, then run a faster slightly less optimal diff
     * @return Linked List of Diff objects.
     */
    public LinkedList<DiffAction> diff_main(String text1, String text2, boolean checklines) {
        // Check for equality (speedup)
        LinkedList<DiffAction> diffs;
        if (text1.equals(text2)) {
            diffs = new LinkedList<>();
            diffs.add(new DiffAction(Operation.EQUAL, text1));
            return diffs;
        }

        // Trim off common prefix (speedup)
        int commonlength = diff_commonPrefix(text1, text2);
        String commonprefix = text1.substring(0, commonlength);
        text1 = text1.substring(commonlength);
        text2 = text2.substring(commonlength);

        // Trim off common suffix (speedup)
        commonlength = diff_commonSuffix(text1, text2);
        String commonsuffix = text1.substring(text1.length() - commonlength);
        text1 = text1.substring(0, text1.length() - commonlength);
        text2 = text2.substring(0, text2.length() - commonlength);

        // Compute the diff on the middle block
        diffs = diff_compute(text1, text2, checklines);

        // Restore the prefix and suffix
        if (commonprefix.length() > 0) {
            diffs.addFirst(new DiffAction(Operation.EQUAL, commonprefix));
        }
        if (commonsuffix.length() > 0) {
            diffs.addLast(new DiffAction(Operation.EQUAL, commonsuffix));
        }

        diff_cleanupMerge(diffs);
        return diffs;
    }


    /**
     * Find the differences between two texts. Assumes that the texts do not have any common prefix or suffix.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @param checklines Speedup flag. If false, then don't run a line-level diff first to identify the changed areas.
     *            If true, then run a faster slightly less optimal diff
     * @return Linked List of Diff objects.
     */
    protected LinkedList<DiffAction> diff_compute(String text1, String text2, boolean checklines) {
        LinkedList<DiffAction> diffs = new LinkedList<>();

        if (text1.length() == 0) {
            // Just add some text (speedup)
            diffs.add(new DiffAction(Operation.INSERT, text2));
            return diffs;
        }

        if (text2.length() == 0) {
            // Just delete some text (speedup)
            diffs.add(new DiffAction(Operation.DELETE, text1));
            return diffs;
        }

        String longtext = text1.length() > text2.length() ? text1 : text2;
        String shorttext = text1.length() > text2.length() ? text2 : text1;
        int i = longtext.indexOf(shorttext);
        if (i != -1) {
            // Shorter text is inside the longer text (speedup)
            Operation op = (text1.length() > text2.length()) ? Operation.DELETE : Operation.INSERT;
            diffs.add(new DiffAction(op, longtext.substring(0, i)));
            diffs.add(new DiffAction(Operation.EQUAL, shorttext));
            diffs.add(new DiffAction(op, longtext.substring(i + shorttext.length())));
            return diffs;
        }
        longtext = shorttext = null; // Garbage collect

        // Check to see if the problem can be split in two.
        String[] hm = diff_halfMatch(text1, text2);
        if (hm != null) {
            // A half-match was found, sort out the return data.
            String text1_a = hm[0];
            String text1_b = hm[1];
            String text2_a = hm[2];
            String text2_b = hm[3];
            String mid_common = hm[4];
            // Send both pairs off for separate processing.
            LinkedList<DiffAction> diffs_a = diff_main(text1_a, text2_a, checklines);
            LinkedList<DiffAction> diffs_b = diff_main(text1_b, text2_b, checklines);
            // Merge the results.
            diffs = diffs_a;
            diffs.add(new DiffAction(Operation.EQUAL, mid_common));
            diffs.addAll(diffs_b);
            return diffs;
        }

        // Perform a real diff.
        if (checklines && (text1.length() < 100 || text2.length() < 100)) {
            checklines = false; // Too trivial for the overhead.
        }
        List<String> linearray = null;
        if (checklines) {
            // Scan the text on a line-by-line basis first.
            LinesToCharsResult b = diff_linesToChars(text1, text2);
            text1 = b.chars1;
            text2 = b.chars2;
            linearray = b.lineArray;
        }

        diffs = diff_map(text1, text2);
        if (diffs == null) {
            // No acceptable result.
            diffs = new LinkedList<>();
            diffs.add(new DiffAction(Operation.DELETE, text1));
            diffs.add(new DiffAction(Operation.INSERT, text2));
        }

        if (checklines) {
            // Convert the diff back to original text.
            diff_charsToLines(diffs, linearray);
            // Eliminate freak matches (e.g. blank lines)
            diff_cleanupSemantic(diffs);

            // Rediff any replacement blocks, this time character-by-character.
            // Add a dummy entry at the end.
            diffs.add(new DiffAction(Operation.EQUAL, ""));
            int count_delete = 0;
            int count_insert = 0;
            String text_delete = "";
            String text_insert = "";
            ListIterator<DiffAction> pointer = diffs.listIterator();
            DiffAction thisDiff = pointer.next();
            while (thisDiff != null) {
                switch (thisDiff.operation) {
                    case INSERT:
                        count_insert++;
                        text_insert += thisDiff.text;
                        break;
                    case DELETE:
                        count_delete++;
                        text_delete += thisDiff.text;
                        break;
                    case EQUAL:
                        // Upon reaching an equality, check for prior redundancies.
                        if (count_delete >= 1 && count_insert >= 1) {
                            // Delete the offending records and add the merged ones.
                            pointer.previous();
                            for (int j = 0; j < count_delete + count_insert; j++) {
                                pointer.previous();
                                pointer.remove();
                            }
                            for (DiffAction newDiff : diff_main(text_delete, text_insert, false)) {
                                pointer.add(newDiff);
                            }
                        }
                        count_insert = 0;
                        count_delete = 0;
                        text_delete = "";
                        text_insert = "";
                        break;
                }
                thisDiff = pointer.hasNext() ? pointer.next() : null;
            }
            diffs.removeLast(); // Remove the dummy entry at the end.
        }
        return diffs;
    }


    /**
     * Split two texts into a list of strings. Reduce the texts to a string of hashes where each Unicode character
     * represents one line.
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return An object containing the encoded text1, the encoded text2 and the List of unique strings. The zeroth
     *         element of the List of unique strings is intentionally blank.
     */
    protected LinesToCharsResult diff_linesToChars(String text1, String text2) {
        List<String> lineArray = new ArrayList<>();
        Map<String, Integer> lineHash = new HashMap<>();
        // e.g. linearray[4] == "Hello\n"
        // e.g. linehash.get("Hello\n") == 4

        // "\x00" is a valid character, but various debuggers don't like it.
        // So we'll insert a junk entry to avoid generating a null character.
        lineArray.add("");

        String chars1 = diff_linesToCharsMunge(text1, lineArray, lineHash);
        String chars2 = diff_linesToCharsMunge(text2, lineArray, lineHash);
        return new LinesToCharsResult(chars1, chars2, lineArray);
    }


    /**
     * Split a text into a list of strings. Reduce the texts to a string of hashes where each Unicode character
     * represents one line.
     *
     * @param text String to encode.
     * @param lineArray List of unique strings.
     * @param lineHash Map of strings to indices.
     * @return Encoded string.
     */
    private String diff_linesToCharsMunge(String text, List<String> lineArray, Map<String, Integer> lineHash) {
        int lineStart = 0;
        int lineEnd = -1;
        String line;
        StringBuilder chars = new StringBuilder();
        // Walk the text, pulling out a substring for each line.
        // text.split('\n') would would temporarily double our memory footprint.
        // Modifying text would create many large strings to garbage collect.
        while (lineEnd < text.length() - 1) {
            lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                lineEnd = text.length() - 1;
            }
            line = text.substring(lineStart, lineEnd + 1);
            lineStart = lineEnd + 1;

            if (lineHash.containsKey(line)) {
                chars.append(String.valueOf((char) (int) lineHash.get(line)));
            } else {
                lineArray.add(line);
                lineHash.put(line, lineArray.size() - 1);
                chars.append(String.valueOf((char) (lineArray.size() - 1)));
            }
        }
        return chars.toString();
    }


    /**
     * Rehydrate the text in a diff from a string of line hashes to real lines of text.
     *
     * @param diffs LinkedList of Diff objects.
     * @param lineArray List of unique strings.
     */
    protected void diff_charsToLines(LinkedList<DiffAction> diffs, List<String> lineArray) {
        StringBuilder text;
        for (DiffAction diff : diffs) {
            text = new StringBuilder();
            for (int y = 0; y < diff.text.length(); y++) {
                text.append(lineArray.get(diff.text.charAt(y)));
            }
            diff.text = text.toString();
        }
    }


    /**
     * Explore the intersection points between the two texts.
     *
     * @param text1 Old string to be diffed.
     * @param text2 New string to be diffed.
     * @return LinkedList of Diff objects or null if no diff available.
     */
    protected LinkedList<DiffAction> diff_map(String text1, String text2) {
        long ms_end = System.currentTimeMillis() + (long) (Diff_Timeout * 1000);
        // Cache the text lengths to prevent multiple calls.
        int text1_length = text1.length();
        int text2_length = text2.length();
        int max_d = text1_length + text2_length - 1;
        boolean doubleEnd = Diff_DualThreshold * 2 < max_d;
        List<Set<Long>> v_map1 = new ArrayList<>();
        List<Set<Long>> v_map2 = new ArrayList<>();
        Map<Integer, Integer> v1 = new HashMap<>();
        Map<Integer, Integer> v2 = new HashMap<>();
        v1.put(1, 0);
        v2.put(1, 0);
        int x, y;
        Long footstep = 0L; // Used to track overlapping paths.
        Map<Long, Integer> footsteps = new HashMap<>();
        boolean done = false;
        // If the total number of characters is odd, then the front path will
        // collide with the reverse path.
        boolean front = ((text1_length + text2_length) % 2 == 1);
        for (int d = 0; d < max_d; d++) {
            // Bail out if timeout reached.
            if (Diff_Timeout > 0 && System.currentTimeMillis() > ms_end) {
                return null;
            }

            // Walk the front path one step.
            v_map1.add(new HashSet<Long>()); // Adds at index 'd'.
            for (int k = -d; k <= d; k += 2) {
                if (k == -d || k != d && v1.get(k - 1) < v1.get(k + 1)) {
                    x = v1.get(k + 1);
                } else {
                    x = v1.get(k - 1) + 1;
                }
                y = x - k;
                if (doubleEnd) {
                    footstep = diff_footprint(x, y);
                    if (front && (footsteps.containsKey(footstep))) {
                        done = true;
                    }
                    if (!front) {
                        footsteps.put(footstep, d);
                    }
                }
                while (!done && x < text1_length && y < text2_length && text1.charAt(x) == text2.charAt(y)) {
                    x++;
                    y++;
                    if (doubleEnd) {
                        footstep = diff_footprint(x, y);
                        if (front && (footsteps.containsKey(footstep))) {
                            done = true;
                        }
                        if (!front) {
                            footsteps.put(footstep, d);
                        }
                    }
                }
                v1.put(k, x);
                v_map1.get(d).add(diff_footprint(x, y));
                if (x == text1_length && y == text2_length) {
                    // Reached the end in single-path mode.
                    return diff_path1(v_map1, text1, text2);
                } else if (done) {
                    // Front path ran over reverse path.
                    v_map2 = v_map2.subList(0, footsteps.get(footstep) + 1);
                    LinkedList<DiffAction> a = diff_path1(v_map1, text1.substring(0, x), text2.substring(0, y));
                    a.addAll(diff_path2(v_map2, text1.substring(x), text2.substring(y)));
                    return a;
                }
            }

            if (doubleEnd) {
                // Walk the reverse path one step.
                v_map2.add(new HashSet<Long>()); // Adds at index 'd'.
                for (int k = -d; k <= d; k += 2) {
                    if (k == -d || k != d && v2.get(k - 1) < v2.get(k + 1)) {
                        x = v2.get(k + 1);
                    } else {
                        x = v2.get(k - 1) + 1;
                    }
                    y = x - k;
                    footstep = diff_footprint(text1_length - x, text2_length - y);
                    if (!front && (footsteps.containsKey(footstep))) {
                        done = true;
                    }
                    if (front) {
                        footsteps.put(footstep, d);
                    }
                    while (!done && x < text1_length && y < text2_length
                            && text1.charAt(text1_length - x - 1) == text2.charAt(text2_length - y - 1)) {
                        x++;
                        y++;
                        footstep = diff_footprint(text1_length - x, text2_length - y);
                        if (!front && (footsteps.containsKey(footstep))) {
                            done = true;
                        }
                        if (front) {
                            footsteps.put(footstep, d);
                        }
                    }
                    v2.put(k, x);
                    v_map2.get(d).add(diff_footprint(x, y));
                    if (done) {
                        // Reverse path ran over front path.
                        v_map1 = v_map1.subList(0, footsteps.get(footstep) + 1);
                        LinkedList<DiffAction> a = diff_path1(v_map1, text1.substring(0, text1_length - x),
                                text2.substring(0, text2_length - y));
                        a.addAll(diff_path2(v_map2, text1.substring(text1_length - x),
                                text2.substring(text2_length - y)));
                        return a;
                    }
                }
            }
        }
        // Number of diffs equals number of characters, no commonality at all.
        return null;
    }


    /**
     * Work from the middle back to the start to determine the path.
     *
     * @param v_map List of path sets.
     * @param text1 Old string fragment to be diffed.
     * @param text2 New string fragment to be diffed.
     * @return LinkedList of Diff objects.
     */
    protected LinkedList<DiffAction> diff_path1(List<Set<Long>> v_map, String text1, String text2) {
        LinkedList<DiffAction> path = new LinkedList<>();
        int x = text1.length();
        int y = text2.length();
        Operation last_op = null;
        for (int d = v_map.size() - 2; d >= 0; d--) {
            while (true) {
                if (v_map.get(d).contains(diff_footprint(x - 1, y))) {
                    x--;
                    if (last_op == Operation.DELETE) {
                        path.getFirst().text = text1.charAt(x) + path.getFirst().text;
                    } else {
                        path.addFirst(new DiffAction(Operation.DELETE, text1.substring(x, x + 1)));
                    }
                    last_op = Operation.DELETE;
                    break;
                } else if (v_map.get(d).contains(diff_footprint(x, y - 1))) {
                    y--;
                    if (last_op == Operation.INSERT) {
                        path.getFirst().text = text2.charAt(y) + path.getFirst().text;
                    } else {
                        path.addFirst(new DiffAction(Operation.INSERT, text2.substring(y, y + 1)));
                    }
                    last_op = Operation.INSERT;
                    break;
                } else {
                    x--;
                    y--;
                    assert (text1.charAt(x) == text2.charAt(y)) : "No diagonal.  Can't happen. (diff_path1)";
                    if (last_op == Operation.EQUAL) {
                        path.getFirst().text = text1.charAt(x) + path.getFirst().text;
                    } else {
                        path.addFirst(new DiffAction(Operation.EQUAL, text1.substring(x, x + 1)));
                    }
                    last_op = Operation.EQUAL;
                }
            }
        }
        return path;
    }


    /**
     * Work from the middle back to the end to determine the path.
     *
     * @param v_map List of path sets.
     * @param text1 Old string fragment to be diffed.
     * @param text2 New string fragment to be diffed.
     * @return LinkedList of Diff objects.
     */
    protected LinkedList<DiffAction> diff_path2(List<Set<Long>> v_map, String text1, String text2) {
        LinkedList<DiffAction> path = new LinkedList<>();
        int x = text1.length();
        int y = text2.length();
        Operation last_op = null;
        for (int d = v_map.size() - 2; d >= 0; d--) {
            while (true) {
                if (v_map.get(d).contains(diff_footprint(x - 1, y))) {
                    x--;
                    if (last_op == Operation.DELETE) {
                        path.getLast().text += text1.charAt(text1.length() - x - 1);
                    } else {
                        path.addLast(new DiffAction(Operation.DELETE, text1.substring(text1.length() - x - 1,
                                text1.length() - x)));
                    }
                    last_op = Operation.DELETE;
                    break;
                } else if (v_map.get(d).contains(diff_footprint(x, y - 1))) {
                    y--;
                    if (last_op == Operation.INSERT) {
                        path.getLast().text += text2.charAt(text2.length() - y - 1);
                    } else {
                        path.addLast(new DiffAction(Operation.INSERT, text2.substring(text2.length() - y - 1,
                                text2.length() - y)));
                    }
                    last_op = Operation.INSERT;
                    break;
                } else {
                    x--;
                    y--;
                    assert (text1.charAt(text1.length() - x - 1) == text2.charAt(text2.length() - y - 1)) : "No diagonal.  Can't happen. (diff_path2)";
                    if (last_op == Operation.EQUAL) {
                        path.getLast().text += text1.charAt(text1.length() - x - 1);
                    } else {
                        path.addLast(new DiffAction(Operation.EQUAL, text1.substring(text1.length() - x - 1,
                                text1.length() - x)));
                    }
                    last_op = Operation.EQUAL;
                }
            }
        }
        return path;
    }


    /**
     * Compute a good hash of two integers.
     *
     * @param x First int.
     * @param y Second int.
     * @return A long made up of both ints.
     */
    protected long diff_footprint(int x, int y) {
        // The maximum size for a long is 9,223,372,036,854,775,807
        // The maximum size for an int is 2,147,483,647
        // Two ints fit nicely in one long.
        long result = x;
        result = result << 32;
        result += y;
        return result;
    }


    /**
     * Determine the common prefix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the start of each string.
     */
    public int diff_commonPrefix(String text1, String text2) {
        // Performance analysis: http://neil.fraser.name/news/2007/10/09/
        int n = Math.min(text1.length(), text2.length());
        for (int i = 0; i < n; i++) {
            if (text1.charAt(i) != text2.charAt(i)) {
                return i;
            }
        }
        return n;
    }


    /**
     * Determine the common suffix of two strings
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return The number of characters common to the end of each string.
     */
    public int diff_commonSuffix(String text1, String text2) {
        // Performance analysis: http://neil.fraser.name/news/2007/10/09/
        int text1_length = text1.length();
        int text2_length = text2.length();
        int n = Math.min(text1_length, text2_length);
        for (int i = 1; i <= n; i++) {
            if (text1.charAt(text1_length - i) != text2.charAt(text2_length - i)) {
                return i - 1;
            }
        }
        return n;
    }


    /**
     * Do the two texts share a substring which is at least half the length of the longer text?
     *
     * @param text1 First string.
     * @param text2 Second string.
     * @return Five element String array, containing the prefix of text1, the suffix of text1, the prefix of text2, the
     *         suffix of text2 and the common middle. Or null if there was no match.
     */
    protected String[] diff_halfMatch(String text1, String text2) {
        String longtext = text1.length() > text2.length() ? text1 : text2;
        String shorttext = text1.length() > text2.length() ? text2 : text1;
        if (longtext.length() < 10 || shorttext.length() < 1) {
            return null; // Pointless.
        }

        // First check if the second quarter is the seed for a half-match.
        String[] hm1 = diff_halfMatchI(longtext, shorttext, (longtext.length() + 3) / 4);
        // Check again based on the third quarter.
        String[] hm2 = diff_halfMatchI(longtext, shorttext, (longtext.length() + 1) / 2);
        String[] hm;
        if (hm1 == null && hm2 == null) {
            return null;
        } else if (hm2 == null) {
            hm = hm1;
        } else if (hm1 == null) {
            hm = hm2;
        } else {
            // Both matched. Select the longest.
            hm = hm1[4].length() > hm2[4].length() ? hm1 : hm2;
        }

        // A half-match was found, sort out the return data.
        if (text1.length() > text2.length()) {
            return hm;
            // return new String[]{hm[0], hm[1], hm[2], hm[3], hm[4]};
        } else {
            return new String[] { hm[2], hm[3], hm[0], hm[1], hm[4] };
        }
    }


    /**
     * Does a substring of shorttext exist within longtext such that the substring is at least half the length of
     * longtext?
     *
     * @param longtext Longer string.
     * @param shorttext Shorter string.
     * @param i Start index of quarter length substring within longtext.
     * @return Five element String array, containing the prefix of longtext, the suffix of longtext, the prefix of
     *         shorttext, the suffix of shorttext and the common middle. Or null if there was no match.
     */
    private String[] diff_halfMatchI(String longtext, String shorttext, int i) {
        // Start with a 1/4 length substring at position i as a seed.
        String seed = longtext.substring(i, i + longtext.length() / 4);
        int j = -1;
        String best_common = "";
        String best_longtext_a = "", best_longtext_b = "";
        String best_shorttext_a = "", best_shorttext_b = "";
        while ((j = shorttext.indexOf(seed, j + 1)) != -1) {
            int prefixLength = diff_commonPrefix(longtext.substring(i), shorttext.substring(j));
            int suffixLength = diff_commonSuffix(longtext.substring(0, i), shorttext.substring(0, j));
            if (best_common.length() < suffixLength + prefixLength) {
                best_common = shorttext.substring(j - suffixLength, j) + shorttext.substring(j, j + prefixLength);
                best_longtext_a = longtext.substring(0, i - suffixLength);
                best_longtext_b = longtext.substring(i + prefixLength);
                best_shorttext_a = shorttext.substring(0, j - suffixLength);
                best_shorttext_b = shorttext.substring(j + prefixLength);
            }
        }
        if (best_common.length() >= longtext.length() / 2) {
            return new String[] { best_longtext_a, best_longtext_b, best_shorttext_a, best_shorttext_b, best_common };
        } else {
            return null;
        }
    }


    /**
     * Reduce the number of edits by eliminating semantically trivial equalities.
     *
     * @param diffs LinkedList of Diff objects.
     */
    public void diff_cleanupSemantic(LinkedList<DiffAction> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        boolean changes = false;
        Stack<DiffAction> equalities = new Stack<>(); // Stack of qualities.
        String lastequality = null; // Always equal to equalities.lastElement().text
        ListIterator<DiffAction> pointer = diffs.listIterator();
        // Number of characters that changed prior to the equality.
        int length_changes1 = 0;
        // Number of characters that changed after the equality.
        int length_changes2 = 0;
        DiffAction thisDiff = pointer.next();
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // equality found
                equalities.push(thisDiff);
                length_changes1 = length_changes2;
                length_changes2 = 0;
                lastequality = thisDiff.text;
            } else {
                // an insertion or deletion
                length_changes2 += thisDiff.text.length();
                if (lastequality != null && (lastequality.length() <= length_changes1)
                        && (lastequality.length() <= length_changes2)) {
                    // System.out.println("Splitting: '" + lastequality + "'");
                    // Walk back to offending equality.
                    while (thisDiff != equalities.lastElement()) {
                        thisDiff = pointer.previous();
                    }
                    pointer.next();

                    // Replace equality with a delete.
                    pointer.set(new DiffAction(Operation.DELETE, lastequality));
                    // Insert a corresponding an insert.
                    pointer.add(new DiffAction(Operation.INSERT, lastequality));

                    equalities.pop(); // Throw away the equality we just deleted.
                    if (!equalities.empty()) {
                        // Throw away the previous equality (it needs to be reevaluated).
                        equalities.pop();
                    }
                    if (equalities.empty()) {
                        // There are no previous equalities, walk back to the start.
                        while (pointer.hasPrevious()) {
                            pointer.previous();
                        }
                    } else {
                        // There is a safe equality we can fall back to.
                        thisDiff = equalities.lastElement();
                        while (thisDiff != pointer.previous()) {
                            // Intentionally empty loop.
                        }
                    }

                    length_changes1 = 0; // Reset the counters.
                    length_changes2 = 0;
                    lastequality = null;
                    changes = true;
                }
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }

        if (changes) {
            diff_cleanupMerge(diffs);
        }
        diff_cleanupSemanticLossless(diffs);
    }


    /**
     * Look for single edits surrounded on both sides by equalities which can be shifted sideways to align the edit to a
     * word boundary. e.g: The c<ins>at c</ins>ame. -> The <ins>cat </ins>came.
     *
     * @param diffs LinkedList of Diff objects.
     */
    public void diff_cleanupSemanticLossless(LinkedList<DiffAction> diffs) {
        String equality1, edit, equality2;
        String commonString;
        int commonOffset;
        int score, bestScore;
        String bestEquality1, bestEdit, bestEquality2;
        // Create a new iterator at the start.
        ListIterator<DiffAction> pointer = diffs.listIterator();
        DiffAction prevDiff = pointer.hasNext() ? pointer.next() : null;
        DiffAction thisDiff = pointer.hasNext() ? pointer.next() : null;
        DiffAction nextDiff = pointer.hasNext() ? pointer.next() : null;
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff.operation == Operation.EQUAL && nextDiff.operation == Operation.EQUAL) {
                // This is a single edit surrounded by equalities.
                equality1 = prevDiff.text;
                edit = thisDiff.text;
                equality2 = nextDiff.text;

                // First, shift the edit as far left as possible.
                commonOffset = diff_commonSuffix(equality1, edit);
                if (commonOffset != 0) {
                    commonString = edit.substring(edit.length() - commonOffset);
                    equality1 = equality1.substring(0, equality1.length() - commonOffset);
                    edit = commonString + edit.substring(0, edit.length() - commonOffset);
                    equality2 = commonString + equality2;
                }

                // Second, step character by character right, looking for the best fit.
                bestEquality1 = equality1;
                bestEdit = edit;
                bestEquality2 = equality2;
                bestScore = diff_cleanupSemanticScore(equality1, edit) + diff_cleanupSemanticScore(edit, equality2);
                while (edit.length() > 0 && equality2.length() > 0 && edit.charAt(0) == equality2.charAt(0)) {
                    equality1 += edit.charAt(0);
                    edit = edit.substring(1) + equality2.charAt(0);
                    equality2 = equality2.substring(1);
                    score = diff_cleanupSemanticScore(equality1, edit) + diff_cleanupSemanticScore(edit, equality2);
                    // The >= encourages trailing rather than leading whitespace on edits.
                    if (score >= bestScore) {
                        bestScore = score;
                        bestEquality1 = equality1;
                        bestEdit = edit;
                        bestEquality2 = equality2;
                    }
                }

                if (!prevDiff.text.equals(bestEquality1)) {
                    // We have an improvement, save it back to the diff.
                    if (bestEquality1.length() > 0) {
                        prevDiff.text = bestEquality1;
                    } else {
                        pointer.previous(); // Walk past nextDiff.
                        pointer.previous(); // Walk past thisDiff.
                        pointer.previous(); // Walk past prevDiff.
                        pointer.remove(); // Delete prevDiff.
                        pointer.next(); // Walk past thisDiff.
                        pointer.next(); // Walk past nextDiff.
                    }
                    thisDiff.text = bestEdit;
                    if (bestEquality2.length() > 0) {
                        nextDiff.text = bestEquality2;
                    } else {
                        pointer.remove(); // Delete nextDiff.
                        nextDiff = thisDiff;
                        thisDiff = prevDiff;
                    }
                }
            }
            prevDiff = thisDiff;
            thisDiff = nextDiff;
            nextDiff = pointer.hasNext() ? pointer.next() : null;
        }
    }


    /**
     * Given two strings, compute a score representing whether the internal boundary falls on logical boundaries. Scores
     * range from 5 (best) to 0 (worst).
     *
     * @param one First string.
     * @param two Second string.
     * @return The score.
     */
    private int diff_cleanupSemanticScore(String one, String two) {
        if (one.length() == 0 || two.length() == 0) {
            // Edges are the best.
            return 5;
        }

        // Each port of this function behaves slightly differently due to
        // subtle differences in each language's definition of things like
        // 'whitespace'. Since this function's purpose is largely cosmetic,
        // the choice has been made to use each language's native features
        // rather than force total conformity.
        int score = 0;
        // One point for non-alphanumeric.
        if (!Character.isLetterOrDigit(one.charAt(one.length() - 1)) || !Character.isLetterOrDigit(two.charAt(0))) {
            score++;
            // Two points for whitespace.
            if (Character.isWhitespace(one.charAt(one.length() - 1)) || Character.isWhitespace(two.charAt(0))) {
                score++;
                // Three points for line breaks.
                if (Character.getType(one.charAt(one.length() - 1)) == Character.CONTROL
                        || Character.getType(two.charAt(0)) == Character.CONTROL) {
                    score++;
                    // Four points for blank lines.
                    if (BLANKLINEEND.matcher(one).find() || BLANKLINESTART.matcher(two).find()) {
                        score++;
                    }
                }
            }
        }
        return score;
    }

    private Pattern BLANKLINEEND = Pattern.compile("\\n\\r?\\n\\Z", Pattern.DOTALL);
    private Pattern BLANKLINESTART = Pattern.compile("\\A\\r?\\n\\r?\\n", Pattern.DOTALL);


    /**
     * Reorder and merge like edit sections. Merge equalities. Any edit section can move as long as it doesn't cross an
     * equality.
     *
     * @param diffs LinkedList of Diff objects.
     */
    public void diff_cleanupMerge(LinkedList<DiffAction> diffs) {
        diffs.add(new DiffAction(Operation.EQUAL, "")); // Add a dummy entry at the end.
        ListIterator<DiffAction> pointer = diffs.listIterator();
        int count_delete = 0;
        int count_insert = 0;
        String text_delete = "";
        String text_insert = "";
        DiffAction thisDiff = pointer.next();
        DiffAction prevEqual = null;
        int commonlength;
        while (thisDiff != null) {
            switch (thisDiff.operation) {
                case INSERT:
                    count_insert++;
                    text_insert += thisDiff.text;
                    prevEqual = null;
                    break;
                case DELETE:
                    count_delete++;
                    text_delete += thisDiff.text;
                    prevEqual = null;
                    break;
                case EQUAL:
                    if (count_delete != 0 || count_insert != 0) {
                        // Delete the offending records.
                        pointer.previous(); // Reverse direction.
                        while (count_delete-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        while (count_insert-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        if (count_delete != 0 && count_insert != 0) {
                            // Factor out any common prefixies.
                            commonlength = diff_commonPrefix(text_insert, text_delete);
                            if (commonlength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous();
                                    assert thisDiff.operation == Operation.EQUAL : "Previous diff should have been an equality.";
                                    thisDiff.text += text_insert.substring(0, commonlength);
                                    pointer.next();
                                } else {
                                    pointer.add(new DiffAction(Operation.EQUAL, text_insert.substring(0, commonlength)));
                                }
                                text_insert = text_insert.substring(commonlength);
                                text_delete = text_delete.substring(commonlength);
                            }
                            // Factor out any common suffixies.
                            commonlength = diff_commonSuffix(text_insert, text_delete);
                            if (commonlength != 0) {
                                thisDiff = pointer.next();
                                thisDiff.text = text_insert.substring(text_insert.length() - commonlength)
                                        + thisDiff.text;
                                text_insert = text_insert.substring(0, text_insert.length() - commonlength);
                                text_delete = text_delete.substring(0, text_delete.length() - commonlength);
                                pointer.previous();
                            }
                        }
                        // Insert the merged records.
                        if (text_delete.length() > 0) {
                            pointer.add(new DiffAction(Operation.DELETE, text_delete));
                        }
                        if (text_insert.length() > 0) {
                            pointer.add(new DiffAction(Operation.INSERT, text_insert));
                        }
                        // Step forward to the equality.
                        thisDiff = pointer.hasNext() ? pointer.next() : null;
                    } else if (prevEqual != null) {
                        // Merge this equality with the previous one.
                        prevEqual.text += thisDiff.text;
                        pointer.remove();
                        thisDiff = pointer.previous();
                        pointer.next(); // Forward direction
                    }
                    count_insert = 0;
                    count_delete = 0;
                    text_delete = "";
                    text_insert = "";
                    prevEqual = thisDiff;
                    break;
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }
        // System.out.println(diff);
        if (diffs.getLast().text.length() == 0) {
            diffs.removeLast(); // Remove the dummy entry at the end.
        }

        /*
         * Second pass: look for single edits surrounded on both sides by equalities which can be shifted sideways to
         * eliminate an equality. e.g: A<ins>BA</ins>C -> <ins>AB</ins>AC
         */
        boolean changes = false;
        // Create a new iterator at the start.
        // (As opposed to walking the current one back.)
        pointer = diffs.listIterator();
        DiffAction prevDiff = pointer.hasNext() ? pointer.next() : null;
        thisDiff = pointer.hasNext() ? pointer.next() : null;
        DiffAction nextDiff = pointer.hasNext() ? pointer.next() : null;
        // Intentionally ignore the first and last element (don't need checking).
        while (nextDiff != null) {
            if (prevDiff.operation == Operation.EQUAL && nextDiff.operation == Operation.EQUAL) {
                // This is a single edit surrounded by equalities.
                if (thisDiff.text.endsWith(prevDiff.text)) {
                    // Shift the edit over the previous equality.
                    thisDiff.text = prevDiff.text
                            + thisDiff.text.substring(0, thisDiff.text.length() - prevDiff.text.length());
                    nextDiff.text = prevDiff.text + nextDiff.text;
                    pointer.previous(); // Walk past nextDiff.
                    pointer.previous(); // Walk past thisDiff.
                    pointer.previous(); // Walk past prevDiff.
                    pointer.remove(); // Delete prevDiff.
                    pointer.next(); // Walk past thisDiff.
                    thisDiff = pointer.next(); // Walk past nextDiff.
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                } else if (thisDiff.text.startsWith(nextDiff.text)) {
                    // Shift the edit over the next equality.
                    prevDiff.text += nextDiff.text;
                    thisDiff.text = thisDiff.text.substring(nextDiff.text.length()) + nextDiff.text;
                    pointer.remove(); // Delete nextDiff.
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                }
            }
            prevDiff = thisDiff;
            thisDiff = nextDiff;
            nextDiff = pointer.hasNext() ? pointer.next() : null;
        }
        // If shifts were made, the diff needs reordering and another shift sweep.
        if (changes) {
            diff_cleanupMerge(diffs);
        }
    }


    /**
     * Return two strings to display as typed and correct text.
     *
     * @param typed (cleaned-up) text the user typed in,
     * @param typed (cleaned-up) correct text
     * @return Two-element String array with HTML representation of the diffs between the inputs.
     */
    public String[] diffedHtmlStrings(String typed, String correct) {
        StringBuilder prettyTyped = new StringBuilder();
        StringBuilder prettyCorrect = new StringBuilder();
        for (DiffAction aDiff : diff_main(typed, correct)) {
            switch (aDiff.operation) {
                case INSERT:
                    prettyTyped.append(wrapBad(aDiff.text));
                    break;
                case DELETE:
                    prettyCorrect.append(wrapMissing(aDiff.text));
                    break;
                case EQUAL:
                    prettyTyped.append(wrapGood(aDiff.text));
                    prettyCorrect.append(wrapGood(aDiff.text));
                    break;
            }
        }
        return new String[] {prettyTyped.toString(), prettyCorrect.toString()};
    }

    public static String wrapBad(String in) {
        // We do the comparison with “<”s &c. in the strings, but should of course not just put those in the HTML
        // output. Also, it looks like the Android WebView swallows single “\”s, so replace those with the entity by
        // hand.
        return "<span class=\"typeBad\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }

    public static String wrapGood(String in) {
        return "<span class=\"typeGood\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }

    public static String wrapMissing(String in) {
        return "<span class=\"typeMissed\">" + CompatHelper.getCompat().detagged(in).replace("\\", "&#x5c;") + "</span>";
    }


    /**
     * Class representing one diff operation.
     */
    public static class DiffAction {
        /**
         * One of: INSERT, DELETE or EQUAL.
         */
        public Operation operation;
        /**
         * The text associated with this diff operation.
         */
        public String text;


        /**
         * Constructor. Initializes the diff with the provided values.
         *
         * @param operation One of INSERT, DELETE or EQUAL.
         * @param text The text being applied.
         */
        public DiffAction(Operation operation, String text) {
            // Construct a diff with the specified operation and text.
            this.operation = operation;
            this.text = text;
        }


        /**
         * Display a human-readable version of this Diff.
         *
         * @return text version.
         */
        @Override
        public String toString() {
            String prettyText = text.replace('\n', '\u00b6');
            return "Diff(" + operation + ",\"" + prettyText + "\")";
        }


        /**
         * Is this Diff equivalent to another Diff?
         *
         * @param d Another Diff to compare against.
         * @return true or false.
         */
        @Override
        public boolean equals(Object d) {
            try {
                return (((DiffAction) d).operation == operation) && (((DiffAction) d).text.equals(text));
            } catch (ClassCastException e) {
                return false;
            }
        }
    }
}
