// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.checks.infrastructure.TestMode
import org.junit.Test

class CommonPreferenceKeyDetectorTest {
    private val resources =
        java(
            """
            package com.ichi2.anki.common.android;
            public final class R {
                public static final class string {
                    public static final int pref_new_review_reminders = 0x7f010001;
                    public static final int arbitrary_key_name = 0x7f010002;
                    public static final int pref_notification_title = 0x7f010003;
                }
            }
            """.trimIndent(),
        )

    private val aliases =
        kotlin(
            """
            package com.ichi2.anki
            typealias CommonString = com.ichi2.anki.common.android.R.string
            typealias CommonPreferenceKey = com.ichi2.anki.common.android.R.string
            """.trimIndent(),
        )

    private val preferenceKeys =
        xml(
            "res/values/preferences_notifications.xml",
            """
            <resources>
                <string name="pref_new_review_reminders" translatable="false">newReviewReminders</string>
                <string name="arbitrary_key_name" translatable="false">anotherKey</string>
            </resources>
            """.trimIndent(),
        )

    private val displayStrings =
        xml(
            "res/values/10-preferences.xml",
            """
            <resources>
                <string name="pref_notification_title">Notifications</string>
            </resources>
            """.trimIndent(),
        )

    private fun check(source: String) =
        lint()
            // Import forms are covered explicitly because the alias spelling is part of the rule.
            .testModes(TestMode.DEFAULT, TestMode.PARTIAL)
            .allowMissingSdk()
            .files(resources, aliases, preferenceKeys, displayStrings, kotlin("src/test/Usage.kt", source.trimIndent()))
            .issues(CommonPreferenceKeyDetector.ISSUE)
            .run()

    @Test
    fun `preference keys require the key alias regardless of their resource name`() {
        check(
            """
            package test
            import com.ichi2.anki.CommonString
            import com.ichi2.anki.common.android.R as CommonR
            val first = CommonString.pref_new_review_reminders
            val second = CommonR.string.arbitrary_key_name
            """,
        ).expectErrorCount(2)
            .expectContains("Use CommonPreferenceKey for shared preference keys")
    }

    @Test
    fun `translated preference labels require the display string alias`() {
        check(
            """
            package test
            import com.ichi2.anki.CommonPreferenceKey
            val title = CommonPreferenceKey.pref_notification_title
            """,
        ).expectErrorCount(1)
            .expectContains("Use CommonString for strings that are not preference keys")
    }

    @Test
    fun `correct aliases including qualified and renamed imports are accepted`() {
        check(
            """
            package test
            import com.ichi2.anki.CommonPreferenceKey
            import com.ichi2.anki.CommonPreferenceKey as Key
            import com.ichi2.anki.CommonString
            val first = CommonPreferenceKey.pref_new_review_reminders
            val second = Key.arbitrary_key_name
            val third = com.ichi2.anki.CommonPreferenceKey.pref_new_review_reminders
            val title = CommonString.pref_notification_title
            """,
        ).expectClean()
    }

    @Test
    fun `qualified and renamed incorrect aliases are reported`() {
        check(
            """
            package test
            import com.ichi2.anki.CommonString as Text
            import com.ichi2.anki.CommonPreferenceKey as Key
            val first = Text.pref_new_review_reminders
            val second = com.ichi2.anki.CommonString.arbitrary_key_name
            val title = Key.pref_notification_title
            """,
        ).expectErrorCount(3)
    }

    @Test
    fun `unrelated objects with the same name are ignored`() {
        check(
            """
            package test
            object CommonPreferenceKey {
                const val pref_notification_title = 42
            }
            val title = CommonPreferenceKey.pref_notification_title
            """,
        ).expectClean()
    }

    @Test
    fun `explicit suppression is respected`() {
        check(
            """
            package test
            import com.ichi2.anki.CommonString
            @Suppress("CommonPreferenceKeyUsage")
            val key = CommonString.pref_new_review_reminders
            """,
        ).expectClean()
    }

    @Test
    fun `fix replaces only the resource qualifier`() {
        check(
            """
            package com.ichi2.anki
            val key = CommonString.pref_new_review_reminders
            """,
        ).expectFixDiffs(
            """
            Fix for src/test/Usage.kt line 2: Use CommonPreferenceKey:
            @@ -2 +2
            - val key = CommonString.pref_new_review_reminders
            + val key = CommonPreferenceKey.pref_new_review_reminders
            """.trimIndent(),
        )
    }

    @Test
    fun `key classification survives merged resource definitions in a consumer`() {
        val common =
            ProjectDescription(
                xml("AndroidManifest.xml", """<manifest package="com.ichi2.anki.common.android"/>"""),
                preferenceKeys,
                displayStrings,
            ).name("common")
        val app =
            ProjectDescription(
                // Stubs for the symbols exposed by the compiled common module.
                resources,
                aliases,
                // Test variants can expose dependency strings through a merged values.xml.
                xml(
                    "res/values/values.xml",
                    """
                    <resources>
                        <string name="pref_new_review_reminders">newReviewReminders</string>
                        <string name="arbitrary_key_name">anotherKey</string>
                        <string name="pref_notification_title">Notifications</string>
                    </resources>
                    """.trimIndent(),
                ),
                kotlin(
                    """
                    package test
                    import com.ichi2.anki.CommonString
                    import com.ichi2.anki.CommonPreferenceKey
                    val key = CommonString.pref_new_review_reminders
                    val title = CommonPreferenceKey.pref_notification_title
                    val validKey = CommonPreferenceKey.arbitrary_key_name
                    """.trimIndent(),
                ),
            ).name("app").dependsOn(common)

        lint()
            .testModes(TestMode.DEFAULT, TestMode.PARTIAL)
            .allowMissingSdk()
            .projects(common, app)
            .issues(CommonPreferenceKeyDetector.ISSUE)
            .run()
            .expectErrorCount(2)
            .expectContains("val key = CommonString.pref_new_review_reminders")
            .expectContains("val title = CommonPreferenceKey.pref_notification_title")
    }
}
