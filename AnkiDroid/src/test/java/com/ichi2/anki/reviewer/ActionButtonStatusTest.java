/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer;

import android.content.Intent;
import android.content.SharedPreferences;

import com.ichi2.anki.Preferences;
import com.ichi2.anki.RobolectricTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ActionButtonStatusTest extends RobolectricTest {

    @Test
    public void allCustomButtonsCanBeDisabled() {
        Set<String> reviewerExpectedKeys = getCustomButtonsExpectedKeys();
        Set<String> actualPreferenceKeys = getAllCustomButtonPreferenceKeys();

        assertThat("Each button in the Action Bar must be modifiable in Preferences - Reviewer - App Bar Buttons",
                reviewerExpectedKeys,
                containsInAnyOrder(Objects.requireNonNull(actualPreferenceKeys.toArray())));
    }


    private Set<String> getCustomButtonsExpectedKeys() {
        SharedPreferences preferences = mock(SharedPreferences.class);
        Set<String> ret = new HashSet<>();
        when(preferences.getString(any(), any())).then(a -> {
            String key = a.getArgument(0);
            ret.add(key);
            return "0";
        }
        );
        ActionButtonStatus status = new ActionButtonStatus(mock(ReviewerUi.class));
        status.setup(preferences);

        return ret;
    }


    private Set<String> getAllCustomButtonPreferenceKeys() {
        AtomicReference<Set<String>> ret = new AtomicReference<>();
        Intent i = Preferences.getPreferenceSubscreenIntent(getTargetContext(), "com.ichi2.anki.prefs.custom_buttons");
        try (ActivityScenario<Preferences> scenario = ActivityScenario.launch(i)) {
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.onActivity(a -> ret.set(a.getLoadedPreferenceKeys()));
        }
        Set<String> preferenceKeys = ret.get();
        if (preferenceKeys == null) {
            throw new IllegalStateException("no keys were set");
        }
        preferenceKeys.remove("reset_custom_buttons");
        return preferenceKeys;
    }
}
