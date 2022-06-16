//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.*
import android.view.animation.Animation
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anim.ActivityTransitionAnimation
import com.ichi2.anim.ActivityTransitionAnimation.getAnimationOptions
import com.ichi2.anim.ActivityTransitionAnimation.slide
import com.ichi2.anki.NotificationChannels.getId
import com.ichi2.anki.UIUtils.showSimpleSnackbar
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.analytics.UsageAnalytics.sendAnalyticsScreenView
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.DialogHandler
import com.ichi2.anki.dialogs.DialogHandler.Companion.storeMessage
import com.ichi2.anki.dialogs.SimpleMessageDialog.Companion.newInstance
import com.ichi2.anki.dialogs.SimpleMessageDialog.SimpleMessageDialogListener
import com.ichi2.async.CollectionLoader
import com.ichi2.async.CollectionLoader.Companion.load
import com.ichi2.compat.CompatHelper.Companion.compat
import com.ichi2.compat.customtabs.CustomTabActivityHelper
import com.ichi2.compat.customtabs.CustomTabActivityHelper.Companion.openCustomTab
import com.ichi2.compat.customtabs.CustomTabsFallback
import com.ichi2.compat.customtabs.CustomTabsHelper.addKeepAliveExtra
import com.ichi2.libanki.Collection
import com.ichi2.libanki.CollectionGetter
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode
import com.ichi2.themes.Themes.getColorFromAttr
import com.ichi2.themes.Themes.setTheme
import com.ichi2.utils.AdaptionUtil.hasWebBrowser
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.AndroidUiUtils.isRunningOnTv
import timber.log.Timber

@Suppress("DEPRECATION")
open class AnkiActivity : AppCompatActivity, SimpleMessageDialogListener, CollectionGetter {
    val SIMPLE_NOTIFICATION_ID = 0

    /** The name of the parent class (Reviewer)  */
    private val mActivityName: String
    val dialogHandler = DialogHandler(this)

    // custom tabs
    var customTabActivityHelper: CustomTabActivityHelper? = null
        private set

    constructor() : super() {
        mActivityName = javaClass.simpleName
    }

