/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.annotations

/**
 * Use when code needs unit tests
 *
 * This annotation is intended to:
 * 1. Be used with all new code contributions if a test is not provided to:
 *    * Show new contributors that we care about testing without delaying their first commits
 *    * Ensure the spec is written with the code fresh in mind
 *    * Ensure the requirement doesn't go stale in a GitHub issue
 *
 * For the future:
 * 2. Let maintainers prioritize tests in terms of difficulty and priority
 * 3. List 'good first tests' for new contributors (Google Summer of Code, etc...)
 * 4. List 'small chunks' of work for shorter periods of maintainer attention
 *
 * @param value the explanation for why the test is required.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class NeedsTest(val value: String)
