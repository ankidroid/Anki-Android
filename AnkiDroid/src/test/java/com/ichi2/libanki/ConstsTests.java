package com.ichi2.libanki;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static com.ichi2.libanki.Consts.BUTTON_TYPE.*;

@RunWith(AndroidJUnit4.class)
public class ConstsTests {
    @Test
    public void testButtonToString() {
        assertThat(BUTTON_ONE.toString(), is("1"));
        assertThat(BUTTON_TWO.toString(), is("2"));
        assertThat(BUTTON_THREE.toString(), is("3"));
        assertThat(BUTTON_FOUR.toString(), is("4"));
    }
}
