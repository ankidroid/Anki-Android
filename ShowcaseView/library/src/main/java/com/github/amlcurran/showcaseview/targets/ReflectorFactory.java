/*
 * Copyright 2014 Alex Curran
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.amlcurran.showcaseview.targets;

import android.app.Activity;

/**
 * Base class which uses reflection to determine how to showcase Action Items and Action Views.
 */
class ReflectorFactory {

    public static Reflector getReflectorForActivity(Activity activity) {
        switch (searchForActivitySuperClass(activity)) {
            case STANDARD:
                return new ActionBarReflector(activity);
            case APP_COMPAT:
                return new AppCompatReflector(activity);
            case ACTIONBAR_SHERLOCK:
                return new SherlockReflector(activity);
        }
        return null;
    }

    private static Reflector.ActionBarType searchForActivitySuperClass(Activity activity) {
        Class currentLevel = activity.getClass();
        while (currentLevel != Activity.class) {
            if (currentLevel.getSimpleName().equals("SherlockActivity") || currentLevel.getSimpleName().equals("SherlockFragmentActivity")) {
                return Reflector.ActionBarType.ACTIONBAR_SHERLOCK;
            }
            if (currentLevel.getSimpleName().equals("ActionBarActivity")) {
                return Reflector.ActionBarType.APP_COMPAT;
            }
            currentLevel = currentLevel.getSuperclass();
        }
        return Reflector.ActionBarType.STANDARD;
    }

}
