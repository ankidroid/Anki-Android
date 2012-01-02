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

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.ichi2.anki.AnkiDb;
import com.ichi2.anki.AnkiDroidApp;
import com.mindprod.common11.StringTools;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Models {

	private static final String defaultModel = 
		"{'sortf': 0, " +
		"'did': 1, " +
		"'latexPre': \"\"\"\\ " +
		"\\\\documentclass[12pt]{article} " +
		"\\\\special{papersize=3in,5in} " +
		"\\\\usepackage[utf8]{inputenc} " +
		"\\\\usepackage{amssymb,amsmath} " +
		"\\\\pagestyle{empty} " +
		"\\\\setlength{\\\\parindent}{0in} " +
		"\\\\begin{document} " +
		"\"\"\", " +
		"'latexPost': \"\\\\end{document}\", " +
		"'mod': 9, " +
		"'usn': 9, " +
		"'vers': [], }";

	private static final String defaultField = 
		"{'name': \"\", " +
		"'ord': None, " +
		"'sticky': False, " +
		// the following alter editing, and are used as defaults for the template wizard
		"'rtl': False, " +
		"'font': \"Arial\", " +
		"'size': 20, " +
		// reserved for future use
		"'media': [], }";

	private static final String defaultTemplate = 
		"{'name': \"\", " +
		"'ord': None, " +
		"'qfmt': \"\", " +
		"'afmt': \"\", " +
		"'did': None, " +
		"'css': \"\"\"\\ " +
		".card { " +
		" font-family: arial;" +
		" font-size: 20px;" +
		" text-align: center;" +
		" color: black;" +
		" background-color: white;" +
		" }" +
		"\"\"\"}";
	
//    /** Regex pattern used in removing tags from text before diff */
//    private static final Pattern sFactPattern = Pattern.compile("%\\([tT]ags\\)s");
//    private static final Pattern sModelPattern = Pattern.compile("%\\(modelTags\\)s");
//    private static final Pattern sTemplPattern = Pattern.compile("%\\(cardModel\\)s");

	private Collection mCol;
	private boolean mChanged;
	private HashMap<String, JSONObject> mModels;

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

//    private Decks mDeck;
//    private AnkiDb mDb;
//
    /** Map for compiled Mustache Templates */
    private HashMap<Integer, HashMap<Integer, Template[]>> mCmpldTemplateMap = new HashMap<Integer, HashMap<Integer, Template[]>>();
//
//    /** Map for convenience and speed which contains FieldNames from current model */
//    private TreeMap<String, Integer> mFieldMap = new TreeMap<String, Integer>();
//
//    /** Map for convenience and speed which contains Templates from current model */
//    private TreeMap<Integer, JSONObject> mTemplateMap = new TreeMap<Integer, JSONObject>();
//
//    /** Map for convenience and speed which contains the CSS code related to a Template */
//    private HashMap<Integer, String> mCssTemplateMap = new HashMap<Integer, String>();
//
//    /**
//     * The percentage chosen in preferences for font sizing at the time when the css for the CardModels related to this
//     * Model was calculated in prepareCSSForCardModels.
//     */
//    private transient int mDisplayPercentage = 0;
//    private boolean mNightMode = false;

    
    
    
    
    /**
     * Saving/loading registry
     * ***********************************************************************************************
     */

    public Models(Collection col) {
    	mCol = col;
    }


    /**
     * Load registry from JSON.
     */
    public void load(String json) {
    	mChanged = false;
    	mModels = new HashMap<String, JSONObject>();
        try {
        	JSONObject decksarray = new JSONObject(json);
        	JSONArray ids = decksarray.names();
        	for (int i = 0; i < ids.length(); i++) {
        		String id = ids.getString(i);
        		JSONObject o = decksarray.getJSONObject(id);
        		mModels.put(id, o);
        	}
 		} catch (JSONException e) {
 			throw new RuntimeException(e);
 		}
    }


    /**
     * Mark M modified if provided, and schedule registry flush.
     */
    public void save() {
    	save(null, false);
    }
    public void save(String m, boolean templates) {
    	// TODO
    }

    
    /**
     * Flush the registry if any models were changed.
     */
    public void flush() {
    	if (mChanged) {
    		mCol.getDb().getDatabase().execSQL("UPDATE col SET models = " + mModels.toString());
    		mChanged = false;
    	}
    }

    /**
     * Retrieving and creating models
     * ***********************************************************************************************
     */
    
    /**
     * Get current model.
     */
    public JSONObject current() {
    	JSONObject m;
		try {
			m = get(mCol.getConf().getInt("curModel"));
	    	if (m != null) {
	    		return m;
	    	} else {
	    		// TODO:
	    		return null;
	    	}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }


    public void setCurrent(JSONObject m) {
    	try {
			mCol.getConf().put("curModel", m.get("id"));
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    	mCol.setMod();
    }


    /** get model with ID, or none. */
    public JSONObject get(int id) {
    	String sid = Integer.toString(id);
    	if (mModels.containsKey(sid)) {
    		return mModels.get(sid);
    	} else {
    		return null;
    	}
    }


    /** get all models */
    public ArrayList<JSONObject> all() {
    	return (ArrayList<JSONObject>) mModels.values();
    }


    /** get model with NAME. */
    public JSONObject byName(String name) {
    	for (JSONObject m : mModels.values()) {
    		try {
				if (m.getString("name").equalsIgnoreCase(name)) {
					return m;
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
    	}
    	return null;
    }


    // new
    // rem
    // add
    // update
    // _setid

    public boolean have(int id) {
    	return mModels.containsKey(Integer.toString(id));
    }

    /**
     * Tools
     * ***********************************************************************************************
     */

    // nids
    // usecounts
    
    /**
     * Copying
     * ***********************************************************************************************
     */
    
    // copy

    /**
     * Fields
     * ***********************************************************************************************
     */

    // new fields


    /** Mapping of field name --> (ord, field). */
    public TreeMap<String, Object[]> fieldMap(JSONObject m) {
    	JSONArray ja;
		try {
			ja = m.getJSONArray("flds");
			TreeMap<String, Object[]> map = new TreeMap<String, Object[]>();
	    	for (int i = 0; i < ja.length(); i++) {
	    		JSONObject f = ja.getJSONObject(i);
	    		map.put(f.getString("name"), new Object[]{f.getInt("ord"), f});
	    	}
	    	return map;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }


    public ArrayList<String> fieldNames(JSONObject m) {
    	JSONArray ja;
		try {
			ja = m.getJSONArray("flds");
			ArrayList<String> names = new ArrayList<String>();
	    	for (int i = 0; i < ja.length(); i++) {
	    		names.add(ja.getJSONObject(i).getString("name"));
	    	}
	    	return names;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    	
    }


    public int sortIdx(JSONObject m) {
    	try {
			return m.getInt("sortf");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
    }


//    public int setSortIdx(JSONObject m, int idx) {
//    	try {
//    		mCol.modSchema();
//    		m.put("sortf", idx);
//    		mCol.updateFieldCache(nids(m));
//    		save(m);
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//    }


    //addfield
    //remfield
    //movefield
    //renamefield
    //updatefieldords
    //transformfields



    /**
     * Templates
     * ***********************************************************************************************
     */

    //newtemplate
    //addtemplate
    //remtemplate
    //_updatetemplords
    //movetemplate
    //_syncTemplates

    
//    public TreeMap<Integer, JSONObject> getTemplates() {
//    	return mTemplateMap;
//    }
//
//
//    public JSONObject getTemplate(int ord) {
//		return mTemplateMap.get(ord);
//    }


    // not in libanki
    public Template[] getCmpldTemplate(int modelId, int ord) {
    	if (!mCmpldTemplateMap.containsKey(modelId)) {
    		mCmpldTemplateMap.put(modelId, new HashMap<Integer, Template[]>());
    	}
		if (!mCmpldTemplateMap.get(modelId).containsKey(ord)) {
    		mCmpldTemplateMap.get(modelId).put(ord, compileTemplate(modelId, ord));
		}
    	return mCmpldTemplateMap.get(modelId).get(ord);
    }


    // not in libanki
    private Template[] compileTemplate(int modelId, int ord) {
    	JSONObject model = mModels.get(modelId);
        JSONObject template;
        Template[] t = new Template[2];
		try {
			template = model.getJSONArray("tmpls").getJSONObject(ord);
			String format = template.getString("qfmt").replace("cloze", "cq:");
            Log.i(AnkiDroidApp.TAG, "Compiling question template \"" + format + "\"");
            t[0] = Mustache.compiler().compile(format);
            format = template.getString("afmt").replace("cloze:", "ca:");
            Log.i(AnkiDroidApp.TAG, "Compiling answer template \"" + format + "\"");
            t[1] = Mustache.compiler().compile(format);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		return t;
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
     * Model changing
     * ***********************************************************************************************
     */
    
    /**
     * Schema hash
     * ***********************************************************************************************
     */
    
    /**
     * Required field/text cache
     * ***********************************************************************************************
     */

    //_updaterequired
    // _reqfortemplate


    /** Given a joined field string, return available template ordinals */
    public int[] availOrds(JSONObject m, String flds) {
    	for (String n : Utils.splitFields(flds)) {
    		// TODO
    	}
    	return null;
    }
    
    
    /**
     * Sync handling
     * ***********************************************************************************************
     */

    // beforeupload
    
    
    /**
     * Other stuff
     * NOT IN LIBANKI
     * ***********************************************************************************************
     */

//	public JSONObject getConf() {
//		return mConf;
//	}
//
//
//	public ArrayList<Integer> fids() {
//		return mDb.queryColumn(Integer.class, "SELECT id FROM facts WHERE mid = " + mId, 0);
//	}
//
//
//	public int useCount() {
//		return (int) mDb.queryScalar("SELECT count() FROM facts WHERE mid = " + mId);
//	}
//    
    
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
//	public String getCSSForFontColorSize(int ord, int percentage, boolean night) {
//        // check whether the percentage is this the same as last time
//        if (mDisplayPercentage != percentage || mNightMode != night || !mCssTemplateMap.containsKey(ord)) {
//            mDisplayPercentage = percentage;
//            mNightMode = night;
//            mCssTemplateMap.put(ord, createCSSForFontColorSize(ord, percentage, night));
//        }
//        return mCssTemplateMap.get(ord);
//    }
//
//
//    /**
//     * @param ord - number of template
//     * @param percentage the factor to apply to the font size in card model to the display size (in %)
//     * @param nightmode boolean
//     * @return the html contents surrounded by a css style which contains class styles for answer/question and fields
//     */
//    private String createCSSForFontColorSize(int ord, int percentage, boolean night) {
//        StringBuffer sb = new StringBuffer();
//        sb.append("<!-- ").append(percentage).append(" % display font size-->");
//        sb.append("<style type=\"text/css\">\n");
//
//        JSONObject template = getTemplate(ord);
//
//		try {
//	        // fields
//			for (int i = 0; i < mFields.length(); i++) {
//				JSONObject fconf = mFields.getJSONObject(i);
//	        	sb.append(_fieldCSS(percentage, 
//	        			String.format(".fm%s-%s", Utils.hexifyID(mId), Utils.hexifyID(fconf.getInt("ord"))), 
//	        			fconf.getString("font"), fconf.getInt("qsize"), 
//	        			invertColor(fconf.getString("qcol"), night),
//	        			fconf.getString("rtl").equals("True"), fconf.getString("pre").equals("True")));
//	        }
//
//	        // templates
//	        for (int i = 0; i < mTemplates.length(); i++) {
//	        	JSONObject tmpl = mTemplates.getJSONObject(i);
//	        	sb.append(String.format(".cm%s-%s {text-align:%s;background:%s;", Utils.hexifyID(mId), Utils.hexifyID(tmpl.getInt("ord")),
//	        			align_text[template.getInt("align")], invertColor(tmpl.getString("bg"), night)));
//	            sb.append("padding-left:5px;");
//	            sb.append("padding-right:5px;}\n");
//	        }
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//
//        // finish
//        sb.append("</style>");
//        return sb.toString();
//    }
//
//
//    private static String invertColor(String color, boolean invert) {
//    	if (invert) {
//    	    if (color.length() != 0) {
//    	        color = StringTools.toUpperCase(color);
//    	    }
//            final char[] items = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
//            final char[] tmpItems = {'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v'};
//            for (int i = 0; i < 16; i++) {
//                color = color.replace(items[i], tmpItems[15-i]);
//            }
//            for (int i = 0; i < 16; i++) {
//                color = color.replace(tmpItems[i], items[i]);
//            }
//		}
//		return color;		
//    }


    // TODO: implement call
    /**
     * Returns a string where all colors have been inverted.
     * It applies to anything that is in a tag and looks like #FFFFFF
     * 
     * Example: Here only #000000 will be replaced (#777777 is content)
     * <span style="color: #000000;">Code #777777 is the grey color</span>
     * 
     * This is done with a state machine with 2 states:
     *  - 0: within content
     *  - 1: within a tag
     */
//    public static String invertColors(String text, boolean invert) {
//        if (invert) {
//            int state = 0;
//            StringBuffer inverted = new StringBuffer(text.length());
//            for(int i=0; i<text.length(); i++) {
//                char character = text.charAt(i);
//                if (state == 1 && character == '#') {
//                    inverted.append(invertColor(text.substring(i+1, i+7), true));
//                }
//                else {
//                    if (character == '<') {
//                        state = 1;
//                    }
//                    if (character == '>') {
//                        state = 0;
//                    }
//                    inverted.append(character);
//                }
//            }
//            return inverted.toString();
//        }
//        else {
//            return text;
//        }
//    }
//
//    private static String _fieldCSS(int percentage, String prefix, String fontFamily, int fontSize, String fontColour, boolean rtl, boolean pre) {
//        StringBuffer sb = new StringBuffer();
//        sb.append(prefix).append(" {");
//        if (null != fontFamily && 0 < fontFamily.trim().length()) {
//            sb.append("font-family:\"").append(fontFamily).append("\";\n");
//        }
//        if (0 < fontSize) {
//            sb.append("font-size:");
//            sb.append((percentage * fontSize) / 100);
//            sb.append("px;\n");
//        }
//        if (null != fontColour && 0 < fontColour.trim().length()) {
//            sb.append("color:").append(fontColour).append(";\n");
//        }
//        if (rtl) {
//            sb.append("direction:rtl;unicode-bidi:embed;\n");
//        }
//        if (rtl) {
//            sb.append("white-space:pre-wrap;\n");
//        }
//        sb.append("}\n");
//        return sb.toString();
//    }


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
