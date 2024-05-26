package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.R

class CustomDialogFragment(
    private val title: Int,
    private val items: Array<String>,
    private val checkedItems: BooleanArray,
    private val positiveButton: Int,
    private val negativeButton: Int,
    private val onPositiveClick: (selectedItems: BooleanArray) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val theme = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            R.style.CustomDialogTheme_Dark
        } else {
            R.style.CustomDialogTheme_Light
        }
    return activity?.let {
        val builder = AlertDialog.Builder(requireContext(), theme)
        builder.setTitle(title)
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(positiveButton) { _, _ ->
                onPositiveClick(checkedItems)
            }
            .setNegativeButton(negativeButton) { dialog, _ ->
                dialog.cancel()
            }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(),
                R.color.material_light_blue_500
            ))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(),
                R.color.material_light_blue_500
            ))
        }
        dialog
    } ?: throw IllegalStateException("Activity cannot be null")
}
}
