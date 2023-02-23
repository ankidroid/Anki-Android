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

// usage of 'this' in constructors when class is non-final - weak warning
// should be OK as this is only non-final for tests
@file:Suppress("LeakingThis")

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
import android.util.TypedValue
import android.view.*
import android.view.View.OnLongClickListener
import android.view.WindowManager.BadTokenException
import android.widget.*
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import anki.collection.OpChanges
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.AnkiDroidApp.Companion.getSharedPrefs
import com.ichi2.anki.CollectionHelper.CollectionIntegrityStorageCheck
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.*
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.ImportDialog.ImportDialogListener
import com.ichi2.anki.dialogs.MediaCheckDialog.MediaCheckDialogListener
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.exception.ConfirmModSchemaException
import com.ichi2.anki.export.ActivityExportingDelegate
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.pages.CsvImporter
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.servicelayer.DeckService
import com.ichi2.anki.servicelayer.SchedulerService.NextCard
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.servicelayer.ScopedStorageService.userMigrationIsInProgress
import com.ichi2.anki.servicelayer.Undo
import com.ichi2.anki.servicelayer.scopedstorage.migrateuserdata.toMB
import com.ichi2.anki.services.MigrationService
import com.ichi2.anki.services.ServiceConnection
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.stats.AnkiStatsTaskHandler
import com.ichi2.anki.web.HostNumFactory
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.*
import com.ichi2.async.CollectionTask.*
import com.ichi2.async.Connection.CancellableTaskListener
import com.ichi2.async.Connection.ConflictResolution
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Collection.CheckDatabaseResult
import com.ichi2.libanki.importer.AnkiPackageImporter
import com.ichi2.libanki.sched.AbstractDeckTreeNode
import com.ichi2.libanki.sched.DeckDueTreeNode
import com.ichi2.libanki.sched.TreeNode
import com.ichi2.libanki.sched.findInDeckTree
import com.ichi2.libanki.sync.CustomSyncServerUrlException
import com.ichi2.libanki.sync.Syncer.ConnectionResultType
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.themes.StyledProgressDialog
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.*
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.utils.Permissions.hasStorageAccessPermission
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.RustCleanup
import org.intellij.lang.annotations.Language
import org.json.JSONException
import timber.log.Timber
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.system.measureTimeMillis

const val MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS = "secondWhenMigrationWasPostponedLast"

/**
 * The current entry point for AnkiDroid. Displays decks, allowing users to study. Many other functions.
 *
 * On a tablet, this is a fragmented view, with [StudyOptionsFragment] to the right: [loadStudyOptionsFragment]
 *
 * Often used as navigation to: [Reviewer], [NoteEditor] (adding notes), [StudyOptionsFragment] [SharedDecksDownloadFragment]
 *
 * Responsibilities:
 * * Setup/upgrades of the application: [handleStartup]
 * * Error handling [handleDbError] [handleDbLocked]
 * * Displaying a tree of decks, some of which may be collapsible: [mDeckListAdapter]
 *   * Allows users to study the decks
 *   * Displays deck progress
 *   * A long press opens a menu allowing modification of the deck
 *   * Filtering decks (if more than 10) [mToolbarSearchView]
 * * Controlling syncs
 *   * A user may [pull down][mPullToSyncWrapper] on the 'tree view' to sync
 *   * A [button][updateSyncIconFromState] which relies on [SyncStatus] to display whether a sync is needed
 *   * Blocks the UI and displays sync progress when syncing
 * * Displaying 'General' AnkiDroid options: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportDialogListener]
 * * A Floating Action Button [mFloatingActionMenu] allowing the user to quickly add notes/cards.
 * * A custom image as a background can be added: [applyDeckPickerBackground]
 */
