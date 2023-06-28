//noinspection MissingCopyrightHeader #8659
@file:Suppress("LeakingThis") // fine - used as WeakReference

package com.ichi2.anki

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.animation.Animation
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.Direction
import com.ichi2.anim.ActivityTransitionAnimation.Direction.*
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.SimpleMessageDialog
import com.ichi2.anki.dialogs.SimpleMessageDialog.SimpleMessageDialogListener
import com.ichi2.anki.preferences.Preferences
import com.ichi2.anki.preferences.Preferences.Companion.MINIMUM_CARDS_DUE_FOR_NOTIFICATION
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.async.CollectionLoader
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.compat.customtabs.CustomTabsFallback
import com.ichi2.compat.customtabs.CustomTabsHelper
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionGetter
import com.ichi2.themes.Themes
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.AndroidUiUtils
import com.ichi2.utils.KotlinCleanup
import com.ichi2.utils.SyncStatus
import timber.log.Timber

@UiThread
open class AnkiActivity : AppCompatActivity, SimpleMessageDialogListener, CollectionGetter {

    /** The name of the parent class (example: 'Reviewer')  */
    private val mActivityName: String
    val dialogHandler = DialogHandler(this)

    private val customTabActivityHelper: CustomTabActivityHelper = CustomTabActivityHelper()

