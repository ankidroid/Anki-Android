/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class NoteEditorTest {

    @Rule public ActivityScenarioRule<NoteEditor> activityRule = new ActivityScenarioRule<>(getStartActivityIntent());
    @Rule public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(WRITE_EXTERNAL_STORAGE);

    @NonNull
    private Intent getStartActivityIntent() {
        Intent intent = new Intent(getTargetContext(), NoteEditor.class);
        intent.setComponent(new ComponentName(getTargetContext(), NoteEditor.class));
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        return intent;
    }

    @Test
    public void testTabOrder() throws Throwable {
        // TODO: Look into these assumptions and see if they can be diagnosed - both work on my emulators.
        // If we fix them, we might be able to use instrumentation.sendKeyDownUpSync
        /*
        java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
        at androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:301)
         */
        assumeThat("Test fails on Travis API 25", Build.VERSION.SDK_INT, not(is(25)));
        /*
        java.lang.AssertionError:
        Expected: is "a"
         */
        assumeThat("Test fails on Travis API 30", Build.VERSION.SDK_INT, not(is(30)));

        ensureCollectionLoaded();
        ActivityScenario<NoteEditor> scenario = activityRule.getScenario();
        scenario.moveToState(Lifecycle.State.RESUMED);

        onActivity(scenario, editor -> {
            sendKeyDownUp(editor, KeyEvent.KEYCODE_A);
            sendKeyDownUp(editor, KeyEvent.KEYCODE_TAB);
            sendKeyDownUp(editor, KeyEvent.KEYCODE_TAB);
            sendKeyDownUp(editor, KeyEvent.KEYCODE_B);
        });

        onActivity(scenario, editor -> {
            String[] currentFieldStrings = editor.getCurrentFieldStrings();
            assertThat(currentFieldStrings[0], is("a"));
            assertThat(currentFieldStrings[1], is("b"));
        });

    }


    protected void sendKeyDownUp(Activity activity, int keyCode) {
        BaseInputConnection mInputConnection = new BaseInputConnection(activity.getCurrentFocus(), true);
        mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }


    protected void onActivity(ActivityScenario<NoteEditor> scenario, ActivityScenario.ActivityAction<NoteEditor> noteEditorActivityAction) throws Throwable {
        AtomicReference<Throwable> wrapped = new AtomicReference<>(null);
        scenario.onActivity(a -> {
            try {
                noteEditorActivityAction.perform(a);
            } catch (Throwable t) {
                wrapped.set(t);
            }
        });
        if (wrapped.get() != null) {
            throw wrapped.get();
        }
    }


    private void ensureCollectionLoaded() {
        CollectionHelper.getInstance().getCol(getTargetContext());
    }


    private Context getTargetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
