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

import java.util.Iterator;
import java.util.NoSuchElementException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.ichi2.libanki.template.Tokenizer.TokenKind.CLOSE_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_NEGATED;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.REPLACEMENT;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.TEXT;

/**
 * This class encodes template.rs's file creating template.
 * Due to the way iterator work in java, it's easier for the class to keep track of the template
 */
public class Tokenizer implements Iterator<Tokenizer.Token> {
    /** 
     * If this text appears at the top of a template (not considering whitespaces and other \s symbols), then the
     * template accept legacy handlebars. That is <% foo %> is interpreted similarly as {{ foo }}.
     * This is used for compatibility with legacy version of anki.
     *
     * Same as rslib/src/template's ALT_HANDLEBAR_DIRECTIVE upstream*/
    @VisibleForTesting
    public final static String ALT_HANDLEBAR_DIRECTIVE = "{{=<% %>=}}";

    /**
     * The remaining of the string to read.
     */
    private @NonNull String mTemplate;
    /**
     * Become true if lexing failed. That is, the string start with {{, but no }} is found.
     */
    private boolean mFailed = false;

    /**
     * Whether we consider <% and %> as handlebar
     */
    private final boolean mLegacy;


    /**
     * @param template A question or answer template.
     */
    Tokenizer(@NonNull String template) {
        String trimmed = template.trim();
        mLegacy = trimmed.startsWith(ALT_HANDLEBAR_DIRECTIVE);
        if (mLegacy) {
            template = trimmed.substring(ALT_HANDLEBAR_DIRECTIVE.length());
        }
        mTemplate = template;
    }


    @Override
    public boolean hasNext() {
        return mTemplate.length() > 0 && !mFailed;
    }


    /**
     * The kind of data we can find in a template and may want to consider for card generation
     */
    enum TokenKind {
        /**
         * Some text, assumed not to contains {{*}}
         */
        TEXT,
        /**
         * {{Field name}}
         */
        REPLACEMENT,
        /**
         * {{#Field name}}
         */
        OPEN_CONDITIONAL,
        /**
         * {{^Field name}}
         */
        OPEN_NEGATED,
        /**
         * {{/Field name}}
         */
        CLOSE_CONDITIONAL
    }



    /**
     * This is equivalent to upstream's template.rs's Token type.
     *
     */
    @VisibleForTesting
    protected static class Token {
        private final TokenKind mKind;
        /**
         * If mKind is Text, then this contains the text.
         * Otherwise, it contains the content between "{{" and "}}", without the curly braces.
         */
        private final String mText;


        public Token(TokenKind kind, String text) {
            mKind = kind;
            mText = text;
        }