    constructor() : super() {
        mActivityName = javaClass.simpleName
    }

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId) {
        mActivityName = javaClass.simpleName
    }

    @Suppress("deprecation") // #9332: UI Visibility -> Insets
    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("AnkiActivity::onCreate - %s", mActivityName)
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
            window.navigationBarColor = ContextCompat.getColor(this, R.color.transparent)
        }
    }

    override fun onStart() {
        Timber.i("AnkiActivity::onStart - %s", mActivityName)
        super.onStart()
        customTabActivityHelper.bindCustomTabsService(this)
    }

    override fun onStop() {
        Timber.i("AnkiActivity::onStop - %s", mActivityName)
        super.onStop()
        customTabActivityHelper.unbindCustomTabsService(this)
    }

    override fun onPause() {
        Timber.i("AnkiActivity::onPause - %s", mActivityName)
        super.onPause()
    }

    override fun onResume() {
        Timber.i("AnkiActivity::onResume - %s", mActivityName)
        super.onResume()
        UsageAnalytics.sendAnalyticsScreenView(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
            SIMPLE_NOTIFICATION_ID
        )
        // Show any pending dialogs which were stored persistently
        dialogHandler.executeMessage()
    }

    override fun onDestroy() {
        Timber.i("AnkiActivity::onDestroy - %s", mActivityName)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.i("Home button pressed")
            return onActionBarBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun onActionBarBackPressed(): Boolean {
        finishWithoutAnimation()
        return true
    }

    // called when the CollectionLoader finishes... usually will be over-ridden
    protected open fun onCollectionLoaded(col: Collection) {
        hideProgressBar()
    }

    override val col: Collection
        get() = CollectionHelper.instance.getCol(this)!!

    fun colIsOpen(): Boolean {
        return CollectionHelper.instance.colIsOpen()
    }

    /**
     * Whether animations should not be displayed
     * This is used to improve the UX for e-ink devices
     * Can be tested via Settings - Advanced - Safe display mode
     *
     * @see .animationEnabled
     */
    fun animationDisabled(): Boolean {
        val preferences = AnkiDroidApp.getSharedPrefs(this)
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

    override fun setContentView(view: View) {
        if (animationDisabled()) {
            view.clearAnimation()
        }
        super.setContentView(view)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // We can't access the icons yet on a TV, so show them all in the menu
        if (AndroidUiUtils.isRunningOnTv(this)) {
            for (i in 0 until menu.size()) {
                menu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams) {
        if (animationDisabled()) {
            view.clearAnimation()
        }
        super.setContentView(view, params)
    }

    override fun addContentView(view: View, params: ViewGroup.LayoutParams) {
        if (animationDisabled()) {
            view.clearAnimation()
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

    @Deprecated("")
    @Suppress("DEPRECATION") // startActivityForResult
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        try {
            super.startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
            this.showSnackbar(R.string.activity_start_failed)
        }
    }

    @Suppress("DEPRECATION") // startActivityForResult
    fun startActivityForResultWithoutAnimation(intent: Intent, requestCode: Int) {
        disableIntentAnimation(intent)
        startActivityForResult(intent, requestCode)
        disableActivityAnimation()
    }

    @Suppress("DEPRECATION") // startActivityForResult
    fun startActivityForResultWithAnimation(
        intent: Intent,
        requestCode: Int,
        animation: Direction
    ) {
        enableIntentAnimation(intent)
        startActivityForResult(intent, requestCode)
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

    fun launchActivityForResultWithoutAnimation(
        intent: Intent,
        launcher: ActivityResultLauncher<Intent?>
    ) {
        disableIntentAnimation(intent)
        launchActivityForResult(intent, launcher, NONE)
    }

    fun launchActivityForResultWithAnimation(
        intent: Intent,
        launcher: ActivityResultLauncher<Intent?>,
        animation: Direction?
    ) {
        enableIntentAnimation(intent)
        launchActivityForResult(intent, launcher, animation)
    }

    override fun finish() {
        finishWithAnimation(DEFAULT)
    }

    fun finishWithoutAnimation() {
        Timber.i("finishWithoutAnimation")
        super.finish()
        disableActivityAnimation()
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
        if (colIsOpen()) {
            Timber.d("Synchronously calling onCollectionLoaded")
            onCollectionLoaded(col)
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
    protected fun onCollectionLoadError() {
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.putExtra("collectionLoadError", true) // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityWithAnimation(deckPicker, START)
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

    @KotlinCleanup("toast -> snackbar")
    open fun openUrl(url: Uri) {
        // DEFECT: We might want a custom view for the toast, given i8n may make the text too long for some OSes to
        // display the toast
        if (!AdaptionUtil.hasWebBrowser(this)) {
            @KotlinCleanup("check RTL with concat")
            showThemedToast(
                this,
                resources.getString(R.string.no_browser_notification) + url,
                false
            )
            return
        }
        val toolbarColor = Themes.getColorFromAttr(this, com.google.android.material.R.attr.colorPrimary)
        val navBarColor = Themes.getColorFromAttr(this, R.attr.customTabNavBarColor)
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .setNavigationBarColor(navBarColor)
            .build()
        val builder = CustomTabsIntent.Builder(customTabActivityHelper.session)
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
            Timber.w(e)
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
    @KotlinCleanup("make message non-null")
    open fun showSimpleMessageDialog(
        message: String?,
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
        val prefs = AnkiDroidApp.getSharedPrefs(this)
        // Show a notification unless all notifications have been totally disabled
        if (prefs.getString(MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "0")!!
            .toInt() <= Preferences.PENDING_NOTIFICATIONS_ONLY
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
                .setColor(ContextCompat.getColor(this, R.color.material_light_blue_500))
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
            val resultPendingIntent = compat.getImmutableActivityIntent(
                this,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
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
            startActivityWithoutAnimation(deckPicker)
        }
    }

    // Dismiss whatever dialog is showing
    fun dismissAllDialogFragments() {
        supportFragmentManager.popBackStack(
            DIALOG_FRAGMENT_TAG,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
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

    fun saveCollectionInBackground(syncIgnoresDatabaseModification: Boolean = false) {
        if (CollectionHelper.instance.colIsOpen()) {
            launchCatchingTask {
                Timber.d("saveCollectionInBackground: start")
                withCol {
                    Timber.d("doInBackgroundSaveCollection")
                    try {
                        if (syncIgnoresDatabaseModification) {
                            SyncStatus.ignoreDatabaseModification { col.save() }
                        } else {
                            col.save()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error on saving deck in background")
                        CrashReportService.sendExceptionReport(e, "AnkiActivity:: saveCollectionInBackground")
                    }
                }
                Timber.d("saveCollectionInBackground: finished")
            }
        }
    }

    companion object {
        const val REQUEST_REVIEW = 901
        const val DIALOG_FRAGMENT_TAG = "dialog"

        /** Extra key to set the finish animation of an activity  */
        const val FINISH_ANIMATION_EXTRA = "finishAnimation"

        /** Finish Activity using FADE animation  */
        fun finishActivityWithFade(activity: Activity) {
            activity.finish()
            ActivityTransitionAnimation.slide(activity, FADE)
        }

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
