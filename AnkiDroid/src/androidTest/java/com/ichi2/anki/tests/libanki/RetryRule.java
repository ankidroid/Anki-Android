/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2018 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.anki.tests.libanki;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Retry a test maxTries times, only failing if zero successes.
 * Defaults to 3 tries.
 * <p>
 * Usage: @Rule public final RetryRule retry = new RetryRule(3);
 */
public final class RetryRule implements TestRule {

    /**
     * How many times to try a test
     */
    private int maxTries = 3;


    /**
     * @param i number of times to try
     * @throws IllegalArgumentException if i is less than 1
     */
    private void setMaxTries(int i) {
        if (i < 1) {
            throw new IllegalArgumentException("iterations < 1: " + i);
        }
        this.maxTries = i;
    }


    /**
     * Try a test maxTries times in an attempt to not fail a full test suite for one flaky test
     *
     * @param i how many times to try
     * @throws IllegalArgumentException if maxTries is less than 1
     */
    public RetryRule(int i) {
        setMaxTries(i);
    }


    public Statement apply(Statement base, Description description) {
        return statement(base, description);
    }

    private Statement statement(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable caughtThrowable = null;

                // implement retry logic here
                for (int i = 0; i < maxTries; i++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (Throwable t) {
                        caughtThrowable = t;
                        System.err.println(description.getDisplayName() + ": run " + (i+1) + " failed");
                        t.printStackTrace(System.err);
                    }
                }
                System.err.println(description.getDisplayName() + ": giving up after " + maxTries + " failures");
                throw caughtThrowable;
            }
        };
    }
}
