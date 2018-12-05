/****************************************************************************************
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>                                   *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.mockito.internal.util.reflection.Fields;
import org.mockito.internal.util.reflection.InstanceField;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowViewGroup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.robolectric.shadow.api.Shadow.directlyOn;

/**
 * This only exists as a workaround for a Robolectric bug with Fragments and ViewPagers - it can be deleted
 * and the relevant @Config( shadows = { ShadowViewPager.class }) entries may be removed once Robolectric is fixed
 * https://github.com/robolectric/robolectric/issues/3698#issuecomment-441839491
 */
@Implements(ViewPager.class)
public class ShadowViewPager extends ShadowViewGroup {

    @RealObject
    protected ViewPager realViewPager;

    @Implementation
    public void setAdapter(PagerAdapter adapter) {
        directlyOn(realViewPager, ViewPager.class).setAdapter(addWorkaround(adapter));
    }

    private PagerAdapter addWorkaround(PagerAdapter adapter) {
        PagerAdapter spied = spy(adapter);
        FragmentManager fragmentManager = getFragmentManagerFromAdapter(spied);
        doAnswer(invocation -> {
            if (fragmentManager.getFragments().isEmpty())
                invocation.callRealMethod();
            return null;
        }).when(spied).finishUpdate(any());
        return spied;
    }

    private FragmentManager getFragmentManagerFromAdapter(PagerAdapter adapter) {
        for (InstanceField instanceField : Fields.allDeclaredFieldsOf(adapter).instanceFields()) {
            Object obj = instanceField.read();
            if (obj instanceof FragmentManager) {
                return (FragmentManager) obj;
            }
        }
        return null;
    }
}