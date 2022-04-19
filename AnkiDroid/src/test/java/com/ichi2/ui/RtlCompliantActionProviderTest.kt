/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
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

package com.ichi2.ui;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class RtlCompliantActionProviderTest {


    @Test
    public void test_unwrapContext_will_get_activity() {

        Activity a = new Activity();
        Context c = new ContextWrapper(
                new ContextThemeWrapper(
                        new ContextWrapper(
                                a
                        ),
                        0
                )
        );

        RtlCompliantActionProvider provider = new RtlCompliantActionProvider(c);
        assertEquals(provider.mActivity, a);
    }


    @Test
    public void test_unwrapContext_will_throw_on_no_activity() {

        Application a = new Application();
        Context c = new ContextWrapper(
                new ContextThemeWrapper(
                        new ContextWrapper(
                                a
                        ),
                        0
                )
        );

        try {
            RtlCompliantActionProvider provider = new RtlCompliantActionProvider(c);
        } catch (Exception e) {
            assertThat(e, instanceOf(ClassCastException.class));
            return;
        }

        fail("unwrapContext should have thrown a ClassCastException, because the base context is not an activity");
    }

}