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
package com.ichi2.testutils

import com.ichi2.anki.RunInBackground
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** A [org.junit.Rule] which detects if the test method has the [com.ichi2.anki.RunInBackground] annotation  */
class TaskSchedulerRule : TestRule {
    private var runInForeground: Boolean? = null
    override fun apply(base: Statement, description: Description): Statement {
        runInForeground = description.getAnnotation(RunInBackground::class.java) == null
        return base
    }

    /** Whether the currently executing test should be run in the foreground  */
    fun shouldRunInForeground(): Boolean {
        return checkNotNull(runInForeground) { "Rule was queried before apply was called" }
    }
}
