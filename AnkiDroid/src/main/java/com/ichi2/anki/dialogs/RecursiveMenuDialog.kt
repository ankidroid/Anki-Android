/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CrashReportService
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.Companion.openImportFilePicker
import com.ichi2.anki.dialogs.RecursiveMenuItemAction.*
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.IntentUtil.tryOpenIntent
import timber.log.Timber

/**
 * A dialog which can show items in a recursive manner. Mainly used for showing the "Help" and
 * "Support AnkiDroid" menu dialogs.
 *
 * Note: class is marked as open only to allow testing.
 */
open class RecursiveMenuDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val menuItems = getMenuItems()
        val titleResourceId = requireContext().getString(
            requireArguments().getInt(KEY_TITLE_RESOURCE_ID)
        )
        val adapter = RecursiveMenuAdapter(
            layoutInflater,
            menuItems.filter { it.shouldBeVisible }
        ) { handleMenuItemSelection(it, getActivityDelegate()) }
        // TODO DEFECT: There is 9dp of bottom margin which we can't remove, see if it still is an
        //  issue after updating the material dialogs library
        val dialog = MaterialDialog.Builder(requireContext())
            .adapter(adapter, null)
            .title(titleResourceId)
            .show()
        setMenuBreadcrumbHeader(dialog)
        return dialog
    }

    /**
     * Method available for subclasses to replace the activity used(useful in testing).
     */
    protected open fun getActivityDelegate(): AnkiActivity = requireActivity() as AnkiActivity

    private fun handleMenuItemSelection(menuItem: RecursiveMenuItem, ankiActivity: AnkiActivity) {
        UsageAnalytics.sendAnalyticsEvent(
            UsageAnalytics.Category.LINK_CLICKED,
            menuItem.analyticsId
        )
        when (menuItem.action) {
            is OpenUrl -> ankiActivity.openUrl(Uri.parse(menuItem.action.url))
            is OpenUrlResource -> ankiActivity.openUrl(Uri.parse(ankiActivity.getString(menuItem.action.urlResourceId)))
            Rate -> tryOpenIntent(ankiActivity, AnkiDroidApp.getMarketIntent(ankiActivity))
            is Importer -> openImportFilePicker(ankiActivity, menuItem.action.multiple)
            ReportError -> {
                if (isUserATestClient) {
                    showThemedToast(
                        ankiActivity,
                        ankiActivity.getString(R.string.user_is_a_robot),
                        false
                    )
                } else {
                    val wasReportSent = CrashReportService.sendReport(ankiActivity)
                    if (!wasReportSent) {
                        showThemedToast(
                            ankiActivity,
                            ankiActivity.getString(R.string.help_dialog_exception_report_debounce),
                            true
                        )
                    }
                }
            }
            Header -> {
                val nextMenuItems = getMenuItems().map { currentMenuItem ->
                    currentMenuItem.copy(shouldBeVisible = menuItem.id == currentMenuItem.parentId)
                }.toTypedArray()
                ankiActivity.showDialogFragment(
                    createInstance(nextMenuItems, menuItem.titleResourceId)
                )
            }
        }
    }

    private fun getMenuItems(): Array<RecursiveMenuItem> {
        return requireArguments().getParcelableArray(KEY_MENU_ITEMS)
            ?.map { it as RecursiveMenuItem }?.toTypedArray()
            ?: error("RecursiveMenuDialog expects a list of menu items to show!")
    }

    private fun setMenuBreadcrumbHeader(dialog: MaterialDialog) {
        try {
            dialog.findViewById(R.id.md_titleFrame).apply {
                setPadding(10, 22, 10, 10)
                setOnClickListener { dismiss() }
            }
            (dialog.findViewById(R.id.md_icon) as ImageView).apply {
                visibility = View.VISIBLE
                val iconValue = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_menu_back_black_24dp
                )
                iconValue!!.isAutoMirrored = true
                setImageDrawable(iconValue)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to set Menu title/icon")
        }
    }

    private class RecursiveMenuAdapter(
        private val layoutInflater: LayoutInflater,
        private val menuItems: List<RecursiveMenuItem>,
        private val selectionHandler: (RecursiveMenuItem) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val root = layoutInflater.inflate(R.layout.material_dialog_list_item, parent, false)
            return object : RecyclerView.ViewHolder(root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val textView = holder.itemView as TextView
            val item = menuItems[position]
            textView.setText(item.titleResourceId)
            textView.setOnClickListener { selectionHandler(item) }
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                item.iconResourceId, 0, 0, 0
            )
        }

        override fun getItemCount(): Int = menuItems.size
    }

    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_MENU_ITEMS = "key_menu_items"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val KEY_TITLE_RESOURCE_ID = "key_title_resource_id"

        fun createInstance(
            menuItems: Array<RecursiveMenuItem>,
            @StringRes titleResourceId: Int
        ): RecursiveMenuDialog {
            return RecursiveMenuDialog().apply {
                arguments = bundleOf(
                    KEY_MENU_ITEMS to menuItems,
                    KEY_TITLE_RESOURCE_ID to titleResourceId
                )
            }
        }
    }
}
