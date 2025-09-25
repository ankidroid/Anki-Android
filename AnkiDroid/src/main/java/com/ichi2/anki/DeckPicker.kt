/* **************************************************************************************
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.database.SQLException
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Message
import android.text.util.Linkify
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.util.component1
import androidx.core.util.component2
import androidx.core.view.GravityCompat
import androidx.core.view.MenuItemCompat
import androidx.core.view.OnReceiveContentListener
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import anki.collection.OpChanges
import anki.sync.SyncStatusResponse
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.InitialActivity.StartupFailure.DBError
import com.ichi2.anki.InitialActivity.StartupFailure.DatabaseLocked
import com.ichi2.anki.InitialActivity.StartupFailure.DirectoryNotAccessible
import com.ichi2.anki.InitialActivity.StartupFailure.DiskFull
import com.ichi2.anki.InitialActivity.StartupFailure.FutureAnkidroidVersion
import com.ichi2.anki.InitialActivity.StartupFailure.SDCardNotMounted
import com.ichi2.anki.InitialActivity.StartupFailure.WebviewFailed
import com.ichi2.anki.IntentHandler.Companion.intentToReviewDeckFromShortcuts
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.android.back.exitViaDoubleTapBackCallback
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.deckpicker.BITMAP_BYTES_PER_PIXEL
import com.ichi2.anki.deckpicker.BackgroundImage
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.deckpicker.DeckPickerViewModel.AnkiDroidEnvironment
import com.ichi2.anki.deckpicker.DeckPickerViewModel.FlattenedDeckList
import com.ichi2.anki.deckpicker.DeckPickerViewModel.StartupResponse
import com.ichi2.anki.deckpicker.DeckSelectionResult
import com.ichi2.anki.deckpicker.DeckSelectionType
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.BackupPromptDialog
import com.ichi2.anki.dialogs.ConfirmationDialog
import com.ichi2.anki.dialogs.CreateDeckDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.CustomExceptionData
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DeckPickerAnalyticsOptInDialog
import com.ichi2.anki.dialogs.DeckPickerBackupNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerNoSpaceLeftDialog
import com.ichi2.anki.dialogs.DialogHandlerMessage
import com.ichi2.anki.dialogs.EmptyCardsDialogFragment
import com.ichi2.anki.dialogs.ImportDialog.ImportDialogListener
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ApkgImportResultLauncherProvider
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.CsvImportResultLauncherProvider
import com.ichi2.anki.dialogs.SchedulerUpgradeDialog
import com.ichi2.anki.dialogs.SyncErrorDialog
import com.ichi2.anki.dialogs.SyncErrorDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SyncErrorDialog.SyncErrorDialogListener
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction
import com.ichi2.anki.dialogs.customstudy.CustomStudyDialog.CustomStudyAction.Companion.REQUEST_KEY
import com.ichi2.anki.export.ExportDialogFragment
import com.ichi2.anki.introduction.CollectionPermissionScreenLauncher
import com.ichi2.anki.introduction.hasCollectionStoragePermissions
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.sched.DeckNode
import com.ichi2.anki.libanki.undoAvailable
import com.ichi2.anki.libanki.undoLabel
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.pages.AnkiPackageImporterFragment
import com.ichi2.anki.pages.CongratsPage
import com.ichi2.anki.pages.CongratsPage.Companion.onDeckCompleted
import com.ichi2.anki.preferences.AdvancedSettingsFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.servicelayer.ScopedStorageService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.BaseSnackbarBuilderProvider
import com.ichi2.anki.snackbar.SnackbarBuilder
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.compose.AnkiDroidApp
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import com.ichi2.anki.utils.Destination
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.anki.worker.SyncMediaWorker
import com.ichi2.anki.worker.SyncWorker
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.ui.BadgeDrawableBuilder
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.ClipboardUtil.IMPORT_MIME_TYPES
import com.ichi2.utils.ImportUtils
import com.ichi2.utils.ImportUtils.ImportResult
import com.ichi2.utils.NetworkUtils
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.utils.VersionUtils
import com.ichi2.utils.cancelable
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.checkWebviewVersion
import com.ichi2.utils.customView
import com.ichi2.utils.dp
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.Translations
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import org.json.JSONException
import timber.log.Timber
import java.io.File

@Composable
private fun DeckPicker.deckPickerPainter(): Painter? {
    // Allow the user to clear data and get back to a good state if they provide an invalid background.
    if (!this.sharedPrefs().getBoolean("deckPickerBackground", false)) {
        Timber.d("No DeckPicker background preference")
        return null
    }
    val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)
    val imgFile = File(currentAnkiDroidDirectory, "DeckPickerBackground.png")
    if (!imgFile.exists()) {
        Timber.d("No DeckPicker background image")
        return null
    }

    // TODO: Temporary fix to stop a crash on startup [15450], it can be removed either:
    // * by moving this check to an upgrade path
    // * once enough time has passed
    val size = BackgroundImage.getBackgroundImageDimensions(this)
    if (size.width * size.height * BITMAP_BYTES_PER_PIXEL > BackgroundImage.MAX_BITMAP_SIZE) {
        Timber.w("DeckPicker background image dimensions too large")
        return null
    }

    Timber.i("Applying background")
    return rememberAsyncImagePainter(model = imgFile)
}

/**
 * The current entry point for AnkiDroid. Displays decks, allowing users to study. Many other functions.
 *
 * On a tablet, this is a fragmented view, with [StudyOptionsFragment] to the right: [loadStudyOptionsFragment]
 *
 * Often used as navigation to: [Reviewer], [NoteEditorFragment] (adding notes), [StudyOptionsFragment] [SharedDecksDownloadFragment]
 *
 * Responsibilities:
 * * Setup/upgrades of the application: [handleStartup]
 * * Error handling [handleDbError] [handleDbLocked]
 * * Displaying a tree of decks, some of which may be collapsible
 *   * Allows users to study the decks
 *   * Displays deck progress
 *   * A long press opens a menu allowing modification of the deck
 *   * Filtering decks (if more than 10) [toolbarSearchView]
 * * Controlling syncs
 *   * A user may [pull down][pullToSyncWrapper] on the 'tree view' to sync
 *   * A [button][updateSyncIconFromState] which relies on [SyncIconState] to display whether a sync is needed
 *   * Blocks the UI and displays sync progress when syncing
 * * Displaying 'General' AnkiDroid options: backups, import, 'check media' etc...
 *   * General handler for error/global dialogs (search for 'as DeckPicker')
 *   * Such as import: [ImportDialogListener]
 * * A Floating Action Button [floatingActionMenu] allowing the user to quickly add notes/cards.
 * * A custom image as a background can be added: [applyDeckPickerBackground]
 */
