/****************************************************************************************
 * Copyright (c) 2009 Andrew Dubya <andrewdubya@gmail.com>                              *
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Daniel Svard <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>
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

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.SQLException
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.TextUtils
import android.util.Pair
import android.util.TypedValue
import android.view.*
import android.view.View.OnLongClickListener
import android.view.WindowManager.BadTokenException
import android.widget.*
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.GravityEnum
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.CollectionHelper.CollectionIntegrityStorageCheck
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.*
import com.ichi2.anki.StudyOptionsFragment.DeckStudyData
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.DeckPickerNoSpaceToDowngradeDialog.FileSizeFormatter
import com.ichi2.anki.dialogs.ImportDialog.ImportDialogListener
import com.ichi2.anki.dialogs.MediaCheckDialog.MediaCheckDialogListener
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.export.ActivityExportingDelegate
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.servicelayer.DeckService
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.anki.servicelayer.UndoService.Undo
import com.ichi2.anki.stats.AnkiStatsTaskHandler
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.async.*
import com.ichi2.async.CollectionTask.*
import com.ichi2.async.Connection.CancellableTaskListener
import com.ichi2.async.Connection.ConflictResolution
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.libanki.Collection.CheckDatabaseResult
import com.ichi2.libanki.Consts
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Utils
import com.ichi2.libanki.backend.exception.DeckRenameException
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.TreeNode
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.libanki.sync.Syncer.ConnectionResultType
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.*
import com.ichi2.utils.Permissions.hasStorageAccessPermission
import com.ichi2.widget.WidgetStatus
import timber.log.Timber
import java.io.File

@KotlinCleanup("automatic IDE lint")
@KotlinCleanup("lots to do")
open class DeckPicker : NavigationDrawerActivity(), StudyOptionsListener, SyncErrorDialogListener, ImportDialogListener, MediaCheckDialogListener, OnRequestPermissionsResultCallback, CustomStudyListener {
    // Short animation duration from system
    private var mShortAnimDuration = 0
    private var mBackButtonPressedToExit = false
    private var mDeckPickerContent: RelativeLayout? = null
    private var mProgressDialog: MaterialDialog? = null
    private var mStudyoptionsFrame: View? = null
    private var mRecyclerView: RecyclerView? = null
    private var mRecyclerViewLayoutManager: LinearLayoutManager? = null
    private var mDeckListAdapter: DeckAdapter? = null
    private val mSnackbarShowHideCallback = Snackbar.Callback()
    private var mExportingDelegate: ActivityExportingDelegate? = null
    @KotlinCleanup("lateinit a lot of these")
    private var mNoDecksPlaceholder: LinearLayout? = null
    private var mPullToSyncWrapper: SwipeRefreshLayout? = null
    private var mReviewSummaryTextView: TextView? = null
    private var mUnmountReceiver: BroadcastReceiver? = null
    private val mDialogEditText: EditText? = null
    private var mFloatingActionMenu: DeckPickerFloatingActionMenu? = null

    // flag asking user to do a full sync which is used in upgrade path
    private var mRecommendFullSync = false

    // flag keeping track of when the app has been paused
    private var mActivityPaused = false

    // Flag to keep track of startup error
    private var mStartupError = false
    private val mExportFileName: String? = null
    private var mEmptyCardTask: Cancellable? = null

    @JvmField
    @VisibleForTesting
    var mDueTree: List<TreeNode<AbstractDeckTreeNode>>? = null

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var mSyncOnResume = false

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    private var mFocusedDeck: Long = 0

    /** If we have accepted the "We will show you permissions" dialog, don't show it again on activity rebirth  */
    private var mClosedWelcomeMessage = false
    private var mToolbarSearchView: SearchView? = null
    private var mCustomStudyDialogFactory: CustomStudyDialogFactory? = null
    private var mContextMenuFactory: DeckPickerContextMenu.Factory? = null

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val mDeckExpanderClickListener = View.OnClickListener { view: View ->
        val did = view.tag as Long
        if (!getCol().decks.children(did).isEmpty()) {
            getCol().decks.collapse(did)
            __renderPage()
            dismissAllDialogFragments()
        }
    }
    private val mDeckClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.DEFAULT) }
    private val mCountsClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.SHOW_STUDY_OPTIONS) }
    private fun onDeckClick(v: View, selectionType: DeckSelectionType) {
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        var collectionIsOpen = false
        try {
            collectionIsOpen = colIsOpen()
            handleDeckSelection(deckId, selectionType)
            if (mFragmented) {
                // Calling notifyDataSetChanged() will update the color of the selected deck.
                // This interferes with the ripple effect, so we don't do it if lollipop and not tablet view
                mDeckListAdapter!!.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            // Maybe later don't report if collectionIsOpen is false?
            Timber.w(e)
            val info = "$deckId colOpen:$collectionIsOpen"
            AnkiDroidApp.sendExceptionReport(e, "deckPicker::onDeckClick", info)
            displayFailedToOpenDeck(deckId)
        }
    }

    private fun displayFailedToOpenDeck(deckId: Long) {
        // #6208 - if the click is accepted before the sync completes, we get a failure.
        // We use the Deck ID as the deck likely doesn't exist any more.
        val message = getString(R.string.deck_picker_failed_deck_load, java.lang.Long.toString(deckId))
        showThemedToast(this, message, false)
        Timber.w(message)
    }

    private val mDeckLongClickListener = OnLongClickListener { v ->
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Long tapped on deck with id %d", deckId)
        showDialogFragment(mContextMenuFactory!!.newDeckPickerContextMenu(deckId))
        true
    }
    @KotlinCleanup("remove ?")
    open val backupManager: BackupManager?
        get() = BackupManager()
    private val mImportAddListener = ImportAddListener(this)

    private class ImportAddListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, String, Triple<AnkiPackageImporter, Boolean, String?>>(deckPicker) {
        override fun actualOnPostExecute(context: DeckPicker, result: Triple<AnkiPackageImporter, Boolean, String?>) {
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            // If boolean and string are both set, we are signalling an error message
            // instead of a successful result.
            if (result.second && result.third != null) {
                Timber.w("Import: Add Failed: %s", result.third)
                context.showSimpleMessageDialog(result.third)
            } else {
                Timber.i("Import: Add succeeded")
                val imp = result.first
                context.showSimpleMessageDialog(TextUtils.join("\n", imp.log))
                context.updateDeckList()
            }
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
                context.mProgressDialog = StyledProgressDialog.show(
                    context,
                    context.resources.getString(R.string.import_title), null, false
                )
            }
        }

        override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
            context.mProgressDialog!!.setContent(value)
        }
    }

    private fun importReplaceListener(): ImportReplaceListener {
        return ImportReplaceListener(this)
    }

    private class ImportReplaceListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, String, Computation<*>>(deckPicker) {
        override fun actualOnPostExecute(context: DeckPicker, result: Computation<*>) {
            Timber.i("Import: Replace Task Completed")
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            val res = context.resources
            if (result.succeeded()) {
                context.updateDeckList()
            } else {
                context.showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg), true)
            }
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
                context.mProgressDialog = StyledProgressDialog.show(
                    context,
                    context.resources.getString(R.string.import_title),
                    context.resources.getString(R.string.import_replacing), false
                )
            }
        }

        /**
         * @param value A message
         */
        override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
            context.mProgressDialog!!.setContent(value)
        }
    }
    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------
    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    @KotlinCleanup("scope functions")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        mExportingDelegate = ActivityExportingDelegate(this) { getCol() }
        mCustomStudyDialogFactory = CustomStudyDialogFactory({ getCol() }, this).attachToActivity<CustomStudyDialogFactory>(this)
        mContextMenuFactory = DeckPickerContextMenu.Factory { getCol() }.attachToActivity<DeckPickerContextMenu.Factory>(this)

        // we need to restore here, as we need it before super.onCreate() is called.
        restoreWelcomeMessage(savedInstanceState)

        // Then set theme and content view
        super.onCreate(savedInstanceState)
        handleStartup()
        setContentView(R.layout.homescreen)
        val mainView = findViewById<View>(android.R.id.content)

        // check, if tablet layout
        mStudyoptionsFrame = findViewById(R.id.studyoptions_fragment)
        // set protected variable from NavigationDrawerActivity
        mFragmented = mStudyoptionsFrame != null && mStudyoptionsFrame!!.visibility == View.VISIBLE

        // Open StudyOptionsFragment if in fragmented mode
        if (mFragmented && !mStartupError) {
            loadStudyOptionsFragment(false)
        }
        registerExternalStorageListener()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView)
        title = resources.getString(R.string.app_name)

        mDeckPickerContent = findViewById(R.id.deck_picker_content)
        mRecyclerView = findViewById(R.id.files)
        mNoDecksPlaceholder = findViewById(R.id.no_decks_placeholder)

        mDeckPickerContent!!.setVisibility(View.GONE)
        mNoDecksPlaceholder!!.setVisibility(View.GONE)

        // specify a LinearLayoutManager and set up item dividers for the RecyclerView
        mRecyclerViewLayoutManager = LinearLayoutManager(this)
        mRecyclerView!!.setLayoutManager(mRecyclerViewLayoutManager)
        val ta = this.obtainStyledAttributes(intArrayOf(R.attr.deckDivider))
        val divider = ta.getDrawable(0)
        ta.recycle()
        val dividerDecorator = DividerItemDecoration(this, mRecyclerViewLayoutManager!!.orientation)
        dividerDecorator.setDrawable(divider!!)
        mRecyclerView!!.addItemDecoration(dividerDecorator)

        // Add background to Deckpicker activity
        val view = if (mFragmented) findViewById(R.id.deckpicker_xl_view) else findViewById<View>(R.id.root_layout)

        var hasDeckPickerBackground = false
        try {
            hasDeckPickerBackground = applyDeckPickerBackground(view)
        } catch (e: OutOfMemoryError) { // 6608 - OOM should be catchable here.
            Timber.w(e, "Failed to apply background - OOM")
            showThemedToast(this, getString(R.string.background_image_too_large), false)
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply background")
            showThemedToast(this, getString(R.string.failed_to_apply_background_image, e.localizedMessage), false)
        }

        // create and set an adapter for the RecyclerView
        mDeckListAdapter = DeckAdapter(layoutInflater, this)
        mDeckListAdapter!!.setDeckClickListener(mDeckClickListener)
        mDeckListAdapter!!.setCountsClickListener(mCountsClickListener)
        mDeckListAdapter!!.setDeckExpanderClickListener(mDeckExpanderClickListener)
        mDeckListAdapter!!.setDeckLongClickListener(mDeckLongClickListener)
        mDeckListAdapter!!.enablePartialTransparencyForBackground(hasDeckPickerBackground)
        mRecyclerView!!.setAdapter(mDeckListAdapter)

        mPullToSyncWrapper = findViewById(R.id.pull_to_sync_wrapper)
        mPullToSyncWrapper!!.setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
        mPullToSyncWrapper!!.setOnRefreshListener(
            OnRefreshListener {
                Timber.i("Pull to Sync: Syncing")
                mPullToSyncWrapper!!.setRefreshing(false)
                sync()
            }
        )
        mPullToSyncWrapper!!.getViewTreeObserver().addOnScrollChangedListener { mPullToSyncWrapper!!.setEnabled(mRecyclerViewLayoutManager!!.findFirstCompletelyVisibleItemPosition() == 0) }

        // Setup the FloatingActionButtons, should work everywhere with min API >= 15
        mFloatingActionMenu = DeckPickerFloatingActionMenu(view, this)

        mReviewSummaryTextView = findViewById(R.id.today_stats_text_view)

        mShortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        Onboarding.DeckPicker(this, mRecyclerViewLayoutManager!!).onCreate()
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     */
    fun handleStartup() {
        if (hasStorageAccessPermission(this)) {
            val failure = InitialActivity.getStartupFailureType(this)
            mStartupError = if (failure == null) {
                // Show any necessary dialogs (e.g. changelog, special messages, etc)
                val sharedPrefs = AnkiDroidApp.getSharedPrefs(this)
                showStartupScreensAndDialogs(sharedPrefs, 0)
                false
            } else {
                // Show error dialogs
                handleStartupFailure(failure)
                true
            }
        } else {
            requestStoragePermission()
        }
    }

    @VisibleForTesting
    @KotlinCleanup("remove parameters named _")
    fun handleStartupFailure(failure: StartupFailure?) {
        when (failure) {
            SD_CARD_NOT_MOUNTED -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            DIRECTORY_NOT_ACCESSIBLE -> {
                Timber.i("AnkiDroid directory inaccessible")
                val i = Preferences.AdvancedSettingsFragment.getSubscreenIntent(this)
                startActivityForResultWithoutAnimation(i, REQUEST_PATH_UPDATE)
                showThemedToast(this, R.string.directory_inaccessible, false)
            }
            FUTURE_ANKIDROID_VERSION -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialog.INCOMPATIBLE_DB_VERSION)
            }
            DATABASE_LOCKED -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_DB_LOCKED)
            }
            DATABASE_DOWNGRADE_REQUIRED -> // This has a callback to continue with handleStartup
                InitialActivity.downgradeBackend(this)
            WEBVIEW_FAILED -> MaterialDialog.Builder(this)
                .title(R.string.ankidroid_init_failed_webview_title)
                .content(getString(R.string.ankidroid_init_failed_webview, AnkiDroidApp.getWebViewErrorMessage()))
                .positiveText(R.string.close)
                .onPositive { _: MaterialDialog?, _: DialogAction? -> exit() }
                .cancelable(false)
                .show()
            DB_ERROR -> displayDatabaseFailure()
            else -> displayDatabaseFailure()
        }
    }

    fun displayDatabaseFailure() {
        Timber.i("Displaying database error")
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED)
    }

    // throws doesn't seem to be checked by the compiler - consider it to be documentation
    @Throws(OutOfMemoryError::class)
    private fun applyDeckPickerBackground(view: View): Boolean {
        // Allow the user to clear data and get back to a good state if they provide an invalid background.
        if (!AnkiDroidApp.getSharedPrefs(this).getBoolean("deckPickerBackground", false)) {
            Timber.d("No DeckPicker background preference")
            view.setBackgroundResource(0)
            return false
        }
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)
        val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
        return if (!imgFile.exists()) {
            Timber.d("No DeckPicker background image")
            view.setBackgroundResource(0)
            false
        } else {
            Timber.i("Applying background")
            val drawable = Drawable.createFromPath(imgFile.absolutePath)
            view.background = drawable
            true
        }
    }

    @KotlinCleanup("remove parameters named _")
    @KotlinCleanup("return early and remove else")
    fun requestStoragePermission() {
        if (mClosedWelcomeMessage) {
            // DEFECT #5847: This fails if the activity is killed.
            // Even if the dialog is showing, we want to show it again.
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        } else {
            Timber.i("Displaying initial permission request dialog")
            // Request storage permission if we don't have it (e.g. on Android 6.0+)
            MaterialDialog.Builder(this)
                .title(R.string.collection_load_welcome_request_permissions_title)
                .titleGravity(GravityEnum.CENTER)
                .content(R.string.collection_load_welcome_request_permissions_details)
                .positiveText(R.string.dialog_ok)
                .onPositive { _: MaterialDialog?, _: DialogAction? ->
                    mClosedWelcomeMessage = true
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_STORAGE_PERMISSION
                    )
                }
                .cancelable(false)
                .canceledOnTouchOutside(false)
                .show()
        }
    }

    /**
     * It can fail if an ancestor is a filtered deck.
     * @param deckName Create a deck with this name.
     * @return Whether creation succeeded.
     */
    protected fun createNewDeck(deckName: String?): Boolean {
        Timber.i("DeckPicker:: Creating new deck...")
        try {
            getCol().decks.id(deckName!!)
        } catch (ex: DeckRenameException) {
            showThemedToast(this, ex.getLocalizedMessage(resources), false)
            return false
        }
        updateDeckList()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Null check to prevent crash when col inaccessible
        // #9081: sync leaves the collection closed, thus colIsOpen() is insufficient, carefully open the collection if possible
        return if (CollectionHelper.getInstance().getColSafe(this) == null) {
            false
        } else super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        mFloatingActionMenu!!.closeFloatingActionMenu()
        menuInflater.inflate(R.menu.deck_picker, menu)
        val sdCardAvailable = AnkiDroidApp.isSdCardMounted()
        menu.findItem(R.id.action_sync).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_new_filtered_deck).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_check_database).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_check_media).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_empty_cards).isEnabled = sdCardAvailable

        val toolbarSearchItem = menu.findItem(R.id.deck_picker_action_filter)
        toolbarSearchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            // When SearchItem is expanded
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                Timber.i("DeckPicker:: SearchItem opened")
                // Hide the floating action button if it is visible
                mFloatingActionMenu!!.hideFloatingActionButton()
                return true
            }

            // When SearchItem is collapsed
            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                Timber.i("DeckPicker:: SearchItem closed")
                // Show the floating action button if it is hidden
                mFloatingActionMenu!!.showFloatingActionButton()
                return true
            }
        })

        mToolbarSearchView = toolbarSearchItem.actionView as SearchView
        mToolbarSearchView!!.queryHint = getString(R.string.search_decks)
        mToolbarSearchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mToolbarSearchView!!.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val adapter = mRecyclerView!!.adapter as Filterable?
                adapter!!.filter.filter(newText)
                return true
            }
        })
        if (colIsOpen()) {
            displaySyncBadge(menu)

            // Show / hide undo
            if (mFragmented || !getCol().undoAvailable()) {
                menu.findItem(R.id.action_undo).isVisible = false
            } else {
                val res = resources
                menu.findItem(R.id.action_undo).isVisible = true
                val undo = res.getString(R.string.studyoptions_congrats_undo, getCol().undoName(res))
                menu.findItem(R.id.action_undo).title = undo
            }

            // Remove the filter - not necessary and search has other implications for new users.
            menu.findItem(R.id.deck_picker_action_filter).isVisible = getCol().decks.count() >= 10
        }
        return super.onCreateOptionsMenu(menu)
    }

    @VisibleForTesting
    protected open fun displaySyncBadge(menu: Menu) {
        val syncMenu = menu.findItem(R.id.action_sync)
        val syncStatus = SyncStatus.getSyncStatus { getCol() }
        when (syncStatus) {
            SyncStatus.BADGE_DISABLED, SyncStatus.NO_CHANGES, SyncStatus.INCONCLUSIVE -> {
                BadgeDrawableBuilder.removeBadge(syncMenu)
                syncMenu.setTitle(R.string.button_sync)
            }
            SyncStatus.HAS_CHANGES -> {
                // Light orange icon
                BadgeDrawableBuilder(resources)
                    .withColor(ContextCompat.getColor(this, R.color.badge_warning))
                    .replaceBadge(syncMenu)
                syncMenu.setTitle(R.string.button_sync)
            }
            SyncStatus.NO_ACCOUNT, SyncStatus.FULL_SYNC -> {
                if (syncStatus === SyncStatus.NO_ACCOUNT) {
                    syncMenu.setTitle(R.string.sync_menu_title_no_account)
                } else if (syncStatus === SyncStatus.FULL_SYNC) {
                    syncMenu.setTitle(R.string.sync_menu_title_full_sync)
                }
                // Orange-red icon with exclamation mark
                BadgeDrawableBuilder(resources)
                    .withText('!')
                    .withColor(ContextCompat.getColor(this, R.color.badge_error))
                    .replaceBadge(syncMenu)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        mFloatingActionMenu!!.closeFloatingActionMenu()

        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.action_undo) {
            Timber.i("DeckPicker:: Undo button pressed")
            undo()
            return true
        } else if (itemId == R.id.deck_picker_action_filter) {
            Timber.i("DeckPicker:: Search button pressed")
            return true
        } else if (itemId == R.id.action_sync) {
            Timber.i("DeckPicker:: Sync button pressed")
            sync()
            return true
        } else if (itemId == R.id.action_import) {
            Timber.i("DeckPicker:: Import button pressed")
            showDialogFragment(ImportFileSelectionFragment.createInstance(this))
            return true
        } else if (itemId == R.id.action_new_filtered_deck) {
            val createFilteredDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
            createFilteredDeckDialog.setOnNewDeckCreated {
                // a filtered deck was created
                openStudyOptions(true)
            }
            createFilteredDeckDialog.showFilteredDeckDialog()
            return true
        } else if (itemId == R.id.action_check_database) {
            Timber.i("DeckPicker:: Check database button pressed")
            showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_DATABASE_CHECK)
            return true
        } else if (itemId == R.id.action_check_media) {
            Timber.i("DeckPicker:: Check media button pressed")
            showMediaCheckDialog(MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK)
            return true
        } else if (itemId == R.id.action_empty_cards) {
            Timber.i("DeckPicker:: Empty cards button pressed")
            handleEmptyCards()
            return true
        } else if (itemId == R.id.action_model_browser_open) {
            Timber.i("DeckPicker:: Model browser button pressed")
            val noteTypeBrowser = Intent(this, ModelBrowser::class.java)
            startActivityForResultWithAnimation(noteTypeBrowser, 0, START)
            return true
        } else if (itemId == R.id.action_restore_backup) {
            Timber.i("DeckPicker:: Restore from backup button pressed")
            showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_RESTORE_BACKUP)
            return true
        } else if (itemId == R.id.action_export) {
            Timber.i("DeckPicker:: Export collection button pressed")
            val msg = resources.getString(R.string.confirm_apkg_export)
            mExportingDelegate!!.showExportDialog(msg)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode == RESULT_MEDIA_EJECTED) {
            onSdCardNotMounted()
            return
        } else if (resultCode == RESULT_DB_ERROR) {
            handleDbError()
            return
        }
        if (requestCode == SHOW_INFO_NEW_VERSION) {
            showStartupScreensAndDialogs(AnkiDroidApp.getSharedPrefs(baseContext), 3)
        } else if (requestCode == LOG_IN_FOR_SYNC && resultCode == RESULT_OK) {
            mSyncOnResume = true
        } else if (requestCode == REQUEST_REVIEW || requestCode == SHOW_STUDYOPTIONS) {
            if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
                // Show a message when reviewing has finished
                if (getCol().sched.count() == 0) {
                    showSimpleSnackbar(this, R.string.studyoptions_congrats_finished, false)
                } else {
                    showSimpleSnackbar(this, R.string.studyoptions_no_cards_due, false)
                }
            } else if (resultCode == AbstractFlashcardViewer.RESULT_ABORT_AND_SYNC) {
                Timber.i("Obtained Abort and Sync result")
                TaskManager.waitForAllToFinish(4)
                sync()
            }
        } else if (requestCode == REQUEST_PATH_UPDATE) {
            // The collection path was inaccessible on startup so just close the activity and let user restart
            finishWithoutAnimation()
        } else if (requestCode == PICK_APKG_FILE && resultCode == RESULT_OK) {
            val importResult = ImportUtils.handleFileImport(this, intent!!)
            if (!importResult.isSuccess) {
                ImportUtils.showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, false)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION && permissions.size == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                invalidateOptionsMenu()
                handleStartup()
            } else {
                // User denied access to file storage  so show error toast and display "App Info"
                showThemedToast(this, R.string.startup_no_storage_permission, false)
                finishWithoutAnimation()
                // Open the Android settings page for our app so that the user can grant the missing permission
                val intent = Intent()
                intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivityWithoutAnimation(intent)
            }
        }
    }

    override fun onResume() {
        Timber.d("onResume()")
        super.onResume()
        refreshState()
    }

    @Suppress("deprecation") // supportInvalidateOptionsMenu: deprecated in Java
    fun refreshState() {
        mActivityPaused = false
        if (mSyncOnResume) {
            Timber.i("Performing Sync on Resume")
            sync()
            mSyncOnResume = false
        } else if (colIsOpen()) {
            selectNavigationItem(R.id.nav_decks)
            if (mDueTree == null) {
                updateDeckList(true)
            }
            updateDeckList()
            title = resources.getString(R.string.app_name)
        }
        /* Complete task and enqueue fetching nonessential data for
          startup. */
        if (colIsOpen()) {
            TaskManager.launchCollectionTask(LoadCollectionComplete())
        }
        // Update sync status (if we've come back from a screen)
        supportInvalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean("mClosedWelcomeMessage", mClosedWelcomeMessage)
        savedInstanceState.putBoolean("mIsFABOpen", mFloatingActionMenu!!.isFABOpen)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mFloatingActionMenu!!.isFABOpen = savedInstanceState.getBoolean("mIsFABOpen")
    }

    override fun onPause() {
        Timber.d("onPause()")
        mActivityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        TaskManager.cancelAllTasks(LoadDeckCounts::class.java)
        super.onPause()
    }

    override fun onStop() {
        Timber.d("onStop()")
        super.onStop()
        if (colIsOpen()) {
            WidgetStatus.update(this)
            // Ignore the modification - a change in deck shouldn't trigger the icon for "pending changes".
            UIUtils.saveCollectionInBackground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver)
        }
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
        }
        Timber.d("onDestroy()")
    }

    private fun automaticSync() {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)

        // Check whether the option is selected, the user is signed in and last sync was AUTOMATIC_SYNC_TIME ago
        // (currently 10 minutes)
        val hkey = preferences.getString("hkey", "")
        val lastSyncTime = preferences.getLong("lastSyncTime", 0)
        if (hkey!!.length != 0 && preferences.getBoolean("automaticSyncMode", false) &&
            Connection.isOnline() && getCol().time.intTimeMS() - lastSyncTime > AUTOMATIC_SYNC_MIN_INTERVAL
        ) {
            Timber.i("Triggering Automatic Sync")
            sync()
        }
    }

    @KotlinCleanup("once in Kotlin: use HandlerUtils.executeFunctionWithDelay")
    override fun onBackPressed() {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (mFloatingActionMenu!!.isFABOpen) {
                mFloatingActionMenu!!.closeFloatingActionMenu()
            } else {
                if (!preferences.getBoolean("exitViaDoubleTapBack", false) || mBackButtonPressedToExit) {
                    automaticSync()
                    finishWithAnimation()
                } else {
                    showThemedToast(this, getString(R.string.back_pressed_once), true)
                }
                mBackButtonPressedToExit = true
                Handler(Looper.getMainLooper()).postDelayed({ mBackButtonPressedToExit = false }, Consts.SHORT_TOAST_DURATION)
            }
        }
    }

    private fun finishWithAnimation() {
        super.finishWithAnimation(DOWN)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (mToolbarSearchView != null && mToolbarSearchView!!.hasFocus()) {
            Timber.d("Skipping keypress: search action bar is focused")
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                Timber.i("Adding Note from keypress")
                addNote()
            }
            KeyEvent.KEYCODE_B -> {
                Timber.i("Open Browser from keypress")
                openCardBrowser()
            }
            KeyEvent.KEYCODE_Y -> {
                Timber.i("Sync from keypress")
                sync()
            }
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                handleDeckSelection(getCol().decks.selected(), DeckSelectionType.SKIP_STUDY_OPTIONS)
            }
            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------
    private fun restoreWelcomeMessage(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }
        mClosedWelcomeMessage = savedInstanceState.getBoolean("mClosedWelcomeMessage")
    }

    /**
     * Perform the following tasks:
     * Automatic backup
     * loadStudyOptionsFragment() if tablet
     * Automatic sync
     */
    private fun onFinishedStartup() {
        // create backup in background if needed
        BackupManager.performBackupInBackground(getCol().path, getCol().time)

        // Force a full sync if flag was set in upgrade path, asking the user to confirm if necessary
        if (mRecommendFullSync) {
            mRecommendFullSync = false
            try {
                getCol().modSchema()
            } catch (e: ConfirmModSchemaException) {
                Timber.w("Forcing full sync")
                e.log()
                // If libanki determines it's necessary to confirm the full sync then show a confirmation dialog
                // We have to show the dialog via the DialogHandler since this method is called via an async task
                val res = resources
                val handlerMessage = Message.obtain()
                handlerMessage.what = DialogHandler.MSG_SHOW_FORCE_FULL_SYNC_DIALOG
                val handlerMessageData = Bundle()
                handlerMessageData.putString(
                    "message",
                    """
     ${res.getString(R.string.full_sync_confirmation_upgrade)}
     
     ${res.getString(R.string.full_sync_confirmation)}
                    """.trimIndent()
                )
                handlerMessage.data = handlerMessageData
                dialogHandler.sendMessage(handlerMessage)
            }
        }
        automaticSync()
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendEmptyMessage(DialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG)
    }

    fun addNote() {
        val intent = Intent(this@DeckPicker, NoteEditor::class.java)
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        startActivityForResultWithAnimation(intent, ADD_NOTE, START)
    }

    @KotlinCleanup(".remove { _: MaterialDialog?, _: DialogAction? -> } ")
    private fun showStartupScreensAndDialogs(preferences: SharedPreferences, skip: Int) {

        // For Android 8/8.1 we want to use software rendering by default or the Reviewer UI is broken #7369
        if (sdkVersion == Build.VERSION_CODES.O ||
            sdkVersion == Build.VERSION_CODES.O_MR1
        ) {
            if (!preferences.contains("softwareRender")) {
                Timber.i("Android 8/8.1 detected with no render preference. Turning on software render.")
                preferences.edit().putBoolean("softwareRender", true).apply()
            } else {
                Timber.i("Android 8/8.1 detected, software render preference already exists.")
            }
        }
        if (!BackupManager.enoughDiscSpace(CollectionHelper.getCurrentAnkiDroidDirectory(this))) {
            Timber.i("Not enough space to do backup")
            showDialogFragment(DeckPickerNoSpaceLeftDialog.newInstance())
        } else if (preferences.getBoolean("noSpaceLeft", false)) {
            Timber.i("No space left")
            showDialogFragment(DeckPickerBackupNoSpaceLeftDialog.newInstance())
            preferences.edit().remove("noSpaceLeft").apply()
        } else if (InitialActivity.performSetupFromFreshInstallOrClearedPreferences(preferences)) {
            onFinishedStartup()
        } else if (skip < 2 && !InitialActivity.isLatestVersion(preferences)) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")
            // The user might appreciate us now, see if they will help us get better?
            if (!preferences.contains(UsageAnalytics.ANALYTICS_OPTIN_KEY)) {
                displayAnalyticsOptInDialog()
            }

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.pkgVersionCode
            Timber.i("Current AnkiDroid version: %s", current)
            val previous: Long
            previous = if (preferences.contains(UPGRADE_VERSION_KEY)) {
                // Upgrading currently installed app
                getPreviousVersion(preferences, current)
            } else {
                // Fresh install
                current
            }
            preferences.edit().putLong(UPGRADE_VERSION_KEY, current).apply()

            // Delete the media database made by any version before 2.3 beta due to upgrade errors.
            // It is rebuilt on the next sync or media check
            if (previous < 20300200) {
                Timber.i("Deleting media database")
                val mediaDb = File(CollectionHelper.getCurrentAnkiDroidDirectory(this), "collection.media.ad.db2")
                if (mediaDb.exists()) {
                    mediaDb.delete()
                }
            }
            // Recommend the user to do a full-sync if they're upgrading from before 2.3.1beta8
            if (previous < 20301208) {
                Timber.i("Recommend the user to do a full-sync")
                mRecommendFullSync = true
            }

            // Fix "font-family" definition in templates created by AnkiDroid before 2.6alpha23
            if (previous < 20600123) {
                Timber.i("Fixing font-family definition in templates")
                try {
                    val models = getCol().models
                    for (m in models.all()) {
                        val css = m.getString("css")
                        if (css.contains("font-familiy")) {
                            m.put("css", css.replace("font-familiy", "font-family"))
                            models.save(m)
                        }
                    }
                    models.flush()
                } catch (e: JSONException) {
                    Timber.e(e, "Failed to upgrade css definitions.")
                }
            }

            // Check if preference upgrade or database check required, otherwise go to new feature screen
            val upgradeDbVersion = AnkiDroidApp.CHECK_DB_AT_VERSION

            // Specifying a checkpoint in the future is not supported, please don't do it!
            if (current < upgradeDbVersion) {
                Timber.e("Invalid value for CHECK_DB_AT_VERSION")
                showSimpleSnackbar(this, "Invalid value for CHECK_DB_AT_VERSION", false)
                onFinishedStartup()
                return
            }

            // Skip full DB check if the basic check is OK
            // TODO: remove this variable if we really want to do the full db check on every user
            val skipDbCheck = false
            // if (previous < upgradeDbVersion && getCol().basicCheck()) {
            //    skipDbCheck = true;
            // }
            val upgradedPreferences = InitialActivity.upgradePreferences(this, previous)
            // Integrity check loads asynchronously and then restart deck picker when finished
            if (!skipDbCheck && previous < upgradeDbVersion) {
                Timber.i("showStartupScreensAndDialogs() running integrityCheck()")
                // #5852 - since we may have a warning about disk space, we don't want to force a check database
                // and show a warning before the user knows what is happening.
                MaterialDialog.Builder(this)
                    .title(R.string.integrity_check_startup_title)
                    .content(R.string.integrity_check_startup_content)
                    .positiveText(R.string.check_db)
                    .negativeText(R.string.close)
                    .onPositive { _: MaterialDialog?, _: DialogAction? -> integrityCheck() }
                    .onNeutral { _: MaterialDialog?, _: DialogAction? -> restartActivity() }
                    .onNegative { _: MaterialDialog?, _: DialogAction? -> restartActivity() }
                    .canceledOnTouchOutside(false)
                    .cancelable(false)
                    .build()
                    .show()
                return
            }
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                restartActivity()
                return
            }

            // If no changes are required we go to the new features activity
            // There the "lastVersion" is set, so that this code is not reached again
            if (VersionUtils.isReleaseVersion) {
                Timber.i("Displaying new features")
                val infoIntent = Intent(this, Info::class.java)
                infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
                if (skip != 0) {
                    startActivityForResultWithAnimation(infoIntent, SHOW_INFO_NEW_VERSION, START)
                } else {
                    startActivityForResultWithoutAnimation(infoIntent, SHOW_INFO_NEW_VERSION)
                }
            } else {
                Timber.i("Dev Build - not showing 'new features'")
                // Don't show new features dialog for development builds
                InitialActivity.setUpgradedToLatestVersion(preferences)
                val ver = resources.getString(R.string.updated_version, VersionUtils.pkgVersionName)
                showSnackbar(this, ver, true, -1, null, findViewById(R.id.root_layout), null)
                showStartupScreensAndDialogs(preferences, 2)
            }
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    open fun displayDowngradeFailedNoSpace() {
        Timber.w("Not enough space to downgrade")
        val formatter = FileSizeFormatter(this)
        val collectionPath = CollectionHelper.getCollectionPath(this)
        val collectionFile = File(collectionPath)
        showDialogFragment(DeckPickerNoSpaceToDowngradeDialog.newInstance(formatter, collectionFile))
    }

    @VisibleForTesting
    protected open fun displayAnalyticsOptInDialog() {
        showDialogFragment(DeckPickerAnalyticsOptInDialog.newInstance())
    }

    fun getPreviousVersion(preferences: SharedPreferences, current: Long): Long {
        var previous: Long
        try {
            previous = preferences.getLong(UPGRADE_VERSION_KEY, current)
        } catch (e: ClassCastException) {
            Timber.w(e)
            previous = try {
                // set 20900203 to default value, as it's the latest version that stores integer in shared prefs
                preferences.getInt(UPGRADE_VERSION_KEY, 20900203).toLong()
            } catch (cce: ClassCastException) {
                Timber.w(cce)
                // Previous versions stored this as a string.
                val s = preferences.getString(UPGRADE_VERSION_KEY, "")
                // The last version of AnkiDroid that stored this as a string was 2.0.2.
                // We manually set the version here, but anything older will force a DB check.
                if ("2.0.2" == s) {
                    40
                } else {
                    0
                }
            }
            Timber.d("Updating shared preferences stored key %s type to long", UPGRADE_VERSION_KEY)
            // Expected Editor.putLong to be called later to update the value in shared prefs
            preferences.edit().remove(UPGRADE_VERSION_KEY).apply()
        }
        Timber.i("Previous AnkiDroid version: %s", previous)
        return previous
    }

    private fun undoTaskListener(isReview: Boolean): UndoTaskListener {
        return UndoTaskListener(isReview, this)
    }

    private class UndoTaskListener(private val isReview: Boolean, deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Unit, Computation<NextCard<*>>>(deckPicker) {
        override fun actualOnCancelled(context: DeckPicker) {
            context.hideProgressBar()
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            context.showProgressBar()
        }

        override fun actualOnPostExecute(context: DeckPicker, result: Computation<NextCard<*>>) {
            context.hideProgressBar()
            Timber.i("Undo completed")
            if (isReview) {
                Timber.i("Review undone - opening reviewer.")
                context.openReviewer()
            }
        }
    }

    private fun undo() {
        Timber.i("undo()")
        val undoReviewString = resources.getString(R.string.undo_action_review)
        val isReview = undoReviewString == getCol().undoName(resources)
        Undo().runWithHandler(undoTaskListener(isReview))
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(id: Int) {
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(id)
        showAsyncDialogFragment(newFragment)
    }

    override fun showMediaCheckDialog(dialogType: Int) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(dialogType))
    }

    override fun showMediaCheckDialog(dialogType: Int, checkList: List<List<String>>) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(dialogType, checkList))
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: Int) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(dialogType: Int, message: String?) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        showAsyncDialogFragment(newFragment, NotificationChannels.Channel.SYNC)
    }

    /**
     * Show simple error dialog with just the message and OK button. Reload the activity when dialog closed.
     */
    private fun showSyncErrorMessage(message: String?) {
        val title = resources.getString(R.string.sync_error)
        showSimpleMessageDialog(title, message, true)
    }

    /**
     * Show a simple snackbar message or notification if the activity is not in foreground
     * @param messageResource String resource for message
     */
    @KotlinCleanup("nullOrEmpty")
    private fun showSyncLogMessage(@StringRes messageResource: Int, syncMessage: String?) {
        if (mActivityPaused) {
            val res = AnkiDroidApp.getAppResources()
            showSimpleNotification(
                res.getString(R.string.app_name),
                res.getString(messageResource),
                NotificationChannels.Channel.SYNC
            )
        } else {
            if (syncMessage == null || syncMessage.length == 0) {
                if (messageResource == R.string.youre_offline && !Connection.getAllowLoginSyncOnNoConnection()) {
                    // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                    showSnackbar(this, messageResource, false, R.string.sync_even_if_offline, {
                        Connection.setAllowLoginSyncOnNoConnection(true)
                        sync()
                    }, null)
                } else {
                    showSimpleSnackbar(this, messageResource, false)
                }
            } else {
                val res = AnkiDroidApp.getAppResources()
                showSimpleMessageDialog(res.getString(messageResource), syncMessage, false)
            }
        }
    }

    @KotlinCleanup("?:")
    fun showImportDialog(id: Int, message: String?) {
        var newMessage = message
        Timber.d("showImportDialog() delegating to ImportDialog")
        if (newMessage == null) {
            newMessage = ""
        }
        val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, newMessage)
        showAsyncDialogFragment(newFragment)
    }

    fun onSdCardNotMounted() {
        showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finishWithoutAnimation()
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        AnkiDroidApp.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    private fun repairCollectionTask(): RepairCollectionTask {
        return RepairCollectionTask(this)
    }

    private class RepairCollectionTask(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, Boolean>(deckPicker) {
        override fun actualOnPreExecute(context: DeckPicker) {
            context.mProgressDialog = StyledProgressDialog.show(
                context, null,
                context.resources.getString(R.string.backup_repair_deck_progress), false
            )
        }

        override fun actualOnPostExecute(context: DeckPicker, result: Boolean) {
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            if (!result) {
                showThemedToast(context, context.resources.getString(R.string.deck_repair_error), true)
                context.showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        TaskManager.launchCollectionTask(RepairCollection(), repairCollectionTask())
    }

    // Callback method to handle database integrity check
    @KotlinCleanup("remove _ parameters")
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            MaterialDialog.Builder(this)
                .title(R.string.check_db_title)
                .content(status.getWarningDetails(this))
                .positiveText(R.string.integrity_check_continue_anyway)
                .onPositive { _: MaterialDialog?, _: DialogAction? -> performIntegrityCheck() }
                .negativeText(R.string.dialog_cancel)
                .show()
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        TaskManager.launchCollectionTask(CheckDatabase(), CheckDatabaseListener())
    }

    private fun mediaCheckListener(): MediaCheckListener {
        return MediaCheckListener(this)
    }

    private class MediaCheckListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, Computation<List<List<String>>>>(deckPicker) {
        override fun actualOnPreExecute(context: DeckPicker) {
            context.mProgressDialog = StyledProgressDialog.show(
                context, null,
                context.resources.getString(R.string.check_media_message), false
            )
        }

        override fun actualOnPostExecute(context: DeckPicker, result: Computation<List<List<String>>>) {
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            if (result.succeeded()) {
                val checkList = result.value
                context.showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, checkList)
            } else {
                context.showSimpleMessageDialog(context.resources.getString(R.string.check_media_failed))
            }
        }
    }

    override fun mediaCheck() {
        TaskManager.launchCollectionTask(CheckMedia(), mediaCheckListener())
    }

    private fun mediaDeleteListener(): MediaDeleteListener {
        return MediaDeleteListener(this)
    }

    private class MediaDeleteListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, Int>(deckPicker) {
        override fun actualOnPreExecute(context: DeckPicker) {
            context.mProgressDialog = StyledProgressDialog.show(
                context, null,
                context.resources.getString(R.string.delete_media_message), false
            )
        }

        /**
         * @param result Number of deleted files
         */
        override fun actualOnPostExecute(context: DeckPicker, result: Int) {
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            context.showSimpleMessageDialog(
                context.resources.getString(R.string.delete_media_result_title),
                context.resources.getQuantityString(R.plurals.delete_media_result_message, result, result)
            )
        }
    }

    override fun deleteUnused(unused: List<String?>?) {
        TaskManager.launchCollectionTask(DeleteMedia(unused), mediaDeleteListener())
    }

    fun exit() {
        CollectionHelper.getInstance().closeCollection(false, "DeckPicker:exit()")
        finishWithoutAnimation()
    }

    open fun handleDbError() {
        Timber.i("Displaying Database Error")
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED)
    }

    open fun handleDbLocked() {
        Timber.i("Displaying Database Locked")
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_DB_LOCKED)
    }

    fun restoreFromBackup(path: String?) {
        importReplace(path)
    }

    // Helper function to check if there are any saved stacktraces
    fun hasErrorFiles(): Boolean {
        for (file in fileList()) {
            if (file.endsWith(".stacktrace")) {
                return true
            }
        }
        return false
    }

    // Sync with Anki Web
    override fun sync() {
        sync(null)
    }

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        val hkey = preferences.getString("hkey", "")
        if (hkey!!.length == 0) {
            Timber.w("User not logged in")
            mPullToSyncWrapper!!.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
        } else {
            Connection.sync(
                mSyncListener,
                Connection.Payload(
                    arrayOf(
                        hkey,
                        preferences.getBoolean("syncFetchesMedia", true),
                        conflict,
                        HostNumFactory.getInstance(baseContext)
                    )
                )
            )
        }
    }

    private val mSyncListener: Connection.TaskListener = object : CancellableTaskListener {
        private var mCurrentMessage: String? = null
        private var mCountUp: Long = 0
        private var mCountDown: Long = 0
        private var mDialogDisplayFailure = false
        override fun onDisconnected() {
            showSyncLogMessage(R.string.youre_offline, "")
        }

        override fun onCancelled() {
            showSyncLogMessage(R.string.sync_cancelled, "")
            if (!mDialogDisplayFailure) {
                mProgressDialog!!.dismiss()
                // update deck list in case sync was cancelled during media sync and main sync was actually successful
                updateDeckList()
            }
            // reset our display failure fate, just in case it is re-used
            mDialogDisplayFailure = false
        }

        @KotlinCleanup("remove some _ parameters")
        override fun onPreExecute() {
            mCountUp = 0
            mCountDown = 0
            val syncStartTime = getCol().time.intTimeMS()
            if (mProgressDialog == null || !mProgressDialog!!.isShowing) {
                try {
                    mProgressDialog = StyledProgressDialog.show(
                        this@DeckPicker, resources.getString(R.string.sync_title),
                        """
                                ${resources.getString(R.string.sync_title)}
                                ${resources.getString(R.string.sync_up_down_size, mCountUp, mCountDown)}
                        """.trimIndent(),
                        false
                    )
                } catch (e: BadTokenException) {
                    // If we could not show the progress dialog to start even, bail out - user will get a message
                    Timber.w(e, "Unable to display Sync progress dialog, Activity not valid?")
                    mDialogDisplayFailure = true
                    Connection.cancel()
                    return
                }

                // Override the back key so that the user can cancel a sync which is in progress
                mProgressDialog!!.setOnKeyListener { _: DialogInterface?, keyCode: Int, event: KeyEvent ->
                    // Make sure our method doesn't get called twice
                    if (event.action != KeyEvent.ACTION_DOWN) {
                        return@setOnKeyListener true
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK && Connection.isCancellable() &&
                        !Connection.getIsCancelled()
                    ) {
                        // If less than 2s has elapsed since sync started then don't ask for confirmation
                        if (getCol().time.intTimeMS() - syncStartTime < 2000) {
                            Connection.cancel()
                            mProgressDialog!!.setContent(R.string.sync_cancel_message)
                            return@setOnKeyListener true
                        }
                        // Show confirmation dialog to check if the user wants to cancel the sync
                        val builder = MaterialDialog.Builder(mProgressDialog!!.context)
                        builder.content(R.string.cancel_sync_confirm)
                            .cancelable(false)
                            .positiveText(R.string.dialog_ok)
                            .negativeText(R.string.continue_sync)
                            .onPositive { _: MaterialDialog?, _: DialogAction? ->
                                mProgressDialog!!.setContent(R.string.sync_cancel_message)
                                Connection.cancel()
                            }
                        builder.show()
                        return@setOnKeyListener true
                    } else {
                        return@setOnKeyListener false
                    }
                }
            }

            // Store the current time so that we don't bother the user with a sync prompt for another 10 minutes
            // Note: getLs() in Libanki doesn't take into account the case when no changes were found, or sync cancelled
            val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
            preferences.edit().putLong("lastSyncTime", syncStartTime).apply()
        }

        override fun onProgressUpdate(vararg values: Any) {
            val res = resources
            if (values[0] is Int) {
                val id = values[0] as Int
                if (id != 0) {
                    mCurrentMessage = res.getString(id)
                }
                if (values.size >= 3) {
                    mCountUp = values[1] as Long
                    mCountDown = values[2] as Long
                }
            } else if (values[0] is String) {
                mCurrentMessage = values[0] as String
                if (values.size >= 3) {
                    mCountUp = values[1] as Long
                    mCountDown = values[2] as Long
                }
            }
            if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                mProgressDialog!!.setContent(
                    """
    $mCurrentMessage
    
                    """.trimIndent() +
                        res
                            .getString(R.string.sync_up_down_size, mCountUp / 1024, mCountDown / 1024)
                )
            }
        }

        override fun onPostExecute(data: Connection.Payload) {
            mPullToSyncWrapper!!.isRefreshing = false
            var dialogMessage: String? = ""
            Timber.d("Sync Listener onPostExecute()")
            val res = resources
            try {
                if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                    mProgressDialog!!.dismiss()
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not dismiss mProgressDialog. The Activity must have been destroyed while the AsyncTask was running")
                AnkiDroidApp.sendExceptionReport(e, "DeckPicker.onPostExecute", "Could not dismiss mProgressDialog")
            }
            val syncMessage = data.message
            Timber.i("Sync Listener: onPostExecute: Data: %s", data.toString())
            if (!data.success) {
                val result = data.result
                val resultType = data.resultType
                if (resultType != null) {
                    when (resultType) {
                        ConnectionResultType.BAD_AUTH -> {
                            // delete old auth information
                            val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
                            val editor = preferences.edit()
                            editor.putString("username", "")
                            editor.putString("hkey", "")
                            editor.apply()
                            // then show not logged in dialog
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
                        }
                        ConnectionResultType.NO_CHANGES -> {
                            SyncStatus.markSyncCompleted()
                            // show no changes message, use false flag so we don't show "sync error" as the Dialog title
                            showSyncLogMessage(R.string.sync_no_changes_message, "")
                        }
                        ConnectionResultType.CLOCK_OFF -> {
                            val diff = result[0] as Long
                            dialogMessage = if (diff >= 86100) {
                                // The difference if more than a day minus 5 minutes acceptable by ankiweb error
                                res.getString(
                                    R.string.sync_log_clocks_unsynchronized, diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_date)
                                )
                            } else if (Math.abs(diff % 3600.0 - 1800.0) >= 1500.0) {
                                // The difference would be within limit if we adjusted the time by few hours
                                // It doesn't work for all timezones, but it covers most and it's a guess anyway
                                res.getString(
                                    R.string.sync_log_clocks_unsynchronized, diff,
                                    res.getString(R.string.sync_log_clocks_unsynchronized_tz)
                                )
                            } else {
                                res.getString(R.string.sync_log_clocks_unsynchronized, diff, "")
                            }
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.FULL_SYNC -> if (getCol().isEmpty) {
                            // don't prompt user to resolve sync conflict if local collection empty
                            sync(ConflictResolution.FULL_DOWNLOAD)
                            // TODO: Also do reverse check to see if AnkiWeb collection is empty if Anki Desktop
                            // implements it
                        } else {
                            // If can't be resolved then automatically then show conflict resolution dialog
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION)
                        }
                        ConnectionResultType.BASIC_CHECK_FAILED -> {
                            dialogMessage = res.getString(R.string.sync_basic_check_failed, res.getString(R.string.check_db))
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_BASIC_CHECK_ERROR, joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.DB_ERROR -> showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CORRUPT_COLLECTION, syncMessage)
                        ConnectionResultType.OVERWRITE_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_overwrite_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.REMOTE_DB_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_remote_db_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.SD_ACCESS_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_write_access_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.FINISH_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_log_finish_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.CONNECTION_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_connection_error)
                            if (result.size >= 0 && result[0] is Exception) {
                                dialogMessage += """
                                    
                                    
                                    ${(result[0] as Exception).localizedMessage}
                                """.trimIndent()
                            }
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.IO_EXCEPTION -> handleDbError()
                        ConnectionResultType.GENERIC_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_generic_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.OUT_OF_MEMORY_ERROR -> {
                            dialogMessage = res.getString(R.string.error_insufficient_memory)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.SANITY_CHECK_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_sanity_failed)
                            showSyncErrorDialog(
                                SyncErrorDialog.DIALOG_SYNC_SANITY_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage)
                            )
                        }
                        ConnectionResultType.SERVER_ABORT -> // syncMsg has already been set above, no need to fetch it here.
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        ConnectionResultType.MEDIA_SYNC_SERVER_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_media_error_check)
                            showSyncErrorDialog(
                                SyncErrorDialog.DIALOG_MEDIA_SYNC_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage)
                            )
                        }
                        ConnectionResultType.CUSTOM_SYNC_SERVER_URL -> {
                            val url = if (result.size > 0 && result[0] is CustomSyncServerUrlException) (result[0] as CustomSyncServerUrlException).url else "unknown"
                            dialogMessage = res.getString(R.string.sync_error_invalid_sync_server, url)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        else -> {
                            if (result.size > 0 && result[0] is Int) {
                                val code = result[0] as Int
                                dialogMessage = rewriteError(code)
                                if (dialogMessage == null) {
                                    dialogMessage = res.getString(
                                        R.string.sync_log_error_specific,
                                        Integer.toString(code), result[1]
                                    )
                                }
                            } else {
                                dialogMessage = res.getString(R.string.sync_log_error_specific, Integer.toString(-1), resultType)
                            }
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                    }
                } else {
                    dialogMessage = res.getString(R.string.sync_generic_error)
                    showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                }
            } else {
                Timber.i("Sync was successful")
                if (data.data[2] != null && "" != data.data[2]) {
                    Timber.i("Syncing had additional information")
                    // There was a media error, so show it
                    // Note: Do not log this data. May contain user email.
                    val message = """
                        ${res.getString(R.string.sync_database_acknowledge)}
                        
                        ${data.data[2]}
                    """.trimIndent()
                    showSimpleMessageDialog(message)
                } else if (data.data.size > 0 && data.data[0] is ConflictResolution) {
                    // A full sync occurred
                    val dataString = data.data[0] as ConflictResolution
                    when (dataString) {
                        ConflictResolution.FULL_UPLOAD -> {
                            Timber.i("Full Upload Completed")
                            showSyncLogMessage(R.string.sync_log_uploading_message, syncMessage)
                        }
                        ConflictResolution.FULL_DOWNLOAD -> {
                            Timber.i("Full Download Completed")
                            showSyncLogMessage(R.string.backup_full_sync_from_server, syncMessage)
                        }
                    }
                } else {
                    Timber.i("Regular sync completed successfully")
                    showSyncLogMessage(R.string.sync_database_acknowledge, syncMessage)
                }
                // Mark sync as completed - then refresh the sync icon
                SyncStatus.markSyncCompleted()
                @Suppress("deprecation")
                supportInvalidateOptionsMenu()
                updateDeckList()
                WidgetStatus.update(this@DeckPicker)
                if (mFragmented) {
                    try {
                        loadStudyOptionsFragment(false)
                    } catch (e: IllegalStateException) {
                        // Activity was stopped or destroyed when the sync finished. Losing the
                        // fragment here is fine since we build a fresh fragment on resume anyway.
                        Timber.w(e, "Failed to load StudyOptionsFragment after sync.")
                    }
                }
            }
        }
    }

    @VisibleForTesting
    fun rewriteError(code: Int): String? {
        val msg: String?
        val res = resources
        msg = when (code) {
            407 -> res.getString(R.string.sync_error_407_proxy_required)
            409 -> res.getString(R.string.sync_error_409)
            413 -> res.getString(R.string.sync_error_413_collection_size)
            500 -> res.getString(R.string.sync_error_500_unknown)
            501 -> res.getString(R.string.sync_error_501_upgrade_required)
            502 -> res.getString(R.string.sync_error_502_maintenance)
            503 -> res.getString(R.string.sync_too_busy)
            504 -> res.getString(R.string.sync_error_504_gateway_timeout)
            else -> null
        }
        return msg
    }

    override fun loginToSyncServer() {
        val myAccount = Intent(this, MyAccount::class.java)
        myAccount.putExtra("notLoggedIn", true)
        startActivityForResultWithAnimation(myAccount, LOG_IN_FOR_SYNC, FADE)
    }

    // Callback to import a file -- adding it to existing collection
    override fun importAdd(importPath: String?) {
        Timber.d("importAdd() for file %s", importPath)
        TaskManager.launchCollectionTask(ImportAdd(importPath), mImportAddListener)
    }

    // Callback to import a file -- replacing the existing collection
    override fun importReplace(importPath: String?) {
        TaskManager.launchCollectionTask(ImportReplace(importPath), importReplaceListener())
    }

    /**
     * Load a new studyOptionsFragment. If withDeckOptions is true, the deck options activity will
     * be loaded on top of it. Use this flag when creating a new filtered deck to allow the user to
     * modify the filter settings before being shown the fragment. The fragment itself will handle
     * rebuilding the deck if the settings change.
     */
    private fun loadStudyOptionsFragment(withDeckOptions: Boolean) {
        val details = StudyOptionsFragment.newInstance(withDeckOptions)
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(R.id.studyoptions_fragment, details)
        ft.commit()
    }

    val fragment: StudyOptionsFragment?
        get() {
            val frag = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)
            return if (frag is StudyOptionsFragment) {
                frag
            } else null
        }

    /**
     * Show a message when the SD card is ejected
     */
    private fun registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                        onSdCardNotMounted()
                    } else if (intent.action == SdCardReceiver.MEDIA_MOUNT) {
                        restartActivity()
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            iFilter.addAction(SdCardReceiver.MEDIA_MOUNT)
            registerReceiver(mUnmountReceiver, iFilter)
        }
    }

    fun openAnkiWebSharedDecks() {
        val intent = Intent(this, SharedDecksActivity::class.java)
        startActivityWithoutAnimation(intent)
    }

    private fun openStudyOptions(withDeckOptions: Boolean) {
        if (mFragmented) {
            // The fragment will show the study options screen instead of launching a new activity.
            loadStudyOptionsFragment(withDeckOptions)
        } else {
            val intent = Intent()
            intent.putExtra("withDeckOptions", withDeckOptions)
            intent.setClass(this, StudyOptionsActivity::class.java)
            startActivityForResultWithAnimation(intent, SHOW_STUDYOPTIONS, START)
        }
    }

    private fun openReviewerOrStudyOptions(selectionType: DeckSelectionType) {
        when (selectionType) {
            DeckSelectionType.DEFAULT -> {
                if (mFragmented) {
                    openStudyOptions(false)
                } else {
                    openReviewer()
                }
                return
            }
            DeckSelectionType.SHOW_STUDY_OPTIONS -> {
                openStudyOptions(false)
                return
            }
            DeckSelectionType.SKIP_STUDY_OPTIONS -> {
                openReviewer()
                return
            }
        }
    }

    private fun handleDeckSelection(did: Long, selectionType: DeckSelectionType) {
        // Clear the undo history when selecting a new deck
        if (getCol().decks.selected() != did) {
            getCol().clearUndo()
        }
        // Select the deck
        getCol().decks.select(did)
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        // Reset the schedule so that we get the counts for the currently selected deck
        mFocusedDeck = did
        // Get some info about the deck to handle special cases
        val deckDueTreeNode = mDeckListAdapter!!.getNodeByDid(did)
        if (!deckDueTreeNode.value.shouldDisplayCounts() || deckDueTreeNode.value.knownToHaveRep()) {
            // If we don't yet have numbers, we trust the user that they knows what they opens, tries to open it.
            // If there is nothing to review, it'll come back to deck picker.
            openReviewerOrStudyOptions(selectionType)
            return
        }
        // There are numbers
        // Figure out what action to take
        if (getCol().sched.hasCardsTodayAfterStudyAheadLimit()) {
            // If there are cards due that can't be studied yet (due to the learn ahead limit) then go to study options
            openStudyOptions(false)
        } else if (getCol().sched.newDue() || getCol().sched.revDue()) {
            // If there are no cards to review because of the daily study limit then give "Study more" option
            showSnackbar(this, R.string.studyoptions_limit_reached, false, R.string.study_more, {
                val d = mCustomStudyDialogFactory!!.newCustomStudyDialog().withArguments(
                    CustomStudyDialog.ContextMenuConfiguration.LIMITS,
                    getCol().decks.selected(), true
                )
                showDialogFragment(d)
            }, findViewById(R.id.root_layout), mSnackbarShowHideCallback)
            // Check if we need to update the fragment or update the deck list. The same checks
            // are required for all snackbars below.
            if (mFragmented) {
                // Tablets must always show the study options that corresponds to the current deck,
                // regardless of whether the deck is currently reviewable or not.
                openStudyOptions(false)
            } else {
                // On phones, we update the deck list to ensure the currently selected deck is
                // highlighted correctly.
                updateDeckList()
            }
        } else if (getCol().decks.isDyn(did)) {
            // Go to the study options screen if filtered deck with no cards to study
            openStudyOptions(false)
        } else if (!deckDueTreeNode.hasChildren() && getCol().isEmptyDeck(did)) {
            // If the deck is empty and has no children then show a message saying it's empty
            showSnackbar(
                this, R.string.empty_deck, false, R.string.empty_deck_add_note,
                { addNote() }, findViewById(R.id.root_layout), mSnackbarShowHideCallback
            )
            if (mFragmented) {
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        } else {
            // Otherwise say there are no cards scheduled to study, and give option to do custom study
            showSnackbar(this, R.string.studyoptions_empty_schedule, false, R.string.custom_study, {
                val d = mCustomStudyDialogFactory!!.newCustomStudyDialog().withArguments(
                    CustomStudyDialog.ContextMenuConfiguration.EMPTY_SCHEDULE,
                    getCol().decks.selected(), true
                )
                showDialogFragment(d)
            }, findViewById(R.id.root_layout), mSnackbarShowHideCallback)
            if (mFragmented) {
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        }
    }

    private fun openHelpUrl(helpUrl: Uri) {
        openUrl(helpUrl)
    }

    /**
     * Scroll the deck list so that it is centered on the current deck.
     *
     * @param did The deck ID of the deck to select.
     */
    private fun scrollDecklistToDeck(did: Long) {
        val position = mDeckListAdapter!!.findDeckPosition(did)
        mRecyclerViewLayoutManager!!.scrollToPositionWithOffset(position, mRecyclerView!!.height / 2)
    }

    private fun <T : AbstractDeckTreeNode> updateDeckListListener(): UpdateDeckListListener<T> {
        return UpdateDeckListListener(this)
    }

    private class UpdateDeckListListener<T : AbstractDeckTreeNode>(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, List<TreeNode<T>>?>(deckPicker) {
        override fun actualOnPreExecute(context: DeckPicker) {
            if (!context.colIsOpen()) {
                context.showProgressBar()
            }
            Timber.d("Refreshing deck list")
        }

        override fun actualOnPostExecute(context: DeckPicker, result: List<TreeNode<T>>?) {
            Timber.i("Updating deck list UI")
            context.hideProgressBar()
            // Make sure the fragment is visible
            if (context.mFragmented) {
                context.mStudyoptionsFrame!!.visibility = View.VISIBLE
            }
            if (result == null) {
                Timber.e("null result loading deck counts")
                context.showCollectionErrorDialog()
                return
            }
            context.mDueTree = result.map { x -> x.unsafeCastToType(AbstractDeckTreeNode::class.java) }
            context.__renderPage()
            // Update the mini statistics bar as well
            AnkiStatsTaskHandler.createReviewSummaryStatistics(context.getCol(), context.mReviewSummaryTextView!!)
            Timber.d("Startup - Deck List UI Completed")
        }
    }

    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, importing, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    internal fun updateDeckList() {
        updateDeckList(false)
    }

    private fun updateDeckList(quick: Boolean) {
        if (quick) {
            TaskManager.launchCollectionTask(LoadDeck(), updateDeckListListener())
        } else {
            TaskManager.launchCollectionTask(LoadDeckCounts(), updateDeckListListener<DeckDueTreeNode>())
        }
    }

    fun __renderPage() {
        if (mDueTree == null) {
            // mDueTree may be set back to null when the activity restart.
            // We may need to recompute it.
            updateDeckList()
            return
        }

        // Check if default deck is the only available and there are no cards
        val isEmpty = mDueTree!!.size == 1 && mDueTree!![0].value.did == 1L && getCol().isEmpty
        if (animationDisabled()) {
            mDeckPickerContent!!.visibility = if (isEmpty) View.GONE else View.VISIBLE
            mNoDecksPlaceholder!!.visibility = if (isEmpty) View.VISIBLE else View.GONE
        } else {
            val translation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f,
                resources.displayMetrics
            )
            val decksListShown = mDeckPickerContent!!.visibility == View.VISIBLE
            val placeholderShown = mNoDecksPlaceholder!!.visibility == View.VISIBLE
            if (isEmpty) {
                if (decksListShown) {
                    fadeOut(mDeckPickerContent, mShortAnimDuration, translation)
                }
                if (!placeholderShown) {
                    fadeIn(mNoDecksPlaceholder, mShortAnimDuration, translation) // This is some bad choreographing here
                        .setStartDelay(if (decksListShown) mShortAnimDuration * 2.toLong() else 0.toLong())
                }
            } else {
                if (!decksListShown) {
                    fadeIn(mDeckPickerContent, mShortAnimDuration, translation)
                        .setStartDelay(if (placeholderShown) mShortAnimDuration * 2.toLong() else 0.toLong())
                }
                if (placeholderShown) {
                    fadeOut(mNoDecksPlaceholder, mShortAnimDuration, translation)
                }
            }
        }
        val currentFilter = if (mToolbarSearchView != null) mToolbarSearchView!!.query else null

        if (isEmpty) {
            if (supportActionBar != null) {
                supportActionBar!!.setSubtitle(null)
            }
            if (mToolbarSearchView != null) {
                mDeckListAdapter!!.filter.filter(currentFilter)
            }
            // We're done here
            return
        }
        mDeckListAdapter!!.buildDeckList(mDueTree!!, getCol(), currentFilter)

        // Set the "x due in y minutes" subtitle
        try {
            val eta = mDeckListAdapter!!.eta
            val due = mDeckListAdapter!!.due
            val res = resources
            if (getCol().cardCount() != -1) {
                var time: String? = "-"
                if (eta != -1 && eta != null) {
                    time = Utils.timeQuantityTopDeckPicker(this, (eta * 60).toLong())
                }
                if (due != null && supportActionBar != null) {
                    val subTitle: String
                    subTitle = if (due == 0) {
                        res.getQuantityString(R.plurals.deckpicker_title_zero_due, getCol().cardCount(), getCol().cardCount())
                    } else {
                        res.getQuantityString(R.plurals.deckpicker_title, due, due, time)
                    }
                    supportActionBar!!.setSubtitle(subTitle)
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "RuntimeException setting time remaining")
        }
        val current = getCol().decks.current().optLong("id")
        if (mFocusedDeck != current) {
            scrollDecklistToDeck(current)
            mFocusedDeck = current
        }
    }

    // Callback to show study options for currently selected deck
    fun showContextMenuDeckOptions(did: Long) {
        // open deck options
        if (getCol().decks.isDyn(did)) {
            // open cram options if filtered deck
            val i = Intent(this@DeckPicker, FilteredDeckOptions::class.java)
            i.putExtra("did", did)
            startActivityWithAnimation(i, FADE)
        } else {
            // otherwise open regular options
            val i = Intent(this@DeckPicker, DeckOptions::class.java)
            i.putExtra("did", did)
            startActivityWithAnimation(i, FADE)
        }
    }

    fun exportDeck(did: Long) {
        val msg = resources.getString(R.string.confirm_apkg_export_deck, getCol().decks.get(did).getString("name"))
        mExportingDelegate!!.showExportDialog(msg, did)
    }

    fun createIcon(context: Context?, did: Long) {
        // This code should not be reachable with lower versions
        val shortcut = ShortcutInfoCompat.Builder(this, java.lang.Long.toString(did))
            .setIntent(
                Intent(context, Reviewer::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra("deckId", did)
            )
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setShortLabel(Decks.basename(getCol().decks.name(did)))
            .setLongLabel(getCol().decks.name(did))
            .build()
        try {
            val success = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)

            // User report: "success" is true even if Vivo does not have permission
            if (AdaptionUtil.isVivo) {
                showThemedToast(this, getString(R.string.create_shortcut_error_vivo), false)
            }
            if (!success) {
                showThemedToast(this, getString(R.string.create_shortcut_failed), false)
            }
        } catch (e: Exception) {
            Timber.w(e)
            showThemedToast(this, getString(R.string.create_shortcut_error, e.localizedMessage), false)
        }
    }

    fun renameDeckDialog(did: Long) {
        val currentName = getCol().decks.name(did)
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.rename_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
        createDeckDialog.deckName = currentName
        createDeckDialog.setOnNewDeckCreated {
            dismissAllDialogFragments()
            mDeckListAdapter!!.notifyDataSetChanged()
            updateDeckList()
            if (mFragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    fun confirmDeckDeletion(did: Long) {
        val res = resources
        if (!colIsOpen()) {
            return
        }
        if (did == 1L) {
            showSimpleSnackbar(this, R.string.delete_deck_default_deck, true)
            dismissAllDialogFragments()
            return
        }
        // Get the number of cards contained in this deck and its subdecks
        val cnt = DeckService.countCardsInDeckTree(getCol(), did)
        val isDyn = getCol().decks.isDyn(did)
        // Delete empty decks without warning. Filtered decks save filters in the deck data, so require confirmation.
        if (cnt == 0 && !isDyn) {
            deleteDeck(did)
            dismissAllDialogFragments()
            return
        }
        // Otherwise we show a warning and require confirmation
        val msg: String
        val deckName = "'" + getCol().decks.name(did) + "'"
        msg = if (isDyn) {
            res.getString(R.string.delete_cram_deck_message, deckName)
        } else {
            res.getQuantityString(R.plurals.delete_deck_message, cnt, deckName, cnt)
        }
        showDialogFragment(DeckPickerConfirmDeleteDeckDialog.newInstance(msg, did))
    }

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     * Use [.confirmDeckDeletion] for a confirmation dialog
     * @param did the deck to delete
     */
    fun deleteDeck(did: Long) {
        TaskManager.launchCollectionTask(DeleteDeck(did), deleteDeckListener(did))
    }

    private fun deleteDeckListener(did: Long): DeleteDeckListener {
        return DeleteDeckListener(did, this)
    }

    private class DeleteDeckListener(private val did: Long, deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, IntArray?>(deckPicker) {
        // Flag to indicate if the deck being deleted is the current deck.
        private var mRemovingCurrent = false
        override fun actualOnPreExecute(context: DeckPicker) {
            context.mProgressDialog = StyledProgressDialog.show(
                context, null,
                context.resources.getString(R.string.delete_deck), false
            )
            if (did == context.getCol().decks.current().optLong("id")) {
                mRemovingCurrent = true
            }
        }

        override fun actualOnPostExecute(context: DeckPicker, result: IntArray?) {
            // After deleting a deck there is no more undo stack
            // Rebuild options menu with side effect of resetting undo button state
            context.invalidateOptionsMenu()

            // In fragmented mode, if the deleted deck was the current deck, we need to reload
            // the study options fragment with a valid deck and re-center the deck list to the
            // new current deck. Otherwise we just update the list normally.
            if (context.mFragmented && mRemovingCurrent) {
                context.updateDeckList()
                context.openStudyOptions(false)
            } else {
                context.updateDeckList()
            }
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                try {
                    context.mProgressDialog!!.dismiss()
                } catch (e: Exception) {
                    Timber.e(e, "onPostExecute - Exception dismissing dialog")
                }
            }
        }
    }

    /**
     * Show progress bars and rebuild deck list on completion
     */
    private fun simpleProgressListener(): SimpleProgressListener {
        return SimpleProgressListener(this)
    }

    private class SimpleProgressListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker, Void, DeckStudyData>(deckPicker) {
        override fun actualOnPreExecute(context: DeckPicker) {
            context.showProgressBar()
        }

        override fun actualOnPostExecute(context: DeckPicker, result: DeckStudyData) {
            context.updateDeckList()
            if (context.mFragmented) {
                context.loadStudyOptionsFragment(false)
            }
        }
    }

    fun rebuildFiltered(did: Long) {
        getCol().decks.select(did)
        TaskManager.launchCollectionTask(RebuildCram(), simpleProgressListener())
    }

    fun emptyFiltered(did: Long) {
        getCol().decks.select(did)
        TaskManager.launchCollectionTask(EmptyCram(), simpleProgressListener())
    }

    override fun onAttachedToWindow() {
        if (!mFragmented) {
            val window = window
            window.setFormat(PixelFormat.RGBA_8888)
        }
    }

    override fun onRequireDeckListUpdate() {
        updateDeckList()
    }

    private fun openReviewer() {
        val reviewer = Intent(this, Reviewer::class.java)
        startActivityForResultWithAnimation(reviewer, REQUEST_REVIEW, START)
    }

    override fun onCreateCustomStudySession() {
        updateDeckList()
        openStudyOptions(false)
    }

    override fun onExtendStudyLimits() {
        if (mFragmented) {
            fragment!!.refreshInterface(true)
        }
        updateDeckList()
    }

    fun handleEmptyCards() {
        mEmptyCardTask = TaskManager.launchCollectionTask(FindEmptyCards(), handlerEmptyCardListener())
    }

    private fun handlerEmptyCardListener(): HandleEmptyCardListener {
        return HandleEmptyCardListener(this)
    }

    private class HandleEmptyCardListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, Int, List<Long?>?>(deckPicker) {
        private val mNumberOfCards: Int
        private val mOnePercent: Int
        private var mIncreaseSinceLastUpdate = 0
        @KotlinCleanup("remove _ parameters")
        private fun confirmCancel(deckPicker: DeckPicker, task: Cancellable) {
            MaterialDialog.Builder(deckPicker)
                .content(R.string.confirm_cancel)
                .positiveText(deckPicker.resources.getString(R.string.yes))
                .negativeText(deckPicker.resources.getString(R.string.dialog_no))
                .onNegative { _: MaterialDialog?, _: DialogAction? -> actualOnPreExecute(deckPicker) }
                .onPositive { _: MaterialDialog?, _: DialogAction? -> task.safeCancel() }.show()
        }

        @KotlinCleanup("scope function")
        @KotlinCleanup("remove _ parameters")
        override fun actualOnPreExecute(context: DeckPicker) {
            val onCancel = DialogInterface.OnCancelListener { _: DialogInterface? ->
                val emptyCardTask = context.mEmptyCardTask
                emptyCardTask?.let { confirmCancel(context, it) }
            }
            context.mProgressDialog = MaterialDialog.Builder(context)
                .progress(false, mNumberOfCards)
                .title(R.string.emtpy_cards_finding)
                .cancelable(true)
                .show()
            context.mProgressDialog!!.setOnCancelListener(onCancel)
            context.mProgressDialog!!.setCanceledOnTouchOutside(false)
        }

        override fun actualOnProgressUpdate(context: DeckPicker, value: Int) {
            mIncreaseSinceLastUpdate += value
            // Increase each time at least a percent of card has been processed since last update
            if (mIncreaseSinceLastUpdate > mOnePercent) {
                context.mProgressDialog!!.incrementProgress(mIncreaseSinceLastUpdate)
                mIncreaseSinceLastUpdate = 0
            }
        }

        override fun actualOnCancelled(context: DeckPicker) {
            context.mEmptyCardTask = null
        }

        /**
         * @param context
         * @param result Null if it is cancelled (in this case we should not have called this method) or a list of cids
         */
        override fun actualOnPostExecute(context: DeckPicker, result: List<Long?>?) {
            context.mEmptyCardTask = null
            if (result == null) {
                return
            }
            if (result.isEmpty()) {
                context.showSimpleMessageDialog(context.resources.getString(R.string.empty_cards_none))
            } else {
                val msg = String.format(context.resources.getString(R.string.empty_cards_count), result.size)
                val dialog = ConfirmationDialog()
                dialog.setArgs(msg)
                val confirm = Runnable {
                    context.getCol().remCards(result)
                    showSimpleSnackbar(
                        context,
                        String.format(
                            context.resources.getString(R.string.empty_cards_deleted), result.size
                        ),
                        false
                    )
                }
                dialog.setConfirm(confirm)
                context.showDialogFragment(dialog)
            }
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
        }

        init {
            mNumberOfCards = deckPicker.getCol().cardCount()
            mOnePercent = mNumberOfCards / 100
        }
    }

    fun createSubDeckDialog(did: Long) {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, did)
        createDeckDialog.setOnNewDeckCreated {
            // a deck was created
            dismissAllDialogFragments()
            mDeckListAdapter!!.notifyDataSetChanged()
            updateDeckList()
            if (mFragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    @get:VisibleForTesting
    val deckCount: Int
        get() = mDeckListAdapter!!.itemCount

    @VisibleForTesting
    internal inner class CheckDatabaseListener : TaskListener<String, Pair<Boolean, CheckDatabaseResult?>>() {
        override fun onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(
                this@DeckPicker, AnkiDroidApp.getAppResources().getString(R.string.app_name),
                resources.getString(R.string.check_db_message), false
            )
        }

        override fun onPostExecute(result: Pair<Boolean, CheckDatabaseResult?>) {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                mProgressDialog!!.dismiss()
            }
            val databaseResult = result.second
            if (databaseResult == null) {
                if (result.first) {
                    Timber.w("Expected result data, got nothing")
                } else {
                    handleDbError()
                }
                return
            }
            if (!result.first || databaseResult.failed) {
                if (databaseResult.databaseLocked) {
                    handleDbLocked()
                } else {
                    handleDbError()
                }
                return
            }
            val count = databaseResult.cardsWithFixedHomeDeckCount
            if (count != 0) {
                val message = resources.getString(R.string.integrity_check_fixed_no_home_deck, count)
                showThemedToast(this@DeckPicker, message, false)
            }
            val msg: String
            val shrunkInMb = Math.round(databaseResult.sizeChangeInKb / 1024.0)
            msg = if (shrunkInMb > 0.0) {
                resources.getString(R.string.check_db_acknowledge_shrunk, shrunkInMb.toInt())
            } else {
                resources.getString(R.string.check_db_acknowledge)
            }
            // Show result of database check and restart the app
            showSimpleMessageDialog(msg, true)
        }

        /**
         * @param value message
         */
        override fun onProgressUpdate(value: String) {
            mProgressDialog!!.setContent(value)
        }
    }

    /**
     * Check if at least one deck is being displayed.
     */
    fun hasAtLeastOneDeckBeingDisplayed(): Boolean {
        return mDeckListAdapter!!.itemCount > 0 && mRecyclerViewLayoutManager!!.getChildAt(0) != null
    }

    private enum class DeckSelectionType {
        /** Show study options if fragmented, otherwise, review  */
        DEFAULT,

        /** Always show study options (if the deck counts are clicked)  */
        SHOW_STUDY_OPTIONS,

        /** Always open reviewer (keyboard shortcut)  */
        SKIP_STUDY_OPTIONS
    }

    companion object {
        /**
         * Result codes from other activities
         */
        const val RESULT_MEDIA_EJECTED = 202
        const val RESULT_DB_ERROR = 203
        const val UPGRADE_VERSION_KEY = "lastUpgradeVersion"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @JvmField
        @VisibleForTesting
        val REQUEST_STORAGE_PERMISSION = 0
        private const val REQUEST_PATH_UPDATE = 1
        const val REPORT_FEEDBACK = 4
        private const val LOG_IN_FOR_SYNC = 6
        private const val SHOW_INFO_NEW_VERSION = 9
        const val SHOW_STUDYOPTIONS = 11
        private const val ADD_NOTE = 12
        const val PICK_APKG_FILE = 13
        private const val PICK_EXPORT_FILE = 14

        // For automatic syncing
        // 10 minutes in milliseconds.
        const val AUTOMATIC_SYNC_MIN_INTERVAL: Long = 600000
        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400
        fun joinSyncMessages(dialogMessage: String?, syncMessage: String?): String? {
            // If both strings have text, separate them by a new line, otherwise return whichever has text
            return if (!TextUtils.isEmpty(dialogMessage) && !TextUtils.isEmpty(syncMessage)) {
                """
     $dialogMessage
     
     $syncMessage
                """.trimIndent()
            } else if (!TextUtils.isEmpty(dialogMessage)) {
                dialogMessage
            } else {
                syncMessage
            }
        }

        // Animation utility methods used by __renderPage() method
        @JvmOverloads
        fun fadeIn(view: View?, duration: Int, translation: Float = 0f, startAction: Runnable? = Runnable { view!!.visibility = View.VISIBLE }): ViewPropertyAnimator {
            view!!.alpha = 0f
            view.translationY = translation
            return view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration.toLong())
                .withStartAction(startAction)
        }

        @JvmOverloads
        fun fadeOut(view: View?, duration: Int, translation: Float = 0f, endAction: Runnable? = Runnable { view!!.visibility = View.GONE }): ViewPropertyAnimator {
            view!!.alpha = 1f
            view.translationY = 0f
            return view.animate()
                .alpha(0f)
                .translationY(translation)
                .setDuration(duration.toLong())
                .withEndAction(endAction)
        }
    }
}
