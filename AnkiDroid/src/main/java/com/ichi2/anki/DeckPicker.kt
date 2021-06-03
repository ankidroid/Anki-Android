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
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.database.SQLException
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.text.TextUtils
import android.util.Pair
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.GravityEnum
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.exception.FilteredAncestor
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.stats.AnkiStatsTaskHandler
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.async.*
import com.ichi2.compat.CompatHelper
import com.ichi2.libanki.Card
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Decks
import com.ichi2.libanki.Utils
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.DeckTreeNode
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.libanki.sync.Syncer
import com.ichi2.libanki.utils.TimeUtils
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.*
import com.ichi2.widget.WidgetStatus
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

// Added this annotation since lint throws error that "COMPANION" should be used here
// TODO: Update lint rules since companion is a keyword in Kotlin
@SuppressLint("ConstantFieldName")
open class DeckPicker : NavigationDrawerActivity(), StudyOptionsFragment.StudyOptionsListener, SyncErrorDialog.SyncErrorDialogListener, ImportDialog.ImportDialogListener, MediaCheckDialog.MediaCheckDialogListener, ExportDialog.ExportDialogListener, ActivityCompat.OnRequestPermissionsResultCallback, CustomStudyDialog.CustomStudyListener {
    // Short animation duration from system
    private var mShortAnimDuration = 0
    private var mBackButtonPressedToExit = false
    private var mDeckPickerContent: RelativeLayout? = null
    private var mProgressDialog: MaterialDialog? = null
    private var mStudyoptionsFrame: View? = null
    private var mRecyclerView: RecyclerView? = null
    private var mRecyclerViewLayoutManager: LinearLayoutManager? = null
    private var mDeckListAdapter: DeckAdapter<*>? = null
    private val mSnackbarShowHideCallback = Snackbar.Callback()
    private var mNoDecksPlaceholder: LinearLayout? = null
    private var mPullToSyncWrapper: SwipeRefreshLayout? = null
    private var mReviewSummaryTextView: TextView? = null
    private var mUnmountReceiver: BroadcastReceiver? = null
    private var mContextMenuDid: Long = 0
    private val mDialogEditText: EditText? = null
    private var mFloatingActionMenu: DeckPickerFloatingActionMenu? = null

    // flag asking user to do a full sync which is used in upgrade path
    private var mRecommendFullSync = false

    // flag keeping track of when the app has been paused
    private var mActivityPaused = false

    // Flag to keep track of startup error
    private var mStartupError = false

    private var mExportFileName: String? = null
    private var mEmptyCardTask: CollectionTask<*, *>? = null

