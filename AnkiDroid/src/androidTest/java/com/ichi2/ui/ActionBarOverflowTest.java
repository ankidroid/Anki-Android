package com.ichi2.ui;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4.class)
public class ActionBarOverflowTest {

    @Test
    public void hasValidActionBarReflectionMethod() {
        assertThat("Ensures that there is a valid way to obtain a listener",
                ActionBarOverflow.hasUsableMethod(), is(true));
    }

    @Test
    public void errorsAreBeingThrownCanary() {
        //See discussion on #5806
        //https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces
        //Once this throws, errors are being thrown on a currently graylisted method
        ActionBarOverflow.setupMethods(ActionBarOverflow::getPrivateMethodOnlyHandleExceptions);
    }
}
