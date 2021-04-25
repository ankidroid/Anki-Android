/****************************************************************************************
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>                              *
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

package com.ichi2.libanki.template;

import android.content.res.Resources;
import android.text.TextUtils;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.libanki.Utils;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * Port template_filters.rs
 */
public class TemplateFilters {

    public static final String CLOZE_DELETION_REPLACEMENT = "[...]";
    private static final Pattern fHookFieldMod = Pattern.compile("^(.*?)(?:\\((.*)\\))?$");
    public static final String CLOZE_REG = "(?si)\\{\\{(c)%s::(.*?)(::(.*?))?\\}\\}";


    /**
     * @param txt The content of the field field_name
     * @param filters a list of filter to apply to this text
     * @param field_name A name of a field
     * @param tag The entire part between {{ and }}
     * @return The result of applying each filter successively to txt
     */
    public static @NonNull String apply_filters(@NonNull String txt, @NonNull List<String> filters, @NonNull String field_name, @NonNull String tag) {
        for (String filter : filters) {
            txt = TemplateFilters.apply_filter(txt, filter, field_name, tag);
            if (txt == null) {
                txt = "";
            }
        }
        return txt;
    }


    /**
     * @param txt The current text the filter may change. It may be changed by multiple filter.
     * @param filter The name of the filter to apply.
     * @param field_name The name of the field whose text is shown
     * @param tag The entire content of the tag.
     * @return Result of filter on current txt.
     */
    protected static @Nullable String apply_filter(@NonNull String txt, @NonNull String filter, @NonNull String field_name, @NonNull String tag) {
        //Timber.d("Models.get():: Processing field: modifier=%s, extra=%s, tag=%s, txt=%s", mod, extra, tag, txt);
        // built-in modifiers
        if ("text".equals(filter)) {
            // strip html
            if (!TextUtils.isEmpty(txt)) {
                return Utils.stripHTML(txt);
            } else {
                return "";
            }
        } else if ("type".equals(filter)) {
            // type answer field; convert it to [[type:...]] for the gui code
            // to process
            return String.format(Locale.US, "[[%s]]", tag);
        } else if (filter.startsWith("cq-") || filter.startsWith("ca-")) {
            // cloze deletion
            String[] split = filter.split("-");
            filter = split[0];
            String extra = split[1];
            if (!TextUtils.isEmpty(txt) && !TextUtils.isEmpty(extra)) {
                return clozeText(txt != null ? txt : "", extra, filter.charAt(1));
            } else {
                return "";
            }
        } else {
            // hook-based field modifier
            Matcher m = fHookFieldMod.matcher(filter);
            if (m.matches()) {
                filter = m.group(1);
                String extra = m.group(2);
            }

            if (txt == null) {
                txt = "";
            }
            try {
                switch (filter) {
                    case "hint":
                        return runHint(txt, field_name);
                    case "kanji":
                        return FuriganaFilters.kanjiFilter(txt);
                    case "kana":
                        return FuriganaFilters.kanaFilter(txt);
                    case "furigana":
                        return FuriganaFilters.furiganaFilter(txt);
                    default:
                        return txt;
                }
            } catch (Exception e) {
                Timber.e(e, "Exception while running hook %s", filter);
                return AnkiDroidApp.getAppResources().getString(R.string.filter_error, filter);
            }
        }
    }


    private static String runHint(String txt, String tag) {
        if (txt.trim().length() == 0) {
            return "";
        }
        Resources res = AnkiDroidApp.getAppResources();
        // random id
        String domid = "hint" + txt.hashCode();
        return "<a class=hint href=\"#\" onclick=\"this.style.display='none';document.getElementById('" +
                domid + "').style.display='block';_relinquishFocus();return false;\">" +
                res.getString(R.string.show_hint, tag) + "</a><div id=\"" +
                domid + "\" class=hint style=\"display: none\">" + txt + "</div>";
    }


    private static @NonNull String clozeText(@NonNull String txt, @NonNull String ord, char type) {
        if (!Pattern.compile(String.format(Locale.US, CLOZE_REG, ord)).matcher(txt).find()) {
            return "";
        }

        txt = removeFormattingFromMathjax(txt, ord);
        Matcher m = Pattern.compile(String.format(Locale.US, CLOZE_REG, ord)).matcher(txt);

        StringBuffer repl = new StringBuffer();
        while (m.find()) {
            // replace chosen cloze with type
            String buf;
            if (type == 'q') {
                if (!TextUtils.isEmpty(m.group(4))) {
                    buf = "[" + m.group(4) + "]";
                } else {
                    buf = CLOZE_DELETION_REPLACEMENT;
                }
            } else {
                buf = m.group(2);
            }

            if ("c".equals(m.group(1))) {
                buf = String.format("<span class=cloze>%s</span>", buf);
            }

            m.appendReplacement(repl, Matcher.quoteReplacement(buf));
        }
        txt = m.appendTail(repl).toString();
        // and display other clozes normally
        return txt.replaceAll(String.format(Locale.US, CLOZE_REG, "\\d+"), "$2");
    }

    /**
     * Marks all clozes within MathJax to prevent formatting them.
     *
     * Active Cloze deletions within MathJax should not be wrapped inside
     * a Cloze <span>, as that would interfere with MathJax. This method finds
     * all Cloze deletions number `ord` in `txt` which are inside MathJax inline
     * or display formulas, and replaces their opening '{{c123' with a '{{C123'.
     * The clozeText method interprets the upper-case C as "don't wrap this
     * Cloze in a <span>".
     */
    public static @NonNull String removeFormattingFromMathjax(@NonNull String txt, @NonNull String ord) {
        String creg = CLOZE_REG.replace("(?si)", "");
        // Scan the string left to right.
        // After a MathJax opening - \( or \[ - flip in_mathjax to True.
        // After a MathJax closing - \) or \] - flip in_mathjax to False.
        // When a Cloze pattern number `ord` is found and we are in MathJax,
        // replace its '{{c' with '{{C'.
        //
        // TODO: Report mismatching opens/closes - e.g. '\(\]'
        // TODO: Report errors in this method better than printing to stdout.
        // flags in middle of expression deprecated
        boolean in_mathjax = false;

        // The following regex matches one of 3 things, noted below:
        String regex = "(?si)" +
                "(\\\\[(\\[])|" +  // group 1, MathJax opening
                "(\\\\[])])|" +  // group 2, MathJax close
                "(" +              // group 3, Cloze deletion number `ord`
                String.format(Locale.US, creg, ord) +
                ")";

        Matcher m = Pattern.compile(regex).matcher(txt);

        StringBuffer repl = new StringBuffer();
        while (m.find()) {
            if (m.group(1) != null) {
                if (in_mathjax) {
                    Timber.d("MathJax opening found while already in MathJax");
                }
                in_mathjax = true;
            } else if (m.group(2) != null) {
                if (!in_mathjax) {
                    Timber.d("MathJax close found while not in MathJax");
                }
                in_mathjax = false;
            } else if (m.group(3) != null) {
                if (in_mathjax) {
                    // appendReplacement has an issue with backslashes, so...
                    m.appendReplacement(
                            repl,
                            Matcher.quoteReplacement(
                                    m.group(0).replace(
                                            "{{c" + ord + "::", "{{C" + ord + "::")));
                    continue;
                }
            } else {
                Timber.d("Unexpected: no expected capture group is present");
            }
            // appendReplacement has an issue with backslashes, so...
            m.appendReplacement(repl, Matcher.quoteReplacement(m.group(0)));
        }
        return m.appendTail(repl).toString();
    }

}
