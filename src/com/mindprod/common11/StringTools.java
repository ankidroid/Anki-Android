/*
 * @(#)StringTools.java
 *
 * Summary: Miscellaneous static methods for dealing with Strings in JDK 1.1+.
 *
 * Copyright: (c) 1997-2010 Roedy Green, Canadian Mind Products, http://mindprod.com
 *
 * Licence: This software may be copied and used freely for any purpose but military.
 *          http://mindprod.com/contact/nonmil.html
 *
 * Requires: JDK 1.1+
 *
 * Created with: IntelliJ IDEA IDE.
 *
 * Version History:
 *  1.5 2005-07-14 - split off from Misc, allow for compilation with old compiler.
 *  1.6 2006-01-01
 *  1.7 2006-03-04 - format with IntelliJ and prepare Javadoc
 *  1.8 2006-10-15 - add condense method.
 *  1.9 2008-03-10 - add StringTools.firstWord
 *  2.0 2008-04-01
 *  2.1 2008-12-16 - new methods in BigDate
 *  2.2 2009-04-19 - add countLeading, countTrailing, TrimLeading, TrimTrailing that take multiple trim chars.
 *  2.3 2009-04-29 - add countInstances( String page, char lookFor )
 *  2.4 2009-04-30 - fix but in countLeading ( String text, String leads ).
 *                   add pruneExcessBlankLines
 *  2.5 2009-11-14 - add haveCommonChar, isLetter, isDigit
 *  2.6 2010-02-11 - removed removeHead (redundant - same as chopLeadingString).
 *                   renamed chopLeading to chopLeadingString to clearify does not trim a variety of chars.
 */

package com.mindprod.common11;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * Miscellaneous static methods for dealing with Strings in JDK 1.1+.
 * <p/>
 * Augmented by com.mindprod.common15.StringTools for JDK 1.5+.
 * <p/>
 * 
 * @author Roedy Green, Canadian Mind Products
 * @version 2.6 2010-02-11 - removed removeHead (redundant - same as chopLeadingString). renamed chopLeading to
 *          chopLeadingString to clearify does not trim a variety of chars.
 * @noinspection WeakerAccess
 * @since 2003-05-15
 */
public class StringTools {
    // ------------------------------ CONSTANTS ------------------------------

    /**
     * true if you want extra debugging output and test code
     */
    private static final boolean DEBUGGING = false;

    /**
     * used to efficiently generate Strings of spaces of varying length
     */
    private static final String SOMESPACES = "                                                                      ";

    /**
     * track which chars in range 0..017F are vowels. Lookup table takes only 48 bytes.
     */
    private static final BitSet vt = new BitSet(0x0180);


    // -------------------------- PUBLIC STATIC METHODS --------------------------

    /**
     * makeshift system beep if awt.Toolkit.beep is not available. Works also in JDK 1.02.
     */
    public static void beep() {
        System.out.print("\007");
        System.out.flush();
    }// end beep


    /**
     * Convert String to canonical standard form. null -> "". Trims lead trail blanks. Never null.
     * 
     * @param s String to be converted.
     * @return String in canonical form.
     */
    public static String canonical(String s) {
        if (s == null) {
            return "";
        } else {
            return s.trim();
        }
    }


    /**
     * remove leading string if present
     * 
     * @param text text with possible leading string, possibly empty or null.
     * @param toChop the leading string of interest. Not a list of possible chars to chop, order matters.
     * @return string with to toChop string removed if the text starts with it, otherwise the original string unmodified.
     * @see #trimLeading(String,String)
     * @see #chopTrailingString(String,String)
     */
    public static String chopLeadingString(String text, String toChop) {
        if (text != null && text.startsWith(toChop)) {
            return text.substring(toChop.length());
        } else {
            return text;
        }
    }


    /**
     * remove trailing string if present
     * 
     * @param text text with possible trailing string, possibly empty, but not null.
     * @param toChop the trailing string of interest. Not a list of possible chars to chop, order matters.
     * @return string with to toChop string removed if the text ends with it, otherwise the original string unmodified.
     * @see #trimTrailing(String,String)
     * @see #chopLeadingString(String,String)
     */
    public static String chopTrailingString(String text, String toChop) {
        if (text != null && text.endsWith(toChop)) {
            return text.substring(0, text.length() - toChop.length());
        } else {
            return text;
        }
    }


