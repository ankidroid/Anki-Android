package com.ichi2.libanki.template;

import com.ichi2.anki.RobolectricTest;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.template.Tokenizer.TokenKind.CLOSE_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_CONDITIONAL;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OPEN_NEGATED;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.REPLACEMENT;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.TEXT;
import static com.ichi2.libanki.template.Tokenizer.IResult;
import static com.ichi2.libanki.template.Tokenizer.classify_handle;
import static com.ichi2.libanki.template.Tokenizer.handlebar_token;
import static com.ichi2.libanki.template.Tokenizer.Token;
import static com.ichi2.libanki.template.Tokenizer.next_token;
import static com.ichi2.libanki.template.Tokenizer.text_token;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class TokenizerTest extends RobolectricTest {

    @Test
    public void test_text_token() {
        assertThat(text_token("{{neasiet}}"), is(nullValue()));
        assertThat(text_token(""), is(nullValue()));
        assertThat(text_token("foo{{bar}}plop"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo"),
                        "{{bar}}plop")));
        assertThat(text_token("foo{bar}plop"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Tokenizer.TokenKind.TEXT, "foo{bar}plop"),
                        "")));
    }

    @Test
    public void test_classify_handle() {
        assertThat(classify_handle("#foo"),
                Matchers.is(new Tokenizer.Token(OPEN_CONDITIONAL,
                        "foo")));
        assertThat(classify_handle("/foo"),
                Matchers.is(new Tokenizer.Token(CLOSE_CONDITIONAL,
                        "foo")));
        assertThat(classify_handle("^foo"),
                Matchers.is(new Tokenizer.Token(OPEN_NEGATED,
                        "foo")));
        assertThat(classify_handle("!foo"),
                Matchers.is(new Tokenizer.Token(REPLACEMENT,
                        "!foo")));
        assertThat(classify_handle("{#foo}"),
                Matchers.is(new Tokenizer.Token(OPEN_CONDITIONAL,
                        "foo}")));
        assertThat(classify_handle("{  #foo}"),
                Matchers.is(new Tokenizer.Token(OPEN_CONDITIONAL,
                        "foo}")));
        assertThat(classify_handle("    #"),
                Matchers.is(new Tokenizer.Token(REPLACEMENT,
                        "#")));
        assertThat(classify_handle("    foo   "),
                Matchers.is(new Tokenizer.Token(REPLACEMENT,
                        "foo")));
    }

    @Test
    public void test_handlebar_token() {
        assertThat(handlebar_token("{{#foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OPEN_CONDITIONAL,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{/foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(CLOSE_CONDITIONAL,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{^foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OPEN_NEGATED,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{!foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                "!foo"),
                        " bar")));
        assertThat(handlebar_token("{{{#foo}}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OPEN_CONDITIONAL,
                                "foo"),
                        "} bar")));
        assertThat(handlebar_token("{{{  #foo}}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OPEN_CONDITIONAL,
                                "foo"),
                        "} bar")));
        assertThat(handlebar_token("{{    #}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                "#"),
                        " bar")));
        assertThat(handlebar_token("{{    foo   }} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{filter:field}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                "filter:field"),
                        " bar")));
        // The empty field name without filter is not valid in Anki,
        // However, it's not the lexer job to deal with it, and so it should be lexed correctly.
        assertThat(handlebar_token("{{}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                ""),
                        " bar")));
        // Empty field name with filter is valid and has special meaning
        assertThat(handlebar_token("{{filter:}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(REPLACEMENT,
                                "filter:"),
                        " bar")));
        assertThat(handlebar_token(""),
                is(nullValue()));
        assertThat(handlebar_token("{"),
                is(nullValue()));
        assertThat(handlebar_token("{nisens"),
                is(nullValue()));
        assertThat(handlebar_token("inesa{{aieb }}"),
                is(nullValue()));
    }

    @Test
    public void test_next_token() {
        assertThat(next_token("{{#foo}} bar"),
                Matchers.is(new IResult(
                        new Tokenizer.Token(OPEN_CONDITIONAL,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{/foo}} bar"),
                Matchers.is(new IResult(
                        new Token(CLOSE_CONDITIONAL,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{^foo}} bar"),
                Matchers.is(new IResult(
                        new Token(OPEN_NEGATED,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{!foo}} bar"),
                Matchers.is(new IResult(
                        new Token(REPLACEMENT,
                                "!foo"),
                        " bar")));
        assertThat(next_token("{{{#foo}}} bar"),
                Matchers.is(new IResult(
                        new Token(OPEN_CONDITIONAL,
                                "foo"),
                        "} bar")));
        assertThat(next_token("{{{  #foo}}} bar"),
                Matchers.is(new IResult(
                        new Token(OPEN_CONDITIONAL,
                                "foo"),
                        "} bar")));
        assertThat(next_token("{{    #}} bar"),
                Matchers.is(new IResult(
                        new Token(REPLACEMENT,
                                "#"),
                        " bar")));
        assertThat(next_token("{{    foo   }} bar"),
                Matchers.is(new IResult(
                        new Token(REPLACEMENT,
                                "foo"),
                        " bar")));

        assertThat(next_token(""), is(nullValue()));
        assertThat(next_token("foo{{bar}}plop"),
                Matchers.is(new IResult(
                        new Token(TEXT, "foo"),
                        "{{bar}}plop")));
        assertThat(next_token("foo{bar}plop"),
                Matchers.is(new IResult(
                        new Token(TEXT, "foo{bar}plop"),
                        "")));
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
