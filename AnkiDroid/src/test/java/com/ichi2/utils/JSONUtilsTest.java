package com.ichi2.utils;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class JSONUtilsTest {

    @Test
    public void validValueIsReturned() {
        JSONObject object = new JSONObject();
        object.put("max", 1);

        int result = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 0);

        assertThat("A valid value should be returned", result, is(1));
    }

    @Test
    public void nullJsonObjectWorksCorrectly() {
        JSONObject object = null;

        @SuppressWarnings("ConstantConditions")
        int result = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 1);

        assertThat("A null JsonObject should still return the default", result, is(1));
    }

    @Test
    public void nullValueReturnsDefault() {
        JSONObject object = new JSONObject();
        object.put("max", null);

        int result = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 1);

        assertThat("A valid value should be returned", result, is(1));
    }

    @Test
    public void nullValueIsSet() {
        JSONObject object = new JSONObject();
        object.put("max", null);

        @SuppressWarnings("unused")
        int unused = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 1);
        int actualProperty = object.getInt("max");

        assertThat("A previously null value should be set", actualProperty, is(1));
    }

    @Test
    public void invalidValueReturnsDefault() {
        JSONObject object = new JSONObject();
        object.put("max", null);

        int actual = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 1);


        assertThat("Default should be returned if value is invalid", actual, is(1));
    }

    @Test
    public void invalidValueIsNotFixed() {
        JSONObject object = new JSONObject();
        object.put("max", "power");

        @SuppressWarnings("unused")
        int unused = JSONUtils.getIntOrSetDefaultWithWarn(object, "max", 1);
        String actualProperty = object.getString("max");

        assertThat("An invalid property will not be mutated", actualProperty, is("power"));
    }
}
