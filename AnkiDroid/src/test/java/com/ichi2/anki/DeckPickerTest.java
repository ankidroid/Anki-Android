package com.ichi2.anki;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.DeckConfig;
import com.ichi2.libanki.sched.AbstractSched;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static com.ichi2.anki.DeckPicker.UPGRADE_VERSION_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
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
    public void getPreviousVersionUpgradeFrom201to292() {
        int newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.1");

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(0, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom202to292() {
        long newVersion = 20900302; // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, 20900203)).thenThrow(ClassCastException.class);
        when(preferences.getString(UPGRADE_VERSION_KEY, "")).thenReturn("2.0.2");

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(40, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom281to291() {
        int prevVersion = 20800301; // 2.8.1
        long newVersion = 20900301; // 2.9.1

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenThrow(ClassCastException.class);
        when(preferences.getInt(UPGRADE_VERSION_KEY, 20900203)).thenReturn(prevVersion);

        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);
        Editor updated = mock(Editor.class);
        when(editor.remove(UPGRADE_VERSION_KEY)).thenReturn(updated);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
        verify(editor, times(1)).remove(UPGRADE_VERSION_KEY);
        verify(updated, times(1)).apply();
    }

    @Test
    public void getPreviousVersionUpgradeFrom291to292() {
        long prevVersion = 20900301; // 2.9.1
        long newVersion = 20900302;  // 2.9.2

        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getLong(UPGRADE_VERSION_KEY, newVersion)).thenReturn(prevVersion);
        Editor editor = mock(Editor.class);
        when(preferences.edit()).thenReturn(editor);

        try (ActivityScenario<DeckPicker> scenario = ActivityScenario.launch(DeckPicker.class)) {
            scenario.onActivity(deckPicker -> {
                long previousVersion = deckPicker.getPreviousVersion(preferences, newVersion);
                assertEquals(prevVersion, previousVersion);
            });
        }
        verify(editor, never()).remove(UPGRADE_VERSION_KEY);
    }

    @Test
    public void limitAppliedAfterReview() {
        Collection col = getCol();
        AbstractSched sched = col.getSched();

        DeckConfig dconf = col.getDecks().getConf(1);
        dconf.getJSONObject("new").put("perDay", 10);
        for (int i = 0; i < 11; i++) {
            addNoteUsingBasicModel("Which card is this ?", Integer.toString(i));
        }
        // This set a card as current card
        sched.getCard();

        ensureCollectionLoadIsSynchronous();
        DeckPicker deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());

        assertEquals(10, deckPicker.mDueTree.get(0).getNewCount());
    }

    @Test
    public void confirmDeckDeletionDeletesEmptyDeck() {
        long did = addDeck("Hello World");

        assertThat("Deck was added", getCol().getDecks().count(), is(2));

        DeckPicker deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker.class, new Intent());

        deckPicker.confirmDeckDeletion(did);

        advanceRobolectricLooperWithSleep();

        assertThat("deck was deleted", getCol().getDecks().count(), is(1));
    }
}