        @NonNull
        @Override
        public String toString() {
            return mKind + "(\"" + mText + "\")";
        }


        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Token)) {
                return false;
            }
            Token t = ((Token) obj);
            return mKind == t.mKind && mText.equals(t.mText);
        }

        public @NonNull TokenKind getKind() {
            return mKind;
        }

        public @NonNull String getText() {
            return mText;
        }

        @VisibleForTesting
        public @NonNull Token new_to_legacy() {
            return new Token(mKind, Tokenizer.new_to_legacy(mText));
        }
    }

    @VisibleForTesting
    public static @NonNull String new_to_legacy(String template_part) {
        return template_part.replace("{{", "<%").replace("}}", "%>");
    }


    /**
     * This is similar to template.rs's type IResult<&str, Token>.
     * That is, it contains a token that was parsed, and the remaining of the string that must be read.
     */
    @VisibleForTesting
    protected static class IResult {
        private final Token mToken;
        /**
         * The part of the string that must still be read.
         */
        /*
        This is a substring of the template. Java deal efficiently with substring by encoding it as original string,
        start index and length, so there is no loss in efficiency in using string instead of position.
         */
        private final String mRemaining;


        public IResult(Token token, String remaining) {
            this.mToken = token;
            this.mRemaining = remaining;
        }


        @NonNull
        @Override
        public String toString() {
            return "(" + mToken + ", \"" + mRemaining + "\")";
        }


        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof IResult)) {
                return false;
            }
            IResult r = ((IResult) obj);
            return mToken.equals(r.mToken) && mRemaining.equals(r.mRemaining);
        }

        @VisibleForTesting
        public @NonNull IResult new_to_legacy() {
            return new IResult(mToken.new_to_legacy(), Tokenizer.new_to_legacy(mRemaining));
        }
    }

    /**
     * @param template The part of the template that must still be lexed
     * @param legacy whether <% is accepted as a handlebar
     * @return The longest prefix without handlebar, or null if it's empty.
     */
    protected static @Nullable IResult text_token(@NonNull String template, boolean legacy) {
        int first_legacy_handlebar = (legacy) ? template.indexOf("<%") : -1;
        int first_new_handlebar = template.indexOf("{{");
        int text_size;
        if (first_new_handlebar == -1) {
            if (first_legacy_handlebar == -1) {
                text_size = template.length();
            } else {
                text_size = first_legacy_handlebar;
            }
        } else {
            if (first_legacy_handlebar == -1 || first_new_handlebar < first_legacy_handlebar) {
                text_size = first_new_handlebar;
            } else {
                text_size = first_legacy_handlebar;
            }
        }
        if (text_size == 0) {
            return null;
        }
        return new IResult(new Token(TEXT, template.substring(0, text_size)), template.substring(text_size));
    }


    /**
     * classify handle based on leading character
     * @param handle The content between {{ and }}
     */
    protected static @NonNull Token classify_handle(@NonNull String handle) {
        int start_pos = 0;
        while (start_pos < handle.length() && handle.charAt(start_pos) == '{') {
            start_pos++;
        }
        String start = handle.substring(start_pos).trim();
        if (start.length() < 2) {
            return new Token(REPLACEMENT, start);
        }
        switch (start.charAt(0)) {
            case '#':
                return new Token(OPEN_CONDITIONAL, start.substring(1).trim());
            case '/':
                return new Token(CLOSE_CONDITIONAL, start.substring(1).trim());
            case '^':
                return new Token(OPEN_NEGATED, start.substring(1).trim());
            default:
                return new Token(REPLACEMENT, start);
        }
    }


    /**
     * @param template a part of a template to lex
     * @param legacy   Whether to also consider handlebar starting with <%
     * @return The content of handlebar at start of template
     */
    protected static @Nullable IResult handlebar_token(@NonNull String template, boolean legacy) {
        IResult new_handlebar_token = new_handlebar_token(template);
        if (new_handlebar_token != null) {
            return new_handlebar_token;
        }
        if (legacy) {
            return legacy_handlebar_token(template);
        }
        return null;
    }

    /**
     * @param template a part of a template to lex
     * @return The content of handlebar at start of template
     */
    @VisibleForTesting
    protected static @Nullable IResult new_handlebar_token(@NonNull String template) {
        return handlebar_token(template, "{{", "}}");
    }

    protected static @Nullable IResult handlebar_token(@NonNull String template, @NonNull String prefix, @NonNull String suffix) {
        if (!template.startsWith(prefix)) {
            return null;
        }
        int end = template.indexOf(suffix);
        if (end == -1) {
            return null;
        }
        String content = template.substring(prefix.length(), end);
        @NonNull Token handlebar = classify_handle(content);
        return new IResult(handlebar, template.substring(end + suffix.length()));
    }


    /**
     * @param template a part of a template to lex
     * @return The content of handlebar at start of template
     */
    @VisibleForTesting
    protected static @Nullable IResult legacy_handlebar_token(@NonNull String template) {
        return handlebar_token(template, "<%", "%>");
    }


    /**
     * @param template The remaining of template to lex
     * @param legacy   Whether to accept <% as handlebar
     * @return The next token, or null at end of string
     */
    protected static @Nullable IResult next_token(@NonNull String template, boolean legacy) {
        IResult t = handlebar_token(template, legacy);
        if (t != null) {
            return t;
        }
        return text_token(template, legacy);
    }


    /**
     * @return The next token.
     * @throws TemplateError.NoClosingBrackets with no message if the template is entirely lexed, and with the remaining string otherwise.
     */
    @Override
    public Token next() throws TemplateError.NoClosingBrackets {
        if (mTemplate.length() == 0) {
            throw new NoSuchElementException();
        }
        IResult ir = next_token(mTemplate, mLegacy);
        if (ir == null) {
            // Missing closing }}
            mFailed = true;
            throw new TemplateError.NoClosingBrackets(mTemplate);
        }
        mTemplate = ir.mRemaining;
        return ir.mToken;
    }
}
