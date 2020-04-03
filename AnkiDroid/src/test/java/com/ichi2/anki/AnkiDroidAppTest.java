package com.ichi2.anki;


import com.ichi2.testutils.AnkiAssert;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AnkiDroidApp.sendExceptionReport;

@RunWith(AndroidJUnit4.class)
public class AnkiDroidAppTest {

    @Test
    public void reportingDoesNotThrowException() {
        AnkiAssert.assertDoesNotThrow(() -> sendExceptionReport("Test", "AnkiDroidAppTest"));
    }

    @Test
    public void reportingWithNullMessageDoesNotFail() {
        String message = null;
        //It's meant to be non-null, but it's developer-defined, and we don't want a crash in the reporting dialog
        //noinspection ConstantConditions
        AnkiAssert.assertDoesNotThrow(() -> sendExceptionReport(message, "AnkiDroidAppTest"));
    }
}