@KotlinCleanup("lots to do")
@NeedsTest("On a new startup, the App Intro is displayed")
@NeedsTest("If the collection has been created, the app intro is not displayed")
@NeedsTest("If the user selects 'Sync Profile' in the app intro, a sync starts immediately")
open class DeckPicker :
    NavigationDrawerActivity(),
    StudyOptionsListener,
    SyncErrorDialogListener,
    ImportDialogListener,
    MediaCheckDialogListener,
    OnRequestPermissionsResultCallback,
    CustomStudyListener,
    ChangeManager.Subscriber {
    // Short animation duration from system
    private var mShortAnimDuration = 0
    private var mBackButtonPressedToExit = false
    private lateinit var mDeckPickerContent: RelativeLayout

    @Suppress("Deprecation") // TODO: Encapsulate ProgressDialog within a class to limit the use of deprecated functionality
    private var mProgressDialog: android.app.ProgressDialog? = null
    private var mStudyoptionsFrame: View? = null // not lateInit - can be null
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var recyclerView: RecyclerView
    private lateinit var mRecyclerViewLayoutManager: LinearLayoutManager
    private lateinit var mDeckListAdapter: DeckAdapter
    private val mSnackbarShowHideCallback = Snackbar.Callback()
    private lateinit var mExportingDelegate: ActivityExportingDelegate
    private lateinit var mNoDecksPlaceholder: LinearLayout
    private lateinit var mPullToSyncWrapper: SwipeRefreshLayout
    private lateinit var mReviewSummaryTextView: TextView

    @KotlinCleanup("make lateinit, but needs more changes")
    private var mUnmountReceiver: BroadcastReceiver? = null
    private lateinit var mFloatingActionMenu: DeckPickerFloatingActionMenu

    // flag asking user to do a full sync which is used in upgrade path
    private var mRecommendFullSync = false

    // flag keeping track of when the app has been paused
    private var mActivityPaused = false

    // Flag to keep track of startup error
    private var mStartupError = false
    private var mEmptyCardTask: Cancellable? = null

    /** See [OptionsMenuState]. */
    @VisibleForTesting
    var optionsMenuState: OptionsMenuState? = null

    @VisibleForTesting
    var dueTree: List<TreeNode<AbstractDeckTreeNode>>? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var searchDecksIcon: MenuItem? = null

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

    private var mToolbarSearchView: SearchView? = null
    private lateinit var mCustomStudyDialogFactory: CustomStudyDialogFactory
    private lateinit var mContextMenuFactory: DeckPickerContextMenu.Factory

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null
    private var loadDeckCounts: Job? = null

    private val migrationService: ServiceConnection<MigrationService> = object : ServiceConnection<MigrationService>() {
        override fun onServiceConnected(service: MigrationService) {
            service.migrationCompletedListener = { onStorageMigrationCompleted() }
        }
        override fun onServiceDisconnected(service: MigrationService) {
            service.migrationCompletedListener = null
        }
    }

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val mDeckExpanderClickListener = View.OnClickListener { view: View ->
        toggleDeckExpand(view.tag as Long)
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
            if (fragmented) {
                // Calling notifyDataSetChanged() will update the color of the selected deck.
                // This interferes with the ripple effect, so we don't do it if lollipop and not tablet view
                mDeckListAdapter.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            // Maybe later don't report if collectionIsOpen is false?
            Timber.w(e)
            val info = "$deckId colOpen:$collectionIsOpen"
            CrashReportService.sendExceptionReport(e, "deckPicker::onDeckClick", info)
            displayFailedToOpenDeck(deckId)
        }
    }

    private fun displayFailedToOpenDeck(deckId: DeckId) {
        // #6208 - if the click is accepted before the sync completes, we get a failure.
        // We use the Deck ID as the deck likely doesn't exist any more.
        val message = getString(R.string.deck_picker_failed_deck_load, deckId.toString())
        showThemedToast(this, message, false)
        Timber.w(message)
    }

    private val mDeckLongClickListener = OnLongClickListener { v ->
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Long tapped on deck with id %d", deckId)
        showDialogFragment(mContextMenuFactory.newDeckPickerContextMenu(deckId))
        true
    }

    private val mImportAddListener = ImportAddListener(this)

    private class ImportAddListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, String, ImporterData?>(deckPicker) {
        override fun actualOnPostExecute(context: DeckPicker, result: ImporterData?) {
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            // If result.errFlag and result are both set, we are signalling
            // some files were imported successfully & some errors occurred.
            // If result.impList is null & result.errList is set
            // we are signalling all the files which were selected threw error
            if (result!!.impList == null && result.errList != null) {
                Timber.w("Import: Add Failed: %s", result.errList)
                context.showSimpleMessageDialog(result.errList)
            } else {
                Timber.i("Import: Add succeeded")

                var fileCount = 0
                var totalCardCount = 0

                var errorMsg = ""

                for (data in result.impList!!) {
                    // Check if mLog is not null or empty
                    // If mLog is not null or empty that indicates an error has occurred.
                    if (data.log.isEmpty()) {
                        fileCount += 1
                        totalCardCount += data.cardCount
                    } else { errorMsg += data.fileName + "\n" + data.log[0] + "\n" }
                }

                var dialogMsg = context.resources.getQuantityString(R.plurals.import_complete_message, fileCount, fileCount, totalCardCount)
                if (result.errList != null) {
                    errorMsg += result.errList
                }
                if (errorMsg.isNotEmpty()) {
                    dialogMsg += "\n\n" + context.resources.getString(R.string.import_stats_error, errorMsg)
                }

                context.showSimpleMessageDialog(dialogMsg)
                context.updateDeckList()
            }
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
                context.mProgressDialog = StyledProgressDialog.show(
                    context,
                    context.resources.getString(R.string.import_title),
                    null,
                    false
                )
            }
        }

        override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
            @Suppress("Deprecation")
            context.mProgressDialog!!.setMessage(value)
        }
    }

    private fun importReplaceListener(): ImportReplaceListener {
        return ImportReplaceListener(this)
    }

    private class ImportReplaceListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, String, Computation<*>?>(deckPicker) {
        override fun actualOnPostExecute(context: DeckPicker, result: Computation<*>?) {
            Timber.i("Import: Replace Task Completed")
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
            val res = context.resources
            if (result!!.succeeded()) {
                context.updateDeckList()
            } else {
                context.showSimpleMessageDialog(res.getString(R.string.import_log_no_apkg), reload = true)
            }
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            if (context.mProgressDialog == null || !context.mProgressDialog!!.isShowing) {
                context.mProgressDialog = StyledProgressDialog.show(
                    context,
                    context.resources.getString(R.string.import_title),
                    context.resources.getString(R.string.import_replacing),
                    false
                )
            }
        }

        /**
         * @param value A message
         */
        override fun actualOnProgressUpdate(context: DeckPicker, value: String) {
            @Suppress("Deprecation")
            context.mProgressDialog!!.setMessage(value)
        }
    }

    // ----------------------------------------------------------------------------
    // ANDROID ACTIVITY METHODS
    // ----------------------------------------------------------------------------
    /** Called when the activity is first created.  */
    @Throws(SQLException::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        Timber.d("onCreate()")
        mExportingDelegate = ActivityExportingDelegate(this) { col }
        mCustomStudyDialogFactory = CustomStudyDialogFactory({ col }, this).attachToActivity(this)
        mContextMenuFactory = DeckPickerContextMenu.Factory { col }.attachToActivity(this)

        // Then set theme and content view
        super.onCreate(savedInstanceState)

        // handle the first load: display the app introduction
        if (!hasShownAppIntro()) {
            val appIntro = Intent(this, IntroductionActivity::class.java)
            appIntro.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityWithoutAnimation(appIntro)
            finish() // calls onDestroy() immediately
            return
        }
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            mSyncOnResume = true
        }

        setContentView(R.layout.homescreen)
        handleStartup()
        val mainView = findViewById<View>(android.R.id.content)

        // check, if tablet layout
        mStudyoptionsFrame = findViewById(R.id.studyoptions_fragment)
        // set protected variable from NavigationDrawerActivity
        fragmented = mStudyoptionsFrame != null && mStudyoptionsFrame!!.visibility == View.VISIBLE

        // Open StudyOptionsFragment if in fragmented mode
        if (fragmented && !mStartupError) {
            loadStudyOptionsFragment(false)
        }
        registerExternalStorageListener()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView)
        title = resources.getString(R.string.app_name)

        mDeckPickerContent = findViewById(R.id.deck_picker_content)
        recyclerView = findViewById(R.id.files)
        mNoDecksPlaceholder = findViewById(R.id.no_decks_placeholder)

        mDeckPickerContent.visibility = View.GONE
        mNoDecksPlaceholder.visibility = View.GONE

        // specify a LinearLayoutManager and set up item dividers for the RecyclerView
        mRecyclerViewLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = mRecyclerViewLayoutManager
        val ta = this.obtainStyledAttributes(intArrayOf(R.attr.deckDivider))
        val divider = ta.getDrawable(0)
        ta.recycle()
        val dividerDecorator = DividerItemDecoration(this, mRecyclerViewLayoutManager.orientation)
        dividerDecorator.setDrawable(divider!!)
        recyclerView.addItemDecoration(dividerDecorator)

        // Add background to Deckpicker activity
        val view = if (fragmented) findViewById(R.id.deckpicker_xl_view) else findViewById<View>(R.id.root_layout)

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
        mDeckListAdapter = DeckAdapter(layoutInflater, this).apply {
            setDeckClickListener(mDeckClickListener)
            setCountsClickListener(mCountsClickListener)
            setDeckExpanderClickListener(mDeckExpanderClickListener)
            setDeckLongClickListener(mDeckLongClickListener)
            enablePartialTransparencyForBackground(hasDeckPickerBackground)
        }
        recyclerView.adapter = mDeckListAdapter

        mPullToSyncWrapper = findViewById<SwipeRefreshLayout?>(R.id.pull_to_sync_wrapper).apply {
            setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
            setOnRefreshListener {
                Timber.i("Pull to Sync: Syncing")
                mPullToSyncWrapper.isRefreshing = false
                sync()
            }
            viewTreeObserver.addOnScrollChangedListener {
                mPullToSyncWrapper.isEnabled = mRecyclerViewLayoutManager.findFirstCompletelyVisibleItemPosition() == 0
            }
        }
        // Setup the FloatingActionButtons, should work everywhere with min API >= 15
        mFloatingActionMenu = DeckPickerFloatingActionMenu(this, view, this)

        mReviewSummaryTextView = findViewById(R.id.today_stats_text_view)

        mShortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        Onboarding.DeckPicker(this, mRecyclerViewLayoutManager).onCreate()
    }

    private fun hasShownAppIntro(): Boolean {
        val prefs = getSharedPrefs(this)

        // if moving from 2.15 to 2.16 then we do not want to show the intro
        // remove this after ~2.17 and default to 'false' if the pref is not set
        if (!prefs.contains(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN)) {
            return if (!InitialActivity.wasFreshInstall(prefs)) {
                prefs.edit { putBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, true) }
                true
            } else {
                false
            }
        }

        return prefs.getBoolean(IntroductionActivity.INTRODUCTION_SLIDES_SHOWN, false)
    }

    /**
     * The first call in showing dialogs for startup
     *
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     *
     * If the migration is in progress, it starts the service if not running
     *
     * See: #5304
     * @return true: Interrupt startup. `false`: continue as normal
     */
    open fun startingStorageMigrationInterruptsStartup(): Boolean {
        val migrationStatus = ScopedStorageService.migrationStatus(this)
        Timber.i("migration status: %s", migrationStatus)
        when (migrationStatus) {
            ScopedStorageService.Status.NEEDS_MIGRATION -> {
                // TODO: we should propose a migration, but not yet (alpha users should opt in)
                // If the migration was proposed too soon, don't show it again and startup normally.
                // TODO: This logic needs thought
                // showDialogThatOffersToMigrateStorage(onPostpone = {
                //     // Unblocks the UI if opened from changing the deck path
                //     updateDeckList()
                //     invalidateOptionsMenu()
                //     handleStartup(skipStorageMigration = true)
                // })
                return false // TODO: Allow startup normally
            }
            ScopedStorageService.Status.IN_PROGRESS -> {
                startMigrateUserDataService()
                return false
            }
            // If legacy storage is being used, ensure storage permission is obtained in order to access Legacy Directories
            ScopedStorageService.Status.REQUIRES_PERMISSION -> {
                requestStoragePermission()
                return true
            }
            ScopedStorageService.Status.PERMISSION_FAILED -> {
                // TODO: Handle "user reinstalled & kept data" scenario.
                //  (Or edge case of permission removal)
                return false
            }
            // App is already using Scoped Storage Directory for user data, no need to migrate & can proceed with startup
            ScopedStorageService.Status.COMPLETED -> return false
        }
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     */
    private fun handleStartup() {
        if (startingStorageMigrationInterruptsStartup()) return

        Timber.d("handleStartup: Continuing. unaffected by storage migration")
        if (hasStorageAccessPermission(this)) {
            val failure = InitialActivity.getStartupFailureType(this)
            mStartupError = if (failure == null) {
                // Show any necessary dialogs (e.g. changelog, special messages, etc)
                val sharedPrefs = getSharedPrefs(this)
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
    fun handleStartupFailure(failure: StartupFailure?) {
        when (failure) {
            SD_CARD_NOT_MOUNTED -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }
            DIRECTORY_NOT_ACCESSIBLE -> {
                Timber.i("AnkiDroid directory inaccessible")
                val i = AdvancedSettingsFragment.getSubscreenIntent(this)
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
            WEBVIEW_FAILED -> AlertDialog.Builder(this).show {
                title(R.string.ankidroid_init_failed_webview_title)
                message(
                    text = getString(
                        R.string.ankidroid_init_failed_webview,
                        AnkiDroidApp.webViewErrorMessage
                    )
                )
                positiveButton(R.string.close) {
                    exit()
                }
                    .setCancelable(false)
            }
            DISK_FULL -> displayNoStorageError()
            DB_ERROR -> displayDatabaseFailure()
            else -> displayDatabaseFailure()
        }
    }

    private fun displayDatabaseFailure() {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_LOAD_FAILED)
    }
    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_DISK_FULL)
    }

    // throws doesn't seem to be checked by the compiler - consider it to be documentation
    @Throws(OutOfMemoryError::class)
    private fun applyDeckPickerBackground(view: View): Boolean {
        // Allow the user to clear data and get back to a good state if they provide an invalid background.
        if (!getSharedPrefs(this).getBoolean("deckPickerBackground", false)) {
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
     * precondition: [hasStorageAccessPermission] should return false
     */
    private fun requestStoragePermission() {
        // DEFECT #5847: This fails if the activity is killed.
        // Even if the dialog is showing, we want to show it again.
        val storagePermissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, storagePermissions, REQUEST_STORAGE_PERMISSION)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        mFloatingActionMenu.closeFloatingActionMenu()
        menuInflater.inflate(R.menu.deck_picker, menu)
        setupSearchIcon(menu.findItem(R.id.deck_picker_action_filter))
        // redraw menu synchronously to avoid flicker
        updateMenuFromState(menu)
        // ...then launch a task to possibly update the visible icons.
        // Store the job so that tests can easily await it. In the future
        // this may be better done by injecting a custom test scheduler
        // into CollectionManager, and awaiting that.
        createMenuJob = launchCatchingTask {
            updateMenuState()
            updateMenuFromState(menu)
        }
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupSearchIcon(menuItem: MenuItem) {
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            // When SearchItem is expanded
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Timber.i("DeckPicker:: SearchItem opened")
                // Hide the floating action button if it is visible
                mFloatingActionMenu.hideFloatingActionButton()
                return true
            }

            // When SearchItem is collapsed
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Timber.i("DeckPicker:: SearchItem closed")
                // Show the floating action button if it is hidden
                mFloatingActionMenu.showFloatingActionButton()
                return true
            }
        })

        (menuItem.actionView as SearchView).run {
            queryHint = getString(R.string.search_decks)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    val adapter = recyclerView.adapter as Filterable?
                    adapter!!.filter.filter(newText)
                    return true
                }
            })
        }
        searchDecksIcon = menuItem
    }

    private fun updateMenuFromState(menu: Menu) {
        menu.setGroupVisible(R.id.allItems, optionsMenuState != null)
        optionsMenuState?.run {
            menu.findItem(R.id.deck_picker_action_filter).isVisible = searchIcon
            updateUndoIconFromState(menu.findItem(R.id.action_undo), undoIcon)
            updateSyncIconFromState(menu.findItem(R.id.action_sync), syncIcon)
            menu.findItem(R.id.action_scoped_storage_migrate).isVisible = offerToMigrate
        }
    }

    private fun updateUndoIconFromState(menuItem: MenuItem, undoTitle: String?) {
        menuItem.run {
            if (undoTitle != null) {
                isVisible = true
                title = resources.getString(R.string.studyoptions_congrats_undo, undoTitle)
            } else {
                isVisible = false
            }
        }
    }

    private fun updateSyncIconFromState(menuItem: MenuItem, syncIcon: SyncIconState) {
        menuItem.setTitle(
            when (syncIcon) {
                SyncIconState.Normal -> R.string.button_sync
                SyncIconState.PendingChanges -> R.string.button_sync
                SyncIconState.FullSync -> R.string.sync_menu_title_full_sync
                SyncIconState.NotLoggedIn -> R.string.sync_menu_title_no_account
                SyncIconState.Disabled -> R.string.button_sync_disabled
            }
        )
        when (syncIcon) {
            SyncIconState.Normal -> {
                BadgeDrawableBuilder.removeBadge(menuItem)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(this)
                    .withColor(ContextCompat.getColor(this@DeckPicker, R.color.badge_warning))
                    .replaceBadge(menuItem)
            }
            SyncIconState.FullSync, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder(this)
                    .withText('!')
                    .withColor(ContextCompat.getColor(this@DeckPicker, R.color.badge_error))
                    .replaceBadge(menuItem)
            }
            SyncIconState.Disabled -> {
                BadgeDrawableBuilder.removeBadge(menuItem)
                menuItem.setIcon(R.drawable.ic_sync_lock_24)
            }
        }
    }

    @VisibleForTesting
    suspend fun updateMenuState() {
        optionsMenuState = withOpenColOrNull {
            val searchIcon = decks.count() >= 10
            val undoIcon = undoName(resources).ifEmpty { null }
            val syncIcon = fetchSyncStatus(col)
            val offerToUpgrade = shouldOfferToUpgrade(context)
            OptionsMenuState(searchIcon, undoIcon, syncIcon, offerToUpgrade)
        }
    }

    private fun shouldOfferToUpgrade(context: Context): Boolean {
        if (!BuildConfig.ALLOW_UNSAFE_MIGRATION && !isLoggedIn()) {
            return false
        }
        return isLegacyStorage(context) && !userMigrationIsInProgress(context)
    }

    private fun fetchSyncStatus(col: Collection): SyncIconState {
        val auth = syncAuth()
        return when (SyncStatus.getSyncStatus(col, this, auth)) {
            SyncStatus.BADGE_DISABLED, SyncStatus.NO_CHANGES, SyncStatus.INCONCLUSIVE -> {
                SyncIconState.Normal
            }
            SyncStatus.ONGOING_MIGRATION -> {
                SyncIconState.Disabled
            }
            SyncStatus.HAS_CHANGES -> {
                SyncIconState.PendingChanges
            }
            SyncStatus.NO_ACCOUNT -> SyncIconState.NotLoggedIn
            SyncStatus.FULL_SYNC -> SyncIconState.FullSync
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        mFloatingActionMenu.closeFloatingActionMenu()

        if (drawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        when (item.itemId) {
            R.id.action_undo -> {
                Timber.i("DeckPicker:: Undo button pressed")
                undo()
                return true
            }
            R.id.deck_picker_action_filter -> {
                Timber.i("DeckPicker:: Search button pressed")
                return true
            }
            R.id.action_sync -> {
                Timber.i("DeckPicker:: Sync button pressed")
                sync()
                return true
            }
            R.id.action_scoped_storage_migrate -> {
                Timber.i("DeckPicker:: migrate button pressed")
                showDialogThatOffersToMigrateStorage()
                return true
            }
            R.id.action_import -> {
                Timber.i("DeckPicker:: Import button pressed")
                showDialogFragment(ImportFileSelectionFragment.createInstance(this))
                return true
            }
            R.id.action_new_filtered_deck -> {
                val createFilteredDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
                createFilteredDeckDialog.setOnNewDeckCreated {
                    // a filtered deck was created
                    openFilteredDeckOptions()
                }
                launchCatchingTask {
                    withProgress {
                        createFilteredDeckDialog.showFilteredDeckDialog()
                    }
                }
                return true
            }
            R.id.action_check_database -> {
                Timber.i("DeckPicker:: Check database button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }
            R.id.action_check_media -> {
                Timber.i("DeckPicker:: Check media button pressed")
                showMediaCheckDialog(MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK)
                return true
            }
            R.id.action_empty_cards -> {
                Timber.i("DeckPicker:: Empty cards button pressed")
                handleEmptyCards()
                return true
            }
            R.id.action_model_browser_open -> {
                Timber.i("DeckPicker:: Model browser button pressed")
                val manageNoteTypesTarget = if (!BackendFactory.defaultLegacySchema) {
                    ManageNotetypes::class.java
                } else {
                    ModelBrowser::class.java
                }
                val noteTypeBrowser = Intent(this, manageNoteTypesTarget)
                startActivityForResultWithAnimation(noteTypeBrowser, 0, START)
                return true
            }
            R.id.action_restore_backup -> {
                Timber.i("DeckPicker:: Restore from backup button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialog.DIALOG_CONFIRM_RESTORE_BACKUP)
                return true
            }
            R.id.action_export -> {
                Timber.i("DeckPicker:: Export collection button pressed")
                val msg = resources.getString(R.string.confirm_apkg_export)
                mExportingDelegate.showExportDialog(msg)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("deprecation") // onActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_MEDIA_EJECTED) {
            onSdCardNotMounted()
            return
        } else if (resultCode == RESULT_DB_ERROR) {
            handleDbError()
            return
        }
        if (requestCode == SHOW_INFO_NEW_VERSION) {
            showStartupScreensAndDialogs(getSharedPrefs(baseContext), 3)
        } else if (requestCode == LOG_IN_FOR_SYNC && resultCode == RESULT_OK) {
            mSyncOnResume = true
        } else if (requestCode == REQUEST_REVIEW || requestCode == SHOW_STUDYOPTIONS) {
            if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
                // Show a message when reviewing has finished
                if (col.sched.count() == 0) {
                    showSnackbar(R.string.studyoptions_congrats_finished)
                } else {
                    showSnackbar(R.string.studyoptions_no_cards_due)
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
            val importResult = ImportUtils.handleFileImport(this, data!!)
            if (!importResult.isSuccess) {
                ImportUtils.showImportUnsuccessfulDialog(this, importResult.humanReadableMessage, false)
            }
        } else if (requestCode == PICK_CSV_FILE && resultCode == RESULT_OK) {
            val path = ImportUtils.getFileCachedCopy(this, data!!) ?: return
            startActivity(CsvImporter.getIntent(this, path))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION && permissions.isNotEmpty()) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
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
        // Migration
    }

    fun refreshState() {
        mActivityPaused = false
        // Due to the App Introduction, this may be called before permission has been granted.
        if (mSyncOnResume && hasStorageAccessPermission(this)) {
            Timber.i("Performing Sync on Resume")
            sync()
            mSyncOnResume = false
        } else if (colIsOpen()) {
            selectNavigationItem(R.id.nav_decks)
            if (dueTree == null) {
                updateDeckList(true)
            }
            updateDeckList()
            title = resources.getString(R.string.app_name)
        }
        /* Complete task and enqueue fetching nonessential data for
          startup. */
        if (colIsOpen()) {
            launchCatchingTask { withCol { CollectionHelper.loadCollectionComplete(col) } }
        }
        // Update sync status (if we've come back from a screen)
        invalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean("mIsFABOpen", mFloatingActionMenu.isFABOpen)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mFloatingActionMenu.isFABOpen = savedInstanceState.getBoolean("mIsFABOpen")
    }

    fun onStorageMigrationCompleted() {
        migrationService.unbind(this)
        invalidateOptionsMenu() // reapply the sync icon
    }

    override fun onStart() {
        super.onStart()
        if (userMigrationIsInProgress(this)) {
            migrationService.bind(this, MigrationService::class.java)
        }
    }

    override fun onPause() {
        Timber.d("onPause()")
        mActivityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        loadDeckCounts?.cancel()
        super.onPause()
    }

    override fun onStop() {
        Timber.d("onStop()")
        super.onStop()
        migrationService.unbind(this)
        if (colIsOpen()) {
            WidgetStatus.update(this)
            // Ignore the modification - a change in deck shouldn't trigger the icon for "pending changes".
            saveCollectionInBackground(true)
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
        val preferences = getSharedPrefs(baseContext)

        // Check whether the option is selected, the user is signed in, last sync was AUTOMATIC_SYNC_TIME ago
        // (currently 10 minutes), and is not under a metered connection (if not allowed by preference)
        val lastSyncTime = preferences.getLong("lastSyncTime", 0)
        val autoSyncIsEnabled = preferences.getBoolean("automaticSyncMode", false)
        val syncIntervalPassed = TimeManager.time.intTimeMS() - lastSyncTime > AUTOMATIC_SYNC_MIN_INTERVAL
        val isNotBlockedByMeteredConnection = preferences.getBoolean(getString(R.string.metered_sync_key), false) || !isActiveNetworkMetered()

        if (isLoggedIn() && autoSyncIsEnabled && NetworkUtils.isOnline && syncIntervalPassed && isNotBlockedByMeteredConnection) {
            Timber.i("Triggering Automatic Sync")
            sync()
        }
    }

    @Suppress("DEPRECATION") // onBackPressed
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val preferences = getSharedPrefs(baseContext)
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (mFloatingActionMenu.isFABOpen) {
                mFloatingActionMenu.closeFloatingActionMenu()
            } else {
                if (!preferences.getBoolean("exitViaDoubleTapBack", false) || mBackButtonPressedToExit) {
                    automaticSync()
                    finishWithAnimation()
                } else {
                    showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT)
                }
                mBackButtonPressedToExit = true
                HandlerUtils.executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) { mBackButtonPressedToExit = false }
            }
        }
    }

    private fun finishWithAnimation() {
        super.finishWithAnimation(DEFAULT)
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
                handleDeckSelection(col.decks.selected(), DeckSelectionType.SKIP_STUDY_OPTIONS)
            }
            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Perform the following tasks:
     * Automatic backup
     * loadStudyOptionsFragment() if tablet
     * Automatic sync
     */
    private fun onFinishedStartup() {
        // create backup in background if needed
        if (BackendFactory.defaultLegacySchema) {
            BackupManager.performBackupInBackground(col.path, TimeManager.time)
        } else {
            // new code triggers backup in updateDeckList()
        }

        // Force a full sync if flag was set in upgrade path, asking the user to confirm if necessary
        if (mRecommendFullSync) {
            mRecommendFullSync = false
            try {
                col.modSchema()
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    @RustCleanup("make mDueTree a concrete DeckDueTreeNode")
    @Suppress("UNCHECKED_CAST")
    fun toggleDeckExpand(did: DeckId) {
        if (!col.decks.children(did).isEmpty()) {
            // update DB
            col.decks.collapse(did)
            // update stored state
            findInDeckTree(dueTree!! as List<TreeNode<DeckDueTreeNode>>, did)?.run {
                collapsed = !collapsed
            }
            renderPage()
            dismissAllDialogFragments()
        }
    }

    fun addNote() {
        val intent = Intent(this@DeckPicker, NoteEditor::class.java)
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        startActivityForResultWithAnimation(intent, ADD_NOTE, START)
    }

    private fun showStartupScreensAndDialogs(preferences: SharedPreferences, skip: Int) {
        // For Android 8/8.1 we want to use software rendering by default or the Reviewer UI is broken #7369
        if (sdkVersion == Build.VERSION_CODES.O ||
            sdkVersion == Build.VERSION_CODES.O_MR1
        ) {
            if (!preferences.contains("softwareRender")) {
                Timber.i("Android 8/8.1 detected with no render preference. Turning on software render.")
                preferences.edit { putBoolean("softwareRender", true) }
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
            preferences.edit { remove("noSpaceLeft") }
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
            val previous: Long = if (preferences.contains(UPGRADE_VERSION_KEY)) {
                // Upgrading currently installed app
                getPreviousVersion(preferences, current)
            } else {
                // Fresh install
                current
            }
            preferences.edit { putLong(UPGRADE_VERSION_KEY, current) }
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
                    val models = col.models
                    for (m in models.all()) {
                        val css = m.getString("css")
                        @Suppress("SpellCheckingInspection")
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
                showSnackbar("Invalid value for CHECK_DB_AT_VERSION")
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
                AlertDialog.Builder(this).show {
                    title(R.string.integrity_check_startup_title)
                    message(R.string.integrity_check_startup_content)
                    positiveButton(R.string.check_db) {
                        integrityCheck()
                    }
                    negativeButton(R.string.close) {
                        recreate()
                    }
                        .setCancelable(false)
                }
                return
            }
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                recreate()
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
                showSnackbar(ver, Snackbar.LENGTH_SHORT)
                showStartupScreensAndDialogs(preferences, 2)
            }
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
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

    private class UndoTaskListener(private val isReview: Boolean, deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, Unit, Computation<NextCard<*>>?>(deckPicker) {
        override fun actualOnCancelled(context: DeckPicker) {
            context.hideProgressBar()
        }

        override fun actualOnPreExecute(context: DeckPicker) {
            context.showProgressBar()
        }

        override fun actualOnPostExecute(context: DeckPicker, result: Computation<NextCard<*>>?) {
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
        fun legacyUndo() {
            val undoReviewString = resources.getString(R.string.undo_action_review)
            val isReview = undoReviewString == col.undoName(resources)
            Undo().runWithHandler(undoTaskListener(isReview))
        }
        if (BackendFactory.defaultLegacySchema) {
            legacyUndo()
        } else {
            launchCatchingTask {
                if (!backendUndoAndShowPopup()) {
                    legacyUndo()
                }
            }
        }
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(id: Int) {
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(id)
        showAsyncDialogFragment(newFragment)
    }

    override fun showMediaCheckDialog(dialogType: Int) {
        showAsyncDialogFragment(MediaCheckDialog.newInstance(dialogType))
    }

    override fun showMediaCheckDialog(dialogType: Int, checkList: MediaCheckResult) {
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
        showAsyncDialogFragment(newFragment, Channel.SYNC)
    }

    /**
     * Show simple error dialog with just the message and OK button. Reload the activity when dialog closed.
     */
    private fun showSyncErrorMessage(message: String?) {
        val title = resources.getString(R.string.sync_error)
        showSimpleMessageDialog(title = title, message = message, reload = true)
    }

    /**
     * Show a simple snackbar message or notification if the activity is not in foreground
     * @param messageResource String resource for message
     */
    fun showSyncLogMessage(@StringRes messageResource: Int, syncMessage: String?) {
        if (mActivityPaused) {
            val res = AnkiDroidApp.appResources
            showSimpleNotification(
                res.getString(R.string.app_name),
                res.getString(messageResource),
                Channel.SYNC
            )
        } else {
            if (syncMessage.isNullOrEmpty()) {
                if (messageResource == R.string.youre_offline && !Connection.allowLoginSyncOnNoConnection) {
                    // #6396 - Add a temporary "Try Anyway" button until we sort out `isOnline`
                    showSnackbar(messageResource) {
                        setAction(R.string.sync_even_if_offline) {
                            Connection.allowLoginSyncOnNoConnection = true
                            sync()
                        }
                    }
                } else {
                    showSnackbar(messageResource)
                }
            } else {
                val res = AnkiDroidApp.appResources
                showSimpleMessageDialog(title = res.getString(messageResource), message = syncMessage)
            }
        }
    }

    fun showImportDialog(id: Int, messageList: ArrayList<String>) {
        Timber.d("showImportDialog() delegating to ImportDialog")
        if (messageList.isEmpty()) {
            messageList.add("")
        }
        val newFragment: AsyncDialogFragment = ImportDialog.newInstance(id, messageList)
        showAsyncDialogFragment(newFragment)
    }

    fun onSdCardNotMounted() {
        showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finishWithoutAnimation()
    }

    // Callback method to submit error report
    fun sendErrorReport() {
        CrashReportService.sendExceptionReport(RuntimeException(), "DeckPicker.sendErrorReport")
    }

    // Callback method to handle repairing deck
    fun repairCollection() {
        Timber.i("Repairing the Collection")
        // TODO: doesn't work on null collection-only on non-openable(is this still relevant with withCol?)
        launchCatchingTask(resources.getString(R.string.deck_repair_error)) {
            Timber.d("doInBackgroundRepairCollection")
            val result = withProgress(resources.getString(R.string.backup_repair_deck_progress)) {
                withCol {
                    Timber.i("RepairCollection: Closing collection")
                    close(false)
                    BackupManager.repairCollection(this)
                }
            }
            if (!result) {
                showThemedToast(this@DeckPicker, resources.getString(R.string.deck_repair_error), true)
                showCollectionErrorDialog()
            }
        }
    }

    // Callback method to handle database integrity check
    override fun integrityCheck() {
        // #5852 - We were having issues with integrity checks where the users had run out of space.
        // display a dialog box if we don't have the space
        val status = CollectionIntegrityStorageCheck.createInstance(this)
        if (status.shouldWarnOnIntegrityCheck()) {
            Timber.d("Displaying File Size confirmation")
            AlertDialog.Builder(this).show {
                title(R.string.check_db_title)
                message(text = status.getWarningDetails(this@DeckPicker))
                positiveButton(R.string.integrity_check_continue_anyway) {
                    performIntegrityCheck()
                }
                negativeButton(R.string.dialog_cancel)
            }
        } else {
            performIntegrityCheck()
        }
    }

    private fun performIntegrityCheck() {
        Timber.i("performIntegrityCheck()")
        if (BackendFactory.defaultLegacySchema) {
            TaskManager.launchCollectionTask(CheckDatabase(), CheckDatabaseListener())
        } else {
            handleDatabaseCheck()
        }
    }

    /**
     * Schedules a background job to find missing, unused and invalid media files.
     * Shows a progress dialog while operation is running.
     * When check is finished a dialog box shows number of missing, unused and invalid media files.
     *
     * If has the storage permission, job is scheduled, otherwise storage permission is asked first.
     */
    override fun mediaCheck() {
        // TODO show to the user this message when failing
        @Suppress("UNUSED_VARIABLE")
        val failedCheck = getString(R.string.check_media_failed)
        if (hasStorageAccessPermission(this)) {
            launchCatchingTask {
                val result = withProgress(R.string.check_media_message) {
                    withCol { media.performFullCheck() }
                }
                showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, result)
            }
        } else {
            requestStoragePermission()
        }
    }

    override fun deleteUnused(unused: List<String>) {
        launchCatchingTask {
            // Number of deleted files
            val noOfDeletedFiles = withProgress(resources.getString(R.string.delete_media_message)) {
                withCol { deleteMedia(this, unused) }
            }
            showSimpleMessageDialog(
                title = resources.getString(R.string.delete_media_result_title),
                message = resources.getQuantityString(R.plurals.delete_media_result_message, noOfDeletedFiles, noOfDeletedFiles)
            )
        }
    }

    fun exit() {
        CollectionHelper.instance.closeCollection(false, "DeckPicker:exit()")
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
        if (BackendFactory.defaultLegacySchema) {
            importReplace(listOf(path))
        } else {
            importColpkg(path)
        }
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

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val preferences = getSharedPrefs(baseContext)

        if (userMigrationIsInProgress(this)) {
            warnNoSyncDuringMigration()
            return
        }

        val hkey = preferences.getString("hkey", "")
        if (hkey!!.isEmpty()) {
            Timber.w("User not logged in")
            mPullToSyncWrapper.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        fun shouldFetchMedia(): Boolean {
            val always = getString(R.string.sync_media_always_value)
            val onlyIfUnmetered = getString(R.string.sync_media_only_unmetered_value)
            val shouldFetchMedia = preferences.getString(getString(R.string.sync_fetch_media_key), always)
            return shouldFetchMedia == always ||
                (shouldFetchMedia == onlyIfUnmetered && !isActiveNetworkMetered())
        }

        /** Nested function that makes the connection to
         * the sync server and starts syncing the data */
        fun doSync() {
            if (!BackendFactory.defaultLegacySchema) {
                handleNewSync(conflict, shouldFetchMedia())
            } else {
                val data = arrayOf(hkey, shouldFetchMedia(), conflict, HostNumFactory.getInstance(baseContext))
                Connection.sync(mSyncListener, Connection.Payload(data))
            }
        }
        // Warn the user in case the connection is metered
        val meteredSyncIsAllowed = preferences.getBoolean(getString(R.string.metered_sync_key), false)
        if (!meteredSyncIsAllowed && isActiveNetworkMetered()) {
            AlertDialog.Builder(this).show {
                message(R.string.metered_sync_warning)
                positiveButton(R.string.dialog_continue) { doSync() }
                negativeButton(R.string.dialog_cancel)
            }
        } else {
            doSync()
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

        override fun onPreExecute() {
            mCountUp = 0
            mCountDown = 0
            val syncStartTime = TimeManager.time.intTimeMS()
            if (mProgressDialog == null || !mProgressDialog!!.isShowing) {
                try {
                    mProgressDialog = StyledProgressDialog.show(
                        this@DeckPicker,
                        resources.getString(R.string.sync_title),
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
                    if (keyCode == KeyEvent.KEYCODE_BACK && Connection.isCancellable &&
                        !Connection.isCancelled
                    ) {
                        // If less than 2s has elapsed since sync started then don't ask for confirmation
                        if (TimeManager.time.intTimeMS() - syncStartTime < 2000) {
                            Connection.cancel()
                            @Suppress("Deprecation")
                            mProgressDialog!!.setMessage(getString(R.string.sync_cancel_message))
                            return@setOnKeyListener true
                        }
                        // Show confirmation dialog to check if the user wants to cancel the sync
                        AlertDialog.Builder(mProgressDialog!!.context).show {
                            message(R.string.cancel_sync_confirm)
                                .setCancelable(false)
                            positiveButton(R.string.dialog_ok) {
                                @Suppress("Deprecation")
                                mProgressDialog!!.setMessage(getString(R.string.sync_cancel_message))
                                Connection.cancel()
                            }
                            negativeButton(R.string.continue_sync)
                        }
                        return@setOnKeyListener true
                    } else {
                        return@setOnKeyListener false
                    }
                }
            }

            // Store the current time so that we don't bother the user with a sync prompt for another 10 minutes
            // Note: getLs() in Libanki doesn't take into account the case when no changes were found, or sync cancelled
            getSharedPrefs(baseContext).edit { putLong("lastSyncTime", syncStartTime) }
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
                @Suppress("Deprecation")
                mProgressDialog!!.setMessage(
                    """
    $mCurrentMessage
    
                    """.trimIndent() +
                        res
                            .getString(R.string.sync_up_down_size, mCountUp / 1024, mCountDown / 1024)
                )
            }
        }

        override fun onPostExecute(data: Connection.Payload) {
            mPullToSyncWrapper.isRefreshing = false
            var dialogMessage: String? = ""
            Timber.d("Sync Listener onPostExecute()")
            val res = resources
            try {
                if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                    mProgressDialog!!.dismiss()
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Could not dismiss mProgressDialog. The Activity must have been destroyed while the AsyncTask was running")
                CrashReportService.sendExceptionReport(e, "DeckPicker.onPostExecute", "Could not dismiss mProgressDialog")
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
                            getSharedPrefs(baseContext).edit {
                                putString("username", "")
                                putString("hkey", "")
                            }
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
                            dialogMessage = when {
                                diff >= 86100 -> {
                                    // The difference if more than a day minus 5 minutes acceptable by ankiweb error
                                    res.getString(
                                        R.string.sync_log_clocks_unsynchronized,
                                        diff,
                                        res.getString(R.string.sync_log_clocks_unsynchronized_date)
                                    )
                                }
                                abs(diff % 3600.0 - 1800.0) >= 1500.0 -> {
                                    // The difference would be within limit if we adjusted the time by few hours
                                    // It doesn't work for all timezones, but it covers most and it's a guess anyway
                                    res.getString(
                                        R.string.sync_log_clocks_unsynchronized,
                                        diff,
                                        res.getString(R.string.sync_log_clocks_unsynchronized_tz)
                                    )
                                }
                                else -> {
                                    res.getString(R.string.sync_log_clocks_unsynchronized, diff, "")
                                }
                            }
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.FULL_SYNC -> if (col.isEmpty) {
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
                            dialogMessage = res.getString(R.string.sync_write_access_error_on_storage)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.FINISH_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_log_finish_error)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.CONNECTION_ERROR -> {
                            dialogMessage = res.getString(R.string.sync_connection_error)
                            if (result[0] is Exception) {
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
                            val url = if (result.isNotEmpty() && result[0] is CustomSyncServerUrlException) (result[0] as CustomSyncServerUrlException).url else "unknown"
                            dialogMessage = res.getString(R.string.sync_error_invalid_sync_server, url)
                            showSyncErrorMessage(joinSyncMessages(dialogMessage, syncMessage))
                        }
                        ConnectionResultType.NETWORK_ERROR -> {
                            showSnackbar(R.string.check_network) {
                                setAction(R.string.sync_even_if_offline) {
                                    Connection.allowLoginSyncOnNoConnection = true
                                    sync()
                                }
                            }
                        }
                        else -> {
                            if (result.isNotEmpty() && result[0] is Int) {
                                val code = result[0] as Int
                                dialogMessage = rewriteError(code)
                                if (dialogMessage == null) {
                                    dialogMessage = res.getString(
                                        R.string.sync_log_error_specific,
                                        code.toString(),
                                        result[1]
                                    )
                                }
                            } else {
                                dialogMessage = res.getString(R.string.sync_log_error_specific, (-1).toString(), resultType)
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
                    val message = res.getString(R.string.sync_database_acknowledge) + "\n\n" + data.data[2]
                    showSimpleMessageDialog(message)
                } else if (data.data.isNotEmpty() && data.data[0] is ConflictResolution) {
                    // A full sync occurred
                    when (data.data[0] as ConflictResolution) {
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
                invalidateOptionsMenu()
                updateDeckList()
                WidgetStatus.update(this@DeckPicker)
                if (fragmented) {
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
    @NeedsTest("Test 2 successful files & test 1 failure & 1 successful file")
    override fun importAdd(importPath: List<String>) {
        Timber.d("importAdd() for file %s", importPath)
        if (BackendFactory.defaultLegacySchema) {
            TaskManager.launchCollectionTask(ImportAdd(importPath), mImportAddListener)
        } else {
            importApkgs(importPath)
        }
    }

    // Callback to import a file -- replacing the existing collection
    @NeedsTest("Test 2 successful files & test 1 failure & 1 successful file")
    override fun importReplace(importPath: List<String>) {
        if (BackendFactory.defaultLegacySchema) {
            TaskManager.launchCollectionTask(ImportReplace(importPath), importReplaceListener())
        } else {
            // multiple colpkg files is nonsensical
            importColpkg(importPath[0])
        }
    }

    /**
     * Load a new studyOptionsFragment. If withDeckOptions is true, the deck options activity will
     * be loaded on top of it. Use this flag when creating a new filtered deck to allow the user to
     * modify the filter settings before being shown the fragment. The fragment itself will handle
     * rebuilding the deck if the settings change.
     */
    private fun loadStudyOptionsFragment(withDeckOptions: Boolean) {
        val details = StudyOptionsFragment.newInstance(withDeckOptions)
        supportFragmentManager.commit {
            replace(R.id.studyoptions_fragment, details)
        }
    }

    val fragment: StudyOptionsFragment?
        get() {
            val frag = supportFragmentManager.findFragmentById(R.id.studyoptions_fragment)
            return if (frag is StudyOptionsFragment) {
                frag
            } else {
                null
            }
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
                        recreate()
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

    private fun openFilteredDeckOptions() {
        val intent = Intent()
        intent.setClass(this, FilteredDeckOptions::class.java)
        startActivityWithAnimation(intent, START)
    }

    private fun openStudyOptions(withDeckOptions: Boolean) {
        if (fragmented) {
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
                if (fragmented) {
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

    private fun promptUserToUpdateScheduler() {
        AlertDialog.Builder(this).show {
            message(text = col.tr.schedulingUpdateRequired())
            positiveButton(R.string.dialog_ok) {
                launchCatchingTask {
                    if (!userAcceptsSchemaChange(col)) {
                        return@launchCatchingTask
                    }
                    withProgress { CollectionManager.updateScheduler() }
                    showThemedToast(this@DeckPicker, col.tr.schedulingUpdateDone(), false)
                    refreshState()
                }
            }
            negativeButton(R.string.dialog_cancel)
            if (AdaptionUtil.hasWebBrowser(this@DeckPicker)) {
                @Suppress("DEPRECATION")
                neutralButton(text = col.tr.schedulingUpdateMoreInfoButton()) {
                    this@DeckPicker.openUrl(Uri.parse("https://faqs.ankiweb.net/the-anki-2.1-scheduler.html#updating"))
                }
            }
        }
    }

    private fun handleDeckSelection(did: DeckId, selectionType: DeckSelectionType) {
        // Clear the undo history when selecting a new deck
        if (col.decks.selected() != did) {
            col.clearUndo()
        }
        if (col.get_config_int("schedVer") == 1) {
            promptUserToUpdateScheduler()
            return
        }
        // Select the deck
        col.decks.select(did)
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        // Reset the schedule so that we get the counts for the currently selected deck
        mFocusedDeck = did
        // Get some info about the deck to handle special cases
        val deckDueTreeNode = mDeckListAdapter.getNodeByDid(did)
        if (!deckDueTreeNode.value.shouldDisplayCounts() || deckDueTreeNode.value.knownToHaveRep()) {
            // If we don't yet have numbers, we trust the user that they knows what they opens, tries to open it.
            // If there is nothing to review, it'll come back to deck picker.
            openReviewerOrStudyOptions(selectionType)
            return
        }
        // There are numbers
        // Figure out what action to take
        if (col.sched.hasCardsTodayAfterStudyAheadLimit()) {
            // If there are cards due that can't be studied yet (due to the learn ahead limit) then go to study options
            openStudyOptions(false)
        } else if (col.sched.newDue() || col.sched.revDue()) {
            // If there are no cards to review because of the daily study limit then give "Study more" option
            showSnackbar(R.string.studyoptions_limit_reached) {
                addCallback(mSnackbarShowHideCallback)
                setAction(R.string.study_more) {
                    val d = mCustomStudyDialogFactory.newCustomStudyDialog().withArguments(
                        CustomStudyDialog.ContextMenuConfiguration.LIMITS,
                        col.decks.selected(),
                        true
                    )
                    showDialogFragment(d)
                }
            }

            // Check if we need to update the fragment or update the deck list. The same checks
            // are required for all snackbars below.
            if (fragmented) {
                // Tablets must always show the study options that corresponds to the current deck,
                // regardless of whether the deck is currently reviewable or not.
                openStudyOptions(false)
            } else {
                // On phones, we update the deck list to ensure the currently selected deck is
                // highlighted correctly.
                updateDeckList()
            }
        } else if (col.decks.isDyn(did)) {
            // Go to the study options screen if filtered deck with no cards to study
            openStudyOptions(false)
        } else if (!deckDueTreeNode.hasChildren() && col.isEmptyDeck(did)) {
            // If the deck is empty and has no children then show a message saying it's empty
            showSnackbar(R.string.empty_deck) {
                addCallback(mSnackbarShowHideCallback)
                setAction(R.string.menu_add) { addNote() }
            }

            if (fragmented) {
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        } else {
            // Otherwise say there are no cards scheduled to study, and give option to do custom study
            showSnackbar(R.string.studyoptions_empty_schedule) {
                addCallback(mSnackbarShowHideCallback)
                setAction(R.string.custom_study) {
                    val d = mCustomStudyDialogFactory.newCustomStudyDialog().withArguments(
                        CustomStudyDialog.ContextMenuConfiguration.EMPTY_SCHEDULE,
                        col.decks.selected(),
                        true
                    )
                    showDialogFragment(d)
                }
            }

            if (fragmented) {
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        }
    }

    /**
     * Scroll the deck list so that it is centered on the current deck.
     *
     * @param did The deck ID of the deck to select.
     */
    private fun scrollDecklistToDeck(did: DeckId) {
        val position = mDeckListAdapter.findDeckPosition(did)
        mRecyclerViewLayoutManager.scrollToPositionWithOffset(position, recyclerView.height / 2)
    }

    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, importing, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun updateDeckList() {
        updateDeckList(false)
    }

    @RustCleanup("backup with 5 minute timer, instead of deck list refresh")
    private fun updateDeckList(quick: Boolean) {
        if (!BackendFactory.defaultLegacySchema && Build.FINGERPRINT != "robolectric") {
            // uses user's desktop settings to determine whether a backup
            // actually happens
            performBackupInBackground()
        }
        if (quick) {
            launchCatchingTask {
                withProgress {
                    val decks: List<TreeNode<com.ichi2.libanki.sched.DeckTreeNode>> =
                        withCol { sched.quickDeckDueTree() }
                    onDecksLoaded(decks)
                }
            }
        } else {
            loadDeckCounts?.cancel()
            loadDeckCounts = launchCatchingTask {
                Timber.d("Refreshing deck list")
                withProgress {
                    Timber.d("doInBackgroundLoadDeckCounts")
                    val deckData = withCol { sched.deckDueTree(null) }
                    onDecksLoaded(deckData)
                }
            }
        }
    }

    private fun <T : AbstractDeckTreeNode> onDecksLoaded(result: List<TreeNode<T>>?) {
        Timber.i("Updating deck list UI")
        hideProgressBar()
        // Make sure the fragment is visible
        if (fragmented) {
            mStudyoptionsFrame!!.visibility = View.VISIBLE
        }
        if (result == null) {
            Timber.e("null result loading deck counts")
            showCollectionErrorDialog()
            return
        }
        @Suppress("UNCHECKED_CAST")
        dueTree = result as List<TreeNode<AbstractDeckTreeNode>>?
        renderPage()
        // Update the mini statistics bar as well
        launchCatchingTask {
            val reviewSummaryStatsSting = AnkiStatsTaskHandler.getReviewSummaryStatisticsString(this@DeckPicker)
            mReviewSummaryTextView.apply {
                text = reviewSummaryStatsSting ?: ""
                visibility = View.VISIBLE
                invalidate()
            }
        }
        Timber.d("Startup - Deck List UI Completed")
    }

    private fun renderPage() {
        if (dueTree == null) {
            // mDueTree may be set back to null when the activity restart.
            // We may need to recompute it.
            updateDeckList()
            return
        }

        // Check if default deck is the only available and there are no cards
        val isEmpty = dueTree!!.size == 1 && dueTree!![0].value.did == 1L && col.isEmpty
        if (animationDisabled()) {
            mDeckPickerContent.visibility = if (isEmpty) View.GONE else View.VISIBLE
            mNoDecksPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
        } else {
            val translation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics
            )
            val decksListShown = mDeckPickerContent.visibility == View.VISIBLE
            val placeholderShown = mNoDecksPlaceholder.visibility == View.VISIBLE
            if (isEmpty) {
                if (decksListShown) {
                    fadeOut(mDeckPickerContent, mShortAnimDuration, translation)
                }
                if (!placeholderShown) {
                    fadeIn(mNoDecksPlaceholder, mShortAnimDuration, translation).startDelay = if (decksListShown) mShortAnimDuration * 2.toLong() else 0.toLong()
                }
            } else {
                if (!decksListShown) {
                    fadeIn(mDeckPickerContent, mShortAnimDuration, translation).startDelay = if (placeholderShown) mShortAnimDuration * 2.toLong() else 0.toLong()
                }
                if (placeholderShown) {
                    fadeOut(mNoDecksPlaceholder, mShortAnimDuration, translation)
                }
            }
        }
        val currentFilter = if (mToolbarSearchView != null) mToolbarSearchView!!.query else null

        if (isEmpty) {
            if (supportActionBar != null) {
                supportActionBar!!.subtitle = null
            }
            if (mToolbarSearchView != null) {
                mDeckListAdapter.filter.filter(currentFilter)
            }
            // We're done here
            return
        }
        mDeckListAdapter.buildDeckList(dueTree!!, col, currentFilter)

        // Set the "x due in y minutes" subtitle
        try {
            val eta = mDeckListAdapter.eta
            val due = mDeckListAdapter.due
            val res = resources
            if (col.cardCount() != -1) {
                val time: String = if (eta != -1 && eta != null) {
                    Utils.timeQuantityTopDeckPicker(this, (eta * 60).toLong())
                } else {
                    "-"
                }
                if (due != null && supportActionBar != null) {
                    val subTitle: String = if (due == 0) {
                        res.getQuantityString(R.plurals.deckpicker_title_zero_due, col.cardCount(), col.cardCount())
                    } else {
                        res.getQuantityString(R.plurals.deckpicker_title, due, due, time)
                    }
                    supportActionBar!!.subtitle = subTitle
                }
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "RuntimeException setting time remaining")
        }
        val current = col.decks.current().optLong("id")
        if (mFocusedDeck != current) {
            scrollDecklistToDeck(current)
            mFocusedDeck = current
        }
    }

    // Callback to show study options for currently selected deck
    fun showContextMenuDeckOptions(did: DeckId) {
        // open deck options
        if (col.decks.isDyn(did)) {
            // open cram options if filtered deck
            val i = Intent(this@DeckPicker, FilteredDeckOptions::class.java)
            i.putExtra("did", did)
            startActivityWithAnimation(i, FADE)
        } else {
            // otherwise open regular options
            val intent = if (BackendFactory.defaultLegacySchema) {
                Intent(this@DeckPicker, DeckOptionsActivity::class.java).apply {
                    putExtra("did", did)
                }
            } else {
                com.ichi2.anki.pages.DeckOptions.getIntent(this, did)
            }
            startActivityWithAnimation(intent, FADE)
        }
    }

    fun exportDeck(did: DeckId) {
        val msg = resources.getString(R.string.confirm_apkg_export_deck, col.decks.get(did).getString("name"))
        mExportingDelegate.showExportDialog(msg, did)
    }

    fun createIcon(context: Context, did: DeckId) {
        // This code should not be reachable with lower versions
        val shortcut = ShortcutInfoCompat.Builder(this, did.toString())
            .setIntent(
                Intent(context, Reviewer::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra("deckId", did)
            )
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setShortLabel(Decks.basename(col.decks.name(did)))
            .setLongLabel(col.decks.name(did))
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

    /** Disables the shortcut of the deck and the children belonging to it.*/
    fun disableDeckAndChildrenShortcuts(did: DeckId) {
        val childDids = col.decks.childDids(did, col.decks.childMap()).map { it.toString() }
        val deckTreeDids = listOf(did.toString(), *childDids.toTypedArray())
        val errorMessage: CharSequence = getString(R.string.deck_shortcut_doesnt_exist)
        ShortcutManagerCompat.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        val currentName = col.decks.name(did)
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.rename_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
        createDeckDialog.deckName = currentName
        createDeckDialog.setOnNewDeckCreated {
            dismissAllDialogFragments()
            mDeckListAdapter.notifyDataSetChanged()
            updateDeckList()
            if (fragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    fun confirmDeckDeletion(did: DeckId): Job? {
        if (!BackendFactory.defaultLegacySchema) {
            dismissAllDialogFragments()
            // No confirmation required, as undoable
            return launchCatchingTask {
                val changes = withProgress {
                    undoableOp {
                        newDecks.removeDecks(listOf(did))
                    }
                }
                showSnackbar(TR.browsingCardsDeleted(changes.count))
            }
        }

        val res = resources
        if (!colIsOpen()) {
            return null
        }
        if (did == 1L) {
            showSnackbar(R.string.delete_deck_default_deck)
            dismissAllDialogFragments()
            return null
        }
        // Get the number of cards contained in this deck and its subdecks
        val cnt = DeckService.countCardsInDeckTree(col, did)
        val isDyn = col.decks.isDyn(did)
        // Delete empty decks without warning. Filtered decks save filters in the deck data, so require confirmation.
        if (cnt == 0 && !isDyn) {
            deleteDeck(did)
            dismissAllDialogFragments()
            return null
        }
        // Otherwise we show a warning and require confirmation
        val msg: String
        val deckName = "'" + col.decks.name(did) + "'"
        msg = if (isDyn) {
            res.getString(R.string.delete_cram_deck_message, deckName)
        } else {
            res.getQuantityString(R.plurals.delete_deck_message, cnt, deckName, cnt)
        }
        showDialogFragment(DeckPickerConfirmDeleteDeckDialog.newInstance(msg, did))
        return null
    }

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     * Use [.confirmDeckDeletion] for a confirmation dialog
     * @param did the deck to delete
     */
    fun deleteDeck(did: DeckId) {
        launchCatchingTask {
            // Flag to indicate if the deck being deleted is the current deck.
            val isRemovingCurrent = (did == col.decks.current().optLong("id"))
            withProgress(resources.getString(R.string.delete_deck)) {
                withCol {
                    Timber.d("doInBackgroundDeleteDeck")
                    col.decks.rem(did, true)
                    // TODO: if we had "undo delete note" like desktop client then we won't need this.
                    col.clearUndo()
                }
            }

            // After deleting a deck there is no more undo stack
            // Rebuild options menu with side effect of resetting undo button state
            invalidateOptionsMenu()

            // In fragmented mode, if the deleted deck was the current deck, we need to reload
            // the study options fragment with a valid deck and re-center the deck list to the
            // new current deck. Otherwise we just update the list normally.
            if (fragmented && isRemovingCurrent) {
                updateDeckList()
                openStudyOptions(false)
            } else {
                updateDeckList()
            }
        }
    }

    suspend fun rebuildFiltered(did: DeckId) {
        withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
            withCol {
                Timber.d("rebuildFiltered: doInBackground - RebuildCram")
                decks.select(did)
                sched.rebuildDyn(decks.selected())
                updateValuesFromDeck(this, true)
            }
            updateDeckList()
            if (fragmented) loadStudyOptionsFragment(false)
        }
    }

    fun emptyFiltered(did: DeckId) {
        col.decks.select(did)
        launchCatchingTask {
            withProgress {
                withCol {
                    Timber.d("doInBackgroundEmptyCram")
                    sched.emptyDyn(decks.selected())
                    updateValuesFromDeck(this, true)
                }
            }
            updateDeckList()
            if (fragmented) loadStudyOptionsFragment(false)
        }
    }

    override fun onAttachedToWindow() {
        if (!fragmented) {
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
        if (fragmented) {
            fragment!!.refreshInterface(true)
        }
        updateDeckList()
    }

    private fun handleEmptyCards() {
        mEmptyCardTask = TaskManager.launchCollectionTask(FindEmptyCards(), handlerEmptyCardListener())
    }

    private fun handlerEmptyCardListener(): HandleEmptyCardListener {
        return HandleEmptyCardListener(this)
    }

    private class HandleEmptyCardListener(deckPicker: DeckPicker) : TaskListenerWithContext<DeckPicker, Int, List<Long?>?>(deckPicker) {
        private val mNumberOfCards: Int = deckPicker.col.cardCount()
        private val mOnePercent: Int = mNumberOfCards / 100
        private var mIncreaseSinceLastUpdate = 0
        private fun confirmCancel(deckPicker: DeckPicker, task: Cancellable) {
            AlertDialog.Builder(deckPicker).show {
                message(R.string.confirm_cancel)
                positiveButton(R.string.dialog_yes) {
                    task.safeCancel()
                }
                negativeButton(R.string.dialog_no) {
                    actualOnPreExecute(deckPicker)
                }
            }
        }

        @KotlinCleanup("Material Progress Dialog")
        override fun actualOnPreExecute(context: DeckPicker) {
            val onCancel = DialogInterface.OnCancelListener { _: DialogInterface? ->
                val emptyCardTask = context.mEmptyCardTask
                emptyCardTask?.let { confirmCancel(context, it) }
            }
            @Suppress("Deprecation")
            context.mProgressDialog = android.app.ProgressDialog(context).apply {
                progress = mNumberOfCards
                setTitle(R.string.emtpy_cards_finding)
                setCancelable(true)
                show()
                setOnCancelListener(onCancel)
                setCanceledOnTouchOutside(false)
            }
        }

        override fun actualOnProgressUpdate(context: DeckPicker, value: Int) {
            mIncreaseSinceLastUpdate += value
            // Increase each time at least a percent of card has been processed since last update
            if (mIncreaseSinceLastUpdate > mOnePercent) {
                @Suppress("Deprecation")
                context.mProgressDialog!!.incrementProgressBy(mIncreaseSinceLastUpdate)
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
                dialog.setConfirm {
                    context.col.remCards(result.requireNoNulls())
                    val message = context.resources.getString(R.string.empty_cards_deleted, result.size)
                    context.showSnackbar(message)
                }
                context.showDialogFragment(dialog)
            }
            if (context.mProgressDialog != null && context.mProgressDialog!!.isShowing) {
                context.mProgressDialog!!.dismiss()
            }
        }
    }

    fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, did)
        createDeckDialog.setOnNewDeckCreated {
            // a deck was created
            dismissAllDialogFragments()
            mDeckListAdapter.notifyDataSetChanged()
            updateDeckList()
            if (fragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    /**
     * The number of decks which are visible to the user (excluding decks if the parent is collapsed).
     * Not the total number of decks
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val visibleDeckCount: Int
        get() = mDeckListAdapter.itemCount

    @VisibleForTesting
    internal inner class CheckDatabaseListener : TaskListener<String, Pair<Boolean, CheckDatabaseResult?>?>() {
        override fun onPreExecute() {
            mProgressDialog = StyledProgressDialog.show(
                this@DeckPicker,
                AnkiDroidApp.appResources.getString(R.string.app_name),
                resources.getString(R.string.check_db_message),
                false
            )
        }

        override fun onPostExecute(result: Pair<Boolean, CheckDatabaseResult?>?) {
            if (mProgressDialog != null && mProgressDialog!!.isShowing) {
                mProgressDialog!!.dismiss()
            }
            val databaseResult = result!!.second
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
            val shrunkInMb = (databaseResult.sizeChangeInKb / 1024.0).roundToLong()
            msg = if (shrunkInMb > 0.0) {
                resources.getString(R.string.check_db_acknowledge_shrunk, shrunkInMb.toInt())
            } else {
                resources.getString(R.string.check_db_acknowledge)
            }
            // Show result of database check and restart the app
            showSimpleMessageDialog(msg, reload = true)
        }

        /**
         * @param value message
         */
        override fun onProgressUpdate(value: String) {
            @Suppress("Deprecation")
            mProgressDialog!!.setMessage(value)
        }
    }

    /**
     * Check if at least one deck is being displayed.
     */
    fun hasAtLeastOneDeckBeingDisplayed(): Boolean {
        return mDeckListAdapter.itemCount > 0 && mRecyclerViewLayoutManager.getChildAt(0) != null
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
         * If passed into the intent, the user should have been logged in and DeckPicker
         * should sync immediately.
         *
         * This is for the 'download existing collection from AnkiWeb' use case
         */
        const val INTENT_SYNC_FROM_LOGIN = "syncFromLogin"

        /**
         * Available options performed by other activities (request codes for onActivityResult())
         */
        @VisibleForTesting
        const val REQUEST_STORAGE_PERMISSION = 0
        private const val REQUEST_PATH_UPDATE = 1
        private const val LOG_IN_FOR_SYNC = 6
        private const val SHOW_INFO_NEW_VERSION = 9
        const val SHOW_STUDYOPTIONS = 11
        private const val ADD_NOTE = 12
        const val PICK_APKG_FILE = 13
        const val PICK_CSV_FILE = 14

        // For automatic syncing
        // 10 minutes in milliseconds.
        const val AUTOMATIC_SYNC_MIN_INTERVAL: Long = 600000
        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400
        fun joinSyncMessages(dialogMessage: String?, syncMessage: String?): String? {
            // If both strings have text, separate them by a new line, otherwise return whichever has text
            return if (!dialogMessage.isNullOrEmpty() && !syncMessage.isNullOrEmpty()) {
                """
     $dialogMessage
     
     $syncMessage
                """.trimIndent()
            } else if (!dialogMessage.isNullOrEmpty()) {
                dialogMessage
            } else {
                syncMessage
            }
        }

        // Animation utility methods used by renderPage() method
        fun fadeIn(view: View?, duration: Int, translation: Float = 0f, startAction: Runnable? = Runnable { view!!.visibility = View.VISIBLE }): ViewPropertyAnimator {
            view!!.alpha = 0f
            view.translationY = translation
            return view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration.toLong())
                .withStartAction(startAction)
        }

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

    override fun opExecuted(changes: OpChanges, handler: Any?) {
        if (changes.studyQueues && handler !== this) {
            invalidateOptionsMenu()
            updateDeckList()
        }
    }

    /**
     * Do the whole migration.
     * Blocks the UI until essential files are migrated.
     * Change the preferences related to storage
     * Migrate the user data in a service
     */
    fun migrate() {
        if (userMigrationIsInProgress(this) || !isLegacyStorage(this)) {
            // This should not ever occurs.
            return
        }
        launchCatchingTask {
            val elapsedMillisDuringEssentialFilesMigration = measureTimeMillis {
                withProgress(getString(R.string.start_migration_progress_message)) {
                    withContext(Dispatchers.IO) {
                        loadDeckCounts?.cancel()
                        CollectionHelper.instance.closeCollection(false, "migration to scoped storage")
                        ScopedStorageService.migrateEssentialFiles(baseContext)

                        updateDeckList()
                        handleStartup()
                        startMigrateUserDataService()
                    }
                }
            }
            if (elapsedMillisDuringEssentialFilesMigration > 800) {
                showSnackbar(R.string.migration_part_1_done_resume)
            }
        }
    }

    /**
     * Start migrating the user data. Assumes that
     */
    private fun startMigrateUserDataService() {
        // TODO: Handle lack of disk space - most common error
        Timber.i("Starting Migrate User Data Service")
        migrationService.startForeground(this, MigrationService::class.java)
    }

    /**
     * Show a dialog that explains no sync can occur during migration.
     */
    private fun warnNoSyncDuringMigration() {
        // TODO: handle value updates
        // Note: migrationService shouldn't be null in normal operation
        val text = migrationService.instance?.let { service ->
            return@let service.totalToTransfer?.let { totalToTransfer ->
                "\n\n" + getString(R.string.migration_transferred_size, service.currentProgress.toMB().toFloat(), totalToTransfer.toMB().toFloat())
            }
        }
        // TODO: maybe handle onStorageMigrationCompleted()
        // TODO: sync_impossible_during_migration needs changing
        AlertDialog.Builder(this).show {
            message(text = resources.getString(R.string.sync_impossible_during_migration, 5) + text)
            positiveButton(R.string.dialog_ok)
            negativeButton(R.string.scoped_storage_learn_more) {
                openUrl(R.string.link_scoped_storage_faq)
            }
        }
    }

    /**
     * Last time the user had chosen to postpone migration. Or 0 if never.
     */
    private var migrationWasLastPostponedAt: Long
        get() = getSharedPrefs(baseContext).getLong(MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS, 0L)
        set(timeInSecond) = getSharedPrefs(baseContext)
            .edit { putLong(MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS, timeInSecond) }

    /**
     * Show a dialog offering to migrate, postpone or learn more.
     */
    private fun showDialogThatOffersToMigrateStorage() {
        Timber.i("Displaying dialog to migrate storage")
        if (userMigrationIsInProgress(baseContext)) {
            // This should not occur. We should have not called the function in this case.
            return
        }

        val ifYouUninstallMessageId = when {
            isLoggedIn() -> R.string.migration_warning_risk_of_data_loss_if_no_update
            else -> R.string.migration_update_request_dialog_download_warning
        }

        @Language("HTML")
        val message = """${getString(R.string.migration_update_request)}
            <br>
            <br>${getString(ifYouUninstallMessageId)}"""

        AlertDialog.Builder(this)
            .setTitle(R.string.scoped_storage_title)
            .setMessage(message)
            .setPositiveButton(
                getString(R.string.scoped_storage_migrate)
            ) { _, _ ->
                migrate()
            }
            .setNegativeButton(
                getString(R.string.scoped_storage_postpone)
            ) { _, _ ->
                setMigrationWasLastPostponedAtToNow()
            }.addScopedStorageLearnMoreLinkAndShow(message)
    }

    // Scoped Storage migration
    private fun setMigrationWasLastPostponedAtToNow() {
        migrationWasLastPostponedAt = TimeManager.time.intTime()
    }
}

/** Android's onCreateOptionsMenu does not play well with coroutines, as
 * it expects the menu to have been fully configured by the time the routine
 * returns. This results in flicker, as the menu gets blanked out, and then
 * configured a moment later when the coroutine runs. To work around this,
 * the current state is stored in the deck picker so that we can redraw the
 * menu immediately. */
data class OptionsMenuState(
    val searchIcon: Boolean,
    /** If undo is available, a string describing the action. */
    val undoIcon: String?,
    val syncIcon: SyncIconState,
    val offerToMigrate: Boolean
)

enum class SyncIconState {
    Normal,
    PendingChanges,
    FullSync,
    NotLoggedIn,

    /**
     * The icon should appear as disabled. Currently only occurs during scoped storage migration.
     */
    Disabled
}

/**
 * @param impList: List of packages to import
 * @param errList: a string describing the errors. Null if no error.
 */
data class ImporterData(val impList: List<AnkiPackageImporter>?, val errList: String?)
