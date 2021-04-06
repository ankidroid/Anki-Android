/*
 Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

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
package com.ichi2.utils;

import org.junit.Test;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FragmentFactoryUtilsTest {


    private static class TestFragment extends Fragment {}

    @Test
    public void test_instantiate() {
        FragmentActivity activity = mock(FragmentActivity.class);
        FragmentManager manager = mock(FragmentManager.class);
        FragmentFactory factory = mock(FragmentFactory.class);
        ClassLoader classLoader = mock(ClassLoader.class);

        TestFragment testFragment = new TestFragment();

        when(activity.getSupportFragmentManager()).thenReturn(manager);
        when(activity.getClassLoader()).thenReturn(classLoader);

        when(manager.getFragmentFactory()).thenReturn(factory);
        when(factory.instantiate(classLoader, testFragment.getClass().getName()))
                .thenReturn(testFragment);


        Fragment result = FragmentFactoryUtils.instantiate(activity, TestFragment.class);
        assertEquals(testFragment, result);
        verify(factory, times(1)).instantiate(classLoader, testFragment.getClass().getName());
    }
}