// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.databinding.FragmentTemplatePreviewerContainerBinding
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.mockWebResourceRequest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class TemplatePreviewerLaunchWarningTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `changing the selected card template allows its blocked link warning`() {
        ensureCollectionLoadIsSynchronous()
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = NotetypeFile(createTransientDirectory(), col.notetypes.basicAndReversed),
                fields = listOf("Front", "Back"),
                tags = emptyList(),
            )
        val intent = TemplatePreviewerPage.getIntent(targetContext, arguments)
        Robolectric.buildActivity(CardViewerActivity::class.java, intent).use { controller ->
            val page = controller.setup().get().fragment as TemplatePreviewerPage
            val tabs = FragmentTemplatePreviewerContainerBinding.bind(page.requireView()).tabLayout
            val preview = page.childFragmentManager.findFragmentById(R.id.fragment_container) as TemplatePreviewerFragment
            advanceRobolectricLooperUntil { tabs.tabCount == 2 }
            val webView = preview.binding.webViewLayout.getChildAt(0) as WebView
            val client = shadowOf(webView).webViewClient
            val request = mockWebResourceRequest("market://details?id=com.example")
            client.shouldOverrideUrlLoading(webView, request)
            advanceRobolectricLooper()
            ShadowDialog.getShownDialogs().single { it.isShowing }.cancel()
            advanceRobolectricLooper()

            tabs.getTabAt(1)!!.select()
            advanceRobolectricLooperUntil { preview.viewModel.ordFlow.value == 1 }
            client.shouldOverrideUrlLoading(webView, request)
            advanceRobolectricLooper()
            assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing }, "explain the blocked link on the second template")
        }
    }
}
