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

package com.github.amlcurran.showcaseview;

import android.widget.RelativeLayout;

/**
 * A simple interface which makes it easy to keep track of what is in the public
 * ShowcaseView API
 */
public interface ShowcaseViewApi {
    void hide();

    void show();

    void setContentTitle(CharSequence title);

    void setContentText(CharSequence text);

    void setButtonPosition(RelativeLayout.LayoutParams layoutParams);

    void setHideOnTouchOutside(boolean hideOnTouch);

    void setBlocksTouches(boolean blockTouches);

    void setStyle(int theme);

    boolean isShowing();
}
