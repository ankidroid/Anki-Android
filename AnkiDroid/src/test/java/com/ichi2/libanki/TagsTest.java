package com.ichi2.libanki;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import com.ichi2.anki.RobolectricTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotEquals;

@RunWith(AndroidJUnit4.class)
public class TagsTest extends RobolectricTest {

    @Test
    public void test_split() {
        Collection col = getCol();
        Tags tags = new Tags(col);

        ArrayList<String> tags_list1 = new ArrayList<>();
        tags_list1.add("Todo");
        tags_list1.add("todo");
        tags_list1.add("Needs revision");

        ArrayList<String> tags_list2 = new ArrayList<>();
        tags_list2.add("Todo");
        tags_list2.add("todo");
        tags_list2.add("Needs");
        tags_list2.add("Revision");

        assertNotEquals(tags_list1, tags.split("Todo todo Needs Revision"));
        assertEquals(tags_list2, tags.split("Todo todo Needs Revision"));
        assertEquals(0, tags.split("").size());
    }

    @Test
    public void test_in_list() {
        Collection col = getCol();
        Tags tags = new Tags(col);

        ArrayList<String> tags_list = new ArrayList<>();
        tags_list.add("Todo");
        tags_list.add("Needs revision");
        tags_list.add("Once more");
        tags_list.add("test1 content");

        assertFalse(tags.inList("Done", tags_list));
        assertTrue(tags.inList("Needs revision", tags_list));
        assertTrue(tags.inList("once More", tags_list));
        assertFalse(tags.inList("test1Content", tags_list));
        assertFalse(tags.inList("", new ArrayList<String>()));
    }

    @Test
    public void test_add_to_str() {
        Collection col = getCol();
        Tags tags = new Tags(col);

        assertEquals(" Needs Revision Todo ", tags.addToStr("todo", "Todo todo Needs Revision"));
        assertEquals(" Todo ", tags.addToStr("Todo", ""));
        assertEquals(" Needs Revision Todo ", tags.addToStr("", "Todo todo Needs Revision"));
    }
}
