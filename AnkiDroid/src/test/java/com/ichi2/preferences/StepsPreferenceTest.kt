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
        // Conversion of JSONArray() to String
        // Any input should get the exact same output in the String format
        assertThat("A integer should return the same integer as a String", StepsPreference.convertFromJSON(JSONArray().put(1) as JSONArray), IsEqual("1"))
        assertThat("The conversion of multiple entries of a JSONArray to a String", StepsPreference.convertFromJSON(JSONArray().put(-1).put(1.0) as JSONArray), IsEqual("-1 1.0"))
        assertThat("A non-numeric should return the same non-numeric as a String", StepsPreference.convertFromJSON(JSONArray().put("A") as JSONArray), IsEqual("A"))
    }

    @Test
    fun convertToJSON() {
        // When the Input String is Null
        assertThat("Empty string should return empty JSONArray()", StepsPreference.convertToJSON(""), IsEqual(JSONArray()))

        // When the input String (Step) is less than or equal to 0 (i.e step<=0)
        assertThat("The string cannot be equal to zero (returns null)", StepsPreference.convertToJSON("0"), IsEqual(null))
        assertThat("The string cannot contain negative (returns null)", StepsPreference.convertToJSON("-1"), IsEqual(null))

        // When the input String (Step) is greater than 0
        assertThat("A integer should return the same integer in the JSONArray", StepsPreference.convertToJSON("1"), IsEqual(JSONArray().put(1)))
        assertThat("A float in x.0 format should return the integer value x", StepsPreference.convertToJSON("1.0"), IsEqual(JSONArray().put(1)))
        assertThat("A float should return the same float value in the JSONArray", StepsPreference.convertToJSON("1.1"), IsEqual(JSONArray().put(1.1)))
        assertThat("Multiple entries in the string should return multiple numbers in the JSONArray", StepsPreference.convertToJSON("1.1 2"), IsEqual(JSONArray().put(1.1).put(2)))

        // Exceptions
        assertThat("A non-numeric string should return null", StepsPreference.convertToJSON("A"), IsEqual(null))
    }
}
