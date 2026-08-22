// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.BaselineProfileRule.Flag
import com.ichi2.anki.BaselineProfileRule.MethodRule
import org.junit.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ensures a manual run of the baseline profile won't drop rules.
 */
class BaselineProfileTest {
    /**
     * [DeckPicker.onResume] runs on every launch before the deck list is
     * usable, so it must stay ahead-of-time compiled.
     *
     * See https://github.com/ankidroid/Anki-Android/issues/20734
     */
    @Test
    fun `profile contains a startup rule for DeckPicker onResume`() {
        val profile = File("src/main/generated/baselineProfiles/baseline-prof.txt")
        assertTrue(profile.exists(), "baseline profile not found at ${profile.absolutePath}")

        val rules = profile.readLines().map { BaselineProfileRule.parse(it) }
        val rule =
            rules.filterIsInstance<MethodRule>().firstOrNull {
                it.className == "com.ichi2.anki.DeckPicker" && it.methodSignature == "onResume()V"
            }

        assertNotNull(
            rule,
            "No rule for DeckPicker.onResume() in ${profile.name}: the method is " +
                "no longer ahead-of-time compiled. Ensure the BaselineProfileGenerator " +
                "journey reaches DeckPicker, then regenerate (baselineprofile/README.md).",
        )
        assertTrue(
            Flag.STARTUP in rule.flags,
            "'$rule' lost the S (startup) flag: DeckPicker.onResume() no longer runs " +
                "inside the profiled startup window. Check the BaselineProfileGenerator " +
                "journey, then regenerate (baselineprofile/README.md).",
        )
    }
}
