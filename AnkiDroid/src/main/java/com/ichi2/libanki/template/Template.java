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

import android.text.TextUtils;

import com.ichi2.libanki.Utils;
import com.ichi2.libanki.hooks.Hooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class renders the card content by parsing the card template and replacing all marked sections
 * and tags with their respective data. The data is derived from a context object that is given to
 * the class when constructed which maps tags to the data that they should be replaced with.
 * <p/>
 * The AnkiDroid version of this class makes some assumptions about the valid data types that flow
 * through it and is thus simplified. Namely, the context is assumed to always be a Map<String, String>,
 * and sections are only ever considered to be String objects. Tests have shown that strings are the
 * only data type used, and thus code that handles anything else has been omitted.
 */
@SuppressWarnings({"PMD.AvoidReassigningParameters","PMD.NPathComplexity","PMD.MethodNamingConventions"})
public class Template {
    public static final String clozeReg = "(?s)\\{\\{c%s::(.*?)(::(.*?))?\\}\\}";
    private static final Pattern fHookFieldMod = Pattern.compile("^(.*?)(?:\\((.*)\\))?$");
    private static final Pattern fClozeSection = Pattern.compile("c[qa]:(\\d+):(.+)");

    // The regular expression used to find a #section
    private Pattern sSection_re = null;

    // The regular expression used to find a tag.
    private Pattern sTag_re = null;

    // Opening tag delimiter
    private String sOtag = "{{";

    // Closing tag delimiter
    private String sCtag = "}}";

    private String mTemplate;
    private Map<String, String> mContext;

    private static String get_or_attr(Map<String, String> obj, String name) {
        return get_or_attr(obj, name, null);
    }

    private static String get_or_attr(Map<String, String> obj, String name, String _default) {
        if (obj.containsKey(name)) {
            return obj.get(name);
        } else {
            return _default;
        }
    }


    public Template(String template, Map<String, String> context) {
        mTemplate = template;
        mContext = context == null ? new HashMap<String, String>() : context;
        compile_regexps();
    }


    /**
     * Turns a Mustache template into something wonderful.
     */
    public String render() {
        String template = render_sections(mTemplate, mContext);
        return render_tags(template, mContext);
    }

    /**
     * Compiles our section and tag regular expressions.
     */
    private void compile_regexps() {
        String otag = Pattern.quote(sOtag);
        String ctag = Pattern.quote(sCtag);

        String section = String.format(Locale.US,
                "%s[\\#|^]([^\\}]*)%s(.+?)%s/\\1%s", otag, ctag, otag, ctag);
        sSection_re = Pattern.compile(section, Pattern.MULTILINE | Pattern.DOTALL);

        String tag = String.format(Locale.US, "%s(#|=|&|!|>|\\{)?(.+?)\\1?%s+", otag, ctag);
        sTag_re = Pattern.compile(tag);
    }

    /**
     * Expands sections.
     */
    private String render_sections(String template, Map<String, String> context) {
        while (true) {
            Matcher match = sSection_re.matcher(template);
            if (!match.find()) {
                break;
            }

            String section = match.group(0);
            String section_name = match.group(1);
            String inner = match.group(2);
            section_name = section_name.trim();
            String it;

            // check for cloze
            Matcher m = fClozeSection.matcher(section_name);
            if (m.find()) {
                // get full field text
                String txt = get_or_attr(context, m.group(2), null);
                Matcher mm = Pattern.compile(String.format(clozeReg, m.group(1))).matcher(txt);
                if (mm.find()) {
                    it = mm.group(1);
                } else {
                    it = null;
                }
            } else {
                it = get_or_attr(context, section_name, null);
            }
            String replacer = "";
            if (!TextUtils.isEmpty(it)) {
                it = Utils.stripHTMLMedia(it).trim();
            }
            if (!TextUtils.isEmpty(it)) {
                if (section.charAt(2) != '^') {
                    replacer = inner;
                }
            } else if (TextUtils.isEmpty(it) && section.charAt(2) == '^') {
                replacer = inner;
            }
            template = template.replace(section, replacer);
        }
        return template;
    }


    /**
     * Renders all the tags in a template for a context.
     */
    private String render_tags(String template, Map<String, String> context) {
        while (true) {
            Matcher match = sTag_re.matcher(template);
            if (!match.find()) {
                break;
            }

            String tag = match.group(0);
            String tag_type = match.group(1);
            String tag_name = match.group(2).trim();
            String replacement;
            if (tag_type == null) {
                replacement = render_unescaped(tag_name, context);
            } else if ("{".equals(tag_type)) {
                replacement = render_tag(tag_name, context);
            } else if ("!".equals(tag_type)) {
                replacement = render_comment();
            } else if ("=".equals(tag_type)) {
                replacement = render_delimiter(tag_name);
            } else {
                return "{{invalid template}}";
            }
            template = template.replace(tag, replacement);
        }
        return template;
    }

    /**
     * {{{ functions just like {{ in anki
     */
    private String render_tag(String tag_name, Map<String, String> context) {
        return render_unescaped(tag_name, context);
    }


    /**
     * Rendering a comment always returns nothing.
     */
    private String render_comment() {
        return "";
    }

    private String render_unescaped(String tag_name, Map<String, String> context) {
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
        Collections.sort(mods, new Comparator<String>() {
            // This comparator ensures "type:" mods are ordered first in the list. The rest of
            // the list remains in the same order.
            @Override
            public int compare(String lhs, String rhs) {
                if ("type".equals(lhs)) {
                    return 0;
                } else {
                    return 1;
                }
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
                    txt = clozeText(txt, extra, mod.charAt(1));
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

                txt = (String) Hooks.runFilter("fmod_" + mod,
                        txt == null ? "" : txt,
                        extra == null ? "" : extra,
                        context, tag, tag_name);
                if (txt == null) {
                    return String.format("{unknown field %s}", tag_name);
                }
            }
        }
        return txt;
    }

    private static String clozeText(String txt, String ord, char type) {
        Matcher m = Pattern.compile(String.format(Locale.US, clozeReg, ord)).matcher(txt);
        if (!m.find()) {
            return "";
        }
        m.reset();
        StringBuffer repl = new StringBuffer();
        while (m.find()) {
            // replace chosen cloze with type
            if (type == 'q') {
                if (!TextUtils.isEmpty(m.group(3))) {
                    m.appendReplacement(repl, "<span class=cloze>[$3]</span>");
                } else {
                    m.appendReplacement(repl, "<span class=cloze>[...]</span>");
                }
            } else {
                m.appendReplacement(repl, "<span class=cloze>$1</span>");
            }
        }
        txt = m.appendTail(repl).toString();
        // and display other clozes normally
        return txt.replaceAll(String.format(Locale.US, clozeReg, "\\d+"), "$1");
    }

    /**
     * Changes the Mustache delimiter.
     */
    private String render_delimiter(String tag_name) {
        try {
            String[] split = tag_name.split(" ");
            sOtag = split[0];
            sCtag = split[1];
        } catch (IndexOutOfBoundsException e) {
            // invalid
            return null;
        }
        compile_regexps();
        return "";
    }
}
