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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.ichi2.anki.R

class ScheduleRemindersAdapter(
    private val setDeckNameFromScopeForView: (ReviewReminderScope, TextView) -> Unit,
    private val toggleReminderEnabled: (ReviewReminderId, ReviewReminderScope) -> Unit,
    private val editReminder: (ReviewReminder) -> Unit,
) : ListAdapter<ReviewReminder, ScheduleRemindersAdapter.ViewHolder>(diffCallback) {
    inner class ViewHolder(
        holder: View,
    ) : RecyclerView.ViewHolder(holder) {
        var reminder: ReviewReminder? = null
        val deckTextView: TextView = holder.findViewById(R.id.reminders_list_deck_text)
        val timeTextView: TextView = holder.findViewById(R.id.reminders_list_time_text)
        val switchView: MaterialSwitch = holder.findViewById(R.id.reminders_list_switch)
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
        val reminder = getItem(position)
        holder.reminder = reminder

        setDeckNameFromScopeForView(reminder.scope, holder.deckTextView)
        holder.timeTextView.text = reminder.time.toString()

        holder.itemView.setOnClickListener { editReminder(reminder) }

        holder.switchView.isChecked = reminder.enabled
        holder.switchView.setOnClickListener { toggleReminderEnabled(reminder.id, reminder.scope) }
    }

    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<ReviewReminder>() {
                override fun areItemsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: ReviewReminder,
                    newItem: ReviewReminder,
                ): Boolean = oldItem == newItem
            }
    }
}
