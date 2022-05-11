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
import android.widget.*
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.core.JsonProcessingException
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.AnkiSerialization
import com.ichi2.anki.R
import com.ichi2.anki.UIUtils.showThemedToast
import com.ichi2.anki.databinding.ActivityTranslationBinding
import com.ichi2.anki.databinding.ProgressBarLayoutBinding
import com.ichi2.anki.multimediacard.glosbe.json.Response
import com.ichi2.anki.multimediacard.language.LanguagesListerGlosbe
import com.ichi2.anki.multimediacard.language.LanguagesListerGlosbe.Companion.requestToResponseLangCode
import com.ichi2.anki.runtimetools.TaskOperations.stopTaskGracefully
import com.ichi2.anki.web.HttpFetcher.fetchThroughHttp
import com.ichi2.async.Connection
import com.ichi2.libanki.Utils
import com.ichi2.themes.Themes.disableXiaomiForceDarkMode
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.AdaptionUtil
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

/**
 * Activity used now with Glosbe.com to enable translation of words.
 * FIXME why isn't this extending from our base classes?
 */
@KotlinCleanup("lateinit")
open class TranslationActivity : FragmentActivity(), DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private lateinit var binding: ActivityTranslationBinding
    private lateinit var progressBarLayoutBinding: ProgressBarLayoutBinding
    private var mSource: String? = null
    private var mTranslation: String? = null
    private var mLanguageLister: LanguagesListerGlosbe? = null
    private var mSpinnerFrom: Spinner? = null
    private var mSpinnerTo: Spinner? = null
    private var mWebServiceAddress: String? = null
    private var mPossibleTranslations: ArrayList<String>? = null
    private var mLangCodeTo: String? = null
    private var mTranslationLoadPost: BackgroundPost? = null
    private fun finishCancel() {
        val resultData = Intent()
        setResult(RESULT_CANCELED, resultData)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableXiaomiForceDarkMode(this)
        if (AdaptionUtil.isUserATestClient) {
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
        binding = ActivityTranslationBinding.inflate(layoutInflater)
        progressBarLayoutBinding = ProgressBarLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mSource = try {
            intent.extras!!.getString(EXTRA_SOURCE)
        } catch (e: Exception) {
            Timber.w(e)
            ""
        }

        // If translation fails this is a default - source will be returned.
        mTranslation = mSource
        val tv: TextView = FixedTextView(this)
        tv.text = getText(R.string.multimedia_editor_trans_poweredglosbe)
        binding.MainLayoutInTranslationActivity.addView(tv)
        val tvFrom: TextView = FixedTextView(this)
        tvFrom.text = getText(R.string.multimedia_editor_trans_from)
        binding.MainLayoutInTranslationActivity.addView(tvFrom)
        mLanguageLister = LanguagesListerGlosbe()
        mSpinnerFrom = Spinner(this)
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            mLanguageLister!!.languages
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mSpinnerFrom!!.adapter = adapter
        binding.MainLayoutInTranslationActivity.addView(mSpinnerFrom)
        val tvTo: TextView = FixedTextView(this)
        tvTo.text = getText(R.string.multimedia_editor_trans_to)
        binding.MainLayoutInTranslationActivity.addView(tvTo)
        mSpinnerTo = Spinner(this)
        val adapterTo = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            mLanguageLister!!.languages
        )
        adapterTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mSpinnerTo!!.adapter = adapterTo
        binding.MainLayoutInTranslationActivity.addView(mSpinnerTo)
        val preferences = AnkiDroidApp.getSharedPrefs(baseContext)

        // Try to set spinner value to last selected position
        val fromLang = preferences.getString("translatorLastLanguageFrom", "")
        val toLang = preferences.getString("translatorLastLanguageTo", "")
        mSpinnerFrom!!.setSelection(getSpinnerIndex(mSpinnerFrom!!, fromLang))
        mSpinnerTo!!.setSelection(getSpinnerIndex(mSpinnerTo!!, toLang))
        // Setup button
        val btnDone = Button(this)
        btnDone.text = getText(R.string.multimedia_editor_trans_translate)
        btnDone.setOnClickListener {
            // Remember currently selected language
            val fromLang1 = mSpinnerFrom!!.selectedItem.toString()
            val toLang1 = mSpinnerTo!!.selectedItem.toString()
            preferences.edit().putString("translatorLastLanguageFrom", fromLang1).apply()
            preferences.edit().putString("translatorLastLanguageTo", toLang1).apply()
            // Get translation
            translate()
        }
        binding.MainLayoutInTranslationActivity.addView(btnDone)
    }

    private fun showProgressBar(title: CharSequence, message: CharSequence) {
        binding.MainLayoutInTranslationActivity.visibility = View.GONE
        progressBarLayoutBinding.progressBarLayout.visibility = View.VISIBLE
        progressBarLayoutBinding.progressBarLayoutTitle.text = title
        progressBarLayoutBinding.progressBarLayoutMessage.text = message
    }

    private fun hideProgressBar() {
        progressBarLayoutBinding.progressBarLayout.visibility = View.GONE
        binding.MainLayoutInTranslationActivity.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.activity_translation, menu)
        return true
    }

    @Suppress("deprecation") // #7108: AsyncTask
    private inner class BackgroundPost : android.os.AsyncTask<Void?, Void?, String>() {
        override fun doInBackground(vararg params: Void?): String {
            return fetchThroughHttp(mWebServiceAddress)
        }

        override fun onPostExecute(result: String) {
            hideProgressBar()
            mTranslation = result
            showPickTranslationDialog()
        }
    }

    @Suppress("deprecation") // #7108: AsyncTask
    protected fun translate() {
        if (!Connection.isOnline()) {
            showToast(gtxt(R.string.network_no_connection))
            return
        }
        showProgressBar(
            getText(R.string.multimedia_editor_progress_wait_title),
            getText(R.string.multimedia_editor_trans_translating_online)
        )
        mWebServiceAddress = computeAddress()
        try {
            mTranslationLoadPost = BackgroundPost()
            mTranslationLoadPost!!.execute()
        } catch (e: Exception) {
            Timber.w(e)
            hideProgressBar()
            showToast(getText(R.string.multimedia_editor_something_wrong))
        }
    }

    private fun computeAddress(): String {
        var address = "https://glosbe.com/gapi/translate?from=FROMLANG&dest=TOLANG&format=json&phrase=SOURCE&pretty=true"
        val strFrom = mSpinnerFrom!!.selectedItem.toString()
        // Conversion to iso, lister created before.
        val langCodeFrom = mLanguageLister!!.getCodeFor(strFrom)
        val strTo = mSpinnerTo!!.selectedItem.toString()
        mLangCodeTo = mLanguageLister!!.getCodeFor(strTo)
        val query: String? = try {
            URLEncoder.encode(mSource, "utf-8")
        } catch (e: UnsupportedEncodingException) {
            Timber.w(e)
            mSource!!.replace(" ", "%20")
        }
        address = address.replace("FROMLANG".toRegex(), langCodeFrom!!).replace("TOLANG".toRegex(), mLangCodeTo!!)
            .replace("SOURCE".toRegex(), query!!)
        return address
    }

    private fun gtxt(id: Int): String {
        return getText(id).toString()
    }

    private fun showToastLong(text: CharSequence) {
        showThemedToast(this, text, false)
    }

    private fun showPickTranslationDialog() {
        if (mTranslation!!.startsWith("FAILED")) {
            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString())
            return
        }
        val objectMapper = AnkiSerialization.objectMapper

        val resp: Response? = try {
            objectMapper.readValue(mTranslation, Response::class.java)
        } catch (e: JsonProcessingException) {
            Timber.w(e)
            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString())
            return
        }

        if (resp == null) {
            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString())
            return
        }

        if (!resp.result!!.contentEquals("ok")) {
            if (!mSource!!.lowercase(Locale.getDefault()).contentEquals(mSource)) {
                showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case))
            }

            returnFailure(getText(R.string.multimedia_editor_trans_getting_failure).toString())
            return
        }

        mPossibleTranslations = parseJson(resp, mLangCodeTo)

        if (mPossibleTranslations!!.isEmpty()) {
            if (!mSource!!.lowercase(Locale.getDefault()).contentEquals(mSource)) {
                showToastLong(gtxt(R.string.multimedia_editor_word_search_try_lower_case))
            }

            returnFailure(getText(R.string.multimedia_editor_error_word_not_found).toString())
            return
        }

        val fragment = PickStringDialogFragment()

        fragment.setChoices(mPossibleTranslations)
        fragment.setOnclickListener(this)
        fragment.setTitle(getText(R.string.multimedia_editor_trans_pick_translation).toString())

        fragment.show(this.supportFragmentManager, "pick.translation")
    }

    private fun returnTheTranslation() {
        val resultData = Intent()
        resultData.putExtra(EXTRA_TRANSLATION, mTranslation)
        setResult(RESULT_OK, resultData)
        finish()
    }

    private fun returnFailure(explanation: String) {
        showToast(explanation)
        setResult(RESULT_CANCELED)
        hideProgressBar()
        finish()
    }

    private fun showToast(text: CharSequence) {
        showThemedToast(this, text, true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(BUNDLE_KEY_SHUT_OFF, true)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        mTranslation = mPossibleTranslations!![which]
        returnTheTranslation()
    }

    override fun onCancel(dialog: DialogInterface) {
        stopWorking()
    }

    private fun stopWorking() {
        stopTaskGracefully(mTranslationLoadPost)
        hideProgressBar()
    }

    override fun onPause() {
        super.onPause()
        stopWorking()
    }

    private fun getSpinnerIndex(spinner: Spinner, myString: String?): Int {
        var index = 0
        for (i in 0 until spinner.count) {
            if (spinner.getItemAtPosition(i) == myString) {
                index = i
            }
        }
        return index
    }

    companion object {
        private const val BUNDLE_KEY_SHUT_OFF = "key.multimedia.shut.off"

        // Something to translate
        const val EXTRA_SOURCE = "translation.activity.extra.source"

        // Translated result
        const val EXTRA_TRANSLATION = "translation.activity.extra.translation"
        private fun parseJson(resp: Response, languageCodeTo: String?): ArrayList<String> {

            /*
             * The algorithm below includes the parsing of glosbe results. Glosbe.com returns a list of different phrases in
             * source and destination languages. This is done, probably, to improve the reader's understanding. We leave
             * here only the translations to the destination language.
             */
            KotlinCleanup("mapNotNull")
            val tucs = resp.tuc ?: return ArrayList(0)
            val res = ArrayList<String>(tucs.size)
            val desiredLang = requestToResponseLangCode(languageCodeTo!!)
            for (tuc in tucs) {
                if (tuc == null) {
                    continue
                }
                val meanings = tuc.meanings
                if (meanings != null) {
                    for (meaning in meanings) {
                        if (meaning == null) {
                            continue
                        }
                        if (meaning.language == null) {
                            continue
                        }
                        if (meaning.language!!.contentEquals(desiredLang)) {
                            val unescapedString = Utils.unescape(meaning.text)
                            res.add(unescapedString)
                        }
                    }
                }
                val phrase = tuc.phrase
                if (phrase != null) {
                    if (phrase.language == null) {
                        continue
                    }
                    if (phrase.language!!.contentEquals(desiredLang)) {
                        val unescapedString = Utils.unescape(phrase.text)
                        res.add(unescapedString)
                    }
                }
            }
            return res
        }
    }
}
