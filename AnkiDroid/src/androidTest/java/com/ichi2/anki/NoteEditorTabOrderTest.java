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
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class NoteEditorTabOrderTest extends NoteEditorTest {
    @Override
    protected List<Integer> getInvalidSdks() {
        /*
        java.lang.AssertionError:
        Expected: is "a"
         */
        return Collections.singletonList(30);
    }


    @Test
    @Ignore("flaky on API 21 as well: " +
            "com.ichi2.anki.NoteEditorTabOrderTest > testTabOrder[test(AVD) - 5.1.1] FAILED \n" +
            "\n" +
            "\tjava.lang.AssertionError:\n" +
            "\n" +
            "\tExpected: is \"a\"")
    public void testTabOrder() throws Throwable {
        ensureCollectionLoaded();
        ActivityScenario<NoteEditor> scenario = mActivityRule.getScenario();
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
}
