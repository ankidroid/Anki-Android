//noinspection MissingCopyrightHeader #8659
@file:Suppress("LeakingThis") // fine - used as WeakReference

package com.ichi2.anki

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.Animation
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.AttrRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_LIGHT
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_SYSTEM
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anim.ActivityTransitionAnimation.Direction.DEFAULT
import com.ichi2.anim.ActivityTransitionAnimation.Direction.NONE
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.android.input.Shortcut
import com.ichi2.anki.android.input.ShortcutGroup
import com.ichi2.anki.android.input.ShortcutGroupProvider
import com.ichi2.anki.android.input.shortcut
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.SimpleMessageDialog.SimpleMessageDialogListener
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.receiver.SdCardReceiver
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.async.CollectionLoader
import com.ichi2.compat.CompatHelper.Companion.registerReceiverCompat
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.compat.customtabs.CustomTabsFallback
import com.ichi2.compat.customtabs.CustomTabsHelper
import com.ichi2.libanki.Collection
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import androidx.browser.customtabs.CustomTabsIntent.Builder as CustomTabsIntentBuilder

@UiThread
@KotlinCleanup("set activityName")
open class AnkiActivity : AppCompatActivity, SimpleMessageDialogListener, ShortcutGroupProvider, AnkiActivityProvider {

    /**
     * Receiver that informs us when a broadcast listen in [broadcastsActions] is received.
     *
     * @see registerReceiver
     * @see broadcastsActions
     */
    private var broadcastReceiver: BroadcastReceiver? = null

    var importColpkgListener: ImportColpkgListener? = null

    /** The name of the parent class (example: 'Reviewer')  */
    private val activityName: String
    val dialogHandler = DialogHandler(this)
    override val ankiActivity = this

    private val customTabActivityHelper: CustomTabActivityHelper = CustomTabActivityHelper()

