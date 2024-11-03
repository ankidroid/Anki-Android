/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.Flag
import com.ichi2.anki.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adapter for the RecyclerView displaying flag items.
 *
 * @param lifecycleScope The CoroutineScope used for launching coroutines.
 */
class FlagAdapter(private val lifecycleScope: CoroutineScope) :
    ListAdapter<FlagItem, FlagAdapter.FlagViewHolder>(FlagItemDiffCallback()) {

    inner class FlagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val flagImageView: ImageView = itemView.findViewById(R.id.ic_flag)
        val flagNameText: TextView = itemView.findViewById(R.id.flag_name)
        val flagNameEdit: TextInputEditText = itemView.findViewById(R.id.flag_name_edit_text)
        val editButton: MaterialButton = itemView.findViewById(R.id.action_edit_flag)
        val saveButton: MaterialButton = itemView.findViewById(R.id.action_save_flag_name)
        val cancelButton: MaterialButton = itemView.findViewById(R.id.action_cancel_flag_rename)

        val flagNameViewLayout: LinearLayout = itemView.findViewById(R.id.flag_name_view_layout)
        val flagNameEditLayout: LinearLayout = itemView.findViewById(R.id.edit_flag_name_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlagViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.edit_flag_item, parent, false)
        return FlagViewHolder(view)
    }

    override fun onBindViewHolder(holder: FlagViewHolder, position: Int) {
        val flagItem = getItem(position)

        holder.flagImageView.setImageResource(flagItem.icon)

        holder.flagNameEditLayout.visibility = View.GONE

        holder.flagNameText.text = flagItem.title
        holder.flagNameEdit.setText(flagItem.title)

        holder.editButton.setOnClickListener {
            flagItem.isInEditMode = true
            holder.flagNameViewLayout.visibility = View.GONE
            holder.flagNameEditLayout.visibility = View.VISIBLE
            holder.flagNameEdit.requestFocus()
            holder.flagNameEdit.text?.let { text -> holder.flagNameEdit.setSelection(text.length) }
            val inputMethodManager = holder.flagNameEdit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(holder.flagNameEdit, InputMethodManager.SHOW_IMPLICIT)
        }

        holder.saveButton.setOnClickListener {
            val updatedTextName = holder.flagNameEdit.text.toString()
            holder.flagNameViewLayout.visibility = View.VISIBLE
            holder.flagNameEditLayout.visibility = View.GONE
            val updatedFlagItem = flagItem.copy(title = updatedTextName)
            val updatedDataset = currentList.toMutableList()
            lifecycleScope.launch {
                flagItem.renameTo(updatedTextName)
            }
            updatedFlagItem.isInEditMode = false
            updatedDataset[position] = updatedFlagItem
            submitList(updatedDataset)
        }

        holder.cancelButton.setOnClickListener {
            holder.flagNameViewLayout.visibility = View.VISIBLE
            holder.flagNameEditLayout.visibility = View.GONE
            flagItem.isInEditMode = false
        }
    }

    class FlagItemDiffCallback : DiffUtil.ItemCallback<FlagItem>() {
        override fun areItemsTheSame(oldItem: FlagItem, newItem: FlagItem): Boolean {
            return oldItem.flag == newItem.flag
        }

        override fun areContentsTheSame(oldItem: FlagItem, newItem: FlagItem): Boolean {
            return oldItem.title == newItem.title
        }
    }
}

/**
 * Data class representing a flag item.
 *
 * @property flag The ordinal value of the flag.
 * @property title The title or name of the flag.
 * @property icon The icon resource ID of the flag.
 * @property isInEditMode Whether the flag is being edited.
 */
data class FlagItem(
    val flag: Flag,
    val title: String,
    val icon: Int,
    var isInEditMode: Boolean = false
) {
    /**
     * Renames the flag
     *
     * @param newName The new name for the flag.
     */
    suspend fun renameTo(newName: String) = flag.rename(newName)
}
