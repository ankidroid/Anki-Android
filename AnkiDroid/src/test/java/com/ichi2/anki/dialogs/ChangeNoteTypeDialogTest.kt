// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.os.Looper.getMainLooper
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.libanki.NoteId
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogTest : RobolectricTest() {
    /** #20944: spinners which can't be used still deliver selections, which used to be fatal */
    @Test
    fun `no template spinners are built for a cloze note type`() {
        val noteId = addClozeNote("{{c1::Hi}}").id
        onChangeNoteTypeDialog(noteId) {
            assertThat("templates are locked", templatesContainer().childCount, equalTo(0))
        }
    }

    @Test
    fun `template spinners are built for a regular note type`() {
        val noteId = addBasicNote().id
        onChangeNoteTypeDialog(noteId) {
            assertThat("a spinner per output template", templatesContainer().childCount, equalTo(1))
        }
    }

    /** The template mapping rows, which only apply to a regular -> regular conversion */
    private fun ChangeNoteTypeDialog.templatesContainer(): ViewGroup {
        // the templates tab is the second page of the pager
        val pager = requireView().findViewById<View>(R.id.change_note_type_pager)
        assertThat("the dialog has loaded", pager.visibility, equalTo(View.VISIBLE))
        selectTemplatesTab()
        return requireNotNull(requireView().findViewById(R.id.templates_container)) {
            "templates_container was not created"
        }
    }

    private fun ChangeNoteTypeDialog.selectTemplatesTab() {
        requireView()
            .findViewById<ViewPager2>(R.id.change_note_type_pager)
            .setCurrentItem(ChangeNoteTypeViewModel.Tab.Templates.position, false)
        shadowOf(getMainLooper()).idle()
    }

    private fun onChangeNoteTypeDialog(
        noteId: NoteId,
        action: ChangeNoteTypeDialog.() -> Unit,
    ) = launchFragment<ChangeNoteTypeDialog>(
        fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(noteId)) },
        themeResId = R.style.Theme_Light,
    ).use { scenario -> scenario.onFragment { action(it) } }
}
