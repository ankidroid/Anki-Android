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

/**
* @author Alex
*/
public interface OnShowcaseEventListener {

    /**
     * Called when the ShowcaseView has been told to hide. Use {@link #onShowcaseViewDidHide(ShowcaseView)}
     * if you want to know when the ShowcaseView has been fully hidden.
     */
    public void onShowcaseViewHide(ShowcaseView showcaseView);

    /**
     * Called when the animation hiding the ShowcaseView has finished, and it is no longer visible on the screen.
     */
    public void onShowcaseViewDidHide(ShowcaseView showcaseView);

    /**
     * Called when the ShowcaseView is shown.
     */
    public void onShowcaseViewShow(ShowcaseView showcaseView);

    /**
     * Empty implementation of OnShowcaseViewEventListener such that null
     * checks aren't needed
     */
    public static final OnShowcaseEventListener NONE = new OnShowcaseEventListener() {
        @Override
        public void onShowcaseViewHide(ShowcaseView showcaseView) {

        }

        @Override
        public void onShowcaseViewDidHide(ShowcaseView showcaseView) {

        }

        @Override
        public void onShowcaseViewShow(ShowcaseView showcaseView) {

        }
    };

}
