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

import android.app.Activity
import android.content.*
import android.database.SQLException
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.util.TypedValue
import android.view.*
import android.view.View.OnLongClickListener
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.core.view.MenuItemCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import anki.sync.SyncStatusResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.*
import com.ichi2.anki.StudyOptionsFragment.StudyOptionsListener
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.deckpicker.BITMAP_BYTES_PER_PIXEL
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.dialogs.*
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.ImportDialog.ImportDialogListener
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.MediaCheckDialog.MediaCheckDialogListener
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialogFactory
import com.ichi2.anki.export.ActivityExportingDelegate
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.export.ExportDialogsFactory
import com.ichi2.anki.export.ExportDialogsFactoryProvider
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.notetype.ManageNotetypes
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.pages.CongratsPage.Companion.onDeckCompleted
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.servicelayer.*
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.servicelayer.ScopedStorageService.mediaMigrationIsInProgress
import com.ichi2.anki.services.MediaMigrationState
import com.ichi2.anki.services.MigrationService
import com.ichi2.anki.services.getMediaMigrationState
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.dialogs.storageMigrationFailedDialogIsShownOrPending
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.utils.SECONDS_PER_DAY
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.annotations.NeedsTest
import com.ichi2.async.*
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.libanki.*
import com.ichi2.libanki.exception.ConfirmModSchemaException
import com.ichi2.libanki.sched.DeckNode
import com.ichi2.libanki.utils.TimeManager
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.*
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import makeLinksClickable
import net.ankiweb.rsdroid.RustCleanup
import org.json.JSONException
import timber.log.Timber
import java.io.File
import java.lang.Runnable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

