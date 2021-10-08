package com.ichi2.anki;

import com.ichi2.utils.LanguageUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import java.util.Arrays;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class InfoTest extends RobolectricTest {

    @Parameter
    public String locale;

    @Parameters(name = "{index} locale:{0}")
    public static java.util.Collection<String> initParameters() {
        return Arrays.asList(LanguageUtil.APP_LANGUAGES);
    }

    @Before
    public void before() {
        if (locale.contains("-")) {
            String[] localeParts = locale.split("-");
            RuntimeEnvironment.setQualifiers(localeParts[0] + "-r" + localeParts[1]);
        } else {
            RuntimeEnvironment.setQualifiers(locale);
        }
    }

    @Test
    public void testCreatingActivityWithLocale() {
        ActivityController<Info> infoController = Robolectric.buildActivity(Info.class).create();
        saveControllerForCleanup(infoController);
    }
}
