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

    // Opening tag delimiter
    protected static final String sOtag = Pattern.quote("{{");

    // Closing tag delimiter
    protected static final String sCtag = Pattern.quote("}}");

    public static final Pattern sTag_re = Pattern.compile(sOtag + "([#=&>{])?(.+?)\\1?" + sCtag + "+");

    // The regular expression used to find a #section
    private static final Pattern sSection_re = Pattern.compile(sOtag + "[#^]([^}]*)" + sCtag + "(.+?)" + sOtag + "/\\1" + sCtag, Pattern.MULTILINE | Pattern.DOTALL);

    // The regular expression used to find a tag.

    private final String mTemplate;
    private final Map<String, String> mContext;


    private static @Nullable String get_or_attr(Map<String, String> obj, String name) {
        if (obj.containsKey(name)) {
            return obj.get(name);
        } else {
            return null;
        }
    }


    public Template(@NonNull String template, @Nullable Map<String, String> context) {
        mTemplate = template;
        mContext = context == null ? new HashMap<>(0) : context;
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
     * Replaces all the tags in a template in a single pass for the values in the given context map.
     */
    private @NonNull String render_tags(@NonNull String template, @NonNull Map<String, String> context) {
        String ALT_HANDLEBAR_DIRECTIVE = "{{=<% %>=}}";
        if (template.contains(ALT_HANDLEBAR_DIRECTIVE)) {
            template = template.replace(ALT_HANDLEBAR_DIRECTIVE, "").replace("<%", "{{").replace("%>", "}}");
        }
        StringBuffer sb = new StringBuffer();
        Matcher match = sTag_re.matcher(template);
        while (match.find()) {
            String tag_type = match.group(1);
            String tag_name = match.group(2).trim();
            String replacement;
            if (tag_type == null) {
                replacement = render_unescaped(tag_name, context);
            } else if ("{".equals(tag_type)) {
                replacement = render_tag(tag_name, context);
            } else {
                return AnkiDroidApp.getAppResources().getString(R.string.invalid_template_short);
            }
            match.appendReplacement(sb, Matcher.quoteReplacement(replacement));
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
        // field modifiers
        List<String> parts = Arrays.asList(tag_name.split(":"));
        List<String> mods = parts.subList(0, parts.size() - 1);
        String tag = parts.get(parts.size() - 1);

        String txt = get_or_attr(context, tag);
        if (txt == null) {
            return AnkiDroidApp.getAppResources().getString(R.string.unknown_field, tag_name);
        }

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
        return TemplateFilters.apply_filters(txt, mods, tag_name);
    }
}
