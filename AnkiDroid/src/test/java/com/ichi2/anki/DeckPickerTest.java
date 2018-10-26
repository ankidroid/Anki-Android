package com.ichi2.anki;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class DeckPickerTest {

    @Test
    public void verifyCodeMessages() {

        Map<Integer, String> mCodeResponsePairs = new HashMap<>();
        final Context context = ApplicationProvider.getApplicationContext();
        mCodeResponsePairs.put(407, context.getString(R.string.sync_error_407_proxy_required));
        mCodeResponsePairs.put(409, context.getString(R.string.sync_error_409));
        mCodeResponsePairs.put(413, context.getString(R.string.sync_error_413_collection_size));
        mCodeResponsePairs.put(500, context.getString(R.string.sync_error_500_unknown));
        mCodeResponsePairs.put(501, context.getString(R.string.sync_error_501_upgrade_required));
        mCodeResponsePairs.put(502, context.getString(R.string.sync_error_502_maintenance));
        mCodeResponsePairs.put(503, context.getString(R.string.sync_too_busy));
        mCodeResponsePairs.put(504, context.getString(R.string.sync_error_504_gateway_timeout));

        DeckPicker deckPicker = Robolectric.setupActivity(NoDatabaseDeckPicker.class);
        for (Map.Entry<Integer, String> entry : mCodeResponsePairs.entrySet()) {
            assertEquals(deckPicker.rewriteError(entry.getKey()), entry.getValue());
        }
    }

    @Test
    public void verifyBadCodesNoMessage() {
        DeckPicker deckPicker = Robolectric.setupActivity(NoDatabaseDeckPicker.class);
        assertNull(deckPicker.rewriteError(0));
        assertNull(deckPicker.rewriteError(-1));
        assertNull(deckPicker.rewriteError(1));
        assertNull(deckPicker.rewriteError(Integer.MIN_VALUE));
        assertNull(deckPicker.rewriteError(Integer.MAX_VALUE));
    }
}