@KotlinCleanup("lots to do")
@NeedsTest("If the collection has been created, the app intro is not displayed")
@NeedsTest("If the user selects 'Sync Profile' in the app intro, a sync starts immediately")
open class DeckPicker :
    NavigationDrawerActivity(),
    SyncErrorDialogListener,
    ImportDialogListener,
    OnRequestPermissionsResultCallback,
    ChangeManager.Subscriber,
    ImportColpkgListener,
    BaseSnackbarBuilderProvider,
    ApkgImportResultLauncherProvider,
    CsvImportResultLauncherProvider,
    CollectionPermissionScreenLauncher {
    override val baseSnackbarBuilder: SnackbarBuilder = {}
    val viewModel: DeckPickerViewModel by viewModels()

    override var fragmented: Boolean
        get() = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK == Configuration.SCREENLAYOUT_SIZE_XLARGE
        set(_) = throw UnsupportedOperationException()

    // flag asking user to do a full sync which is used in upgrade path
    private var recommendOneWaySync = false

    private var syncMediaProgressJob: Job? = null

    // flag keeping track of when the app has been paused
    var activityPaused = false
        private set

    /** See [OptionsMenuState]. */
    @VisibleForTesting
    var optionsMenuState: OptionsMenuState? = null

    @VisibleForTesting
    val dueTree: DeckNode?
        get() = viewModel.dueTree

    /**
     * Flag to indicate whether the activity will perform a sync in its onResume.
     * Since syncing closes the database, this flag allows us to avoid doing any
     * work in onResume that might use the database and go straight to syncing.
     */
    private var syncOnResume = false

    override val permissionScreenLauncher = recreateActivityResultLauncher()

    private val reviewLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                processReviewResults(it.resultCode)
            },
        )

    private val showNewVersionInfoLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                showStartupScreensAndDialogs(baseContext.sharedPrefs(), 3)
            },
        )

    private val loginForSyncLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    syncOnResume = true
                }
            },
        )

    private val requestPathUpdateLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                // The collection path was inaccessible on startup so just close the activity and let user restart
                finish()
            },
        )

    private val apkgFileImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            onSelectedPackageToImport(it.data!!)
                        }
                    }
                }
            },
        )

    private val csvImportResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
            DeckPickerActivityResultCallback {
                if (it.resultCode == RESULT_OK) {
                    onSelectedCsvForImport(it.data!!)
                }
            },
        )

    private val exitAndSyncBackCallback =
        object : OnBackPressedCallback(enabled = true) {
            override fun handleOnBackPressed() {
                // TODO: Room for improvement now we use back callbacks
                // can't use launchCatchingTask because any errors
                // would need to be shown in the UI
                lifecycleScope
                    .launch {
                        automaticSync(runInBackground = true)
                    }.invokeOnCompletion {
                        finish()
                    }
            }
        }

    private inner class DeckPickerActivityResultCallback(
        private val callback: (result: ActivityResult) -> Unit,
    ) : ActivityResultCallback<ActivityResult> {
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

    // stored for testing purposes
    @VisibleForTesting
    var createMenuJob: Job? = null

    init {
        ChangeManager.subscribe(this)
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
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
        }
        Timber.d("Not displaying app intro")
        if (intent.hasExtra(INTENT_SYNC_FROM_LOGIN)) {
            Timber.d("launched from introduction activity login: syncing")
            syncOnResume = true
        }

        enableToolbar()
        // TODO This method is run on every activity recreation, which can happen often.
        //  It seems that the original idea was for for this to only run once, on app start.
        //  This method triggers backups, sync, and may re-show dialogs
        //  that may have been dismissed. Make this run only once?
        handleStartup()

        registerReceiver()

        // create inherited navigation drawer layout here so that it can be used by parent class
        initNavigationDrawer()
        title = resources.getString(R.string.app_name)

        checkWebviewVersion(this)

        setFragmentResultListener(REQUEST_KEY) { _, bundle ->
            when (CustomStudyAction.fromBundle(bundle)) {
                CustomStudyAction.CUSTOM_STUDY_SESSION -> {
                    Timber.d("Custom study created")
                    updateDeckList()
                    if (!fragmented) {
                        openStudyOptionsActivity(false)
                    }
                }

                CustomStudyAction.EXTEND_STUDY_LIMITS -> {
                    Timber.d("Study limits updated")
                    updateDeckList()
                }
            }
        }

        ViewCompat.setOnReceiveContentListener(
            window.decorView,
            IMPORT_MIME_TYPES,
            onReceiveContentListener,
        )

        setupFlows()

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            val deckList by viewModel.flowOfDeckList.collectAsState(
                initial = FlattenedDeckList(emptyList(), false)
            )
            val isRefreshing by viewModel.isSyncing.collectAsState(initial = false)
            var searchQuery by remember { mutableStateOf("") }
            var requestSearchFocus by remember { mutableStateOf(false) }
            val focusedDeckId by viewModel.flowOfFocusedDeck.collectAsState()
            var studyOptionsData by remember { mutableStateOf<com.ichi2.anki.ui.compose.StudyOptionsData?>(null) }

            LaunchedEffect(focusedDeckId) {
                val currentFocusedDeck = focusedDeckId
                if (currentFocusedDeck != null) {
                    studyOptionsData =
                        withContext(Dispatchers.IO) {
                            withCol {
                                decks.select(currentFocusedDeck)
                                val deck = decks.current()
                                val counts = sched.counts()
                                var buriedNew = 0
                                var buriedLearning = 0
                                var buriedReview = 0
                                val tree = sched.deckDueTree(currentFocusedDeck)
                                if (tree != null) {
                                    buriedNew = tree.newCount - counts.new
                                    buriedLearning = tree.learnCount - counts.lrn
                                    buriedReview = tree.reviewCount - counts.rev
                                }
                                com.ichi2.anki.ui.compose.StudyOptionsData(
                                    deckId = currentFocusedDeck,
                                    deckName = deck.getString("name"),
                                    deckDescription = deck.description,
                                    newCount = counts.new,
                                    lrnCount = counts.lrn,
                                    revCount = counts.rev,
                                    buriedNew = buriedNew,
                                    buriedLrn = buriedLearning,
                                    buriedRev = buriedReview,
                                    totalNewCards = sched.totalNewForCurrentDeck(),
                                    totalCards = decks.cardCount(currentFocusedDeck, includeSubdecks = true),
                                    isFiltered = deck.isFiltered,
                                    haveBuried = sched.haveBuried(),
                                )
                            }
                        }
                } else {
                    studyOptionsData = null
                }
            }

            AnkiDroidApp(
                fragmented = fragmented,
                decks = deckList.data,
                isRefreshing = isRefreshing,
                onRefresh = { sync() },
                searchQuery = searchQuery,
                onSearchQueryChanged = {
                    searchQuery = it
                    viewModel.updateDeckFilter(it)
                },
                backgroundImage = deckPickerPainter(),
                onDeckClick = { deck ->
                    viewModel.onDeckSelected(
                        deck.did,
                        DeckSelectionType.DEFAULT,
                    )
                },
                onExpandClick = { deck -> viewModel.toggleDeckExpand(deck.did) },
                onAddNote = { addNote() },
                onAddDeck = { showCreateDeckDialog() },
                onAddSharedDeck = { openAnkiWebSharedDecks() },
                onAddFilteredDeck = { showCreateFilteredDeckDialog() },
                onDeckOptions = { deck -> viewModel.openDeckOptions(deck.did) },
                onRename = { deck -> renameDeckDialog(deck.did) },
                onExport = { deck -> exportDeck(deck.did) },
                onDelete = { deck -> deleteDeck(deck.did) },
                onRebuild = { deck -> rebuildFiltered(deck.did) },
                onEmpty = { deck -> emptyFiltered(deck.did) },
                onNavigationIconClick = {
                    drawerLayout.openDrawer(GravityCompat.START)
                },
                studyOptionsData = studyOptionsData,
                onStartStudy = { openReviewer() },
                onRebuildDeck = { deckId -> rebuildFiltered(deckId) },
                onEmptyDeck = { deckId -> emptyFiltered(deckId) },
                onCustomStudy = { deckId -> showCustomStudyDialog(deckId) },
                onDeckOptionsItemSelected = { deckId -> viewModel.openDeckOptions(deckId) },
                onUnbury = { deckId -> viewModel.unburyDeck(deckId) },
                requestSearchFocus = requestSearchFocus,
                onSearchFocusRequested = { requestSearchFocus = false },
                snackbarHostState = snackbarHostState,
            )

            LaunchedEffect(Unit) {
                viewModel.deckDeletedNotification.flowWithLifecycle(lifecycle).collect {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message = it.toHumanReadableString(),
                            actionLabel = getString(R.string.undo),
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        undo()
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.emptyCardsNotification.flowWithLifecycle(lifecycle).collect {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message = it.toHumanReadableString(),
                            actionLabel = getString(R.string.undo),
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        undo()
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.deckSelectionResult.flowWithLifecycle(lifecycle).collect { result ->
                    when (result) {
                        is DeckSelectionResult.HasCardsToStudy -> {
                            when (result.selectionType) {
                                DeckSelectionType.DEFAULT -> {
                                    if (!fragmented) {
                                        openReviewer()
                                    }
                                }

                                DeckSelectionType.SHOW_STUDY_OPTIONS -> {
                                    if (!fragmented) {
                                        openStudyOptionsActivity(false)
                                    }
                                }

                                DeckSelectionType.SKIP_STUDY_OPTIONS -> {
                                    openReviewer()
                                }
                            }
                        }

                        is DeckSelectionResult.Empty -> {
                            coroutineScope.launch {
                                val snackbarResult =
                                    snackbarHostState.showSnackbar(
                                        message = getString(R.string.empty_deck),
                                        actionLabel = getString(R.string.menu_add),
                                    )
                                if (snackbarResult == SnackbarResult.ActionPerformed) {
                                    viewModel.addNote(result.deckId, true)
                                }
                            }
                        }

                        is DeckSelectionResult.NoCardsToStudy -> {
                            onDeckCompleted()
                        }
                    }
                }
            }
        }
    }

    override fun setupBackPressedCallbacks() {
        onBackPressedDispatcher.addCallback(this, exitAndSyncBackCallback)
        onBackPressedDispatcher.addCallback(this, exitViaDoubleTapBackCallback())
        super.setupBackPressedCallbacks()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun setupFlows() {
        fun onDeckCountsChanged(unit: Unit) {
            updateDeckList()
        }

        fun onDestinationChanged(destination: Destination) {
            startActivity(destination.toIntent(this))
        }

        fun onPromptUserToUpdateScheduler(op: Unit) {
            SchedulerUpgradeDialog(
                activity = this,
                onUpgrade = {
                    launchCatchingRequiringOneWaySync {
                        withCol { sched.upgradeToV2() }
                        showThemedToast(this@DeckPicker, TR.schedulingUpdateDone(), false)
                    }
                },
                onCancel = {
                    onBackPressedDispatcher.onBackPressed()
                },
            ).showDialog()
        }

        fun onUndoUpdated(a: Unit) {
            launchCatchingTask {
                withOpenColOrNull {
                    optionsMenuState =
                        optionsMenuState?.copy(
                            undoLabel = undoLabel(),
                            undoAvailable = undoAvailable(),
                        )
                }
                invalidateOptionsMenu()
            }
        }

        fun onResizingDividerVisibilityChanged(isVisible: Boolean) {
            val resizingDivider = findViewById<View>(R.id.homescreen_resizing_divider)
            resizingDivider?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun onCardsDueChanged(dueCount: Int?) {
            if (dueCount == null) {
                supportActionBar?.subtitle = null
                return
            }

            supportActionBar?.apply {
                subtitle =
                    if (dueCount == 0) {
                        null
                    } else {
                        resources.getQuantityString(
                            R.plurals.widget_cards_due,
                            dueCount,
                            dueCount,
                        )
                    }
                val toolbar = findViewById<Toolbar>(R.id.toolbar)
                TooltipCompat.setTooltipText(toolbar, toolbar.subtitle)
            }
        }

        fun onDeckListChanged(deckList: FlattenedDeckList) {
        }

        fun onDecksReloaded(param: Unit) {
            hideProgressBar()
        }

        fun onStartupResponse(response: StartupResponse) {
            Timber.d("onStartupResponse: %s", response)
            when (response) {
                is StartupResponse.RequestPermissions -> {
                    viewModel.flowOfStartupResponse.value =
                        null // Prevent duplicate permission screen launches
                    permissionScreenLauncher.launch(
                        PermissionsActivity.getIntent(this, response.requiredPermissions),
                    )
                }

                is StartupResponse.Success -> {
                    showStartupScreensAndDialogs(sharedPrefs(), 0)
                }

                is StartupResponse.FatalError -> handleStartupFailure(response.failure)
            }
        }

        fun onError(errorMessage: String) {
            AlertDialog
                .Builder(this)
                .setTitle(R.string.vague_error)
                .setMessage(errorMessage)
                .show()
        }

        viewModel.flowOfDeckCountsChanged.launchCollectionInLifecycleScope(::onDeckCountsChanged)
        viewModel.flowOfDestination.launchCollectionInLifecycleScope(::onDestinationChanged)
        viewModel.onError.launchCollectionInLifecycleScope(::onError)
        viewModel.flowOfPromptUserToUpdateScheduler.launchCollectionInLifecycleScope(::onPromptUserToUpdateScheduler)
        viewModel.flowOfUndoUpdated.launchCollectionInLifecycleScope(::onUndoUpdated)
        viewModel.flowOfCardsDue.launchCollectionInLifecycleScope(::onCardsDueChanged)
        viewModel.flowOfDeckList.launchCollectionInLifecycleScope(::onDeckListChanged)
        viewModel.flowOfResizingDividerVisible.launchCollectionInLifecycleScope(::onResizingDividerVisibilityChanged)
        viewModel.flowOfDecksReloaded.launchCollectionInLifecycleScope(::onDecksReloaded)
        viewModel.flowOfStartupResponse
            .filterNotNull()
            .launchCollectionInLifecycleScope(::onStartupResponse)
    }

    private val onReceiveContentListener =
        OnReceiveContentListener { _, payload ->
            val (uriContent, remaining) = payload.partition { item -> item.uri != null }

            val clip = uriContent?.clip ?: return@OnReceiveContentListener remaining
            val uri = clip.getItemAt(0).uri
            if (!ImportUtils.FileImporter().isValidImportType(this, uri)) {
                ImportResult.fromErrorString(getString(R.string.import_log_no_apkg))
                return@OnReceiveContentListener remaining
            }

            try {
                // Intent is nullable because `clip.getItemAt(0).intent` always returns null
                ImportUtils.FileImporter().handleContentProviderFile(this, uri, Intent().setData(uri))
                onResume()
            } catch (e: Exception) {
                Timber.w(e)
                CrashReportService.sendExceptionReport(e, "DeckPicker::onReceiveContent")
                return@OnReceiveContentListener remaining
            }

            return@OnReceiveContentListener remaining
        }

    /**
     * @see DeckPickerViewModel.handleStartup
     */
    private fun handleStartup() {
        val context = AnkiDroidApp.instance

        val environment: AnkiDroidEnvironment =
            object : AnkiDroidEnvironment {
                private val folder = selectAnkiDroidFolder(context)

                override fun hasRequiredPermissions(): Boolean = folder.hasRequiredPermissions(context)

                override val requiredPermissions: PermissionSet
                    get() = folder.permissionSet

                override fun initializeAnkiDroidFolder(): Boolean = CollectionHelper.isCurrentAnkiDroidDirAccessible(context)
            }

        viewModel.handleStartup(environment = environment)
    }

    @VisibleForTesting
    fun handleStartupFailure(failure: StartupFailure) {
        when (failure) {
            is SDCardNotMounted -> {
                Timber.i("SD card not mounted")
                onSdCardNotMounted()
            }

            is DirectoryNotAccessible -> {
                Timber.i("AnkiDroid directory inaccessible")
                if (ScopedStorageService.collectionWasMadeInaccessibleAfterUninstall(this)) {
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL)
                } else {
                    showDirectoryNotAccessibleDialog()
                }
            }

            is FutureAnkidroidVersion -> {
                Timber.i("Displaying database versioning")
                showDatabaseErrorDialog(DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION)
            }

            is DatabaseLocked -> {
                Timber.i("Displaying database locked error")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DB_LOCKED)
            }

            is WebviewFailed ->
                AlertDialog.Builder(this).show {
                    title(R.string.ankidroid_init_failed_webview_title)
                    message(
                        text =
                            getString(
                                R.string.ankidroid_init_failed_webview,
                                AnkiDroidApp.webViewErrorMessage,
                            ),
                    )
                    positiveButton(R.string.close) {
                        closeCollectionAndFinish()
                    }
                    cancelable(false)
                }

            is DiskFull -> displayNoStorageError()
            is DBError -> displayDatabaseFailure(CustomExceptionData.fromException(failure.exception))
        }
    }

    private fun showDirectoryNotAccessibleDialog() {
        val contentView =
            TextView(this).apply {
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
                text =
                    getString(
                        R.string.directory_inaccessible_info,
                        getString(R.string.link_full_storage_access),
                    )
            }
        AlertDialog.Builder(this).show {
            title(R.string.directory_inaccessible)
            customView(
                contentView,
                paddingTop = 16.dp.toPx(this@DeckPicker),
                paddingStart = 32.dp.toPx(this@DeckPicker),
                paddingEnd = 32.dp.toPx(this@DeckPicker),
            )
            positiveButton(R.string.open_settings) {
                val settingsIntent =
                    PreferencesActivity.getIntent(this@DeckPicker, AdvancedSettingsFragment::class)
                requestPathUpdateLauncher.launch(settingsIntent)
            }
        }
    }

    private fun displayDatabaseFailure(exceptionData: CustomExceptionData? = null) {
        Timber.i("Displaying database failure")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
    }

    private fun displayNoStorageError() {
        Timber.i("Displaying no storage error")
        showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_DISK_FULL)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Timber.d("onCreateOptionsMenu()")
        // TODO: Refactor menu handling logic to the activity
        // The menus for the fragmented view should be the responsibility of the activity.
        // This would mean extracting the menu logic out of the fragments, extending it to the full width of the activity,
        // and having the activity be responsible for it. This change should reduce complexity.
        // We should have two menu files for the DeckPicker (fragmented/non), and one for the Options (non-fragmented)
        menuInflater.inflate(R.menu.deck_picker, menu)
        // Search is handled in Compose now
        menu.findItem(R.id.deck_picker_action_filter)?.isVisible = false

        menu.findItem(R.id.action_export_collection)?.title = TR.actionsExport()
        setupMediaSyncMenuItem(menu)
        // redraw menu synchronously to avoid flicker
        updateMenuFromState(menu)
        // ...then launch a task to possibly update the visible icons.
        // Store the job so that tests can easily await it. In the future
        // this may be better done by injecting a custom test scheduler
        // into CollectionManager, and awaiting that.
        createMenuJob =
            launchCatchingTask {
                updateMenuState()
                updateDeckRelatedMenuItems(menu)
                if (!fragmented) {
                    updateMenuFromState(menu)
                }
            }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_custom_study)?.setShowAsAction(
            if (fragmented) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER,
        )
        return super.onPrepareOptionsMenu(menu)
    }

    fun setupMediaSyncMenuItem(menu: Menu) {
        // shouldn't be necessary, but `invalidateOptionsMenu()` is called way more than necessary
        syncMediaProgressJob?.cancel()

        val syncItem = menu.findItem(R.id.action_sync)
        val progressIndicator =
            syncItem.actionView?.findViewById<LinearProgressIndicator>(R.id.progress_indicator)

        val workManager = WorkManager.getInstance(this)
        val flow = workManager.getWorkInfosForUniqueWorkFlow(UniqueWorkNames.SYNC_MEDIA)

        syncMediaProgressJob =
            lifecycleScope.launch {
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

    fun updateMenuFromState(menu: Menu) {
        optionsMenuState?.run {
            updateUndoLabelFromState(menu.findItem(R.id.action_undo), undoLabel, undoAvailable)
            updateSyncIconFromState(menu.findItem(R.id.action_sync), this)
        }
        updateDeckRelatedMenuItems(menu)
    }

    /**
     * Shows/hides deck related menu items based on the collection being empty or not.
     */
    private fun updateDeckRelatedMenuItems(menu: Menu) {
        optionsMenuState?.run {
            menu.findItem(R.id.action_deck_rename)?.isVisible = !isColEmpty
            menu.findItem(R.id.action_deck_delete)?.isVisible = !isColEmpty
            // added to the menu by StudyOptionsFragment
            menu.findItem(R.id.action_deck_or_study_options)?.isVisible = !isColEmpty
        }
    }

    private fun updateUndoLabelFromState(
        menuItem: MenuItem,
        undoLabel: String?,
        undoAvailable: Boolean,
    ) {
        menuItem.run {
            if (undoLabel != null && undoAvailable) {
                isVisible = true
                title = undoLabel
            } else {
                isVisible = false
            }
        }
    }

    private fun updateSyncIconFromState(
        menuItem: MenuItem,
        state: OptionsMenuState,
    ) {
        val provider = MenuItemCompat.getActionProvider(menuItem) as? SyncActionProvider ?: return
        val tooltipText =
            when (state.syncIcon) {
                SyncIconState.Normal, SyncIconState.PendingChanges -> R.string.button_sync
                SyncIconState.OneWay -> R.string.sync_menu_title_one_way_sync
                SyncIconState.NotLoggedIn -> R.string.sync_menu_title_no_account
            }
        provider.setTooltipText(getString(tooltipText))
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
        optionsMenuState =
            withOpenColOrNull {
                val undoLabel = undoLabel()
                val undoAvailable = undoAvailable()
                // besides checking for cards being available also consider if we have empty decks
                val isColEmpty = isEmpty && decks.count() == 1
                // the correct sync status is fetched in the next call so "Normal" is used as a placeholder
                // the sync status is calculated in the next call so "Normal" is used as a placeholder
                OptionsMenuState(undoLabel, SyncIconState.Normal, undoAvailable, isColEmpty)
            }?.let { (undoLabel, _, undoAvailable, isColEmpty) ->
                val syncIcon = fetchSyncIconState()
                OptionsMenuState(undoLabel, syncIcon, undoAvailable, isColEmpty)
            }
    }

    private suspend fun fetchSyncIconState(): SyncIconState {
        if (!Prefs.displaySyncStatus) return SyncIconState.Normal
        val auth = syncAuth()
        if (auth == null) return SyncIconState.NotLoggedIn
        return try {
            // Use CollectionManager to ensure that this doesn't block 'deck count' tasks
            // throws if a .colpkg import or similar occurs just before this call
            val output =
                withContext(Dispatchers.IO) { CollectionManager.getBackend().syncStatus(auth) }
            if (output.hasNewEndpoint() && output.newEndpoint.isNotEmpty()) {
                Prefs.currentSyncUri = output.newEndpoint
            }
            when (output.required) {
                SyncStatusResponse.Required.NO_CHANGES -> SyncIconState.Normal
                SyncStatusResponse.Required.NORMAL_SYNC -> SyncIconState.PendingChanges
                SyncStatusResponse.Required.FULL_SYNC -> SyncIconState.OneWay
                SyncStatusResponse.Required.UNRECOGNIZED, null -> TODO("unexpected required response")
            }
        } catch (_: BackendNetworkException) {
            SyncIconState.Normal
        } catch (e: Exception) {
            Timber.d(e, "error obtaining sync status: collection likely closed")
            SyncIconState.Normal
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
                showMediaCheckDialog()
                return true
            }

            R.id.action_empty_cards -> {
                Timber.i("DeckPicker:: Empty cards button pressed")
                EmptyCardsDialogFragment().show(
                    supportFragmentManager,
                    EmptyCardsDialogFragment.TAG,
                )
                return true
            }

            R.id.action_model_browser_open -> {
                Timber.i("DeckPicker:: Model browser button pressed")
                viewModel.openManageNoteTypes()
                return true
            }

            R.id.action_restore_backup -> {
                Timber.i("DeckPicker:: Restore from backup button pressed")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                return true
            }

            R.id.action_deck_rename -> {
                launchCatchingTask {
                    val targetDeckId = withCol { decks.selected() }
                    renameDeckDialog(targetDeckId)
                }
                return true
            }

            R.id.action_deck_delete -> {
                launchCatchingTask {
                    withProgress(resources.getString(R.string.delete_deck)) {
                        viewModel.deleteSelectedDeck().join()
                    }
                }
                return true
            }

            R.id.action_export_collection -> {
                Timber.i("DeckPicker:: Export menu item selected")
                ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun showMediaCheckDialog() {
        Timber.i("showing media check dialog")
        AlertDialog.Builder(this).show {
            title(text = getString(R.string.check_media_title))
            message(text = getString(R.string.check_media_warning))
            positiveButton(R.string.dialog_ok) {
                Timber.i("Starting media check")
                startActivity(MediaCheckFragment.getIntent(this@DeckPicker))
            }
            negativeButton(R.string.dialog_cancel)
        }
    }

    fun showCreateFilteredDeckDialog() {
        val createFilteredDeckDialog =
            CreateDeckDialog(
                this@DeckPicker,
                R.string.new_deck,
                CreateDeckDialog.DeckDialogType.FILTERED_DECK,
                null,
            )
        createFilteredDeckDialog.onNewDeckCreated = { deckId ->
            // a filtered deck was created
            viewModel.openDeckOptions(deckId, isFiltered = true)
        }
        launchCatchingTask {
            withProgress {
                createFilteredDeckDialog.showFilteredDeckDialog()
            }
        }
    }

    fun exportCollection() {
        ExportDialogFragment.newInstance().show(supportFragmentManager, "exportDialog")
    }

    private fun showCustomStudyDialog(deckId: Long) {
        showDialogFragment(CustomStudyDialog.createInstance(deckId))
    }

    private fun processReviewResults(resultCode: Int) {
        if (resultCode == AbstractFlashcardViewer.RESULT_NO_MORE_CARDS) {
            CongratsPage.onReviewsCompleted(this, getColUnsafe.sched.totalCount() == 0)
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
        importColpkgListener?.let {
            if (it is DatabaseRestorationListener) {
                outState.getString("dbRestorationPath", it.newAnkiDroidDirectory.absolutePath)
            }
        }
        outState.putSerializable("mediaUsnOnConflict", mediaUsnOnConflict)
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("dbRestorationPath")?.let { path ->
            val path = File(path)
            CollectionHelper.ankiDroidDirectoryOverride = path
            importColpkgListener = DatabaseRestorationListener(this, path)
        }
        mediaUsnOnConflict = savedInstanceState.getSerializableCompat("mediaUsnOnConflict")
    }

    override fun onPause() {
        activityPaused = true
        // The deck count will be computed on resume. No need to compute it now
        viewModel.loadDeckCounts?.cancel()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        WidgetStatus.updateInBackground(this@DeckPicker)
    }

    /**
     * Performs a sync if the conditions are met, e.g. user is logged in, there are changes,
     * and auto sync is enabled.
     * @param runInBackground whether the sync should be performed in the background or not
     * @return whether a sync was performed or not.
     */
    private suspend fun automaticSync(runInBackground: Boolean = false): Boolean {
        /**
         * @return whether there are collection changes to be sync.
         *
         * It DOES NOT include if there are media to be synced.
         */
        suspend fun areThereChangesToSync(): Boolean {
            val auth = syncAuth() ?: return false
            val status =
                withContext(Dispatchers.IO) {
                    CollectionManager.getBackend().syncStatus(auth)
                }.required

            return when (status) {
                SyncStatusResponse.Required.NO_CHANGES,
                SyncStatusResponse.Required.UNRECOGNIZED,
                null,
                -> false

                SyncStatusResponse.Required.FULL_SYNC,
                SyncStatusResponse.Required.NORMAL_SYNC,
                -> true
            }
        }

        fun syncIntervalPassed(): Boolean {
            val automaticSyncIntervalInMS = AUTOMATIC_SYNC_MINIMAL_INTERVAL_IN_MINUTES * 60 * 1000
            return TimeManager.time.intTimeMS() - Prefs.lastSyncTime > automaticSyncIntervalInMS
        }

        val isBlockedByMeteredConnection =
            !Prefs.allowSyncOnMeteredConnections && isActiveNetworkMetered()

        when {
            !Prefs.isAutoSyncEnabled -> Timber.d("autoSync: not enabled")
            isBlockedByMeteredConnection -> Timber.d("autoSync: blocked by metered connection")
            !NetworkUtils.isOnline -> Timber.d("autoSync: offline")
            !runInBackground && !syncIntervalPassed() -> Timber.d("autoSync: interval not passed")
            !isLoggedIn() -> Timber.d("autoSync: not logged in")
            !areThereChangesToSync() -> {
                Timber.d("autoSync: no collection changes to sync. Syncing media if set")
                if (shouldFetchMedia()) {
                    val auth = syncAuth() ?: return false
                    SyncMediaWorker.start(this, auth)
                }
                setLastSyncTimeToNow()
            }

            else -> {
                if (runInBackground) {
                    Timber.i("autoSync: starting background")
                    val auth = syncAuth() ?: return false
                    SyncWorker.start(this, auth, shouldFetchMedia())
                } else {
                    Timber.i("autoSync: starting foreground")
                    sync()
                }
                return true
            }
        }
        return false
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                Timber.i("Adding Note from keypress")
                viewModel.addNote(deckId = null, setAsCurrent = true)
                return true
            }

            KeyEvent.KEYCODE_B -> {
                if (event.isCtrlPressed) {
                    // Shortcut: CTRL + B
                    Timber.i("show restore backup dialog from keypress")
                    showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP)
                } else {
                    // Shortcut: B
                    Timber.i("Open Browser from keypress")
                    openCardBrowser()
                }
                return true
            }

            KeyEvent.KEYCODE_Y -> {
                Timber.i("Sync from keypress")
                sync()
                return true
            }

            KeyEvent.KEYCODE_SLASH -> {
                Timber.d("Search from keypress")
                // requestSearchFocus = true
                return true
            }

            KeyEvent.KEYCODE_S -> {
                Timber.i("Study from keypress")
                launchCatchingTask {
                    viewModel.onDeckSelected(
                        withCol { decks.selected() },
                        DeckSelectionType.SKIP_STUDY_OPTIONS,
                    )
                }
                return true
            }

            KeyEvent.KEYCODE_T -> {
                Timber.i("Open Statistics from keypress")
                openStatistics()
                return true
            }

            KeyEvent.KEYCODE_C -> {
                // Shortcut: C
                Timber.i("Check database from keypress")
                showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                return true
            }

            KeyEvent.KEYCODE_D -> {
                // Shortcut: D
                Timber.i("Create Deck from keypress")
                showCreateDeckDialog()
                return true
            }

            KeyEvent.KEYCODE_F -> {
                Timber.i("Create Filtered Deck from keypress")
                showCreateFilteredDeckDialog()
                return true
            }

            KeyEvent.KEYCODE_DEL -> {
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    if (event.isShiftPressed) {
                        // Shortcut: Shift + DEL - Delete deck without confirmation dialog
                        Timber.i("Shift+DEL: Deck deck without confirmation")
                        viewModel.focusedDeck?.let { did -> deleteDeck(did) }
                    } else {
                        // Shortcut: DEL
                        Timber.i("Delete Deck from keypress")
                        showDeleteDeckConfirmationDialog()
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_R -> {
                // Shortcut: R
                // This action on a deck should only occur when the user see the deck name very clearly,
                // that is, when it appears in the trailing study option fragment
                if (fragmented) {
                    Timber.i("Rename Deck from keypress")
                    viewModel.focusedDeck?.let { did -> renameDeckDialog(did) }
                    return true
                }
            }

            KeyEvent.KEYCODE_P -> {
                Timber.i("Open Settings from keypress")
                openSettings()
                return true
            }

            KeyEvent.KEYCODE_M -> {
                Timber.i("Check media from keypress")
                showMediaCheckDialog()
                return true
            }

            KeyEvent.KEYCODE_E -> {
                if (event.isCtrlPressed) {
                    // Shortcut: CTRL + E
                    Timber.i("Show export dialog from keypress")
                    exportCollection()
                    return true
                }
            }

            KeyEvent.KEYCODE_I -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + I
                    Timber.i("Show import dialog from keypress")
                    showImportDialog()
                    return true
                }
            }

            KeyEvent.KEYCODE_N -> {
                if (event.isCtrlPressed && event.isShiftPressed) {
                    // Shortcut: CTRL + Shift + N
                    Timber.i("Open ManageNoteTypes from keypress")
                    viewModel.openManageNoteTypes()
                    return true
                }
            }

            else -> {}
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Displays a confirmation dialog for deleting deck.
     */
    private fun showDeleteDeckConfirmationDialog() =
        launchCatchingTask {
            val focusedDeck =
                viewModel.focusedDeck ?: run {
                    Timber.w("no focused deck")
                    return@launchCatchingTask
                }

            val (deckName, totalCards, isFilteredDeck) =
                withCol {
                    Triple(
                        decks.name(focusedDeck),
                        decks.cardCount(focusedDeck, includeSubdecks = true),
                        decks.isFiltered(focusedDeck),
                    )
                }
            val confirmDeleteDeckDialog =
                DeckPickerConfirmDeleteDeckDialog.newInstance(
                    deckName = deckName,
                    deckId = focusedDeck,
                    totalCards = totalCards,
                    isFilteredDeck = isFilteredDeck,
                )
            showDialogFragment(confirmDeleteDeckDialog)
        }

    /**
     * Perform the following tasks:
     * Automatic backup
     * loadStudyOptionsFragment() if tablet
     * Automatic sync
     */
    private fun onFinishedStartup() {
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
                val message =
                    """
                    ${res.getString(R.string.full_sync_confirmation_upgrade)}
                    
                    ${res.getString(R.string.full_sync_confirmation)}
                    """.trimIndent()

                dialogHandler.sendMessage(OneWaySyncDialog(message).toMessage())
            }
        } else {
            launchCatchingTask {
                if (!automaticSync()) {
                    BackupPromptDialog.showIfAvailable(this@DeckPicker)
                }
            }
        }
    }

    private fun showCollectionErrorDialog() {
        dialogHandler.sendMessage(CollectionLoadingErrorDialog().toMessage())
    }

    // VisibleForTesting: method is mocked, should be replaced
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun addNote(did: DeckId? = null) {
        viewModel.addNote(did, true)
    }

    private fun showStartupScreensAndDialogs(
        preferences: SharedPreferences,
        skip: Int,
    ) {
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
            val previous: Long =
                if (preferences.contains(UPGRADE_VERSION_KEY)) {
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
                val mediaDb =
                    File(
                        CollectionHelper.getCurrentAnkiDroidDirectory(this),
                        "collection.media.ad.db2",
                    )
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
                    val notetypes = getColUnsafe.notetypes
                    for (noteType in notetypes.all()) {
                        val css = noteType.css
                        @Suppress("SpellCheckingInspection")
                        if (css.contains("font-familiy")) {
                            noteType.css = css.replace("font-familiy", "font-family")
                            notetypes.save(noteType)
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
        duration: Int = Snackbar.LENGTH_LONG,
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

    @SuppressLint("UseKtx") // keep SharedPreferences.edit() instead of edit {} fot tests
    fun getPreviousVersion(
        preferences: SharedPreferences,
        current: Long,
    ): Long {
        var previous: Long
        try {
            previous = preferences.getLong(UPGRADE_VERSION_KEY, current)
        } catch (e: ClassCastException) {
            Timber.w(e)
            previous =
                try {
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

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     */
    override fun showSyncErrorDialog(dialogType: SyncErrorDialog.Type) {
        showSyncErrorDialog(dialogType, "")
    }

    /**
     * Show a specific sync error dialog
     * @param dialogType id of dialog to show
     * @param message text to show
     */
    override fun showSyncErrorDialog(
        dialogType: SyncErrorDialog.Type,
        message: String?,
    ) {
        val newFragment: AsyncDialogFragment = newInstance(dialogType, message)
        showAsyncDialogFragment(newFragment, Channel.SYNC)
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
            val result =
                withProgress(resources.getString(R.string.backup_repair_deck_progress)) {
                    withCol {
                        Timber.i("RepairCollection: Closing collection")
                        close()
                        BackupManager.repairCollection(this@withCol)
                    }
                }
            if (!result) {
                showThemedToast(
                    this@DeckPicker,
                    resources.getString(R.string.deck_repair_error),
                    true,
                )
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
        handleDatabaseCheck()
    }

    override fun mediaCheck() {
        showMediaCheckDialog()
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
        baseContext.sharedPrefs()

        val hkey = Prefs.hkey
        if (hkey.isNullOrEmpty()) {
            Timber.w("User not logged in")
            viewModel.isSyncing.value = false
            showSyncErrorDialog(SyncErrorDialog.Type.DIALOG_USER_NOT_LOGGED_IN_SYNC)
            return
        }

        MyAccount.checkNotificationPermission(this, notificationPermissionLauncher)

        /** Nested function that makes the connection to
         * the sync server and starts syncing the data */
        fun doSync() {
            handleNewSync(conflict, shouldFetchMedia())
        }
        // Warn the user in case the connection is metered
        if (!Prefs.allowSyncOnMeteredConnections && isActiveNetworkMetered()) {
            AlertDialog.Builder(this).show {
                message(R.string.metered_sync_data_warning)
                positiveButton(R.string.dialog_continue) { doSync() }
                negativeButton(R.string.dialog_cancel)
                checkBoxPrompt(R.string.button_do_not_show_again) { isCheckboxChecked ->
                    Prefs.allowSyncOnMeteredConnections = isCheckboxChecked
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

    /**
     * Refresh the deck picker when the SD card is inserted.
     */
    override val broadcastsActions =
        super.broadcastsActions +
            mapOf(
                SdCardReceiver.MEDIA_MOUNT to { ActivityCompat.recreate(this) },
            )

    fun openAnkiWebSharedDecks() {
        if (!NetworkUtils.isOnline) {
            showSnackbar(R.string.check_network)
            Timber.d("DeckPicker:: No network, Shared deck download failed")
            return
        }
        val intent = Intent(this, SharedDecksActivity::class.java)
        startActivity(intent)
    }

    private fun openStudyOptionsActivity(withDeckOptions: Boolean) {
        val intent = Intent(this, StudyOptionsComposeActivity::class.java)
        intent.putExtra("withDeckOptions", withDeckOptions)
        reviewLauncher.launch(intent)
    }

    /**
     * @see DeckPickerViewModel.updateDeckList
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    fun updateDeckList() {
        launchCatchingTask {
            viewModel.updateDeckList()?.join()
        }
    }

    fun exportDeck(did: DeckId) {
        ExportDialogFragment.newInstance(did).show(supportFragmentManager, "exportOptions")
    }

    private fun createIcon(
        context: Context,
        did: DeckId,
    ) {
        // This code should not be reachable with lower versions
        val shortcut =
            ShortcutInfoCompat
                .Builder(this, did.toString())
                .setIntent(
                    intentToReviewDeckFromShortcuts(context, did),
                ).setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
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
            showThemedToast(
                this,
                getString(R.string.create_shortcut_error, e.localizedMessage),
                false,
            )
        }
    }

    /** Disables the shortcut of the deck and the children belonging to it.*/
    @NeedsTest("ensure collapsed decks are also deleted")
    private fun disableDeckAndChildrenShortcuts(did: DeckId) {
        // Get the DeckId and all child DeckIds
        val deckTreeDids = dueTree?.find(did)?.map { it.did.toString() } ?: listOf()
        val errorMessage: CharSequence = getString(R.string.deck_shortcut_doesnt_exist)
        ShortcutManagerCompat.disableShortcuts(this, deckTreeDids, errorMessage)
    }

    fun renameDeckDialog(did: DeckId) {
        val currentName = getColUnsafe.decks.name(did)
        val createDeckDialog =
            CreateDeckDialog(
                this@DeckPicker,
                R.string.rename_deck,
                CreateDeckDialog.DeckDialogType.RENAME_DECK,
                null,
            )
        createDeckDialog.deckName = currentName
        createDeckDialog.onNewDeckCreated = {
            dismissAllDialogFragments()
            viewModel.updateDeckList()
        }
        createDeckDialog.showDialog()
    }

    /**
     * Displays a dialog for creating a new deck.
     *
     * @see CreateDeckDialog
     */
    fun showCreateDeckDialog() {
        val createDeckDialog =
            CreateDeckDialog(
                this@DeckPicker,
                R.string.new_deck,
                CreateDeckDialog.DeckDialogType.DECK,
                null,
            )
        createDeckDialog.onNewDeckCreated = {
            updateDeckList()
            invalidateOptionsMenu()
        }
        createDeckDialog.showDialog()
    }

    /**
     * Deletes the provided deck, child decks, and all cards inside.
     * @param did ID of the deck to delete
     */
    fun deleteDeck(did: DeckId) =
        launchCatchingTask {
            withProgress {
                viewModel.deleteDeck(did).join()
            }
        }

    @NeedsTest("14285: regression test to ensure UI is updated after this call")
    fun rebuildFiltered(did: DeckId) {
        launchCatchingTask {
            withProgress {
                withCol {
                    Timber.d("rebuildFiltered: doInBackground - RebuildCram")
                    decks.select(did)
                    sched.rebuildFilteredDeck(decks.selected())
                }
            }
            updateDeckList()
        }
    }

    private fun emptyFiltered(did: DeckId) {
        launchCatchingTask {
            withProgress {
                viewModel.emptyFilteredDeck(did).join()
            }
        }
    }

    override fun onAttachedToWindow() {
        if (!fragmented) {
            val window = window
            window.setFormat(PixelFormat.RGBA_8888)
        }
    }

    private fun openReviewer() {
        val intent = Reviewer.getIntent(this)
        reviewLauncher.launch(intent)
    }

    private fun createSubDeckDialog(did: DeckId) {
        val createDeckDialog =
            CreateDeckDialog(
                this@DeckPicker,
                R.string.create_subdeck,
                CreateDeckDialog.DeckDialogType.SUB_DECK,
                did,
            )
        createDeckDialog.onNewDeckCreated = {
            // a deck was created
            dismissAllDialogFragments()
            viewModel.updateDeckList()
            invalidateOptionsMenu()
        }
        createDeckDialog.showDialog()
    }

    override val shortcuts
        get() =
            ShortcutGroup(
                listOfNotNull(
                    shortcut("A", R.string.menu_add_note),
                    shortcut("B", R.string.card_browser_context_menu),
                    shortcut("Y", R.string.pref_cat_sync),
                    shortcut("/", R.string.deck_conf_cram_search),
                    shortcut("S", Translations::decksStudyDeck),
                    shortcut("T", R.string.open_statistics),
                    shortcut("C", R.string.check_db),
                    shortcut("D", R.string.new_deck),
                    shortcut("F", R.string.new_dynamic_deck),
                    if (fragmented) {
                        shortcut(
                            "DEL",
                            R.string.contextmenu_deckpicker_delete_deck,
                        )
                    } else {
                        null
                    },
                    if (fragmented) {
                        shortcut(
                            "Shift+DEL",
                            R.string.delete_deck_without_confirmation,
                        )
                    } else {
                        null
                    },
                    if (fragmented) shortcut("R", R.string.rename_deck) else null,
                    shortcut("P", R.string.open_settings),
                    shortcut("M", R.string.check_media),
                    shortcut("Ctrl+E", R.string.export_collection),
                    shortcut("Ctrl+Shift+I", R.string.menu_import),
                    shortcut("Ctrl+Shift+N", R.string.model_browser_label),
                ),
                R.string.deck_picker_group,
            )

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

        private const val PREF_DECK_PICKER_PANE_WEIGHT = "deckPickerPaneWeight"
        private const val PREF_STUDY_OPTIONS_PANE_WEIGHT = "studyOptionsPaneWeight"
    }

    override fun opExecuted(
        changes: OpChanges,
        handler: Any?,
    ) {
        // undo state may have changed
        invalidateOptionsMenu()
        if (changes.studyQueues && handler != this && handler != viewModel) {
            if (!activityPaused) {
                // No need to update while the activity is paused, because `onResume` calls `refreshState` that calls `updateDeckList`.
                updateDeckList()
            }
        }
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

    /**
     * Returns if the deck and its subdecks are all empty.
     *
     * @param did The id of a deck with no pending cards to review
     */

    override fun getApkgFileImportResultLauncher(): ActivityResultLauncher<Intent> = apkgFileImportResultLauncher

    override fun getCsvFileImportResultLauncher(): ActivityResultLauncher<Intent> = csvImportResultLauncher
}

/** Android's onCreateOptionsMenu does not play well with coroutines, as
 * it expects the menu to have been fully configured by the time the routine
 * returns. This results in flicker, as the menu gets blanked out, and then
 * configured a moment later when the coroutine runs. To work around this,
 * the current state is stored in the deck picker so that we can redraw the
 * menu immediately. */
data class OptionsMenuState(
    /** If undo is available, a string describing the action. */
    val undoLabel: String?,
    val syncIcon: SyncIconState,
    val undoAvailable: Boolean,
    val isColEmpty: Boolean,
)

enum class SyncIconState {
    Normal,
    PendingChanges,
    OneWay,
    NotLoggedIn,
}

class CollectionLoadingErrorDialog :
    DialogHandlerMessage(
        WhichDialogHandler.MSG_SHOW_COLLECTION_LOADING_ERROR_DIALOG,
        "CollectionLoadErrorDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Collection could not be opened
        activity.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
    }

    override fun toMessage() = emptyMessage(this.what)
}

class OneWaySyncDialog(
    val message: String?,
) : DialogHandlerMessage(
        which = WhichDialogHandler.MSG_SHOW_ONE_WAY_SYNC_DIALOG,
        analyticName = "OneWaySyncDialog",
    ) {
    override fun handleAsyncMessage(activity: AnkiActivity) {
        // Confirmation dialog for one-way sync
        val dialog = ConfirmationDialog()
        val confirm =
            Runnable {
                // Bypass the check once the user confirms
                CollectionManager.getColUnsafe().modSchemaNoCheck()
            }
        dialog.setConfirm(confirm)
        dialog.setArgs(message)
        activity.showDialogFragment(dialog)
    }

    override fun toMessage(): Message =
        Message.obtain().apply {
            what = this@OneWaySyncDialog.what
            data = bundleOf("message" to message)
        }

    companion object {
        fun fromMessage(message: Message): DialogHandlerMessage = OneWaySyncDialog(message.data.getString("message"))
    }
}

/**
 * [launchCatchingTask], showing a one-way sync dialog: [R.string.full_sync_confirmation]
 */
private fun AnkiActivity.launchCatchingRequiringOneWaySync(block: suspend () -> Unit) =
    launchCatchingTask {
        try {
            block()
        } catch (e: ConfirmModSchemaException) {
            e.log()

            // .also is used to ensure the activity is used as context
            val confirmModSchemaDialog =
                ConfirmationDialog().also { dialog ->
                    dialog.setArgs(message = getString(R.string.full_sync_confirmation))
                    dialog.setConfirm {
                        launchCatchingTask {
                            withCol { modSchemaNoCheck() }
                            block()
                        }
                    }
                }
            showDialogFragment(confirmModSchemaDialog)
        }
    }
