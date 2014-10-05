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

import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * Created by curraa01 on 13/10/2013.
 */
interface ShowcaseDrawer {

    void setShowcaseColour(int color);

    void drawShowcase(Bitmap buffer, float x, float y, float scaleMultiplier);

    int getShowcaseWidth();

    int getShowcaseHeight();

    float getBlockedRadius();

    void setBackgroundColour(int backgroundColor);

    void erase(Bitmap bitmapBuffer);

    void drawToCanvas(Canvas canvas, Bitmap bitmapBuffer);
}
