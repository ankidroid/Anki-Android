// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class IntentActionNamingDetectorTest {
    @Test
    fun `reports constants passed to setAction and addAction`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentFilter,
                kotlin(
                    """
                    package test
                    import android.content.Intent
                    import android.content.IntentFilter

                    const val MEDIA_EJECT = "com.ichi2.anki.action.MEDIA_EJECT"
                    object Actions {
                        const val MEDIA_MOUNT = "com.ichi2.anki.action.MEDIA_MOUNT"
                    }
                    fun register(intent: Intent, filter: IntentFilter) {
                        intent.setAction(MEDIA_EJECT)
                        filter.addAction(Actions.MEDIA_MOUNT)
                        intent.action = MEDIA_EJECT
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectErrorCount(3)
            .expectContains("Intent Action constants must start with ACTION_: MEDIA_EJECT")
    }

    @Test
    fun `allows ACTION_ constants dynamic actions and unrelated methods`() {
        lint()
            .allowMissingSdk()
            .files(
                intent,
                intentFilter,
                kotlin(
                    """
                    package test
                    import android.content.Intent
                    import android.content.IntentFilter

                    object Actions {
                        const val ACTION_MEDIA_EJECT = "com.ichi2.anki.action.MEDIA_EJECT"
                    }
                    fun register(intent: Intent, filter: IntentFilter, action: String) {
                        intent.setAction(Actions.ACTION_MEDIA_EJECT)
                        filter.addAction(Actions.ACTION_MEDIA_EJECT)
                        // forwarded parameters and runtime-generated actions have no constant name
                        intent.setAction(action)
                        intent.setAction("com.ichi2.anki.action." + System.nanoTime())
                    }
                    class Unrelated {
                        fun addAction(action: String) {}
                        fun register() = addAction(Actions.ACTION_MEDIA_EJECT)
                    }
                    """,
                ).indented(),
            ).issues(IntentActionNamingDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        private val intent =
            java(
                """
                package android.content;
                public class Intent {
                    public String getAction() { return null; }
                    public Intent setAction(String action) { return this; }
                }
                """,
            ).indented()
        private val intentFilter =
            java(
                """
                package android.content;
                public class IntentFilter {
                    public void addAction(String action) {}
                }
                """,
            ).indented()
    }
}
