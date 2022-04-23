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
package com.ichi2.utils;

import com.ichi2.testutils.MockFragmentClassLoader;

import org.junit.Test;

import javax.annotation.Nonnull;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExtendedFragmentFactoryTest {


    @Nonnull
    private static final ClassLoader fakeClassLoader = new MockFragmentClassLoader();

    static class TestFragmentFactoryTest extends ExtendedFragmentFactory {
        public TestFragmentFactoryTest() {
        }
        public TestFragmentFactoryTest(@NonNull FragmentFactory baseFactory) {
            super(baseFactory);
        }
    }


    @Test
    public void willCallBaseFactory() {
        final FragmentFactory baseFF = mock(FragmentFactory.class);
        final ExtendedFragmentFactory testFF = new TestFragmentFactoryTest(baseFF);

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);

        verify(baseFF, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);
    }


    @Test
    public void testAttachToActivity() {
        final AppCompatActivity activity = mock(AppCompatActivity.class);
        final FragmentManager fragmentManager = mock(FragmentManager.class);
        final FragmentFactory baseFactory = mock(FragmentFactory.class);

        when(activity.getSupportFragmentManager()).thenReturn(fragmentManager);
        when(fragmentManager.getFragmentFactory()).thenReturn(baseFactory);

        final ExtendedFragmentFactory testFF = new TestFragmentFactoryTest();

        final ExtendedFragmentFactory result = testFF.attachToActivity(activity);

        assertEquals(testFF, result);

        verify(fragmentManager, times(1)).setFragmentFactory(testFF);

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);

        verify(baseFactory, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);
    }


    @Test
    public void testAttachToFragmentManager() {
        final FragmentManager fragmentManager = mock(FragmentManager.class);
        final FragmentFactory baseFactory = mock(FragmentFactory.class);

        when(fragmentManager.getFragmentFactory()).thenReturn(baseFactory);

        final ExtendedFragmentFactory testFF = new TestFragmentFactoryTest();

        final ExtendedFragmentFactory result = testFF.attachToFragmentManager(fragmentManager);

        assertEquals(testFF, result);

        verify(fragmentManager, times(1)).setFragmentFactory(testFF);

        testFF.instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);

        verify(baseFactory, times(1)).instantiate(fakeClassLoader, MockFragmentClassLoader.FAKE_CLASS_NAME);
    }
}
