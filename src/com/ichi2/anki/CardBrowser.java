/****************************************************************************************
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>                         *
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

package com.ichi2.anki;

import android.app.Activity;

import com.ichi2.anki.R;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.async.DeckTask;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledOpenCollectionDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.ichi2.widget.WidgetStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.amr.arabic.ArabicUtilities;
import org.json.JSONException;

public class CardBrowser extends Activity {
    private ArrayList<HashMap<String, String>> mCards;
    private ArrayList<HashMap<String, String>> mAllCards;
    private ListView mCardsListView;
    private SimpleAdapter mCardsAdapter;
    private EditText mSearchEditText;

    private StyledProgressDialog mProgressDialog;
    private StyledOpenCollectionDialog mOpenCollectionDialog;
    private boolean mUndoRedoDialogShowing = false;

    public static Card sCardBrowserCard;

    private int mPositionInCardsList;

    private int mOrder;
	private int mField;

    /** Modifier of percentage of the font size of the card browser */
    private int mrelativeBrowserFontSize = DEFAULT_FONT_SIZE_RATIO;

    private static final int CONTEXT_MENU_MARK = 0;
    private static final int CONTEXT_MENU_SUSPEND = 1;
    private static final int CONTEXT_MENU_DELETE = 2;
    private static final int CONTEXT_MENU_DETAILS = 3;

    private static final int DIALOG_ORDER = 0;
    private static final int DIALOG_CONTEXT_MENU = 1;
    private static final int DIALOG_RELOAD_CARDS = 2;
    private static final int DIALOG_TAGS = 3;
	private static final int DIALOG_FIELD = 4;

    private static final int BACKGROUND_NORMAL = 0;
    private static final int BACKGROUND_MARKED = 1;
    private static final int BACKGROUND_SUSPENDED = 2;
    private static final int BACKGROUND_MARKED_SUSPENDED = 3;

    private static final int MENU_UNDO = 0;
    private static final int MENU_ADD_NOTE = 1;
    private static final int MENU_SHOW_MARKED = 2;
    private static final int MENU_SELECT = 3;
    private static final int MENU_SELECT_SUSPENDED = 31;
    private static final int MENU_SELECT_TAG = 32;
    private static final int MENU_CHANGE_ORDER = 5;
	private static final int MENU_FIELD = 6;

    private static final int EDIT_CARD = 0;
    private static final int ADD_NOTE = 1;
    private static final int DEFAULT_FONT_SIZE_RATIO = 100;

    private static final int CARD_ORDER_NONE = 0;
    private static final int CARD_ORDER_SFLD = 1;
    private static final int CARD_ORDER_DUE = 2;

    private int[] mBackground;

    private boolean mWholeCollection;

    private boolean mShowOnlyMarSus = false;

    private String[] allTags;
	private String[] mFields;
    private HashSet<String> mSelectedTags;

    private boolean mPrefFixArabic;

    private boolean mPrefCacheCardBrowser;
    // private static ArrayList<HashMap<String, String>> sAllCardsCache;

    private Handler mTimerHandler = new Handler();
    private static final int WAIT_TIME_UNTIL_UPDATE = 800;

    private Collection mCol;
    private HashMap<Long, HashMap<Integer, String>> mTemplates;

    private Runnable updateList = new Runnable() {
        public void run() {
            updateCardsList();
        }
    };

    // private DeckTask.TaskListener mUndoRedoHandler = new DeckTask.TaskListener() {
    // @Override
    // public void onPreExecute() {
    // Resources res = getResources();
    // mProgressDialog = ProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes), true);
    // }

    private DialogInterface.OnClickListener mContextMenuListener = new DialogInterface.OnClickListener() {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case CONTEXT_MENU_MARK:
                    DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mUpdateCardHandler, new DeckTask.TaskData(
                            mCol.getSched(), mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))),
                            0));
                    return;

                case CONTEXT_MENU_SUSPEND:
                    DeckTask.launchDeckTask(
                            DeckTask.TASK_TYPE_DISMISS_NOTE,
                            mSuspendCardHandler,
                            new DeckTask.TaskData(mCol.getSched(), mCol.getCard(Long.parseLong(mCards.get(
                                    mPositionInCardsList).get("id"))), 1));
                    return;

                case CONTEXT_MENU_DELETE:
                    Resources res = getResources();
                    StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
                    builder.setTitle(res.getString(R.string.delete_card_title));
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    builder.setMessage(res.getString(R.string.delete_card_message, mCards.get(mPositionInCardsList)
                            .get("sfld")));
                    builder.setPositiveButton(res.getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Card card = mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
                            deleteNote(card);
                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DISMISS_NOTE, mDeleteNoteHandler,
                                    new DeckTask.TaskData(mCol.getSched(), card, 3));
                        }
                    });
                    builder.setNegativeButton(res.getString(R.string.no), null);
                    builder.create().show();
                    return;

                case CONTEXT_MENU_DETAILS:
    				Card tempCard = mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
    				Themes.htmlOkDialog(CardBrowser.this, 
    						getResources().getString(R.string.card_browser_card_details), 
    						tempCard.getCardDetails(CardBrowser.this) ).show();
                    return;
            }
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Themes.applyTheme(this);
        super.onCreate(savedInstanceState);

        View mainView = getLayoutInflater().inflate(R.layout.card_browser, null);
        setContentView(mainView);
        Themes.setContentStyle(mainView, Themes.CALLER_CARDBROWSER);

        mCol = Collection.currentCollection();
        if (mCol == null) {
            reloadCollection(savedInstanceState);
            return;
        }

        Intent i = getIntent();
        mWholeCollection = i.hasExtra("fromDeckpicker") && i.getBooleanExtra("fromDeckpicker", false);

        mBackground = Themes.getCardBrowserBackground();

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
        mrelativeBrowserFontSize = preferences.getInt("relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mPrefCacheCardBrowser = preferences.getBoolean("cardBrowserCache", false);
        mOrder = preferences.getInt("cardBrowserOrder", CARD_ORDER_NONE);

        mCards = new ArrayList<HashMap<String, String>>();
        mAllCards = new ArrayList<HashMap<String, String>>();
        mCardsListView = (ListView) findViewById(R.id.card_browser_list);

        mCardsAdapter = new SizeControlledListAdapter(this, mCards, R.layout.card_item, new String[] { "sfld", "tmpl",
                "deck", "flags" }, new int[] { R.id.card_sfld, R.id.card_tmpl, R.id.card_deck, R.id.card_item },
                mrelativeBrowserFontSize);
        mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Object arg1, String text) {
                if (view.getId() == R.id.card_item) {
                    int which = BACKGROUND_NORMAL;
                    if (text.equals("1")) {
                        which = BACKGROUND_SUSPENDED;
                    } else if (text.equals("2")) {
                        which = BACKGROUND_MARKED;
                    } else if (text.equals("3")) {
                        which = BACKGROUND_MARKED_SUSPENDED;
                    }
                    view.setBackgroundResource(mBackground[which]);
                    return true;
                } else if (view.getId() == R.id.card_deck && text.length() > 0) {
                    view.setVisibility(View.VISIBLE);
                }
                return false;
            }
        });

        mCardsListView.setAdapter(mCardsAdapter);
        mCardsListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent editCard = new Intent(CardBrowser.this, CardEditor.class);
                editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_EDIT);
                mPositionInCardsList = position;
                long cardId = Long.parseLong(mCards.get(mPositionInCardsList).get("id"));
                sCardBrowserCard = mCol.getCard(cardId);
                // if (mSelectedCard == null) {
                // deleteCard(mCards.get(mPositionInCardsList).get("id"), mPositionInCardsList);
                // return;
                // }
                startActivityForResult(editCard, EDIT_CARD);
                if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
            }
        });
        registerForContextMenu(mCardsListView);

        mSearchEditText = (EditText) findViewById(R.id.card_browser_search);
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                mTimerHandler.removeCallbacks(updateList);
                mTimerHandler.postDelayed(updateList, WAIT_TIME_UNTIL_UPDATE);
            }


            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }


            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        if (mWholeCollection) {
            setTitle(getResources().getString(R.string.card_browser_all_decks));
        } else {
            try {
                setTitle(mCol.getDecks().current().getString("name"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        mSelectedTags = new HashSet<String>();

        getCards();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) {
            WidgetStatus.update(this);
            UIUtils.saveCollectionInBackground(mCol);
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        mPositionInCardsList = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
        showDialog(DIALOG_CONTEXT_MENU);
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
            if (mSearchEditText.getText().length() == 0 && !mShowOnlyMarSus && mSelectedTags.size() == 0) {
                // if (mPrefCacheCardBrowser) {
                // sCachedDeckPath = mDeck.getDeckPath();
                // sAllCardsCache = new ArrayList<HashMap<String, String>>();
                // sAllCardsCache.addAll(mAllCards);
                // }
                closeCardBrowser();
            } else {
                mSearchEditText.setText("");
                mSearchEditText.setHint(R.string.downloaddeck_search);
                mSelectedTags.clear();
                mCards.clear();
                mCards.addAll(mAllCards);
                updateList();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo);
        item.setIcon(R.drawable.ic_menu_revert);
        item = menu.add(Menu.NONE, MENU_ADD_NOTE, Menu.NONE, R.string.card_editor_add_card);
        item.setIcon(R.drawable.ic_menu_add);
		if (mWholeCollection == false) {
			item = menu.add(Menu.NONE, MENU_FIELD, Menu.NONE, R.string.card_browser_field);
			item.setIcon(R.drawable.ic_menu_add);
		}
		item = menu.add(Menu.NONE, MENU_CHANGE_ORDER, Menu.NONE, R.string.card_browser_change_display_order);
        item.setIcon(R.drawable.ic_menu_sort_by_size);
        item = menu.add(Menu.NONE, MENU_SHOW_MARKED, Menu.NONE, R.string.card_browser_show_marked);
        item.setIcon(R.drawable.ic_menu_star_on);
        item = menu.add(Menu.NONE, MENU_SELECT_SUSPENDED, Menu.NONE, R.string.card_browser_show_suspended);
        item.setIcon(R.drawable.ic_menu_search);
        item = menu.add(Menu.NONE, MENU_SELECT_TAG, Menu.NONE, R.string.card_browser_search_by_tag);
        item.setIcon(R.drawable.ic_menu_close_clear_cancel);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCol == null) {
            return false;
        }
        menu.findItem(MENU_UNDO).setEnabled(mCol.undoAvailable());
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case MENU_UNDO:
                // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoRedoHandler, new DeckTask.TaskData(0, mDeck, 0,
                // true));
                return true;

            case MENU_ADD_NOTE:
                Intent intent = new Intent(CardBrowser.this, CardEditor.class);
                intent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_ADD);
                startActivityForResult(intent, ADD_NOTE);
                if (UIUtils.getApiLevel() > 4) {
                    ActivityTransitionAnimation.slide(CardBrowser.this, ActivityTransitionAnimation.LEFT);
                }
                return true;

            case MENU_SHOW_MARKED:
                mShowOnlyMarSus = true;
                mSearchEditText.setHint(R.string.card_browser_show_marked);
                mCards.clear();
                for (int i = 0; i < mAllCards.size(); i++) {
                    int flags = Integer.parseInt(mAllCards.get(i).get("flags"));
                    if (flags == 2 || flags == 3) {
                        mCards.add(mAllCards.get(i));
                    }
                }
                updateList();
                return true;

            case MENU_SELECT_SUSPENDED:
                mShowOnlyMarSus = true;
                mSearchEditText.setHint(R.string.card_browser_show_suspended);
                mCards.clear();
                for (int i = 0; i < mAllCards.size(); i++) {
                    int flags = Integer.parseInt(mAllCards.get(i).get("flags"));
                    if (flags == 1 || flags == 3) {
                        mCards.add(mAllCards.get(i));
                    }
                }
                updateList();
                return true;

            case MENU_SELECT_TAG:
                showDialog(DIALOG_TAGS);
                return true;

            case MENU_CHANGE_ORDER:
                showDialog(DIALOG_ORDER);
                return true;
			
		case MENU_FIELD:
			showDialog(DIALOG_FIELD);
			return true;
        }

        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == DeckPicker.RESULT_DB_ERROR) {
            closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
        }

        if (requestCode == EDIT_CARD && resultCode != RESULT_CANCELED) {
            Log.i(AnkiDroidApp.TAG, "CardBrowser: Saving card...");
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler,
                    new DeckTask.TaskData(mCol.getSched(), sCardBrowserCard, false));
        } else if (requestCode == ADD_NOTE && resultCode == RESULT_OK) {
            getCards();
        }
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        StyledDialog dialog = null;
        Resources res = getResources();
        StyledDialog.Builder builder = new StyledDialog.Builder(this);

        switch (id) {
            case DIALOG_ORDER:
                builder.setTitle(res.getString(R.string.card_browser_change_display_order_title));
                builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
                builder.setSingleChoiceItems(getResources().getStringArray(R.array.card_browser_order_labels), mOrder,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int which) {
                                if (which != mOrder) {
                                    mOrder = which;
                                    AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
                                            .putInt("cardBrowserOrder", mOrder).commit();
                                    if (mOrder != CARD_ORDER_NONE) {
                                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_CARD_BROWSER_LIST,
                                                mSortCardsHandler, new DeckTask.TaskData(mAllCards,
                                                        new HashMapCompare()));
                                    }
                                }
                            }
                        });
                dialog = builder.create();
                break;

            case DIALOG_CONTEXT_MENU:
                String[] entries = new String[4];
                @SuppressWarnings("unused")
                MenuItem item;
                entries[CONTEXT_MENU_MARK] = res.getString(R.string.card_browser_mark_card);
                entries[CONTEXT_MENU_SUSPEND] = res.getString(R.string.card_browser_suspend_card);
                entries[CONTEXT_MENU_DELETE] = res.getString(R.string.card_browser_delete_card);
                entries[CONTEXT_MENU_DETAILS] = res.getString(R.string.card_browser_card_details);
                builder.setTitle("contextmenu");
                builder.setIcon(R.drawable.ic_menu_manage);
                builder.setItems(entries, mContextMenuListener);
                dialog = builder.create();
                break;

            case DIALOG_TAGS:
                allTags = mCol.getTags().all();
                builder.setTitle(R.string.studyoptions_limit_select_tags);
                builder.setMultiChoiceItems(allTags, new boolean[allTags.length],
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String tag = allTags[which];
                                if (mSelectedTags.contains(tag)) {
                                    Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                                    mSelectedTags.remove(tag);
                                } else {
                                    Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
                                    mSelectedTags.add(tag);
                                }
                            }
                        });
                builder.setPositiveButton(res.getString(R.string.select), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSearchEditText.setText("");
                        mTimerHandler.removeCallbacks(updateList);
                        String tags = mSelectedTags.toString();
                        mSearchEditText.setHint(getResources().getString(R.string.card_browser_tags_shown,
                                tags.substring(1, tags.length() - 1)));
                        DeckTask.launchDeckTask(
                                DeckTask.TASK_TYPE_UPDATE_CARD_BROWSER_LIST,
                                new DeckTask.TaskListener() {
                                    @Override
                                    public void onPreExecute() {
                                        mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                                                getResources().getString(R.string.card_browser_filtering_cards), true);
                                    }


                                    @Override
                                    public void onProgressUpdate(DeckTask.TaskData... values) {
                                    }


                                    @Override
                                    public void onPostExecute(DeckTask.TaskData result) {
                                        updateList();
                                        if (mProgressDialog != null && mProgressDialog.isShowing()) {
                                            mProgressDialog.dismiss();
                                        }
                                    }
                                }, new DeckTask.TaskData(mAllCards), new DeckTask.TaskData(mCards),
                                new DeckTask.TaskData(new Object[] { mSelectedTags }));
                    }
                });
                builder.setNegativeButton(res.getString(R.string.cancel), new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSelectedTags.clear();
                    }
                });
                builder.setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mSelectedTags.clear();
                    }
                });
                dialog = builder.create();
                break;
