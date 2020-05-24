/****************************************************************************************
 * Copyright (c) 2015 Houssam Salem <houssam.salem.au@gmail.com>                        *
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

/**
 * This class renders the card content by parsing the card template and replacing all marked sections
 * and tags with their respective data. The data is derived from a context object that is given to
 * the class when constructed which maps tags to the data that they should be replaced with.
 * <p/>
 * The AnkiDroid version of this class makes some assumptions about the valid data types that flow
 * through it and is thus simplified. Namely, the context is assumed to always be a Map<String, String>,
 * and sections are only ever considered to be String objects. Tests have shown that strings are the
 * only data type used, and thus code that handles anything else has been omitted.
 *
 * The AnkiDroid version of this also provides a containsMathjax method.
 */
@SuppressWarnings({"PMD.AvoidReassigningParameters","PMD.NPathComplexity","PMD.MethodNamingConventions"})
public class Template {
    public static final String clozeReg = "(?si)\\{\\{(c)%s::(.*?)(::(.*?))?\\}\\}";
    public static final String CLOZE_DELETION_REPLACEMENT = "[...]";

    private static final Pattern fHookFieldMod = Pattern.compile("^(.*?)(?:\\((.*)\\))?$");

    // Opening tag delimiter
    private static final String sOtag = Pattern.quote("{{");

    // Closing tag delimiter
    private static final String sCtag = Pattern.quote("}}");

    // The regular expression used to find a #section
    private static final Pattern sSection_re = Pattern.compile(sOtag + "[#|^]([^}]*)" + sCtag + "(.+?)" + sOtag + "/\\1" + sCtag, Pattern.MULTILINE | Pattern.DOTALL);

    // The regular expression used to find a tag.
    private static final Pattern sTag_re = Pattern.compile(sOtag + "([#=&!>{])?(.+?)\\1?" + sCtag + "+");

    // MathJax opening delimiters
    private static String[] sMathJaxOpenings = {"\\(", "\\["};

    // MathJax closing delimiters
    private static String[] sMathJaxClosings = {"\\)", "\\]"};

    private String mTemplate;
    private Map<String, String> mContext;


    private static @Nullable String get_or_attr(Map<String, String> obj, String name) {
        if (obj.containsKey(name)) {
            return obj.get(name);
        } else {
            return null;
        }
    }


    public Template(@NonNull String template, @Nullable Map<String, String> context) {
        mTemplate = template;
        mContext = context == null ? new HashMap<>() : context;
    }


    /**
     * Turns a Mustache template into something wonderful.
     */
    public @NonNull String render() {
        String template = render_sections(mTemplate, mContext);
        return render_tags(template, mContext);
    }

    /**
     * Expands sections.
     */
    private @NonNull String render_sections(@NonNull String template, @NonNull Map<String, String> context) {
        /* Apply render_some_section to the templates, until
           render_some_section states that it does not find sections
           anymore. Return the last template found. */
        String previous_template = null;
        while (template != null) {
            previous_template = template;
            template = render_some_sections(template, context);
        }
        return previous_template;
    }

    /** Deal with conditionals that are found. If no conditionals are
     * found, return null.

     It is not guaranteed that are conditionals are found. For example, on
     {{#field1}}
       {{#field2}}
     {{/field1}}
       {{/field2}}, the regexp only finds {{field1}} and ignore {{field2}}.

     Note that all conditionals are found, unless a conditional
     appears inside itself, or conditionals are not properly
     closed. Both cases leads to error for some values of fields so
     should not appear in template anyways.

     If some change is done, the function should be called again to
     remove those new pairs of conditionals.
     */
    private @Nullable String render_some_sections(@NonNull String template, @NonNull Map<String, String> context) {
        StringBuffer sb = new StringBuffer();
        Matcher match = sSection_re.matcher(template);
        boolean found = false;
        while (match.find()) {
            found = true;
            String section = match.group(0);
            String section_name = match.group(1).trim();
            String inner = match.group(2);
            String it = get_or_attr(context, section_name);
            boolean field_is_empty =  it == null || TextUtils.isEmpty(Utils.stripHTMLMedia(it).trim());
            boolean conditional_is_negative = section.charAt(2) == '^';
            // Showing inner content if either field is empty and the
            // conditional is a ^; or if the field is non-empty and
            // the conditional is not ^.
            boolean show_inner = field_is_empty == conditional_is_negative;
            String replacer = (show_inner) ? inner : "";
            match.appendReplacement(sb, Matcher.quoteReplacement(replacer));
        }
        if (!found) {
            // There were no replacement. We can halt the computation
            return null;
        }
        match.appendTail(sb);
        return sb.toString();
    }

    /**
     * Expands all tags, iteratively until all tags (even tags that are replaced by tags) are resolved.
     */
    private @NonNull String render_tags(@NonNull String template, @NonNull Map<String, String> context) {
        /* Apply render_some_tags to the tags, until
           render_some_tags states that it does not find tags to replace anymore
           anymore. Return the last template state */
        String previous_template = null;
        while (template != null) {
            previous_template = template;
            template = render_some_tags(template, context);
        }
        return previous_template;
    }

