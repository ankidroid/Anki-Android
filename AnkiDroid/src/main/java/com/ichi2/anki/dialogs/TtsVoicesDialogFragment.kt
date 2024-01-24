/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.AndroidTtsVoice
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils
import com.ichi2.anki.dialogs.viewmodel.TtsVoicesViewModel
import com.ichi2.anki.localizedErrorMessage
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.libanki.TtsVoice
import com.ichi2.themes.Themes
import com.ichi2.utils.UiUtil.makeFullscreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A dialog which allows a user to preview Text to speech voices and copy them to the clipboard
 * for use in a card template
 *
 * This may be opened after {{tts-voices:}} is placed a card template
 *
 * It provides the user the ability to filter voices by online/offline, and whether the voice
 * is installed on the system
 *
 * We filter by online/offline to discourage data usage and reduce latency
 * Although uninstalled voices can be used, and a TTS should install an uninstalled voice,
 * in practice there are many bugs in the TTS Engines, so we want to explicitly inform a user
 *
 * As a future extension: this dialog should have a 'refresh' button/functionality to handle
 * voice installs
 *
 * @see [TtsVoicesViewModel]
 */
class TtsVoicesDialogFragment : DialogFragment() {

    private val viewModel: TtsVoicesViewModel by this.viewModels()

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var layout: View
    private lateinit var spokenTextEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var internetRequiredChip: Chip

    private lateinit var voicesAdapter: TtsVoiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        voicesAdapter = TtsVoiceAdapter()
        Themes.setTheme(requireContext()) // (re)-enable selectableItemBackground on theme change

        layout = inflater.inflate(R.layout.dialog_tts_voices, null).apply {
            recyclerView = findViewById<RecyclerView>(R.id.files).apply {
                this.adapter = voicesAdapter
            }
            spokenTextEditText = findViewById<EditText>(R.id.spoken_text).apply {
                // setup the initial value from the UI
                viewModel.setSpokenText(text.toString())
                doOnTextChanged { text, _, _, _ -> viewModel.setSpokenText(text.toString()) }
            }
            findViewById<MaterialButton>(R.id.back_button).apply {
                setOnClickListener {
                    this@TtsVoicesDialogFragment.dismiss()
                }
            }
            findViewById<Button>(R.id.options_buttons).apply {
                setOnClickListener { openTtsSettings() }
            }
            internetRequiredChip = findViewById<Chip>(R.id.toggle_internet_required).apply {
                setOnCheckedChangeListener { _, value ->
                    viewModel.showInternetEnabled.value = value
                    chipBackgroundColor = if (value) {
                        // TODO: This should be RMaterial.attr.colorSecondaryContainer
                        // but this shows as Purple after Themes.setTheme
                        ColorStateList.valueOf(requireContext().getColor(R.color.text_input_background))
                    } else {
                        ColorStateList.valueOf(Color.TRANSPARENT)
                    }
                }
                viewModel.showInternetEnabled.value = this.isChecked
            }
            findViewById<Chip>(R.id.only_show_uninstalled).apply {
                setOnCheckedChangeListener { _, value ->
                    viewModel.showNotInstalled.value = value
                    chipBackgroundColor = if (value) {
                        ColorStateList.valueOf(requireContext().getColor(R.color.text_input_background))
                    } else {
                        ColorStateList.valueOf(Color.TRANSPARENT)
                    }
                }
                viewModel.showNotInstalled.value = this.isChecked
            }
            progressBar = findViewById(R.id.progress)
        }

