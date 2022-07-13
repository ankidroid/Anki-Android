/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2022 Dorrin Sotoudeh <dorrinsotoudeh123@gmail.com>                     *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.testutil

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Adding this rule to the test class will make every test that fails, run [retryCount] times.
 * If it failed every time, it is an actual fail
 * @param retryCount the maximum number of times to run test
 * @sample [@get:Rule val retry: Retry = Retry(2)]
 */
class RetryRule(private val retryCount: Int) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        statement(base, description)

    private fun statement(base: Statement, description: Description): Statement =
        object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                var caughtThrowable: Throwable? = null

                // implement retry logic here
                for (i in 0 until retryCount) {
                    try {
                        base.evaluate()
                        return
                    } catch (t: Throwable) {
                        caughtThrowable = t
                        println("${description.displayName}: run ${i + 1} failed")
                    }
                }
                println("${description.displayName}: giving up after $retryCount failures")
                throw caughtThrowable!!
            }
        }
}
