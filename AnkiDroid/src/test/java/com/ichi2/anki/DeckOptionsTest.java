package com.ichi2.anki;

import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONObject;

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
        // Verify that for newly created deck hardFactor is default.
        JSONObject conf = mCol.getConf();
        double hardFactor = conf.optDouble("hardFactor", 1.2);
        Assert.assertEquals(1.2, hardFactor, 0.01);

        // Modify hard factor.
        conf.put("hardFactor", 1.0);
        mCol.setConf(conf);

        // Verify that hardFactor value has changed.
        conf = mCol.getConf();
        hardFactor = conf.optDouble("hardFactor", 1.2);
        Assert.assertEquals(1.0, hardFactor, 0.01);
    }

}
