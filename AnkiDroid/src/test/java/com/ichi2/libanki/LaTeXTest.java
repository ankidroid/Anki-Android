package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;


@RunWith(AndroidJUnit4.class)
public class LaTeXTest extends RobolectricTest {

    public static class MockMedia extends Media {
        public MockMedia(Collection col) {
            super(col, false);
        }


        /**
         * @param fname A field name
         * @return Always true, given that we want to assume the field exists in test
         */
        @Override
        public boolean have(String fname) {
            return true;
        }
    }

    @Test
    public void  _imgLinkTest() {
        Collection col = getCol();
        Media m = new MockMedia(col);
        Model model = col.getModels().byName("Basic");
        // The hashing function should never change, as it would broke link. So hard coding the expected hash value is valid
        // Test with media access
        assertThat(LaTeX._imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", model, m),
                Matchers.is("<img class=latex alt=\"\\$\\\\sqrt[3]{2} + \\\\text{&quot;var&quot;}\\$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">"));

        // Test without access to media
        assertThat(LaTeX._imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", model, col.getMedia()),
                Matchers.is("\\$\\\\sqrt[3]{2} + \\\\text{\"var\"}\\$"));
    }

    @Test
    public void  mungeQATest() {
        Collection col = getCol();
        Media m = new MockMedia(col);
        Model model = col.getModels().byName("Basic");

        // Test with media access
        assertThat(LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", m, model),
                Matchers.is("<img class=latex alt=\"$\\sqrt[3]{2} + \\text{&quot;var&quot;}$\" src=\"latex-dd84e5d506179a137f7924d0960609a8c89d491e.png\">"));

        // Test without access to media
        assertThat(LaTeX.mungeQA("[$]\\sqrt[3]{2} + \\text{\"var\"}[/$]", col, model),
                Matchers.is("$\\sqrt[3]{2} + \\text{\"var\"}$"));
    }
}
