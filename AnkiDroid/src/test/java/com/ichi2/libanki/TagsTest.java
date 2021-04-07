package com.ichi2.libanki;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import com.ichi2.anki.RobolectricTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

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

        assertFalse(tags_list1.equals(tags.split("Todo todo Needs Revision")));
        assertTrue(tags_list2.equals(tags.split("Todo todo Needs Revision")));
        assertTrue(tags.split("").size() == 0);
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

        assertTrue(" Needs Revision Todo ".equals(tags.addToStr("todo", "Todo todo Needs Revision")));
        assertTrue(" Todo " .equals(tags.addToStr("Todo", "")));
        assertTrue(" Needs Revision Todo ".equals(tags.addToStr("", "Todo todo Needs Revision")));
    }
}
