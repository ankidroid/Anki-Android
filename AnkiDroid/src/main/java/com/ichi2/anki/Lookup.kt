//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import com.afollestad.materialdialogs.MaterialDialog;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.libanki.Utils;

import timber.log.Timber;

public class Lookup {

    /**
     * Searches
     */
    private static final int DICTIONARY_NONE = 0;    // use no dictionary
    private static final int DICTIONARY_AEDICT = 1;  // Japanese dictionary
    private static final int DICTIONARY_EIJIRO_WEB = 2; // japanese web dictionary
    private static final int DICTIONARY_LEO_WEB = 3; // German web dictionary for English, French, Spanish, Italian,
                                                     // Chinese, Russian
    private static final int DICTIONARY_LEO_APP = 4; // German web dictionary for English, French, Spanish, Italian,
                                                     // Chinese, Russian
    private static final int DICTIONARY_COLORDICT = 5;
    private static final int DICTIONARY_FORA = 6;
    private static final int DICTIONARY_NCIKU_WEB = 7; // chinese web dictionary

    private static Context mContext;
    private static boolean mIsDictionaryAvailable;
    private static String mDictionaryAction;
    private static int mDictionary;
    private static String mLookupText;


    public static boolean initialize(Context context) {
        mContext = context;
        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext());
        mDictionary = Integer.parseInt(preferences.getString("dictionary", Integer.toString(DICTIONARY_NONE)));
        switch (mDictionary) {
            case DICTIONARY_AEDICT:
                mDictionaryAction = "sk.baka.aedict.action.ACTION_SEARCH_EDICT";
                mIsDictionaryAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction);
                break;
            case DICTIONARY_LEO_WEB:
            case DICTIONARY_NCIKU_WEB:
            case DICTIONARY_EIJIRO_WEB:
                mDictionaryAction = "android.intent.action.VIEW";
                mIsDictionaryAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction);
                break;
            case DICTIONARY_LEO_APP:
                mDictionaryAction = "android.intent.action.SEND";
                mIsDictionaryAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction, new ComponentName(
                        "org.leo.android.dict", "org.leo.android.dict.LeoDict"));
                break;
            case DICTIONARY_COLORDICT:
                mDictionaryAction = "colordict.intent.action.SEARCH";
                mIsDictionaryAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction);
                break;
            case DICTIONARY_FORA:
                mDictionaryAction = "com.ngc.fora.action.LOOKUP";
                mIsDictionaryAvailable = Utils.isIntentAvailable(mContext, mDictionaryAction);
                break;
            case DICTIONARY_NONE:
            default:
                mIsDictionaryAvailable = false;
                break;
        }
        Timber.v("Is intent available = %b", mIsDictionaryAvailable);
        return mIsDictionaryAvailable;
    }


    public static boolean lookUp(String text) {
        if (!mIsDictionaryAvailable) {
            return false;
        }
        // clear text from leading and closing dots, commas, brackets etc.
        text = text.trim().replaceAll("[,;:\\s(\\[)\\].]*$", "").replaceAll("^[,;:\\s(\\[)\\].]*", "");
        switch (mDictionary) {
            case DICTIONARY_NONE:
                return false;
            case DICTIONARY_AEDICT:
                Intent aedictSearchIntent = new Intent(mDictionaryAction);
                aedictSearchIntent.putExtra("kanjis", text);
                mContext.startActivity(aedictSearchIntent);
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.AEDICT);
                return true;
            case DICTIONARY_LEO_WEB:
            case DICTIONARY_LEO_APP:
                mLookupText = text;
                // localisation is needless here since leo.org translates only into or out of German
                final CharSequence[] itemValues = { "en", "fr", "es", "it", "ch", "ru" };
                String language = getLanguage(MetaDB.LANGUAGES_QA_UNDEFINED);
                if (language.length() > 0) {
                    for (CharSequence itemValue : itemValues) {
                        if (language.contentEquals(itemValue)) {
                            lookupLeo(language, mLookupText);
                            mLookupText = "";
                            return true;
                        }
                    }
                }
                final String[] items = { "Englisch", "Französisch", "Spanisch", "Italienisch", "Chinesisch", "Russisch" };
                new MaterialDialog.Builder(mContext)
                        .title("\"" + mLookupText + "\" nachschlagen")
                        .items(items)
                        .itemsCallback((materialDialog, view, item, charSequence) -> {
                            String language1 = itemValues[item].toString();
                            storeLanguage(language1, MetaDB.LANGUAGES_QA_UNDEFINED);
                            lookupLeo(language1, mLookupText);
                            mLookupText = "";
                        })
                        .build().show();
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.LEO);
                return true;
            case DICTIONARY_COLORDICT:
                Intent colordictSearchIntent = new Intent(mDictionaryAction);
                colordictSearchIntent.putExtra("EXTRA_QUERY", text);
                mContext.startActivity(colordictSearchIntent);
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.COLORDICT);
                return true;
            case DICTIONARY_FORA:
                Intent foraSearchIntent = new Intent(mDictionaryAction);
                foraSearchIntent.putExtra("HEADWORD", text.trim());
                mContext.startActivity(foraSearchIntent);
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.FORA);
                return true;
            case DICTIONARY_NCIKU_WEB:
                Intent ncikuWebIntent = new Intent(mDictionaryAction, Uri.parse("http://m.nciku.com/en/entry/?query="
                        + text));
                mContext.startActivity(ncikuWebIntent);
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.NCIKU);
                return true;
            case DICTIONARY_EIJIRO_WEB:
                Intent eijiroWebIntent = new Intent(mDictionaryAction, Uri.parse("http://eow.alc.co.jp/" + text));
                mContext.startActivity(eijiroWebIntent);
                UsageAnalytics.sendAnalyticsEvent(Lookup.class.getSimpleName(), UsageAnalytics.Actions.EIJIRO);
                return true;
        }
        return false;
    }


    private static void lookupLeo(String language, CharSequence text) {
        switch (mDictionary) {
            case DICTIONARY_LEO_WEB:
                Intent leoSearchIntent = new Intent(mDictionaryAction, Uri.parse("http://pda.leo.org/?lp=" + language
                        + "de&search=" + text));
                mContext.startActivity(leoSearchIntent);
                break;
            case DICTIONARY_LEO_APP:
                Intent leoAppSearchIntent = new Intent(mDictionaryAction);
                leoAppSearchIntent.putExtra("org.leo.android.dict.DICTIONARY", language + "de");
                leoAppSearchIntent.putExtra(Intent.EXTRA_TEXT, text);
                leoAppSearchIntent.setComponent(new ComponentName("org.leo.android.dict",
                        "org.leo.android.dict.LeoDict"));
                mContext.startActivity(leoAppSearchIntent);
                break;
            default:
        }
    }


    public static String getSearchStringTitle() {
        return String.format(mContext.getString(R.string.menu_search),
                mContext.getResources().getStringArray(R.array.dictionary_labels)[mDictionary]);
    }


    public static boolean isAvailable() {
        return mIsDictionaryAvailable;
    }


    private static String getLanguage(int questionAnswer) {
        // if (mCurrentCard == null) {
        return "";
        // } else {
        // return MetaDB.getLanguage(mContext, mDeckFilename, Models.getModel(DeckManager.getMainDeck(),
        // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer);
        // }
    }


    private static void storeLanguage(String language, int questionAnswer) {
        // if (mCurrentCard != null) {
        // MetaDB.storeLanguage(mContext, mDeckFilename, Models.getModel(DeckManager.getMainDeck(),
        // mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer, language);
        // }
    }
}
