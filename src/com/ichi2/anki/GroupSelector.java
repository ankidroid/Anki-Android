///****************************************************************************************
// * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
// *                                                                                      *
// * This program is free software; you can redistribute it and/or modify it under        *
// * the terms of the GNU General Public License as published by the Free Software        *
// * Foundation; either version 3 of the License, or (at your option) any later           *
// * version.                                                                             *
// *                                                                                      *
// * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
// * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
// * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
// *                                                                                      *
// * You should have received a copy of the GNU General Public License along with         *
// * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
// ****************************************************************************************/
//
//package com.ichi2.anki;import com.ichi2.anki2.R;
//
//import android.app.Activity;
//import android.app.AlertDialog;
//import android.content.Context;
//import android.content.DialogInterface;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.content.DialogInterface.OnClickListener;
//import android.os.Bundle;
//import android.text.Editable;
//import android.util.Log;
//import android.view.ContextMenu;
//import android.view.GestureDetector;
//import android.view.KeyEvent;
//import android.view.Menu;
//import android.view.MenuItem;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ContextMenu.ContextMenuInfo;
//import android.view.GestureDetector.SimpleOnGestureListener;
//import android.widget.AdapterView;
//import android.widget.CheckBox;
//import android.widget.EditText;
//import android.widget.ListView;
//import android.widget.SimpleAdapter;
//import android.widget.TextView;
//import android.widget.AdapterView.AdapterContextMenuInfo;
//import android.widget.AdapterView.OnItemClickListener;
//
//import com.ichi2.libanki.Decks;
//import com.tomgibara.android.veecheck.util.PrefSettings;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.TreeMap;
//
//import org.json.JSONArray;
//import org.json.JSONException;
//
//public class GroupSelector extends Activity {
//
//	private static final int ARROW_DOWN = 0;
//	private static final int ARROW_UP = 1;
//	private static final int ARROW_NONE = 2;
//
//	private static final int STATUS_VISIBLE = 0;
//	private static final int STATUS_HAS_CHILDREN = 1;
//	private static final int STATUS_OPENED = 2;
//	private static final int STATUS_CHECKED = 3;
//
//	private static final int CONTEXT_MENU_SELECT_CONF = 0;
//
//	private AlertDialog mConfDialog;
//
//    /**
//     * Swipe Detection
//     */
//    private GestureDetector gestureDetector;
//    View.OnTouchListener gestureListener;
//    private boolean mSwipeEnabled;
//
//	Decks mDeck;
//
//	SimpleAdapter mGroupsAdapter;
//	ListView mGroupListView;
//
//	ArrayList<HashMap<String, String>> mGroups;
//	ArrayList<HashMap<String, String>> mAllGroups;
//	HashMap<String, String> mCurrentSelectedGroup;
//
//	EditText mSessionTimeLimit;
//	EditText mSessionRepLimit;	
//
//	String[] mConfNames;
//	int[] mConfIds;
//	HashMap<Integer, String> mConfs;
//
//	/** for storing extra-information: 0: is visible; 1: has children; 2: is opened; 3: is checked **/
//	HashMap<String, Boolean[]> mGroupStatus;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        setContentView(R.layout.group_selector);
//        mDeck = AnkiDroidApp.deck();
//
//        restorePreferences();
//
//        mGroups = new ArrayList<HashMap<String, String>>();
//        mAllGroups = new ArrayList<HashMap<String, String>>();
//        mGroupStatus = new HashMap<String, Boolean[]>();
//        mGroupListView = (ListView) findViewById(R.id.group_list);
//
//        mGroupsAdapter = new ClickableListAdapter(this, mGroups, R.layout.group_item, 
//        		new String[] { "fullname", "name", "conf", "all", "due", "new" }, 
//        		new int[] { R.id.group_item_check, R.id.group_item_name, R.id.group_item_conf, R.id.group_item_all, R.id.group_item_due, R.id.group_item_new });
//        mGroupsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
//            @Override
//            public boolean setViewValue(View view, Object arg1, String text) {
//                if (view.getId() == R.id.group_item_check) {
//                	((CheckBox)view).setChecked(mGroupStatus.get(text)[STATUS_CHECKED]);
//                    return true;
//                }
//                return false;
//            }
//        });
//        mGroupListView.setOnItemClickListener(new OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//
//            }
//        });
//        mGroupListView.setAdapter(mGroupsAdapter);
//
//        registerForContextMenu(mGroupListView);
//
//        gestureDetector = new GestureDetector(new MyGestureDetector());
//        mGroupListView.setOnTouchListener(new View.OnTouchListener() {
//            public boolean onTouch(View v, MotionEvent event) {
//                if (gestureDetector.onTouchEvent(event)) {
//                    return true;
//                }
//                return false;
//            }
//        });
//
//        mSessionTimeLimit = (EditText) findViewById(R.id.session_minutes);
//        mSessionRepLimit = (EditText) findViewById(R.id.session_questions);
//
//        mSessionTimeLimit.setText(mDeck.getStringVar("sessionTimeLimit", "0"));
//        mSessionRepLimit.setText(mDeck.getStringVar("sessionRepLimit", "0"));
//
//        prepareDialogs();
//        getGroups();
//    }
//
//
//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event)  {
//        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
//            Log.i(AnkiDroidApp.TAG, "GroupSelector - onBackPressed()");
//            closeGroupSelector();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }
//
//
//    @Override
//    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//        super.onCreateContextMenu(menu, v, menuInfo);
//        @SuppressWarnings("unused")
//        MenuItem item;
//        item = menu.add(Menu.NONE, CONTEXT_MENU_SELECT_CONF, Menu.NONE, "select configuration");
//        int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
//        if (mGroups.get(position).get("conf") == null) {
//        	item.setEnabled(false);
//        }
//        String[] name = mGroups.get(position).get("fullname").split("::");
//        menu.setHeaderTitle(name[name.length-1]);
//    }
//
//
//    @Override
//    public boolean onContextItemSelected(MenuItem item) {
//        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
//        mCurrentSelectedGroup = mGroups.get(info.position);
//        switch (item.getItemId()) {
//            case CONTEXT_MENU_SELECT_CONF:
//            	mConfDialog.show();
//                return true;
//            default:
//                return super.onContextItemSelected(item);
//        }
//    }
//
//
//    private SharedPreferences restorePreferences() {
//        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
//        mSwipeEnabled = preferences.getBoolean("swipe", false);
//        return preferences;
//    }
//
//
//    private void prepareDialogs() {
//    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
//    	
//        builder.setTitle("select conf");
//        builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
//        TreeMap<String, Integer> groupconfs = mDeck.groupConfs();
//        int len = groupconfs.size();
//        mConfNames = new String[len];
//        mConfIds = new int[len];
//        mConfs = new HashMap<Integer, String>();
//        int i = 0;
//        for (Map.Entry<String, Integer> g : groupconfs.entrySet()) {
//        	mConfNames[i] = g.getKey();
//        	mConfIds[i] = g.getValue();
//        	mConfs.put(g.getValue(), g.getKey());
//        	i++;
//        }
//        builder.setSingleChoiceItems(mConfNames, 0, new OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {            	
//            	// save to db
//            	mDeck.setGroupConf(mCurrentSelectedGroup.get("fullname"), mConfIds[which]);
//            	mCurrentSelectedGroup.put("gcid", Integer.toString(mConfIds[which]));
//            	mCurrentSelectedGroup.put("conf", "(" + mConfs.get(mConfIds[which]) + ")");
//            	int i = 0;
//            	while (!mAllGroups.get(i).get("fullname").equals(mCurrentSelectedGroup.get("fullname"))) {
//            		i++;
//            	}
//            	mAllGroups.get(i).put("gcid", Integer.toString(mConfIds[which]));
//            	mAllGroups.get(i).put("conf", "(" + mConfs.get(mConfIds[which]) + ")");
//            	mGroupsAdapter.notifyDataSetChanged();
//            	dialog.dismiss();
//            }
//        });
//        mConfDialog = builder.create();
//    }
//
//
//    private void closeGroupSelector() {
//        // save selected groups
//        JSONArray ja = new JSONArray();
//        boolean limited = false;
//        for (int i = 0; i < mAllGroups.size(); i++) {
//        	HashMap<String, String> map =  mAllGroups.get(i);
//        	String name = map.get("fullname");
//        	boolean checked = mGroupStatus.get(name)[STATUS_CHECKED];
//        	int gid = Integer.parseInt(map.get("gid"));
//        	if (checked) {
//            	if (gid != 0) {
//            		ja.put(gid);
//            	}
//        	} else if (gid != 0) {
//        		limited = true;
//        	}
//    		Log.e(AnkiDroidApp.TAG, "Group " + name + " activated: "+ checked);
//        }
//        if (!limited) {
//        	ja = new JSONArray();
//        }
//        if (ja.length() == 0) {
//        	limited = false;
//        }
//        try {
//			mDeck.getQconf().put("groups", ja);
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//
//		String limit = mSessionTimeLimit.getText().toString();
//		if (isValidInt(limit) && !limit.equals("0")) {
//			mDeck.setStringVar("sessionTimeLimit", limit);
//			limited = true;
//		} else {
//			mDeck.setStringVar("sessionTimeLimit", "0");
//		}
//		limit = mSessionRepLimit.getText().toString();
//		if (isValidInt(limit) && !limit.equals("0")) {
//			mDeck.setStringVar("sessionRepLimit", limit);			
//			limited = true;				
//		} else if (isValidInt(limit)){
//			mDeck.setStringVar("sessionRepLimit", "0");
//		}
//		if (limited) {
//			setResult(RESULT_OK, this.getIntent());			
//		} else {
//			setResult(RESULT_CANCELED, this.getIntent());
//		}
//        finish();
//    	if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
//   			MyAnimation.slide(GroupSelector.this, MyAnimation.DOWN);
//    	}
//    }
//
// 
//    private Boolean isValidInt(String test) {
//        try {
//            Integer.parseInt(test);
//            return true;
//        } catch (NumberFormatException e) {
//            return false;
//        }
//    }
//
//
//    private void getGroups() {
//    	TreeMap<String, int[]> groups = mDeck.getSched().groupCountTree();
//    	for (Map.Entry<String, int[]> g : groups.entrySet()) {
//    		HashMap<String, String> map = new HashMap<String, String>();
//    		String[] name = g.getKey().split("::");
//    		StringBuilder sb = new StringBuilder();
//    		for (int i = 0; i < name.length - 1; i++) {
//    			sb.append("    ");
//    		}
//    		sb.append("\u25bd ").append(name[name.length - 1]);
//    		map.put("name", sb.toString());
//    		map.put("fullname", g.getKey());
//    		map.put("all", Integer.toString(g.getValue()[2]));
//    		map.put("due", Integer.toString(g.getValue()[3]));
//    		map.put("new", Integer.toString(g.getValue()[4]));
//    		map.put("gid", Integer.toString(g.getValue()[0]));
//    		int gcid = g.getValue()[1];
//    		map.put("gcid", Integer.toString(gcid));
//    		if (gcid != 0) {
//        		map.put("conf", "(" + mConfs.get(gcid) + ")");    			
//    		}
//        	mGroups.add(map);
//    	}
//
//    	// get activated groups
//    	ArrayList<Integer> selectedGroups = new ArrayList<Integer>();
//		try {
//			JSONArray ja = mDeck.getQconf().getJSONArray("groups");
//	        for (int i = 0; i < ja.length(); i++) {
//	        	selectedGroups.add(ja.getInt(i));
//	        }
//		} catch (JSONException e) {
//			throw new RuntimeException(e);
//		}
//		// check activated groups
//    	for (int i = 0; i < mGroups.size(); i++) {
//    		String fullname = mGroups.get(i).get("fullname");
//    		if (i < mGroups.size() - 1 && mGroups.get(i + 1).get("fullname").startsWith(fullname + "::")) {
//    			mGroupStatus.put(fullname, new Boolean[]{ true, true, true, selectedGroups.isEmpty() || selectedGroups.contains(Integer.parseInt(mGroups.get(i).get("gid"))) });
//    		} else {
//    			changeCaption(mGroups.get(i), ARROW_NONE);
//    			mGroupStatus.put(fullname, new Boolean[]{ true, false, false, selectedGroups.isEmpty() || selectedGroups.contains(Integer.parseInt(mGroups.get(i).get("gid"))) });
//    		}
//    	}
//    	mAllGroups.addAll(mGroups);
//    	mGroupsAdapter.notifyDataSetChanged();
//    }
//
//
//    private void updateList() {
//    	mGroups.clear();
//    	for (int i = 0; i < mAllGroups.size(); i++) {
//    		HashMap<String, String> map = mAllGroups.get(i);
//    		Boolean[] status = mGroupStatus.get(map.get("fullname"));
//    		if (status[STATUS_VISIBLE]) {
//    			if (status[STATUS_HAS_CHILDREN]) {
//        			if (status[STATUS_OPENED]) {
//        				changeCaption(map, ARROW_DOWN);
//        			} else {
//        				changeCaption(map, ARROW_UP);
//        			}    				
//    			}
//    			mGroups.add(map);
//    		}
//    	}
//    	mGroupsAdapter.notifyDataSetChanged();
//    }
//
//
//    private void changeCaption(HashMap<String, String> map, int action) {
//    	String oldname = map.remove("name");
//    	String newname = oldname;
//    	switch (action) {
//    	case ARROW_DOWN:
//        	newname = oldname.replace("\u25b3", "\u25bd");
//        	break;
//    	case ARROW_UP:
//        	newname = oldname.replace("\u25bd", "\u25b3");
//        	break;
//    	case ARROW_NONE:
//        	newname = oldname.replace("\u25bd", "    ");
//        	newname = newname.replace("\u25b3", "    ");
//        	break;
//    	}
//    	map.put("name", newname);
//    }
//
//
//    private void changeChildrenStatus(String name, int position, boolean value) {
//    	for (int i = 0; i < mAllGroups.size(); i++) {
//    		String fullname = mAllGroups.get(i).get("fullname");
//			if (fullname.startsWith(name + "::")) {
//				mGroupStatus.get(fullname)[position] = value;
//			}
//		}
//    }
//
//
//    private class ClickableListAdapter extends SimpleAdapter {
//
//        public ClickableListAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
//                String[] from, int[] to) {
//            super(context, data, resource, from, to);
//        }
//
//
//        public View getView(final int position, View convertView, ViewGroup parent) {
//            View view = super.getView(position, convertView, parent);
//            TextView name = (TextView)view.findViewById(R.id.group_item_name);
//            name.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					String name = mGroups.get(position).get("fullname");
//					if (mGroupStatus.get(name)[STATUS_OPENED]) {
//						mGroupStatus.get(name)[STATUS_OPENED] = false;
//						int pos = position + 1;
//						while (pos < mGroups.size() && mGroups.get(pos).get("fullname").startsWith(name + "::")) {
//							mGroupStatus.get(mGroups.get(pos).get("fullname"))[STATUS_VISIBLE] = false;
//							pos++;
//						}
//					} else if (mGroupStatus.get(name)[STATUS_HAS_CHILDREN]) {
//						mGroupStatus.get(name)[STATUS_OPENED] = true;
//						changeChildrenStatus(name, STATUS_VISIBLE, true);
//						changeChildrenStatus(name, STATUS_OPENED, true);
//					}
//					updateList();
//				}
//            });
//            CheckBox box = (CheckBox)view.findViewById(R.id.group_item_check);
//            box.setOnClickListener(new View.OnClickListener() {
//				@Override
//				public void onClick(View v) {
//					String name = mGroups.get(position).get("fullname");
//					boolean newState = !mGroupStatus.get(name)[STATUS_CHECKED];
//					mGroupStatus.get(name)[STATUS_CHECKED] = newState;
//					if (mGroupStatus.get(name)[1]) {
//						changeChildrenStatus(name, STATUS_CHECKED, newState);
//					}
//					updateList();
//				}
//            });
//
//            return view;
//        }
//    }
//
//
//    class MyGestureDetector extends SimpleOnGestureListener {
//        @Override
//        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            if (mSwipeEnabled) {
//                try {
//                    if (e2.getY() - e1.getY() > StudyOptions.sSwipeMinDistance
//                            && Math.abs(velocityY) > StudyOptions.sSwipeThresholdVelocity
//                            && Math.abs(e1.getX() - e2.getX()) < StudyOptions.sSwipeMaxOffPath) {
//                    	closeGroupSelector();
//                    }
//                } catch (Exception e) {
//                    Log.e(AnkiDroidApp.TAG, "onFling Exception = " + e.getMessage());
//                }
//            }
//            return false;
//        }
//    }
//
//
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if (gestureDetector.onTouchEvent(event))
//            return true;
//        else
//            return false;
//    }
//}