    constructor(@LayoutRes contentLayoutId: Int) : super(contentLayoutId) {
        mActivityName = javaClass.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.i("AnkiActivity::onCreate - %s", mActivityName)
        // The hardware buttons should control the music volume
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Set the theme
        setTheme(this)
        disableXiaomiForceDarkMode(this)
        super.onCreate(savedInstanceState)
        // Disable the notifications bar if running under the test monkey.
        if (isUserATestClient) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
        customTabActivityHelper = CustomTabActivityHelper()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AnkiDroidApp.updateContextWithLanguage(base))
    }

    override fun onStart() {
        Timber.i("AnkiActivity::onStart - %s", mActivityName)
        super.onStart()
        customTabActivityHelper!!.bindCustomTabsService(this)
    }

    override fun onStop() {
        Timber.i("AnkiActivity::onStop - %s", mActivityName)
        super.onStop()
        customTabActivityHelper!!.unbindCustomTabsService(this)
    }

    override fun onPause() {
        Timber.i("AnkiActivity::onPause - %s", mActivityName)
        super.onPause()
    }

    override fun onResume() {
        Timber.i("AnkiActivity::onResume - %s", mActivityName)
        super.onResume()
        sendAnalyticsScreenView(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(
            SIMPLE_NOTIFICATION_ID
        )
        // Show any pending dialogs which were stored persistently
        dialogHandler.readMessage()
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

    override fun getCol(): Collection {
        return CollectionHelper.getInstance().getCol(this)
    }

    fun colIsOpen(): Boolean {
        return CollectionHelper.getInstance().colIsOpen()
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
        if (isRunningOnTv(this)) {
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

    @Deprecated("")
    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
    }

    fun startActivityWithoutAnimation(intent: Intent) {
        disableIntentAnimation(intent)
        super.startActivity(intent)
        disableActivityAnimation()
    }

    fun startActivityWithAnimation(
        intent: Intent,
        animation: ActivityTransitionAnimation.Direction
    ) {
        enableIntentAnimation(intent)
        super.startActivity(intent)
        enableActivityAnimation(animation)
    }

    @Deprecated("")
    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        try {
            super.startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
            showSimpleSnackbar(this, R.string.activity_start_failed, true)
        }
    }

    fun startActivityForResultWithoutAnimation(intent: Intent, requestCode: Int) {
        disableIntentAnimation(intent)
        startActivityForResult(intent, requestCode)
        disableActivityAnimation()
    }

    fun startActivityForResultWithAnimation(
        intent: Intent,
        requestCode: Int,
        animation: ActivityTransitionAnimation.Direction
    ) {
        enableIntentAnimation(intent)
        startActivityForResult(intent, requestCode)
        enableActivityAnimation(animation)
    }

    fun launchActivityForResult(
        intent: Intent?,
        launcher: ActivityResultLauncher<Intent?>,
        animation: ActivityTransitionAnimation.Direction?
    ) {
        try {
            launcher.launch(intent, getAnimationOptions(this, animation))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e)
            showSimpleSnackbar(this, R.string.activity_start_failed, true)
        }
    }

    fun launchActivityForResultWithoutAnimation(
        intent: Intent,
        launcher: ActivityResultLauncher<Intent?>
    ) {
        disableIntentAnimation(intent)
        launchActivityForResult(intent, launcher, ActivityTransitionAnimation.Direction.NONE)
    }

    fun launchActivityForResultWithAnimation(
        intent: Intent,
        launcher: ActivityResultLauncher<Intent?>,
        animation: ActivityTransitionAnimation.Direction?
    ) {
        enableIntentAnimation(intent)
        launchActivityForResult(intent, launcher, animation)
    }

    @Deprecated("")
    override fun finish() {
        super.finish()
    }

    fun finishWithoutAnimation() {
        Timber.i("finishWithoutAnimation")
        super.finish()
        disableActivityAnimation()
    }

    fun finishWithAnimation(animation: ActivityTransitionAnimation.Direction) {
        Timber.i("finishWithAnimation %s", animation)
        super.finish()
        enableActivityAnimation(animation)
    }

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
        slide(this, ActivityTransitionAnimation.Direction.NONE)
    }

    private fun enableIntentAnimation(intent: Intent) {
        if (animationDisabled()) {
            disableIntentAnimation(intent)
        }
    }

    private fun enableActivityAnimation(animation: ActivityTransitionAnimation.Direction) {
        if (animationDisabled()) {
            disableActivityAnimation()
        } else {
            slide(this, animation)
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
        load(
            this,
            object : CollectionLoader.Callback {
                override fun execute(col: Collection?) {
                    if (col != null) {
                        Timber.d("Asynchronously calling onCollectionLoaded")
                        onCollectionLoaded(col)
                    } else {
                        onCollectionLoadError()
                    }
                }
            }
        )
    }

    /** The action to take when there was an error loading the collection  */
    protected fun onCollectionLoadError() {
        val deckPicker = Intent(this, DeckPicker::class.java)
        deckPicker.putExtra("collectionLoadError", true) // don't currently do anything with this
        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityWithAnimation(deckPicker, ActivityTransitionAnimation.Direction.START)
    }

    fun showProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.VISIBLE
        }
    }

    fun hideProgressBar() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        if (progressBar != null) {
            progressBar.visibility = View.GONE
        }
    }

    fun mayOpenUrl(url: Uri) {
        val success = customTabActivityHelper!!.mayLaunchUrl(url, null, null)
        if (!success) {
            Timber.w("Couldn't preload url: %s", url.toString())
        }
    }

    open fun openUrl(url: Uri) {
        // DEFECT: We might want a custom view for the toast, given i8n may make the text too long for some OSes to
        // display the toast
        if (!hasWebBrowser(this)) {
            showThemedToast(
                this,
                resources.getString(R.string.no_browser_notification) + url,
                false
            )
            return
        }
        val toolbarColor = getColorFromAttr(this, R.attr.colorPrimary)
        val navBarColor = getColorFromAttr(this, R.attr.customTabNavBarColor)
        val colorSchemeParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .setNavigationBarColor(navBarColor)
            .build()
        val helper = customTabActivityHelper
        val builder = CustomTabsIntent.Builder(helper!!.session)
            .setShowTitle(true)
            .setStartAnimations(this, R.anim.slide_right_in, R.anim.slide_left_out)
            .setExitAnimations(this, R.anim.slide_left_in, R.anim.slide_right_out)
            .setCloseButtonIcon(
                BitmapFactory.decodeResource(
                    this.resources,
                    R.drawable.ic_back_arrow_custom_tab
                )
            )
            .setColorScheme(colorScheme)
            .setDefaultColorSchemeParams(colorSchemeParams)
        val customTabsIntent = builder.build()
        addKeepAliveExtra(this, customTabsIntent.intent)
        openCustomTab(this, customTabsIntent, url, CustomTabsFallback())
    }

    private val colorScheme: Int
        get() {
            val prefs = AnkiDroidApp.getSharedPrefs(this)
            return if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                CustomTabsIntent.COLOR_SCHEME_SYSTEM
            } else if (prefs.getBoolean("invertedColors", false)) {
                CustomTabsIntent.COLOR_SCHEME_DARK
            } else {
                CustomTabsIntent.COLOR_SCHEME_LIGHT
            }
        }

    /**
     * Global method to show dialog fragment including adding it to back stack Note: DO NOT call this from an async
     * task! If you need to show a dialog from an async task, use showAsyncDialogFragment()
     *
     * @param newFragment  the DialogFragment you want to show
     */
    open fun showDialogFragment(newFragment: DialogFragment) {
        showDialogFragment(this, newFragment)
    }

    /**
     * Calls [.showAsyncDialogFragment] internally, using the channel
     * [NotificationChannels.Channel.GENERAL]
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     */
    open fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
        showAsyncDialogFragment(newFragment, NotificationChannels.Channel.GENERAL)
    }

    /**
     * Global method to show a dialog fragment including adding it to back stack and handling the case where the dialog
     * is shown from an async task, by showing the message in the notification bar if the activity was stopped before the
     * AsyncTask completed
     *
     * @param newFragment  the AsyncDialogFragment you want to show
     * @param channel the NotificationChannels.Channel to use for the notification
     */
    fun showAsyncDialogFragment(
        newFragment: AsyncDialogFragment,
        channel: NotificationChannels.Channel?
    ) {
        try {
            showDialogFragment(newFragment)
        } catch (e: IllegalStateException) {
            Timber.w(e)
            // Store a persistent message to SharedPreferences instructing AnkiDroid to show dialog
            storeMessage(newFragment.dialogHandlerMessage)
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
     */
    fun showSimpleMessageDialog(message: String?) {
        showSimpleMessageDialog(message, false)
    }

    fun showSimpleMessageDialog(title: String?, message: String?) {
        showSimpleMessageDialog(title, message, false)
    }

    /**
     * Show a simple message dialog, dismissing the message without taking any further action when OK button is pressed.
     * If a DialogFragment cannot be shown due to the Activity being stopped then the message is shown in the
     * notification bar instead.
     *
     * @param message
     * @param reload flag which forces app to be restarted when true
     */
    open fun showSimpleMessageDialog(message: String?, reload: Boolean) {
        val newFragment: AsyncDialogFragment = newInstance(message, reload)
        showAsyncDialogFragment(newFragment)
    }

    fun showSimpleMessageDialog(title: String?, message: String?, reload: Boolean) {
        val newFragment: AsyncDialogFragment = newInstance(title, message, reload)
        showAsyncDialogFragment(newFragment)
    }

    fun showSimpleNotification(
        title: String?,
        message: String?,
        channel: NotificationChannels.Channel?
    ) {
        val prefs = AnkiDroidApp.getSharedPrefs(this)
        // Show a notification unless all notifications have been totally disabled
        if (prefs.getString(Preferences.MINIMUM_CARDS_DUE_FOR_NOTIFICATION, "0")!!
            .toInt() <= Preferences.PENDING_NOTIFICATIONS_ONLY
        ) {
            // Use the title as the ticker unless the title is simply "AnkiDroid"
            var ticker = title
            if (title == resources.getString(R.string.app_name)) {
                ticker = message
            }
            // Build basic notification
            val builder = NotificationCompat.Builder(
                this,
                getId(channel)
            )
                .setSmallIcon(R.drawable.ic_stat_notify)
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

    // Restart the activity
    fun restartActivity() {
        Timber.i("AnkiActivity -- restartActivity()")
        val intent = Intent()
        intent.setClass(this, this.javaClass)
        intent.putExtras(Bundle())
        startActivityWithoutAnimation(intent)
        finishWithoutAnimation()
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

    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?): Boolean {
        if (AnkiDroidApp.isInitialized()) {
            return false
        }

        // #7630: Can be triggered with `adb shell bmgr restore com.ichi2.anki` after AnkiDroid settings are changed.
        // Application.onCreate() is not called if:
        // * The App was open
        // * A restore took place
        // * The app is reopened (until it exits: finish() does not do this - and removes it from the app list)
        Timber.w("Activity started with no application instance")
        showThemedToast(
            this,
            getString(R.string.ankidroid_cannot_open_after_backup_try_again),
            false
        )

        // fixes: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
        // on Importer
        setTheme(this)
        // Avoids a SuperNotCalledException
        super.onCreate(savedInstanceState)
        finishActivityWithFade(this)

        // If we don't kill the process, the backup is not "done" and reopening the app show the same message.
        Thread {

            // 3.5 seconds sleep, as the toast is killed on process death.
            // Same as the default value of LENGTH_LONG
            try {
                Thread.sleep(3500)
            } catch (e: InterruptedException) {
                Timber.w(e)
            }
            Process.killProcess(Process.myPid())
        }.start()
        return true
    }

    companion object {
        const val REQUEST_REVIEW = 901
        const val DIALOG_FRAGMENT_TAG = "dialog"

        /** Extra key to set the finish animation of an activity  */
        const val FINISH_ANIMATION_EXTRA = "finishAnimation"

        /** Finish Activity using FADE animation  */
        fun finishActivityWithFade(activity: Activity) {
            activity.finish()
            slide(activity, ActivityTransitionAnimation.Direction.UP)
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
    }
}
