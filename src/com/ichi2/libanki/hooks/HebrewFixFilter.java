
package com.ichi2.libanki.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HebrewFixFilter extends Hook {
    /** Regex patterns used in identifying and fixing Hebrew words, so we can reverse them */
    private static final Pattern sHebrewPattern = Pattern.compile(
    // Either a series of characters, starting from a hebrew character...
            "([\\u0591-\\u05F4\\uFB1D-\\uFB4F]" +
    // ...followed by hebrew characters, punctuation and spaces...
            "[\\u0591-\\u05F4\\uFB1D-\\uFB4F,.?!;:\"'\\s]*" +
    // ...and ending with hebrew character or punctuation
            "[\\u0591-\\u05F4\\uFB1D-\\uFB4F,.?!;:]|" +
    // or just a single Hebrew character
            "[\\u0591-\\u05F4\\uFB1D-\\uFB4F])");

    @Override
    public Object runFilter(Object arg, Object... args) {
        return applyFixForHebrew((String) arg);
    }


    public static void install(Hooks h) {
        h.addHook("mungeQA", new HebrewFixFilter());
    }


    public static void uninstall(Hooks h) {
        h.remHook("mungeQA", new HebrewFixFilter());
    }


    private String applyFixForHebrew(String text) {
        Matcher m = sHebrewPattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String hebrewText = m.group();
            String reversed = new StringBuffer(hebrewText).reverse().toString();
            String translated = translate(reversed);
            m.appendReplacement(sb, "<span style=\"font-family:Tohu;\">" + translated + "</span>");
        }
        m.appendTail(sb);

        return sb.toString();
    }


    /**
     * Translates sections of Hebrew (RTL) unicode into western locations to bypass the 
     * flakey Android BiDi algorithm. This is necessary if the text includes Hebrew vowels.
     * This is supposed to be used in conjuction with a specially modified font Tohu.ttf,
     * see ankidroid forum for more details:
     * https://groups.google.com/forum/?fromgroups#!topic/anki-android/n9JpDiQ_dgU
     * 
     * @param text Hebrew text
     * @return text in the Western (LTR) alphabet and punctuation range, starting in the extended range
     */
    String translate(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int codePoint = 0;

        for (int i = 0; i < text.length(); i++) {
            codePoint = text.codePointAt(i);

            if (codePoint >= 1424 && codePoint < 1536) {
                // Hebrew letters and punctuation
                sb.append(String.valueOf((char) (codePoint - 400)));
            } else if (codePoint >= 64281 && codePoint < 64336) {
                // Hebrew compound forms and ligatures
                sb.append(String.valueOf((char) (codePoint - 63138)));
            } else if (codePoint >= 59393 && codePoint < 59398) {
                // Some characters from the Private Use Area
                sb.append(String.valueOf((char) (codePoint - 58257)));
            } else if (codePoint >= 59408 && codePoint < 59410) {
                // Some more characters from the Private Use Area
                sb.append(String.valueOf((char) (codePoint - 58267)));
            } else if (codePoint >= 1114131 && codePoint < 1114132) {
                // One last "straggler"
                sb.append(String.valueOf((char) (codePoint - 1112933)));
            } else { // anything not in range, leave as it is
                sb.append((text.charAt(i)));
            }
        }
        return sb.toString();
    }
}
