// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

/** The Gradle projects whose unit test suites are selected. */
internal sealed interface UnitTestModules {
    /** Select every project's suite. */
    data object All : UnitTestModules

    /** Select only [paths]. An empty set selects no suites. */
    data class Selected(
        val paths: Set<String>,
    ) : UnitTestModules

    operator fun contains(path: String): Boolean =
        when (this) {
            All -> true
            is Selected -> path in paths
        }
}
