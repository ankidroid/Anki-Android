package com.ichi2.preferences

import com.ichi2.utils.JSONArray
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StepsPreferenceTest {

    @Test
    fun convertFromJSON() {
        assertThat(StepsPreference.convertFromJSON(JSONArray().put(1) as JSONArray), IsEqual("1"))
        assertThat(StepsPreference.convertFromJSON(JSONArray().put(-1).put(1.0) as JSONArray), IsEqual("-1 1.0"))
        assertThat(StepsPreference.convertFromJSON(JSONArray().put("A") as JSONArray), IsEqual("A"))
    }

    @Test
    fun convertToJSON() {

        // When the Input String is Null
        assertThat(StepsPreference.convertToJSON(""), IsEqual(JSONArray()))

        // When the input String (Step) is less than or equal to 0 (i.e step<=0)
        assertThat(StepsPreference.convertToJSON("0"), IsEqual(null))
        assertThat(StepsPreference.convertToJSON("0.0"), IsEqual(null))
        assertThat(StepsPreference.convertToJSON("-1"), IsEqual(null))

        // When the input String (Step) is greater than 0
        assertThat(StepsPreference.convertToJSON("1"), IsEqual(JSONArray().put(1))) // Testing the integers
        assertThat(StepsPreference.convertToJSON("1.0"), IsEqual(JSONArray().put(1))) // Testing the conversion of decimal to integer
        assertThat(StepsPreference.convertToJSON("1.1"), IsEqual(JSONArray().put(1.1))) // Testing the decimals
        assertThat(StepsPreference.convertToJSON("1.1 2"), IsEqual(JSONArray().put(1.1).put(2))) // Testing multiple entries

        // Exceptions
        assertThat(StepsPreference.convertToJSON("A"), IsEqual(null)) // Checking for non-numbers
    }
}
