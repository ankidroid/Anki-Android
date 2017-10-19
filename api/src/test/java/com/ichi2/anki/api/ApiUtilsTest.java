package com.ichi2.anki.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertEquals;

/**
 * Created by rodrigobresan on 19/10/17.
 * <p>
 * In case of any questions, feel free to ask me
 * <p>
 * E-mail: rcbresan@gmail.com
 * Slack: bresan
 */

@RunWith(JUnit4.class)
public class ApiUtilsTest {

    private static String delimiter = "\u001F";

    @Test
    public void joinFieldsShouldJoinWhenListIsValid() {
        String fieldList[] = {"A", "B", "C"};
        String output = Utils.joinFields(fieldList);

        assertEquals("A" + delimiter + "B" + delimiter + "C", output);
    }

    @Test
    public void joinFieldsShouldReturnNullWhenListIsNull() {
        String fieldList[] = null;
        String output = Utils.joinFields(fieldList);

        assertEquals(null, output);
    }

    @Test
    public void splitFieldsShouldSplitRightWhenStringIsValid() {
        String fieldList = "A" + delimiter + "B" + delimiter + "C";
        String output[] = Utils.splitFields(fieldList);

        assertEquals("A", output[0]);
        assertEquals("B", output[1]);
        assertEquals("C", output[2]);
    }

    @Test
    public void splitFieldsShouldReturnNullWhenStringIsNull() {
        String fieldList = null;
        String output[] = Utils.splitFields(fieldList);

        assertEquals(null, output);
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsValid() {
        Set<String> set = new HashSet<>();
        set.add("A");
        set.add("B");
        set.add("C");

        String output = Utils.joinTags(set);

        assertEquals("A B C", output);
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsNull() {
        Set<String> set = null;
        String output = Utils.joinTags(set);

        assertEquals("", output);
    }

    @Test
    public void joinTagsShouldReturnEmptyStringWhenSetIsEmpty() {
        Set<String> set = new HashSet<>();
        String output = Utils.joinTags(set);

        assertEquals("", output);
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
        String tags = null;
        String[] output = Utils.splitTags(tags);

        assertEquals(null, output);
    }

    @Test
    public void shouldGenerateProperCheckSum() {
        String input = "AnkiDroid";

        Long checkSum = Utils.fieldChecksum(input);
        assertEquals(Long.valueOf(3533307532l), Long.valueOf(checkSum));
    }


}
