package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.hamcrest.CoreMatchers;
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
        assertThat(LaTeX._imgLink("$\\sqrt[3]{2} + \\text{\"var\"}$", model, m),
                CoreMatchers.containsString("<img class=latex alt=\"$\\sqrt[3]{2} + \\text{&quot;var&quot;}$\" src="));
    }
}
