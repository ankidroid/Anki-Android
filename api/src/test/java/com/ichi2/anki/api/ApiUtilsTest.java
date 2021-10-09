//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by rodrigobresan on 19/10/17.
 * <p>
 * In case of any questions, feel free to ask me
 * <p>
 * E-mail: rcbresan@gmail.com
 * Slack: bresan
 */

@RunWith(RobolectricTestRunner.class)
public class ApiUtilsTest {

    private static final String delimiter = "\u001F";

    @Test
    public void joinFieldsShouldJoinWhenListIsValid() {
        String[] fieldList = {"A", "B", "C"};
        assertEquals("A" + delimiter + "B" + delimiter + "C", Utils.joinFields(fieldList));
    }

    @Test
    public void joinFieldsShouldReturnNullWhenListIsNull() {
        assertNull(Utils.joinFields(null));
    }

    @Test
    public void splitFieldsShouldSplitRightWhenStringIsValid() {
        String fieldList = "A" + delimiter + "B" + delimiter + "C";
        String[] output = Utils.splitFields(fieldList);
        assertEquals("A", output[0]);
        assertEquals("B", output[1]);
        assertEquals("C", output[2]);
    }

    @Test
    public void splitFieldsShouldReturnNullWhenStringIsNull() {
        assertNull(Utils.splitFields(null));
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsValid() {
        Set<String> set = new HashSet<>();
        set.add("A");
        set.add("B");
        set.add("C");
        assertEquals("A B C", Utils.joinTags(set));
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsNull() {
        assertEquals("", Utils.joinTags(null));
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsEmpty() {
        assertEquals("", Utils.joinTags(new HashSet<>()));
    }

    @Test
    public void splitTagsShouldReturnNullWhenStringIsValid() {
        String tags = "A B C";
        String[] output = Utils.splitTags(tags);
        assertEquals("A", output[0]);
        assertEquals("B", output[1]);
        assertEquals("C", output[2]);
    }

    @Test
    public void splitTagsShouldReturnNullWhenStringIsNull() {
        assertNull(Utils.splitTags(null));
    }

    @Test
    public void shouldGenerateProperCheckSum() {
        assertEquals(Long.valueOf(3533307532L), Utils.fieldChecksum("AnkiDroid"));
    }
}
