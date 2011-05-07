/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.libanki;

import android.database.Cursor;
import android.util.Log;

import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.mindprod.common11.StringTools;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Anki model. A model describes the type of information you want to input, and the type of cards which should be
 * generated. See http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Models There can be several models in a Deck. A Model
 * is related to a Deck via attribute deckId. A CardModel is related to a Model via CardModel's modelId. A FieldModel is
 * related to a Model via FieldModel's modelId A Card has a link to CardModel via Card's cardModelId A Card has a link
 * to a Fact via Card's factId A Field has a link to a Fact via Field's factId A Field has a link to a FieldModel via
 * Field's fieldModelId => In order to get the CardModel and all FieldModels for a given Card: % the CardModel can
 * directly be retrieved from the DB using the Card's cardModelId % then from the retrieved CardModel we can get the
 * modelId % using the modelId we can get all FieldModels from the DB % (alternatively in the CardModel the qformat and
 * aformat fields could be parsed for relevant field names and then this used to only get the necessary fields. But this
 * adds a lot overhead vs. using a bit more memory)
 * 
 * 
 * ...Schedule: fail, pass1, pass2, etc in minutes
 * ...Intervals: graduation, first remove, later remove
 * maybe define a random cutoff at say +/-30% which controls exit interval
 * variation - 30% of 1 day is 0.7 or 1.3 so always 1 day; 30% of 4 days is
 * 2.8-5.2, so any time from 3-5 days is acceptable
 * collapse time should be bigger than default failSchedule
 * need to think about failed interval handling - if the final drill is
 * optional, what intervals should the default be? 3 days or more if cards are
 * over that interval range? and what about failed mature bonus?
 */
public class Model {

	private String defaultConf = 
		"{'sortf': 0, " +
		"'gid': 1, " +
		"'tags': [], " +
		"'clozectx': False, " +
		"'latexPre': \"\"\"\\ " +
		"\\\\documentclass[12pt]{article} " +
		"\\\\special{papersize=3in,5in} " +
		"\\\\usepackage[utf8]{inputenc} " +
		"\\\\usepackage{amssymb,amsmath} " +
		"\\\\pagestyle{empty} " +
		"\\\\setlength{\\\\parindent}{0in} " +
		"\\\\begin{document} " +
		"\"\"\", " +
		"'latexPost': \"\\\\end{document}\",}";

	private String defaultField = 
		"{'name': \"\", " +
		"'ord': None, " +
		"'rtl': False, " +
		"'req': False, " +
		"'uniq': False, " +
		"'font': \"Arial\", " +
		"'qsize': 20, " +
		"'esize': 20, " +
		"'qcol': \"#000\", " +
		"'pre': True, " +
		"'sticky': False, }";

	private String defaultTemplate = 
		"{'name': \"\", " +
		"'ord': None, " +
		"'actv': True, " +
		"'qfmt': \"\", " +
		"'afmt': \"\", " +
		"'hideQ': False, " +
		"'align': 0, " +
		"'bg': \"#fff\", " +
		"'emptyAns': True, " +
		"'typeAns': None, " +
		"'gid': None, }";
	
	private static final String[] align_text = { "center", "left", "right" };

//    /** Regex pattern used in removing tags from text before diff */
//    private static final Pattern sFactPattern = Pattern.compile("%\\([tT]ags\\)s");
//    private static final Pattern sModelPattern = Pattern.compile("%\\(modelTags\\)s");
//    private static final Pattern sTemplPattern = Pattern.compile("%\\(cardModel\\)s");


    // BEGIN SQL table entries
    private int mId;
    private String mName = "";
    private int mCrt = Utils.intNow();
    private int mMod = Utils.intNow();
    private JSONObject mConf;
    private String mCss = "";
    private JSONArray mFields;
    private JSONArray mTemplates;
    // BEGIN SQL table entries

    private Deck mDeck;
    private AnkiDb mDb;

    /** Map for compiled Mustache Templates */
    private HashMap<Integer, Template[]> mCmpldTemplateMap;

    /** Map for convenience and speed which contains FieldNames from current model */
    private TreeMap<String, Integer> mFieldMap = new TreeMap<String, Integer>();