        return layout
    }

    fun openTtsSettings() {
        try {
            requireContext().startActivity(
                Intent("com.android.settings.TTS_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
            UIUtils.showThemedToast(requireContext(), R.string.tts_voices_failed_opening_tts_system_settings, shortLength = true)
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.makeFullscreen()

        viewModel.availableVoicesFlow.observe {
            if (it is TtsVoicesViewModel.VoiceLoadingState.Failure) {
                progressBar.visibility = View.VISIBLE
                AlertDialog.Builder(ContextThemeWrapper(this.context, R.style.AlertDialogStyle))
                    .setMessage(it.exception.localizedMessage)
                    .setOnDismissListener { this@TtsVoicesDialogFragment.dismiss() }
                    .show()
            }

            if (it is TtsVoicesViewModel.VoiceLoadingState.Success) {
                Timber.v("loaded new voice collection")
                voicesAdapter.submitList(it.voices)
                progressBar.visibility = View.GONE
            }
        }

        viewModel.showNotInstalled.observe { showInstalled ->
            // if the user is showing installed, it shows ALL uninstalled voices
            internetRequiredChip.isEnabled = !showInstalled
        }

        viewModel.uninstalledVoicePlayed.observe { voice ->
            dialog?.window?.decorView?.showSnackbar(R.string.tts_voices_selected_voice_should_be_installed) {
                setAction(R.string.tts_voices_use_selected_voice_without_install) { viewModel.copyToClipboard(voice) }
            }
        }

        viewModel.ttsPlaybackErrorFlow.observe {
            val string = it.localizedErrorMessage(requireContext())
            dialog?.window?.decorView?.showSnackbar(string) {
                setAction(R.string.help) {
                    // TODO: Should do this in ViewModel, but we need an Activity
                    (requireActivity() as AnkiActivity).openUrl(R.string.link_faq_tts)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.waitForRefresh()
    }

    /**
     * Helper function to observe a flow while the current lifecycle is active
     */
    private fun <T> Flow<T>.observe(exec: (T) -> Unit) {
        lifecycleScope.launch {
            this@observe
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect(exec)
        }
    }

    private class TtsVoiceDiffCallback : DiffUtil.ItemCallback<AndroidTtsVoice>() {
        override fun areItemsTheSame(oldItem: AndroidTtsVoice, newItem: AndroidTtsVoice): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(
            oldItem: AndroidTtsVoice,
            newItem: AndroidTtsVoice
        ): Boolean = oldItem.unavailable() == newItem.unavailable()
    }

    // inner allows access to viewModel/openTtsSettings
    inner class TtsVoiceAdapter() : ListAdapter<AndroidTtsVoice, TtsVoiceAdapter.TtsViewHolder>(TtsVoiceDiffCallback()) {
        inner class TtsViewHolder(private val voiceView: View) : RecyclerView.ViewHolder(voiceView) {
            private val textViewTop = voiceView.findViewById<TextView>(R.id.mtrl_list_item_secondary_text)
            private val textViewBottom = voiceView.findViewById<TextView>(R.id.mtrl_list_item_text)
            private val actionButton = voiceView.findViewById<MaterialButton>(R.id.action_button)
            private val localOrOffline = voiceView.findViewById<MaterialButton>(R.id.local_or_network)

            fun bind(voice: AndroidTtsVoice) {
                textViewTop.text = voice.normalizedLocale.displayName
                textViewBottom.text = voice.tryDisplayLocalizedName()

                localOrOffline.setIconResource(if (voice.isNetworkConnectionRequired) R.drawable.baseline_wifi_24 else R.drawable.baseline_offline_pin_24)
                if (voice.unavailable()) {
                    actionButton.setOnClickListener { openTtsSettings() }
                    actionButton.setIconResource(R.drawable.ic_file_download_white)
                } else {
                    actionButton.setIconResource(R.drawable.baseline_content_copy_24)
                    actionButton.setOnClickListener { viewModel.copyToClipboard(voice) }
                    voiceView.setOnClickListener { viewModel.playVoice(voice) }
                }

                voiceView.setOnClickListener { viewModel.playVoice(voice) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TtsViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.dialog_tts_voices_voice, parent, false)
            return TtsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TtsViewHolder, position: Int) {
            holder.bind(this.currentList[position])
        }
    }
}

fun TtsVoice.tryDisplayLocalizedName(): String {
    if (this !is AndroidTtsVoice) {
        return this.toString()
    }

    if (engine == "com.google.android.tts") {
        return prettyPrintGoogle(this)
    }
    // exclude the engine when printing
    return voice.name
}

/**
 * Removes the prefix and suffix from a Google voice name, and provides the 'code' of the voice
 *
 * * sr => sr
 * * cmn-cn-x-ccd-network => ccd
 * * cmn-cn-x-ccc-local => ccc
 * * zh-CN-language => zh CN
 *
 * We can remove the Locale prefix as we have better locale data in the voice
 * We can remove the network/local suffix as this can be obtained from the TTS Engine
 */
fun prettyPrintGoogle(voice: AndroidTtsVoice): String {
    val parts = voice.voice.name.split("-").toMutableList()

    if (parts.last() == "language") {
        return parts.dropLast(1).joinToString(" ")
    }

    if (parts.size == 1 && parts[0] == voice.lang) {
        return voice.voice.name
    }

    when (parts.last()) {
        "local", "network" -> parts.dropLast(1)
        else -> {}
    }

    if (parts.size == 5 && parts[2] == "x") {
        return parts[3]
    }

    return parts.joinToString(" ")
}
