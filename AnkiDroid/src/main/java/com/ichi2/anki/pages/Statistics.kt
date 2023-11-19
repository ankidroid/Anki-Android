/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.anki.utils.getTimestamp
import com.ichi2.libanki.utils.TimeManager

class Statistics : PageFragment() {
    override val title = R.string.statistics
    override val pageName = "graphs"
    override var webViewClient = PageWebViewClient()
    override var webChromeClient = PageChromeClient()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().invalidateOptionsMenu()
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.statistics, menu)
                    val exportStats = menu.findItem(R.id.action_export_stats)
                    exportStats?.title = CollectionManager.TR.statisticsSavePdf()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_export_stats -> {
                            exportWebViewContentAsPDF()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    /**Prepares and initiates a printing task for the content(stats) displayed in the WebView.
     * It uses the Android PrintManager service to create a print job, based on the content of the WebView.
     * The resulting output is a PDF document. **/
    fun exportWebViewContentAsPDF() {
        val printManager = getSystemService(requireContext(), PrintManager::class.java)
        val currentDateTime = getTimestamp(TimeManager.time)
        val jobName = "${getString(R.string.app_name)}-stats-$currentDateTime"
        val printAdapter = webView.createPrintDocumentAdapter(jobName)
        printManager?.print(
            jobName,
            printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    companion object {
        fun getIntent(context: Context): Intent {
            return PagesActivity.getIntent(context, Statistics::class)
        }
    }
}
