/*
 Copyright (c) 2024 Alexandre Ferreira <alexandre.bruno.ferreira@tecnico.ulisboa.pt>
 Copyright (c) 2024 Afonso Palmeira <afonsopalmeira@tecnico.ulisboa.pt>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs.flags

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer

class FlagsDialog() : DialogFragment() {

    private var dialogTitle: String? = null
    private var _dialog: MaterialDialog? = null
    public var _reviewer: Reviewer? = null
    fun withArguments(
        reviewer: Reviewer
    ): FlagsDialog {
        _reviewer = reviewer
        return this
    }

    private fun onButtonClicked(button: Button) {
        when (button.id) {
            R.id.action_flag_zero -> {
                _reviewer?.toggleFlag(Flag.NONE)
                dismiss()
            }
            R.id.action_flag_one -> {
                _reviewer?.toggleFlag(Flag.RED)
                dismiss()
            }
            R.id.action_flag_two -> {
                _reviewer?.toggleFlag(Flag.ORANGE)
                dismiss()
            }
            R.id.action_flag_three -> {
                _reviewer?.toggleFlag(Flag.GREEN)
                dismiss()
            }
            R.id.action_flag_four -> {
                _reviewer?.toggleFlag(Flag.BLUE)
                dismiss()
            }
            R.id.action_flag_five -> {
                _reviewer?.toggleFlag(Flag.PINK)
                dismiss()
            }
            R.id.action_flag_six -> {
                _reviewer?.toggleFlag(Flag.TURQUOISE)
                dismiss()
            }
            R.id.action_flag_seven -> {
                _reviewer?.toggleFlag(Flag.PURPLE)
                dismiss()
            }
            else -> {
                return onButtonClicked(button)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogLayout = LayoutInflater.from(activity).inflate(R.layout.flags_dialog, null, false)

        dialogTitle = resources.getString(R.string.flag_dialog_title)

        val flagButtonZero = dialogLayout.findViewById<Button>(R.id.action_flag_zero)

        val flagButtonRed = dialogLayout.findViewById<Button>(R.id.action_flag_one)
        val flagButtonOrange = dialogLayout.findViewById<Button>(R.id.action_flag_two)
        val flagButtonGreen = dialogLayout.findViewById<Button>(R.id.action_flag_three)
        val flagButtonBlue = dialogLayout.findViewById<Button>(R.id.action_flag_four)
        val flagButtonPink = dialogLayout.findViewById<Button>(R.id.action_flag_five)
        val flagButtonTurquoise = dialogLayout.findViewById<Button>(R.id.action_flag_six)
        val flagButtonPurple = dialogLayout.findViewById<Button>(R.id.action_flag_seven)

        flagButtonZero.setOnClickListener {
            onButtonClicked(flagButtonZero)
        }

        flagButtonRed.setOnClickListener {
            onButtonClicked(flagButtonRed)
        }

        flagButtonOrange.setOnClickListener {
            onButtonClicked(flagButtonOrange)
        }

        flagButtonGreen.setOnClickListener {
            onButtonClicked(flagButtonGreen)
        }

        flagButtonBlue.setOnClickListener {
            onButtonClicked(flagButtonBlue)
        }

        flagButtonPink.setOnClickListener {
            onButtonClicked(flagButtonPink)
        }

        flagButtonTurquoise.setOnClickListener {
            onButtonClicked(flagButtonTurquoise)
        }

        flagButtonPurple.setOnClickListener {
            onButtonClicked(flagButtonPurple)
        }

        val dialog = MaterialDialog(requireActivity())
            .customView(view = dialogLayout, noVerticalPadding = true)
        _dialog = dialog
        return dialog
    }
}
