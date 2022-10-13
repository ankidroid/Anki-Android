/*
 * Copyright (c) 2022 Prateek Singh <prateeksingh3212@gmail.com>
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

package com.ichi2.anki

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.TimePicker
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.model.DeckNotification
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.DeckId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationSheetDialog(
    val deckId: DeckId,
    val deckName: String,
    val position: Int,
    val onDialogueDismissListener: OnDialogueDismissListener
) :
    BottomSheetDialogFragment() {

    private lateinit var mDeckName: TextView
    private lateinit var mTimePicker: TimePicker
    private lateinit var mSubDeck: CheckBox
    private lateinit var mStickyNotification: CheckBox
    private lateinit var mSaveButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.notification_sheet_dialog, container, false)
        initViews(view)
        // Set Deck name
        mDeckName.text = deckName

        CoroutineScope(Dispatchers.Main).launch {
            val deckNotification = NotificationDatastore.getInstance(requireContext()).getDeckSchedData(deckId)
            deckNotification?.let {
                CompatHelper.compat.setTime(mTimePicker, it.schedHour, it.schedMinutes)
            }
        }

        mSaveButton.setOnClickListener {
            val compat = CompatHelper.compat
            val notification = DeckNotification(
                true,
                deckId,
                compat.getHour(mTimePicker),
                compat.getMinute(mTimePicker)
            )

            saveDeckNotification(notification)
        }
        return view
    }

    // Save Deck Notification
    private fun saveDeckNotification(notification: DeckNotification) {
        val notificationDatastore = NotificationDatastore.getInstance(requireContext())
        CoroutineScope(Dispatchers.Main).launch {
            val isSuccess = notificationDatastore.setDeckSchedData(deckId, notification)
            if (isSuccess) {
                this@NotificationSheetDialog.dismiss()
            } else {
                UIUtils.showThemedToast(
                    context,
                    "Unable to save, Please Retry",
                    true
                )
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDialogueDismissListener.onDialogueDismiss(position)
    }

    interface OnDialogueDismissListener {
        fun onDialogueDismiss(position: Int)
    }

    // Delete Deck Notification

    private fun initViews(view: View) {
        mDeckName = view.findViewById(R.id.notification_sheet_deck_name)
        mTimePicker = view.findViewById(R.id.sheet_time_picker)
        mSubDeck = view.findViewById(R.id.notification_sheet_subdeck)
        mStickyNotification = view.findViewById(R.id.notification_sheet_sticky)
        mSaveButton = view.findViewById(R.id.notification_sheet_save_deck)
    }
}
