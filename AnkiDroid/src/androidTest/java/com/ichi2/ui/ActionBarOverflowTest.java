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
                    "Once this throws, errors are being thrown on a currently graylisted method");
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