const val MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS = "secondWhenMigrationWasPostponedLast"
const val TIMES_STORAGE_MIGRATION_POSTPONED_KEY = "timesStorageMigrationPostponed"
const val OLDEST_WORKING_WEBVIEW_VERSION = 77

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
 * * Displaying a tree of decks, some of which may be collapsible: [deckListAdapter]
 *   * Allows users to study the decks
 *   * Displays deck progress
 *   * A long press opens a menu allowing modification of the deck
 *   * Filtering decks (if more than 10) [toolbarSearchView]
 * * Controlling syncs
 *   * A user may [pull down][pullToSyncWrapper] on the 'tree view' to sync
 *   * A [button][updateSyncIconFromState] which relies on [SyncStatus] to display whether a sync is needed
 *   * Blocks the UI and displays sync progress when syncing
 * * Displaying 'General' AnkiDroid options: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportDialogListener]
 * * A Floating Action Button [floatingActionMenu] allowing the user to quickly add notes/cards.
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
    ChangeManager.Subscriber,
    SyncCompletionListener,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider,
    CollectionPermissionScreenLauncher,
    ExportDialogsFactoryProvider {
    // Short animation duration from system
    private var shortAnimDuration = 0
    private var backButtonPressedToExit = false
    private lateinit var deckPickerContent: RelativeLayout

    @Suppress("Deprecation") // TODO: Encapsulate ProgressDialog within a class to limit the use of deprecated functionality
    var progressDialog: android.app.ProgressDialog? = null
    private var studyoptionsFrame: View? = null // not lateInit - can be null
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: LinearLayoutManager
    private lateinit var deckListAdapter: DeckAdapter
    lateinit var exportingDelegate: ActivityExportingDelegate
    private lateinit var noDecksPlaceholder: LinearLayout
    lateinit var pullToSyncWrapper: SwipeRefreshLayout
        private set

    private lateinit var reviewSummaryTextView: TextView

    @KotlinCleanup("make lateinit, but needs more changes")
    private var unmountReceiver: BroadcastReceiver? = null
    private lateinit var floatingActionMenu: DeckPickerFloatingActionMenu

    // flag asking user to do a full sync which is used in upgrade path
    private var recommendOneWaySync = false

    var activeSnackBar: Snackbar? = null
    private val activeSnackbarCallback = object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
        override fun onShown(transientBottomBar: Snackbar?) {
            activeSnackBar = transientBottomBar
        }

        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
            activeSnackBar = null
        }
    }
    override val baseSnackbarBuilder: SnackbarBuilder = {
        anchorView = findViewById<FloatingActionButton>(R.id.fab_main)
        addCallback(activeSnackbarCallback)
    }

    private var syncMediaProgressJob: Job? = null

    // flag keeping track of when the app has been paused
    var activityPaused = false
        private set

    // Flag to keep track of startup error
    private var startupError = false

    /** See [OptionsMenuState]. */
    @VisibleForTesting
    var optionsMenuState: OptionsMenuState? = null

    @VisibleForTesting
    var dueTree: DeckNode? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var searchDecksIcon: MenuItem? = null

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var syncOnResume = false

    /**
     * Keep track of which deck was last given focus in the deck list. If we find that this value
     * has changed between deck list refreshes, we need to recenter the deck list to the new current
     * deck.
     */
    @VisibleForTesting
    internal var focusedDeck: DeckId = 0

    var importColpkgListener: ImportColpkgListener? = null

    private var toolbarSearchItem: MenuItem? = null
    private var toolbarSearchView: SearchView? = null
    internal lateinit var customStudyDialogFactory: CustomStudyDialogFactory

    override val permissionScreenLauncher = recreateActivityResultLauncher()

    private val reviewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            processReviewResults(it.resultCode)
        }
    )

    private val showNewVersionInfoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            showStartupScreensAndDialogs(baseContext.sharedPrefs(), 3)
        }
    )

    private val loginForSyncLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                syncOnResume = true
            }
        }
    )

    private val requestPathUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            // The collection path was inaccessible on startup so just close the activity and let user restart
            finish()
        }
    )

    private val apkgFileImportResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                lifecycleScope.launch(Dispatchers.IO) {
                    onSelectedPackageToImport(it.data!!)
                }
            }
        }
    )

    private val csvImportResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        DeckPickerActivityResultCallback {
            if (it.resultCode == RESULT_OK) {
                onSelectedCsvForImport(it.data!!)
            }
        }
    )

    private inner class DeckPickerActivityResultCallback(private val callback: (result: ActivityResult) -> Unit) : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
            if (result.resultCode == RESULT_MEDIA_EJECTED) {
                onSdCardNotMounted()
                return
            } else if (result.resultCode == RESULT_DB_ERROR) {
                handleDbError()
                return
            }
            callback(result)
        }
    }

    private var migrateStorageAfterMediaSyncCompleted = false

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null
    private var loadDeckCounts: Job? = null

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------
    private val deckExpanderClickListener = View.OnClickListener { view: View ->
        launchCatchingTask { toggleDeckExpand(view.tag as Long) }
    }
    private val deckClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.DEFAULT) }
    private val countsClickListener = View.OnClickListener { v: View -> onDeckClick(v, DeckSelectionType.SHOW_STUDY_OPTIONS) }
    private fun onDeckClick(v: View, selectionType: DeckSelectionType) {
        val deckId = v.tag as Long
        Timber.i("DeckPicker:: Selected deck with id %d", deckId)
        launchCatchingTask {
            handleDeckSelection(deckId, selectionType)
            if (fragmented) {
                // Calling notifyDataSetChanged() will update the color of the selected deck.
                // This interferes with the ripple effect, so we don't do it if lollipop and not tablet view
                deckListAdapter.notifyDataSetChanged()
                updateDeckList()
            }
        }
    }

    private val deckLongClickListener = OnLongClickListener { v ->
        val deckId = v.tag as DeckId
        Timber.i("DeckPicker:: Long tapped on deck with id %d", deckId)
        launchCatchingTask {
            val (deckName, isDynamic, hasBuriedInDeck) = withCol {
                decks.select(deckId)
                Triple(
                    decks.name(deckId),
                    decks.isFiltered(deckId),
                    sched.haveBuried()
                )
            }
            updateDeckList() // focus has changed
            showDialogFragment(
                DeckPickerContextMenu.newInstance(
                    id = deckId,
                    name = deckName,
                    isDynamic = isDynamic,
                    hasBuriedInDeck = hasBuriedInDeck
                )
            )
        }
        true
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Timber.i("notification permission: %b", it)
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
        exportingDelegate = ActivityExportingDelegate(this) { getColUnsafe }
        customStudyDialogFactory = CustomStudyDialogFactory({ getColUnsafe }, this).attachToActivity(this)

        // Then set theme and content view
        super.onCreate(savedInstanceState)

        // handle the first load: display the app introduction
        if (!hasShownAppIntro()) {
            Timber.i("Displaying app intro")
            val appIntro = Intent(this, IntroductionActivity::class.java)
            appIntro.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(appIntro)
            finish() // calls onDestroy() immediately
            return
        } else {
            Timber.d("Not displaying app intro")
        }
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.d("launched from introduction activity login: syncing")
            syncOnResume = true
        }

        setContentView(R.layout.homescreen)
        handleStartup()
        val mainView = findViewById<View>(android.R.id.content)

        // check, if tablet layout
        studyoptionsFrame = findViewById(R.id.studyoptions_fragment)
        // set protected variable from NavigationDrawerActivity
        fragmented = studyoptionsFrame != null && studyoptionsFrame!!.visibility == View.VISIBLE

        // Open StudyOptionsFragment if in fragmented mode
        if (fragmented && !startupError) {
            loadStudyOptionsFragment(false)
        }
        registerExternalStorageListener()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer(mainView)
        title = resources.getString(R.string.app_name)

        deckPickerContent = findViewById(R.id.deck_picker_content)
        recyclerView = findViewById(R.id.files)
        noDecksPlaceholder = findViewById(R.id.no_decks_placeholder)

        deckPickerContent.visibility = View.GONE
        noDecksPlaceholder.visibility = View.GONE

        // specify a LinearLayoutManager and set up item dividers for the RecyclerView
        recyclerViewLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = recyclerViewLayoutManager
        val ta = this.obtainStyledAttributes(intArrayOf(R.attr.deckDivider))
        val divider = ta.getDrawable(0)
        ta.recycle()
        val dividerDecorator = DividerItemDecoration(this, recyclerViewLayoutManager.orientation)
        dividerDecorator.setDrawable(divider!!)
        recyclerView.addItemDecoration(dividerDecorator)

        // Add background to Deckpicker activity
        val view = if (fragmented) findViewById(R.id.deckpicker_xl_view) else findViewById<View>(R.id.root_layout)

        var hasDeckPickerBackground = false
        try {
            hasDeckPickerBackground = applyDeckPickerBackground()
        } catch (e: OutOfMemoryError) { // 6608 - OOM should be catchable here.
            Timber.w(e, "Failed to apply background - OOM")
            showThemedToast(this, getString(R.string.background_image_too_large), false)
        } catch (e: Exception) {
            Timber.w(e, "Failed to apply background")
            showThemedToast(this, getString(R.string.failed_to_apply_background_image, e.localizedMessage), false)
        }
        exportingDelegate.onRestoreInstanceState(savedInstanceState)

        // create and set an adapter for the RecyclerView
        deckListAdapter = DeckAdapter(layoutInflater, this).apply {
            setDeckClickListener(deckClickListener)
            setCountsClickListener(countsClickListener)
            setDeckExpanderClickListener(deckExpanderClickListener)
            setDeckLongClickListener(deckLongClickListener)
            enablePartialTransparencyForBackground(hasDeckPickerBackground)
        }
        recyclerView.adapter = deckListAdapter

        pullToSyncWrapper = findViewById<SwipeRefreshLayout?>(R.id.pull_to_sync_wrapper).apply {
            setDistanceToTriggerSync(SWIPE_TO_SYNC_TRIGGER_DISTANCE)
            setOnRefreshListener {
                Timber.i("Pull to Sync: Syncing")
                pullToSyncWrapper.isRefreshing = false
                sync()
            }
            viewTreeObserver.addOnScrollChangedListener {
                pullToSyncWrapper.isEnabled = recyclerViewLayoutManager.findFirstCompletelyVisibleItemPosition() == 0
            }
        }
        // Setup the FloatingActionButtons, should work everywhere with min API >= 15
        floatingActionMenu = DeckPickerFloatingActionMenu(this, view, this)

        reviewSummaryTextView = findViewById(R.id.today_stats_text_view)

        shortAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        Onboarding.DeckPicker(this, recyclerViewLayoutManager).onCreate()

        launchShowingHidingEssentialFileMigrationProgressDialog()
        if (BuildConfig.DEBUG) {
            checkWebviewVersion()
        }

        supportFragmentManager.setFragmentResultListener(DeckPickerContextMenu.REQUEST_KEY_CONTEXT_MENU, this) { requestKey, arguments ->
            when (requestKey) {
                DeckPickerContextMenu.REQUEST_KEY_CONTEXT_MENU -> handleContextMenuSelection(
                    arguments.getSerializableCompat<DeckPickerContextMenuOption>(DeckPickerContextMenu.CONTEXT_MENU_DECK_OPTION)
                        ?: error("Unable to retrieve selected context menu option"),
                    arguments.getLong(DeckPickerContextMenu.CONTEXT_MENU_DECK_ID, -1)
                )
                else -> error("Unexpected fragment result key! Did you forget to update DeckPicker?")
            }
        }
    }

    private fun handleContextMenuSelection(
        selectedOption: DeckPickerContextMenuOption,
        deckId: DeckId
    ) {
        when (selectedOption) {
            DeckPickerContextMenuOption.DELETE_DECK -> {
                Timber.i("ContextMenu: Delete deck selected")

                /* we can only disable the shortcut for now as it is restricted by Google https://issuetracker.google.com/issues/68949561?pli=1#comment4
                 * if fixed or given free hand to delete the shortcut with the help of API update this method and use the new one
                 */
                disableDeckAndChildrenShortcuts(deckId)
                confirmDeckDeletion(deckId)
            }
            DeckPickerContextMenuOption.DECK_OPTIONS -> {
                Timber.i("ContextMenu: Open deck options selected")
                showContextMenuDeckOptions(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY -> {
                Timber.i("ContextMenu: Custom study option selected")
                val d = FragmentFactoryUtils.instantiate(this, CustomStudyDialog::class.java)
                d.withArguments(CustomStudyDialog.ContextMenuConfiguration.STANDARD, deckId)
                showDialogFragment(d)
            }
            DeckPickerContextMenuOption.CREATE_SHORTCUT -> {
                Timber.i("ContextMenu: Create icon for a deck")
                createIcon(this, deckId)
            }
            DeckPickerContextMenuOption.RENAME_DECK -> {
                Timber.i("ContextMenu: Rename deck selected")
                renameDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EXPORT_DECK -> {
                Timber.i("ContextMenu: Export deck selected")
                exportDeck(deckId)
            }
            DeckPickerContextMenuOption.UNBURY -> {
                Timber.i("ContextMenu: Unbury deck selected")
                getColUnsafe.sched.unburyDeck(deckId)
                onRequireDeckListUpdate()
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD -> {
                Timber.i("ContextMenu: Rebuild deck selected")
                rebuildFiltered(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY -> {
                Timber.i("ContextMenu: Empty deck selected")
                emptyFiltered(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.CREATE_SUBDECK -> {
                Timber.i("ContextMenu: Create Subdeck selected")
                createSubDeckDialog(deckId)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.BROWSE_CARDS -> {
                Timber.i("ContextMenu: Browse cards")
                getColUnsafe.decks.select(deckId)
                AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository.lastDeckId = deckId
                val intent = Intent(this, CardBrowser::class.java)
                startActivity(intent)
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.ADD_CARD -> {
                Timber.i("ContextMenu: Add selected")
                getColUnsafe.decks.select(deckId)
                addNote()
                dismissAllDialogFragments()
            }
            DeckPickerContextMenuOption.EDIT_DESCRIPTION -> {
                Timber.i("Editing deck description for deck '%d'", deckId)
                showDialogFragment(EditDeckDescriptionDialog.newInstance(deckId))
            }
        }
    }

    /**
     * Check if the current WebView version is older than the last supported version and if it is,
     * inform the developer with a snackbar.
     */
    private fun checkWebviewVersion() {
        // Doesn't need to be translated as it's debug only
        val webviewPackageInfo = getAndroidSystemWebViewPackageInfo(packageManager)
        if (webviewPackageInfo == null) {
            val snackbarMessage = "No Android System WebView found"
            postSnackbar(snackbarMessage, Snackbar.LENGTH_INDEFINITE)
            return
        }

        val versionCode = webviewPackageInfo.versionName.split(".")[0].toInt()
        if (versionCode < OLDEST_WORKING_WEBVIEW_VERSION) {
            val snackbarMessage =
                "The WebView version $versionCode is outdated (<$OLDEST_WORKING_WEBVIEW_VERSION)."
            postSnackbar(snackbarMessage, Snackbar.LENGTH_INDEFINITE)
        }
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
     *
     * TODO BEFORE-RELEASE This always returns false.
     *   Investigate why and either fix the method or make it return Unit.
     */
    open fun startingStorageMigrationInterruptsStartup(): Boolean {
        val mediaMigrationState = getMediaMigrationState()
        Timber.i("migration status: %s", mediaMigrationState)
        when (mediaMigrationState) {
            is MediaMigrationState.NotOngoing.Needed -> {
                // TODO BEFORE-RELEASE we should propose a migration, but not yet (alpha users should opt in)
                //   If the migration was proposed too soon, don't show it again and startup normally.
                // TODO BEFORE-RELEASE This logic needs thought
                //   showDialogThatOffersToMigrateStorage(onPostpone = {
                //       // Unblocks the UI if opened from changing the deck path
                //       updateDeckList()
                //       invalidateOptionsMenu()
                //       handleStartup(skipStorageMigration = true)
                //   })
                return false // TODO BEFORE-RELEASE Allow startup normally
            }
            is MediaMigrationState.Ongoing.PausedDueToError -> {
                if (!storageMigrationFailedDialogIsShownOrPending(this)) {
                    showDialogThatOffersToResumeMigrationAfterError(mediaMigrationState.errorText)
                }
                return false
            }
            is MediaMigrationState.Ongoing.NotPaused -> {
                MigrationService.start(baseContext)
                return false
            }
            // App is already using Scoped Storage Directory for user data, no need to migrate & can proceed with startup
            is MediaMigrationState.NotOngoing.NotNeeded -> return false
        }
    }

    /**
     * The first call in showing dialogs for startup - error or success.
     * Attempts startup if storage permission has been acquired, else, it requests the permission
     *
     * TODO This method is run on every activity recreation, which can happen often.
     *   It seems that the original idea was for for this to only run once, on app start.
     *   This method triggers backups, sync, and may re-show dialogs
     *   that may have been dismissed. Make this run only once?
     */
    private fun handleStartup() {
        if (collectionPermissionScreenWasOpened()) {
            return
        }

        if (startingStorageMigrationInterruptsStartup()) return

        Timber.d("handleStartup: Continuing. unaffected by storage migration")
        val failure = InitialActivity.getStartupFailureType(this)
        startupError = if (failure == null) {
            // Show any necessary dialogs (e.g. changelog, special messages, etc)
            val sharedPrefs = this.sharedPrefs()
            showStartupScreensAndDialogs(sharedPrefs, 0)
            false
        } else {
            // Show error dialogs
            handleStartupFailure(failure)
            true
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
                if (ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(this)) {
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL)
                } else {
                    val i = AdvancedSettingsFragment.getSubscreenIntent(this)
                    requestPathUpdateLauncher.launch(i)
                    showThemedToast(this, R.string.directory_inaccessible, false)
                }
            }
            FUTURE_ANKIDROID_VERSION -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION)
            }
            DATABASE_LOCKED -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
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
                    closeCollectionAndFinish()
                }
                cancelable(false)
            }
            DISK_FULL -> displayNoStorageError()
            DB_ERROR -> displayDatabaseFailure()
            else -> displayDatabaseFailure()
        }
    }

    private fun displayDatabaseFailure() {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }
    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DISK_FULL)
    }

    // throws doesn't seem to be checked by the compiler - consider it to be documentation
    @Throws(OutOfMemoryError::class)
    private fun applyDeckPickerBackground(): Boolean {
        val backgroundView = findViewById<ImageView>(R.id.background)
        // Allow the user to clear data and get back to a good state if they provide an invalid background.
        if (!this.sharedPrefs().getBoolean("deckPickerBackground", false)) {
            Timber.d("No DeckPicker background preference")
            backgroundView.setBackgroundResource(0)
            return false
        }
        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)
        val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
        if (!imgFile.exists()) {
            Timber.d("No DeckPicker background image")
            backgroundView.setBackgroundResource(0)
            return false
        }

        // TODO: Temporary fix to stop a crash on startup [15450], it can be removed either:
        // * by moving this check to an upgrade path
        // * once enough time has passed
        val size = BackgroundImage.getBackgroundImageDimensions(this)
        if (size.width * size.height * BITMAP_BYTES_PER_PIXEL > BackgroundImage.MAX_BITMAP_SIZE) {
            Timber.w("DeckPicker background image dimensions too large")
            backgroundView.setBackgroundResource(0)
            return false
        }

        Timber.i("Applying background")
        val drawable = Drawable.createFromPath(imgFile.absolutePath)
        backgroundView.setImageDrawable(drawable)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        floatingActionMenu.closeFloatingActionMenu(applyRiseAndShrinkAnimation = false)
        menuInflater.inflate(R.menu.deck_picker, menu)
        menu.findItem(R.id.action_export)?.title = TR.exportingExport()
        setupMediaSyncMenuItem(menu)
        menu.findItem(R.id.deck_picker_action_filter)?.let {
            toolbarSearchItem = it
            setupSearchIcon(it)
            toolbarSearchView = it.actionView as SearchView
        }
        toolbarSearchView?.maxWidth = Integer.MAX_VALUE
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

    private var migrationProgressPublishingJob: Job? = null
    private var cachedMigrationProgressMenuItemActionView: View? = null

    private fun setupMediaSyncMenuItem(menu: Menu) {
        // shouldn't be necessary, but `invalidateOptionsMenu()` is called way more than necessary
        syncMediaProgressJob?.cancel()

        val syncItem = menu.findItem(R.id.action_sync)
        val progressIndicator = syncItem.actionView
            ?.findViewById<LinearProgressIndicator>(R.id.progress_indicator)

        val workManager = WorkManager.getInstance(this)
        val flow = workManager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)

        syncMediaProgressJob = lifecycleScope.launch {
            flow.flowWithLifecycle(lifecycle).collectLatest {
                val workInfo = it.lastOrNull()
                if (workInfo?.state == WorkInfo.State.RUNNING && progressIndicator?.isVisible == false) {
                    Timber.i("DeckPicker: Showing media sync progress indicator")
                    progressIndicator.isVisible = true
                } else if (progressIndicator?.isVisible == true) {
                    Timber.i("DeckPicker: Hiding media sync progress indicator")
                    progressIndicator.isVisible = false
                }
            }
        }
    }

    /**
     * Set up the menu item that shows circular progress of storage migration.
     * Can be called multiple times without harm.
     *
     * Note that, somewhat unconventionally, AnkiDroid will often call [invalidateOptionsMenu],
     * which results in [onCreateOptionsMenu] being called and the menu recreated from scratch,
     * with the state of individual menu items reset.
     *
     * This also means that the view showing progress can be swapped for another view at any time.
     * This is not a problem with [ProgressBar], but [CircularProgressIndicator],
     * which comes with sensible drawables out of the box--including rounded corners--
     * seems to be failing to draw right away when its `progress` is set, resulting in a flicker.
     *
     * To overcome these issues, we:
     *   * set visibility of the menu item on every call, and
     *   * cache and reuse the view that shows progress.
     *
     * TODO Investigate whether we can stop recreating the menu,
     *   relying instead on modifying it directly and/or using [onPrepareOptionsMenu].
     *   Note an issue with the latter: https://github.com/ankidroid/Anki-Android/issues/7755
     */
    private fun setupMigrationProgressMenuItem(menu: Menu, mediaMigrationState: MediaMigrationState) {
        val migrationProgressMenuItem = menu.findItem(R.id.action_migration_progress)
            .apply { isVisible = mediaMigrationState is MediaMigrationState.Ongoing.NotPaused }

        fun CircularProgressIndicator.publishProgress(progress: MigrationService.Progress.MovingMediaFiles) {
            when (progress) {
                is MigrationService.Progress.MovingMediaFiles.CalculatingNumberOfBytesToMove -> {
                    this.isIndeterminate = true
                }

                is MigrationService.Progress.MovingMediaFiles.MovingFiles -> {
                    this.isIndeterminate = false
                    this.progress = (progress.ratio * Int.MAX_VALUE).toInt()
                }
            }
        }

        if (mediaMigrationState is MediaMigrationState.Ongoing.NotPaused) {
            if (cachedMigrationProgressMenuItemActionView == null) {
                val actionView = migrationProgressMenuItem.actionView!!
                    .also { cachedMigrationProgressMenuItemActionView = it }

                val progressIndicator = actionView
                    .findViewById<CircularProgressIndicator>(R.id.progress_indicator)
                    .apply { max = Int.MAX_VALUE }

                actionView.findViewById<ImageButton>(R.id.button).also { button ->
                    button.setOnClickListener { warnNoSyncDuringMigration() }
                    TooltipCompat.setTooltipText(button, getText(R.string.show_migration_progress))
                }

                migrationProgressPublishingJob = lifecycleScope.launch {
                    MigrationService.flowOfProgress
                        .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                        .filterNotNull()
                        .collect { progress ->
                            when (progress) {
                                is MigrationService.Progress.CopyingEssentialFiles -> {
                                    // Button is not shown when transferring essential files
                                }

                                is MigrationService.Progress.MovingMediaFiles -> {
                                    progressIndicator.publishProgress(progress)
                                }

                                is MigrationService.Progress.Done -> {
                                    updateMenuState()
                                    updateMenuFromState(menu)
                                }
                            }
                        }
                }
            } else {
                migrationProgressMenuItem.actionView = cachedMigrationProgressMenuItemActionView
            }
        } else {
            cachedMigrationProgressMenuItemActionView = null

            migrationProgressPublishingJob?.cancel()
            migrationProgressPublishingJob = null
        }
    }

    private fun setupSearchIcon(menuItem: MenuItem) {
        menuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            // When SearchItem is expanded
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                Timber.i("DeckPicker:: SearchItem opened")
                // Hide the floating action button if it is visible
                floatingActionMenu.hideFloatingActionButton()
                return true
            }

            // When SearchItem is collapsed
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                Timber.i("DeckPicker:: SearchItem closed")
                // Show the floating action button if it is hidden
                floatingActionMenu.showFloatingActionButton()
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
            updateUndoLabelFromState(menu.findItem(R.id.action_undo), undoLabel)
            updateSyncIconFromState(menu.findItem(R.id.action_sync), this)
            menu.findItem(R.id.action_scoped_storage_migrate).isVisible = shouldShowStartMigrationButton
            setupMigrationProgressMenuItem(menu, mediaMigrationState)
        }
    }

    private fun updateUndoLabelFromState(menuItem: MenuItem, undoLabel: String?) {
        menuItem.run {
            if (undoLabel != null) {
                isVisible = true
                title = undoLabel
            } else {
                isVisible = false
            }
        }
    }

    private fun updateSyncIconFromState(menuItem: MenuItem, state: OptionsMenuState) {
        if (state.mediaMigrationState is MediaMigrationState.Ongoing) {
            menuItem.isVisible = false
            return
        }
        menuItem.isVisible = true

        val provider = MenuItemCompat.getActionProvider(menuItem) as? SyncActionProvider
            ?: return
        val tooltipText = when (state.syncIcon) {
            SyncIconState.Normal, SyncIconState.PendingChanges -> R.string.button_sync
            SyncIconState.OneWay -> R.string.sync_menu_title_one_way_sync
            SyncIconState.NotLoggedIn -> R.string.sync_menu_title_no_account
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            provider.setTooltipText(getString(tooltipText))
        }
        when (state.syncIcon) {
            SyncIconState.Normal -> {
                BadgeDrawableBuilder.removeBadge(provider)
            }
            SyncIconState.PendingChanges -> {
                BadgeDrawableBuilder(this)
                    .withColor(getColor(R.color.badge_warning))
                    .replaceBadge(provider)
            }
            SyncIconState.OneWay, SyncIconState.NotLoggedIn -> {
                BadgeDrawableBuilder(this)
                    .withText('!')
                    .withColor(getColor(R.color.badge_error))
                    .replaceBadge(provider)
            }
        }
    }

    @VisibleForTesting
    suspend fun updateMenuState() {
        optionsMenuState = withOpenColOrNull {
            val searchIcon = decks.count() >= 10
            val undoLabel = undoLabel()
            Pair(searchIcon, undoLabel)
        }?.let { (searchIcon, undoLabel) ->
            val syncIcon = fetchSyncStatus()
            val mediaMigrationState = getMediaMigrationState()
            val shouldShowStartMigrationButton = shouldOfferToMigrate() ||
                mediaMigrationState is MediaMigrationState.Ongoing.PausedDueToError
            OptionsMenuState(searchIcon, undoLabel, syncIcon, shouldShowStartMigrationButton, mediaMigrationState)
        }
    }

    // TODO BEFORE-RELEASE This doesn't offer to migrate data if not logged in.
    //   This should be changed so that we offer to migrate regardless.
    // TODO BEFORE-RELEASE Stop offering to migrate on every activity recreation.
    //   Currently the dialog re-appears if you dismiss it and then e.g. toggle device dark theme.
    private fun shouldOfferToMigrate(): Boolean {
        // ALLOW_UNSAFE_MIGRATION skips ensuring that the user is backed up to AnkiWeb
        if (!BuildConfig.ALLOW_UNSAFE_MIGRATION && !isLoggedIn()) {
            return false
        }
        return getMediaMigrationState() is MediaMigrationState.NotOngoing.Needed &&
            MigrationService.flowOfProgress.value !is MigrationService.Progress.Running
    }

    private suspend fun fetchSyncStatus(): SyncIconState {
        val auth = syncAuth()
        return when (SyncStatus.getSyncStatus(this, auth)) {
            SyncStatus.BADGE_DISABLED, SyncStatus.NO_CHANGES, SyncStatus.ERROR -> SyncIconState.Normal
            SyncStatus.HAS_CHANGES -> SyncIconState.PendingChanges
            SyncStatus.NO_ACCOUNT -> SyncIconState.NotLoggedIn
            SyncStatus.ONE_WAY -> SyncIconState.OneWay
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
                val actionProvider = MenuItemCompat.getActionProvider(item) as? SyncActionProvider
                if (actionProvider?.isProgressShown == true) {
                    launchCatchingTask {
                        monitorMediaSync(this@DeckPicker)
                    }
                } else {
                    sync()
                }
                return true
            }
            R.id.action_scoped_storage_migrate -> {
                Timber.i("DeckPicker:: migrate button pressed")
                val migrationState = getMediaMigrationState()
                if (migrationState is MediaMigrationState.Ongoing.PausedDueToError) {
                    showDialogThatOffersToResumeMigrationAfterError(migrationState.errorText)
                } else {
                    showDialogThatOffersToMigrateStorage(shownAutomatically = false)
                }
                return true
            }
            R.id.action_import -> {
                Timber.i("DeckPicker:: Import button pressed")
                showImportDialog()
                return true
            }
            R.id.action_check_database -> {
                Timber.i("DeckPicker:: Check database button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
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
                val manageNoteTypesTarget =
                    ManageNotetypes::class.java
                val noteTypeBrowser = Intent(this, manageNoteTypesTarget)
                startActivity(noteTypeBrowser)
                return true
            }
            R.id.action_restore_backup -> {
                Timber.i("DeckPicker:: Restore from backup button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                return true
            }
            R.id.action_export -> {
                Timber.i("DeckPicker:: Export menu item selected")
                if (mediaMigrationIsInProgress(this)) {
                    showSnackbar(R.string.functionality_disabled_during_storage_migration, Snackbar.LENGTH_SHORT)
                    return true
                }
                ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun createFilteredDialog() {
        val createFilteredDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
        createFilteredDeckDialog.onNewDeckCreated = {
            // a filtered deck was created
            openFilteredDeckOptions()
        }
        launchCatchingTask {
            withProgress {
                createFilteredDeckDialog.showFilteredDeckDialog()
            }
        }
    }

    override fun exportDialogsFactory(): ExportDialogsFactory = exportingDelegate.dialogsFactory

    fun exportCollection() {
        if (mediaMigrationIsInProgress(this)) {
            showSnackbar(R.string.functionality_disabled_during_storage_migration, Snackbar.LENGTH_SHORT)
            return
        }
        ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
    }

    private fun processReviewResults(resultCode: Int) {
        if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
            CongratsPage.onReviewsCompleted(this, getColUnsafe.sched.totalCount() == 0)
        } else if (resultCode == AbstractFlashcardViewer.RESULT_ABORT_AND_SYNC) {
            Timber.i("Obtained Abort and Sync result")
            sync()
        }
    }

    override fun onResume() {
        activityPaused = false
        // stop onResume() processing the message.
        // we need to process the message after `loadDeckCounts` is added in refreshState
        // As `loadDeckCounts` is cancelled in `migrate()`
        val message = dialogHandler.popMessage()
        super.onResume()
        if (navDrawerIsReady() && hasCollectionStoragePermissions()) {
            refreshState()
        }
        message?.let { dialogHandler.sendStoredMessage(it) }
    }

    fun refreshState() {
        // Due to the App Introduction, this may be called before permission has been granted.
        if (syncOnResume && hasCollectionStoragePermissions()) {
            Timber.i("Performing Sync on Resume")
            sync()
            syncOnResume = false
        } else {
            selectNavigationItem(R.id.nav_decks)
            updateDeckList()
            title = resources.getString(R.string.app_name)
        }
        // Update sync status (if we've come back from a screen)
        invalidateOptionsMenu()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("mIsFABOpen", floatingActionMenu.isFABOpen)
        outState.putBoolean("migrateStorageAfterMediaSyncCompleted", migrateStorageAfterMediaSyncCompleted)
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.getString("dbRestorationPath", it.newAnkiDroidDirectory)
            }
        }
        exportingDelegate.onSaveInstanceState(outState)
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
        floatingActionMenu.showFloatingActionButton()
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        floatingActionMenu.isFABOpen = savedInstanceState.getBoolean("mIsFABOpen")
        migrateStorageAfterMediaSyncCompleted = savedInstanceState.getBoolean("migrateStorageAfterMediaSyncCompleted")
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
    }

    override fun onPause() {
        activityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        loadDeckCounts?.cancel()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        WidgetStatus.updateInBackground(this@DeckPicker)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (unmountReceiver != null) {
            unregisterReceiver(unmountReceiver)
        }
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
    }

    private suspend fun automaticSync(runInBackground: Boolean = false) {
        /**
         * @return whether there are collection changes to be sync.
         *
         * It DOES NOT include if there are media to be synced.
         */
        suspend fun areThereChangesToSync(): Boolean {
            val auth = syncAuth() ?: return false
            val status = withContext(Dispatchers.IO) {
                CollectionManager.getBackend().syncStatus(auth)
            }.required

            return when (status) {
                SyncStatusResponse.Required.NO_CHANGES,
                SyncStatusResponse.Required.UNRECOGNIZED,
                null -> false
                SyncStatusResponse.Required.FULL_SYNC,
                SyncStatusResponse.Required.NORMAL_SYNC -> true
            }
        }

        fun syncIntervalPassed(): Boolean {
            val lastSyncTime = sharedPrefs().getLong("lastSyncTime", 0)
            val automaticSyncIntervalInMS = AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES * 60 * 1000
            return TimeManager.time.intTimeMS() - lastSyncTime > automaticSyncIntervalInMS
        }

        val isAutoSyncEnabled = sharedPrefs().getBoolean("automaticSyncMode", false)

        val isBlockedByMeteredConnection = !sharedPrefs().getBoolean(getString(R.string.metered_sync_key), false) &&
            isActiveNetworkMetered()

        when {
            !isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
            isBlockedByMeteredConnection -> Timber.d("autoSync: blocked by metered connection")
            !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
            !runInBackground && !syncIntervalPassed() -> Timber.d("autoSync: interval not passed")
            !isLoggedIn() -> Timber.d("autoSync: not logged in")
            mediaMigrationIsInProgress(this) -> Timber.d("autoSync: migrating storage")
            !areThereChangesToSync() -> {
                Timber.d("autoSync: no collection changes to sync. Syncing media if set")
                if (shouldFetchMedia(sharedPrefs())) {
                    val auth = syncAuth() ?: return
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }
            else -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return
                    SyncWorker.start(this, auth, shouldFetchMedia(sharedPrefs()))
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val preferences = baseContext.sharedPrefs()
        if (isDrawerOpen) {
            super.onBackPressed()
        } else {
            Timber.i("Back key pressed")
            if (floatingActionMenu.isFABOpen) {
                floatingActionMenu.closeFloatingActionMenu(applyRiseAndShrinkAnimation = true)
            } else {
                if (!preferences.getBoolean(
                        "exitViaDoubleTapBack",
                        false
                    ) || backButtonPressedToExit
                ) {
                    // can't use launchCatchingTask because any errors
                    // would need to be shown in the UI
                    lifecycleScope.launch {
                        automaticSync(runInBackground = true)
                    }.invokeOnCompletion {
                        finish()
                    }
                } else {
                    showSnackbar(R.string.back_pressed_once, Snackbar.LENGTH_SHORT)
                }
                backButtonPressedToExit = true
                HandlerUtils.executeFunctionWithDelay(Consts.SHORT_TOAST_DURATION) {
                    backButtonPressedToExit = false
                }
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (toolbarSearchView != null && toolbarSearchView!!.hasFocus()) {
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
            KeyEvent.KEYCODE_SLASH -> {
                Timber.d("Search from keypress")
                if (toolbarSearchItem?.isVisible == true) {
                    toolbarSearchItem?.expandActionView()
                }
            }
            KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                launchCatchingTask {
                    handleDeckSelection(getColUnsafe.decks.selected(), DeckSelectionType.SKIP_STUDY_OPTIONS)
                }
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
        launchCatchingTask {
            val shownBackupDialog = BackupPromptDialog.showIfAvailable(this@DeckPicker)
            if (
                !shownBackupDialog &&
                shouldOfferToMigrate() &&
                timeToShowStorageMigrationDialog() &&
                !storageMigrationFailedDialogIsShownOrPending(this@DeckPicker)
            ) {
                showDialogThatOffersToMigrateStorage(shownAutomatically = true)
            }
        }

        // Force a one-way sync if flag was set in upgrade path, asking the user to confirm if necessary
        if (recommendOneWaySync) {
            recommendOneWaySync = false
            try {
                getColUnsafe.modSchema()
            } catch (e: ConfirmModSchemaException) {
                Timber.w("Forcing one-way sync")
                e.log()
                // If libanki determines it's necessary to confirm the one-way sync then show a confirmation dialog
                // We have to show the dialog via the DialogHandler since this method is called via an async task
                val res = resources
                val message = """
     ${res.getString(R.string.full_sync_confirmation_upgrade)}
     
     ${res.getString(R.string.full_sync_confirmation)}
                """.trimIndent()

                dialogHandler.sendMessage(OneWaySyncDialog(message).toMessage())
            }
        }
        launchCatchingTask {
            automaticSync()
        }
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendMessage(CollectionLoadingErrorDialog().toMessage())
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun toggleDeckExpand(did: DeckId) {
        // update DB
        getColUnsafe.decks.collapse(did)
        // update stored state
        dueTree?.find(did)?.run {
            collapsed = !collapsed
        }
        renderPage(getColUnsafe.isEmpty)
        dismissAllDialogFragments()
    }

    fun addNote() {
        val intent = Intent(this@DeckPicker, NoteEditor::class.java)
        intent.putExtra(NoteEditor.EXTRA_CALLER, NoteEditor.CALLER_DECKPICKER)
        startActivity(intent)
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
                recommendOneWaySync = true
            }

            // Fix "font-family" definition in templates created by AnkiDroid before 2.6alpha23
            if (previous < 20600123) {
                Timber.i("Fixing font-family definition in templates")
                try {
                    val models = getColUnsafe.notetypes
                    for (m in models.all()) {
                        val css = m.getString("css")
                        @Suppress("SpellCheckingInspection")
                        if (css.contains("font-familiy")) {
                            m.put("css", css.replace("font-familiy", "font-family"))
                            models.save(m)
                        }
                    }
                } catch (e: JSONException) {
                    Timber.e(e, "Failed to upgrade css definitions.")
                }
            }

            // Check if preference upgrade or database check required, otherwise go to new feature screen
            val upgradeDbVersion = AnkiDroidApp.CHECK_DB_AT_VERSION

            // Specifying a checkpoint in the future is not supported, please don't do it!
            if (current < upgradeDbVersion) {
                Timber.e("Invalid value for CHECK_DB_AT_VERSION")
                postSnackbar("Invalid value for CHECK_DB_AT_VERSION")
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
                        ActivityCompat.recreate(this@DeckPicker)
                    }
                    cancelable(false)
                }
                return
            }
            if (upgradedPreferences) {
                Timber.i("Updated preferences with no integrity check - restarting activity")
                // If integrityCheck() doesn't occur, but we did update preferences we should restart DeckPicker to
                // proceed
                ActivityCompat.recreate(this)
                return
            }

            // If no changes are required we go to the new features activity
            // There the "lastVersion" is set, so that this code is not reached again
            if (VersionUtils.isReleaseVersion) {
                Timber.i("Displaying new features")
                val infoIntent = Intent(this, Info::class.java)
                infoIntent.putExtra(Info.TYPE_EXTRA, Info.TYPE_NEW_VERSION)
                showNewVersionInfoLauncher.launch(infoIntent)
            } else {
                Timber.i("Dev Build - not showing 'new features'")
                // Don't show new features dialog for development builds
                InitialActivity.setUpgradedToLatestVersion(preferences)
                val ver = resources.getString(R.string.updated_version, VersionUtils.pkgVersionName)
                postSnackbar(ver, Snackbar.LENGTH_SHORT)
                showStartupScreensAndDialogs(preferences, 2)
            }
        } else {
            // This is the main call when there is nothing special required
            Timber.i("No startup screens required")
            onFinishedStartup()
        }
    }

    // #16061. We have to queue snackbar to avoid the misaligned snackbar showed from onCreate()
    private fun postSnackbar(
        text: CharSequence,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        val view: View? = findViewById(R.id.root_layout)
        if (view != null) {
            view.post {
                showSnackbar(text, duration)
            }
        } else {
            showSnackbar(text, duration)
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

    private fun undo() {
        launchCatchingTask {
            undoAndShowSnackbar()
        }
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(errorDialogType: DatabaseErrorDialogType) {
        if (errorDialogType == DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK && mediaMigrationIsInProgress(this)) {
            showSnackbar(R.string.functionality_disabled_during_storage_migration, Snackbar.LENGTH_SHORT)
            return
        }
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(errorDialogType)
        showAsyncDialogFragment(newFragment)
    }

    override fun showMediaCheckDialog(dialogType: Int) {
        if (dialogType == MediaCheckDialog.DIALOG_CONFIRM_MEDIA_CHECK && mediaMigrationIsInProgress(this)) {
            showSnackbar(R.string.functionality_disabled_during_storage_migration, Snackbar.LENGTH_SHORT)
            return
        }
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

    fun onSdCardNotMounted() {
        showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finish()
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
                    close()
                    BackupManager.repairCollection()
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
        if (mediaMigrationIsInProgress(this)) {
            // The only path which can still display this is a sync error, which shouldn't be possible
            showSnackbar(R.string.functionality_disabled_during_storage_migration, Snackbar.LENGTH_SHORT)
            return
        }

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
        handleDatabaseCheck()
    }

    /**
     * Schedules a background job to find missing, unused and invalid media files.
     * Shows a progress dialog while operation is running.
     * When check is finished a dialog box shows number of missing, unused and invalid media files.
     *
     * If has the storage permission, job is scheduled, otherwise storage permission is asked first.
     */
    override fun mediaCheck() {
        launchCatchingTask {
            val mediaCheckResult = checkMedia() ?: return@launchCatchingTask
            showMediaCheckDialog(MediaCheckDialog.DIALOG_MEDIA_CHECK_RESULTS, mediaCheckResult)
        }
    }

    override fun deleteUnused(unused: List<String>) {
        launchCatchingTask {
            // Number of deleted files
            val noOfDeletedFiles = withProgress(resources.getString(R.string.delete_media_message)) {
                withCol { deleteMedia(unused) }
            }
            showSimpleMessageDialog(
                title = resources.getString(R.string.delete_media_result_title),
                message = resources.getQuantityString(R.plurals.delete_media_result_message, noOfDeletedFiles, noOfDeletedFiles)
            )
        }
    }

    open fun handleDbError() {
        Timber.i("Displaying Database Error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    open fun handleDbLocked() {
        Timber.i("Displaying Database Locked")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
    }

    fun restoreFromBackup(path: String) {
        importColpkg(path)
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

    /** In the conflict case, we need to store the USN received from the initial sync, and reuse
     it after the user has decided. */
    var mediaUsnOnConflict: Int? = null

    /**
     * The mother of all syncing attempts. This might be called from sync() as first attempt to sync a collection OR
     * from the mSyncConflictResolutionListener if the first attempt determines that a full-sync is required.
     */
    override fun sync(conflict: ConflictResolution?) {
        val preferences = baseContext.sharedPrefs()

        if (!canSync(this)) {
            warnNoSyncDuringMigration()
            return
        }

        val hkey = preferences.getString("hkey", "")
        if (hkey!!.isEmpty()) {
            Timber.w("User not logged in")
            pullToSyncWrapper.isRefreshing = false
            showSyncErrorDialog(SyncErrorDialog.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        MyAccount.checkNotificationPermission(this, notificationPermissionLauncher)

        /** Nested function that makes the connection to
         * the sync server and starts syncing the data */
        fun doSync() {
            handleNewSync(conflict, shouldFetchMedia(preferences))
        }
        // Warn the user in case the connection is metered
        val meteredSyncIsAllowed =
            preferences.getBoolean(getString(R.string.metered_sync_key), false)
        if (!meteredSyncIsAllowed && isActiveNetworkMetered()) {
            AlertDialog.Builder(this).show {
                message(R.string.metered_sync_data_warning)
                positiveButton(R.string.dialog_continue) { doSync() }
                negativeButton(R.string.dialog_cancel)
                checkBoxPrompt(R.string.button_do_not_show_again) { isCheckboxChecked ->
                    preferences.edit {
                        putBoolean(
                            getString(R.string.metered_sync_key),
                            isCheckboxChecked
                        )
                    }
                }
            }
        } else {
            doSync()
        }
    }

    override fun loginToSyncServer() {
        val myAccount = Intent(this, MyAccount::class.java)
        myAccount.putExtra("notLoggedIn", true)
        loginForSyncLauncher.launch(myAccount)
    }

    // Callback to import a file -- adding it to existing collection
    override fun importAdd(importPath: String) {
        Timber.d("importAdd() for file %s", importPath)
        startActivity(AnkiPackageImporterFragment.getIntent(this, importPath))
    }

    // Callback to import a file -- replacing the existing collection
    override fun importReplace(importPath: String) {
        Timber.d("importReplace() for file %s", importPath)
        importColpkg(importPath)
    }

    /**
     * Load a new studyOptionsFragment. If withDeckOptions is true, the deck options activity will
     * be loaded on top of it. Use this flag when creating a new filtered deck to allow the user to
     * modify the filter settings before being shown the fragment. The fragment itself will handle
     * rebuilding the deck if the settings change.
     */
    fun loadStudyOptionsFragment(withDeckOptions: Boolean) {
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
        if (unmountReceiver == null) {
            unmountReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == SdCardReceiver.MEDIA_EJECT) {
                        onSdCardNotMounted()
                    } else if (intent.action == SdCardReceiver.MEDIA_MOUNT) {
                        ActivityCompat.recreate(this@DeckPicker)
                    }
                }
            }
            val iFilter = IntentFilter()
            iFilter.addAction(SdCardReceiver.MEDIA_EJECT)
            iFilter.addAction(SdCardReceiver.MEDIA_MOUNT)
            registerReceiver(unmountReceiver, iFilter)
        }
    }

    fun openAnkiWebSharedDecks() {
        val intent = Intent(this, SharedDecksActivity::class.java)
        startActivity(intent)
    }

    private fun openFilteredDeckOptions() {
        val intent = Intent()
        intent.setClass(this, FilteredDeckOptions::class.java)
        startActivity(intent)
    }

    internal fun openStudyOptions(@Suppress("SameParameterValue") withDeckOptions: Boolean) {
        if (fragmented) {
            // The fragment will show the study options screen instead of launching a new activity.
            loadStudyOptionsFragment(withDeckOptions)
        } else {
            val intent = Intent()
            intent.putExtra("withDeckOptions", withDeckOptions)
            intent.setClass(this, StudyOptionsActivity::class.java)
            reviewLauncher.launch(intent)
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
            message(text = getColUnsafe.tr.schedulingUpdateRequired())
            positiveButton(R.string.dialog_ok) {
                launchCatchingTask {
                    if (!userAcceptsSchemaChange(getColUnsafe)) {
                        return@launchCatchingTask
                    }
                    withProgress { withCol { sched.upgradeToV2() } }
                    showThemedToast(this@DeckPicker, getColUnsafe.tr.schedulingUpdateDone(), false)
                    refreshState()
                }
            }
            negativeButton(R.string.dialog_cancel)
            if (AdaptionUtil.hasWebBrowser(this@DeckPicker)) {
                neutralButton(text = getColUnsafe.tr.schedulingUpdateMoreInfoButton()) {
                    this@DeckPicker.openUrl(Uri.parse("https://faqs.ankiweb.net/the-anki-2.1-scheduler.html#updating"))
                }
            }
        }
    }

    @NeedsTest("14608: Ensure that the deck options refer to the selected deck")
    private suspend fun handleDeckSelection(did: DeckId, selectionType: DeckSelectionType) {
        fun showEmptyDeckSnackbar() = showSnackbar(R.string.empty_deck) {
            setAction(R.string.menu_add) { addNote() }
        }

        /** Check if we need to update the fragment or update the deck list */
        fun updateUi() {
            if (fragmented) {
                // Tablets must always show the study options that corresponds to the current deck,
                // regardless of whether the deck is currently reviewable or not.
                openStudyOptions(withDeckOptions = false)
            } else {
                // On phones, we update the deck list to ensure the currently selected deck is
                // highlighted correctly.
                updateDeckList()
            }
        }

        if (withCol { ((config.get("schedVer") ?: 1L) == 1L) }) {
            promptUserToUpdateScheduler()
            return
        }

        withCol { decks.select(did) }
        // Also forget the last deck used by the Browser
        CardBrowser.clearLastDeckId()
        focusedDeck = did
        val deck = deckListAdapter.getNodeByDid(did)
        if (deck.hasCardsReadyToStudy()) {
            openReviewerOrStudyOptions(selectionType)
            return
        }

        when (queryCompletedDeckCustomStudyAction(did)) {
            CompletedDeckStatus.LEARN_AHEAD_LIMIT_REACHED,
            CompletedDeckStatus.REGULAR_DECK_NO_MORE_CARDS_TODAY,
            CompletedDeckStatus.DYNAMIC_DECK_NO_LIMITS_REACHED,
            CompletedDeckStatus.DAILY_STUDY_LIMIT_REACHED -> {
                onDeckCompleted()
            }
            CompletedDeckStatus.EMPTY_REGULAR_DECK -> {
                // If the deck is empty (& has no children) then show a message saying it's empty
                showEmptyDeckSnackbar()
                updateUi()
            }
        }
    }

    /**
     * Scroll the deck list so that it is centered on the current deck.
     *
     * @param did The deck ID of the deck to select.
     */
    private fun scrollDecklistToDeck(did: DeckId) {
        val position = deckListAdapter.findDeckPosition(did)
        recyclerViewLayoutManager.scrollToPositionWithOffset(position, recyclerView.height / 2)
    }

    /**
     * Launch an asynchronous task to rebuild the deck list and recalculate the deck counts. Use this
     * after any change to a deck (e.g., rename, importing, add/delete) that needs to be reflected
     * in the deck list.
     *
     * This method also triggers an update for the widget to reflect the newly calculated counts.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    @RustCleanup("backup with 5 minute timer, instead of deck list refresh")
    fun updateDeckList() {
        if (!CollectionManager.isOpenUnsafe()) {
            return
        }
        if (Build.FINGERPRINT != "robolectric") {
            // uses user's desktop settings to determine whether a backup
            // actually happens
            performBackupInBackground()
        }
        Timber.d("updateDeckList")
        loadDeckCounts?.cancel()
        loadDeckCounts = launchCatchingTask {
            withProgress {
                Timber.d("Refreshing deck list")
                val deckData = withCol {
                    Pair(sched.deckDueTree(), this.isEmpty)
                }
                onDecksLoaded(deckData.first, deckData.second)
            }
        }
    }

    private fun onDecksLoaded(result: DeckNode, collectionIsEmpty: Boolean) {
        Timber.i("Updating deck list UI")
        hideProgressBar()
        // Make sure the fragment is visible
        if (fragmented) {
            studyoptionsFrame!!.visibility = View.VISIBLE
        }
        dueTree = result
        launchCatchingTask { renderPage(collectionIsEmpty) }
        // Update the mini statistics bar as well
        reviewSummaryTextView.setSingleLine()
        launchCatchingTask {
            reviewSummaryTextView.text = withCol {
                sched.studiedToday()
            }
        }
        Timber.d("Startup - Deck List UI Completed")
    }

    private suspend fun renderPage(collectionIsEmpty: Boolean) {
        val tree = dueTree
        if (tree == null) {
            // mDueTree may be set back to null when the activity restart.
            // We may need to recompute it.
            Timber.d("renderPage: recomputing dueTree")
            updateDeckList()
            return
        }

        // Check if default deck is the only available and there are no cards
        val isEmpty = tree.children.size == 1 && tree.children[0].did == 1L && collectionIsEmpty
        if (animationDisabled()) {
            deckPickerContent.visibility = if (isEmpty) View.GONE else View.VISIBLE
            noDecksPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
        } else {
            val translation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                resources.displayMetrics
            )
            val decksListShown = deckPickerContent.visibility == View.VISIBLE
            val placeholderShown = noDecksPlaceholder.visibility == View.VISIBLE
            if (isEmpty) {
                if (decksListShown) {
                    fadeOut(deckPickerContent, shortAnimDuration, translation)
                }
                if (!placeholderShown) {
                    fadeIn(noDecksPlaceholder, shortAnimDuration, translation).startDelay = if (decksListShown) shortAnimDuration * 2.toLong() else 0.toLong()
                }
            } else {
                if (!decksListShown) {
                    fadeIn(deckPickerContent, shortAnimDuration, translation).startDelay = if (placeholderShown) shortAnimDuration * 2.toLong() else 0.toLong()
                }
                if (placeholderShown) {
                    fadeOut(noDecksPlaceholder, shortAnimDuration, translation)
                }
            }
        }
        val currentFilter = if (toolbarSearchView != null) toolbarSearchView!!.query else null

        if (isEmpty) {
            if (supportActionBar != null) {
                supportActionBar!!.subtitle = null
            }
            if (toolbarSearchView != null) {
                deckListAdapter.filter?.filter(currentFilter)
            }
            Timber.d("Not rendering deck list as there are no cards")
            // We're done here
            return
        }
        deckListAdapter.buildDeckList(tree, currentFilter)

        // Set the "x due" subtitle
        try {
            val due = deckListAdapter.due
            val res = resources

            if (due != null && supportActionBar != null) {
                val cardCount = withCol { cardCount() }
                val subTitle: String = if (due == 0) {
                    res.getQuantityString(R.plurals.deckpicker_title_zero_due, cardCount, cardCount)
                } else {
                    res.getQuantityString(R.plurals.widget_cards_due, due, due)
                }
                supportActionBar!!.subtitle = subTitle
            }
        } catch (e: RuntimeException) {
            Timber.e(e, "RuntimeException setting time remaining")
        }
        val current = withCol { decks.current().optLong("id") }
        if (focusedDeck != current) {
            scrollDecklistToDeck(current)
            focusedDeck = current
        }
    }

    // Callback to show study options for currently selected deck
    fun showContextMenuDeckOptions(did: DeckId) {
        // open deck options
        if (getColUnsafe.decks.isFiltered(did)) {
            // open cram options if filtered deck
            val i = Intent(this@DeckPicker, FilteredDeckOptions::class.java)
            i.putExtra("did", did)
            startActivity(i)
        } else {
            // otherwise open regular options
            val intent = com.ichi2.anki.pages.DeckOptions.getIntent(this, did)
            startActivity(intent)
        }
    }

    fun exportDeck(did: DeckId) {
        ExportDialogFragment.newInstance(did).show(supportFragmentManager, "exportOptions")
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
            .setShortLabel(Decks.basename(getColUnsafe.decks.name(did)))
            .setLongLabel(getColUnsafe.decks.name(did))
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
        val childDids = dueTree?.find(did)?.filterAndFlatten(null)?.map { it.did.toString() } ?: listOf()
        val deckTreeDids = listOf(did.toString(), *childDids.toTypedArray())
        val errorMessage: CharSequence = getString(R.string.deck_shortcut_doesnt_exist)
        ShortcutManagerCompat.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        val currentName = getColUnsafe.decks.name(did)
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.rename_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
        createDeckDialog.deckName = currentName
        createDeckDialog.onNewDeckCreated = {
            dismissAllDialogFragments()
            deckListAdapter.notifyDataSetChanged()
            updateDeckList()
            if (fragmented) {
                loadStudyOptionsFragment(false)
            }
        }
        createDeckDialog.showDialog()
    }

    fun confirmDeckDeletion(did: DeckId): Job {
        // No confirmation required, as undoable
        dismissAllDialogFragments()
        return deleteDeck(did)
    }

    /**
     * Deletes the provided deck, child decks. and all cards inside.
     * Use [.confirmDeckDeletion] for a confirmation dialog
     * @param did the deck to delete
     */
    fun deleteDeck(did: DeckId): Job {
        return launchCatchingTask {
            val changes = withProgress(resources.getString(R.string.delete_deck)) {
                undoableOp {
                    decks.remove(listOf(did))
                }
            }
            // After deletion: decks.current() reverts to Default, necessitating `focusedDeck`
            // to match and avoid unnecessary scrolls in `renderPage()`.
            focusedDeck = Consts.DEFAULT_DECK_ID
            showSnackbar(TR.browsingCardsDeleted(changes.count), Snackbar.LENGTH_SHORT) {
                setAction(R.string.undo) { undo() }
            }
        }
    }

    @NeedsTest("14285: regression test to ensure UI is updated after this call")
    fun rebuildFiltered(did: DeckId) {
        launchCatchingTask {
            withProgress(resources.getString(R.string.rebuild_filtered_deck)) {
                withCol {
                    Timber.d("rebuildFiltered: doInBackground - RebuildCram")
                    decks.select(did)
                    sched.rebuildDyn(decks.selected())
                    updateValuesFromDeck()
                }
            }
            updateDeckList()
            if (fragmented) loadStudyOptionsFragment(false)
        }
    }

    fun emptyFiltered(did: DeckId) {
        getColUnsafe.decks.select(did)
        launchCatchingTask {
            withProgress {
                withCol {
                    Timber.d("doInBackgroundEmptyCram")
                    sched.emptyDyn(decks.selected())
                    updateValuesFromDeck()
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
        val intent = if (sharedPrefs().getBoolean("newReviewer", false)) {
            ReviewerFragment.getIntent(this)
        } else {
            Intent(this, Reviewer::class.java)
        }
        reviewLauncher.launch(intent)
    }

    override fun onCreateCustomStudySession() {
        updateDeckList()
        openStudyOptions(false)
    }

    override fun onExtendStudyLimits() {
        if (fragmented) {
            fragment!!.refreshInterface()
        }
        updateDeckList()
    }

    private fun handleEmptyCards() {
        launchCatchingTask {
            val emptyCids = withProgress(R.string.emtpy_cards_finding) {
                withCol {
                    emptyCids()
                }
            }
            AlertDialog.Builder(this@DeckPicker).show {
                setTitle(TR.emptyCardsWindowTitle())
                if (emptyCids.isEmpty()) {
                    setMessage(TR.emptyCardsNotFound())
                    setPositiveButton(R.string.dialog_ok) { _, _ -> }
                } else {
                    setMessage(getString(R.string.empty_cards_count, emptyCids.size))
                    setPositiveButton(R.string.dialog_positive_delete) { _, _ ->
                        launchCatchingTask {
                            withProgress(TR.emptyCardsDeleting()) {
                                withCol { removeCardsAndOrphanedNotes(emptyCids) }
                            }
                        }
                        showSnackbar(getString(R.string.empty_cards_deleted, emptyCids.size))
                    }
                    setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                }
            }
        }
    }

    fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog = CreateDeckDialog(this@DeckPicker, R.string.create_subdeck, CreateDeckDialog.DeckDialogType.SUB_DECK, did)
        createDeckDialog.onNewDeckCreated = {
            // a deck was created
            dismissAllDialogFragments()
            deckListAdapter.notifyDataSetChanged()
            updateDeckList()
            if (fragmented) {
                loadStudyOptionsFragment(false)
            }
            invalidateOptionsMenu()
        }
        createDeckDialog.showDialog()
    }

    /**
     * The number of decks which are visible to the user (excluding decks if the parent is collapsed).
     * Not the total number of decks
     */
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    val visibleDeckCount: Int
        get() = deckListAdapter.itemCount

    /**
     * Check if at least one deck is being displayed.
     */
    fun hasAtLeastOneDeckBeingDisplayed(): Boolean {
        return deckListAdapter.itemCount > 0 && recyclerViewLayoutManager.getChildAt(0) != null
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

        // For automatic syncing
        // 10 minutes in milliseconds..
        private const val AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES: Long = 10
        private const val SWIPE_TO_SYNC_TRIGGER_DISTANCE = 400

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
        migrateStorageAfterMediaSyncCompleted = false

        if (mediaMigrationIsInProgress(this) || !isLegacyStorage(this)) {
            // This should not ever occurs.
            return
        }

        if (activityPaused) {
            sendNotificationForAsyncOperation(MigrateStorageOnSyncSuccess(this.resources), Channel.SYNC)
            return
        }

        loadDeckCounts?.cancel()

        MigrationService.start(baseContext)
    }

    private fun launchShowingHidingEssentialFileMigrationProgressDialog() = lifecycleScope.launch {
        while (true) {
            MigrationService.flowOfProgress
                .first { it is MigrationService.Progress.CopyingEssentialFiles }

            val (progress, duration) = measureTimedValue {
                withImmediatelyShownProgress(R.string.start_migration_progress_message) {
                    MigrationService.flowOfProgress
                        .first { it !is MigrationService.Progress.CopyingEssentialFiles }
                }
            }

            if (progress is MigrationService.Progress.MovingMediaFiles && duration > 800.milliseconds) {
                showSnackbar(R.string.migration_part_1_done_resume)
            }

            refreshState()
            updateDeckList()
        }
    }

    /**
     * Show a dialog that explains no sync can occur during migration.
     */
    private fun warnNoSyncDuringMigration() {
        MigrationProgressDialogFragment().show(supportFragmentManager, "MigrationProgressDialogFragment")
    }

    /**
     * Last time the user had chosen to postpone migration. Or 0 if never.
     */
    private var migrationWasLastPostponedAt: Long
        get() = baseContext.sharedPrefs().getLong(MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS, 0L)
        set(timeInSecond) = baseContext.sharedPrefs()
            .edit { putLong(MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS, timeInSecond) }

    /**
     * The number of times the storage migration was postponed. -1 for 'disabled'
     */
    private var timesStorageMigrationPostponed: Int
        get() = baseContext.sharedPrefs().getInt(TIMES_STORAGE_MIGRATION_POSTPONED_KEY, 0)
        set(value) = baseContext.sharedPrefs()
            .edit { putInt(TIMES_STORAGE_MIGRATION_POSTPONED_KEY, value) }

    /** Whether the user has disabled the dialog from [showDialogThatOffersToMigrateStorage] */
    private val disabledScopedStorageReminder: Boolean
        get() = timesStorageMigrationPostponed == -1

    /**
     * Show a dialog offering to migrate, postpone or learn more.
     * @return shownAutomatically `true` if the dialog was shown automatically, `false` if the user
     * pressed a button to open the dialog
     */
    private fun showDialogThatOffersToMigrateStorage(shownAutomatically: Boolean) {
        Timber.i("Displaying dialog to migrate storage")
        if (mediaMigrationIsInProgress(baseContext)) {
            // This should not occur. We should have not called the function in this case.
            return
        }

        val message = getString(R.string.migration_update_request_requires_media_sync)

        fun onPostponeOnce() {
            if (shownAutomatically) {
                timesStorageMigrationPostponed += 1
            }
            setMigrationWasLastPostponedAtToNow()
        }

        fun onPostponePermanently() {
            BackupPromptDialog.showPermanentlyDismissDialog(
                this,
                onCancel = { onPostponeOnce() },
                onDisableReminder = {
                    this.sharedPrefs().edit {
                        putInt(TIMES_STORAGE_MIGRATION_POSTPONED_KEY, -1)
                        remove(MIGRATION_WAS_LAST_POSTPONED_AT_SECONDS)
                    }
                }
            )
        }

        var userCheckedDoNotShowAgain = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.scoped_storage_title)
            .setMessage(message)
            .setPositiveButton(
                getString(R.string.scoped_storage_migrate)
            ) { _, _ ->
                performMediaSyncBeforeStorageMigration()
            }
            .setNegativeButton(
                getString(R.string.scoped_storage_postpone)
            ) { _, _ ->
                if (userCheckedDoNotShowAgain) {
                    onPostponePermanently()
                } else {
                    onPostponeOnce()
                }
            }
        // allow the user to dismiss the automatic dialog after it's been seen twice
        if (shownAutomatically && timesStorageMigrationPostponed > 1) {
            dialog.checkBoxPrompt(R.string.button_do_not_show_again) { checked ->
                Timber.d("Don't show again checked: %b", checked)
                userCheckedDoNotShowAgain = checked
            }
        }
        dialog.addScopedStorageLearnMoreLinkAndShow(message)
    }

    private fun showDialogThatOffersToResumeMigrationAfterError(errorText: String) {
        val helpUrl = getString(R.string.link_migration_failed_dialog_learn_more_en)
        val message = getString(R.string.migration__resume_after_failed_dialog__message, errorText, helpUrl)
            .parseAsHtml()

        AlertDialog.Builder(this)
            .setTitle(R.string.scoped_storage_title)
            .setMessage(message)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> }
            .setPositiveButton(R.string.migration__resume_after_failed_dialog__button_positive) { _, _ ->
                MigrationService.start(baseContext)
                invalidateOptionsMenu()
            }
            .create()
            .makeLinksClickable()
            .show()
    }

    // TODO BEFORE-RELEASE Fix the logic. As I understand, this works the following way,
    //   which could make a little more sense:
    //     if (media sync is not disabled,
    //         and (either we sync media unconditionally or are on a suitable network),
    //         and (either we are logged in, or unsafe migration is disallowed (the default))):
    //       set flag migrate-after-media-synced, and
    //       call sync, which may fail to actually sync or even fail to start syncing
    //       (in these cases, migration might start unexpectedly after a successful sync);
    //     else:
    //       tell the user that migration is disabled in the settings (might not be true)
    //       and tell them to sync & backup before continuing (which isn't possible),
    //       and instead of offering them to force sync,
    //       offer them to migrate regardless of the above.
    private fun performMediaSyncBeforeStorageMigration() {
        // if we allow an unsafe migration, the 'sync required' dialog shows an unsafe migration confirmation dialog
        val showUnsafeSyncDialog = (BuildConfig.ALLOW_UNSAFE_MIGRATION && !isLoggedIn())

        if (shouldFetchMedia(this.sharedPrefs()) && !showUnsafeSyncDialog) {
            Timber.i("Syncing before storage migration")
            migrateStorageAfterMediaSyncCompleted = true
            sync()
        } else {
            Timber.i("media sync disabled: displaying dialog")
            AlertDialog.Builder(this).show {
                setTitle(R.string.media_sync_required_title)
                setIcon(R.drawable.ic_warning)
                setMessage(R.string.media_sync_unavailable_message)
                setPositiveButton(getString(R.string.scoped_storage_migrate)) { _, _ ->
                    Timber.i("Performing unsafe storage migration")
                    migrate()
                }
                setNegativeButton(getString(R.string.scoped_storage_postpone)) { _, _ ->
                    setMigrationWasLastPostponedAtToNow()
                }
            }
        }
    }

    // Scoped Storage migration
    private fun setMigrationWasLastPostponedAtToNow() {
        migrationWasLastPostponedAt = TimeManager.time.intTime()
    }

    private fun timeToShowStorageMigrationDialog(): Boolean {
        return !disabledScopedStorageReminder &&
            // A reminder was shown more than 4 days ago
            migrationWasLastPostponedAt + SECONDS_PER_DAY * 4 <= TimeManager.time.intTime()
    }

    override fun onImportColpkg(colpkgPath: String?) {
        launchCatchingTask {
            // as the current collection is closed before importing a new collection, make sure the
            // new collection is open before the code to update the DeckPicker ui runs
            withCol { }
            invalidateOptionsMenu()
            updateDeckList()
            importColpkgListener?.onImportColpkg(colpkgPath)
        }
    }

    override fun onMediaSyncCompleted(data: SyncCompletion) {
        Timber.i("Media sync completed. Success: %b", data.isSuccess)
        if (migrateStorageAfterMediaSyncCompleted) {
            migrate()
        }
    }

    /**
     * Returns how a user can 'custom study' a deck with no more pending cards
     *
     * @param did The id of a deck with no pending cards to review
     */
    private suspend fun queryCompletedDeckCustomStudyAction(
        did: DeckId
    ): CompletedDeckStatus = withCol {
        when {
            sched.hasCardsTodayAfterStudyAheadLimit() -> CompletedDeckStatus.LEARN_AHEAD_LIMIT_REACHED
            sched.newDue() || sched.revDue() -> CompletedDeckStatus.LEARN_AHEAD_LIMIT_REACHED
            decks.isFiltered(did) -> CompletedDeckStatus.DYNAMIC_DECK_NO_LIMITS_REACHED
            deckListAdapter.getNodeByDid(did).children.isEmpty() && isEmptyDeck(did) -> CompletedDeckStatus.EMPTY_REGULAR_DECK
            else -> CompletedDeckStatus.REGULAR_DECK_NO_MORE_CARDS_TODAY
        }
    }

    /** Status for a deck with no current cards to review */
    enum class CompletedDeckStatus {
        /** No cards for today, but there would be if the user waited */
        LEARN_AHEAD_LIMIT_REACHED,

        /** No cards for today, but either the 'new' or 'review' limit was reached */
        DAILY_STUDY_LIMIT_REACHED,

        /** No cards are available, but the deck was dynamic */
        DYNAMIC_DECK_NO_LIMITS_REACHED,

        /** The deck contained no cards and had no child decks */
        EMPTY_REGULAR_DECK,

        /** The user has completed their studying for today, and there are future reviews */
        REGULAR_DECK_NO_MORE_CARDS_TODAY
    }

    override fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent> {
        return apkgFileImportResultLauncher
    }

    override fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent> {
        return csvImportResultLauncher
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
    val undoLabel: String?,
    val syncIcon: SyncIconState,
    val shouldShowStartMigrationButton: Boolean,
    val mediaMigrationState: MediaMigrationState
)

enum class SyncIconState {
    Normal,
    PendingChanges,
    OneWay,
    NotLoggedIn
}

class CollectionLoadingErrorDialog : DialogHandlerMessage(
    WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
    "CollectionLoadErrorDialog"
) {
    override fun handleAsyncMessage(deckPicker: DeckPicker) {
        // Collection could not be opened
        deckPicker.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}

class OneWaySyncDialog(val message: String?) : DialogHandlerMessage(
    which = WhichDialogHandler.MSG_SHOW_ONE_WAY_SYNC_DIALOG,
    analyticName = "OneWaySyncDialog"
) {
    override fun handleAsyncMessage(deckPicker: DeckPicker) {
        // Confirmation dialog for one-way sync
        val dialog = ConfirmationDialog()
        val confirm = Runnable {
            // Bypass the check once the user confirms
            CollectionManager.getColUnsafe().modSchemaNoCheck()
        }
        dialog.setConfirm(confirm)
        dialog.setArgs(message)
        deckPicker.showDialogFragment(dialog)
    }

    override fun toMessage(): Message = Message.obtain().apply {
        what = this@OneWaySyncDialog.what
        data = bundleOf("message" to message)
    }

    companion object {
        fun fromMessage(message: Message): DialogHandlerMessage =
            OneWaySyncDialog(message.data.getString("message"))
    }
}

// This is used to re-show the dialog immediately on activity recreation
private suspend fun <T> Activity.withImmediatelyShownProgress(@StringRes messageId: Int, block: suspend () -> T) =
    withProgressDialog(context = this, onCancel = null, delayMillis = 0L) { dialog ->
        @Suppress("DEPRECATION") // ProgressDialog
        dialog.setMessage(getString(messageId))
        block()
    }
