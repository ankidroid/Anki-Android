package com.ichi2.anki

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldEditTextTest {
    val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun verifySameInstanceStateAfterSaveAndReload() {
        val dummyOrdValue = 5
        assertThat("dummy ord value must not be 0 for test validity", dummyOrdValue, not(0))
        val fieldEditText = FieldEditText(targetContext)
        fieldEditText.ord = dummyOrdValue
        val save = fieldEditText.onSaveInstanceState()
        fieldEditText.onRestoreInstanceState(save!!)
        assertThat("Ord should be the same after saving and reloading", fieldEditText.ord, equalTo(dummyOrdValue))
    }

    @Test
    fun verifySameInstanceStateWhenLoadedStateDiffersFromCurrentState() {
        val dummyOrdValue = 7
        assertThat("dummy ord value must not be 0 for test validity", dummyOrdValue, not(0))
        val resetOrdValue = 8
        assertThat("reset ord value must not be 0 for test validity", resetOrdValue, not(0))
        assertThat("dummy and reset ord values must not be the same for test validity", resetOrdValue, not(dummyOrdValue))

        val fieldEditText = FieldEditText(targetContext)
        fieldEditText.ord = dummyOrdValue
        val save = fieldEditText.onSaveInstanceState()

        fieldEditText.ord = resetOrdValue
        assertThat("ord has been reset to a different value", fieldEditText.ord, not(dummyOrdValue))

        fieldEditText.onRestoreInstanceState(save!!)
        assertThat("ord should be the same as the state that is loaded", fieldEditText.ord, equalTo(dummyOrdValue))
    }

    @Test
    fun verifyThatDifferentObjectLoadedFromSameInstanceStateMatchesInstanceState() {
        val originalOrdValue = 5
        assertThat("original ord value must not be 0 for test validity", originalOrdValue, not(0))

        val fieldEditText = FieldEditText(targetContext)
        fieldEditText.ord = originalOrdValue
        val save = fieldEditText.onSaveInstanceState()

        fieldEditText.onRestoreInstanceState(save!!)

        val newFieldEditText = FieldEditText(targetContext)
        assertThat("new object should not have ord initialized to originalOrdValue", newFieldEditText.ord, not(originalOrdValue))

        newFieldEditText.onRestoreInstanceState(save)
        assertThat("new instance with the same context should have the same ord", newFieldEditText.ord, equalTo(originalOrdValue))
    }
}
