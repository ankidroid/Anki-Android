package com.ichi2.libanki.template;

import android.util.Pair;

import com.ichi2.anki.RobolectricTest;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.template.Tokenizer.TokenKind.CloseConditional;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OpenConditional;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.OpenNegated;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.Replacement;
import static com.ichi2.libanki.template.Tokenizer.TokenKind.Text;
import static com.ichi2.libanki.template.Tokenizer.IResult;
import static com.ichi2.libanki.template.Tokenizer.classify_handle;
import static com.ichi2.libanki.template.Tokenizer.handlebar_token;
import static com.ichi2.libanki.template.Tokenizer.Token;
import static com.ichi2.libanki.template.Tokenizer.next_token;
import static com.ichi2.libanki.template.Tokenizer.text_token;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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
                        new Tokenizer.Token(Tokenizer.TokenKind.Text, "foo"),
                        "{{bar}}plop")));
        assertThat(text_token("foo{bar}plop"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Tokenizer.TokenKind.Text, "foo{bar}plop"),
                        "")));
    }

    @Test
    public void test_classify_handle() {
        assertThat(classify_handle("#foo"),
                Matchers.is(new Tokenizer.Token(OpenConditional,
                        "foo")));
        assertThat(classify_handle("/foo"),
                Matchers.is(new Tokenizer.Token(CloseConditional,
                        "foo")));
        assertThat(classify_handle("^foo"),
                Matchers.is(new Tokenizer.Token(OpenNegated,
                        "foo")));
        assertThat(classify_handle("!foo"),
                Matchers.is(new Tokenizer.Token(Replacement,
                        "!foo")));
        assertThat(classify_handle("{#foo}"),
                Matchers.is(new Tokenizer.Token(OpenConditional,
                        "foo}")));
        assertThat(classify_handle("{  #foo}"),
                Matchers.is(new Tokenizer.Token(OpenConditional,
                        "foo}")));
        assertThat(classify_handle("    #"),
                Matchers.is(new Tokenizer.Token(Replacement,
                        "#")));
        assertThat(classify_handle("    foo   "),
                Matchers.is(new Tokenizer.Token(Replacement,
                        "foo")));
    }

    @Test
    public void test_handlebar_token() {
        assertThat(handlebar_token("{{#foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OpenConditional,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{/foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(CloseConditional,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{^foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OpenNegated,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{!foo}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
                                "!foo"),
                        " bar")));
        assertThat(handlebar_token("{{{#foo}}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OpenConditional,
                                "foo"),
                        "} bar")));
        assertThat(handlebar_token("{{{  #foo}}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(OpenConditional,
                                "foo"),
                        "} bar")));
        assertThat(handlebar_token("{{    #}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
                                "#"),
                        " bar")));
        assertThat(handlebar_token("{{    foo   }} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
                                "foo"),
                        " bar")));
        assertThat(handlebar_token("{{filter:field}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
                                "filter:field"),
                        " bar")));
        // The empty field name without filter is not valid in Anki,
        // However, it's not the lexer job to deal with it, and so it should be lexed correctly.
        assertThat(handlebar_token("{{}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
                                ""),
                        " bar")));
        // Empty field name with filter is valid and has special meaning
        assertThat(handlebar_token("{{filter:}} bar"),
                Matchers.is(new Tokenizer.IResult(
                        new Tokenizer.Token(Replacement,
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
                        new Tokenizer.Token(OpenConditional,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{/foo}} bar"),
                Matchers.is(new IResult(
                        new Token(CloseConditional,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{^foo}} bar"),
                Matchers.is(new IResult(
                        new Token(OpenNegated,
                                "foo"),
                        " bar")));
        assertThat(next_token("{{!foo}} bar"),
                Matchers.is(new IResult(
                        new Token(Replacement,
                                "!foo"),
                        " bar")));
        assertThat(next_token("{{{#foo}}} bar"),
                Matchers.is(new IResult(
                        new Token(OpenConditional,
                                "foo"),
                        "} bar")));
        assertThat(next_token("{{{  #foo}}} bar"),
                Matchers.is(new IResult(
                        new Token(OpenConditional,
                                "foo"),
                        "} bar")));
        assertThat(next_token("{{    #}} bar"),
                Matchers.is(new IResult(
                        new Token(Replacement,
                                "#"),
                        " bar")));
        assertThat(next_token("{{    foo   }} bar"),
                Matchers.is(new IResult(
                        new Token(Replacement,
                                "foo"),
                        " bar")));

        assertThat(next_token(""), is(nullValue()));
        assertThat(next_token("foo{{bar}}plop"),
                Matchers.is(new IResult(
                        new Token(Text, "foo"),
                        "{{bar}}plop")));
        assertThat(next_token("foo{bar}plop"),
                Matchers.is(new IResult(
                        new Token(Text, "foo{bar}plop"),
                        "")));
    }

    @Test
    public void test_tokens() {
        Tokenizer tokenizer = new Tokenizer("Foo {{Test}} {{{  #Bar}} {{/Plop }}iee {{!ien nnr");

        assertThat(tokenizer.next(), is(new Token(Text, "Foo ")));
        assertThat(tokenizer.next(), is(new Token(Replacement, "Test")));
        assertThat(tokenizer.next(), is(new Token(Text, " ")));
        assertThat(tokenizer.next(), is(new Token(OpenConditional, "Bar")));
        assertThat(tokenizer.next(), is(new Token(Text, " ")));
        assertThat(tokenizer.next(), is(new Token(CloseConditional, "Plop")));
        assertThat(tokenizer.next(), is(new Token(Text, "iee ")));
        try {
            tokenizer.next();
            fail();
        } catch (TemplateError.NoClosingBrackets exc) {
            assertThat(exc.mRemaining, is("{{!ien nnr"));
        }
        assertThat(tokenizer.hasNext(), is(false));
    }
}
