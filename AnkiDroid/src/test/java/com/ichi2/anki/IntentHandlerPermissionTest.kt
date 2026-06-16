//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.Manifest.permission.INTERNET
import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity.Companion.CONTINUE_INTENT_EXTRA
import com.ichi2.testutils.withDeniedPermissions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class IntentHandlerPermissionTest : RobolectricTest() {
    @Test
    fun sharedTextWithoutPermissionOpensPermissionActivityWithDeckPickerContinuation() =
        runTest {
            withDeniedPermissions(INTERNET) {
                val sharedTextIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "shared text")
                    }

                val controller = Robolectric.buildActivity(IntentHandler::class.java, sharedTextIntent).create()
                val activity = controller.get()
                saveControllerForCleanup(controller)

                val permissionIntent = assertNotNull(shadowOf(activity).nextStartedActivity)
                assertThat(permissionIntent.component?.className, equalTo(PermissionsActivity::class.java.name))

                val continueIntent = permissionIntent.getContinueIntent()
                assertThat(continueIntent.component?.className, equalTo(DeckPicker::class.java.name))
            }
        }

    @Test
    fun addImageWithoutPermissionOpensPermissionActivityWithDeckPickerContinuation() =
        runTest {
            withDeniedPermissions(INTERNET) {
                val addImageIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, "content://image".toUri())
                    }

                val controller = Robolectric.buildActivity(IntentHandler2::class.java, addImageIntent).create()
                val activity = controller.get()
                saveControllerForCleanup(controller)

                val permissionIntent = assertNotNull(shadowOf(activity).nextStartedActivity)
                assertThat(permissionIntent.component?.className, equalTo(PermissionsActivity::class.java.name))

                val continueIntent = permissionIntent.getContinueIntent()
                assertThat(continueIntent.component?.className, equalTo(DeckPicker::class.java.name))
            }
        }

    private fun Intent.getContinueIntent(): Intent =
        assertNotNull(IntentCompat.getParcelableExtra(this, CONTINUE_INTENT_EXTRA, Intent::class.java))
}
