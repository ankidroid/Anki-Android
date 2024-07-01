/*
 Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

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
package com.ichi2.anki

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.ichi2.async.CollectionLoader
import com.ichi2.libanki.Collection
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.tintOverflowMenuIcons
import timber.log.Timber

open class AnkiFragment(@LayoutRes layout: Int) : Fragment(layout), Toolbar.OnMenuItemClickListener, DispatchKeyEventListener {

    val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    lateinit var ankiActivity: AnkiActivity

    lateinit var mainToolbar: Toolbar

    private fun colIsOpenUnsafe(): Boolean {
        return CollectionManager.isOpenUnsafe()
    }

    fun animationEnabled(): Boolean {
        return !ankiActivity.animationDisabled()
    }

    open fun openUrl(url: Uri) {
        ankiActivity.openUrl(url)
    }

    protected fun enableToolbar(view: View) {
        mainToolbar = view.findViewById(R.id.toolbar)
    }

    open fun hideProgressBar() {
        ankiActivity.hideProgressBar()
    }

    private fun showProgressBar() {
        ankiActivity.showProgressBar()
    }

    protected fun registerReceiver(unmountReceiver: BroadcastReceiver?, iFilter: IntentFilter) {
        requireContext().registerReceiver(unmountReceiver, iFilter)
    }

    protected fun increaseHorizontalPaddingOfOverflowMenuIcons(menu: Menu) {
        requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(menu)
    }

    protected fun tintOverflowMenuIcons(menu: Menu) {
        requireContext().tintOverflowMenuIcons(menu)
    }

    protected fun invalidateMenu() {
        requireActivity().invalidateMenu()
    }

    protected open fun onCollectionLoaded(col: Collection) {
        hideProgressBar()
    }

    protected fun setTitle(title: Int) {
        mainToolbar.setTitle(title)
    }

    protected open fun startLoadingCollection() {
        Timber.d("AnkiFragment.startLoadingCollection()")
        if (colIsOpenUnsafe()) {
            Timber.d("Synchronously calling onCollectionLoaded")
            onCollectionLoaded(getColUnsafe)
            return
        }
        // Open collection asynchronously if it hasn't already been opened
        showProgressBar()
        CollectionLoader.load(
            this
        ) { col: com.ichi2.libanki.Collection? ->
            if (col != null) {
                Timber.d("Asynchronously calling onCollectionLoaded")
                onCollectionLoaded(col)
            } else {
                onCollectionLoadError()
            }
        }
    }

    protected open fun showDialogFragment(newFragment: DialogFragment) {
        ankiActivity.showDialogFragment(newFragment)
    }

    suspend fun <T> Fragment.withProgress(
        message: String = resources.getString(R.string.dialog_processing),
        block: suspend () -> T
    ): T =
        requireActivity().withProgress(message, block)

    private fun onCollectionLoadError() {
        val deckPicker = Intent(requireContext(), DeckPicker::class.java)
        deckPicker.putExtra("collectionLoadError", true) // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ankiActivity = activity as AnkiActivity
        if (ankiActivity.showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        if (!ankiActivity.ensureStoragePermissions()) {
            return
        }
        ankiActivity.setNavigationBarColor(R.attr.toolbarBackgroundColor)
        requireActivity().window.statusBarColor = ThemeUtils.getThemeAttrColor(requireContext(), R.attr.appBarColor)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return false
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return false
    }
}
