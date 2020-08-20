/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import com.ichi2.libanki.Sound;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Sound.SoundSide.QUESTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

@RunWith(AndroidJUnit4.class)
public class ReadTextTest extends RobolectricTest{

    @Before
    public void init() {
        ReadText.initializeTts(getTargetContext(), mock(AbstractFlashcardViewer.ReadTextListener.class));
    }

    @Test
    public void clozeIsReplacedWithBlank() {

        String content = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}.cloze {font-weight: bold;color: blue;}</style>This is a <span class=cloze>[...]</span>";

        getValueFromReadSide(content);

        assertThat(ReadText.getTextToSpeak(), is("This is a blank"));
    }


    @Test
    public void providedExampleClozeReplaces() {
        String content = "<style>.card {\n" +
                " font-family: arial;\n" +
                " font-size: 20px;\n" +
                " text-align: center;\n" +
                " color: black;\n" +
                " background-color: white;\n" +
                "}.cloze {font-weight: bold;color: blue;}</style>A few lizards are venomous, eg <span class=cloze>[...]</span>. They have grooved teeth and sublingual venom glands.";

        String actual = getValueFromReadSide(content);

        assertThat(actual, is("A few lizards are venomous, eg blank. They have grooved teeth and sublingual venom glands."));
    }



    @CheckResult
    private String getValueFromReadSide(@NonNull String content) {
        ReadText.readCardSide(QUESTION, content, 1, 1, "blank");
        return ReadText.getTextToSpeak();
    }

    @Test
    public void SaveValue() {
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is(""));
        MetaDB.storeLanguage(getTargetContext(), 1, 1, QUESTION, "French");
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is("French"));
        MetaDB.storeLanguage(getTargetContext(), 1, 1, QUESTION, "German");
        assertThat(MetaDB.getLanguage(getTargetContext(), 1, 1, QUESTION), is("German"));
        MetaDB.storeLanguage(getTargetContext(), 2, 1, QUESTION, "English");
        assertThat(MetaDB.getLanguage(getTargetContext(), 2, 1, QUESTION), is("English"));
    }
}
