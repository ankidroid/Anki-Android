/****************************************************************************************
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
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
 ****************************************************************************************/
package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.ClosableDrawerLayout
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.dialogs.HelpDialog
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.workarounds.FullDraggableContainerFix
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.CardId
import com.ichi2.themes.Themes
import com.ichi2.utils.HandlerUtils
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

@KotlinCleanup("IDE-lint")
abstract class NavigationDrawerActivity :
    AnkiActivity(),
    NavigationView.OnNavigationItemSelectedListener {
    /**
     * Navigation Drawer
     */
    var fragmented = false
        protected set
    private var mNavButtonGoesBack = false

    // Navigation drawer list item entries
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var mNavigationView: NavigationView
    lateinit var drawerToggle: ActionBarDrawerToggle
        private set

    /**
     * runnable that will be executed after the drawer has been closed.
     */
    private var mPendingRunnable: Runnable? = null

    override fun setContentView(@LayoutRes layoutResID: Int) {
        val preferences = baseContext.sharedPrefs()

        // Using ClosableDrawerLayout as a parent view.
        val closableDrawerLayout = LayoutInflater.from(this).inflate(
            navigationDrawerLayout,
            null,
            false
        ) as ClosableDrawerLayout
        // Get CoordinatorLayout using resource ID
        val coordinatorLayout = LayoutInflater.from(this)
            .inflate(layoutResID, closableDrawerLayout, false) as CoordinatorLayout
        if (preferences.getBoolean(FULL_SCREEN_NAVIGATION_DRAWER, false)) {
            // If full screen navigation drawer is needed, then add FullDraggableContainer as a child view of closableDrawerLayout.
            // Then add coordinatorLayout as a child view of fullDraggableContainer.
            val fullDraggableContainer = FullDraggableContainerFix(this)
            fullDraggableContainer.addView(coordinatorLayout)
            closableDrawerLayout.addView(fullDraggableContainer, 0)
        } else {
            // If full screen navigation drawer is not needed, then directly add coordinatorLayout as the child view.
            closableDrawerLayout.addView(coordinatorLayout, 0)
        }
        setContentView(closableDrawerLayout)
    }

    @get:LayoutRes
    private val navigationDrawerLayout: Int
        get() = if (fitsSystemWindows()) R.layout.navigation_drawer_layout else R.layout.navigation_drawer_layout_fullscreen

    /** Whether android:fitsSystemWindows="true" should be applied to the navigation drawer  */
    protected open fun fitsSystemWindows(): Boolean {
        return true
    }

    // Navigation drawer initialisation
    protected fun initNavigationDrawer(mainView: View) {
        // Create inherited navigation drawer layout here so that it can be used by parent class
        mDrawerLayout = mainView.findViewById(R.id.drawer_layout)
        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        // Force transparent status bar with primary dark color underlaid so that the drawer displays under status bar
        window.statusBarColor = getColor(R.color.transparent)
        mDrawerLayout.setStatusBarBackgroundColor(
            Themes.getColorFromAttr(
                this,
                android.R.attr.colorPrimary
            )
        )
        // Setup toolbar and hamburger
        mNavigationView = mDrawerLayout.findViewById(R.id.navdrawer_items_container)
        mNavigationView.setNavigationItemSelectedListener(this)
        val toolbar: Toolbar? = mainView.findViewById(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            // enable ActionBar app icon to behave as action to toggle nav drawer
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setHomeButtonEnabled(true)

            // Decide which action to take when the navigation button is tapped.
            toolbar.setNavigationOnClickListener { onNavigationPressed() }
        }
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        drawerToggle = object : ActionBarDrawerToggle(
            this,
            mDrawerLayout,
            R.string.drawer_open,
            R.string.drawer_close
        ) {

            override fun onDrawerClosed(drawerView: View) {
                super.onDrawerClosed(drawerView)
                invalidateOptionsMenu()

                // If animations are disabled, this is executed before onNavigationItemSelected is called
                // PERF: May be able to reduce this delay
                HandlerUtils.postDelayedOnNewHandler({
                    if (mPendingRunnable != null) {
                        HandlerUtils.postOnNewHandler(mPendingRunnable!!) // TODO: See if we can use the same handler here
                        mPendingRunnable = null
                    }
                }, 100)
            }

            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu()
            }
        }
        if (mDrawerLayout is ClosableDrawerLayout) {
            (mDrawerLayout as ClosableDrawerLayout).setAnimationEnabled(animationEnabled())
        } else {
            Timber.w("Unexpected Drawer layout - could not modify navigation animation")
        }
        drawerToggle.isDrawerSlideAnimationEnabled = animationEnabled()
        mDrawerLayout.addDrawerListener(drawerToggle)

        enablePostShortcut(this)
    }

    /**
     * Sets selected navigation drawer item
     */
    protected fun selectNavigationItem(itemId: Int) {
        val menu = mNavigationView.menu
        if (itemId == -1) {
            for (i in 0 until menu.size()) {
                menu.getItem(i).isChecked = false
            }
        } else {
            val item = menu.findItem(itemId)
            if (item != null) {
                item.isChecked = true
            } else {
                Timber.e("Could not find item %d", itemId)
            }
        }
    }

    override fun setTitle(title: CharSequence) {
        if (supportActionBar != null) {
            supportActionBar!!.title = title
        }
        super.setTitle(title)
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState()
    }

    private val preferences: SharedPreferences
        get() = this@NavigationDrawerActivity.sharedPrefs()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig)
    }

    /**
     * This function locks the navigation drawer closed in regards to swipes,
     * but continues to allowed it to be opened via it's indicator button. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected fun disableDrawerSwipe() {
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    /**
     * This function allows swipes to open the navigation drawer. This
     * function in a noop if the drawer hasn't been initialized.
     */
    protected fun enableDrawerSwipe() {
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    private val mPreferencesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val preferences = preferences
            Timber.i(
                "Handling Activity Result: %d. Result: %d",
                REQUEST_PREFERENCES_UPDATE,
                result.resultCode
            )
            CompatHelper.compat.setupNotificationChannel(applicationContext)
            // Restart the activity on preference change
            // collection path hasn't been changed so just restart the current activity
            if (this is Reviewer && preferences.getBoolean("tts", false)) {
                // Workaround to kick user back to StudyOptions after opening settings from Reviewer
                // because onDestroy() of old Activity interferes with TTS in new Activity
                finish()
            } else {
                ActivityCompat.recreate(this)
            }
        }

    @Suppress("deprecation") // onBackPressed
    override fun onBackPressed() {
        if (isDrawerOpen) {
            Timber.i("Back key pressed")
            closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Called, when navigation button of the action bar is pressed.
     * Design pattern: template method. Subclasses can override this to define their own behaviour.
     */
    protected open fun onNavigationPressed() {
        if (mNavButtonGoesBack) {
            finishWithAnimation(END)
        } else {
            openDrawer()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Don't do anything if user selects already selected position
        if (item.isChecked) {
            closeDrawer()
            return true
        }

        /*
         * This runnable will be executed in onDrawerClosed(...)
         * to make the animation more fluid on older devices.
         */
        mPendingRunnable = Runnable {
            // Take action if a different item selected
            when (item.itemId) {
                R.id.nav_decks -> {
                    Timber.i("Navigating to decks")
                    val deckPicker = Intent(this@NavigationDrawerActivity, DeckPicker::class.java)
                    // opening DeckPicker should use the instance on the back stack & clear back history
                    deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivityWithAnimation(deckPicker, END)
                }

                R.id.nav_browser -> {
                    Timber.i("Navigating to card browser")
                    openCardBrowser()
                }

                R.id.nav_stats -> {
                    Timber.i("Navigating to stats")
                    val intent = com.ichi2.anki.pages.Statistics.getIntent(this)
                    startActivityWithAnimation(intent, START)
                }

                R.id.nav_settings -> {
                    Timber.i("Navigating to settings")
                    launchActivityForResultWithAnimation(
                        Intent(
                            this@NavigationDrawerActivity,
                            Preferences::class.java
                        ),
                        mPreferencesLauncher,
                        FADE
                    )
                }

                R.id.nav_help -> {
                    Timber.i("Navigating to help")
                    showDialogFragment(HelpDialog.createInstance())
                }

                R.id.support_ankidroid -> {
                    Timber.i("Navigating to support AnkiDroid")
                    showDialogFragment(HelpDialog.createInstanceForSupportAnkiDroid(this))
                }
            }
        }
        closeDrawer()
        return true
    }

    protected fun openCardBrowser() {
        val intent = Intent(this@NavigationDrawerActivity, CardBrowser::class.java)
        if (currentCardId != null) {
            intent.putExtra("currentCard", currentCardId)
        }
        startActivityWithAnimation(intent, START)
    }

    // Override this to specify a specific card id
    protected open val currentCardId: CardId?
        get() = null

    protected fun showBackIcon() {
        drawerToggle.isDrawerIndicatorEnabled = false
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }
        mNavButtonGoesBack = true
    }

    protected fun restoreDrawerIcon() {
        drawerToggle.isDrawerIndicatorEnabled = true
        mNavButtonGoesBack = false
    }

    @VisibleForTesting
    open val isDrawerOpen: Boolean
        get() = mDrawerLayout.isDrawerOpen(GravityCompat.START)

    /**
     * Restart the activity and discard old backstack, creating it new from the hierarchy in the manifest
     */
    protected fun restartActivityInvalidateBackstack(activity: AnkiActivity) {
        Timber.i("AnkiActivity -- restartActivityInvalidateBackstack()")
        val intent = Intent()
        intent.setClass(activity, activity.javaClass)
        val stackBuilder = TaskStackBuilder.create(activity)
        stackBuilder.addNextIntentWithParentStack(intent)
        stackBuilder.startActivities(Bundle())
        activity.finish()
    }

    fun toggleDrawer() {
        if (!isDrawerOpen) {
            openDrawer()
        } else {
            closeDrawer()
        }
    }

    private fun openDrawer() {
        mDrawerLayout.openDrawer(GravityCompat.START, animationEnabled())
    }

    private fun closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START, animationEnabled())
    }

    fun focusNavigation() {
        // mNavigationView.getMenu().getItem(0).setChecked(true);
        selectNavigationItem(R.id.nav_decks)
        mNavigationView.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isDrawerOpen) {
            return super.onKeyDown(keyCode, event)
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            closeDrawer()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        // Intent request codes
        const val REQUEST_PREFERENCES_UPDATE = 100
        const val FULL_SCREEN_NAVIGATION_DRAWER = "gestureFullScreenNavigationDrawer"

        const val EXTRA_STARTED_WITH_SHORTCUT = "com.ichi2.anki.StartedWithShortcut"

        fun enablePostShortcut(context: Context) {
            // Review Cards Shortcut
            val intentReviewCards = Intent(context, Reviewer::class.java)
            intentReviewCards.action = Intent.ACTION_VIEW
            intentReviewCards.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            intentReviewCards.putExtra(EXTRA_STARTED_WITH_SHORTCUT, true)
            val reviewCardsShortcut = ShortcutInfoCompat.Builder(context, "reviewCardsShortcutId")
                .setShortLabel(context.getString(R.string.studyoptions_start))
                .setLongLabel(context.getString(R.string.studyoptions_start))
                .setIcon(IconCompat.createWithResource(context, R.drawable.review_shortcut))
                .setIntent(intentReviewCards)
                .build()

            // Add Shortcut
            val intentAddNote = Intent(context, NoteEditor::class.java)
            intentAddNote.action = Intent.ACTION_VIEW
            intentAddNote.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            intentAddNote.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
            val noteEditorShortcut = ShortcutInfoCompat.Builder(context, "noteEditorShortcutId")
                .setShortLabel(context.getString(R.string.menu_add))
                .setLongLabel(context.getString(R.string.menu_add))
                .setIcon(IconCompat.createWithResource(context, R.drawable.add_shortcut))
                .setIntent(intentAddNote)
                .build()

            // CardBrowser Shortcut
            val intentCardBrowser = Intent(context, CardBrowser::class.java)
            intentCardBrowser.action = Intent.ACTION_VIEW
            intentCardBrowser.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
            val cardBrowserShortcut = ShortcutInfoCompat.Builder(context, "cardBrowserShortcutId")
                .setShortLabel(context.getString(R.string.card_browser))
                .setLongLabel(context.getString(R.string.card_browser))
                .setIcon(IconCompat.createWithResource(context, R.drawable.browse_shortcut))
                .setIntent(intentCardBrowser)
                .build()
            ShortcutManagerCompat.addDynamicShortcuts(
                context,
                listOf(
                    reviewCardsShortcut,
                    noteEditorShortcut,
                    cardBrowserShortcut
                )
            )
        }
    }
}
