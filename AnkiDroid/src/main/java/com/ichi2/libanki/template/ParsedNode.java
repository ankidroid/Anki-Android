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

import android.content.Context;
import android.util.Pair;

import com.ichi2.anki.R;
import com.ichi2.libanki.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import timber.log.Timber;

/**
 * Represents a template, allow to check in linear time which card is empty/render card.
 */
public abstract class ParsedNode {

    public static final String TEMPLATE_ERROR_LINK =
            "https://anki.tenderapp.com/kb/problems/card-template-has-a-problem";
    public static final String TEMPLATE_BLANK_LINK =
            "https://anki.tenderapp.com/kb/card-appearance/the-front-of-this-card-is-blank";
    public static final String TEMPLATE_BLANK_CLOZE_LINK =
            "https://anki.tenderapp.com/kb/problems/no-cloze-found-on-card";

    /**
     * @param nonempty_fields A set of fields that are not empty
     * @return Whether the card is empty. I.e. no non-empty fields are shown
     */
    public abstract boolean template_is_empty(Set<String> nonempty_fields);

    // Used only fot testing
    @VisibleForTesting
    public boolean template_is_empty(String... nonempty_fields) {
        return template_is_empty(new HashSet<>(Arrays.asList(nonempty_fields)));
    }

    public abstract void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) throws TemplateError;


    /**
     * Associate to each template its node, or the error it generates
     */
    private static WeakHashMap<String, Pair<ParsedNode, TemplateError>> parse_inner_cache = new WeakHashMap<>();

    /**
     * @param template A question or answer template
     * @return A tree representing the template.
     * @throws TemplateError if the template is not valid
     */
    public static @NonNull ParsedNode parse_inner(@NonNull String template) throws TemplateError{
        if (!parse_inner_cache.containsKey(template)) {
            Pair<ParsedNode, TemplateError> res;
            try {
                ParsedNode node = parse_inner(new Tokenizer(template));
                res = new Pair<>(node, null);
            } catch (TemplateError er) {
                res = new Pair<>(null, er);
            }
            parse_inner_cache.put(template, res);
        }
        Pair<ParsedNode, TemplateError> res = parse_inner_cache.get(template);
        if (res.first != null) {
            return res.first;
        }
        throw res.second;
    }

    /**
     * @param tokens An iterator returning a list of token obtained from a template
     * @return A tree representing the template
     * @throws TemplateError Any reason meaning the data is not valid as a template.
     */
    protected static @NonNull ParsedNode parse_inner(@NonNull Iterator<Tokenizer.Token> tokens) throws TemplateError{
        return parse_inner(tokens, null);
    }

    /**
     * @param tokens An iterator returning a list of token obtained from a template
     * @param open_tag The last opened tag that is not yet closed, or null
     * @return A tree representing the template, or nulll if no text can be generated.
     * @throws TemplateError Any reason meaning the data is not valid as a template.
     */
    private static @Nullable ParsedNode parse_inner(@NonNull Iterator<Tokenizer.Token> tokens, @Nullable String open_tag) throws TemplateError{
        List<ParsedNode> nodes = new ArrayList<>();
        while (tokens.hasNext()) {
            Tokenizer.Token token = tokens.next();
            switch (token.getKind()) {
                case TEXT: {
                    nodes.add(new Text(token.getText()));
                    break;
                }
                case REPLACEMENT: {
                    String[] it = token.getText().split(":", -1);
                    String key = it[it.length - 1];
                    List<String> filters = new ArrayList<>(it.length - 1);
                    for (int i = it.length - 2; i >= 0; i--) {
                        filters.add(it[i]);
                    }
                    nodes.add(new Replacement(key, filters, token.getText()));
                    break;
                }
                case OPEN_CONDITIONAL: {
                    String tag = token.getText();
                    nodes.add(new Conditional(tag, parse_inner(tokens, tag)));
                    break;
                }
                case OPEN_NEGATED: {
                    String tag = token.getText();
                    nodes.add(new NegatedConditional(tag, parse_inner(tokens, tag)));
                    break;
                }
                case CLOSE_CONDITIONAL: {
                    String tag = token.getText();
                    if (open_tag == null) {
                        throw new TemplateError.ConditionalNotOpen(tag);
                    }
                    if (!tag.equals(open_tag)) { // open_tag may be null, tag is not
                        throw new TemplateError.WrongConditionalClosed(tag, open_tag);
                    } else {
                        return ParsedNodes.create(nodes);
                    }
                }
            }
        }
        if (open_tag != null) {
            throw new TemplateError.ConditionalNotClosed(open_tag);
        }
        return ParsedNodes.create(nodes);
    }

    public @NonNull String render(Map<String, String> fields, boolean question, Context context) {
        try {
            StringBuilder builder = new StringBuilder();
            render_into(fields, Utils.nonEmptyFields(fields), builder);
            return builder.toString();
        } catch (TemplateError er) {
            Timber.w(er);
            String side = (question)? context.getString(R.string.card_template_editor_front): context.getString(R.string.card_template_editor_back);
            String explanation = context.getString(R.string.has_a_problem, side, er.message(context));
            String more_explanation = "<a href=\""+ TEMPLATE_ERROR_LINK+"\">" + context.getString(R.string.more_information) + "</a>";
            return explanation + "<br/>" + more_explanation;
        }
    }
}