    constructor() : super() {
        activityName = javaClass.simpleName
    }

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId) {
        activityName = javaClass.simpleName
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    override fun onCreate(savedInstanceState: Bundle?) {
        // The hardware buttons should control the music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Set the theme
        Themes.setTheme(this)
        Themes.disableXiaomiForceDarkMode(this)
        super.onCreate(savedInstanceState)
        // Disable the notifications bar if running under the test monkey.
        if (AdaptionUtil.isUserATestClient) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = getColor(R.color.transparent)
        }
    }

    override fun onStart() {
        super.onStart()
        customTabActivityHelper.bindCustomTabsService(this)
    }

    override fun onStop() {
        super.onStop()
        customTabActivityHelper.unbindCustomTabsService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        broadcastReceiver?.let { unregisterReceiver(it) }
    }

    override fun onResume() {
        super.onResume()
        UsageAnalytics.sendAnalyticsScreenView(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
            SIMPLE_NOTIFICATION_ID
        )
        // Show any pending dialogs which were stored persistently
        dialogHandler.executeMessage()
    }

    /**
     * Sets the title of the toolbar (support action bar) for the activity.
     *
     * @param title The new title to be set for the toolbar.
     */
    open fun setToolbarTitle(title: String) {
        supportActionBar?.title = title
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.i("Home button pressed")
            return onActionBarBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun onActionBarBackPressed(): Boolean {
        Timber.v("onActionBarBackPressed")
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // called when the CollectionLoader finishes... usually will be over-ridden
    protected open fun onCollectionLoaded(col: Collection) {
        hideProgressBar()
    }

    /**
     * Maps from intent name action to function to run when this action is received by [broadcastReceiver].
     * By default it handles [SdCardReceiver.MEDIA_EJECT], and shows/dismisses dialogs when an SD
     * card is ejected/remounted (collection is saved beforehand by [SdCardReceiver])
     */
    protected open val broadcastsActions = mapOf(
        SdCardReceiver.MEDIA_EJECT to { onSdCardNotMounted() }
    )

    /**
     * Register a broadcast receiver, associating an intent to an action as in [broadcastsActions].
     * Add more values in [broadcastsActions] to react to more intents.
     */
    fun registerReceiver() {
        if (broadcastReceiver != null) {
            // Receiver already registered
            return
        }
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcastsActions[intent.action]?.invoke()
            }
        }.also {
            val iFilter = IntentFilter()
            broadcastsActions.keys.map(iFilter::addAction)
            registerReceiverCompat(it, iFilter, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    protected fun onSdCardNotMounted() {
        showThemedToast(this, resources.getString(R.string.sd_card_not_mounted), false)
        finish()
    }

    /** Legacy code should migrate away from this, and use withCol {} instead.
     * */
    val getColUnsafe: Collection
        get() = CollectionManager.getColUnsafe()

    fun colIsOpenUnsafe(): Boolean {
        return CollectionManager.isOpenUnsafe()
    }

    /**
     * Whether animations should not be displayed
     * This is used to improve the UX for e-ink devices
     * Can be tested via Settings - Advanced - Safe display mode
     *
     * @see .animationEnabled
     */
    fun animationDisabled(): Boolean {
        val preferences = this.sharedPrefs()
        return preferences.getBoolean("safeDisplay", false)
    }

    /**
     * Whether animations should be displayed
     * This is used to improve the UX for e-ink devices
     * Can be tested via Settings - Advanced - Safe display mode
     *
     * @see .animationDisabled
     */
    fun animationEnabled(): Boolean {
        return !animationDisabled()
    }

    override fun setContentView(view: View?) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.setContentView(view)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.setContentView(view, params)
    }

    override fun addContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (animationDisabled()) {
            view?.clearAnimation()
        }
        super.addContentView(view, params)
    }

    override fun startActivity(intent: Intent) {
        startActivityWithAnimation(intent, DEFAULT)
    }

    fun startActivityWithoutAnimation(intent: Intent) {
        disableIntentAnimation(intent)
        super.startActivity(intent)
        disableActivityAnimation()
    }

    fun startActivityWithAnimation(
        intent: Intent,
        animation: Direction
    ) {
        enableIntentAnimation(intent)
        super.startActivity(intent)
        enableActivityAnimation(animation)
    }

    private fun launchActivityForResult(
        intent: Intent?,
        launcher: ActivityResultLauncher<Intent?>,
        animation: Direction?
    ) {
        try {
            launcher.launch(
                intent,
                ActivityTransitionAnimation.getAnimationOptions(this, animation)
            )
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
            this.showSnackbar(R.string.activity_start_failed)
        }
    }

    override fun finish() {
        finishWithAnimation(DEFAULT)
    }

    fun finishWithAnimation(animation: Direction) {
        Timber.i("finishWithAnimation %s", animation)
        super.finish()
        enableActivityAnimation(animation)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun disableViewAnimation(view: View) {
        view.clearAnimation()
    }

    protected fun enableViewAnimation(view: View, animation: Animation?) {
        if (animationDisabled()) {
            disableViewAnimation(view)
        } else {
            view.animation = animation
        }
    }

    private fun disableIntentAnimation(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    private fun disableActivityAnimation() {
        ActivityTransitionAnimation.slide(this, NONE)
    }

    @KotlinCleanup("Maybe rename this? This only disables the animation conditionally")
    private fun enableIntentAnimation(intent: Intent) {
        if (animationDisabled()) {
            disableIntentAnimation(intent)
        }
    }

    private fun enableActivityAnimation(animation: Direction) {
        if (animationDisabled()) {
            disableActivityAnimation()
        } else {
            ActivityTransitionAnimation.slide(this, animation)
        }
    }

    /** Method for loading the collection which is inherited by every [AnkiActivity]  */
    fun startLoadingCollection() {
        Timber.d("AnkiActivity.startLoadingCollection()")
        if (colIsOpenUnsafe()) {
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
                onCollectionLoadError()
            }
        }
    }

    /** The action to take when there was an error loading the collection  */
    fun onCollectionLoadError() {
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.putExtra("collectionLoadError", true) // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(deckPicker)
    }

    fun showProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.VISIBLE
        }
    }

    open fun hideProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.GONE
        }
    }

    internal fun mayOpenUrl(url: Uri) {
        val success = customTabActivityHelper.mayLaunchUrl(url, null, null)
        if (!success) {
            Timber.w("Couldn't preload url: %s", url.toString())
        }
    }

    /**
     * Opens a URL in a custom tab, with fallback to a browser if no custom tab implementation is available.
     *
     * This method first checks if there is a web browser available on the device. If no browser is found,
     * a snackbar message is displayed informing the user. If a browser is available, a custom tab is
     * opened with customized appearance and animations.
     *
     * @param url The URI to be opened.
     */
    @KotlinCleanup("toast -> snackbar")
    open fun openUrl(url: Uri) {
        if (!AdaptionUtil.hasWebBrowser(this)) {
            showSnackbar(getString(R.string.no_browser_msg, url.toString()))
            return
        }
        val toolbarColor = MaterialColors.getColor(this, R.attr.appBarColor, 0)
        val navBarColor = MaterialColors.getColor(this, R.attr.customTabNavBarColor, 0)
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .setNavigationBarColor(navBarColor)
            .build()
        val builder = CustomTabsIntentBuilder(customTabActivityHelper.session)
            .setShowTitle(true)
            .setStartAnimations(this, R.anim.slide_right_in, R.anim.slide_left_out)
            .setExitAnimations(this, R.anim.slide_left_in, R.anim.slide_right_out)
            .setCloseButtonIcon(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.drawable.ic_back_arrow_custom_tab
                )
            )
            .setColorScheme(customTabsColorScheme)
            .setDefaultColorSchemeParams(colorSchemeParams)
        val customTabsIntent = builder.build()
        CustomTabsHelper.addKeepAliveExtra(this, customTabsIntent.intent)
        CustomTabActivityHelper.openCustomTab(this, customTabsIntent, url, CustomTabsFallback())
    }

    fun openUrl(urlString: String) {
        openUrl(Uri.parse(urlString))
    }

    fun openUrl(@StringRes url: Int) {
        openUrl(getString(url))
    }

    private val customTabsColorScheme: Int
        get() = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            COLOR_SCHEME_SYSTEM
        } else if (Themes.currentTheme.isNightMode) {
            COLOR_SCHEME_DARK
        } else {
            COLOR_SCHEME_LIGHT
        }

    /**
     * Global method to show dialog fragment including adding it to back stack Note: DO NOT call this from an async
     * task! If you need to show a dialog from an async task, use showAsyncDialogFragment()
     *
     * @param newFragment  the DialogFragment you want to show
     */
    open fun showDialogFragment(newFragment: DialogFragment) {
        runOnUiThread {
            showDialogFragment(this, newFragment)
        }
    }

    /**
     * Calls [.showAsyncDialogFragment] internally, using the channel
     * [Channel.GENERAL]
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     */
    open fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
        showAsyncDialogFragment(newFragment, Channel.GENERAL)
    }

    /**
     * Global method to show a dialog fragment including adding it to back stack and handling the case where the dialog
     * is shown from an async task, by showing the message in the notification bar if the activity was stopped before the
     * AsyncTask completed
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     * @param channel the Channel to use for the notification
     */
    fun showAsyncDialogFragment(
        newFragment: AsyncDialogFragment,
        channel: Channel
    ) {
        try {
            showDialogFragment(newFragment)
        } catch (e: IllegalStateException) {
            Timber.w("failed to show fragment, activity is likely paused. Sending notification")
            // Store a persistent message to SharedPreferences instructing AnkiDroid to show dialog
            DialogHandler.storeMessage(newFragment.dialogHandlerMessage?.toMessage())
            // Show a basic notification to the user in the notification bar in the meantime
            val title = newFragment.notificationTitle
            val message = newFragment.notificationMessage
            showSimpleNotification(title, message, channel)
        }
    }

    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     *
     * @param message
     * @param reload flag which forces app to be restarted when true
     */
    open fun showSimpleMessageDialog(
        message: String,
        title: String = "",
        reload: Boolean = false
    ) {
        val newFragment: AsyncDialogFragment =
            SimpleMessageDialog.newInstance(title, message, reload)
        showAsyncDialogFragment(newFragment)
    }

    fun showSimpleNotification(
        title: String,
        message: String?,
        channel: Channel
    ) {
        val prefs = this.sharedPrefs()
        // Show a notification unless all notifications have been totally disabled
        if (prefs.getString(getString(R.string.pref_notifications_minimum_cards_due_key), "0")!!
            .toInt() <= PENDING_NOTIFICATIONS_ONLY
        ) {
            // Use the title as the ticker unless the title is simply "AnkiDroid"
            val ticker: String? = if (title == resources.getString(R.string.app_name)) {
                message
            } else {
                title
            }
            // Build basic notification
            val builder = NotificationCompat.Builder(
                this,
                channel.id
            )
                .setSmallIcon(R.drawable.ic_star_notify)
                .setContentTitle(title)
                .setContentText(message)
                .setColor(this.getColor(R.color.material_light_blue_500))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setTicker(ticker)
            // Enable vibrate and blink if set in preferences
            if (prefs.getBoolean("widgetVibrate", false)) {
                builder.setVibrate(longArrayOf(1000, 1000, 1000))
            }
            if (prefs.getBoolean("widgetBlink", false)) {
                builder.setLights(Color.BLUE, 1000, 1000)
            }
            // Creates an explicit intent for an Activity in your app
            val resultIntent = Intent(this, DeckPicker::class.java)
            resultIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val resultPendingIntent = PendingIntentCompat.getActivity(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false
            )
            builder.setContentIntent(resultPendingIntent)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            // mId allows you to update the notification later on.
            notificationManager.notify(SIMPLE_NOTIFICATION_ID, builder.build())
        }
    }

    // Handle closing simple message dialog
    override fun dismissSimpleMessageDialog(reload: Boolean) {
        dismissAllDialogFragments()
        if (reload) {
            val deckPicker = Intent(this, DeckPicker::class.java)
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(deckPicker)
        }
    }

    // Dismiss whatever dialog is showing
    fun dismissAllDialogFragments() {
        // trying to pop fragment manager back state crashes if state already saved
        if (!supportFragmentManager.isStateSaved) {
            supportFragmentManager.popBackStack(
                DIALOG_FRAGMENT_TAG,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
    }

    // Show dialogs to deal with database loading issues etc
    open fun showDatabaseErrorDialog(errorDialogType: DatabaseErrorDialogType) {
        val newFragment: AsyncDialogFragment = DatabaseErrorDialog.newInstance(errorDialogType)
        showAsyncDialogFragment(newFragment)
    }

    /**
     * sets [.getSupportActionBar] and returns the action bar
     * @return The action bar which was created
     * @throws IllegalStateException if the bar could not be enabled
     */
    protected fun enableToolbar(): ActionBar {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
            ?: // likely missing "<include layout="@layout/toolbar" />"
            throw IllegalStateException("Unable to find toolbar")
        setSupportActionBar(toolbar)
        return supportActionBar!!
    }

    /**
     * sets [.getSupportActionBar] and returns the action bar
     * @param view the view which contains a toolbar element:
     * @return The action bar which was created
     * @throws IllegalStateException if the bar could not be enabled
     */
    protected fun enableToolbar(view: View): ActionBar {
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
            ?: // likely missing "<include layout="@layout/toolbar" />"
            throw IllegalStateException("Unable to find toolbar: $view")
        setSupportActionBar(toolbar)
        return supportActionBar!!
    }

    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state -> super.onCreate(state) }
        )

    /** @see Window.setNavigationBarColor */
    @Suppress("deprecation", "API35 properly handle edge-to-edge")
    fun setNavigationBarColor(@AttrRes attr: Int) {
        window.navigationBarColor = Themes.getColorFromAttr(this, attr)
    }

    fun closeCollectionAndFinish() {
        Timber.i("closeCollectionAndFinish()")
        Timber.i("closeCollection: %s", "AnkiActivity:closeCollectionAndFinish()")
        CollectionManager.closeCollectionBlocking()
        finish()
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>,
        menu: Menu?,
        deviceId: Int
    ) {
        val shortcutGroups = getShortcuts()
        data.addAll(shortcutGroups)
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }

    /**
     * Shows keyboard shortcuts dialog
     */
    fun showKeyboardShortcutsDialog() {
        val shortcutsGroup = getShortcuts()
        // Don't show keyboard shortcuts dialog if there is no available shortcuts and also
        // if there's 1 item because shortcutsGroup always includes generalShortcutGroup.
        if (shortcutsGroup.size <= 1) return
        Timber.i("displaying keyboard shortcut screen")
        requestShowKeyboardShortcuts()
    }

    /**
     * Get current activity keyboard shortcuts
     */
    fun getShortcuts(): List<KeyboardShortcutGroup> {
        val generalShortcutGroup = ShortcutGroup(
            listOf(
                shortcut("Alt+K", R.string.show_keyboard_shortcuts_dialog),
                shortcut("Ctrl+Z", R.string.undo)
            ),
            R.string.pref_cat_general
        ).toShortcutGroup(this)

        return listOfNotNull(shortcuts?.toShortcutGroup(this), generalShortcutGroup)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isAltPressed && keyCode == KeyEvent.KEYCODE_K) {
            showKeyboardShortcutsDialog()
            return true
        }

        val done = super.onKeyUp(keyCode, event)

        if (done || shortcuts == null) return false

        // Show snackbar only if the current activity have shortcuts, a modifier key is pressed and the keyCode is an unmapped alphabet or num key
        if (Shortcut.isPotentialShortcutCombination(event, keyCode)) {
            showSnackbar(R.string.show_shortcuts_message, Snackbar.LENGTH_SHORT)
            return true
        }
        return false
    }

    /**
     * If storage permissions are not granted, shows a toast message and finishes the activity.
     *
     * This should be called AFTER a call to `super.`[onCreate]
     *
     * @return `true`: activity may continue to start, `false`: [onCreate] should stop executing
     * as storage permissions are mot granted
     */
    fun ensureStoragePermissions(): Boolean {
        if (IntentHandler.grantedStoragePermissions(this, showToast = true)) {
            return true
        }
        Timber.w("finishing activity. No storage permission")
        finish()
        return false
    }

    override val shortcuts
        get(): ShortcutGroup? = null

    companion object {
        const val DIALOG_FRAGMENT_TAG = "dialog"

        /** Extra key to set the finish animation of an activity  */
        const val FINISH_ANIMATION_EXTRA = "finishAnimation"

        fun showDialogFragment(activity: AnkiActivity, newFragment: DialogFragment) {
            showDialogFragment(activity.supportFragmentManager, newFragment)
        }

        fun showDialogFragment(manager: FragmentManager, newFragment: DialogFragment) {
            // DialogFragment.show() will take care of adding the fragment
            // in a transaction. We also want to remove any currently showing
            // dialog, so make our own transaction and take care of that here.
            val ft = manager.beginTransaction()
            val prev = manager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
            if (prev != null) {
                ft.remove(prev)
            }
            // save transaction to the back stack
            ft.addToBackStack(DIALOG_FRAGMENT_TAG)
            newFragment.show(ft, DIALOG_FRAGMENT_TAG)
            manager.executePendingTransactions()
        }

        private const val SIMPLE_NOTIFICATION_ID = 0
    }
}

fun Fragment.requireAnkiActivity(): AnkiActivity {
    return requireActivity() as? AnkiActivity?
        ?: throw java.lang.IllegalStateException("Fragment $this not attached to an AnkiActivity.")
}

interface AnkiActivityProvider {
    val ankiActivity: AnkiActivity
}
