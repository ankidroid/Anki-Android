package com.ichi2.anki;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.content.SharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

import java.util.HashMap;
import java.util.Map;

import static com.ichi2.anki.DeckPicker.UPGRADE_VERSION_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class DeckPickerTest extends RobolectricTest {

    @Test
    public void verifyCodeMessages() {

        Map<Integer, String> mCodeResponsePairs = new HashMap<>();
        final Context context = getTargetContext();
        mCodeResponsePairs.put(407, context.getString(R.string.sync_error_407_proxy_required));
        mCodeResponsePairs.put(409, context.getString(R.string.sync_error_409));
        mCodeResponsePairs.put(413, context.getString(R.string.sync_error_413_collection_size));
        mCodeResponsePairs.put(500, context.getString(R.string.sync_error_500_unknown));
        mCodeResponsePairs.put(501, context.getString(R.string.sync_error_501_upgrade_required));
        mCodeResponsePairs.put(502, context.getString(R.string.sync_error_502_maintenance));
        mCodeResponsePairs.put(503, context.getString(R.string.sync_too_busy));
        mCodeResponsePairs.put(504, context.getString(R.string.sync_error_504_gateway_timeout));

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                for (Map.Entry<Integer, String> entry : mCodeResponsePairs.entrySet()) {
                    assertEquals(deckPicker.rewriteError(entry.getKey()), entry.getValue());
                }
            });
        }
    }

    @Test
    public void verifyBadCodesNoMessage() {
        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                assertNull(deckPicker.rewriteError(0));
                assertNull(deckPicker.rewriteError(-1));
                assertNull(deckPicker.rewriteError(1));
                assertNull(deckPicker.rewriteError(Integer.MIN_VALUE));
                assertNull(deckPicker.rewriteError(Integer.MAX_VALUE));
            });
        }
    }

    @Test
    public void getPreviousVersion_from201to292() {
        int newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.1");

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                int previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(0, previousVersion);
            });
        }
    }

    @Test
    public void getPreviousVersion_from202to292() {
        int newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.2");

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                int previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(40, previousVersion);
            });
        }
    }

    @Test
    public void getPreviousVersion_version203() {
        int prevVersion = 20030301; // 2.0.3
        int newVersion = 20900302;  // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenReturn(prevVersion);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                int previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
    }

    @Test
    public void getPreviousVersion_version291() {
        int prevVersion = 20800301; // 2.8.1
        int newVersion = 20900301;  // 2.9.1

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenReturn(prevVersion);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                int previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
    }
}
