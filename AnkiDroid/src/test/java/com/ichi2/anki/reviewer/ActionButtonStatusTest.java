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

import android.content.SharedPreferences;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.testutils.PreferenceUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        Set<String> actualPreferenceKeys = PreferenceUtils.getAllCustomButtonKeys(getTargetContext());
        reviewerExpectedKeys.add("customButtonLookup"); // preference isn't read if Lookup is disabled.

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
}