    /**
     * Replaces all the tags in a template in a single pass for the values in the given context map.
     */
    private @Nullable String render_some_tags(@NonNull String template, @NonNull Map<String, String> context) {
        String ALT_HANDLEBAR_DIRECTIVE = "{{=<% %>=}}";
        if (template.contains(ALT_HANDLEBAR_DIRECTIVE)) {
            template = template.replace(ALT_HANDLEBAR_DIRECTIVE, "").replace("<%", "{{").replace("%>", "}}");
        }
        StringBuffer sb = new StringBuffer();
        Matcher match = sTag_re.matcher(template);
        boolean found = false;
        while (match.find()) {
            found = true;
            String tag_type = match.group(1);
            String tag_name = match.group(2).trim();
            String replacement;
            if (tag_type == null) {
                replacement = render_unescaped(tag_name, context);
            } else if ("{".equals(tag_type)) {
                replacement = render_tag(tag_name, context);
            } else if ("!".equals(tag_type)) {
                replacement = render_comment();
            } else {
                return "{{invalid template}}";
            }
            match.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return null;
        }
        match.appendTail(sb);
        return sb.toString();
    }

    /**
     * {{{ functions just like {{ in anki
     */
    private @NonNull String render_tag(@NonNull String tag_name, @NonNull Map<String, String> context) {
        return render_unescaped(tag_name, context);
    }


    /**
     * Rendering a comment always returns nothing.
     */
    private String render_comment() {
        return "";
    }

    private @NonNull String render_unescaped(@NonNull String tag_name, @NonNull Map<String, String> context) {
        String txt = get_or_attr(context, tag_name);
        if (txt != null) {
            // some field names could have colons in them
            // avoid interpreting these as field modifiers
            // better would probably be to put some restrictions on field names
            return txt;
        }

        // field modifiers
        List<String> parts = Arrays.asList(tag_name.split(":"));
        String extra = null;
        List<String> mods;
        String tag;
        if (parts.size() == 1 || "".equals(parts.get(0))) {
            return String.format("{unknown field %s}", tag_name);
        } else {
            mods = parts.subList(0, parts.size() - 1);
            tag = parts.get(parts.size() - 1);
        }

        txt = get_or_attr(context, tag);

        // Since 'text:' and other mods can affect html on which Anki relies to
        // process clozes, we need to make sure clozes are always
        // treated after all the other mods, regardless of how they're specified
        // in the template, so that {{cloze:text: == {{text:cloze:
        // For type:, we return directly since no other mod than cloze (or other
        // pre-defined mods) can be present and those are treated separately
        Collections.reverse(mods);
        // This comparator ensures "type:" mods are ordered first in the list. The rest of
        // the list remains in the same order.
        //noinspection ComparatorMethodParameterNotUsed
        Collections.sort(mods, (lhs, rhs) -> {
            if ("type".equals(lhs)) {
                return 0;
            } else {
                return 1;
            }
        });

        for (String mod : mods) {
            //Timber.d("Models.get():: Processing field: modifier=%s, extra=%s, tag=%s, txt=%s", mod, extra, tag, txt);
            // built-in modifiers
            if ("text".equals(mod)) {
                // strip html
                if (!TextUtils.isEmpty(txt)) {
                    txt = Utils.stripHTML(txt);
                } else {
                    txt = "";
                }
            } else if ("type".equals(mod)) {
                // type answer field; convert it to [[type:...]] for the gui code
                // to process
                return String.format(Locale.US, "[[%s]]", tag_name);
            } else if (mod.startsWith("cq-") || mod.startsWith("ca-")) {
                // cloze deletion
                String[] split = mod.split("-");
                mod = split[0];
                extra = split[1];
                if (!TextUtils.isEmpty(txt) && !TextUtils.isEmpty(extra)) {
                    txt = clozeText(txt != null ? txt : "", extra, mod.charAt(1));
                } else {
                    txt = "";
                }
            } else {
                // hook-based field modifier
                Matcher m = fHookFieldMod.matcher(mod);
                if (m.matches()) {
                    mod = m.group(1);
                    extra = m.group(2);
                }

                if (txt == null) {
                    txt = "";
                }
                try {
                    switch (mod) {
                    case "hint" :
                        txt = runHint(txt, tag);
                        break;
                    case "kanji" :
                        txt = FuriganaFilters.kanjiFilter(txt);
                        break;
                    case "kana" :
                        txt = FuriganaFilters.kanaFilter(txt);
                        break;
                    case "furigana" :
                        txt = FuriganaFilters.furiganaFilter(txt);
                        break;
                    default :
                        break;
                    }
                } catch (Exception e) {
                    Timber.e(e, "Exception while running hook %s", mod);
                    return "Error in filter " + mod;
                }
                if (txt == null) {
                    return String.format("{unknown field %s}", tag_name);
                }
            }
        }
        return txt != null ? txt : "";
    }

    private String runHint(String txt, String tag) {
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
        if (!Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(txt).find()) {
            return "";
        }

        txt = removeFormattingFromMathjax(txt, ord);
        Matcher m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(txt);

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
        return txt.replaceAll(String.format(Locale.US, clozeReg, "\\d+"), "$2");
    }

    public static boolean textContainsMathjax(@NonNull String txt) {
        // Do you have the first opening and then the first closing,
        // or the second opening and the second closing...?

        //This assumes that the openings and closings are the same length.

        String opening;
        String closing;
        for (int i = 0; i < sMathJaxOpenings.length; i++) {
            opening = sMathJaxOpenings[i];
            closing = sMathJaxClosings[i];

            //What if there are more than one thing?
            //Let's look for the first opening, and the last closing, and if they're in the right order,
            //we are good.

            int first_opening_index = txt.indexOf(opening);
            int last_closing_index = txt.lastIndexOf(closing);

            if (first_opening_index != -1
                    && last_closing_index != -1
                    && first_opening_index < last_closing_index)
            {
                return true;
            }
        }
        return false;
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
        String creg = clozeReg.replace("(?si)", "");
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
