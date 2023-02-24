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

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.multimediacard.beolingus.parsing.BeolingusParser
import com.ichi2.anki.multimediacard.language.LanguageListerBeolingus
import com.ichi2.anki.web.HttpFetcher.downloadFileToSdCard
import com.ichi2.anki.web.HttpFetcher.fetchThroughHttp
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode
import com.ichi2.utils.AdaptionUtil.isUserATestClient
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Activity to load pronunciation files from Beolingus.
 * <p>
 * User picks a source language and the source is passed as extra.
 * <p>
 * When activity finished, it passes the filepath as another extra to the caller.
 */
open class LoadPronunciationActivity : AnkiActivity(), DialogInterface.OnCancelListener {
    private var mStopped = false
    private lateinit var source: String
    private lateinit var mTranslationAddress: String
    private lateinit var mPronunciationAddress: String
    private lateinit var mMp3Address: String
    private lateinit var mActivity: LoadPronunciationActivity
    private lateinit var mLoadingLayoutTitle: TextView
    private lateinit var mLoadingLayoutMessage: TextView
    private lateinit var mLoadingLayout: View
    private lateinit var mMainLayout: LinearLayout
    private lateinit var postTranslationJob: Job
    private lateinit var postPronunciationJob: Job
    private lateinit var downloadMp3Job: Job
    private lateinit var mLanguageLister: LanguageListerBeolingus
    private lateinit var mSpinnerFrom: Spinner
    private val unknownExceptionHandler = CoroutineExceptionHandler { _, e ->
        Timber.w(e)
        hideProgressBar()
        showToast(gtxt(R.string.multimedia_editor_something_wrong))
    }

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
        source = intent.extras!!.getString(EXTRA_SOURCE)!!.trim()
        mMainLayout = findViewById(R.id.layoutInLoadPronActivity)
        mLoadingLayout = findViewById(R.id.progress_bar_layout)
        mLoadingLayoutTitle = findViewById(R.id.progress_bar_layout_title)
        mLoadingLayoutMessage = findViewById(R.id.progress_bar_layout_message)
        mLanguageLister = LanguageListerBeolingus()
        mSpinnerFrom = Spinner(this)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            mLanguageLister.languages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mSpinnerFrom.adapter = adapter
        mMainLayout.addView(mSpinnerFrom)
        val buttonLoadPronunciation = Button(this)
        buttonLoadPronunciation.text = gtxt(R.string.multimedia_editor_pron_load)
        mMainLayout.addView(buttonLoadPronunciation)
        buttonLoadPronunciation.setOnClickListener(this@LoadPronunciationActivity::onLoadPronunciation)
        val saveButton = Button(this)
        saveButton.text = "Save"
        saveButton.setOnClickListener { }
        mActivity = this
        mStopped = false
        enableToolbar()
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
        mMainLayout.visibility = View.GONE
        mLoadingLayout.visibility = View.VISIBLE
        mLoadingLayoutTitle.text = title
        mLoadingLayoutMessage.text = message
    }

    override fun hideProgressBar() {
        mLoadingLayout.visibility = View.GONE
        mMainLayout.visibility = View.VISIBLE
    }

    /**
     * @param v Start of the story.
     */
    private fun onLoadPronunciation(@Suppress("UNUSED_PARAMETER") v: View?) {
        if (!NetworkUtils.isOnline) {
            showToast(gtxt(R.string.network_no_connection))
            return
        }
        val message = gtxt(R.string.multimedia_editor_searching_word)
        showProgressBar(message)
        mTranslationAddress = computeAddressOfTranslationPage()
        lifecycleScope.launch(unknownExceptionHandler) { backgroundPost(mTranslationAddress) }
    }

    /**
     * This method is used two times. First time from Beolingus it requests a page with the word
     * translation. Second time it loads a page with the link to mp3 pronunciation file.
     */
    private suspend fun backgroundPost(address: String) = withContext(ioDispatcher) {
        // TMP CODE for quick testing
        // val response = if (address.contentEquals(mTranslationAddress))
        // {
        //  MockTranslationFetcher.get();
        // }
        // else if (address.contentEquals(mPronunciationAddress))
        // {
        //  MockPronounciationPageFetcher.get();
        // }

        // Should be just this
        val result = fetchThroughHttp(address, "ISO-8859-1")
        Timber.d("Fetched response")
        // Result here is the whole HTML of the page
        // address is passed to ask and differentiate, which of the
        // post has finished.
        withContext(mainDispatcher) { processPostFinished(address, result) }
    }

    /**
     *  This is to load finally the MP3 file with pronunciation.
     */
    private suspend fun downloadFile(address: String) = withContext(ioDispatcher) {
        val result = downloadFileToSdCard(address, mActivity, "pronunc")
        Timber.d("Fetched mp3")
        withContext(mainDispatcher) { receiveMp3File(result) }
    }

    protected fun processPostFinished(address: String, result: String) {
        if (mStopped) {
            return
        }

        // First call returned
        // Means we get the page with the word translation,
        // And we have to start fetching the page with pronunciation
        if (address.contentEquals(mTranslationAddress)) {
            if (result.startsWith("FAILED")) {
                failNoPronunciation()
                return
            }
            mPronunciationAddress = BeolingusParser.getPronunciationAddressFromTranslation(result, source.trim())
            if (mPronunciationAddress.contentEquals("no")) {
                failNoPronunciation()
                if (source.contains(" ")) {
                    showToastLong(gtxt(R.string.multimedia_editor_only_one_word))
                } else if (source.any { it.isUpperCase() }) {
                    showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case))
                }
                return
            }
            showProgressBar(gtxt(R.string.multimedia_editor_pron_looking_up))
            postPronunciationJob = lifecycleScope.launch(unknownExceptionHandler) {
                backgroundPost(mPronunciationAddress)
            }
            return
        }

        // Else
        // second call returned
        // This is a call when pronunciation page has been fetched.
        // We check if mp3 file could be downloaded and download it.
        if (address.contentEquals(mPronunciationAddress)) {
            // else here = pronunciation post returned;
            mMp3Address = BeolingusParser.getMp3AddressFromPronunciation(result)
            if (mMp3Address.contentEquals("no")) {
                failNoPronunciation()
                return
            }
            // Download MP3 file
            showProgressBar(gtxt(R.string.multimedia_editor_general_downloading))
            downloadMp3Job = lifecycleScope.launch(unknownExceptionHandler) {
                downloadFile(mMp3Address)
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
        finishWithoutAnimation()
    }

    private fun finishCancel() {
        val resultData = Intent()
        setResult(RESULT_CANCELED, resultData)
        finishWithoutAnimation()
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
        val strFrom = mSpinnerFrom.selectedItem.toString()
        val langCodeFrom = mLanguageLister.getCodeFor(strFrom)
        val query: String? = try {
            URLEncoder.encode(source, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            Timber.w(e)
            source.replace(" ", "%20")
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
        finishWithoutAnimation()
    }

    private fun stopAllTasks() {
        if (this::postPronunciationJob.isInitialized) {
            postPronunciationJob.cancel()
        }
        if (this::postTranslationJob.isInitialized) {
            postTranslationJob.cancel()
        }
        if (this::downloadMp3Job.isInitialized) {
            downloadMp3Job.cancel()
        }
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

        private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        private var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

        @VisibleForTesting
        fun setTestDispatchers(dispatcher: CoroutineDispatcher) {
            ioDispatcher = dispatcher
            mainDispatcher = dispatcher
        }
    }
}
