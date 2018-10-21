package com.ichi2.anki;

import android.content.res.Resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class DeckPickerTest {

    private Map<Integer, String> mCodeResponsePairs = new HashMap<>();
    private int[] mCodes;

    @Mock
    private Resources mMockResources;

    @InjectMocks
    private DeckPicker deckPicker;

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        Mockito.when(mMockResources.getString(R.string.sync_error_407_proxy_required))
                .thenReturn("Proxy authentication required.");
        Mockito.when(mMockResources.getString(R.string.sync_error_409))
                .thenReturn("Only one client can access AnkiWeb at a time. If a previous sync failed, please try again in a few minutes.");
        Mockito.when(mMockResources.getString(R.string.sync_error_413_collection_size))
                .thenReturn("Your collection or a media file is too large to sync.");
        Mockito.when(mMockResources.getString(R.string.sync_error_500_unknown))
                .thenReturn("AnkiWeb encountered an error. Please try again in a few minutes, and if the problem persists, please file a bug report.");
        Mockito.when(mMockResources.getString(R.string.sync_error_501_upgrade_required))
                .thenReturn("Please upgrade to the latest version of AnkiDroid");
        Mockito.when(mMockResources.getString(R.string.sync_error_502_maintenance))
                .thenReturn("AnkiWeb is under maintenance. Please try again in a few minutes.");
        Mockito.when(mMockResources.getString(R.string.sync_too_busy))
                .thenReturn("Server busy. Try again later.");
        Mockito.when(mMockResources.getString(R.string.sync_error_504_gateway_timeout))
                .thenReturn("504 gateway timeout error received.");

        mCodes = new int[] {407, 409, 413, 500, 501, 502, 503, 504};

        mCodeResponsePairs.put(407, "Proxy authentication required.");
        mCodeResponsePairs.put(409, "Only one client can access AnkiWeb at a time. If a previous sync failed, please try again in a few minutes.");
        mCodeResponsePairs.put(413, "Your collection or a media file is too large to sync.");
        mCodeResponsePairs.put(500, "AnkiWeb encountered an error. Please try again in a few minutes, and if the problem persists, please file a bug report.");
        mCodeResponsePairs.put(501, "Please upgrade to the latest version of AnkiDroid");
        mCodeResponsePairs.put(502, "AnkiWeb is under maintenance. Please try again in a few minutes.");
        mCodeResponsePairs.put(503, "Server busy. Try again later.");
        mCodeResponsePairs.put(504, "504 gateway timeout error received.");
    }

    @Test
    public void verifyCodeMessages() {
        // verify the all codes return the correct strings
        for (int code : mCodes) {
            String msg = deckPicker.rewriteError(code);
            assertEquals(mCodeResponsePairs.get(code), msg);
        }
    }

    @Test
    public void verifyBadCodesNoMessage() {
        // verify the unknown codes always return null
        String msg = "";
        msg = deckPicker.rewriteError(0);
        assertNull(msg);

        msg = "";
        msg = deckPicker.rewriteError(-1);
        assertNull(msg);

        msg = "";
        msg = deckPicker.rewriteError(1);
        assertNull(msg);

        msg = "";
        msg = deckPicker.rewriteError(Integer.MIN_VALUE);
        assertNull(msg);

        msg = "";
        msg = deckPicker.rewriteError(Integer.MAX_VALUE);
        assertNull(msg);
    }

    @After
    public void validate() {
        Mockito.validateMockitoUsage();
    }
}
