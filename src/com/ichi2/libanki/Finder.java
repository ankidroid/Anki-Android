/****************************************************************************************
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.util.Log;

public class Finder {

	private static final int SEARCH_TAG = 0;
	private static final int SEARCH_TYPE = 1;
	private static final int SEARCH_PHRASE = 2;
	private static final int SEARCH_NID = 3;
	private static final int SEARCH_TEMPLATE = 4;
	private static final int SEARCH_FIELD = 5;
	private static final int SEARCH_MODEL = 6;
	private static final int SEARCH_DECK = 7;

	public static Pattern allPattern = Pattern.compile("(-)?\\'(([^\\'\\\\]|\\\\.)*)\\'|(-)?\"(([^\"\\\\]|\\\\.)*)\"|(-)?([^ ]+)|([ ]+)");

	private Collection mCol;
	private String mOrder;
	private String mQuery;
	private JSONObject mLims;
	private boolean mFull = false;

	public Finder(Collection col) {
		mCol = col;
	}

	private ArrayList<String> fieldNames(Collection col) {
		return fieldNames(col, true);
	}
	private ArrayList<String> fieldNames(Collection col, boolean downcase) {
		TreeSet<String> fields = new TreeSet<String>();
		ArrayList<String> names = new ArrayList<String>();
		for (JSONObject m : col.getModels().all()) {
			try {
				JSONArray flds = m.getJSONArray("flds");
				for (int i = 0; i < flds.length(); i++) {
					String name = flds.getJSONObject(i).getString("name");
					if (!fields.contains(name.toLowerCase())) {
						names.add(name);
						fields.add(name.toLowerCase());
					}
				}
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
		}
		if (downcase) {
			return new ArrayList<String>(fields);
		} else {
			return names;
		}
	}

	/** Return a list of card ids for QUERY */
	public ArrayList<Long> findCards(String query, String order) {
		mOrder = order;
		mQuery = query;
		_findLimits();
		try {
			if (!mLims.getBoolean("valid")) {
				return new ArrayList<Long>();
			}
			JSONArray ja = mLims.getJSONArray("preds");
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < ja.length(); i++) {
				sb.append(ja.getString(i));
				if (i < ja.length() - 1) {
					sb.append(" AND ");
				}
			}
			String q = sb.toString();
			if (q.length() == 0) {
				q = "1";
			}
			JSONObject args = mLims.getJSONObject("args");
			order = _order();
			query = "SELECT c.id FROM cards c, notes n WHERE " + q + " AND c.nid=n.id " + order;
			// manually place the dict value into the query string als java sqlite query does not allow dict as an argument
			JSONArray names = args.names();
			for (int i = 0; i < names.length(); i++) {
				query = query.replace(":" + names.getString(i), "\'" + args.getString(names.getString(i)) + "\'");
			}
			ArrayList<Long> res = mCol.getDb().queryColumn(Long.class, query, 0);
			if (order.length() == 0 && mCol.getConf().getBoolean("sortBackwards")) {
				Collections.reverse(res);
			}
			return res;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private String _order() {
		// user provided override?
		if (mOrder != null && mOrder.length() > 0) {
			return mOrder;
		}
		String type;
		try {
			type = mCol.getConf().getString("sortType");
			if (type.length() == 0) {
				return null;
			}
			String sort;
			if (type.startsWith("note")) {
				if (type.startsWith("noteCrt")) {
					sort = "n.id, c.ord";
				} else if (type.startsWith("noteMod")) {
					sort = "n.mod, c.ord";
				} else if (type.startsWith("noteFld")) {
					sort = "n.sfld COLLATE NOCASE, c.ord";
				} else {
					throw new RuntimeException("wrong sort type");
				}
			} else if (type.startsWith("card")) {
				if (type.startsWith("cardMod")) {
					sort = "c.mod";
				} else if (type.startsWith("cardReps")) {
					sort = "c.reps";
				} else if (type.startsWith("cardDue")) {
					sort = "c.due";
				} else if (type.startsWith("cardEase")) {
					sort = "c.factor";
				} else if (type.startsWith("cardLapses")) {
					sort = "c.lapses";
				} else if (type.startsWith("cardIvl")) {
					sort = "c.ivl";
				} else {
					throw new RuntimeException("wrong sort type");
				}
			} else {
				throw new RuntimeException("wrong sort type");
			}
			return " ORDER BY " + sort;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/** Generate a list of note/card limits for the query. */
	private void _findLimits() {
		mLims = new JSONObject();
		try {
			mLims.put("preds", new JSONArray());
			mLims.put("args", new JSONObject());
			mLims.put("valid", true);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
		ArrayList<Object[]> pq = _parseQuery();
		for (int c = 0; c < pq.size(); c++) {
			Object[] o = pq.get(c);
			String token = (String) o[0];
			boolean isNeg = (Boolean) o[1];
			int type = (Integer) o[2];
			if (type == SEARCH_TAG) {
				_findTag(token, isNeg, c);
			} else if (type == SEARCH_TYPE) {
				_findCardState(token, isNeg);
			} else if (type == SEARCH_NID) {
				_findNids(token);
			} else if (type == SEARCH_TEMPLATE) {
				_findTemplate(token, isNeg);
			} else if (type == SEARCH_FIELD) {
				_findField(token, isNeg);
			} else if (type == SEARCH_MODEL) {
				_findModel(token, isNeg);
			} else if (type == SEARCH_DECK) {
				_findDeck(token, isNeg);
			} else {
				_findText(token, isNeg, c);
			}
		}
	}

	private void _findTag(String val, boolean neg, int c) {
		try {
			String t;
			if (val.equals("none")) {
				if (neg) {
					t = "tags != \'\'";
				} else {
					t = "tags = \'\'";
				}
				mLims.getJSONArray("preds").put(t);
				return;
			}
			String extra = neg ? "NOT" : "";
			val = val.replace("*", "%");
			if (!val.startsWith("%")) {
				val = "% " + val;
			}
			if (!val.endsWith("%")) {
				val += " %";
			}
			mLims.getJSONObject("args").put("_tag_" + c, val);
			mLims.getJSONArray("preds").put("tags " + extra + " like :_tag_" + c);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private void _findCardState(String val, boolean neg) {
		String cond = null;
		int n;
		if (val.equals("review") || val.equals("new") || val.equals("learn")) {
			if (val.equals("review")) {
				n = 2;
			} else if (val.equals("new")) {
				n = 0;
			} else {
				n = 1;
			}
			cond = "type = " + n;
		} else if (val.equals("suspended")) {
			cond = "queue = -1";
		} else if (val.equals("due")) {
			cond = "(queue = 2 AND due <= " + mCol.getSched().getToday();
		}
		if (neg) {
			cond = "NOT (" + cond + ")";
		}
		try {
			if (cond.length() > 0) {
				mLims.getJSONArray("preds").put(cond);
			} else {
				mLims.put("valid", false);
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	private void _findText(String val, boolean neg, int c) {
		try {
			val = val.replace("*", "%");
			if (!mFull) {
				mLims.getJSONObject("args").put("_text_" + c, "%" + val + "%");
				String txt = "sfld LIKE :_text_" + c + " ESCAPE \'\\\\\' OR flds LIKE :_text_" + c + " ESCAPE \'\\\\\'";
				if (!neg) {
					mLims.getJSONArray("preds").put(txt);
				} else {
					mLims.getJSONArray("preds").put("NOT " + txt);
				}
			} else {
				ArrayList<Long> nids = new ArrayList<Long>();
				String extra = neg ? "NOT" : "";
				Cursor cur = null;
				try {
					cur = mCol.getDb().getDatabase().rawQuery("SELECT id, flds FROM notes", null);
					while (cur.moveToNext()) {
						if (Utils.stripHTML(cur.getString(1)).contains(val)) {
							nids.add(cur.getLong(0));
						}
					}
				} finally {
					if (cur != null && !cur.isClosed()) {
						cur.close();
					}
				}
				mLims.getJSONArray("preds").put("n.id " + extra + " IN " + Utils.ids2str(Utils.arrayList2array(nids)));
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}	
	}

	private void _findNids(String val) {
		try {
			mLims.getJSONArray("preds").put("n.id IN (" + val + ")");
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}		
	}

	private void _findModel(String val, boolean isNeg) {
		// TODO
	}

	private void _findDeck(String val, boolean isNeg) {
		try {
			long id;
			String extra;
			if (val.toLowerCase().equals("current")) {
				id = mCol.getDecks().current().getLong("id");
			} else if (val.toLowerCase().equals("none")) {
				if (isNeg) {
					extra = "";
				} else {
					extra = "NOT";
				}
				mLims.getJSONArray("preds").put("c.did " + extra + " IN " + Utils.ids2str(mCol.getDecks().allIds()));
				return;
			} else {
				id = mCol.getDecks().id(val, false);
			}
			ArrayList<Long> ids = new ArrayList<Long>();
			ids.add(id);
			for (Long a : mCol.getDecks().children(id).values()) {
				ids.add(a);
			}
			String sids = Utils.ids2str(Utils.arrayList2array(ids));
			if (!isNeg) {
				// normal search
				mLims.getJSONArray("preds").put("(c.odid IN " + sids + " OR c.did IN " + sids + ")");
			} else {
				// inverted search
				mLims.getJSONArray("preds").put("((CASE c.odid WHEN 0 then 1 else c.odid NOT IN " + sids + " END) AND c.did NOT IN " + sids + ")");
			}
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}	
	}

	private void _findTemplate(String val, boolean neg) {
		// TODO
	}

	private void _findField(String val, boolean neg) {
		// TODO
	}

	private ArrayList<Object[]> _parseQuery() {
		int type = 0;
		try {
			ArrayList<JSONObject> tokens = new ArrayList<JSONObject>();
			ArrayList<Object[]> res = new ArrayList<Object[]>();
			ArrayList<String> allowedfields = fieldNames(mCol);
			// break query into words or phraselog
			// an extra space is added so the loop never ends in the middle
			// completing a token
			Matcher matcher = allPattern.matcher(mQuery + " ");
			while (matcher.find()) {
				String value = matcher.group(2) != null ? matcher.group(2) : (matcher.group(5) != null ? matcher.group(5) : matcher.group(8));
				boolean isNeg = (matcher.group(1) != null && matcher.group(1).equals("-")) || (matcher.group(4) != null && matcher.group(4).equals("-")) || (matcher.group(5) != null && matcher.group(5).equals("-"));
				if (value != null) {
					JSONObject o = new JSONObject();
					o.put("value", value);
					o.put("is_neg", isNeg);				
					tokens.add(o);					
				}
			}
			boolean intoken = false;
			boolean isNeg = false;
			String field = ""; // name of the field for field related commands
			ArrayList<JSONObject> phraselog = new ArrayList<JSONObject>(); // log of phrases in case potential command is not a command
			for (int c = 0; c < tokens.size(); c++) {
				JSONObject token = tokens.get(c);
				boolean doprocess = true; // only look for commands when this is true
				// prevent cases such as "field" : value as being processed as a command
				if (token.getString("value").length() == 0) {
					if (intoken && type == SEARCH_FIELD && field.length() > 0) {
						// case: fieldname: any thing here check for existance of fieldname
						addSearchFieldToken(field, "*", isNeg, res, allowedfields, phraselog);
						phraselog.clear(); // reset phrases since command is completed
					}
					intoken = false;
					doprocess = false;
				}
				if (intoken) {
					if (type == SEARCH_FIELD && field.length() > 0) {
						// case: fieldname:"value"
						addSearchFieldToken(field, token.getString("value"), isNeg, res, allowedfields, phraselog);
						intoken = false;
						doprocess = false;
					} else if (type == SEARCH_FIELD && field.length() == 0) {
						// case: "fieldname":"name" or "field" anything
						if (token.getString("value").startsWith(":") && phraselog.size() == 1) {
							// we now know a colon is next, so mark it as field
	                        // and keep looking for the value
							field = phraselog.get(0).getString("value");
							String[] parts = token.getString("value").split(":", 1);
							JSONObject o = new JSONObject();
							o.put("value", token.getString("value"));
							o.put("is_neg", false);
							o.put("type", SEARCH_PHRASE);
							phraselog.add(o);
							if (parts[1].length() > 0) {
								// value is included with the :, so wrap it up
								addSearchFieldToken(field, parts[1], isNeg, res, allowedfields, phraselog);
								intoken = false;
								doprocess = false;
							}
							doprocess = false;
						} else {
							// case: "fieldname"string/"fieldname"tag:name
							intoken = false;
						}
					}
					if (!intoken && !doprocess) {
						// command has been fully processed
						phraselog.clear(); // reset phraselog, since we used it for a command
					}
				}
				if (!intoken) {
					// include any non-command related phrases in the query
					for (JSONObject p : phraselog) {
						res.add(new Object[]{p.getString("value"), p.getBoolean("is_neg"), p.getInt("type")});
					}
					phraselog.clear();
				}
				if (!intoken && doprocess) {
					field = "";
					isNeg = token.getBoolean("is_neg");
					String val = token.getString("value");
					if (val.startsWith("tag:")) {
						token.put("value", val.substring(4));
						type = SEARCH_TAG;
					} else if (val.startsWith("is:")) {
						token.put("value", val.substring(3));						
						type = SEARCH_TYPE;
					} else if (val.startsWith("note:")) {
						token.put("value", val.substring(5));
						type = SEARCH_MODEL;
					} else if (val.startsWith("deck:")) {
						token.put("value", val.substring(5));
						type = SEARCH_DECK;
					} else if (val.startsWith("nid:") && val.length() > 4) {
						String dec = val.substring(4);
						try {
							Integer.parseInt(dec);
							token.put("value", val.substring(4));
						} catch (Exception e) {
							try {
								for (String d : dec.split(",")) {
									Integer.parseInt(d);
								}
								token.put("value", val.substring(4));								
							} catch (Exception e2) {
								token.put("value", "0");
							}
						}
						type = SEARCH_NID;
					} else if (val.startsWith("card:")) {
						token.put("value", val.substring(5));
						type = SEARCH_TEMPLATE;
					} else {
						type = SEARCH_FIELD;
						intoken = true;
						String[] parts = token.getString("value").split(":", 1);
						JSONObject o = new JSONObject();
						o.put("value", token.getString("value"));
						o.put("is_neg", isNeg);
						o.put("type", SEARCH_PHRASE);
						phraselog.add(o);
						if (parts.length == 2 && parts[0].length() > 0) {
							field = parts[0];
							if (parts[1].length() > 0) {
								// simple fieldname:value case -
								// no need to look for more data
								addSearchFieldToken(field, parts[1], isNeg, res, allowedfields, phraselog);
							}
							intoken = false;
							doprocess = false;
						}
						if (!intoken) {
							phraselog.clear();
						}
					}
					if (!intoken && doprocess) {
						res.add(new Object[] {token.getString("value"), isNeg, type});
					}
				}
			}
			return res;
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}

	}
	private void addSearchFieldToken(String field, String value, boolean isNeg, ArrayList<Object[]> res, ArrayList<String> allowedfields, ArrayList<JSONObject> phraselog) {
		if (allowedfields.contains(field.toLowerCase())) {
			res.add(new Object[]{field + ":" + value, isNeg, SEARCH_FIELD});
		} else {
			for (JSONObject p : phraselog) {
				try {
					res.add(new Object[]{p.getString("value"), p.getBoolean("is_neg"), p.getInt("type")});
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	// findReplace
	// findDuplicates

}