//		case DIALOG_RELOAD_CARDS:
//			builder.setTitle(res.getString(R.string.pref_cache_cardbrowser));
//			builder.setMessage(res.getString(R.string.pref_cache_cardbrowser_reload));
//			builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {
//
//				@Override
//				public void onClick(DialogInterface arg0, int arg1) {
//					DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS,
//							mLoadCardsHandler,
//							new DeckTask.TaskData(mDeck, LOAD_CHUNK));	
//					}
//				
//			});
//			builder.setNegativeButton(res.getString(R.string.no), new OnClickListener() {
//
//				@Override
//				public void onClick(DialogInterface arg0, int arg1) {
//					mAllCards.addAll(sAllCardsCache);
//					mCards.addAll(mAllCards);
//					updateList();
//				}
//				
//			});
//			builder.setCancelable(true);
//			builder.setOnCancelListener(new OnCancelListener() {
//
//				@Override
//				public void onCancel(DialogInterface arg0) {
//					mAllCards.addAll(sAllCardsCache);
//					mCards.addAll(mAllCards);
//					updateList();
//				}
//				
//			});
//			dialog = builder.create();
//			break;
		case DIALOG_FIELD:
			builder.setTitle(res
					.getString(R.string.card_browser_field_title));
			builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
			
	        HashMap<String, String> card = mAllCards.get(0);
	        
			String[][] items = mCol.getCard(Long.parseLong( card.get("id") )).note().items();
			
			
			mFields = new String[items.length+1];
			mFields[0]="SFLD";
			
			for (int i = 0; i < items.length; i++) {
				mFields[i+1] = items[i][0];
			}
			
			builder.setSingleChoiceItems(mFields, 0, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int which) {
					if (which != mField) {
						mField = which;
						AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit().putInt("cardBrowserField", mField).commit();
						getCards();
					}
				}
	        });
			dialog = builder.create();
			break;
        }
        return dialog;
    }


    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        Resources res = getResources();
        StyledDialog ad = (StyledDialog) dialog;
        switch (id) {
            case DIALOG_CONTEXT_MENU:
                HashMap<String, String> card = mCards.get(mPositionInCardsList);
                int flags = Integer.parseInt(card.get("flags"));
                if (flags == 2 || flags == 3) {
                    ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_unmark_card));
                    Log.i(AnkiDroidApp.TAG, "Selected Card is currently marked");
                } else {
                    ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_mark_card));
                }
                if (flags == 1 || flags == 3) {
                    ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_unsuspend_card));
                    Log.i(AnkiDroidApp.TAG, "Selected Card is currently suspended");
                } else {
                    ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_suspend_card));
                }
                ad.setTitle(card.get("sfld"));
                break;
            case DIALOG_TAGS:
                mSelectedTags.clear();
                ad.setMultiChoiceItems(allTags, new boolean[allTags.length], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String tag = allTags[which];
                        if (mSelectedTags.contains(tag)) {
                            Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
                            mSelectedTags.remove(tag);
                        } else {
                            Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
                            mSelectedTags.add(tag);
                        }
                    }
                });
                break;
        }
    }


    private void updateCardsList() {
        String searchText = mSearchEditText.getText().toString().toLowerCase();
        mShowOnlyMarSus = false;

        mSearchEditText.setHint(R.string.downloaddeck_search);
        mCards.clear();
        if (searchText.length() == 0 && mSelectedTags.size() == 0) {
            mCards.addAll(mAllCards);
        } else {
            for (int i = 0; i < mAllCards.size(); i++) {
                HashMap<String, String> card = mAllCards.get(i);
                if (card.get("sfld").toLowerCase().indexOf(searchText) != -1) {
                    mCards.add(mAllCards.get(i));
                }
            }
        }
        updateList();
    }


    private void getCards() {
        // if ((sCachedDeckPath != null && !sCachedDeckPath.equals(mDeck.getDeckPath())) || !mPrefCacheCardBrowser) {
        // sCachedDeckPath = null;
        // sAllCardsCache = null;
        // }
        // if (mPrefCacheCardBrowser && sAllCardsCache != null && !sAllCardsCache.isEmpty()) {
        // showDialog(DIALOG_RELOAD_CARDS);
        // } else {
        mAllCards.clear();
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS, mLoadCardsHandler, new DeckTask.TaskData(mCol, 0,
                mWholeCollection));
        // }
    }


    private void reloadCollection(final Bundle savedInstanceState) {
        DeckTask.launchDeckTask(
                DeckTask.TASK_TYPE_OPEN_COLLECTION,
                new DeckTask.TaskListener() {

                    @Override
                    public void onPostExecute(DeckTask.TaskData result) {
                        if (mOpenCollectionDialog.isShowing()) {
                            try {
                            	mOpenCollectionDialog.dismiss();
                            } catch (Exception e) {
                                Log.e(AnkiDroidApp.TAG, "onPostExecute - Dialog dismiss Exception = " + e.getMessage());
                            }
                        }
                        mCol = result.getCollection();
                        Collection.putCurrentCollection(mCol);
                        if (mCol == null) {
                            finish();
                        } else {
                            onCreate(savedInstanceState);
                        }
                    }


                    @Override
                    public void onPreExecute() {
                    	mOpenCollectionDialog = StyledOpenCollectionDialog.show(CardBrowser.this, getResources().getString(R.string.open_collection), new OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface arg0) {
                                        finish();
                                    }
                                });
                    }


                    @Override
                    public void onProgressUpdate(DeckTask.TaskData... values) {
                    }
                },
                new DeckTask.TaskData(AnkiDroidApp.getSharedPrefs(getBaseContext()).getString("deckPath",
                        AnkiDroidApp.getDefaultAnkiDroidDirectory())
                        + AnkiDroidApp.COLLECTION_PATH));
    }


    private void updateList() {
        mCardsAdapter.notifyDataSetChanged();
        int count = mCards.size();
        UIUtils.setActionBarSubtitle(this,
                getResources().getQuantityString(R.plurals.card_browser_subtitle, count, count, mAllCards.size()));
    }


    private int getPosition(ArrayList<HashMap<String, String>> list, long cardId) {
        String cardid = Long.toString(cardId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).get("id").equals(cardid)) {
                return i;
            }
        }
        return -1;
    }


    private void updateCardInList(Card card) {
        Note note = card.note();
        for (Card c : note.cards()) {
            int aPos = getPosition(mAllCards, c.getId());
            int pos = getPosition(mCards, c.getId());

            String sfld = note.getSFld();
            mAllCards.get(aPos).put("sfld", sfld);
            mCards.get(pos).put("sfld", sfld);

            if (mWholeCollection) {
                String deckName;
                try {
                    deckName = mCol.getDecks().get(card.getDid()).getString("name");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                mAllCards.get(aPos).put("deck", deckName);
                mCards.get(pos).put("deck", deckName);
            }

            String flags = Integer.toString((c.getQueue() == -1 ? 1 : 0) + (note.hasTag("marked") ? 2 : 0));
            mAllCards.get(aPos).put("flags", flags);
            mCards.get(pos).put("flags", flags);
        }
        updateList();
    }


    private void deleteNote(Card card) {
        ArrayList<Card> cards = card.note().cards();
        for (Card c : cards) {
            mCards.remove(getPosition(mCards, c.getId()));
            mAllCards.remove(getPosition(mAllCards, c.getId()));
        }
        updateList();
    }

    private DeckTask.TaskListener mLoadCardsHandler = new DeckTask.TaskListener() {
        boolean canceled = false;


        @Override
        public void onPreExecute() {
            if (!mUndoRedoDialogShowing) {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.setMessage(getResources().getString(R.string.card_browser_load));
                    mProgressDialog.setOnCancelListener(new OnCancelListener() {

                        @Override
                        public void onCancel(DialogInterface arg0) {
                            canceled = true;
                            DeckTask.cancelTask();
                            closeCardBrowser();
                        }
                    });
                } else {
                    mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                            getResources().getString(R.string.card_browser_load), true, true, new OnCancelListener() {

                                @Override
                                public void onCancel(DialogInterface arg0) {
                                    canceled = true;
                                    DeckTask.cancelTask();
                                    closeCardBrowser();
                                }
                            });
                }
            } else {
                mProgressDialog.setMessage(getResources().getString(R.string.card_browser_load));
                mUndoRedoDialogShowing = false;
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            // This verification would not be necessary if
            // onConfigurationChanged it's executed correctly (which seems
            // that emulator does not do)
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            if (canceled) {
                return;
            }
            ArrayList<HashMap<String, String>> cards = values[0].getCards();
            if (cards == null) {
                Resources res = getResources();
                StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
                builder.setTitle(res.getString(R.string.error));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(res.getString(R.string.card_browser_cardloading_error));
                builder.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeCardBrowser();
                    }
                });
                builder.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        closeCardBrowser();
                    }
                });
                builder.create().show();
            } else {
                if (mPrefFixArabic) {
                    for (HashMap<String, String> entry : cards) {
                        entry.put("sfld", ArabicUtilities.reshapeSentence(entry.get("sfld")));
                    }
                }
                try {
					
					int field = AnkiDroidApp.getSharedPrefs(getBaseContext()).getInt("cardBrowserField", 0);
					
					if (cards.size() > 0 && field > 0 && (mFields != null)) {
						Card tempCard = mCol.getCard(Long.parseLong(cards.get(0).get("id")));						
						ArrayList<String> uniqueFields = new ArrayList<String>();
						for (HashMap<String, String> entry : cards) {
							tempCard = mCol.getCard(Long.parseLong(entry.get("id")));
							String item = tempCard.note().getitem(mFields[field]);
							entry.put("sfld", item);

							if (!uniqueFields.contains(item)) {
								uniqueFields.add(item);
								mAllCards.add(entry);
								mCards.add(entry);
							}						
						}
					} else {
						mAllCards.addAll(cards);
						mCards.addAll(cards);
					}
					
					
					
                    if (mOrder == CARD_ORDER_NONE) {
                        updateCardsList();
                        mProgressDialog.dismiss();
                    } else {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_CARD_BROWSER_LIST, mSortCardsHandler,
                                new DeckTask.TaskData(mAllCards, new HashMapCompare()));
                    }
                } catch (OutOfMemoryError e) {
                    Log.e(AnkiDroidApp.TAG, "CardBrowser: mLoadCardsHandler: OutOfMemoryError: " + e);
                    Themes.showThemedToast(CardBrowser.this,
                            getResources().getString(R.string.error_insufficient_memory), false);
                    closeCardBrowser();
                }
            }
        }
    };

    private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            updateCardInList(values[0].getCard());
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (!result.getBoolean()) {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

        }
    };

    private DeckTask.TaskListener mSuspendCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            if (result.getBoolean()) {
                updateCardInList(mCol.getCard(Long.parseLong(mCards.get(mPositionInCardsList).get("id"))));
            } else {
                closeCardBrowser(DeckPicker.RESULT_DB_ERROR);
            }
            mProgressDialog.dismiss();

        }
    };

    private DeckTask.TaskListener mDeleteNoteHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res.getString(R.string.saving_changes),
                    true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();

        }
    };

    private DeckTask.TaskListener mSortCardsHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
            Resources res = getResources();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.setMessage(res.getString(R.string.card_browser_sorting_cards));
            } else {
                mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
                        res.getString(R.string.card_browser_sorting_cards), true);
            }
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            updateCardsList();
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    };


    // private DeckTask.TaskListener mUndoRedoHandler = new DeckTask.TaskListener() {
    // @Override
    // public void onPreExecute() {
    // Resources res = getResources();
    // mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
    // .getString(R.string.saving_changes), true);
    // }
    //
    // @Override
    // public void onProgressUpdate(DeckTask.TaskData... values) {
    // }
    //
    // @Override
    // public void onPostExecute(DeckTask.TaskData result) {
    // mUndoRedoCardId = result.getLong();
    // String undoType = result.getString();
    // if (undoType.equals(Deck.UNDO_TYPE_DELETE_CARD)) {
    // int position = getPosition(mDeletedCards, mUndoRedoCardId);
    // if (position != -1) {
    // HashMap<String, String> data = new HashMap<String, String>();
    // data.put("id", mDeletedCards.get(position).get("id"));
    // data.put("question", mDeletedCards.get(position).get(
    // "question"));
    // data.put("answer", mDeletedCards.get(position)
    // .get("answer"));
    // data.put("flags", mDeletedCards.get(position)
    // .get("flags"));
    // mAllCards.add(Integer.parseInt(mDeletedCards.get(position)
    // .get("allCardPos")), data);
    // mDeletedCards.remove(position);
    // updateCardsList();
    // } else {
    // deleteCard(Long.toString(mUndoRedoCardId), getPosition(
    // mCards, mUndoRedoCardId));
    // }
    // mProgressDialog.dismiss();
    // } else {
    // mUndoRedoCard = mDeck.cardFromId(mUndoRedoCardId);
    // if (undoType.equals(Deck.UNDO_TYPE_EDIT_CARD)) {
    // mUndoRedoCard.fromDB(mUndoRedoCardId);
    // int pos = getPosition(mAllCards, mUndoRedoCardId);
    // updateCard(mUndoRedoCard, mAllCards, pos);
    // pos = getPosition(mCards, mUndoRedoCardId);
    // if (pos != -1) {
    // updateCard(mUndoRedoCard, mCards, pos);
    // }
    // updateList();
    // mProgressDialog.dismiss();
    // } else if (undoType.equals(Deck.UNDO_TYPE_MARK_CARD)) {
    // markCards(mUndoRedoCard.getFactId(), mUndoRedoCard
    // .isMarked());
    // mProgressDialog.dismiss();
    // } else if (undoType.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
    // suspendCard(mUndoRedoCard, getPosition(mCards,
    // mUndoRedoCardId), mUndoRedoCard.getSuspendedState());
    // mProgressDialog.dismiss();
    // } else {
    // mUndoRedoDialogShowing = true;
    // getCards();
    // }
    // }
    // }
    // };

    private void closeCardBrowser() {
        closeCardBrowser(RESULT_CANCELED);
    }


    private void closeCardBrowser(int result) {
        setResult(result);
        finish();
        if (UIUtils.getApiLevel() > 4) {
            ActivityTransitionAnimation.slide(this, ActivityTransitionAnimation.RIGHT);
        }
    }

    public class SizeControlledListAdapter extends SimpleAdapter {

        private int fontSizeScalePcent;
        private float originalTextSize = -1.0f;
        private Typeface mCustomTypeface;

        public SizeControlledListAdapter(Context context, List<? extends Map<String, ?>> data, int resource,
                String[] from, int[] to, int fontSizeScalePcent) {
            super(context, data, resource, from, to);
            this.fontSizeScalePcent = fontSizeScalePcent;

            // Use custom font if selected from preferences
            SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(getBaseContext());
            String customFont = preferences.getString("browserEditorFont", "");
            if (!customFont.equals("")) {
                mCustomTypeface = AnkiFont.getTypeface(context, customFont);
            }
        }


        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            // Iterate on all first level children
            if (view instanceof ViewGroup) {
                ViewGroup group = ((ViewGroup) view);
                View child;
                for (int i = 0; i < group.getChildCount(); i++) {
                    child = group.getChildAt(i);
                    // and set text size on all TextViews found
                    if (child instanceof TextView) {
                        // mBrowserFontSize
                        float currentSize = ((TextView) child).getTextSize();
                        if (originalTextSize < 0) {
                            originalTextSize = ((TextView) child).getTextSize();
                        }
                        // do nothing when pref is 100% and apply scaling only
                        // once
                        if ((fontSizeScalePcent < 99 || fontSizeScalePcent > 101)
                                && (Math.abs(originalTextSize - currentSize) < 0.1)) {
                            ((TextView) child).setTextSize(TypedValue.COMPLEX_UNIT_SP, originalTextSize
                                    * (fontSizeScalePcent / 100.0f));
                        }

                        if (mCustomTypeface != null) {
                            ((TextView) child).setTypeface(mCustomTypeface);
                        }

                    }

                }

            }

            return view;
        }
    }

    private class HashMapCompare implements Comparator<HashMap<String, String>> {
        @Override
        public int compare(HashMap<String, String> object1, HashMap<String, String> object2) {
            try {
                int result = 0;
                switch (mOrder) {
                    case CARD_ORDER_SFLD:
                        result = object1.get("sfld").compareToIgnoreCase(object2.get("sfld"));
                        if (result == 0) {
                            result = object1.get("tmpl").compareToIgnoreCase(object2.get("tmpl"));
                        }
                        break;

                    case CARD_ORDER_DUE:
                        result = Long.valueOf(object1.get("due")).compareTo(Long.valueOf(object2.get("due")));
                        if (result == 0) {
                            result = object1.get("sfld").compareToIgnoreCase(object2.get("sfld"));
                        }
                        break;
                }
                return result;
            } catch (Exception e) {
                Log.e(AnkiDroidApp.TAG, "Error on sorting cards: " + e);
                return 0;
            }
        }
    }
}