    /**
     * Collapse multiple spaces in string down to a single space. Remove lead and trailing spaces. Does not collapse
     * other whitespace.
     * 
     * @param s String to strip of blanks.
     * @return String with all blanks, lead/trail/embedded removed.
     * @noinspection WeakerAccess,SameParameterValue
     * @see #squish(String)
     */
    public static String condense(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.indexOf("  ") < 0) {
            return s;
        }
        int len = s.length();
        // have to use StringBuffer for JDK 1.1
        StringBuilder b = new StringBuilder(len - 1);
        boolean suppressSpaces = false;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (suppressSpaces) {
                    // subsequent space
                } else {
                    // first space
                    b.append(c);
                    suppressSpaces = true;
                }
            } else {
                // was not a space
                b.append(c);
                suppressSpaces = false;
            }
        }// end for
        return b.toString();
    }


    /**
     * Count how many times a String occurs on a page.
     * 
     * @param page big String to look in.
     * @param lookFor small String to look for and count instances.
     * @return number of times the String appears non-overlapping.
     */
    public static int countInstances(String page, String lookFor) {
        int count = 0;
        for (int start = 0; (start = page.indexOf(lookFor, start)) >= 0; start += lookFor.length()) {
            count++;
        }
        return count;
    }


    /**
     * Count how many times a char occurs in a String.
     * 
     * @param page big String to look in.
     * @param lookFor char to lookfor count instances.
     * @return number of times the char appears.
     */
    public static int countInstances(String page, char lookFor) {
        int count = 0;
        for (int i = 0; i < page.length(); i++) {
            if (page.charAt(i) == lookFor) {
                count++;
            }
        }
        return count;
    }


    /**
     * count of how many leading characters there are on a string matching a given character. It does not remove them.
     * 
     * @param text text with possible leading characters, possibly empty, but not null.
     * @param c the leading character of interest, usually ' ' or '\n'
     * @return count of leading matching characters, possibly 0.
     * @noinspection WeakerAccess
     */
    public static int countLeading(String text, char c) {
        int count;
        for (count = 0; count < text.length() && text.charAt(count) == c; count++) {
            // empty loop
        }
        return count;
    }


    /**
     * count of how many leading characters there are on a string matching a given character. It does not remove them.
     * 
     * @param text text with possible leading characters, possibly empty, but not null.
     * @param possibleChars the leading characters of interest, usually ' ' or '\n'
     * @return count of leading matching characters, possibly 0.
     * @noinspection WeakerAccess
     */
    public static int countLeading(String text, String possibleChars) {
        int count;
        for (count = 0; count < text.length() && possibleChars.indexOf(text.charAt(count)) >= 0; count++) {
            // empty loop.
        }
        return count;
    }


    /**
     * count of how many trailing characters there are on a string matching a given character. It does not remove them.
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param c the trailing character of interest, usually ' ' or '\n'
     * @return count of trailing matching characters, possibly 0.
     * @noinspection WeakerAccess
     */
    public static int countTrailing(String text, char c) {
        int length = text.length();
        // need defined outside the for loop.
        int count;
        for (count = 0; count < length && text.charAt(length - 1 - count) == c; count++) {
            // empty loop
        }
        return count;
    }


    /**
     * count of how many trailing characters there are on a string matching a given character. It does not remove them.
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param possibleChars the trailing characters of interest, usually ' ' or '\n'
     * @return count of trailing matching characters, possibly 0.
     * @noinspection WeakerAccess
     */
    public static int countTrailing(String text, String possibleChars) {
        int length = text.length();
        // need defined outside the for loop.
        int count;
        for (count = 0; count < length && possibleChars.indexOf(text.charAt(length - 1 - count)) >= 0; count++) {
            // empty loop
        }
        return count;
    }


    /**
     * gets the first word of a String, delimited by space or the end of the string. \n will not delimit a word. If
     * there are no blanks in the string, the result is the entire string.
     * 
     * @param s the input String
     * @return the first word of the String.
     * @see #lastWord(String)
     */
    public static String firstWord(String s) {
        s = s.trim();
        final int place = s.indexOf(' ');
        return (place < 0) ? s : s.substring(0, place);
    }


    /**
     * Returns true if strings a and b have one or more characters in common, not necessarily at the same offset. If you
     * think of a and b as sets of chars, returns true if the intersection of those sets in not null. It can also me
     * though of as like an indexOf that scans for multiple characters at once.
     * 
     * @param a first string
     * @param b second string
     * @return true if the strings have one or more characters in common.
     */
    public static boolean haveCommonChar(String a, String b) {
        for (int i = 0; i < b.length(); i++) {
            if (a.indexOf(b.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }


    /**
     * find the first instance of whitespace (space, \n, \r, \t in a string.
     * 
     * @param s string to scan
     * @return -1 if not found, offset relative to start of string where found
     */
    public static int indexOfWhiteSpace(String s) {
        return indexOfWhiteSpace(s, 0);
    }


    /**
     * find the first instance of whitespace (space, \n, \r, \t in a string.
     * 
     * @param s string to scan
     * @param startOffset where in string to start looking
     * @return -1 if not found, offset relative to start of string where found, not relative to startOffset.
     */
    public static int indexOfWhiteSpace(String s, int startOffset) {
        final int length = s.length();
        for (int i = startOffset; i < length; i++) {
            switch (s.charAt(i)) {
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    return i;
                default:
                    // keep looking
            }
        }// end for
        return -1;
    }


    /**
     * Check if char is plain ASCII digit.
     * 
     * @param c char to check.
     * @return true if char is in range 0-9
     * @see Character#isLetter(char)
     */
    public static boolean isDigit(char c) {
        return '0' <= c && c <= '9';
    }


    /**
     * Is this string empty? In Java 1.6 + isEmpty is build in. Sun's version being an instance method cannot test for
     * null.
     * 
     * @param s String to be tested for emptiness.
     * @return true if the string is null or equal to the "" null string. or just blanks
     */
    public static boolean isEmpty(String s) {
        return (s == null) || s.trim().length() == 0;
    }


    /**
     * Ensure the string contains only legal characters.
     * 
     * @param candidate string to test.
     * @param legalChars characters than are legal for candidate.
     * @return true if candidate is formed only of chars from the legal set.
     */
    public static boolean isLegal(String candidate, String legalChars) {
        for (int i = 0; i < candidate.length(); i++) {
            if (legalChars.indexOf(candidate.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }


    /**
     * Ensure the char is only one a set of legal characters.
     * 
     * @param candidate char to test.
     * @param legalChars characters than are legal for candidate.
     * @return true if candidate is one of the legallegal set.
     */
    public static boolean isLegal(char candidate, String legalChars) {
        return legalChars.indexOf(candidate) >= 0;
    }


    /**
     * Check if char is plain ASCII letter lower or upper case.
     * 
     * @param c char to check.
     * @return true if char is in range a..z A..Z
     * @see Character#isLowerCase(char)
     * @see Character#isUpperCase(char)
     * @see Character#isDigit(char)
     */
    public static boolean isLetter(char c) {
        return 'a' <= c && c <= 'z' || 'A' <= c && c <= 'Z';
    }


    /**
     * Check if char is plain ASCII lower case.
     * 
     * @param c char to check.
     * @return true if char is in range a..z.
     * @see Character#isLowerCase(char)
     * @see Character#isLetter(char)
     */
    public static boolean isUnaccentedLowerCase(char c) {
        return 'a' <= c && c <= 'z';
    }


    /**
     * Check if char is plain ASCII upper case.
     * 
     * @param c char to check.
     * @return true if char is in range A..Z.
     * @see Character#isUpperCase(char)
     * @see Character#isLetter(char)
     */
    public static boolean isUnaccentedUpperCase(char c) {
        return 'A' <= c && c <= 'Z';
    }


    /**
     * is this character a vowel?
     * 
     * @param c the character, any char upper or lower case, punctuation or symbol
     * @return true if char is aeiou or AEIOU, or vowel accented in any way or ligature ae AE oe OE ij IJ
     */
    public static boolean isVowel(char c) {
        return c < 0x0180 && vt.get(c);
    }


    /**
     * gets the last word of a String, delimited by space or the end of the string.
     * 
     * @param s the input String
     * @return the last word of the String.
     * @see #firstWord(String)
     */
    public static String lastWord(String s) {
        s = s.trim();
        return s.substring(s.lastIndexOf(' ') + 1);
    }


    /**
     * Pads the string value out to the given length by applying blanks on the right, left justifying the value.
     * 
     * @param value value to be converted to string String to be padded/chopped.
     * @param newLen length of new String desired.
     * @param chop true if Strings longer than newLen should be truncated to newLen chars.
     * @return String padded on right/chopped to the desired length. Spaces are inserted on the right.
     * @see #toLZ
     */
    public static String leftJustified(int value, int newLen, boolean chop) {
        return rightPad(Integer.toString(value), newLen, chop);
    }


    /**
     * Pads the string out to the given length by applying blanks on the left.
     * 
     * @param s String to be padded/chopped.
     * @param newLen length of new String desired.
     * @param chop true if Strings longer than newLen should be truncated to newLen chars.
     * @return String padded on left/chopped to the desired length. Spaces are inserted on the left.
     * @see #toLZ
     */
    public static String leftPad(String s, int newLen, boolean chop) {
        int grow = newLen - s.length();
        if (grow <= 0) {
            if (chop) {
                return s.substring(0, newLen);
            } else {
                return s;
            }
        } else {
            return spaces(grow) + s;
        }
    }


    /**
     * convert a String to a long. The routine is very forgiving. It ignores invalid chars, lead trail, embedded spaces,
     * decimal points etc. Dash is treated as a minus sign.
     * 
     * @param numStr String to be parsed.
     * @return long value of String with junk characters stripped.
     * @throws NumberFormatException if the number is too big to fit in a long.
     */
    public static long parseDirtyLong(String numStr) {
        numStr = numStr.trim();
        // strip commas, spaces, decimals + etc
        StringBuilder b = new StringBuilder(numStr.length());
        boolean negative = false;
        for (int i = 0, n = numStr.length(); i < n; i++) {
            char c = numStr.charAt(i);
            if (c == '-') {
                negative = true;
            } else if ('0' <= c && c <= '9') {
                b.append(c);
            }
        }// end for
        numStr = b.toString();
        if (numStr.length() == 0) {
            return 0;
        }
        long num = Long.parseLong(numStr);
        if (negative) {
            num = -num;
        }
        return num;
    }


    /**
     * convert a String into long pennies. It ignores invalid chars, lead trail, embedded spaces. Dash is treated as a
     * minus sign. 0 or 2 decimal places are permitted.
     * 
     * @param numStr String to be parsed.
     * @return long pennies.
     * @throws NumberFormatException if the number is too big to fit in a long.
     * @noinspection WeakerAccess
     */
    public static long parseLongPennies(String numStr) {
        numStr = numStr.trim();
        // strip commas, spaces, + etc
        StringBuilder b = new StringBuilder(numStr.length());
        boolean negative = false;
        int decpl = -1;
        for (int i = 0, n = numStr.length(); i < n; i++) {
            char c = numStr.charAt(i);
            switch (c) {
                case '-':
                    negative = true;
                    break;

                case '.':
                    if (decpl == -1) {
                        decpl = 0;
                    } else {
                        throw new NumberFormatException("more than one decimal point");
                    }
                    break;

                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    if (decpl != -1) {
                        decpl++;
                    }
                    b.append(c);
                    break;

                default:
                    // ignore junk chars
                    break;
            }// end switch
        }// end for
        if (numStr.length() != b.length()) {
            numStr = b.toString();
        }

        if (numStr.length() == 0) {
            return 0;
        }
        long num = Long.parseLong(numStr);
        if (decpl == -1 || decpl == 0) {
            num *= 100;
        } else if (decpl == 2) {/* it is fine as is */
        } else {
            throw new NumberFormatException("wrong number of decimal places.");
        }

        if (negative) {
            num = -num;
        }
        return num;
    }


    /**
     * Print dollar currency, stored internally as scaled int. convert pennies to a string with a decorative decimal
     * point.
     * 
     * @param pennies long amount in pennies.
     * @return amount with decorative decimal point, but no lead $.
     * @noinspection WeakerAccess
     */
    public static String penniesToString(long pennies) {
        boolean negative;
        if (pennies < 0) {
            pennies = -pennies;
            negative = true;
        } else {
            negative = false;
        }
        String s = Long.toString(pennies);
        int len = s.length();
        switch (len) {
            case 1:
                s = "0.0" + s;
                break;
            case 2:
                s = "0." + s;
                break;
            default:
                s = s.substring(0, len - 2) + "." + s.substring(len - 2, len);
                break;
        }// end switch
        if (negative) {
            s = "-" + s;
        }
        return s;
    }


    /**
     * Extracts a number from a string, returns 0 if malformed.
     * 
     * @param s String containing the integer.
     * @return binary integer.
     */
    public static int pluck(String s) {
        int result = 0;
        try {
            result = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            // leave result at 0
        }
        return result;
    }


    /**
     * Collapse multiple blank lines down to one. Discards lead and trail blank lines. Blank lines are lines that when
     * trimmed have length 0. Enhanced version available in com.mindprod.common11.StringTools for JDK 1.5+.
     * 
     * @param lines array of lines to tidy.
     * @param minBlankLinesToKeep usually 1 meaning 1+ consecutive blank lines become 1, effectively collapsing runs of
     *            blank lines down to 1. if 2, 1 blank line is removed, and 2+ consecutive blanks lines become 1,
     *            effectively undouble spacing. if zero, non-blank lines will be separated by one blank line, even if
     *            there was not one there to begin with, completely independent of preexisting blank lines, effectively
     *            double spacing.. 9999 effectively removes all blank lines.
     * @return array of lines with lead and trail blank lines removed, and excess blank lines collapsed down to one or 0.
     *         The results are NOT trimmed.
     */
    public static String[] pruneExcessBlankLines(String[] lines, int minBlankLinesToKeep) {
        int firstNonBlankLine = lines.length;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().length() > 0) {
                firstNonBlankLine = i;
                break;
            }
        }

        int lastNonBlankLine = -1;
        for (int i = lines.length - 1; i > 0; i--) {
            if (lines[i].trim().length() > 0) {
                lastNonBlankLine = i;
                break;
            }
        }
        if (firstNonBlankLine > lastNonBlankLine) {
            return new String[0];
        }

        // collapse blank lines in the middle chunk

        ArrayList<String> keep = new ArrayList<String>(lastNonBlankLine - firstNonBlankLine + 1);
        int pendingBlankLines = 0;
        for (int i = firstNonBlankLine; i <= lastNonBlankLine; i++) {
            if (lines[i].trim().length() == 0) {
                pendingBlankLines++;
            } else {
                if (pendingBlankLines >= minBlankLinesToKeep) {
                    keep.add("");
                }
                keep.add(lines[i]); // we don't trim. That is up to caller.
                pendingBlankLines = 0;
            }
        }
        return keep.toArray(new String[keep.size()]);
    }


    /**
     * used to prepare SQL string literals by doubling each embedded ' and wrapping in ' at each end. Further quoting is
     * required to use the results in Java String literals. If you use PreparedStatement, then this method is not
     * needed. The ' quoting is automatically handled for you.
     * 
     * @param sql Raw SQL string literal
     * @return sql String literal enclosed in '
     * @noinspection WeakerAccess,SameParameterValue
     */
    public static String quoteSQL(String sql) {
        StringBuilder sb = new StringBuilder(sql.length() + 5);
        sb.append('\'');
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                sb.append("\'\'");
            } else {
                sb.append(c);
            }
        }
        sb.append('\'');
        return sb.toString();
    }


    /**
     * Produce a String of a given repeating character.
     * 
     * @param c the character to repeat
     * @param count the number of times to repeat
     * @return String, e.g. rep('*',4) returns "****"
     * @noinspection WeakerAccess,SameParameterValue
     */
    public static String rep(char c, int count) {
        if (c == ' ' && count <= SOMESPACES.length()) {
            return SOMESPACES.substring(0, count);
        }
        char[] s = new char[count];
        for (int i = 0; i < count; i++) {
            s[i] = c;
        }
        return new String(s).intern();
    }


    /**
     * Pads the string value out to the given length by applying blanks on the left, right justifying the value.
     * 
     * @param value value to be converted to string String to be padded/chopped.
     * @param newLen length of new String desired.
     * @param chop true if Strings longer than newLen should be truncated to newLen chars.
     * @return String padded on left/chopped to the desired length. Spaces are inserted on the left.
     * @see #toLZ
     */
    public static String rightJustified(int value, int newLen, boolean chop) {
        return leftPad(Integer.toString(value), newLen, chop);
    }


    /**
     * Pads the string out to the given length by applying blanks on the right.
     * 
     * @param s String to be padded/chopped.
     * @param newLen length of new String desired.
     * @param chop true if Strings longer than newLen should be truncated to newLen chars.
     * @return String padded on right/chopped to the desired length. Spaces are inserted on the right.
     * @noinspection WeakerAccess,SameParameterValue
     */
    public static String rightPad(String s, int newLen, boolean chop) {
        int grow = newLen - s.length();
        if (grow <= 0) {
            if (chop) {
                return s.substring(0, newLen);
            } else {
                return s;
            }
        } else {
            return s + spaces(grow);
        }
    }


    /**
     * Generate a string of spaces n chars long.
     * 
     * @param n how many spaces long
     * @return a string of spaces n chars long.
     */
    public static String spaces(int n) {
        if (n <= SOMESPACES.length()) {
            return SOMESPACES.substring(0, n);
        } else {
            return rep(' ', n);
        }
    }


    /**
     * Remove all spaces from a String. Does not touch other whitespace.
     * 
     * @param s String to strip of blanks.
     * @return String with all blanks, lead/trail/embedded removed.
     * @see #condense(String)
     */
    public static String squish(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        if (s.indexOf(' ') < 0) {
            return s;
        }
        int len = s.length();
        // StringBuilder has been part of Android since API Level 1
        StringBuilder b = new StringBuilder(len - 1);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c != ' ') {
                b.append(c);
            }
        }// end for
        return b.toString();
    }


    /**
     * convert to Book Title case, with first letter of each word capitalised. e.g. "handbook to HIGHER consciousness"
     * -> "Handbook to Higher Consciousness" e.g. "THE HISTORY OF THE U.S.A." -> "The History of the U.S.A." e.g. "THE
     * HISTORY OF THE USA" -> "The History of the Usa" (sorry about that.) Don't confuse this with Character.isTitleCase
     * which concerns ligatures.
     * 
     * @param s String to convert. May be any mixture of case.
     * @return String with each word capitalised, except embedded words "the" "of" "to"
     * @noinspection WeakerAccess
     */
    public static String toBookTitleCase(String s) {
        char[] ca = s.toCharArray();
        // Track if we changed anything so that
        // we can avoid creating a duplicate String
        // object if the String is already in Title case.
        boolean changed = false;
        boolean capitalise = true;
        boolean firstCap = true;
        for (int i = 0; i < ca.length; i++) {
            char oldLetter = ca[i];
            if (oldLetter != '\''
                    && (oldLetter <= '/' || ':' <= oldLetter && oldLetter <= '?' || ']' <= oldLetter
                            && oldLetter <= '`')) {
                /* whitespace, control chars or punctuation */
                /* Next normal char should be capitalised */
                /* apostrophe treated as letter so Molly's won't come out Molly'S */
                capitalise = true;
            } else {
                if (capitalise && !firstCap) {
                    // might be the_ of_ or to_
                    capitalise = !(s.substring(i, Math.min(i + 4, s.length())).equalsIgnoreCase("the ")
                            || s.substring(i, Math.min(i + 3, s.length())).equalsIgnoreCase("of ") || s.substring(i,
                            Math.min(i + 3, s.length())).equalsIgnoreCase("to "));
                }// end if
                char newLetter = capitalise ? Character.toUpperCase(oldLetter) : Character.toLowerCase(oldLetter);
                ca[i] = newLetter;
                changed |= (newLetter != oldLetter);
                capitalise = false;
                firstCap = false;
            }// end if
        }// end for
        if (changed) {
            s = new String(ca);
        }
        return s;
    }


    /**
     * Convert int to hex with lead zeroes
     * 
     * @param h number you want to convert to hex
     * @return 0x followed by unsigned hex 8-digit representation
     * @noinspection WeakerAccess
     */
    public static String toHexString(int h) {
        String s = Integer.toHexString(h);
        if (s.length() < 8) {// pad on left with zeros
            s = "00000000".substring(0, 8 - s.length()) + s;
        }
        return "0x" + s;
    }


    /**
     * Convert an integer to a String, with left zeroes.
     * 
     * @param i the integer to be converted
     * @param len the length of the resulting string. Warning. It will chop the result on the left if it is too long.
     * @return String representation of the int e.g. 007
     * @see #leftPad
     */
    public static String toLZ(int i, int len) {
        // Since String is final, we could not add this method there.
        String s = Integer.toString(i);
        if (s.length() > len) {/* return rightmost len chars */
            return s.substring(s.length() - len);
        } else if (s.length() < len)
        // pad on left with zeros
        {
            return "000000000000000000000000000000".substring(0, len - s.length()) + s;
        } else {
            return s;
        }
    }


    /**
     * convert an integer value to unsigned hex with leading zeroes.
     * 
     * @param value integer to convert.
     * @param len how many characters you want in the result.
     * @return value in hex, padded to len chars with 0s on the left.
     */
    public static String toLZHexString(int value, int len) {
        // Since String is final, we could not add this method there.
        final String s = Integer.toHexString(value);
        if (s.length() > len) {/* return rightmost len chars */
            return s.substring(s.length() - len);
        } else if (s.length() < len)
        // pad on left with zeros. at most 7 will be prepended
        {
            return "0000000".substring(0, len - s.length()) + s;
        } else {
            return s;
        }
    }


    /**
     * Quick replacement for Character.toLowerCase for use with English-only. It does not deal with accented characters.
     * 
     * @param c character to convert
     * @return character converted to lower case
     */
    public static char toLowerCase(char c) {
        return 'A' <= c && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }


    /**
     * Quick replacement for Character.toLowerCase for use with English-only. It does not deal with accented characters.
     * 
     * @param s String to convert
     * @return String converted to lower case
     */
    public static String toLowerCase(String s) {
        final char[] ca = s.toCharArray();
        final int length = ca.length;
        boolean changed = false;
        // can't use for:each since we need the index to set.
        for (int i = 0; i < length; i++) {
            final char c = ca[i];
            if ('A' <= c && c <= 'Z') {
                // found a char that needs conversion.
                ca[i] = (char) (c + ('a' - 'A'));
                changed = true;
            }
        }
        // give back same string if unchanged.
        return changed ? new String(ca) : s;
    }


    /**
     * Quick replacement for Character.toUpperCase for use with English-only. It does not deal with accented characters.
     * 
     * @param c character to convert
     * @return character converted to upper case
     */
    public static char toUpperCase(char c) {
        return 'a' <= c && c <= 'z' ? (char) (c + ('A' - 'a')) : c;
    }


    /**
     * Quick replacement for Character.toUpperCase for use with English-only. It does not deal with accented characters.
     * 
     * @param s String to convert
     * @return String converted to upper case
     */
    public static String toUpperCase(String s) {
        final char[] ca = s.toCharArray();
        final int length = ca.length;
        boolean changed = false;
        // can't use for:each since we need the index to set.
        for (int i = 0; i < length; i++) {
            final char c = ca[i];
            if ('a' <= c && c <= 'z') {
                // found a char that needs conversion.
                ca[i] = (char) (c + ('A' - 'a'));
                changed = true;
            }
        }
        // give back same string if unchanged.
        return changed ? new String(ca) : s;
    }


    /**
     * Removes white space from beginning this string.
     * 
     * @param s String to process. As always the original in unchanged.
     * @return this string, with leading white space removed
     * @noinspection WeakerAccess,WeakerAccess,WeakerAccess,SameParameterValue,WeakerAccess
     * @see #trimLeading(String,char) <p/>
     *      All characters that have codes less than or equal to <code>'&#92;u0020'</code> (the space character) are
     *      considered to be white space.
     */
    public static String trimLeading(String s) {
        if (s == null) {
            return null;
        }
        int len = s.length();
        int st = 0;
        while ((st < len) && (s.charAt(st) <= ' ')) {
            st++;
        }
        return (st > 0) ? s.substring(st, len) : s;
    }


    /**
     * trim leading characters there are on a string matching a given character.
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param c the trailing character of interest, usually ' ' or '\n'
     * @return string with any of those trailing characters removed.
     * @see #trimLeading(String)
     */
    public static String trimLeading(String text, char c) {
        int count = countLeading(text, c);
        // substring will optimise the 0 case.
        return text.substring(count);
    }


    /**
     * trim leading characters there are on a string matching a given characters
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param toTrim the leading characters of interest, usually ' ' or '\n'
     * @return string with any of those leading characters removed.
     * @see #trimTrailing(String)
     * @see #chopLeadingString(String,String)
     */
    public static String trimLeading(String text, String toTrim) {
        int count = countLeading(text, toTrim);
        // substring will optimise the 0 case.
        return text.substring(count);
    }


    /**
     * Removes white space from end this string.
     * 
     * @param s String to process. As always the original in unchanged.
     * @return this string, with trailing white space removed
     * @see #trimTrailing(String,char) <p/>
     *      All characters that have codes less than or equal to <code>'&#92;u0020'</code> (the space character) are
     *      considered to be white space.
     */
    public static String trimTrailing(String s) {
        if (s == null) {
            return null;
        }
        int len = s.length();
        int origLen = len;
        while ((len > 0) && (s.charAt(len - 1) <= ' ')) {
            len--;
        }
        return (len != origLen) ? s.substring(0, len) : s;
    }


    /**
     * trim trailing characters there are on a string matching a given character.
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param c the trailing character of interest, usually ' ' or '\n'
     * @return string with any of those trailing characters removed.
     * @see #trimTrailing(String)
     */
    public static String trimTrailing(String text, char c) {
        int count = countTrailing(text, c);
        // substring will optimise the 0 case.
        return text.substring(0, text.length() - count);
    }


    /**
     * trim trailing characters there are on a string matching given characters.
     * 
     * @param text text with possible trailing characters, possibly empty, but not null.
     * @param toTrim the trailing characters of interest, usually ' ' or '\n'
     * @return string with any of those trailing characters removed. ".com" would not only chop .com, but any combination
     *         of those letters e.g. mc.moc
     * @see #trimTrailing(String)
     * @see #chopTrailingString(String,String)
     */
    public static String trimTrailing(String text, String toTrim) {
        int count = countTrailing(text, toTrim);
        // substring will optimise the 0 case.
        return text.substring(0, text.length() - count);
    }

    // -------------------------- STATIC METHODS --------------------------

    static {
        // initialize vt Vowel Table
        vt.set('A');
        vt.set('E');
        vt.set('I');
        vt.set('O');
        vt.set('U');
        vt.set('a');
        vt.set('e');
        vt.set('i');
        vt.set('o');
        vt.set('u');
        vt.set('\u00c0', '\u00c6');
        vt.set('\u00c8', '\u00cf');
        vt.set('\u00d2', '\u00d6');
        vt.set('\u00d8', '\u00dc');
        vt.set('\u00e0', '\u00e6');
        vt.set('\u00e8', '\u00ef');
        vt.set('\u00f2', '\u00f6');
        vt.set('\u00f8', '\u00fc');
        vt.set('\u0100', '\u0105');
        vt.set('\u0112', '\u011b');
        vt.set('\u0128', '\u012f');
        vt.set('\u0130');
        vt.set('\u0132', '\u0133');
        vt.set('\u014c', '\u014f');
        vt.set('\u0150', '\u0153');
        vt.set('\u0168', '\u016f');
        vt.set('\u0170', '\u0173');
    }


    // --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Dummy constructor StringTools contains only static methods.
     */
    protected StringTools() {
    }


    // --------------------------- main() method ---------------------------

    /**
     * Test harness, used in debugging
     * 
     * @param args not used
     */
    public static void main(String[] args) {
        if (DEBUGGING) {
            System.out.println(">>condense");

            System.out.println(condense("   this  is   spaced.  "));

            System.out.println(">> trimLeading");

            System.out.println(trimLeading("*****t*r*i*m****", '*'));
            System.out.println(trimLeading("   trim   "));

            System.out.println(">> trimTrailing");

            System.out.println(trimTrailing("   trim   "));
            System.out.println(trimTrailing("*****t*r*i*m****", '*'));

            System.out.println(">> chopLeadingString");

            System.out.println(chopLeadingString("abcdefg", "abc"));

            System.out.println(">> chopTrailingString");

            System.out.println(chopTrailingString("say!", "!"));

            System.out.println(">> toHexString");

            System.out.println(toHexString(-3));
            System.out.println(toHexString(3));

            System.out.println(">> countLeading");

            System.out.println(countLeading("none", ' '));
            System.out.println(countLeading("*one***", '*'));
            System.out.println(countLeading(" abc ", " *"));
            System.out.println(countLeading(" *   * abc ", " *"));
            System.out.println(countLeading(" *   * abc ", "* "));
            System.out.println(countLeading("\n\ntw\n\n\no\n\n\n\n", '\n'));

            System.out.println(">> countTrailing");

            System.out.println(countTrailing("none", ' '));
            System.out.println(countTrailing("***one*", '*'));
            System.out.println(countTrailing("\n\n\n\nt\n\n\n\nwo\n\n", '\n'));
            System.out.println(countTrailing(" abc *  * ", " *"));
            System.out.println(countTrailing(" *   * abc  *  * ", " *"));
            System.out.println(countTrailing(" *   * abc  *  * ", "* "));

            System.out.println(">> quoteSQL");

            System.out.println(quoteSQL("Judy's Place"));

            System.out.println(">> parseLongPennies");

            System.out.println(parseLongPennies("$5.00"));
            System.out.println(parseLongPennies("$50"));
            System.out.println(parseLongPennies("50"));
            System.out.println(parseLongPennies("$50-"));

            System.out.println(">> penniesToString");

            System.out.println(penniesToString(0));
            System.out.println(penniesToString(-1));
            System.out.println(penniesToString(20));
            System.out.println(penniesToString(302));
            System.out.println(penniesToString(-100000));

            System.out.println(">> toBookTitleCase");

            System.out.println(toBookTitleCase("handbook to HIGHER consciousness"));
            System.out.println(toBookTitleCase("THE HISTORY OF THE U.S.A."));
            System.out.println(toBookTitleCase("THE HISTORY OF THE USA"));

            System.out.println(">> rightPad");

            System.out.println(rightPad("abc", 6, true) + "*");
            System.out.println(rightPad("abc", 2, true) + "*");
            System.out.println(rightPad("abc", 2, false) + "*");
            System.out.println(rightPad("abc", 3, true) + "*");
            System.out.println(rightPad("abc", 3, false) + "*");
            System.out.println(rightPad("abc", 0, true) + "*");
            System.out.println(rightPad("abc", 20, true) + "*");
            System.out.println(rightPad("abc", 29, true) + "*");
            System.out.println(rightPad("abc", 30, true) + "*");
            System.out.println(rightPad("abc", 31, true) + "*");
            System.out.println(rightPad("abc", 40, true) + "*");

            System.out.println(">> toUpperCase");

            System.out.println(toUpperCase('q'));
            System.out.println(toUpperCase('Q'));
            System.out.println(toUpperCase("The quick brown fox was 10 feet tall."));
            System.out.println(toUpperCase("THE QUICK BROWN FOX WAS 10 FEET TALL."));
            System.out.println(toUpperCase("the quick brown fox was 10 feet tall."));

            System.out.println(">> toLowerCase");

            System.out.println(toLowerCase('q'));
            System.out.println(toLowerCase('Q'));
            System.out.println(toLowerCase("The quick brown fox was 10 feet tall."));
            System.out.println(toLowerCase("THE QUICK BROWN FOX WAS 10 FEET TALL."));
            System.out.println(toLowerCase("the quick brown fox was 10 feet tall."));

            System.out.println(">> countInstances");

            System.out.println("count instances should be 4: " + countInstances(" abab abcdefgab", "ab"));
        }
    }
}
