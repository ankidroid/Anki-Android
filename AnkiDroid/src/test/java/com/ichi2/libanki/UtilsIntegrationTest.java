package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.testutils.NullApplication;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import androidx.annotation.CheckResult;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
@Config(application = NullApplication.class, qualifiers = "en")
public class UtilsIntegrationTest extends RobolectricTest {

    @Test
    public void deckPickerTimeOneAndHalfHours() {
        int oneAndAHalfHours = 60 * 90;

        String actual = deckPickerTime(oneAndAHalfHours);

        assertThat(actual, is("1 h 30 m"));
    }

    @Test
    public void deckPickerTimeOneHour() {
        int oneAndAHalfHours = 60 * 60;

        String actual = deckPickerTime(oneAndAHalfHours);

        assertThat(actual, is("1 h 0 m"));
    }

    @Test
    public void deckPickerTime60Seconds() {
        int oneAndAHalfHours = 60;

        String actual = deckPickerTime(oneAndAHalfHours);

        assertThat(actual, is("1 min"));
    }

    @Test
    public void deckPickerTimeOneAndAHalfDays() {
        int oneAndAHalfHours = 60 * 60 * 36;

        String actual = deckPickerTime(oneAndAHalfHours);

        assertThat(actual, is("1 d 12 h"));
    }


    @Test
    @Config(qualifiers = "en")
    public void timeQuantityMonths() {
        // Anki Desktop 2.1.30: '\u206810.8\u2069 months'
        assertThat(timeQuantityNextInterval(28080000), is("10.8 mo"));
    }


    @NotNull
    private String timeQuantityNextInterval(@SuppressWarnings("SameParameterValue") int time_s) {
        return Utils.timeQuantityNextIvl(getTargetContext(), time_s);
    }


    @NotNull
    @CheckResult
    private String deckPickerTime(long time) {
        return Utils.timeQuantityTopDeckPicker(this.getTargetContext(), time);
    }
}