    /** Map for convenience and speed which contains Templates from current model */
    private TreeMap<Integer, JSONObject> mTemplateMap = new TreeMap<Integer, JSONObject>();

    /** Map for convenience and speed which contains the CSS code related to a Template */
    private HashMap<Integer, String> mCssTemplateMap = new HashMap<Integer, String>();

    /**
     * The percentage chosen in preferences for font sizing at the time when the css for the CardModels related to this
     * Model was calculated in prepareCSSForCardModels.
     */
    private transient int mDisplayPercentage = 0;
    private boolean mNightMode = false;


    public Model(Deck deck) {
        this(deck, 0);
    }
    public Model(Deck deck, int id) {
        mDeck = deck;
        mDb = mDeck.getDB();
        mId = id;
        if (id != 0) {
        	fromDB(id);
        } else {
            try {
				mFields = new JSONArray(defaultField);
	            mTemplates = new JSONArray(defaultTemplate);
	            mConf = new JSONObject(defaultConf);
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
        }
    }


	public boolean fromDB(int id) {
        Cursor cursor = null;
        try {
            cursor = mDb.getDatabase().rawQuery(
                    "SELECT crt, mod, name, flds, tmpls, conf, css FROM cards WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w(AnkiDroidApp.TAG, "Card.java (fromDB(id)): No result from query.");
                return false;
            }
            mCrt = cursor.getInt(0);
            mMod = cursor.getInt(1);
            mName = cursor.getString(2);
			mFields = new JSONArray(cursor.getString(3));
            mTemplates = new JSONArray(cursor.getString(4));
            mConf = new JSONObject(cursor.getString(5));
            mCss = cursor.getString(6);
   		} catch (JSONException e) {
   			throw new RuntimeException(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
		}
        mTemplateMap = new TreeMap<Integer, JSONObject>();
    	for (int i = 0; i < mFields.length(); i++) {
    		try {
    			mTemplateMap.put(mTemplates.getJSONObject(i).getInt("ord"), mTemplates.getJSONObject(i));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
    	}
        return true;
    }


	public JSONObject getConf() {
		return mConf;
	}


	public ArrayList<Integer> fids() {
		return mDb.queryColumn(Integer.class, "SELECT id FROM facts WHERE mid = " + mId, 0);
	}


	public int useCount() {
		return (int) mDb.queryScalar("SELECT count() FROM facts WHERE mid = " + mId);
	}
    
    
    /**
     * Css generation
     * ***********************************************************************************************
     */

    /**
     * Returns a cached CSS for the font color and font size of a given Template taking into account the included
     * fields
     *
     * @param ord - number of template
     * @param percentage the preference factor to use for calculating the display font size from the cardmodel and
     *            fontmodel font size
     * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
     */
	public String getCSSForFontColorSize(int ord, int percentage, boolean night) {
        // check whether the percentage is this the same as last time
        if (mDisplayPercentage != percentage || mNightMode != night || !mCssTemplateMap.containsKey(ord)) {
            mDisplayPercentage = percentage;
            mNightMode = night;
            mCssTemplateMap.put(ord, createCSSForFontColorSize(ord, percentage, night));
        }
        return mCssTemplateMap.get(ord);
    }


    /**
     * @param ord - number of template
     * @param percentage the factor to apply to the font size in card model to the display size (in %)
     * @param nightmode boolean
     * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
     */
    private String createCSSForFontColorSize(int ord, int percentage, boolean night) {
        StringBuffer sb = new StringBuffer();
        sb.append("<!-- ").append(percentage).append(" % display font size-->");
        sb.append("<style type=\"text/css\">\n");

        JSONObject template = getTemplate(ord);

		try {
	        // fields
	        for (Map.Entry<String, Integer> entry : mFieldMap.entrySet()) {
	        	JSONObject fconf;
					fconf = mFields.getJSONObject(entry.getValue());
	        	sb.append(_fieldCSS(percentage, 
	        			String.format(".fm%s-%s", Utils.hexifyID(mId), Utils.hexifyID(entry.getValue())), 
	        			fconf.getString("font"), fconf.getInt("qsize"), 
	        			invertColor(fconf.getString("qcol"), night),
	        			fconf.getString("rtl").equals("true"), fconf.getString("pre").equals("true")));
	        }

	        // templates
	        for (int i = 0; i < mTemplates.length(); i++) {
	        	JSONObject tmpl = mTemplates.getJSONObject(i);
	        	sb.append(String.format(".cm%s-%s {text-align:%s;background:%s;", Utils.hexifyID(mId), Utils.hexifyID(tmpl.getInt("ord")),
	        			align_text[template.getInt("align")], invertColor(tmpl.getString("bg"), night)));
	            sb.append("padding-left:5px;");
	            sb.append("padding-right:5px;}\n");
	        }
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

        // finish
        sb.append("</style>");
        return sb.toString();
    }


    private static String invertColor(String color, boolean invert) {
    	if (invert) {
    	    if (color.length() != 0) {
    	        color = StringTools.toUpperCase(color);
    	    }
            final char[] items = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
            final char[] tmpItems = {'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v'};
            for (int i = 0; i < 16; i++) {
                color = color.replace(items[i], tmpItems[15-i]);
            }
            for (int i = 0; i < 16; i++) {
                color = color.replace(tmpItems[i], items[i]);
            }
		}
		return color;		
    }


    private static String _fieldCSS(int percentage, String prefix, String fontFamily, int fontSize, String fontColour, boolean rtl, boolean pre) {
        StringBuffer sb = new StringBuffer();
        sb.append(prefix).append(" {");
        if (null != fontFamily && 0 < fontFamily.trim().length()) {
            sb.append("font-family:\"").append(fontFamily).append("\";\n");
        }
        if (0 < fontSize) {
            sb.append("font-size:");
            sb.append((percentage * fontSize) / 100);
            sb.append("px;\n");
        }
        if (null != fontColour && 0 < fontColour.trim().length()) {
            sb.append("color:").append(fontColour).append(";\n");
        }
        if (rtl) {
            sb.append("direction:rtl;unicode-bidi:embed;\n");
        }
        if (rtl) {
            sb.append("white-space:pre-wrap;\n");
        }
        sb.append("}\n");
        return sb.toString();
    }


//    /**
//     * Prepares the Background Colors for all CardModels in this Model
//     */
//    private void prepareColorForCardModels(boolean invertedColors) {
//        CardModel myCardModel = null;
//        String color = null;
//        for (Map.Entry<Long, CardModel> entry : mCardModelsMap.entrySet()) {
//            myCardModel = entry.getValue();
//            color = invertColor(myCardModel.getLastFontColour(), invertedColors);
//            mColorCardModelMap.put(myCardModel.getId(), color);
//        }
//    }
//
//    protected final String getBackgroundColor(long myCardModelId, boolean invertedColors) {
//    	if (mColorCardModelMap.size() == 0) {
//    		prepareColorForCardModels(invertedColors);
//    	}
//		String color = mColorCardModelMap.get(myCardModelId);
//		if (color != null) {
//			return color;
//		} else {
//			return "#FFFFFF";
//        }
//    }

    /**
     * Fields
     * ***********************************************************************************************
     */

    public TreeMap<String, Integer> fieldMap() {
    	TreeMap<String, Integer> map = new TreeMap<String, Integer>();
    	for (int i = 0; i < mFields.length(); i++) {
    		try {
				map.put(mFields.getJSONObject(i).getString("name"), mFields.getJSONObject(i).getInt("ord"));
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
    	}
    	return map;
    }

    public int sortIdx() {
    	try {
			return mConf.getInt("sortf");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }

    /**
     * Templates
     * ***********************************************************************************************
     */

    public TreeMap<Integer, JSONObject> getTemplates() {
    	return mTemplateMap;
    }


    public JSONObject getTemplate(int ord) {
		return mTemplateMap.get(ord);
    }


    public Template[] getCmpldTemplate(int ord) {
    	if (!mCmpldTemplateMap.containsKey(ord)) {
    		mCmpldTemplateMap.put(ord, compileTemplate(ord));
    	}
    	return mCmpldTemplateMap.get(ord);
    }


    private Template[] compileTemplate(int ord) {
        Template[] template = new Template[2];
    	try {
			String string = mTemplates.getJSONObject(ord).getString("qfmt");
            Log.i(AnkiDroidApp.TAG, "Compiling question template \"" + string + "\"");
            template[0] = Mustache.compiler().compile(string);
			string = mTemplates.getJSONObject(ord).getString("afmt");
            Log.i(AnkiDroidApp.TAG, "Compiling answer template \"" + string + "\"");
            template[1] = Mustache.compiler().compile(string);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return template;
    }

//    /**
//     * This function recompiles the templates for question and answer. It should be called everytime we change mQformat
//     * or mAformat, so if in the future we create set(Q|A)Format setters, we should include a call to this.
//     */
//    private void refreshTemplates(int ord) {
//        // Question template
//        StringBuffer sb = new StringBuffer();
//        Matcher m = sOldStylePattern.matcher(mQformat);
//        while (m.find()) {
//            // Convert old style
//            m.appendReplacement(sb, "{{" + m.group(1) + "}}");
//        }
//        m.appendTail(sb);
//        Log.i(AnkiDroidApp.TAG, "Compiling question template \"" + sb.toString() + "\"");
//        mQTemplate = Mustache.compiler().compile(sb.toString());
//
//        // Answer template
//        sb = new StringBuffer();
//        m = sOldStylePattern.matcher(mAformat);
//        while (m.find()) {
//            // Convert old style
//            m.appendReplacement(sb, "{{" + m.group(1) + "}}");
//        }
//        m.appendTail(sb);
//        Log.i(AnkiDroidApp.TAG, "Compiling answer template \"" + sb.toString() + "\"");
//        mATemplate = Mustache.compiler().compile(sb.toString());
//    }
    

    /**
     * ***********************************************************************************************
     */

//
//    public static HashMap<Long, Model> getModels(Deck deck) {
//        Model mModel;
//        HashMap<Long, Model> mModels = new HashMap<Long, Model>();
//
//        Cursor mCursor = null;
//        try {
//            mCursor = deck.getDB().getDatabase().rawQuery("SELECT id FROM models", null);
//            if (!mCursor.moveToFirst()) {
//                return mModels;
//            }
//            do {
//                Long id = mCursor.getLong(0);
//                mModel = getModel(deck, id, true);
//                mModels.put(id, mModel);
//
//            } while (mCursor.moveToNext());
//
//        } finally {
//            if (mCursor != null) {
//                mCursor.close();
//            }
//        }
//        return mModels;
//    }
//
//    
//    
//    private static String replaceField(String replaceFrom, Fact fact, int replaceAt, boolean isQuestion) {
//        int endIndex = replaceFrom.indexOf(")", replaceAt);
//        String fieldName = replaceFrom.substring(replaceAt + 2, endIndex);
//        char fieldType = replaceFrom.charAt(endIndex + 1);
//        if (isQuestion) {
//            String replace = "%(" + fieldName + ")" + fieldType;
//            String with = "<span class=\"fm" + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">"
//                    + fact.getFieldValue(fieldName) + "</span>";
//            replaceFrom = replaceFrom.replace(replace, with);
//        } else {
//            replaceFrom.replace("%(" + fieldName + ")" + fieldType, "<span class=\"fma"
//                    + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">" + fact.getFieldValue(fieldName)
//                    + "</span");
//        }
//        return replaceFrom;
//    }
//
//
//    private static String replaceHtmlField(String replaceFrom, Fact fact, int replaceAt) {
//        int endIndex = replaceFrom.indexOf(")", replaceAt);
//        String fieldName = replaceFrom.substring(replaceAt + 7, endIndex);
//        char fieldType = replaceFrom.charAt(endIndex + 1);
//        String replace = "%(text:" + fieldName + ")" + fieldType;
//        String with = fact.getFieldValue(fieldName);
//        replaceFrom = replaceFrom.replace(replace, with);
//        return replaceFrom;
//    }



    /**
     * @return the ID
     */
    public int getId() {
        return mId;
    }

    
    /**
     * @return the name
     */
    public String getName() {
        return mName;
    }

}
