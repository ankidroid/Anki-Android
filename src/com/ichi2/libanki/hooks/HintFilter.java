
package com.ichi2.libanki.hooks;

import java.util.Locale;

public class HintFilter {
    public void install(Hooks h) {
        h.addHook("fmod_hint", new Hint());
    }
    
    public class Hint extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            String txt = (String) arg;
            if (txt.trim().length() == 0) {
                return "";
            }
            String tag = (String) args[2];
            // random id
            String domid = String.format(Locale.US, "hint%d", txt.hashCode());
            return String.format(Locale.US, "<a href=\"#\" onclick=\"" +
            		"this.style.display='none';document.getElementById('%s').style.display='block';" +
            		"return false;\">%s</a><div id=\"%s\" style=\"display: none\">%s</div>",
            		domid, String.format(Locale.US, "Show %s", tag), domid, txt);
        }
    }
}
