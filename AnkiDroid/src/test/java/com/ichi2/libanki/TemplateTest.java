package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
public class TemplateTest extends RobolectricTest {

    /*****************
     ** Templates    *
     *****************/

    @Test
    public void test_deferred_frontside() throws Exception {
        Collection col = getCol();
        Model m = col.getModels().current();
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{custom:Front}}");
        col.getModels().save(m);

        Note note = col.newNote();
        note.setItem("Front", "xxtest");
        note.setItem("Back", "");
        col.addNote(note);

        assertThat(note.cards().get(0).a(), containsString("xxtest"));
    }
}
