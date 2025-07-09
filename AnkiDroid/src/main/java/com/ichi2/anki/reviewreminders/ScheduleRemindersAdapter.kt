/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R
import com.ichi2.anki.reviewreminders.ScheduleReminders.Companion.SWITCH_WIDTH
import com.ichi2.utils.Dp
import timber.log.Timber

class ScheduleRemindersAdapter(
    val reminders: MutableList<ReviewReminder>,
    private val setDeckNameFromScopeForView: (ReviewReminderScope, TextView) -> Unit,
    private val toggleReminderEnabled: (ReviewReminderId, ReviewReminderScope, Int) -> Unit,
    private val enableMultiSelectMode: () -> Unit,
    private val toggleReminderSelected: (ReviewReminderId, Int) -> Unit,
    private val isInMultiSelectMode: () -> Boolean,
    private val isReminderSelected: (ReviewReminderId) -> Boolean,
    private val editReminder: (ReviewReminder) -> Unit,
) : RecyclerView.Adapter<ScheduleRemindersAdapter.ViewHolder>() {
    inner class ViewHolder(
        holder: View,
    ) : RecyclerView.ViewHolder(holder) {
        var reminder: ReviewReminder? = null

        val checkBoxView: CheckBox = holder.findViewById(R.id.reminders_list_checkbox)
        val switchView: MaterialSwitch = holder.findViewById(R.id.reminders_list_switch)
        val deckTextView: TextView = holder.findViewById(R.id.reminders_list_deck_text)
        val timeTextView: TextView = holder.findViewById(R.id.reminders_list_time_text)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.schedule_reminders_list_item, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("DefaultLocale")
    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        val reminder = reminders[position]
        holder.reminder = reminder

        holder.checkBoxView.visibility = if (isInMultiSelectMode()) View.VISIBLE else View.GONE
        holder.checkBoxView.isChecked = isReminderSelected(reminder.id)
        holder.checkBoxView.setOnClickListener {
            toggleReminderSelected(reminder.id, position)
        }

        holder.switchView.layoutParams.width = getSwitchWidthInPixels(holder.itemView.context)

        holder.switchView.isChecked = reminder.enabled
        holder.switchView.setOnClickListener {
            toggleReminderEnabled(reminder.id, reminder.scope, position)
        }

        setDeckNameFromScopeForView(reminder.scope, holder.deckTextView)
        holder.timeTextView.text = reminder.time.toString()

        holder.itemView.setOnClickListener {
            Timber.d("Clicked review reminder: ${reminder.id}")
            if (isInMultiSelectMode()) {
                toggleReminderSelected(reminder.id, position)
            } else {
                editReminder(reminder)
            }
        }

        holder.itemView.setOnLongClickListener {
            Timber.d("Long-clicked review reminder: ${reminder.id}")
            if (!isInMultiSelectMode()) {
                enableMultiSelectMode()
            }
            toggleReminderSelected(reminder.id, position)
            true
        }
    }

    override fun getItemCount(): Int = reminders.size

    companion object {
        private var cachedSwitchWidthInPixels: Int? = null

        /**
         * Since this computation is a bit expensive and runs every time an element in the list
         * is rendered, we cache its value.
         */
        private fun getSwitchWidthInPixels(context: Context): Int =
            cachedSwitchWidthInPixels
                ?: Dp(SWITCH_WIDTH.toFloat())
                    .toPx(context)
                    .also { cachedSwitchWidthInPixels = it }
                    .also { Timber.d("Computed switch width: $it") }
    }
}
