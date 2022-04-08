/****************************************************************************************
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>                          *
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>                              *
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>                           *
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>                                   *
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

package com.ichi2.anki.multimediacard.activity

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.*
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.beolingus.parsing.BeolingusParser
import com.ichi2.anki.multimediacard.language.LanguageListerBeolingus
import com.ichi2.anki.runtimetools.TaskOperations.stopTaskGracefully
import com.ichi2.anki.web.HttpFetcher.downloadFileToSdCard
import com.ichi2.anki.web.HttpFetcher.fetchThroughHttp
import com.ichi2.async.Connection
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.KotlinCleanup
import org.intellij.lang.annotations.Language
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

/**
 * Activity to load pronunciation files from Beolingus.
 * <p>
 * User picks a source language and the source is passed as extra.
 * <p>
 * When activity finished, it passes the filepath as another extra to the caller.
 * FIXME why isn't this extending AnkiActivity?
 */
@KotlinCleanup("lateinit")
open class LoadPronunciationActivity : Activity(), DialogInterface.OnCancelListener {
    var source: String? = null
    private var mTranslationAddress: String? = null
    private var mPronunciationAddress: String? = null
    private var mMp3Address: String? = null
    private var mActivity: LoadPronunciationActivity? = null
    private var mLanguageLister: LanguageListerBeolingus? = null
    private var mSpinnerFrom: Spinner? = null
    private var mMainLayout: LinearLayout? = null
    private var mLoadingLayoutTitle: TextView? = null
    private var mLoadingLayoutMessage: TextView? = null
    private var mLoadingLayout: View? = null
    private var mPostTranslation: BackgroundPost? = null
    private var mPostPronunciation: BackgroundPost? = null
    private var mDownloadMp3Task: DownloadFileTask? = null
    private var mStopped = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableXiaomiForceDarkMode(this)
        if (isUserATestClient) {
            finishCancel()
            return
        }
        if (savedInstanceState != null) {
            val b = savedInstanceState.getBoolean(BUNDLE_KEY_SHUT_OFF, false)
            if (b) {
                finishCancel()
                return
            }
        }
        setContentView(R.layout.activity_load_pronounciation)
        source = intent.extras!!.getString(EXTRA_SOURCE)
        mMainLayout = findViewById(R.id.layoutInLoadPronActivity)
        mLoadingLayout = findViewById(R.id.progress_bar_layout)
        mLoadingLayoutTitle = findViewById(R.id.progress_bar_layout_title)
        mLoadingLayoutMessage = findViewById(R.id.progress_bar_layout_message)
        mLanguageLister = LanguageListerBeolingus()
        mSpinnerFrom = Spinner(this)
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            mLanguageLister!!.languages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mSpinnerFrom!!.adapter = adapter
        mMainLayout!!.addView(mSpinnerFrom)
        val buttonLoadPronunciation = Button(this)
        buttonLoadPronunciation.text = gtxt(R.string.multimedia_editor_pron_load)
        mMainLayout!!.addView(buttonLoadPronunciation)
        buttonLoadPronunciation.setOnClickListener(this@LoadPronunciationActivity::onLoadPronunciation)
        val saveButton = Button(this)
        saveButton.text = "Save"
        saveButton.setOnClickListener { }
        mActivity = this
        mStopped = false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.activity_load_pronounciation, menu)
        return true
    }

    private fun showProgressBar(message: String) {
        showProgressBar(gtxt(R.string.multimedia_editor_progress_wait_title), message)
    }

    private fun showProgressBar(title: CharSequence, message: CharSequence) {
        mMainLayout!!.visibility = View.GONE
        mLoadingLayout!!.visibility = View.VISIBLE
        mLoadingLayoutTitle!!.text = title
        mLoadingLayoutMessage!!.text = message
    }

    private fun hideProgressBar() {
        mLoadingLayout!!.visibility = View.GONE
        mMainLayout!!.visibility = View.VISIBLE
    }

    /**
     * @param v Start of the story.
     */
    @Suppress("deprecation") // #7108: AsyncTask
    private fun onLoadPronunciation(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (!Connection.isOnline()) {
            showToast(gtxt(R.string.network_no_connection))
            return
        }
        val message = gtxt(R.string.multimedia_editor_searching_word)
        showProgressBar(message)
        mTranslationAddress = computeAddressOfTranslationPage()
        try {
            mPostTranslation = BackgroundPost()
            mPostTranslation!!.address = mTranslationAddress
            // post.setStopper(PRONUNC_STOPPER);
            mPostTranslation!!.execute()
        } catch (e: Exception) {
            Timber.w(e)
            hideProgressBar()
            showToast(gtxt(R.string.multimedia_editor_something_wrong))
        }
    }

    /**
     * @author zaur This class is used two times. First time from Beolingus it requests a page with the word
     * translation. Second time it loads a page with the link to mp3 pronunciation file.
     */
    @Suppress("deprecation") // #7108: AsyncTask
    protected inner class BackgroundPost : android.os.AsyncTask<Void?, Void?, String?>() {
        /**
         * @return Used to know, which of the posts finished, to differentiate.
         *
         * @param address Used to set the download address
         */
        var address: String? = null

        // private String mStopper;
        override fun doInBackground(vararg p0: Void?): String {
            // TMP CODE for quick testing
            // if (mAddress.contentEquals(mTranslationAddress))
            // {
            // return MockTranslationFetcher.get();
            // }
            // else if (mAddress.contentEquals(mPronunciationAddress))
            // {
            // return MockPronounciationPageFetcher.get();
            // }

            // Should be just this
            return fetchThroughHttp(address, "ISO-8859-1")
        }

        override fun onPostExecute(@Language("HTML") result: String?) {
            // Result here is the whole HTML of the page
            // this is passed to ask for address and differentiate, which of the
            // post has finished.
            processPostFinished(this, result!!)
        }
    }

    /**
     * @author zaur This is to load finally the MP3 file with pronunciation.
     */
    @Suppress("deprecation") // #7108: AsyncTask
    @KotlinCleanup("make mAddress lateInit")
    private inner class DownloadFileTask : android.os.AsyncTask<Void?, Void?, String?>() {
        private var mAddress: String? = null
        override fun doInBackground(vararg p0: Void?): String {
            return downloadFileToSdCard(mAddress!!, mActivity!!, "pronunc")
        }

        fun setAddress(address: String?) {
            mAddress = address
        }

        override fun onPostExecute(result: String?) {
            receiveMp3File(result)
        }
    }

    @Suppress("deprecation") // #7108: AsyncTask
    protected fun processPostFinished(post: BackgroundPost, @Language("HTML") result: String) {
        if (mStopped) {
            return
        }

        // First call returned
        // Means we get the page with the word translation,
        // And we have to start fetching the page with pronunciation
        if (post.address.contentEquals(mTranslationAddress)) {
            if (result.startsWith("FAILED")) {
                failNoPronunciation()
                return
            }
            mPronunciationAddress = BeolingusParser.getPronunciationAddressFromTranslation(result, source)
            if (mPronunciationAddress.contentEquals("no")) {
                failNoPronunciation()
                if (!source!!.lowercase(Locale.getDefault()).contentEquals(source)) {
                    showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case))
                }
                return
            }
            try {
                showProgressBar(gtxt(R.string.multimedia_editor_pron_looking_up))
                mPostPronunciation = BackgroundPost()
                mPostPronunciation!!.address = mPronunciationAddress
                mPostPronunciation!!.execute()
            } catch (e: Exception) {
                Timber.w(e)
                hideProgressBar()
                showToast(gtxt(R.string.multimedia_editor_something_wrong))
            }
            return
        }

        // Else
        // second call returned
        // This is a call when pronunciation page has been fetched.
        // We chekc if mp3 file could be downloaded and download it.
        if (post.address.contentEquals(mPronunciationAddress)) {
            // else here = pronunciation post returned;
            mMp3Address = BeolingusParser.getMp3AddressFromPronunciation(result)
            if (mMp3Address.contentEquals("no")) {
                failNoPronunciation()
                return
            }

            // Download MP3 file
            try {
                showProgressBar(gtxt(R.string.multimedia_editor_general_downloading))
                mDownloadMp3Task = DownloadFileTask()
                mDownloadMp3Task!!.setAddress(mMp3Address)
                mDownloadMp3Task!!.execute()
            } catch (e: Exception) {
                Timber.w(e)
                hideProgressBar()
                showToast(gtxt(R.string.multimedia_editor_something_wrong))
            }
        }
    }

    // This is called when MP3 Download is finished.
    fun receiveMp3File(result: String?) {
        if (mStopped) {
            return
        }
        if (result == null) {
            failNoPronunciation()
            return
        }
        if (result.startsWith("FAIL")) {
            failNoPronunciation()
            return
        }
        hideProgressBar()
        showToast(gtxt(R.string.multimedia_editor_general_done))
        val resultData = Intent()
        resultData.putExtra(EXTRA_PRONUNCIATION_FILE_PATH, result)
        setResult(RESULT_OK, resultData)
        finish()
    }

    private fun finishCancel() {
        val resultData = Intent()
        setResult(RESULT_CANCELED, resultData)
        finish()
    }

    private fun failNoPronunciation() {
        stop(gtxt(R.string.multimedia_editor_error_word_not_found))
        mPronunciationAddress = "no"
        mMp3Address = "no"
    }

    private fun stop(string: String) {
        hideProgressBar()
        showToast(string)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true)
    }

    private fun computeAddressOfTranslationPage(): String {
        // Service name has to be replaced from the language lister.
        var address = "https://dict.tu-chemnitz.de/dings.cgi?lang=en&service=SERVICE&opterrors=0&optpro=0&query=Welt"
        val strFrom = mSpinnerFrom!!.selectedItem.toString()
        val langCodeFrom = mLanguageLister!!.getCodeFor(strFrom)
        val query: String? = try {
            URLEncoder.encode(source, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            Timber.w(e)
            source!!.replace(" ", "%20")
        }
        address = address.replace("SERVICE".toRegex(), langCodeFrom!!).replace("Welt".toRegex(), query!!)
        return address
    }

    private fun showToast(text: CharSequence) {
        showThemedToast(this, text, true)
    }

    private fun showToastLong(text: CharSequence) {
        showThemedToast(this, text, false)
    }

    // If the loading and dialog are cancelled
    override fun onCancel(dialog: DialogInterface) {
        mStopped = true
        hideProgressBar()
        stopAllTasks()
        val resultData = Intent()
        setResult(RESULT_CANCELED, resultData)
        finish()
    }

    @Suppress("deprecation") // #7108: AsyncTask
    private fun stopAllTasks() {
        var t: android.os.AsyncTask<*, *, *>? = mPostTranslation
        stopTaskGracefully(t)
        t = mPostPronunciation
        stopTaskGracefully(t)
        t = mDownloadMp3Task
        stopTaskGracefully(t)
    }

    override fun onPause() {
        super.onPause()
        hideProgressBar()
        stopAllTasks()
    }

    private fun gtxt(id: Int): String {
        return getText(id).toString()
    }

    companion object {
        private const val BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off"

        // Must be passed in
        const val EXTRA_SOURCE = "com.ichi2.anki.LoadPronounciationActivity.extra.source"

        // Passed out as a result
        const val EXTRA_PRONUNCIATION_FILE_PATH = "com.ichi2.anki.LoadPronounciationActivity.extra.pronun.file.path"
    }
}
