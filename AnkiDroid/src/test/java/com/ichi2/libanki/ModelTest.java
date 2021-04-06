package com.ichi2.libanki;

import com.ichi2.anki.R;
import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Consts.MODEL_CLOZE;
import static com.ichi2.libanki.Models.REQ_ALL;
import static com.ichi2.libanki.Models.REQ_ANY;
import static com.ichi2.libanki.Utils.stripHTML;
import static com.ichi2.utils.ListUtil.assertListEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ModelTest extends RobolectricTest {
    /*****************
     ** Models       *
     *****************/

    @Test
    public void test_modelDelete() throws ConfirmModSchemaException {
        Collection col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "2");
        col.addNote(note);
        assertEquals(1, col.cardCount());
        col.getModels().rem(col.getModels().current());
        assertEquals(0, col.cardCount());
    }


    @Test
    public void test_modelCopy() {
        Collection col = getCol();
        Model m = col.getModels().current();
        Model m2 = col.getModels().copy(m);
        assertEquals("Basic copy", m2.getString("name"));
        assertNotEquals(m2.getLong("id"), m.getLong("id"));
        assertEquals(2, m2.getJSONArray("flds").length());
        assertEquals(2, m.getJSONArray("flds").length());
        assertEquals(m.getJSONArray("flds").length(), m2.getJSONArray("flds").length());
        assertEquals(1, m.getJSONArray("tmpls").length());
        assertEquals(1, m2.getJSONArray("tmpls").length());
        assertEquals(col.getModels().scmhash(m), col.getModels().scmhash(m2));
    }


    @Test
    public void test_fields() throws ConfirmModSchemaException {
        Collection col = getCol();
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "2");
        col.addNote(note);
        Model m = col.getModels().current();
        // make sure renaming a field updates the templates
        col.getModels().renameField(m, m.getJSONArray("flds").getJSONObject(0), "NewFront");
        assertThat(m.getJSONArray("tmpls").getJSONObject(0).getString("qfmt"), containsString("{{NewFront}}"));
        String h = col.getModels().scmhash(m);
        // add a field
        JSONObject field = col.getModels().newField("foo");
        col.getModels().addField(m, field);
        assertArrayEquals(new String[] {"1", "2", ""}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        assertNotEquals(h, col.getModels().scmhash(m));
        // rename it
        field = m.getJSONArray("flds").getJSONObject(2);
        col.getModels().renameField(m, field, "bar");
        assertEquals("", col.getNote(col.getModels().nids(m).get(0)).getItem("bar"));
        // delete back
        col.getModels().remField(m, m.getJSONArray("flds").getJSONObject(1));
        assertArrayEquals(new String[] {"1", ""}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // move 0 -> 1
        col.getModels().moveField(m, m.getJSONArray("flds").getJSONObject(0), 1);
        assertArrayEquals(new String[] {"", "1"}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // move 1 -> 0
        col.getModels().moveField(m, m.getJSONArray("flds").getJSONObject(1), 0);
        assertArrayEquals(new String[] {"1", ""}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // add another and put in middle
        field = col.getModels().newField("baz");
        col.getModels().addField(m, field);
        note = col.getNote(col.getModels().nids(m).get(0));
        note.setItem("baz", "2");
        note.flush();
        assertArrayEquals(new String[] {"1", "", "2"}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // move 2 -> 1
        col.getModels().moveField(m, m.getJSONArray("flds").getJSONObject(2), 1);
        assertArrayEquals(new String[] {"1", "2", ""}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // move 0 -> 2
        col.getModels().moveField(m, m.getJSONArray("flds").getJSONObject(0), 2);
        assertArrayEquals(new String[] {"2", "", "1"}, col.getNote(col.getModels().nids(m).get(0)).getFields());
        // move 0 -> 1
        col.getModels().moveField(m, m.getJSONArray("flds").getJSONObject(0), 1);
        assertArrayEquals(new String[] {"", "2", "1"}, col.getNote(col.getModels().nids(m).get(0)).getFields());
    }


    @Test
    public void test_templates() throws ConfirmModSchemaException {
        Collection col = getCol();
        Model m = col.getModels().current();
        Models mm = col.getModels();
        JSONObject t = Models.newTemplate("Reverse");
        t.put("qfmt", "{{Back}}");
        t.put("afmt", "{{Front}}");
        mm.addTemplateModChanged(m, t);
        mm.save(m);
        Note note = col.newNote();
        note.setItem("Front", "1");
        note.setItem("Back", "2");
        col.addNote(note);
        assertEquals(2, col.cardCount());
        List<Card> cards = note.cards();
        assertEquals(2, cards.size());
        Card c = cards.get(0);
        Card c2 = cards.get(1);
        // first card should have first ord
        assertEquals(0, c.getOrd());
        assertEquals(1, c2.getOrd());
        // switch templates
        col.getModels().moveTemplate(m, c.template(), 1);
        c.load();
        c2.load();
        assertEquals(1, c.getOrd());
        assertEquals(0, c2.getOrd());
        // removing a template should delete its cards
        col.getModels().remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0));
        assertEquals(1, col.cardCount());
        // and should have updated the other cards' ordinals
        c = note.cards().get(0);
        assertEquals(0, c.getOrd());
        assertEquals("1", stripHTML(c.q()));
        // it shouldn't be possible to orphan notes by removing templates
        t = Models.newTemplate("template name");
        mm.addTemplateModChanged(m, t);
        col.getModels().remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0));
        assertEquals(0,
                col.getDb().queryLongScalar(
                        "select count() from cards where nid not in (select id from notes)"));
    }


    @Test
    public void test_cloze_ordinals() throws ConfirmModSchemaException {
        Collection col = getCol();
        col.getModels().setCurrent(col.getModels().byName("Cloze"));
        Model m = col.getModels().current();
        Models mm = col.getModels();

        // We replace the default Cloze template
        JSONObject t = Models.newTemplate("ChainedCloze");
        t.put("qfmt", "{{text:cloze:Text}}");
        t.put("afmt", "{{text:cloze:Text}}");
        mm.addTemplateModChanged(m, t);
        mm.save(m);
        col.getModels().remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0));

        Note note = col.newNote();
        note.setItem("Text", "{{c1::firstQ::firstA}}{{c2::secondQ::secondA}}");
        col.addNote(note);
        assertEquals(2, col.cardCount());
        List<Card> cards = note.cards();
        assertEquals(2, cards.size());
        Card c = cards.get(0);
        Card c2 = cards.get(1);
        // first card should have first ord
        assertEquals(0, c.getOrd());
        assertEquals(1, c2.getOrd());
    }

    @Test
    public void test_cloze_empty() {
        Collection col = getCol();
        Models mm = col.getModels();
        Model cloze_model = mm.byName("Cloze");
        mm.setCurrent(cloze_model);
        assertListEquals(Arrays.asList(0, 1), Models.availOrds(cloze_model, new String[]{"{{c1::Empty}} and {{c2::}}", ""}));
    }


    @Test
    public void test_text() {
        Collection col = getCol();
        Model m = col.getModels().current();
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{text:Front}}");
        col.getModels().save(m);
        Note note = col.newNote();
        note.setItem("Front", "hello<b>world");
        col.addNote(note);
        assertThat(note.cards().get(0).q(), containsString("helloworld"));
    }


    @Test
    public void test_cloze() {
        Collection col = getCol();
        col.getModels().setCurrent(col.getModels().byName("Cloze"));
        Note note = col.newNote();
        assertEquals("Cloze", note.model().getString("name"));
        // a cloze model with no clozes is not empty
        note.setItem("Text", "nothing");
        assertEquals(1, col.addNote(note));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(0, col.addNote(note, Models.AllowEmpty.FALSE));
        // try with one cloze
        note = col.newNote();
        note.setItem("Text", "hello {{c1::world}}");
        assertEquals(1, col.addNote(note));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE));
        assertThat(note.cards().get(0).q(), containsString("hello <span class=cloze>[...]</span>"));
        assertThat(note.cards().get(0).a(), containsString("hello <span class=cloze>world</span>"));
        // and with a comment
        note = col.newNote();
        note.setItem("Text", "hello {{c1::world::typical}}");
        assertEquals(1, col.addNote(note));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE));
        assertThat(note.cards().get(0).q(), containsString("<span class=cloze>[typical]</span>"));
        assertThat(note.cards().get(0).a(), containsString("<span class=cloze>world</span>"));
        // and with 2 clozes
        note = col.newNote();
        note.setItem("Text", "hello {{c1::world}} {{c2::bar}}");
        assertEquals(2, col.addNote(note));
        List<Card> cards = note.cards();
        assertEquals(2, cards.size());
        Card c1 = cards.get(0);
        Card c2 = cards.get(1);
        assertThat(c1.q(), containsString("<span class=cloze>[...]</span> bar"));
        assertThat(c1.a(), containsString("<span class=cloze>world</span> bar"));
        assertThat(c2.q(), containsString("world <span class=cloze>[...]</span>"));
        assertThat(c2.a(), containsString("world <span class=cloze>bar</span>"));
        // if there are multiple answers for a single cloze, they are given in a
        // list
        note.setItem("Text", "a {{c1::b}} {{c1::c}}");
        assertEquals(1, col.addNote(note));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.FALSE));
        assertThat(note.cards().get(0).a(), containsString("<span class=cloze>b</span> <span class=cloze>c</span>"));
        // if we add another cloze, a card should be generated
        note.setItem("Text", "{{c2::hello}} {{c1::foo}}");
        assertEquals(2, col.addNote(note));
        assertEquals(2, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(2, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(2, col.addNote(note, Models.AllowEmpty.FALSE));
        // 0 or negative indices are not supported
        note.setItem("Text", "{{c0::zero}} {{c-1:foo}}");
        assertEquals(1, col.addNote(note));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.TRUE));
        assertEquals(1, col.addNote(note, Models.AllowEmpty.ONLY_CLOZE));
        assertEquals(0, col.addNote(note, Models.AllowEmpty.FALSE));

        note = col.newNote();
        note.setItem("Text", "hello {{c1::world}}");
        col.addNote(note);
        assertEquals(1, note.numberOfCards());
        note.setItem("Text", "hello {{c2::world}}");
        note.flush();
        assertEquals(2, note.numberOfCards());
        note.setItem("Text", "{{c1::hello}} {{c2::world}}");
        note.flush();
        assertEquals(2, note.numberOfCards());
        note.setItem("Text", "{{c1::hello}} {{c3::world}}");
        note.flush();
        assertEquals(3, note.numberOfCards());
        note.setItem("Text", "{{c0::hello}} {{c-1::world}}");
        note.flush();
        assertEquals(3, note.numberOfCards());
    }


    @Test
    public void test_cloze_mathjax() {
        Collection col = getCol();
        col.getModels().setCurrent(col.getModels().byName("Cloze"));
        Note note = col.newNote();
        note.setItem("Text", "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}");
        assertNotEquals(0, col.addNote(note));
        assertEquals(5, note.numberOfCards());
        assertThat(note.cards().get(0).q(), containsString("class=cloze"));
        assertThat(note.cards().get(1).q(), containsString("class=cloze"));
        assertThat(note.cards().get(2).q(), not(containsString("class=cloze")));
        assertThat(note.cards().get(3).q(), containsString("class=cloze"));
        assertThat(note.cards().get(4).q(), containsString("class=cloze"));

        note = col.newNote();
        note.setItem("Text", "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]");
        assertNotEquals(0, col.addNote(note));
        assertEquals(1, note.numberOfCards());
        String question = note.cards().get(0).q();
        assertTrue("Question «" + question + "» does not end correctly", question.endsWith("\\(a\\) <span class=cloze>[...]</span> \\[ [...] \\]"));
    }


    @Test
    public void test_typecloze() {
        Collection col = getCol();
        Model m = col.getModels().byName("Cloze");
        col.getModels().setCurrent(m);
        m.getJSONArray("tmpls").getJSONObject(0).put("qfmt", "{{cloze:Text}}{{type:cloze:Text}}");
        col.getModels().save(m);
        Note note = col.newNote();
        note.setItem("Text", "hello {{c1::world}}");
        col.addNote(note);
        assertThat(note.cards().get(0).q(), containsString("[[type:cloze:Text]]"));
    }


    @Test
    public void test_chained_mods() throws ConfirmModSchemaException {
        Collection col = getCol();
        col.getModels().setCurrent(col.getModels().byName("Cloze"));
        Model m = col.getModels().current();
        Models mm = col.getModels();

        // We replace the default Cloze template
        JSONObject t = Models.newTemplate("ChainedCloze");
        t.put("qfmt", "{{cloze:text:Text}}");
        t.put("afmt", "{{cloze:text:Text}}");
        mm.addTemplateModChanged(m, t);
        mm.save(m);
        col.getModels().remTemplate(m, m.getJSONArray("tmpls").getJSONObject(0));

        Note note = col.newNote();
        String q1 = "<span style=\"color:red\">phrase</span>";
        String a1 = "<b>sentence</b>";
        String q2 = "<span style=\"color:red\">en chaine</span>";
        String a2 = "<i>chained</i>";
        note.setItem("Text", "This {{c1::" + q1 + "::" + a1 + "}} demonstrates {{c1::" + q2 + "::" + a2 + "}} clozes.");
        assertEquals(1, col.addNote(note));
        String question = note.cards().get(0).q();
        /* TODO: chained modifier
        assertThat("Question «"+question+"» does not contain the expected string", question, containsString("This <span class=cloze>[sentence]</span> demonstrates <span class=cloze>[chained]</span> clozes.")
                   );
        assertThat(note.cards().get(0).a(), containsString("This <span class=cloze>phrase</span> demonstrates <span class=cloze>en chaine</span> clozes."
                                                    ));

         */
    }


    @Test
    public void test_modelChange() throws ConfirmModSchemaException {
        Collection col = getCol();
        Model cloze = col.getModels().byName("Cloze");
        // enable second template and add a note
        Model basic = col.getModels().current();
        Models mm = col.getModels();
        JSONObject t = Models.newTemplate("Reverse");
        t.put("qfmt", "{{Back}}");
        t.put("afmt", "{{Front}}");
        mm.addTemplateModChanged(basic, t);
        mm.save(basic);
        Note note = col.newNote();
        note.setItem("Front", "note");
        note.setItem("Back", "b123");
        col.addNote(note);
        // switch fields
        Map<Integer, Integer> map = new HashMap<>();
        map.put(0, 1);
        map.put(1, 0);
        col.getModels().change(basic, note.getId(), basic, map, null);
        note.load();
        assertEquals("b123", note.getItem("Front"));
        assertEquals("note", note.getItem("Back"));
        // switch cards
        Card c0 = note.cards().get(0);
        Card c1 = note.cards().get(1);
        assertThat(c0.q(), containsString("b123"));
        assertThat(c1.q(), containsString("note"));
        assertEquals(0, c0.getOrd());
        assertEquals(1, c1.getOrd());
        col.getModels().change(basic, note.getId(), basic, null, map);
        note.load();
        c0.load();
        c1.load();
        assertThat(c0.q(), containsString("note"));
        assertThat(c1.q(), containsString("b123"));
        assertEquals(1, c0.getOrd());
        assertEquals(0, c1.getOrd());
        // .cards() returns cards in order
        assertEquals(c1.getId(), note.cards().get(0).getId());
        // delete first card
        map = new HashMap<>();
        map.put(0, null);
        map.put(1, 1);
        // if (isWin) {
        //     // The low precision timer on Windows reveals a race condition
        //     time.sleep(0.05);
        // }
        col.getModels().change(basic, note.getId(), basic, null, map);
        note.load();
        c0.load();
        // the card was deleted
        // but we have two cards, as a new one was generated
        assertEquals(2, note.numberOfCards());
        // an unmapped field becomes blank
        assertEquals("b123", note.getItem("Front"));
        assertEquals("note", note.getItem("Back"));
        col.getModels().change(basic, note.getId(), basic, map, null);
        note.load();
        assertEquals("", note.getItem("Front"));
        assertEquals("note", note.getItem("Back"));
        // another note to try model conversion
        note = col.newNote();
        note.setItem("Front", "f2");
        note.setItem("Back", "b2");
        col.addNote(note);
        // counts = col.getModels().all_use_counts();
        // Using older version of the test
        assertEquals(2, col.getModels().useCount(basic));
        assertEquals(0, col.getModels().useCount(cloze));
        // Identity map
        map = new HashMap<>();
        map.put(0, 0);
        map.put(1, 1);
        col.getModels().change(basic, note.getId(), cloze, map, map);
        note.load();
        assertEquals("f2", note.getItem("Text"));
        assertEquals(2, note.numberOfCards());
        // back the other way, with deletion of second ord
        col.getModels().remTemplate(basic, basic.getJSONArray("tmpls").getJSONObject(1));
        assertEquals(2, col.getDb().queryScalar("select count() from cards where nid = ?", note.getId()));
        map = new HashMap<>();
        map.put(0, 0);
        col.getModels().change(cloze, note.getId(), basic, map, map);
        assertEquals(1, col.getDb().queryScalar("select count() from cards where nid = ?", note.getId()));
    }


    private void reqSize(Model model) {
        if (model.getInt("type") == MODEL_CLOZE) {
            return;
        }
        assertEquals(model.getJSONArray("req").length(), model.getJSONArray("tmpls").length());
    }

    @Test
    @Config(qualifiers = "en")
    public void regression_test_pipe() {
        Collection col = getCol();
        Models mm = col.getModels();
        Model basic = mm.byName("Basic");
        JSONObject template = basic.getJSONArray("tmpls").getJSONObject(0);
        template.put("qfmt", "{{|Front}}{{Front}}{{/Front}}{{Front}}");
        mm.save(basic, true);
        try {
            Note note = addNoteUsingBasicModel("foo", "bar");
            fail();
        } catch (IllegalStateException er) {
        }
    }

    @Test
    public void test_getNamesOfFieldContainingCloze() {
        assertListEquals(new ArrayList<>(), Models.getNamesOfFieldsContainingCloze(""));
        String example = "{{cloze::foo}} <%cloze:bar%>";
        assertListEquals(Arrays.asList("foo", "bar"), Models.getNamesOfFieldsContainingCloze(example));
        assertListEquals(Arrays.asList("foo", "bar"), Models.getNamesOfFieldsContainingCloze(example));
    }

    @Test
    public void nonEmptyFieldTest() {
        Collection col = getCol();
        Models mm = col.getModels();
        Model basic = mm.byName("Basic");
        Set s = new HashSet<>();
        assertEquals(s, basic.nonEmptyFields(new String[] {"", ""}));
        s.add("Front");
        assertEquals(s, basic.nonEmptyFields(new String[] {"<br/>", "   \t "})); // Html is not stripped to check for card generation
        assertEquals(s, basic.nonEmptyFields(new String[] {"P", ""}));
        s.add("Back");
        assertEquals(s, basic.nonEmptyFields(new String[] {"P", "A"}));
    }

    @Test
    public void avail_standard_order_test() {
        Collection col = getCol();
        Models mm = col.getModels();
        Model basic = mm.byName("Basic");
        Model reverse = mm.byName("Basic (and reversed card)");

        assertListEquals(new ArrayList<>(), Models._availStandardOrds(basic, new String[]{"", ""}));
        assertListEquals(new ArrayList<>(), Models._availStandardOrds(basic, new String[]{"", "Back"}));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"Foo", ""}));
        assertListEquals(Arrays.asList(), Models._availStandardOrds(basic, new String[]{"  \t ", ""}));
        assertListEquals(new ArrayList<>(), Models._availStandardOrds(reverse, new String[]{"", ""}));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(reverse, new String[]{"Foo", ""}));
        assertListEquals(Arrays.asList(0, 1), Models._availStandardOrds(reverse, new String[]{"Foo", "Bar"}));
        assertListEquals(Arrays.asList(1), Models._availStandardOrds(reverse, new String[]{"  \t ", "Bar"}));

        assertListEquals(new ArrayList<>(), Models._availStandardOrds(basic, new String[]{"", ""}, false) );
        assertListEquals(new ArrayList<>(), Models._availStandardOrds(basic, new String[]{"", "Back"}, false));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"Foo", ""}, false));
        assertListEquals(Arrays.asList(), Models._availStandardOrds(basic, new String[]{"  \t ", ""}, false));
        assertListEquals(new ArrayList<>(), Models._availStandardOrds(reverse, new String[]{"", ""}, false));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(reverse, new String[]{"Foo", ""}, false));
        assertListEquals(Arrays.asList(0, 1), Models._availStandardOrds(reverse, new String[]{"Foo", "Bar"}, false));
        assertListEquals(Arrays.asList(1), Models._availStandardOrds(reverse, new String[]{"  \t ", "Bar"}, false));

        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"", ""}, true) );
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"", "Back"}, true));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"Foo", ""}, true));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(basic, new String[]{"  \t ", ""}, true));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(reverse, new String[]{"", ""}, true));
        assertListEquals(Arrays.asList(0), Models._availStandardOrds(reverse, new String[]{"Foo", ""}, true));
        assertListEquals(Arrays.asList(0, 1), Models._availStandardOrds(reverse, new String[]{"Foo", "Bar"}, true));
        assertListEquals(Arrays.asList(1), Models._availStandardOrds(reverse, new String[]{"  \t ", "Bar"}, true));
    }

    @Test
    public void avail_ords_test() {
        Collection col = getCol();
        Models mm = col.getModels();
        Model basic = mm.byName("Basic");
        Model reverse = mm.byName("Basic (and reversed card)");

        assertListEquals(new ArrayList<>(), Models.availOrds(basic, new String[]{"", ""}));
        assertListEquals(new ArrayList<>(), Models.availOrds(basic, new String[]{"", "Back"}));
        assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[]{"Foo", ""}));
        assertListEquals(Arrays.asList(), Models.availOrds(basic, new String[]{"  \t ", ""}));
        assertListEquals(new ArrayList<>(), Models.availOrds(reverse, new String[]{"", ""}));
        assertListEquals(Arrays.asList(0), Models.availOrds(reverse, new String[]{"Foo", ""}));
        assertListEquals(Arrays.asList(0, 1), Models.availOrds(reverse, new String[]{"Foo", "Bar"}));
        assertListEquals(Arrays.asList(1), Models.availOrds(reverse, new String[]{"  \t ", "Bar"}));

        for (Models.AllowEmpty allow : new Models.AllowEmpty[] {Models.AllowEmpty.ONLY_CLOZE, Models.AllowEmpty.FALSE}) {
            assertListEquals(new ArrayList<>(), Models.availOrds(basic, new String[] {"", ""}, allow));
            assertListEquals(new ArrayList<>(), Models.availOrds(basic, new String[] {"", "Back"}, allow));
            assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[] {"Foo", ""}, allow));
            assertListEquals(Arrays.asList(), Models.availOrds(basic, new String[] {"  \t ", ""}, allow));
            assertListEquals(new ArrayList<>(), Models.availOrds(reverse, new String[] {"", ""}, allow));
            assertListEquals(Arrays.asList(0), Models.availOrds(reverse, new String[] {"Foo", ""}, allow));
            assertListEquals(Arrays.asList(0, 1), Models.availOrds(reverse, new String[] {"Foo", "Bar"}, allow));
            assertListEquals(Arrays.asList(1), Models.availOrds(reverse, new String[] {"  \t ", "Bar"}, allow));
        }

        assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[]{"", ""}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[]{"", "Back"}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[]{"Foo", ""}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0), Models.availOrds(basic, new String[]{"  \t ", ""}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0), Models.availOrds(reverse, new String[]{"", ""}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0), Models.availOrds(reverse, new String[]{"Foo", ""}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(0, 1), Models.availOrds(reverse, new String[]{"Foo", "Bar"}, Models.AllowEmpty.TRUE));
        assertListEquals(Arrays.asList(1), Models.availOrds(reverse, new String[]{"  \t ", "Bar"}, Models.AllowEmpty.TRUE));
    }
}
