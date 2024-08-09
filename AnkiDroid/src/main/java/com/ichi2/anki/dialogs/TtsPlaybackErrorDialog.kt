/*
 *  Copyright (c) 2024 RohanRaj123 <rajrohan88293@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.TtsVoices
import com.ichi2.anki.showThemedToast
import com.ichi2.anki.utils.openUrl
import com.ichi2.libanki.TTSTag
import timber.log.Timber

class TtsPlaybackErrorDialog(private val tag: TTSTag?) : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val engine = TtsVoices.getTtsEngine()
        Timber.d("engine : $engine, lan : ${tag?.lang}")
        return AlertDialog.Builder(context)
            .setTitle("TTS Error: Missing Language Support")
            .setMessage("$engine does not support language : ${tag?.lang}")
            .setNegativeButton("Change Engine") { _, _ -> openSettings() }
            .setPositiveButton("Voices Options") { _, _ -> showVoicesDialog() }
            .setNeutralButton("Help?") { _, _ ->
                openUrl(Uri.parse(getString(R.string.link_faq_tts)))
            }
            .create()
    }

    private fun openSettings() {
        try {
            requireContext().startActivity(
                Intent("com.android.settings.TTS_SETTINGS").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
        } catch (e: Exception) {
            showThemedToast(requireContext(), "Unable to open settings", true)
        }
    }

    private fun showVoicesDialog() {
        val voicesDialog = TtsVoicesDialogFragment()
        voicesDialog.show(parentFragmentManager, "TTS_VOICES_DIALOG_FRAGMENT")
    }
}