    @JvmField
    @VisibleForTesting
    var mDueTree: List<AbstractDeckTreeNode<*>>? = null

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

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val mDeckExpanderClickListener = View.OnClickListener { view: View ->
        val did = view.tag as Long
        if (col?.decks.children(did).size > 0) {
            col?.decks.collapse(did)
            __renderPage()
            dismissAllDialogFragments()
        }
    }
    private val mDeckClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.DEFAULT) }
    private val mCountsClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.SHOW_STUDY_OPTIONS) }
    private fun onDeckClick(v: View, selectionType: DeckSelectionType) {
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        if (mFloatingActionMenu?.mIsFABOpen == true) {
            mFloatingActionMenu?.closeFloatingActionMenu()
        }
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
        UIUtils.showThemedToast(this, message, false)
        Timber.w(message)
    }

    private val mDeckLongClickListener = View.OnLongClickListener { v ->
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Long tapped on deck with id %d", deckId)
        mContextMenuDid = deckId
        showDialogFragment(DeckPickerContextMenu.newInstance(deckId))
        true
    }
    open val backupManager: BackupManager?
        get() = BackupManager()
    private val mImportAddListener = ImportAddListener(this)

    private class ImportAddListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, String?, Triple<AnkiPackageImporter?, Boolean?, String?>?>(deckPicker) {
        override fun actualOnPostExecute(deckPicker: DeckPicker, result: Triple<AnkiPackageImporter?, Boolean?, String?>?) {
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
            // If boolean and string are both set, we are signalling an error message
            // instead of a successful result.
            if (result?.second == true && result.third != null) {
                Timber.w("Import: Add Failed: %s", result.third)
                deckPicker.showSimpleMessageDialog(result.third)
            } else {
                Timber.i("Import: Add succeeded")
                val imp = result?.first
                deckPicker.showSimpleMessageDialog(imp?.log?.let { TextUtils.join("\n", it) })
                deckPicker.updateDeckList()
            }
        }

        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            if (deckPicker.mProgressDialog == null || !deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog = StyledProgressDialog.show(
                    deckPicker,
                    deckPicker.resources.getString(R.string.import_title), null, false
                )
            }
        }

        override fun actualOnProgressUpdate(deckPicker: DeckPicker, content: String?) {
            deckPicker.mProgressDialog?.setContent(content)
        }
    }

    private fun importReplaceListener(): ImportReplaceListener {
        return ImportReplaceListener(this)
    }

    private class ImportReplaceListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, String?, BooleanGetter?>(deckPicker) {
        override fun actualOnPostExecute(deckPicker: DeckPicker, result: BooleanGetter?) {
            Timber.i("Import: Replace Task Completed")
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
            val res = deckPicker.resources
            if (result?.boolean == true) {
                deckPicker.updateDeckList()
            } else {
                deckPicker.showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg), true)
            }
        }

        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            if (deckPicker.mProgressDialog == null || !deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog = StyledProgressDialog.show(
                    deckPicker,
                    deckPicker.resources.getString(R.string.import_title),
                    deckPicker.resources.getString(R.string.import_replacing), false
                )
            }
        }

        override fun actualOnProgressUpdate(deckPicker: DeckPicker, message: String?) {
            deckPicker.mProgressDialog?.setContent(message)
        }
    }

    private fun exportListener(): ExportListener {
        return ExportListener(this)
    }

    private class ExportListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, Pair<Boolean?, String?>?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.mProgressDialog = StyledProgressDialog.show(
                deckPicker, null,
                deckPicker.resources.getString(R.string.export_in_progress), false
            )
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, result: Pair<Boolean?, String?>?) {
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }

            // If boolean and string are both set, we are signalling an error message
            // instead of a successful result.
            if (result?.first == true && result.second != null) {
                Timber.w("Export Failed: %s", result.second)
                deckPicker.showSimpleMessageDialog(result.second)
            } else {
                Timber.i("Export successful")
                val exportPath = result?.second
                if (exportPath != null) {
                    deckPicker.showAsyncDialogFragment(DeckPickerExportCompleteDialog.newInstance(exportPath))
                } else {
                    UIUtils.showThemedToast(deckPicker, deckPicker.resources.getString(R.string.export_unsuccessful), true)
                }
            }
        }
    }
    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------
    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate()")
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        mCustomStudyDialogFactory = CustomStudyDialogFactory({ this.col }, this).attachToActivity(this)

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
        mDeckPickerContent?.setVisibility(View.GONE)
        mNoDecksPlaceholder?.setVisibility(View.GONE)

        // specify a LinearLayoutManager and set up item dividers for the RecyclerView
        mRecyclerViewLayoutManager = LinearLayoutManager(this)
        mRecyclerView?.setLayoutManager(mRecyclerViewLayoutManager)
        val ta = this.obtainStyledAttributes(intArrayOf(R.attr.deckDivider))
        val divider = ta.getDrawable(0)
        ta.recycle()
        val dividerDecorator = DividerItemDecoration(this, mRecyclerViewLayoutManager!!.orientation)
        dividerDecorator.setDrawable(divider!!)
        mRecyclerView?.addItemDecoration(dividerDecorator)

        // Add background to Deckpicker activity
        val view = if (mFragmented) findViewById(R.id.deckpicker_view) else findViewById<View>(R.id.root_layout)
        var hasDeckPickerBackground = false
        try {
            hasDeckPickerBackground = applyDeckPickerBackground(view)
        } catch (e: OutOfMemoryError) { // 6608 - OOM should be catchable here.
            Timber.w(e, "Failed to apply background - OOM")
            UIUtils.showThemedToast(this, getString(R.string.background_image_too_large), false)
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply background")
            UIUtils.showThemedToast(this, getString(R.string.failed_to_apply_background_image, e.localizedMessage), false)
        }

        // create and set an adapter for the RecyclerView
        mDeckListAdapter = DeckAdapter<DeckTreeNode>(layoutInflater, this)
        mDeckListAdapter?.setDeckClickListener(mDeckClickListener)
        mDeckListAdapter?.setCountsClickListener(mCountsClickListener)
        mDeckListAdapter?.setDeckExpanderClickListener(mDeckExpanderClickListener)
        mDeckListAdapter?.setDeckLongClickListener(mDeckLongClickListener)
        mDeckListAdapter?.enablePartialTransparencyForBackground(hasDeckPickerBackground)
        mRecyclerView?.setAdapter(mDeckListAdapter)
        mPullToSyncWrapper = findViewById(R.id.pull_to_sync_wrapper)
        mPullToSyncWrapper?.setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
        mPullToSyncWrapper?.setOnRefreshListener {
            Timber.i("Pull to Sync: Syncing")
            mPullToSyncWrapper?.setRefreshing(false)
            sync()
        }
        mPullToSyncWrapper?.getViewTreeObserver()?.addOnScrollChangedListener { mPullToSyncWrapper?.setEnabled(mRecyclerViewLayoutManager!!.findFirstCompletelyVisibleItemPosition() == 0) }

        // Setup the FloatingActionButtons, should work everywhere with min API >= 15
        mFloatingActionMenu = DeckPickerFloatingActionMenu(view, this)
        mReviewSummaryTextView = findViewById(R.id.today_stats_text_view)
        mShortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     */
    fun handleStartup() {
        if (Permissions.hasStorageAccessPermission(this)) {
            val colOpen = firstCollectionOpen()
            if (colOpen) {
                // Show any necessary dialogs (e.g. changelog, special messages, etc)
                val sharedPrefs = AnkiDroidApp.getSharedPrefs(this)
                showStartupScreensAndDialogs(sharedPrefs, 0)
                mStartupError = false
            } else {
                // Show error dialogs
                val failure = InitialActivity.getStartupFailureType(this)
                handleStartupFailure(failure)
                mStartupError = false
            }
        } else {
            requestStoragePermission()
        }
    }

    @VisibleForTesting
    fun handleStartupFailure(failure: InitialActivity.StartupFailure?) {
        when (failure) {
            InitialActivity.StartupFailure.SD_CARD_NOT_MOUNTED -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            InitialActivity.StartupFailure.DIRECTORY_NOT_ACCESSIBLE -> {
                /*Timber.i("AnkiDroid directory inaccessible")
                val i = Preferences.getPreferenceSubscreenIntent(this, "com.ichi2.anki.prefs.advanced")
                startActivityForResultWithoutAnimation(i, REQUEST_PATH_UPDATE)*/
                UIUtils.showThemedToast(this, R.string.directory_inaccessible, false)
            }
            InitialActivity.StartupFailure.FUTURE_ANKIDROID_VERSION -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialog.INCOMPATIBLE_DB_VERSION)
            }
            InitialActivity.StartupFailure.DATABASE_LOCKED -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_DB_LOCKED)
            }
            InitialActivity.StartupFailure.DATABASE_DOWNGRADE_REQUIRED -> // This has a callback to continue with handleStartup
                InitialActivity.downgradeBackend(this)
            InitialActivity.StartupFailure.DB_ERROR -> displayDatabaseFailure()
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

    /**
     * Try to open the Collection for the first time
     * @return whether or not we were successful
     */
    private fun firstCollectionOpen(): Boolean {
        if (AnkiDroidApp.webViewFailedToLoad()) {
            MaterialDialog.Builder(this)
                .title(R.string.ankidroid_init_failed_webview_title)
                .content(getString(R.string.ankidroid_init_failed_webview, AnkiDroidApp.getWebViewErrorMessage()))
                .positiveText(R.string.close)
                .onPositive { d: MaterialDialog?, w: DialogAction? -> exit() }
                .cancelable(false)
                .show()
            return false
        }
        return CollectionHelper.getInstance().getColSafe(this) != null
    }

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
                .onPositive { innerDialog: MaterialDialog?, innerWhich: DialogAction? ->
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
            col?.decks.id(deckName)
        } catch (filteredAncestor: FilteredAncestor) {
            UIUtils.showThemedToast(this, getString(R.string.decks_rename_filtered_nosubdecks), false)
            return false
        }
        updateDeckList()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Null check to prevent crash when col inaccessible
        return if (!colIsOpen()) {
            false
        } else super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        menuInflater.inflate(R.menu.deck_picker, menu)
        val sdCardAvailable = AnkiDroidApp.isSdCardMounted()
        menu.findItem(R.id.action_sync).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_new_filtered_deck).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_check_database).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_check_media).isEnabled = sdCardAvailable
        menu.findItem(R.id.action_empty_cards).isEnabled = sdCardAvailable
        val toolbarSearchItem = menu.findItem(R.id.deck_picker_action_filter)
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
            if (mFragmented || col?.undoAvailable() == false) {
                menu.findItem(R.id.action_undo).isVisible = false
            } else {
                val res = resources
                menu.findItem(R.id.action_undo).isVisible = true
                val undo = res.getString(R.string.studyoptions_congrats_undo, col?.undoName(res))
                menu.findItem(R.id.action_undo).title = undo
            }

            // Remove the filter - not necessary and search has other implications for new users.
            menu.findItem(R.id.deck_picker_action_filter).isVisible = col?.decks.count() >= 10
        }
        return super.onCreateOptionsMenu(menu)
    }

    @VisibleForTesting
    protected open fun displaySyncBadge(menu: Menu) {
        val syncMenu = menu.findItem(R.id.action_sync)
        val syncStatus = SyncStatus.getSyncStatus { this.col }
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
                if (syncStatus == SyncStatus.NO_ACCOUNT) {
                    syncMenu.setTitle(R.string.sync_menu_title_no_account)
                } else if (syncStatus == SyncStatus.FULL_SYNC) {
                    syncMenu.setTitle(R.string.sync_menu_title_full_sync)
                }
                // Orange-red icon with exclamation mark
                BadgeDrawableBuilder(resources)
                    .withText('!')
                    .withColor(ContextCompat.getColor(this, R.color.badge_error))
                    .replaceBadge(syncMenu)
            }
            else -> {
                Timber.w("Unhandled sync status: %s", syncStatus)
                syncMenu.setTitle(R.string.sync_title)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val res = resources
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        val itemId = item.itemId
        if (itemId == R.id.action_undo) {
            Timber.i("DeckPicker:: Undo button pressed")
            undo()
            return true
        } else if (itemId == R.id.action_sync) {
            Timber.i("DeckPicker:: Sync button pressed")
            sync()
            return true
        } else if (itemId == R.id.action_import) {
            Timber.i("DeckPicker:: Import button pressed")
            showImportDialog(ImportDialog.DIALOG_IMPORT_HINT)
            return true
        } else if (itemId == R.id.action_new_filtered_deck) {
            val createFilteredDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
            createFilteredDeckDialog.setOnNewDeckCreated { id: Long? ->
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
            startActivityForResultWithAnimation(noteTypeBrowser, 0, ActivityTransitionAnimation.Direction.START)
            return true
        } else if (itemId == R.id.action_restore_backup) {
            Timber.i("DeckPicker:: Restore from backup button pressed")
            showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_RESTORE_BACKUP)
            return true
        } else if (itemId == R.id.action_export) {
            Timber.i("DeckPicker:: Export collection button pressed")
            val msg = resources.getString(R.string.confirm_apkg_export)
            showDialogFragment(ExportDialog.newInstance(msg))
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
            if (resultCode == Reviewer.RESULT_NO_MORE_CARDS) {
                // Show a message when reviewing has finished
                if (col?.sched.count() == 0) {
                    UIUtils.showSimpleSnackbar(this, R.string.studyoptions_congrats_finished, false)
                } else {
                    UIUtils.showSimpleSnackbar(this, R.string.studyoptions_no_cards_due, false)
                }
            } else if (resultCode == Reviewer.RESULT_ABORT_AND_SYNC) {
                Timber.i("Obtained Abort and Sync result")
                TaskManager.waitForAllToFinish(4)
                sync()
            }
        } else if (requestCode == REQUEST_PATH_UPDATE) {
            // The collection path was inaccessible on startup so just close the activity and let user restart
            finishWithoutAnimation()
        } else if (requestCode == PICK_APKG_FILE && resultCode == RESULT_OK) {
            val importResult = ImportUtils.handleFileImport(this, intent)
            if (!importResult.isSuccess) {
                ImportUtils.showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, false)
            }
        } else if (requestCode == PICK_EXPORT_FILE && resultCode == RESULT_OK) {
            if (exportToProvider(intent, true)) {
                UIUtils.showSimpleSnackbar(this, getString(R.string.export_save_apkg_successful), true)
            } else {
                UIUtils.showSimpleSnackbar(this, getString(R.string.export_save_apkg_unsuccessful), false)
            }
        }
    }

    private fun exportToProvider(intent: Intent?, deleteAfterExport: Boolean): Boolean {
        if (intent == null || intent.data == null) {
            Timber.e("exportToProvider() provided with insufficient intent data %s", intent)
            return false
        }
        val uri = intent.data
        Timber.d("Exporting from file to ContentProvider URI: %s/%s", mExportFileName, uri.toString())
        val fileOutputStream: FileOutputStream
        val pfd: ParcelFileDescriptor?
        try {
            pfd = contentResolver.openFileDescriptor(uri!!, "w")
            if (pfd != null) {
                fileOutputStream = FileOutputStream(pfd.fileDescriptor)
                CompatHelper.getCompat().copyFile(mExportFileName, fileOutputStream)
                fileOutputStream.close()
                pfd.close()
            } else {
                Timber.w("exportToProvider() failed - ContentProvider returned null file descriptor for %s", uri)
                return false
            }
            if (deleteAfterExport && !File(mExportFileName).delete()) {
                Timber.w("Failed to delete temporary export file %s", mExportFileName)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to export file to Uri: %s/%s", mExportFileName, uri.toString())
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION && permissions.size == 1) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                invalidateOptionsMenu()
                handleStartup()
            } else {
                // User denied access to file storage  so show error toast and display "App Info"
                UIUtils.showThemedToast(this, R.string.startup_no_storage_permission, false)
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
          startup. */if (colIsOpen()) {
            TaskManager.launchCollectionTask(CollectionTask.LoadCollectionComplete())
        }
        // Update sync status (if we've come back from a screen)
        supportInvalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putLong("mContextMenuDid", mContextMenuDid)
        savedInstanceState.putBoolean("mClosedWelcomeMessage", mClosedWelcomeMessage)
        mFloatingActionMenu?.mIsFABOpen?.let { savedInstanceState.putBoolean("mIsFABOpen", it) }
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mContextMenuDid = savedInstanceState.getLong("mContextMenuDid")
        mFloatingActionMenu?.mIsFABOpen = savedInstanceState.getBoolean("mIsFABOpen")
    }

    override fun onPause() {
        Timber.d("onPause()")
        mActivityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        TaskManager.cancelAllTasks(CollectionTask.LoadDeckCounts::class.java)
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
            Connection.isOnline() && col?.time?.intTimeMS() - lastSyncTime > AUTOMATIC_SYNC_MIN_INTERVAL
        ) {
            Timber.i("Triggering Automatic Sync")
            sync()
        }
    }

    override fun onBackPressed() {
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (mFloatingActionMenu?.mIsFABOpen == true) {
                mFloatingActionMenu?.closeFloatingActionMenu()
            } else {
                if (mBackButtonPressedToExit) {
                    automaticSync()
                    finishWithAnimation()
                } else {
                    UIUtils.showThemedToast(this, getString(R.string.back_pressed_once), true)
                }
                mBackButtonPressedToExit = true
            }
        }
    }

    private fun finishWithAnimation() {
        super.finishWithAnimation(ActivityTransitionAnimation.Direction.DOWN)
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
                handleDeckSelection(col?.decks.selected(), DeckSelectionType.SKIP_STUDY_OPTIONS)
            }
            else -> {
            }
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
        BackupManager.performBackupInBackground(col?.path, col?.time)

        // Force a full sync if flag was set in upgrade path, asking the user to confirm if necessary
        if (mRecommendFullSync) {
            mRecommendFullSync = false
            try {
                col?.modSchema()
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
        startActivityForResultWithAnimation(intent, ADD_NOTE, ActivityTransitionAnimation.Direction.START)
    }

    private fun showStartupScreensAndDialogs(preferences: SharedPreferences, skip: Int) {

        // For Android 8/8.1 we want to use software rendering by default or the Reviewer UI is broken #7369
        if (CompatHelper.getSdkVersion() == Build.VERSION_CODES.O ||
            CompatHelper.getSdkVersion() == Build.VERSION_CODES.O_MR1
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
        } else if ("" == preferences.getString("lastVersion", "")) {
            Timber.i("Fresh install")
            preferences.edit().putString("lastVersion", VersionUtils.getPkgVersionName()).apply()
            onFinishedStartup()
        } else if (skip < 2 && preferences.getString("lastVersion", "") != VersionUtils.getPkgVersionName()) {
            Timber.i("AnkiDroid is being updated and a collection already exists.")
            // The user might appreciate us now, see if they will help us get better?
            if (!preferences.contains(UsageAnalytics.ANALYTICS_OPTIN_KEY)) {
                displayAnalyticsOptInDialog()
            }

            // For upgrades, we check if we are upgrading
            // to a version that contains additions to the database integrity check routine that we would
            // like to run on all collections. A missing version number is assumed to be a fresh
            // installation of AnkiDroid and we don't run the check.
            val current = VersionUtils.getPkgVersionCode()
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

            // Fix "font-family" definition in templates created by AnkiDroid before 2.6alhpa23
            if (previous < 20600123) {
                Timber.i("Fixing font-family definition in templates")
                try {
                    val models = col?.models
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
            val upgradePrefsVersion = AnkiDroidApp.CHECK_PREFERENCES_AT_VERSION
            val upgradeDbVersion = AnkiDroidApp.CHECK_DB_AT_VERSION

            // Specifying a checkpoint in the future is not supported, please don't do it!
            if (current < upgradePrefsVersion) {
                Timber.e("Checkpoint in future produced.")
                UIUtils.showSimpleSnackbar(this, "Invalid value for CHECK_PREFERENCES_AT_VERSION", false)
                onFinishedStartup()
                return
            }
            if (current < upgradeDbVersion) {
                Timber.e("Invalid value for CHECK_DB_AT_VERSION")
                UIUtils.showSimpleSnackbar(this, "Invalid value for CHECK_DB_AT_VERSION", false)
                onFinishedStartup()
                return
            }

            // Skip full DB check if the basic check is OK
            // TODO: remove this variable if we really want to do the full db check on every user
            val skipDbCheck = false
            // if (previous < upgradeDbVersion && getCol().basicCheck()) {
            //    skipDbCheck = true;
            // }
            if (!skipDbCheck && previous < upgradeDbVersion || previous < upgradePrefsVersion) {
                if (previous < upgradePrefsVersion) {
                    Timber.i("showStartupScreensAndDialogs() running upgradePreferences()")
                    upgradePreferences(previous)
                }
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
                        .onPositive { materialDialog: MaterialDialog?, dialogAction: DialogAction? -> integrityCheck() }
                        .onNeutral { materialDialog: MaterialDialog?, dialogAction: DialogAction? -> restartActivity() }
                        .onNegative { materialDialog: MaterialDialog?, dialogAction: DialogAction? -> restartActivity() }
                        .canceledOnTouchOutside(false)
                        .cancelable(false)
                        .build()
                        .show()
                } else if (previous < upgradePrefsVersion) {
                    Timber.i("Updated preferences with no integrity check - restarting activity")
                    // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                    // proceed
                    restartActivity()
                }
            } else {
                // If no changes are required we go to the new features activity
                // There the "lastVersion" is set, so that this code is not reached again
                if (VersionUtils.isReleaseVersion()) {
                    Timber.i("Displaying new features")
                    val infoIntent = Intent(this, Info::class.java)
                    infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
                    if (skip != 0) {
                        startActivityForResultWithAnimation(infoIntent, SHOW_INFO_NEW_VERSION, ActivityTransitionAnimation.Direction.START)
                    } else {
                        startActivityForResultWithoutAnimation(infoIntent, SHOW_INFO_NEW_VERSION)
                    }
                } else {
                    Timber.i("Dev Build - not showing 'new features'")
                    // Don't show new features dialog for development builds
                    preferences.edit().putString("lastVersion", VersionUtils.getPkgVersionName()).apply()
                    val ver = resources.getString(R.string.updated_version, VersionUtils.getPkgVersionName())
                    UIUtils.showSnackbar(this, ver, true, -1, null, findViewById(R.id.root_layout), null)
                    showStartupScreensAndDialogs(preferences, 2)
                }
            }
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    open fun displayDowngradeFailedNoSpace() {
        Timber.w("Not enough space to downgrade")
        val formatter = DeckPickerNoSpaceToDowngradeDialog.FileSizeFormatter(this)
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

    private fun upgradePreferences(previousVersionCode: Long) {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        // clear all prefs if super old version to prevent any errors
        if (previousVersionCode < 20300130) {
            Timber.i("Old version of Anki - Clearing preferences")
            preferences.edit().clear().apply()
        }
        // when upgrading from before 2.5alpha35
        if (previousVersionCode < 20500135) {
            Timber.i("Old version of Anki - Fixing Zoom")
            // Card zooming behaviour was changed the preferences renamed
            val oldCardZoom = preferences.getInt("relativeDisplayFontSize", 100)
            val oldImageZoom = preferences.getInt("relativeImageSize", 100)
            preferences.edit().putInt("cardZoom", oldCardZoom).apply()
            preferences.edit().putInt("imageZoom", oldImageZoom).apply()
            if (!preferences.getBoolean("useBackup", true)) {
                preferences.edit().putInt("backupMax", 0).apply()
            }
            preferences.edit().remove("useBackup").apply()
            preferences.edit().remove("intentAdditionInstantAdd").apply()
        }
        if (preferences.contains("fullscreenReview")) {
            Timber.i("Old version of Anki - Fixing Fullscreen")
            // clear fullscreen flag as we use a integer
            try {
                val old = preferences.getBoolean("fullscreenReview", false)
                preferences.edit().putString("fullscreenMode", if (old) "1" else "0").apply()
            } catch (e: ClassCastException) {
                Timber.w(e)
                // TODO:  can remove this catch as it was only here to fix an error in the betas
                preferences.edit().remove("fullscreenMode").apply()
            }
            preferences.edit().remove("fullscreenReview").apply()
        }
    }

    private fun undoTaskListener(isReview: Boolean): UndoTaskListener {
        return UndoTaskListener(isReview, this)
    }

    private class UndoTaskListener(private val mIsreview: Boolean, deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Card?, BooleanGetter?>(deckPicker) {
        override fun actualOnCancelled(deckPicker: DeckPicker) {
            deckPicker.hideProgressBar()
        }

        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.showProgressBar()
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, result: BooleanGetter?) {
            deckPicker.hideProgressBar()
            Timber.i("Undo completed")
            if (mIsreview) {
                Timber.i("Review undone - opening reviewer.")
                deckPicker.openReviewer()
            }
        }
    }

    private fun undo() {
        Timber.i("undo()")
        val undoReviewString = resources.getString(R.string.undo_action_review)
        val isReview = undoReviewString == col?.undoName(resources)
        TaskManager.launchCollectionTask(CollectionTask.Undo(), undoTaskListener(isReview))
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(id: Int) {
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(id)
        showAsyncDialogFragment(newFragment)
    }

    override fun showMediaCheckDialog(id: Int) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(id))
    }

    override fun showMediaCheckDialog(id: Int, checkList: List<List<String>>) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(id, checkList))
    }

    /**
     * Show a specific sync error dialog
     * @param id id of dialog to show
     */
    override fun showSyncErrorDialog(id: Int) {
        showSyncErrorDialog(id, "")
    }

    /**
     * Show a specific sync error dialog
     * @param id id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(id: Int, message: String?) {
        val newFragment: AsyncDialogFragment = SyncErrorDialog.newInstance(id, message)
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
                if (messageResource == R.string.youre_offline && !Connection.getAllowSyncOnNoConnection()) {
                    // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                    val root = findViewById<View>(R.id.root_layout)
                    UIUtils.showSnackbar(
                        this, messageResource, false, R.string.sync_even_if_offline,
                        { v: View? ->
                            Connection.setAllowSyncOnNoConnection(true)
                            sync()
                        },
                        null
                    )
                } else {
                    UIUtils.showSimpleSnackbar(this, messageResource, false)
                }
            } else {
                val res = AnkiDroidApp.getAppResources()
                showSimpleMessageDialog(res.getString(messageResource), syncMessage, false)
            }
        }
    }

    override fun showImportDialog(id: Int) {
        showImportDialog(id, "")
    }

    override fun showImportDialog(id: Int, message: String) {
        // On API19+ we only use import dialog to confirm, otherwise we use it the whole time
        if (id == ImportDialog.DIALOG_IMPORT_ADD_CONFIRM || id == ImportDialog.DIALOG_IMPORT_REPLACE_CONFIRM) {
            Timber.d("showImportDialog() delegating to ImportDialog")
            val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, message)
            showAsyncDialogFragment(newFragment)
        } else {
            Timber.d("showImportDialog() delegating to file picker intent")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
            intent.putExtra("android.content.extra.FANCY", true)
            intent.putExtra("android.content.extra.SHOW_FILESIZE", true)
            startActivityForResultWithoutAnimation(intent, PICK_APKG_FILE)
        }
    }

    fun onSdCardNotMounted() {
        UIUtils.showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finishWithoutAnimation()
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        AnkiDroidApp.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    private fun repairCollectionTask(): RepairCollectionTask {
        return RepairCollectionTask(this)
    }

    private class RepairCollectionTask(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, Boolean?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.mProgressDialog = StyledProgressDialog.show(
                deckPicker, null,
                deckPicker.resources.getString(R.string.backup_repair_deck_progress), false
            )
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, result: Boolean?) {
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
            if (result == false) {
                UIUtils.showThemedToast(deckPicker, deckPicker.resources.getString(R.string.deck_repair_error), true)
                deckPicker.showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        TaskManager.launchCollectionTask(CollectionTask.RepairCollection(), repairCollectionTask())
    }

    // Callback method to handle database integrity check
    fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionHelper.CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            MaterialDialog.Builder(this)
                .title(R.string.check_db_title)
                .content(status.getWarningDetails(this))
                .positiveText(R.string.integrity_check_continue_anyway)
                .onPositive { dialog: MaterialDialog?, which: DialogAction? -> performIntegrityCheck() }
                .negativeText(R.string.dialog_cancel)
                .show()
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        TaskManager.launchCollectionTask(CollectionTask.CheckDatabase(), CheckDatabaseListener())
    }

    private fun mediaCheckListener(): MediaCheckListener {
        return MediaCheckListener(this)
    }

    private class MediaCheckListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, PairWithBoolean<List<List<String?>?>?>?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.mProgressDialog = StyledProgressDialog.show(
                deckPicker, null,
                deckPicker.resources.getString(R.string.check_media_message), false
            )
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, result: PairWithBoolean<List<List<String?>?>?>?) {
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
            if (result?.bool == true) {
                val checkList = result.other
                deckPicker.showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, checkList as List<List<String>>)
            } else {
                deckPicker.showSimpleMessageDialog(deckPicker.resources.getString(R.string.check_media_failed))
            }
        }
    }

    override fun mediaCheck() {
        TaskManager.launchCollectionTask(CollectionTask.CheckMedia(), mediaCheckListener())
    }

    private fun mediaDeleteListener(): MediaDeleteListener {
        return MediaDeleteListener(this)
    }

    private class MediaDeleteListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, Int?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.mProgressDialog = StyledProgressDialog.show(
                deckPicker, null,
                deckPicker.resources.getString(R.string.delete_media_message), false
            )
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, deletedFiles: Int?) {
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
            deckPicker.showSimpleMessageDialog(
                deckPicker.resources.getString(R.string.delete_media_result_title),
                deletedFiles?.let { deckPicker.resources.getQuantityString(R.plurals.delete_media_result_message, it, deletedFiles) }
            )
        }
    }

    override fun deleteUnused(unused: List<String>) {
        TaskManager.launchCollectionTask(CollectionTask.DeleteMedia(unused), mediaDeleteListener())
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

    fun restoreFromBackup(path: String) {
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
    override fun sync(syncConflictResolution: Connection.ConflictResolution?) {
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
        val hkey = preferences.getString("hkey", "")
        if (hkey!!.length == 0) {
            Timber.w("User not logged in")
            mPullToSyncWrapper!!.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
        } else {
            Connection.sync(
                mSyncListener,
                arrayOf(
                    hkey,
                    preferences.getBoolean("syncFetchesMedia", true),
                    syncConflictResolution,
                    HostNumFactory.getInstance(baseContext)
                )?.let { Connection.Payload(arrayOf(it)) }
            )
        }
    }

    private val mSyncListener: Connection.TaskListener = object : Connection.CancellableTaskListener {
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

        override fun onPreExecute() {
            mCountUp = 0
            mCountDown = 0
            val syncStartTime = col?.time.intTimeMS()
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
                } catch (e: WindowManager.BadTokenException) {
                    // If we could not show the progress dialog to start even, bail out - user will get a message
                    Timber.w(e, "Unable to display Sync progress dialog, Activity not valid?")
                    mDialogDisplayFailure = true
                    Connection.cancel()
                    return
                }

                // Override the back key so that the user can cancel a sync which is in progress
                mProgressDialog?.setOnKeyListener(
                    DialogInterface.OnKeyListener { dialog: DialogInterface?, keyCode: Int, event: KeyEvent ->
                        // Make sure our method doesn't get called twice
                        if (event.action != KeyEvent.ACTION_DOWN) {
                            return@OnKeyListener true
                        }
                        if (keyCode == KeyEvent.KEYCODE_BACK && Connection.isCancellable() &&
                            !Connection.getIsCancelled()
                        ) {
                            // If less than 2s has elapsed since sync started then don't ask for confirmation
                            if (col?.time.intTimeMS() - syncStartTime < 2000) {
                                Connection.cancel()
                                mProgressDialog?.setContent(R.string.sync_cancel_message)
                                return@OnKeyListener true
                            }
                            // Show confirmation dialog to check if the user wants to cancel the sync
                            val builder = mProgressDialog?.getContext()?.let { MaterialDialog.Builder(it) }
                            builder?.content(R.string.cancel_sync_confirm)
                                ?.cancelable(false)
                                ?.positiveText(R.string.dialog_ok)
                                ?.negativeText(R.string.continue_sync)
                                ?.onPositive { inner_dialog: MaterialDialog?, which: DialogAction? ->
                                    mProgressDialog?.setContent(R.string.sync_cancel_message)
                                    Connection.cancel()
                                }
                            builder?.show()
                            return@OnKeyListener true
                        } else {
                            return@OnKeyListener false
                        }
                    }
                )
            }

            // Store the current time so that we don't bother the user with a sync prompt for another 10 minutes
            // Note: getLs() in Libanki doesn't take into account the case when no changes were found, or sync cancelled
            val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
            preferences.edit().putLong("lastSyncTime", syncStartTime).apply()
        }

        override fun onProgressUpdate(vararg values: Any?) {
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

        override fun onPostExecute(data: Connection.Payload?) {
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
            val syncMessage = data?.message
            Timber.i("Sync Listener: onPostExecute: Data: %s", data.toString())
            if (data?.success == false) {
                val result = data?.result
                val resultType = data?.resultType
                if (resultType != null) {
                    when (resultType) {
                        Syncer.ConnectionResultType.BAD_AUTH -> {
                            // delete old auth information
                            val preferences = AnkiDroidApp.getSharedPrefs(baseContext)
                            val editor = preferences.edit()
                            editor.putString("username", "")
                            editor.putString("hkey", "")
                            editor.apply()
                            // then show not logged in dialog
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
                        }
                        Syncer.ConnectionResultType.NO_CHANGES -> {
                            SyncStatus.markSyncCompleted()
                            // show no changes message, use false flag so we don't show "sync error" as the Dialog title
                            showSyncLogMessage(R.string.sync_no_changes_message, "")
                        }
                        Syncer.ConnectionResultType.CLOCK_OFF -> {
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
                        Syncer.ConnectionResultType.FULL_SYNC -> if (col?.isEmpty) {
                            // don't prompt user to resolve sync conflict if local collection empty
                            sync(Connection.ConflictResolution.FULL_DOWNLOAD)
                            // TODO: Also do reverse check to see if AnkiWeb collection is empty if Anki Desktop
                            // implements it
                        } else {
                            // If can't be resolved then automatically then show conflict resolution dialog
                            showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CONFLICT_RESOLUTION)
                        }
                        Syncer.ConnectionResultType.BASIC_CHECK_FAILED -> {
                            dialogMessage = res.getString(R.string.sync_basic_check_failed, res.getString(R.string.check_db))
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.DB_ERROR -> syncMessage?.let { showSyncErrorDialog(SyncErrorDialog.DIALOG_SYNC_CORRUPT_COLLECTION, it) }
                        Syncer.ConnectionResultType.OVERWRITE_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_overwrite_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.REMOTE_DB_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_remote_db_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.SD_ACCESS_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_write_access_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.FINISH_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_log_finish_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.CONNECTION_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_connection_error)
                            if (result.size >= 0 && result[0] is Exception) {
                                dialogMessage += """
                                  
                                  
                                   ${(result[0] as Exception).localizedMessage}
                                """.trimIndent()
                            }
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.IO_EXCEPTION -> handleDbError()
                        Syncer.ConnectionResultType.GENERIC_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_generic_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.OUT_OF_MEMORY_ERROR -> {
                            dialogMessage = res.getString(R.string.error_insufficient_memory)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        Syncer.ConnectionResultType.SANITY_CHECK_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_sanity_failed)
                            showSyncErrorDialog(
                                SyncErrorDialog.DIALOG_SYNC_SANITY_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage)!!
                            )
                        }
                        Syncer.ConnectionResultType.SERVER_ABORT -> // syncMsg has already been set above, no need to fetch it here.
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        Syncer.ConnectionResultType.MEDIA_SYNC_SERVER_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_media_error_check)
                            showSyncErrorDialog(
                                SyncErrorDialog.DIALOG_MEDIA_SYNC_ERROR,
                                joinSyncMessages(dialogMessage, syncMessage)!!
                            )
                        }
                        Syncer.ConnectionResultType.CUSTOM_SYNC_SERVER_URL -> {
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
                if (data?.data?.get(2) != null && "" != data.data[2]) {
                    Timber.i("Syncing had additional information")
                    // There was a media error, so show it
                    // Note: Do not log this data. May contain user email.
                    val message = """
                       ${res.getString(R.string.sync_database_acknowledge)}
                      
                       ${data.data.get(2)}
                    """.trimIndent()
                    showSimpleMessageDialog(message)
                } else if ((data?.data?.size ?: 0) > 0 && data?.data?.get(0) is Connection.ConflictResolution) {
                    // A full sync occurred
                    val dataString = data.data[0] as Connection.ConflictResolution
                    when (dataString) {
                        Connection.ConflictResolution.FULL_UPLOAD -> {
                            Timber.i("Full Upload Completed")
                            showSyncLogMessage(R.string.sync_log_uploading_message, syncMessage)
                        }
                        Connection.ConflictResolution.FULL_DOWNLOAD -> {
                            Timber.i("Full Download Completed")
                            showSyncLogMessage(R.string.backup_full_sync_from_server, syncMessage)
                        }
                        else -> {
                            Timber.i("Full Sync Completed (Unknown Direction)")
                            showSyncLogMessage(R.string.sync_database_acknowledge, syncMessage)
                        }
                    }
                } else {
                    Timber.i("Regular sync completed successfully")
                    showSyncLogMessage(R.string.sync_database_acknowledge, syncMessage)
                }
                // Mark sync as completed - then refresh the sync icon
                SyncStatus.markSyncCompleted()
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
        startActivityForResultWithAnimation(myAccount, LOG_IN_FOR_SYNC, ActivityTransitionAnimation.Direction.FADE)
    }

    // Callback to import a file -- adding it to existing collection
    override fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        TaskManager.launchCollectionTask(CollectionTask.ImportAdd(importPath), mImportAddListener)
    }

    // Callback to import a file -- replacing the existing collection
    override fun importReplace(importPath: String) {
        TaskManager.launchCollectionTask(CollectionTask.ImportReplace(importPath), importReplaceListener())
    }

    override fun exportApkg(filename: String, did: Long, includeSched: Boolean, includeMedia: Boolean) {
        val exportDir = File(externalCacheDir, "export")
        exportDir.mkdirs()
        val exportPath: File
        val timeStampSuffix = "-" + TimeUtils.getTimestamp(col?.time)
        exportPath = if (filename != null) {
            // filename has been explicitly specified
            File(exportDir, filename)
        } else if (did != null) {
            // filename not explicitly specified, but a deck has been specified so use deck name
            File(exportDir, col?.decks[did].getString("name").replace("\\W+".toRegex(), "_") + timeStampSuffix + ".apkg")
        } else if (!includeSched) {
            // full export without scheduling is assumed to be shared with someone else -- use "All Decks.apkg"
            File(exportDir, "All Decks$timeStampSuffix.apkg")
        } else {
            // full collection export -- use "collection.colpkg"
            val colPath = File(col?.path)
            val newFileName = colPath.name.replace(".anki2", "$timeStampSuffix.colpkg")
            File(exportDir, newFileName)
        }
        TaskManager.launchCollectionTask(CollectionTask.ExportApkg(exportPath.path, did, includeSched, includeMedia), exportListener())
    }

    fun emailFile(path: String?) {
        // Make sure the file actually exists
        val attachment = File(path)
        if (!attachment.exists()) {
            Timber.e("Specified apkg file %s does not exist", path)
            UIUtils.showThemedToast(this, resources.getString(R.string.apk_share_error), false)
            return
        }
        // Get a URI for the file to be shared via the FileProvider API
        val uri: Uri
        uri = try {
            FileProvider.getUriForFile(this@DeckPicker, "com.ichi2.anki.apkgfileprovider", attachment)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Could not generate a valid URI for the apkg file")
            UIUtils.showThemedToast(this, resources.getString(R.string.apk_share_error), false)
            return
        }
        val shareIntent = ShareCompat.IntentBuilder(this@DeckPicker)
            .setType("application/apkg")
            .setStream(uri)
            .setSubject(getString(R.string.export_email_subject, attachment.name))
            .setHtmlText(getString(R.string.export_email_text))
            .intent
        if (shareIntent.resolveActivity(packageManager) != null) {
            startActivityWithoutAnimation(shareIntent)
        } else {
            // Try to save it?
            UIUtils.showSimpleSnackbar(this, R.string.export_send_no_handlers, false)
            saveExportFile(path)
        }
    }

    fun saveExportFile(path: String?) {
        // Make sure the file actually exists
        val attachment = File(path)
        if (!attachment.exists()) {
            Timber.e("saveExportFile() Specified apkg file %s does not exist", path)
            UIUtils.showSimpleSnackbar(this, R.string.export_save_apkg_unsuccessful, false)
            return
        }

        // Send the user to the standard Android file picker via Intent
        mExportFileName = path
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "application/apkg"
        saveIntent.putExtra(Intent.EXTRA_TITLE, attachment.name)
        saveIntent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        saveIntent.putExtra("android.content.extra.FANCY", true)
        saveIntent.putExtra("android.content.extra.SHOW_FILESIZE", true)
        startActivityForResultWithoutAnimation(saveIntent, PICK_EXPORT_FILE)
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

    fun addSharedDeck() {
        openUrl(Uri.parse(resources.getString(R.string.shared_decks_url)))
    }

    private fun openStudyOptions(withDeckOptions: Boolean) {
        if (mFragmented) {
            // The fragment will show the study options screen instead of launching a new activity.
            loadStudyOptionsFragment(withDeckOptions)
        } else {
            val intent = Intent()
            intent.putExtra("withDeckOptions", withDeckOptions)
            intent.setClass(this, StudyOptionsActivity::class.java)
            startActivityForResultWithAnimation(intent, SHOW_STUDYOPTIONS, ActivityTransitionAnimation.Direction.START)
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
            else -> Timber.w("openReviewerOrStudyOptions: Unknown selection: %s", selectionType)
        }
    }

    private fun handleDeckSelection(did: Long, selectionType: DeckSelectionType) {
        // Clear the undo history when selecting a new deck
        if (col?.decks.selected() != did) {
            col?.clearUndo()
        }
        // Select the deck
        col?.decks.select(did)
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        // Reset the schedule so that we get the counts for the currently selected deck
        mFocusedDeck = did
        // Get some info about the deck to handle special cases
        val deckDueTreeNode = mDeckListAdapter!!.getNodeByDid(did)
        if (!deckDueTreeNode.shouldDisplayCounts() || deckDueTreeNode.knownToHaveRep()) {
            // If we don't yet have numbers, we trust the user that they knows what they opens, tries to open it.
            // If there is nothing to review, it'll come back to deck picker.
            openReviewerOrStudyOptions(selectionType)
            return
        }
        // There are numbers
        // Figure out what action to take
        if (col?.sched.hasCardsTodayAfterStudyAheadLimit()) {
            // If there are cards due that can't be studied yet (due to the learn ahead limit) then go to study options
            openStudyOptions(false)
        } else if (col?.sched.newDue() || col?.sched.revDue()) {
            // If there are no cards to review because of the daily study limit then give "Study more" option
            UIUtils.showSnackbar(
                this, R.string.studyoptions_limit_reached, false, R.string.study_more,
                { v: View? ->
                    val d = mCustomStudyDialogFactory!!.newCustomStudyDialog().withArguments(
                        CustomStudyDialog.CONTEXT_MENU_LIMITS,
                        col?.decks.selected(), true
                    )
                    showDialogFragment(d)
                },
                findViewById(R.id.root_layout), mSnackbarShowHideCallback
            )
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
        } else if (col?.decks.isDyn(did)) {
            // Go to the study options screen if filtered deck with no cards to study
            openStudyOptions(false)
        } else if (!deckDueTreeNode.hasChildren() && col?.isEmptyDeck(did)) {
            // If the deck is empty and has no children then show a message saying it's empty
            UIUtils.showSnackbar(
                this, R.string.empty_deck, false, R.string.empty_deck_add_note,
                { v: View? -> addNote() }, findViewById(R.id.root_layout), mSnackbarShowHideCallback
            )
            if (mFragmented) {
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        } else {
            // Otherwise say there are no cards scheduled to study, and give option to do custom study
            UIUtils.showSnackbar(
                this, R.string.studyoptions_empty_schedule, false, R.string.custom_study,
                { v: View? ->
                    val d = mCustomStudyDialogFactory!!.newCustomStudyDialog().withArguments(
                        CustomStudyDialog.CONTEXT_MENU_EMPTY_SCHEDULE,
                        col?.decks.selected(), true
                    )
                    showDialogFragment(d)
                },
                findViewById(R.id.root_layout), mSnackbarShowHideCallback
            )
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

    private fun <T : AbstractDeckTreeNode<T>?> updateDeckListListener(): UpdateDeckListListener<T> {
        return UpdateDeckListListener(this)
    }

    private class UpdateDeckListListener<T : AbstractDeckTreeNode<T>?>(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, List<T>?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            if (!deckPicker.colIsOpen()) {
                deckPicker.showProgressBar()
            }
            Timber.d("Refreshing deck list")
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, dueTree: List<T>?) {
            Timber.i("Updating deck list UI")
            deckPicker.hideProgressBar()
            // Make sure the fragment is visible
            if (deckPicker.mFragmented) {
                deckPicker.mStudyoptionsFrame!!.visibility = View.VISIBLE
            }
            if (dueTree == null) {
                Timber.e("null result loading deck counts")
                deckPicker.showCollectionErrorDialog()
                return
            }
            deckPicker.mDueTree = dueTree as List<DeckDueTreeNode>?
            deckPicker.__renderPage()
            // Update the mini statistics bar as well
            AnkiStatsTaskHandler.createReviewSummaryStatistics(deckPicker.col, deckPicker.mReviewSummaryTextView)
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
    fun updateDeckList() {
        updateDeckList(false)
    }

    private fun updateDeckList(quick: Boolean) {
        if (quick) {
            TaskManager.launchCollectionTask(CollectionTask.LoadDeck(), updateDeckListListener())
        } else {
            TaskManager.launchCollectionTask(CollectionTask.LoadDeckCounts(), updateDeckListListener())
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
        val isEmpty = mDueTree!!.size == 1 && mDueTree!![0].did == 1L && col?.isEmpty
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
                        .setStartDelay((if (decksListShown) mShortAnimDuration * 2 else 0).toLong())
                }
            } else {
                if (!decksListShown) {
                    fadeIn(mDeckPickerContent, mShortAnimDuration, translation)
                        .setStartDelay((if (placeholderShown) mShortAnimDuration * 2 else 0).toLong())
                }
                if (placeholderShown) {
                    fadeOut(mNoDecksPlaceholder, mShortAnimDuration, translation)
                }
            }
        }
        val currentFilter = if (mToolbarSearchView != null) mToolbarSearchView!!.query else null
        if (isEmpty) {
            if (supportActionBar != null) {
                supportActionBar?.setSubtitle(null)
            }
            if (mToolbarSearchView != null) {
                mDeckListAdapter!!.filter.filter(currentFilter)
            }
            // We're done here
            return
        }
        mDueTree?.let {
            mDeckListAdapter?.buildDeckList(it as List<Nothing>?, col, currentFilter)
        }

        // Set the "x due in y minutes" subtitle
        try {
            val eta = mDeckListAdapter!!.eta
            val due = mDeckListAdapter!!.due
            val res = resources
            if (col?.cardCount() != -1) {
                var time: String? = "-"
                if (eta != -1 && eta != null) {
                    time = Utils.timeQuantityTopDeckPicker(AnkiDroidApp.getInstance(), (eta * 60).toLong())
                }
                if (due != null && supportActionBar != null) {
                    supportActionBar!!.setSubtitle(res.getQuantityString(R.plurals.deckpicker_title, due, due, time))
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "RuntimeException setting time remaining")
        }
        val current = col?.decks.current().optLong("id")
        if (mFocusedDeck != current) {
            scrollDecklistToDeck(current)
            mFocusedDeck = current
        }
    }

    // Callback to show study options for currently selected deck
    fun showContextMenuDeckOptions() {
        // open deck options
        if (col?.decks.isDyn(mContextMenuDid)) {
            // open cram options if filtered deck
            val i = Intent(this@DeckPicker, FilteredDeckOptions::class.java)
            i.putExtra("did", mContextMenuDid)
            startActivityWithAnimation(i, ActivityTransitionAnimation.Direction.FADE)
        } else {
            // otherwise open regular options
            val i = Intent(this@DeckPicker, DeckOptions::class.java)
            i.putExtra("did", mContextMenuDid)
            startActivityWithAnimation(i, ActivityTransitionAnimation.Direction.FADE)
        }
    }

    // Callback to show export dialog for currently selected deck
    fun showContextMenuExportDialog() {
        exportDeck(mContextMenuDid)
    }

    fun exportDeck(did: Long) {
        val msg = resources.getString(R.string.confirm_apkg_export_deck, col?.decks[did].getString("name"))
        showDialogFragment(ExportDialog.newInstance(msg, did))
    }

    fun createIcon(context: Context?) {
        // This code should not be reachable with lower versions
        val shortcut = ShortcutInfoCompat.Builder(this, java.lang.Long.toString(mContextMenuDid))
            .setIntent(
                Intent(context, Reviewer::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra("deckId", mContextMenuDid)
            )
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setShortLabel(Decks.basename(col?.decks.name(mContextMenuDid)))
            .setLongLabel(col?.decks.name(mContextMenuDid))
            .build()
        try {
            val success = ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)

            // User report: "success" is true even if Vivo does not have permission
            if (AdaptionUtil.isVivo()) {
                UIUtils.showThemedToast(this, getString(R.string.create_shortcut_error_vivo), false)
            }
            if (!success) {
                UIUtils.showThemedToast(this, getString(R.string.create_shortcut_failed), false)
            }
        } catch (e: Exception) {
            Timber.w(e)
            UIUtils.showThemedToast(this, getString(R.string.create_shortcut_error, e.localizedMessage), false)
        }
    }

    // Callback to show dialog to rename the current deck
    @JvmOverloads
    fun renameDeckDialog(did: Long = mContextMenuDid) {
        val res = resources
        val currentName = col?.decks.name(did)
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.rename_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
        createDeckDialog.deckName = currentName
        createDeckDialog.setOnNewDeckCreated { id: Long? ->
            dismissAllDialogFragments()
            mDeckListAdapter!!.notifyDataSetChanged()
            updateDeckList()
            if (mFragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    // Callback to show confirm deck deletion dialog before deleting currently selected deck
    @JvmOverloads
    fun confirmDeckDeletion(did: Long = mContextMenuDid) {
        val res = resources
        if (!colIsOpen()) {
            return
        }
        if (did == 1L) {
            UIUtils.showSimpleSnackbar(this, R.string.delete_deck_default_deck, true)
            dismissAllDialogFragments()
            return
        }
        // Get the number of cards contained in this deck and its subdecks
        val children = col?.decks.children(did)
        val dids = LongArray(children.size + 1)
        dids[0] = did
        var i = 1
        for (l in children.values) {
            dids[i++] = l
        }
        val ids = Utils.ids2str(dids)
        val cnt = col?.db.queryScalar(
            "select count() from cards where did in $ids or odid in $ids"
        )
        // Delete empty decks without warning
        if (cnt == 0) {
            deleteDeck(did)
            dismissAllDialogFragments()
            return
        }
        // Otherwise we show a warning and require confirmation
        val msg: String
        val deckName = "'" + col?.decks.name(did) + "'"
        val isDyn = col?.decks.isDyn(did)
        msg = if (isDyn) {
            res.getString(R.string.delete_cram_deck_message, deckName)
        } else {
            res.getQuantityString(R.plurals.delete_deck_message, cnt, deckName, cnt)
        }
        showDialogFragment(DeckPickerConfirmDeleteDeckDialog.newInstance(msg))
    }

    // Callback to delete currently selected deck
    fun deleteContextMenuDeck() {
        deleteDeck(mContextMenuDid)
    }

    fun deleteDeck(did: Long) {
        TaskManager.launchCollectionTask(CollectionTask.DeleteDeck(did), deleteDeckListener(did))
    }

    private fun deleteDeckListener(did: Long): DeleteDeckListener {
        return DeleteDeckListener(did, this)
    }

    private class DeleteDeckListener(private val mDid: Long, deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, IntArray?>(deckPicker) {
        // Flag to indicate if the deck being deleted is the current deck.
        private var mRemovingCurrent = false
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.mProgressDialog = StyledProgressDialog.show(
                deckPicker, null,
                deckPicker.resources.getString(R.string.delete_deck), false
            )
            if (mDid == deckPicker.col?.decks.current().optLong("id")) {
                mRemovingCurrent = true
            }
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, v: IntArray?) {
            // In fragmented mode, if the deleted deck was the current deck, we need to reload
            // the study options fragment with a valid deck and re-center the deck list to the
            // new current deck. Otherwise we just update the list normally.
            if (deckPicker.mFragmented && mRemovingCurrent) {
                deckPicker.updateDeckList()
                deckPicker.openStudyOptions(false)
            } else {
                deckPicker.updateDeckList()
            }
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                try {
                    deckPicker.mProgressDialog!!.dismiss()
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

    private class SimpleProgressListener(deckPicker: DeckPicker?) : TaskListenerWithContext<DeckPicker?, Void?, StudyOptionsFragment.DeckStudyData?>(deckPicker) {
        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            deckPicker.showProgressBar()
        }

        override fun actualOnPostExecute(deckPicker: DeckPicker, stats: StudyOptionsFragment.DeckStudyData?) {
            deckPicker.updateDeckList()
            if (deckPicker.mFragmented) {
                deckPicker.loadStudyOptionsFragment(false)
            }
        }
    }

    fun rebuildFiltered() {
        col?.decks.select(mContextMenuDid)
        TaskManager.launchCollectionTask(CollectionTask.RebuildCram(), simpleProgressListener())
    }

    fun emptyFiltered() {
        col?.decks.select(mContextMenuDid)
        TaskManager.launchCollectionTask(CollectionTask.EmptyCram(), simpleProgressListener())
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
        startActivityForResultWithAnimation(reviewer, REQUEST_REVIEW, ActivityTransitionAnimation.Direction.START)
    }

    override fun onCreateCustomStudySession() {
        updateDeckList()
        openStudyOptions(false)
    }

    override fun onExtendStudyLimits() {
        if (mFragmented) {
            fragment?.refreshInterface(true)
        }
        updateDeckList()
    }

    fun handleEmptyCards() {
        mEmptyCardTask = TaskManager.launchCollectionTask(CollectionTask.FindEmptyCards(), handlerEmptyCardListener())
    }

    private fun handlerEmptyCardListener(): HandleEmptyCardListener {
        return HandleEmptyCardListener(this)
    }

    private class HandleEmptyCardListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker?, Int?, List<Long?>?>(deckPicker) {
        private val mNumberOfCards: Int
        private val mOnePercent: Int
        private var mIncreaseSinceLastUpdate = 0
        private fun confirmCancel(deckPicker: DeckPicker, task: CollectionTask<*, *>) {
            MaterialDialog.Builder(deckPicker)
                .content(R.string.confirm_cancel)
                .positiveText(deckPicker.resources.getString(R.string.yes))
                .negativeText(deckPicker.resources.getString(R.string.dialog_no))
                .onNegative { x: MaterialDialog?, y: DialogAction? -> actualOnPreExecute(deckPicker) }
                .onPositive { x: MaterialDialog?, y: DialogAction? -> task.safeCancel() }.show()
        }

        override fun actualOnPreExecute(deckPicker: DeckPicker) {
            val onCancel = DialogInterface.OnCancelListener { dialogInterface: DialogInterface? ->
                val emptyCardTask = deckPicker.mEmptyCardTask
                emptyCardTask?.let { confirmCancel(deckPicker, it) }
            }
            deckPicker.mProgressDialog = MaterialDialog.Builder(deckPicker)
                .progress(false, mNumberOfCards)
                .title(R.string.emtpy_cards_finding)
                .cancelable(true)
                .show()
            deckPicker.mProgressDialog?.setOnCancelListener(onCancel)
            deckPicker.mProgressDialog?.setCanceledOnTouchOutside(false)
        }

        override fun actualOnProgressUpdate(deckPicker: DeckPicker, progress: Int?) {
            if (progress != null) {
                mIncreaseSinceLastUpdate += progress
            }
            // Increase each time at least a percent of card has been processed since last update
            if (mIncreaseSinceLastUpdate > mOnePercent) {
                deckPicker.mProgressDialog!!.incrementProgress(mIncreaseSinceLastUpdate)
                mIncreaseSinceLastUpdate = 0
            }
        }

        override fun actualOnCancelled(deckPicker: DeckPicker) {
            deckPicker.mEmptyCardTask = null
        }

        /**
         * @param deckPicker
         * @param cids Null if it is cancelled (in this case we should not have called this method) or a list of cids
         */
        override fun actualOnPostExecute(deckPicker: DeckPicker, cids: List<Long?>?) {
            deckPicker.mEmptyCardTask = null
            if (cids == null) {
                return
            }
            if (cids.size == 0) {
                deckPicker.showSimpleMessageDialog(deckPicker.resources.getString(R.string.empty_cards_none))
            } else {
                val msg = String.format(deckPicker.resources.getString(R.string.empty_cards_count), cids.size)
                val dialog = ConfirmationDialog()
                dialog.setArgs(msg)
                val confirm = Runnable {
                    deckPicker.col?.remCards(cids)
                    UIUtils.showSimpleSnackbar(
                        deckPicker,
                        String.format(
                            deckPicker.resources.getString(R.string.empty_cards_deleted), cids.size
                        ),
                        false
                    )
                }
                dialog.setConfirm(confirm)
                deckPicker.showDialogFragment(dialog)
            }
            if (deckPicker.mProgressDialog != null && deckPicker.mProgressDialog!!.isShowing) {
                deckPicker.mProgressDialog!!.dismiss()
            }
        }

        init {
            mNumberOfCards = deckPicker.col?.cardCount()
            mOnePercent = mNumberOfCards / 100
        }
    }

    fun createSubdeckDialog() {
        createSubDeckDialog(mContextMenuDid)
    }

    private fun createSubDeckDialog(did: Long) {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, did)
        createDeckDialog.setOnNewDeckCreated { i: Long? ->
            // a deck was created
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
    internal inner class CheckDatabaseListener : TaskListener<String?, Pair<Boolean?, Collection.CheckDatabaseResult?>?>() {
        override fun onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(
                this@DeckPicker, AnkiDroidApp.getAppResources().getString(R.string.app_name),
                resources.getString(R.string.check_db_message), false
            )
        }

        override fun onPostExecute(result: Pair<Boolean?, Collection.CheckDatabaseResult?>?) {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                mProgressDialog!!.dismiss()
            }
            if (result == null) {
                handleDbError()
                return
            }
            val databaseResult = result.second
            if (databaseResult == null) {
                if (result.first == true) {
                    Timber.w("Expected result data, got nothing")
                } else {
                    handleDbError()
                }
                return
            }
            if (result.first == false || databaseResult.failed) {
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
                UIUtils.showThemedToast(this@DeckPicker, message, false)
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

        override fun onProgressUpdate(message: String?) {
            mProgressDialog!!.setContent(message)
        }
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
        private const val PICK_APKG_FILE = 13
        private const val PICK_EXPORT_FILE = 14

        // For automatic syncing
        // 10 minutes in milliseconds.
        const val AUTOMATIC_SYNC_MIN_INTERVAL: Long = 600000
        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400
        @JvmStatic
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
