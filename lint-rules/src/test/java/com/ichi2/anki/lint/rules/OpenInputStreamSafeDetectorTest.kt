/*
 * Copyright (c) 2025 Nishtha Jain <jnishtha305@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class OpenInputStreamSafeDetectorTest {
    @Test
    fun testDirectOpenInputStreamCall() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    class MyClass {
                        fun loadData(resolver: android.content.ContentResolver, uri: android.net.Uri) {
                            val stream = resolver.openInputStream(uri)
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectContains("Use openInputStreamSafe() instead of openInputStream()")
    }

    @Test
    fun testOpenInputStreamSafeCall() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    class MyClass {
                        fun loadData(resolver: android.content.ContentResolver, uri: android.net.Uri) {
                            val stream = resolver.openInputStreamSafe(uri)
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testJavaDirectOpenInputStreamCall() {
        lint()
            .allowMissingSdk()
            .files(
                java(
                    """
                    public class MyClass {
                        public void loadData(android.content.ContentResolver resolver, android.net.Uri uri) {
                            java.io.InputStream stream = resolver.openInputStream(uri);
                        }
                    }
                    """,
                ).indented(),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectContains("Use openInputStreamSafe() instead of openInputStream()")
    }

    @Test
    fun `openInputStreamSafe is not flagged`() {
        lint()
            .allowMissingSdk()
            .files(
                kotlin(
                    """
@Suppress("UnusedReceiverParameter")
fun android.content.ContentResolver.openInputStreamSafe(uri: Uri): InputStream? {
    return openInputStream(uri)
}
                    """,
                ),
            ).issues(OpenInputStreamSafeDetector.ISSUE)
            .run()
            .expectClean()
    }
}
