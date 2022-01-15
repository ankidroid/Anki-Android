/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
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
 * this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.                  *
 ***************************************************************************************/
package com.ichi2.anki.jsaddons

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.*
import com.ichi2.anki.AnkiActivity.showDialogFragment
import com.ichi2.async.TaskManager

class AddonsDownloadAdapter(private var addonList: MutableList<AddonModel>) : RecyclerView.Adapter<AddonsDownloadAdapter.AddonsViewHolder>() {
    private var preferences: SharedPreferences? = null
    private var context: Context? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonsViewHolder {
        context = parent.context
        preferences = AnkiDroidApp.getSharedPrefs(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.addon_item_in_download, parent, false)
        return AddonsViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddonsViewHolder, position: Int) {
        val addonModel: AddonModel = addonList[position]
        holder.addonTitleTextView.text = addonModel.addonTitle
        holder.addonDescription.text = addonModel.description
        holder.addonTypeVersion.text = context?.getString(R.string.addon_type_and_version, addonModel.addonType, addonModel.version)

        holder.itemView.setOnClickListener {
            val addonModelDialog = AddonDetailsDialog(context!!, addonModel)
            showDialogFragment(context as AnkiActivity?, addonModelDialog)
        }

        holder.installButton.setOnClickListener {

            val progressDialog = Dialog(context as Activity)
            progressDialog.setContentView(R.layout.addon_progress_bar_layout)
            progressDialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            progressDialog.setCancelable(false)

            // call another task which download .tgz file and extract and copy to addons folder
            // here result is tarBallUrl
            val cancellable = TaskManager.launchCollectionTask(
                NpmPackageDownloader.DownloadAddon(context as Activity, addonModel.dist!!["tarball"]),
                NpmPackageDownloader.DownloadAddonListener(context as Activity, addonModel.addonTitle, progressDialog)
            )

            val cancelRunnable = Runnable {
                cancellable.cancel(true)
                progressDialog.dismiss()
            }

            val cancelButton: Button = progressDialog.findViewById(R.id.cancel_action)
            cancelButton.setText(R.string.dialog_cancel)
            cancelButton.setOnClickListener { cancelRunnable.run() }

            progressDialog.show()
        }
    }

    override fun getItemCount(): Int {
        return addonList.size
    }

    inner class AddonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var addonTitleTextView: TextView = itemView.findViewById(R.id.addon_title)
        var addonDescription: TextView = itemView.findViewById(R.id.addon_description)
        var addonTypeVersion: TextView = itemView.findViewById(R.id.addon_type_version)
        var installButton: ImageButton = itemView.findViewById(R.id.install_addon_button)
    }
}
