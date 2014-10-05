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
import android.graphics.Point;
import android.view.ViewParent;

/**
 * Represents an Action item to showcase (e.g., one of the buttons on an ActionBar).
 * To showcase specific action views such as the home button, use {@link com.github.amlcurran.showcaseview.targets.ActionItemTarget}
 *
 * @see com.github.amlcurran.showcaseview.targets.ActionItemTarget
 */
public class ActionItemTarget implements Target {

    private final Activity mActivity;
    private final int mItemId;

    ActionBarViewWrapper mActionBarWrapper;

    public ActionItemTarget(Activity activity, int itemId) {
        mActivity = activity;
        mItemId = itemId;
    }

    @Override
    public Point getPoint() {
        setUp();
        return new ViewTarget(mActionBarWrapper.getActionItem(mItemId)).getPoint();
    }

    protected void setUp() {
        Reflector reflector = ReflectorFactory.getReflectorForActivity(mActivity);
        ViewParent p = reflector.getActionBarView(); //ActionBarView
        mActionBarWrapper = new ActionBarViewWrapper(p);
    }

}
