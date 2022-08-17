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

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.junit.Before;
import org.junit.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

public abstract class NoteEditorTest {
    @Rule
    public GrantPermissionRule mRuntimePermissionRule =
            GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Rule
    public ActivityScenarioRule<NoteEditor> mActivityRule = new ActivityScenarioRule<>(getNoteEditorIntent());


    @NonNull
    private Intent getNoteEditorIntent() {
        Intent intent = new Intent(getTargetContext(), NoteEditor.class);
        intent.setComponent(new ComponentName(getTargetContext(), NoteEditor.class));
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER);
        return intent;
    }

    protected NoteEditorTest() {
        // Rules mean that we get a failure on API 25.
        // Even if we ignore the tests, the rules cause a failure.
        // We can't ignore the test in @BeforeClass ("Test run failed to complete. Expected 150 tests, received 149")
        // and @Before executes after the rule.
        // So, disable the rules in the constructor, and ignore in before.
        if (getInvalidSdksImpl().contains(Build.VERSION.SDK_INT)) {
            mActivityRule = null;
            mRuntimePermissionRule = null;
        }
    }

    @Before
    public void before() {
        for (int invalid : getInvalidSdksImpl()) {
            assumeThat(String.format("Test fails on Travis API %d", invalid), Build.VERSION.SDK_INT, not(is(invalid)));
        }
    }

    protected final List<Integer> getInvalidSdksImpl() {
        // TODO: Look into these assumptions and see if they can be diagnosed - both work on my emulators.
        // If we fix them, we might be able to use instrumentation.sendKeyDownUpSync
        /*
        java.lang.AssertionError: Activity never becomes requested state "[DESTROYED]" (last lifecycle transition = "PAUSED")
        at androidx.test.core.app.ActivityScenario.waitForActivityToBecomeAnyOf(ActivityScenario.java:301)
         */
        int invalid = Build.VERSION_CODES.N_MR1;
        ArrayList<Integer> integers = new ArrayList<>(Collections.singletonList(invalid));
        integers.addAll(getInvalidSdks());
        return integers;
    }

    protected List<Integer> getInvalidSdks() {
        return new ArrayList<>();
    }

    protected Context getTargetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
}
