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
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.ThemeUtils
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.ichi2.async.CollectionLoader
import com.ichi2.libanki.Collection
import com.ichi2.utils.increaseHorizontalPaddingOfOverflowMenuIcons
import com.ichi2.utils.tintOverflowMenuIcons
import timber.log.Timber

/**
 * Base class for fragments in the AnkiDroid.
 * This class provides common functionality and convenience methods for all fragments that extend it.
 *
 * Why Extend AnkiFragment:
 * - **Consistency**: Provides a consistent setup for fragments, ensuring they all handle common tasks in the same way.
 * - **Helper Methods**: Contains helper methods to reduce boilerplate code in descendant classes.
 * - **Common Initialization**: Ensures fragments have consistent initialization, such as setting navigation bar colors or checking storage permissions.
 *
 * @param layout Resource ID of the layout to be used for this fragment.
 */
// TODO: Consider refactoring to create AnkiInterface to consolidate common implementations between AnkiFragment and AnkiActivity.
//  This could help reduce code repetition and improve maintainability.
open class AnkiFragment(@LayoutRes layout: Int) : Fragment(layout) {

    val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    val ankiActivity: AnkiActivity
        get() = requireAnkiActivity()

    val mainToolbar: Toolbar
        get() = requireView().findViewById(R.id.toolbar)

    // Open function: These can be overridden to react to specific parts of the lifecycle

    /**
     * Callback for [startLoadingCollection], executed once the collection is available.
     */
    protected open fun onCollectionLoaded(col: Collection) {
        hideProgressBar()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().window.statusBarColor = ThemeUtils.getThemeAttrColor(requireContext(), R.attr.appBarColor)
        super.onViewCreated(view, savedInstanceState)
    }

    // Helper functions: These make fragment code shorter

    /**
     * Check whether animation is enabled
     *
     * @see AnkiActivity.animationEnabled
     */
    protected fun animationEnabled() = ankiActivity.animationEnabled()

    /**
     * Open `url` in a custom tab or in a browser.
     *
     * @see AnkiActivity.openUrl
     */
    protected fun openUrl(url: Uri) = ankiActivity.openUrl(url)

    /**
     * Checks whether we are allowed to change the schema
     *
     * @see AnkiActivity.userAcceptsSchemaChange
     */
    protected suspend fun userAcceptsSchemaChange() = ankiActivity.userAcceptsSchemaChange()

    fun setNavigationBarColor(@AttrRes attr: Int) {
        requireActivity().window.navigationBarColor = ThemeUtils.getThemeAttrColor(requireContext(), attr)
    }

    /**
     * Finds a view in the fragment's layout by the specified ID.
     *
     */
    fun <T : View> findViewById(@IdRes id: Int): T {
        return requireView().findViewById(id)
    }

    /**
     * Hides progress bar.
     */
    private fun hideProgressBar() = ankiActivity.hideProgressBar()

    /**
     * Shows progress bar.
     */
    private fun showProgressBar() = ankiActivity.showProgressBar()

    /**
     * Unregisters a previously registered broadcast receiver.
     *
     * @param unmountReceiver The BroadcastReceiver instance to unregister.
     */
    protected fun unregisterReceiver(unmountReceiver: BroadcastReceiver?) {
        requireActivity().unregisterReceiver(unmountReceiver)
    }

    /**
     * Increases the horizontal padding of overflow menu icons in the given Menu.
     *
     * @see AnkiActivity.increaseHorizontalPaddingOfOverflowMenuIcons
     */
    protected fun increaseHorizontalPaddingOfOverflowMenuIcons(menu: Menu) {
        requireContext().increaseHorizontalPaddingOfOverflowMenuIcons(menu)
    }

    /**
     * Sets color to icons of overflow menu items in given Menu.
     *
     * @see AnkiActivity.tintOverflowMenuIcons
     */
    protected fun tintOverflowMenuIcons(menu: Menu) {
        requireContext().tintOverflowMenuIcons(menu)
    }

    /**
     * Invalidates the options menu, causing it to be recreated.
     */
    protected fun invalidateMenu() {
        requireActivity().invalidateMenu()
    }

    /**
     * Sets the title of the toolbar.
     *
     */
    protected fun setTitle(@StringRes title: Int) {
        mainToolbar.setTitle(title)
    }

    /**
     * Starts loading the Anki collection asynchronously if it hasn't been opened yet.
     * If the collection is already open, calls `onCollectionLoaded` synchronously.
     * Shows a progress bar during loading.
     */
    protected fun startLoadingCollection() {
        Timber.d("AnkiFragment.startLoadingCollection()")
        if (CollectionManager.isOpenUnsafe()) {
            Timber.d("Synchronously calling onCollectionLoaded")
            onCollectionLoaded(getColUnsafe)
            return
        }
        // Open collection asynchronously if it hasn't already been opened
        showProgressBar()
        CollectionLoader.load(
            this
        ) { col: Collection? ->
            if (col != null) {
                Timber.d("Asynchronously calling onCollectionLoaded")
                onCollectionLoaded(col)
            } else {
                ankiActivity.onCollectionLoadError()
            }
        }
    }

    /**
     * Method to show dialog fragment including adding it to back stack
     *
     * @see AnkiActivity.showDialogFragment
     */
    protected open fun showDialogFragment(newFragment: DialogFragment) = ankiActivity.showDialogFragment(newFragment)

    /**
     * Run the provided operation, showing a progress window with the provided
     * message until the operation completes.
     */
    protected suspend fun <T> Fragment.withProgress(
        message: String = resources.getString(R.string.dialog_processing),
        block: suspend () -> T
    ): T =
        requireActivity().withProgress(message, block)

    /**
     * If storage permissions are not granted, shows a toast message and finishes the activity.
     *
     * This should be called AFTER a call to `super.`[onCreate]
     *
     * @return `true`: activity may continue to start, `false`: [onCreate] should stop executing
     * as storage permissions are mot granted
     */
    protected fun ensureStoragePermissions(): Boolean {
        if (IntentHandler.grantedStoragePermissions(requireContext(), showToast = true)) {
            return true
        }
        Timber.w("finishing activity. No storage permission")
        requireActivity().finish()
        return false
    }
}
