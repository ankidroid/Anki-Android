package com.ichi2.libanki.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FuriganaFilters {
    private static final Pattern r = Pattern.compile(" ?([^\\[]+?)\\[(.+?)\\]([^ ]+?|$)");
    private static final String ruby = "<ruby><rb>\\1</rb><rt>\\2</rt></ruby>";
    
    public void install(Hooks h) {
        h.addHook("fmod_kanji", new Kanji());
        h.addHook("fmod_kana", new Kana());
        h.addHook("fmod_furigana", new Furigana());
    }
    
    private static String noSound(Matcher match, String repl) {
    	repl += "\\3";
        if (match.group(2).startsWith("sound:")) {
            // return without modification
            return match.group(0);
        } else {
            return r.matcher(match.group(0)).replaceAll(repl);
        }
    }
    
    public class Kanji extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String)arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, "$1"));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    public class Kana extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String)arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, "$2"));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    public class Furigana extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String)arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, ruby));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }
}
