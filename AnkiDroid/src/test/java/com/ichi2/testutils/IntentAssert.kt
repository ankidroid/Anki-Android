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

package com.ichi2.testutils;

import android.content.Intent;

import java.util.Objects;
import java.util.Set;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class IntentAssert {

    public static void doesNotHaveExtra(Intent intent, String extraKey) {
        Set<String> keySet = Objects.requireNonNull(intent.getExtras()).keySet();
        assertThat(String.format("Intent should not have extra '%s'", extraKey), keySet, not(hasItem(extraKey)));
    }


    public static void hasExtra(Intent intent, String extraKey, long value) {
        Set<String> keySet = Objects.requireNonNull(intent.getExtras()).keySet();
        assertThat(String.format("Intent should have extra '%s'", extraKey), keySet, hasItem(extraKey));

        assertThat(intent.getLongExtra(extraKey, -1337), is(value));
    }
}
