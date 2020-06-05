package com.ichi2.libanki;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.libanki.Note.ClozeUtils.getNextClozeIndex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class NoteTest {
    @Test
    public void noFieldDataReturnsFirstClozeIndex() {
        int expected = getNextClozeIndex(Collections.emptyList());

        assertThat("No data should return a cloze index of 1 the next.", expected, is(1));
    }

    @Test
    public void negativeFieldIsIgnored() {
        String fieldValue = "{{c-1::foo}}";
        int actual = getNextClozeIndex(Collections.singletonList(fieldValue));

        assertThat("The next consecutive value should be returned.", actual, is(1));
    }

    @Test
    public void singleFieldReturnsNextValue() {
        String fieldValue = "{{c2::bar}}{{c1::foo}}";
        int actual = getNextClozeIndex(Collections.singletonList(fieldValue));

        assertThat("The next consecutive value should be returned.", actual, is(3));
    }

    @Test
    public void multiFieldIsHandled() {
        List<String> fields = Arrays.asList("{{c1::foo}}", "{{c2::bar}}");
        int actual = getNextClozeIndex(fields);

        assertThat("The highest of all fields should be used.", actual, is(3));
    }

    @Test
    public void missingFieldIsSkipped() {
        //this mimics Anki Desktop
        List<String> fields = Arrays.asList("{{c1::foo}}", "{{c3::bar}}{{c4::baz}}");
        int actual = getNextClozeIndex(fields);

        assertThat("A missing cloze index should not be selected if there are higher values.", actual, is(5));

    }
}
