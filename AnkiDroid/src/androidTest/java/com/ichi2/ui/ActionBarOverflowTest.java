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
package com.ichi2.ui;

import android.content.Context;
import android.view.MenuItem;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class ActionBarOverflowTest {

    @Test
    public void hasValidActionBarReflectionMethod() {
        assertThat("Ensures that there is a valid way to obtain a listener",
                ActionBarOverflow.hasUsableMethod(), is(true));
    }

    @Test
    public void errorsAreBeingThrownCanary() {
        try {
            ActionBarOverflow.setupMethods(ActionBarOverflow::getPrivateMethodOnlyHandleExceptions);
        } catch (Error e) {
            fail("See discussion on #5806\n" +
                    "https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces\n" +
                    "Once this throws, errors are being thrown on a currently greylisted method");
        }
    }

    @Test
    public void testAndroidXMenuItem() {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MenuBuilder b = new MenuBuilder(targetContext);
        MenuItem i = b.add("Test");

        Boolean value = ActionBarOverflow.isActionButton(i);

        assertThat(value, notNullValue());
    }

    @Test
    public void testAndroidMenuItem() throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String clazz = "com.android.internal.view.menu.MenuBuilder";
        Class<?> c = Class.forName(clazz);

        Constructor<?> constructor = c.getConstructor(Context.class);

        Object mmb = constructor.newInstance(targetContext);

        Method add = mmb.getClass().getMethod("add", CharSequence.class);
        add.setAccessible(true);

        MenuItem mi = (MenuItem) add.invoke(mmb, "Add");

        Boolean value = ActionBarOverflow.isActionButton(mi);

        assertThat(value, notNullValue());
    }
}
