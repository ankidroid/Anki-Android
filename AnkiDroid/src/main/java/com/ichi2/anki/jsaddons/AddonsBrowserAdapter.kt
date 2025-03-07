/****************************************************************************************
 * Copyright (c) 2022 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************************/

package com.ichi2.anki.jsaddons

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R
import com.ichi2.anki.showThemedToast

/**
 * Adapter used for listing the addons from AnkiDroid/addons directory.
 */
class AddonsBrowserAdapter(
    private val context: Context,
    private val onToggleAddon: (AddonModel, Boolean) -> Unit,
    private val onDetailsRequested: (AddonModel) -> Unit,
    private val onDeleteAddon: (AddonModel) -> Unit
) : ListAdapter<AddonModelUi, AddonsBrowserAdapter.AddonsViewHolder>(
    addonModelDiff
) {
    private val inflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonsViewHolder =
        AddonsViewHolder(inflater.inflate(R.layout.item_addon, parent, false))

    override fun onBindViewHolder(holder: AddonsViewHolder, position: Int) {
        val modelUi: AddonModelUi = getItem(position)
        holder.addonTitle.text = modelUi.modelRef.addonTitle
        holder.addonVersion.text = modelUi.modelRef.version
        holder.addonDescription.text = modelUi.modelRef.description
        holder.addonActivate.isChecked = modelUi.isEnabled

        holder.addonActivate.setOnCheckedChangeListener { _, isChecked ->
            onToggleAddon(modelUi.modelRef, isChecked)
        }

        holder.detailsBtn.setOnClickListener { onDetailsRequested(modelUi.modelRef) }

        // remove addon from directory and update prefs
        holder.removeBtn.setOnClickListener { onDeleteAddon(modelUi.modelRef) }

        // clicking the addon triggers the configuration of the addon
        holder.itemView.setOnClickListener {
            showThemedToast(context, "Configuring an addon is not implemented yet!", false)
        }
    }

    inner class AddonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val addonTitle: TextView = itemView.findViewById(R.id.addon_title)
        val addonDescription: TextView = itemView.findViewById(R.id.addon_description)
        val addonVersion: TextView = itemView.findViewById(R.id.addon_version)
        val removeBtn: Button = itemView.findViewById(R.id.addon_remove)
        val detailsBtn: Button = itemView.findViewById(R.id.addon_details)
        val addonActivate: SwitchCompat = itemView.findViewById(R.id.toggle_addon)
    }
}

/** Wrapper around [AddonModel] which also encloses the status(enabled/disabled) of the addon **/
data class AddonModelUi(val modelRef: AddonModel, val isEnabled: Boolean)

private val addonModelDiff = object : DiffUtil.ItemCallback<AddonModelUi>() {
    // assume the same names and versions represent the same addon
    override fun areItemsTheSame(oldItem: AddonModelUi, newItem: AddonModelUi): Boolean {
        return oldItem.modelRef.name == newItem.modelRef.name && oldItem.modelRef.version == newItem.modelRef.version
    }

    override fun areContentsTheSame(oldItem: AddonModelUi, newItem: AddonModelUi): Boolean {
        return oldItem.modelRef.name == newItem.modelRef.name &&
            oldItem.modelRef.addonType == newItem.modelRef.addonType &&
            oldItem.modelRef.addonTitle == newItem.modelRef.addonTitle &&
            oldItem.modelRef.author["name"] == oldItem.modelRef.author["name"] &&
            oldItem.modelRef.author["url"] == oldItem.modelRef.author["url"] &&
            oldItem.modelRef.version == newItem.modelRef.version &&
            oldItem.modelRef.ankidroidJsApi == newItem.modelRef.ankidroidJsApi &&
            oldItem.modelRef.description == newItem.modelRef.description &&
            oldItem.modelRef.homepage == newItem.modelRef.homepage &&
            oldItem.modelRef.license == newItem.modelRef.license &&
            oldItem.modelRef.dist.tarball == newItem.modelRef.dist.tarball
    }
}
