package com.ichi2.anki;

import com.ichi2.libanki.Collection;
import com.ichi2.testutils.FastAnkiDroidApp;
import com.ichi2.utils.JSONObject;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
@Config(application = FastAnkiDroidApp.class)
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
