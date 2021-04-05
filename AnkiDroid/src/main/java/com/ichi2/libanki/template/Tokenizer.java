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
     * The remaining of the string to read.
     */
    private @NonNull String mTemplate;
    /**
     * Become true if lexing failed. That is, the string start with {{, but no }} is found.
     */
    private @Nullable boolean mFailed;

    Tokenizer(@NonNull String template) {
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


        public Token(TokenKind king, String text) {
            mKind = king;
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
    }


    /**
     * @param template The part of the template that must still be lexed
     * @return The longest prefix without {{, or null if it's empty.
     */
    @VisibleForTesting
    protected static @Nullable IResult text_token(@NonNull String template) {
        int first_handlebar = template.indexOf("{{");
        int text_size = (first_handlebar == -1) ? template.length() : first_handlebar;
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
                return new Token(OPEN_CONDITIONAL, start.substring(1));
            case '/':
                return new Token(CLOSE_CONDITIONAL, start.substring(1));
            case '^':
                return new Token(OPEN_NEGATED, start.substring(1));
            default:
                return new Token(REPLACEMENT, start);
        }
    }


    /**
     * @param template a part of a template to lex
     * @return The content of handlebar at start of template
     */
    @VisibleForTesting
    protected static @Nullable IResult handlebar_token(@NonNull String template) {
        if (template.length() < 2 || template.charAt(0) != '{' || template.charAt(1) != '{') {
            return null;
        }
        int end = template.indexOf("}}");
        if (end == -1) {
            return null;
        }
        String content = template.substring(2, end);
        @NonNull Token handlebar = classify_handle(content);
        return new IResult(handlebar, template.substring(end+2));
    }


    /**
     * @param template The remaining of template to lex
     * @return The next token, or null at end of string
     */
    @VisibleForTesting
    protected static @Nullable IResult next_token(@NonNull String template) {
        IResult t = handlebar_token(template);
        if (t != null) {
            return t;
        }
        return text_token(template);
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
        IResult ir = next_token(mTemplate);
        if (ir == null) {
            // Missing closing }}
            mFailed = true;
            throw new TemplateError.NoClosingBrackets(mTemplate);
        }
        mTemplate = ir.mRemaining;
        return ir.mToken;
    }
}
