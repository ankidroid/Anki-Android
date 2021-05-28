package com.ichi2.libanki.template;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.template.Tokenizer.TokenKind.CLOSE_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_NEGATED;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.REPLACEMENT;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.TEXT;
import static com.ichi2.libanki.template.Tokenizer.IResult;
import static com.ichi2.libanki.template.Tokenizer.classify_handle;
import static com.ichi2.libanki.template.Tokenizer.legacy_handlebar_token;
import static com.ichi2.libanki.template.Tokenizer.new_handlebar_token;
import static com.ichi2.libanki.template.Tokenizer.Token;
import static com.ichi2.libanki.template.Tokenizer.new_to_legacy;
import static com.ichi2.libanki.template.Tokenizer.next_token;
import static com.ichi2.libanki.template.Tokenizer.text_token;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class TokenizerTest extends RobolectricTest {
    private void test_text_token_is_null(@NonNull String template) {
        assertThat(text_token(template), nullValue());
    }

    private void test_text_token(@NonNull String template, @NonNull IResult expected) {
        assertThat(text_token(template), is(expected));
    }

    @Test
    public void test_text_token() {
        test_text_token_is_null("{{neasiet}}");
        test_text_token_is_null("");
        test_text_token("foo{{bar}}plop", new Tokenizer.IResult(
                        new Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                        "{{bar}}plop"));
        test_text_token("foo{bar}plop",
                new Tokenizer.IResult(
                        new Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo{bar}plop"),
                        ""));
    }

    private void test_classify_handle(@NonNull String template, @NonNull Tokenizer.TokenKind token, @NonNull String remaining) {
        assertThat(classify_handle(template), is (new Tokenizer.Token(token, remaining)));
    }
    @Test
    public void test_classify_handle() {
        test_classify_handle("#foo", OPEN_CONDITIONAL, "foo");
        test_classify_handle("/foo", CLOSE_CONDITIONAL, "foo");
        test_classify_handle("^foo", OPEN_NEGATED, "foo");
        test_classify_handle("!foo", REPLACEMENT, "!foo");
        test_classify_handle("{#foo}", OPEN_CONDITIONAL, "foo}");
        test_classify_handle("{  #foo}", OPEN_CONDITIONAL, "foo}");
        test_classify_handle("    #", REPLACEMENT, "#");
        test_classify_handle("    foo   ", REPLACEMENT, "foo");
    }

    private void test_handlebar_token(@NonNull String template, @NonNull Tokenizer.TokenKind token, @NonNull String field_name, @NonNull String remaining) {
        IResult expected = new IResult(
                        new Tokenizer.Token(token, field_name),
                        remaining);
        assertThat(new_handlebar_token(template), is(expected));
        String legacy_template = new_to_legacy(template);
        IResult legacy_expected = expected.new_to_legacy();
        assertThat(legacy_handlebar_token(legacy_template), is (legacy_expected));
    }

    private void test_handlebar_token_is_null(@NonNull String template) {
        assertThat(new_handlebar_token(template), nullValue());
        String legacy_template = new_to_legacy(template);
        assertThat(legacy_handlebar_token(legacy_template), nullValue());
    }

    @Test
    public void test_handlebar_token() {
        test_handlebar_token("{{#foo}} bar", OPEN_CONDITIONAL,  "foo", " bar");
        test_handlebar_token("{{/foo}} bar", CLOSE_CONDITIONAL, "foo", " bar");
        test_handlebar_token("{{^foo}} bar", OPEN_NEGATED, "foo", " bar");
        test_handlebar_token("{{!foo}} bar", REPLACEMENT, "!foo", " bar");
        test_handlebar_token("{{{#foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar");
        test_handlebar_token("{{{  #foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar");
        test_handlebar_token("{{    #}} bar", REPLACEMENT, "#", " bar");
        test_handlebar_token("{{    foo   }} bar", REPLACEMENT, "foo", " bar");
        test_handlebar_token("{{filter:field}} bar", REPLACEMENT, "filter:field", " bar");
        // The empty field name without filter is not valid in Anki,
        // However, it's not the lexer job to deal with it, and so it should be lexed correctly.
        test_handlebar_token("{{}} bar", REPLACEMENT, "", " bar");
        // Empty field name with filter is valid and has special meaning
        test_handlebar_token("{{filter:}} bar", REPLACEMENT, "filter:", " bar");
        test_handlebar_token_is_null("");
        test_handlebar_token_is_null("{");
        test_handlebar_token_is_null("{nisens");
        test_handlebar_token_is_null("inesa{{aieb }}");
    }

    @Test
    public void test_space_in_token() {
        test_next_token("{{ # foo bar }} baz", OPEN_CONDITIONAL, "foo bar", " baz");
        test_handlebar_token("{{ / foo bar }} baz", CLOSE_CONDITIONAL, "foo bar", " baz");
        test_handlebar_token("{{ ^ foo bar }} baz", OPEN_NEGATED, "foo bar", " baz");
        // REPLACEMENT types will have leading and trailing spaces trimmed, but otherwise no changes
        test_handlebar_token("{{ ! foo}} bar", REPLACEMENT, "! foo", " bar");
        // REPLACEMENT types will have leading and trailing spaces trimmed, but otherwise no changes
        test_handlebar_token("{{ ! foo with spaces before during and after }} bar", REPLACEMENT, "! foo with spaces before during and after", " bar");
    }

    private void test_next_token(@NonNull String template, @NonNull Tokenizer.TokenKind token, @NonNull String field_name, @NonNull String remaining) {
        IResult expected = new IResult(new Tokenizer.Token(token,
                                                           field_name),
                                       remaining);
        assertThat(next_token(template), is(expected));

    }

    private void test_next_token_is_null(@NonNull String template) {
        assertThat(next_token(template), nullValue());
    }

    @Test
    public void test_next_token() {
        test_next_token("{{#foo}} bar", OPEN_CONDITIONAL, "foo", " bar");
        test_next_token("{{/foo}} bar", CLOSE_CONDITIONAL, "foo", " bar");
        test_next_token("{{^foo}} bar", OPEN_NEGATED, "foo", " bar");
        test_next_token("{{!foo}} bar", REPLACEMENT, "!foo", " bar");
        test_next_token("{{{#foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar");
        test_next_token("{{{  #foo}}} bar", OPEN_CONDITIONAL, "foo", "} bar");
        test_next_token("{{    #}} bar", REPLACEMENT, "#", " bar");
        test_next_token("{{    foo   }} bar", REPLACEMENT, "foo", " bar");

        test_next_token_is_null("");
        test_next_token("foo{{bar}}plop", TEXT, "foo", "{{bar}}plop");
        test_next_token("foo{bar}plop", TEXT, "foo{bar}plop","");
    }

    @Test
    public void test_tokens() {
        Tokenizer tokenizer = new Tokenizer("Foo {{Test}} {{{  #Bar}} {{/Plop }}iee {{!ien nnr");

        assertThat(tokenizer.next(), is(new Token(TEXT, "Foo ")));
        assertThat(tokenizer.next(), is(new Token(REPLACEMENT, "Test")));
        assertThat(tokenizer.next(), is(new Token(TEXT, " ")));
        assertThat(tokenizer.next(), is(new Token(OPEN_CONDITIONAL, "Bar")));
        assertThat(tokenizer.next(), is(new Token(TEXT, " ")));
        assertThat(tokenizer.next(), is(new Token(CLOSE_CONDITIONAL, "Plop")));
        assertThat(tokenizer.next(), is(new Token(TEXT, "iee ")));
        try {
            tokenizer.next();
            fail();
        } catch (TemplateError.NoClosingBrackets exc) {
            assertThat(exc.mRemaining, is("{{!ien nnr"));
        }
        assertThat(tokenizer.hasNext(), is(false));
    }
}
