// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

/** Records work needed before a test can run without Robolectric. Does not change test execution. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class NeedsRobolectric(
    val reason: String,
)
