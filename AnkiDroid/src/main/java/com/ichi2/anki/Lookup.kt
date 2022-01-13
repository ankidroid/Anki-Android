//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.afollestad.materialdialogs.MaterialDialog
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.libanki.Utils
import com.ichi2.utils.KotlinCleanup
import timber.log.Timber

object Lookup {
    /**
     * Searches
     */
    private const val DICTIONARY_NONE = 0 // use no dictionary
    private const val DICTIONARY_AEDICT = 1 // Japanese dictionary
    private const val DICTIONARY_EIJIRO_WEB = 2 // japanese web dictionary
    /**
     *  German web dictionary for English, French, Spanish, Italian, Chinese, Russian
     */
    private const val DICTIONARY_LEO_WEB = 3
    /**
     *  German web dictionary for English, French, Spanish, Italian, Chinese, Russian
     */
    private const val DICTIONARY_LEO_APP = 4
    // Chinese, Russian
    private const val DICTIONARY_COLORDICT = 5
    private const val DICTIONARY_FORA = 6
    private const val DICTIONARY_NCIKU_WEB = 7 // chinese web dictionary
    @KotlinCleanup("lateinit")
    private var mContext: Context? = null
    @JvmStatic
    var isAvailable = false
        private set
    private var mDictionaryAction: String? = null
    private var mDictionary = 0
    private var mLookupText: String? = null
    @JvmStatic
    fun initialize(context: Context?): Boolean {
        mContext = context
        val preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().baseContext)
        mDictionary = preferences.getString("dictionary", DICTIONARY_NONE.toString())!!.toInt()
        when (mDictionary) {
            DICTIONARY_AEDICT -> {
                mDictionaryAction = "sk.baka.aedict.action.ACTION_SEARCH_EDICT"
                isAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction)
            }
            DICTIONARY_LEO_WEB, DICTIONARY_NCIKU_WEB, DICTIONARY_EIJIRO_WEB -> {
                mDictionaryAction = "android.intent.action.VIEW"
                isAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction)
            }
            DICTIONARY_LEO_APP -> {
                mDictionaryAction = "android.intent.action.SEND"
                isAvailable = Utils.isIntentAvailable(
                    mContext, mDictionaryAction,
                    ComponentName(
                        "org.leo.android.dict", "org.leo.android.dict.LeoDict"
                    )
                )
            }
            DICTIONARY_COLORDICT -> {
                mDictionaryAction = "colordict.intent.action.SEARCH"
                isAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction)
            }
            DICTIONARY_FORA -> {
                mDictionaryAction = "com.ngc.fora.action.LOOKUP"
                isAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction)
            }
            DICTIONARY_NONE -> isAvailable = false
            else -> isAvailable = false
        }
        Timber.v("Is intent available = %b", isAvailable)
        return isAvailable
    }

    @JvmStatic
    fun lookUp(textStr: String): Boolean {
        if (!isAvailable) {
            return false
        }
        // clear text from leading and closing dots, commas, brackets etc.
        val text = textStr.trim { it <= ' ' }.replace("[,;:\\s(\\[)\\].]*$".toRegex(), "").replace("^[,;:\\s(\\[)\\].]*".toRegex(), "")
        when (mDictionary) {
            DICTIONARY_NONE -> return false
            DICTIONARY_AEDICT -> {
                val aedictSearchIntent = Intent(mDictionaryAction)
                aedictSearchIntent.putExtra("kanjis", text)
                mContext!!.startActivity(aedictSearchIntent)
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.AEDICT)
                return true
            }
            DICTIONARY_LEO_WEB, DICTIONARY_LEO_APP -> {
                mLookupText = text
                // localisation is needless here since leo.org translates only into or out of German
                val itemValues = arrayOf<CharSequence>("en", "fr", "es", "it", "ch", "ru")
                val language = getLanguage()
                if (language.isNotEmpty()) {
                    for (itemValue in itemValues) {
                        if (language.contentEquals(itemValue)) {
                            lookupLeo(language, mLookupText)
                            mLookupText = ""
                            return true
                        }
                    }
                }
                val items = arrayOf("Englisch", "Französisch", "Spanisch", "Italienisch", "Chinesisch", "Russisch")
                MaterialDialog.Builder(mContext!!)
                    .title("\"$mLookupText\" nachschlagen")
                    .items(*items)
                    .itemsCallback { _: MaterialDialog?, _: View?, item: Int, _: CharSequence? ->
                        val language1 = itemValues[item].toString()
                        storeLanguage()
                        lookupLeo(language1, mLookupText)
                        mLookupText = ""
                    }
                    .build().show()
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.LEO)
                return true
            }
            DICTIONARY_COLORDICT -> {
                val colordictSearchIntent = Intent(mDictionaryAction)
                colordictSearchIntent.putExtra("EXTRA_QUERY", text)
                mContext!!.startActivity(colordictSearchIntent)
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.COLORDICT)
                return true
            }
            DICTIONARY_FORA -> {
                val foraSearchIntent = Intent(mDictionaryAction)
                foraSearchIntent.putExtra("HEADWORD", text.trim { it <= ' ' })
                mContext!!.startActivity(foraSearchIntent)
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.FORA)
                return true
            }
            DICTIONARY_NCIKU_WEB -> {
                val ncikuWebIntent = Intent(
                    mDictionaryAction,
                    Uri.parse(
                        "http://m.nciku.com/en/entry/?query=" +
                            text
                    )
                )
                mContext!!.startActivity(ncikuWebIntent)
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.NCIKU)
                return true
            }
            DICTIONARY_EIJIRO_WEB -> {
                val eijiroWebIntent = Intent(mDictionaryAction, Uri.parse("http://eow.alc.co.jp/$text"))
                mContext!!.startActivity(eijiroWebIntent)
                UsageAnalytics.sendAnalyticsEvent(Lookup::class.java.simpleName, UsageAnalytics.Actions.EIJIRO)
                return true
            }
        }
        return false
    }

    private fun lookupLeo(language: String, text: CharSequence?) {
        when (mDictionary) {
            DICTIONARY_LEO_WEB -> {
                val leoSearchIntent = Intent(
                    mDictionaryAction,
                    Uri.parse(
                        "http://pda.leo.org/?lp=" + language +
                            "de&search=" + text
                    )
                )
                mContext!!.startActivity(leoSearchIntent)
            }
            DICTIONARY_LEO_APP -> {
                val leoAppSearchIntent = Intent(mDictionaryAction)
                leoAppSearchIntent.putExtra("org.leo.android.dict.DICTIONARY", language + "de")
                leoAppSearchIntent.putExtra(Intent.EXTRA_TEXT, text)
                leoAppSearchIntent.component = ComponentName(
                    "org.leo.android.dict",
                    "org.leo.android.dict.LeoDict"
                )
                mContext!!.startActivity(leoAppSearchIntent)
            }
            else -> {
            }
        }
    }

    @Suppress("unused")
    val searchStringTitle: String
        get() = String.format(
            mContext!!.getString(R.string.menu_search),
            mContext!!.resources.getStringArray(R.array.dictionary_labels)[mDictionary]
        )

    @Suppress("unused")
    private fun getLanguage(): String {
        // if (mCurrentCard == null) {
        return ""
        // } else {
        // return MetaDB.getLanguage(mContext, mDeckFilename, Models.getModel(DeckManager.getMainDeck(),
        // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer);
        // }
    }

    @Suppress("unused")
    private fun storeLanguage() {
        // if (mCurrentCard != null) {
        // MetaDB.storeLanguage(mContext, mDeckFilename, Models.getModel(DeckManager.getMainDeck(),
        // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer, language);
        // }
    }
}
