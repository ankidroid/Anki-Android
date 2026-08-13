// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Bundle
import android.os.Looper.getMainLooper
import android.view.accessibility.AccessibilityManager
import androidx.fragment.app.testing.launchFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.libanki.NoteId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogTest : RobolectricTest() {
    /**
     * #20944: with an accessibility service enabled, a Spinner reports its initial selection.
     * A template selection is meaningless for cloze, and used to be fatal
     */
    @Test
    fun `templates tab for a cloze note does not crash with an accessibility service`() {
        enableAccessibilityService()

        withChangeNoteTypeDialog(addClozeNote("{{c1::Hi}}").id) {
            selectTemplatesTab()
        }
        // a crash is reported by FailOnUnhandledExceptionRule
    }

    private fun ChangeNoteTypeDialog.selectTemplatesTab() {
        requireView()
            .findViewById<ViewPager2>(R.id.change_note_type_pager)
            .setCurrentItem(ChangeNoteTypeViewModel.Tab.Templates.position, false)
        shadowOf(getMainLooper()).idle()
    }

    /** Makes [AccessibilityManager.isEnabled] return true, as it would with TalkBack running */
    private fun enableAccessibilityService() {
        val manager = targetContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
    }

    private fun withChangeNoteTypeDialog(
        noteId: NoteId,
        action: ChangeNoteTypeDialog.() -> Unit,
    ) {
        launchFragment<ChangeNoteTypeDialog>(
            fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(noteId)) },
            themeResId = R.style.Theme_Light,
        ).use { scenario -> scenario.onFragment { action(it) } }
    }
}
