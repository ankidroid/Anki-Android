/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.common.annotations

/**
 * Indicates that a section of code is part of the legacy notifications system in place before
 * August 2025. Code flagged with this annotation is slated to be eventually deleted once the
 * review reminders system becomes stable.
 *
 * Also see all conditional points gated by Prefs.newReviewRemindersEnabled.
 *
 * Once all occurrences of both this annotation and Prefs.newReviewRemindersEnabled are no longer
 * present in the code base, the migration from the legacy notifications system to the new review
 * reminders system will be complete.
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
annotation class LegacyNotifications(
    val optionalReason: String = "",
)
