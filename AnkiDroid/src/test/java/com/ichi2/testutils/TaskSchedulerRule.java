/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.anki.RunInBackground;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** A {@link org.junit.Rule} which detects if the test method has the {@link com.ichi2.anki.RunInBackground} annotation */
public class TaskSchedulerRule implements TestRule {

    private Boolean mRunInForeground = null;

    @Override
    public Statement apply(final Statement base, final Description description) {
        mRunInForeground = description.getAnnotation(RunInBackground.class) == null;
        return base;
    }

    /** Whether the currently executing test should be run in the foreground */
    public boolean shouldRunInForeground() {
        if (mRunInForeground == null) {
            throw new IllegalStateException("Rule was queried before apply was called");
        }
        return mRunInForeground;
    }
}