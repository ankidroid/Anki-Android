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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.ichi2.anki.DeckPicker;
import com.ichi2.utils.FunctionalInterfaces.Function;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.util.Collections;
import java.util.List;

import androidx.annotation.CheckResult;

import static com.ichi2.testutils.ActivityList.ActivityLaunchParam.get;

public class ActivityList {
    @CheckResult
    public static List<ActivityLaunchParam> allActivitiesAndIntents() {
        return Collections.singletonList(
                get(DeckPicker.class)
        );
    }

    public static class ActivityLaunchParam {
        public Class<? extends Activity> mActivity;
        public Function<Context, Intent> mIntentBuilder;


        public ActivityLaunchParam(Class<? extends Activity> clazz, Function<Context, Intent> intent) {
            mActivity = clazz;
            mIntentBuilder = intent;
        }


        public static ActivityLaunchParam get(Class<? extends Activity> clazz) {
            return get(clazz, c -> new Intent());
        }

        public static ActivityLaunchParam get(Class<? extends Activity> clazz, Function<Context, Intent> i) {
            return new ActivityLaunchParam(clazz, i);
        }

        public String getSimpleName() {
            return mActivity.getSimpleName();
        }


        public ActivityController<? extends Activity> build(Context context) {
            return Robolectric.buildActivity(mActivity, mIntentBuilder.apply(context));
        }


        public String getClassName() {
            return this.mActivity.getName();
        }
    }
}
