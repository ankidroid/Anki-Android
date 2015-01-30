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

import android.graphics.Point;

/**
 * Showcase a specific x/y co-ordinate on the screen.
 */
public class PointTarget implements Target {

    private final Point mPoint;

    public PointTarget(Point point) {
        mPoint = point;
    }

    public PointTarget(int xValue, int yValue) {
        mPoint = new Point(xValue, yValue);
    }

    @Override
    public Point getPoint() {
        return mPoint;
    }
}
