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
@file:KotlinCleanup(
    "Remove all TextUtils references then see if we can remove " +
        "@RunWith(AndroidJUnit4::class) to speed up tests (if no other Android references)",
)

package com.ichi2.utils

/** Use when code can be changed after further conversion to Kotlin */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FILE,
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
@MustBeDocumented
annotation class KotlinCleanup(val value: String)

/** This must be fixed/implemented before AnkiDroid can switch to using the new backend code
 * by default.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
@MustBeDocumented
annotation class BlocksSchemaUpgrade(val value: String)
