package com.ichi2.anki.lint.rules

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class OpenInputStreamSafeDetectorTest {
    // Stub files for Android classes needed by the tests
    private val contentResolverStub =
        java(
            """
        package android.content;
        
        import android.net.Uri;
        import java.io.InputStream;
        
        public abstract class ContentResolver {
            public final InputStream openInputStream(Uri uri) {
                return null;
            }
        }
        """,
        ).indented()

    private val uriStub =
        java(
            """
        package android.net;
        
        public abstract class Uri {
        }
        """,
        ).indented()

    @Test
    fun testDirectOpenInputStreamCall() {
        lint()
            .allowMissingSdk()
            .files(
                contentResolverStub,
                uriStub,
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
                contentResolverStub,
                uriStub,
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
                contentResolverStub,
                uriStub,
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
}
