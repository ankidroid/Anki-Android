package com.ichi2.anki;

import android.content.SharedPreferences;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Deck;
import com.ichi2.libanki.Decks;
import com.ichi2.utils.JSONObject;

import org.apache.http.util.Asserts;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class DeckOptionsTest extends RobolectricTest {

    @Test
    public void changeHardFactor() {
        Collection col = getCol();

        // Verify that for newly created deck hardFactor is default.
        JSONObject conf = col.getConf();
        double hardFactor = conf.optDouble("hardFactor", 1.2);
        Assert.assertEquals(1.2, hardFactor, 0.01);

        // Modify hard factor.
        conf.put("hardFactor", 1.0);
        col.setConf(conf);

        // Verify that hardFactor value has changed.
        conf = col.getConf();
        hardFactor = conf.optDouble("hardFactor", 1.2);
        Assert.assertEquals(1.0, hardFactor, 0.01);
    }

}
