package com.ichi2.libanki.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HebrewFixFilter extends Hook {
    /** Regex patterns used in identifying and fixing Hebrew words, so we can reverse them */
    private static final Pattern sHebrewPattern = Pattern.compile(
    // Two cases caught below:
    // Either a series of characters, starting from a hebrew character...
            "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]]" +
            // ...followed by hebrew characters, punctuation, parenthesis, spaces, numbers or numerical symbols...
                    "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:\"'\\[\\](){}+\\-*/%=0-9\\s]*" +
                    // ...and ending with hebrew character, punctuation or numerical symbol
                    "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:0-9%])|" +
                    // or just a single Hebrew character
                    "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]])");
    private static final Pattern sHebrewVowelsPattern = Pattern
            .compile("[[\\u0591-\\u05BD][\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]]");

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
//        Matcher m = sHebrewPattern.matcher(text);
//        StringBuffer sb = new StringBuffer();
//        while (m.find()) {
//            String hebrewText = m.group();
//            // Some processing before we reverse the Hebrew text
//            // 1. Remove all Hebrew vowels as they cannot be displayed properly
//            Matcher mv = sHebrewVowelsPattern.matcher(hebrewText);
//            hebrewText = mv.replaceAll("");
//            m.appendReplacement(sb, hebrewText);
//        }
//        m.appendTail(sb);
//        return sb.toString();
        return sHebrewVowelsPattern.matcher(text).replaceAll("");
    }
}
