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
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import android.view.SubMenu;
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
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.StyledProgressDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.amr.arabic.ArabicUtilities;

public class CardBrowser extends Activity {
	private ArrayList<HashMap<String, String>> mCards;
	private ArrayList<HashMap<String, String>> mAllCards;
	private ArrayList<HashMap<String, String>> mDeletedCards;
	private ListView mCardsListView;
	private SimpleAdapter mCardsAdapter;
	private EditText mSearchEditText;

	private StyledProgressDialog mProgressDialog;
	private boolean mUndoRedoDialogShowing = false;
	private Card mSelectedCard;
	private Card mUndoRedoCard;
	private long mUndoRedoCardId;
	private static Card sEditorCard;
	private boolean mIsSuspended;
	private boolean mIsMarked;
	private Deck mDeck;
	private int mPositionInCardsList;

	/** Modifier of percentage of the font size of the card browser */
	private int mrelativeBrowserFontSize = CardModel.DEFAULT_FONT_SIZE_RATIO;

	public static final int LOAD_CHUNK = 200;

	private static final int CONTEXT_MENU_MARK = 0;
	private static final int CONTEXT_MENU_SUSPEND = 1;
	private static final int CONTEXT_MENU_DELETE = 2;
	private static final int CONTEXT_MENU_DETAILS = 3;

	private static final int DIALOG_ORDER = 0;
	private static final int DIALOG_CONTEXT_MENU = 1;
	private static final int DIALOG_RELOAD_CARDS = 2;

	private static final int BACKGROUND_NORMAL = 0;
	private static final int BACKGROUND_MARKED = 1;
	private static final int BACKGROUND_SUSPENDED = 2;
	private static final int BACKGROUND_MARKED_SUSPENDED = 3;

	private static final int MENU_UNDO = 0;
	private static final int MENU_REDO = 1;
	private static final int MENU_ADD_FACT = 2;
	private static final int MENU_SHOW_MARKED = 3;
	private static final int MENU_SELECT = 4;
	private static final int MENU_SELECT_SUSPENDED = 41;
	private static final int MENU_SELECT_TAG = 42;
	private static final int MENU_CHANGE_ORDER = 5;

	private static final int EDIT_CARD = 0;
	private static final int ADD_FACT = 1;
	private static final int DEFAULT_FONT_SIZE_RATIO = 100;

	private static final int CARD_ORDER_ANSWER = 1;
	private static final int CARD_ORDER_QUESTION = 0;
	private static final int CARD_ORDER_DUE = 2;
	private static final int CARD_ORDER_INTERVAL = 3;
	private static final int CARD_ORDER_FACTOR = 4;
	private static final int CARD_ORDER_CREATED = 5;

	private int[] mBackground;

	private boolean mShowOnlyMarSus = false;

	private int mSelectedOrder = CARD_ORDER_CREATED;
	
	private String[] allTags;
	private HashSet<String> mSelectedTags;
	private StyledDialog mTagsDialog;

	private boolean mPrefFixArabic;

	private boolean mPrefCacheCardBrowser;
	private static ArrayList<HashMap<String, String>> sAllCardsCache;
	private static String sCachedDeckPath;

    private Handler mTimerHandler = new Handler();
    private static final int WAIT_TIME_UNTIL_UPDATE = 500;

	private Runnable updateList = new Runnable() {
    	public void run() {
    		updateCardsList();
    	}
    };


    private DialogInterface.OnClickListener mContextMenuListener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (mSelectedCard == null) {
				return;
			}
			switch (which) {
			case CONTEXT_MENU_MARK:
				DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD,
						mMarkCardHandler, new DeckTask.TaskData(0, mDeck,
								mSelectedCard));
				return;
			case CONTEXT_MENU_SUSPEND:
				DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD,
						mSuspendCardHandler, new DeckTask.TaskData(0, mDeck,
								mSelectedCard));
				return;
			case CONTEXT_MENU_DELETE:
				Resources res = getResources();
				StyledDialog.Builder builder = new StyledDialog.Builder(CardBrowser.this);
				builder.setTitle(res.getString(R.string.delete_card_title));
				builder.setIcon(android.R.drawable.ic_dialog_alert);
				builder.setMessage(String.format(res
						.getString(R.string.delete_card_message), mCards.get(
						mPositionInCardsList).get("question"), mCards.get(
						mPositionInCardsList).get("answer")));
				builder.setPositiveButton(res.getString(R.string.yes),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								DeckTask.launchDeckTask(
										DeckTask.TASK_TYPE_DELETE_CARD,
										mDeleteCardHandler, new DeckTask.TaskData(
												0, mDeck, mSelectedCard));
							}
						});
				builder.setNegativeButton(res.getString(R.string.no), null);
				builder.create().show();
				return;
			case CONTEXT_MENU_DETAILS:
				Themes.htmlOkDialog(CardBrowser.this, getResources().getString(R.string.card_browser_card_details), mSelectedCard.getCardDetails(CardBrowser.this, true)).show();
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

		mDeck = DeckManager.getMainDeck();
		if (mDeck == null) {
			finish();
			return;
		}
		mDeck.resetUndo();

		mBackground = Themes.getCardBrowserBackground();

		SharedPreferences preferences = PrefSettings
				.getSharedPrefs(getBaseContext());
		mrelativeBrowserFontSize = preferences.getInt(
				"relativeCardBrowserFontSize", DEFAULT_FONT_SIZE_RATIO);
		mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
		mPrefCacheCardBrowser = preferences.getBoolean("cardBrowserCache", false);

		mCards = new ArrayList<HashMap<String, String>>();
		mAllCards = new ArrayList<HashMap<String, String>>();
		mCardsListView = (ListView) findViewById(R.id.card_browser_list);

		mCardsAdapter = new SizeControlledListAdapter(this, mCards,
				R.layout.card_item, new String[] { "question", "answer",
						"flags" }, new int[] { R.id.card_question,
						R.id.card_answer, R.id.card_item },
				mrelativeBrowserFontSize);
		mCardsAdapter.setViewBinder(new SimpleAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Object arg1, String text) {
				if (view.getId() == R.id.card_item) {
					int which = BACKGROUND_NORMAL;
					if (text.equals("11")) {
						which = BACKGROUND_MARKED_SUSPENDED;
					} else if (text.substring(1, 2).equals("1")) {
						which = BACKGROUND_SUSPENDED;
					} else if (text.substring(0, 1).equals("1")) {
						which = BACKGROUND_MARKED;
					}
					view.setBackgroundResource(mBackground[which]);
					return true;
				}
				return false;
			}
		});

		mCardsListView.setAdapter(mCardsAdapter);
		mCardsListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Intent editCard = new Intent(CardBrowser.this, CardEditor.class);
	            editCard.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_EDIT);
	            editCard.putExtra(CardEditor.EXTRA_DECKPATH, DeckManager.getMainDeckPath());
				mPositionInCardsList = position;
				mSelectedCard = mDeck.cardFromId(Long.parseLong(mCards.get(
						mPositionInCardsList).get("id")));
				if (mSelectedCard == null) {
					deleteCard(mCards.get(mPositionInCardsList).get("id"), mPositionInCardsList);
					return;
				}
				sEditorCard = mSelectedCard;
				editCard.putExtra("callfromcardbrowser", true);
				startActivityForResult(editCard, EDIT_CARD);
				if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
					ActivityTransitionAnimation.slide(CardBrowser.this,
							ActivityTransitionAnimation.LEFT);
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

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}
		});
		getWindow().setSoftInputMode(
				WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		setTitle(mDeck.getDeckName());

		allTags = null;
		mSelectedTags = new HashSet<String>();

		getCards();
	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		mPositionInCardsList = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
		showDialog(DIALOG_CONTEXT_MENU);
	}


	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			// Log.i(AnkiDroidApp.TAG, "CardBrowser - onBackPressed()");
			if (mSearchEditText.getText().length() == 0 && !mShowOnlyMarSus
					&& mSelectedTags.size() == 0) {
				if (mPrefCacheCardBrowser) {
					sCachedDeckPath = mDeck.getDeckPath();
					sAllCardsCache = new ArrayList<HashMap<String, String>>();
					sAllCardsCache.addAll(mAllCards);					
				}
				setResult(RESULT_OK);
				finish();
				if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
					ActivityTransitionAnimation.slide(CardBrowser.this,
							ActivityTransitionAnimation.RIGHT);
				}
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
		item = menu.add(Menu.NONE, MENU_REDO, Menu.NONE, R.string.redo);
		item.setIcon(R.drawable.ic_menu_redo);
		item = menu.add(Menu.NONE, MENU_ADD_FACT, Menu.NONE, R.string.add);
		item.setIcon(R.drawable.ic_menu_add);
		item = menu.add(Menu.NONE, MENU_CHANGE_ORDER, Menu.NONE,
				R.string.card_browser_change_display_order);
		item.setIcon(R.drawable.ic_menu_sort_by_size);
		item = menu.add(Menu.NONE, MENU_SHOW_MARKED, Menu.NONE,
				R.string.card_browser_show_marked);
		item.setIcon(R.drawable.ic_menu_star_on);
		SubMenu selectSubMenu = menu.addSubMenu(Menu.NONE, MENU_SELECT,
				Menu.NONE, R.string.card_browser_search);
		selectSubMenu.setIcon(R.drawable.ic_menu_search);
		selectSubMenu.add(Menu.NONE, MENU_SELECT_SUSPENDED, Menu.NONE,
				R.string.card_browser_search_suspended);
		selectSubMenu.add(Menu.NONE, MENU_SELECT_TAG, Menu.NONE,
				R.string.card_browser_search_by_tag);
		item.setIcon(R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(MENU_UNDO).setEnabled(mDeck.undoAvailable());
		menu.findItem(MENU_REDO).setEnabled(mDeck.redoAvailable());
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_UNDO:
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUndoRedoHandler,
					new DeckTask.TaskData(0, mDeck, 0, true));
			return true;
		case MENU_REDO:
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUndoRedoHandler,
					new DeckTask.TaskData(0, mDeck, 0, true));
			return true;
		case MENU_ADD_FACT:
			Intent intent = new Intent(CardBrowser.this, CardEditor.class);
			intent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_CARDBROWSER_ADD);
			intent.putExtra(CardEditor.EXTRA_DECKPATH, DeckManager.getMainDeckPath());
			startActivityForResult(intent, ADD_FACT);
			if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
				ActivityTransitionAnimation.slide(CardBrowser.this,
						ActivityTransitionAnimation.LEFT);
			}
			return true;
		case MENU_SHOW_MARKED:
			mShowOnlyMarSus = true;
			mSearchEditText.setHint(R.string.card_browser_show_marked);
			mCards.clear();
			for (int i = 0; i < mAllCards.size(); i++) {
				if ((mAllCards.get(i).get("question").toLowerCase().indexOf(
						mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards
						.get(i).get("answer").toLowerCase().indexOf(
								mSearchEditText.getText().toString()
										.toLowerCase()) != -1)
						&& mAllCards.get(i).get("flags").subSequence(0, 1)
								.equals("1")) {
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
				if ((mAllCards.get(i).get("question").toLowerCase().indexOf(
						mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards
						.get(i).get("answer").toLowerCase().indexOf(
								mSearchEditText.getText().toString()
										.toLowerCase()) != -1)
						&& mAllCards.get(i).get("flags").subSequence(1, 2)
								.equals("1")) {
					mCards.add(mAllCards.get(i));
				}
			}
			updateList();
			return true;
		case MENU_SELECT_TAG:
			recreateTagsDialog();
			mTagsDialog.show();
			return true;
		case MENU_CHANGE_ORDER:
			showDialog(DIALOG_ORDER);
			return true;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == EDIT_CARD && resultCode == RESULT_OK) {
			// Log.i(AnkiDroidApp.TAG, "Saving card...");
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT,
					mUpdateCardHandler, new DeckTask.TaskData(0, mDeck,
							mSelectedCard));
		} else if (requestCode == ADD_FACT && resultCode == RESULT_OK) {
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
			builder.setTitle(res
					.getString(R.string.card_browser_change_display_order_title));
			builder.setIcon(android.R.drawable.ic_menu_sort_by_size);
	        builder.setSingleChoiceItems(getResources().getStringArray(R.array.card_browser_order_labels), mSelectedOrder, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int which) {
					if (which != mSelectedOrder) {
						mSelectedOrder = which;
						DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SORT_CARDS, mSortCardsHandler, new DeckTask.TaskData(mAllCards, new HashMapCompare()));
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
		case DIALOG_RELOAD_CARDS:
			builder.setTitle(res.getString(R.string.pref_cache_cardbrowser));
			builder.setMessage(res.getString(R.string.pref_cache_cardbrowser_reload));
			builder.setPositiveButton(res.getString(R.string.yes), new OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS,
							mLoadCardsHandler,
							new DeckTask.TaskData(mDeck, LOAD_CHUNK));	
					}
				
			});
			builder.setNegativeButton(res.getString(R.string.no), new OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					mAllCards.addAll(sAllCardsCache);
					mCards.addAll(mAllCards);
					updateList();
				}
				
			});
			builder.setCancelable(true);
			builder.setOnCancelListener(new OnCancelListener() {

				@Override
				public void onCancel(DialogInterface arg0) {
					mAllCards.addAll(sAllCardsCache);
					mCards.addAll(mAllCards);
					updateList();
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
		StyledDialog ad = (StyledDialog)dialog;
		switch (id) {
		case DIALOG_CONTEXT_MENU:
			mSelectedCard = mDeck.cardFromId(Long.parseLong(mCards.get(mPositionInCardsList).get("id")));
			if (mSelectedCard == null) {
				deleteCard(mCards.get(mPositionInCardsList).get("id"), mPositionInCardsList);
				ad.setEnabled(false);
				return;
			}
			if (mSelectedCard.isMarked()) {
				ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_unmark_card));
				mIsMarked = true;
				// Log.i(AnkiDroidApp.TAG, "Selected Card is currently marked");
			} else {
				ad.changeListItem(CONTEXT_MENU_MARK, res.getString(R.string.card_browser_mark_card));
				mIsMarked = false;
			}
			if (mSelectedCard.getSuspendedState()) {
				ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_unsuspend_card));
				mIsSuspended = true;
				// Log.i(AnkiDroidApp.TAG, "Selected Card is currently suspended");
			} else {
				ad.changeListItem(CONTEXT_MENU_SUSPEND, res.getString(R.string.card_browser_suspend_card));
				mIsSuspended = false;
			}
			ad.setTitle(mCards.get(mPositionInCardsList).get("question"));
			break;
		}		
	}


	private void recreateTagsDialog() {
		Resources res = getResources();
		if (allTags == null) {
			String[] oldTags = DeckManager.getMainDeck().allTags_();
			// Log.i(AnkiDroidApp.TAG, "all tags: " + Arrays.toString(oldTags));
			allTags = new String[oldTags.length];
			for (int i = 0; i < oldTags.length; i++) {
				allTags[i] = oldTags[i];
			}
		}
		mSelectedTags.clear();

		StyledDialog.Builder builder = new StyledDialog.Builder(this);
		builder.setTitle(R.string.studyoptions_limit_select_tags);
		builder.setMultiChoiceItems(allTags, new boolean[0],
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String tag = allTags[which];
						if (mSelectedTags.contains(tag)) {
							// Log.i(AnkiDroidApp.TAG, "unchecked tag: " + tag);
							mSelectedTags.remove(tag);
						} else {
							// Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
							mSelectedTags.add(tag);
						}
					}
				});
		builder.setPositiveButton(res.getString(R.string.select),
				new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						updateCardsList();
					}
				});
		builder.setNegativeButton(res.getString(R.string.cancel),
				new OnClickListener() {
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
		mTagsDialog = builder.create();
	}

	private void updateCardsList() {
		mShowOnlyMarSus = false;
		if (mSelectedTags.size() == 0) {
			mSearchEditText.setHint(R.string.downloaddeck_search);
		} else {
			String tags = mSelectedTags.toString();
			mSearchEditText.setHint(getResources().getString(
					R.string.card_browser_tags_shown,
					tags.substring(1, tags.length() - 1)));
		}
		mCards.clear();
		if (mSearchEditText.getText().length() == 0
				&& mSelectedTags.size() == 0 && mSelectedTags.size() == 0) {
			mCards.addAll(mAllCards);
		} else {
			for (int i = 0; i < mAllCards.size(); i++) {
				if ((mAllCards.get(i).get("question").toLowerCase().indexOf(
						mSearchEditText.getText().toString().toLowerCase()) != -1 || mAllCards
						.get(i).get("answer").toLowerCase().indexOf(
								mSearchEditText.getText().toString()
										.toLowerCase()) != -1)
						&& Arrays.asList(
								Utils.parseTags(mAllCards.get(i).get("tags")))
								.containsAll(mSelectedTags)) {
					mCards.add(mAllCards.get(i));
				}
			}
		}
		updateList();
	}

	private void getCards() {
		if ((sCachedDeckPath != null && !sCachedDeckPath.equals(mDeck.getDeckPath())) || !mPrefCacheCardBrowser) {
			sCachedDeckPath = null;
			sAllCardsCache = null;
		}
		if (mPrefCacheCardBrowser && sAllCardsCache != null && !sAllCardsCache.isEmpty()) {
			showDialog(DIALOG_RELOAD_CARDS);
		} else {
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_CARDS,
					mLoadCardsHandler,
					new DeckTask.TaskData(mDeck, LOAD_CHUNK));
		}
	}

	public static Card getEditorCard() {
		return sEditorCard;
	}

	private void updateList() {
		mCardsAdapter.notifyDataSetChanged();
		int count = mCards.size();
		setTitle(getResources().getQuantityString(R.plurals.card_browser_title,
				count, mDeck.getDeckName(), count, mAllCards.size()));
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

	private void updateCard(Card card, ArrayList<HashMap<String, String>> list,
			int position) {
		list.get(position).put("question", Utils.stripHTML(card.getQuestion().replaceAll("<br(\\s*\\/*)>","\n")));
		list.get(position).put("answer", Utils.stripHTML(card.getAnswer().replaceAll("<br(\\s*\\/*)>","\n")));
		for (long cardId : mDeck.getCardsFromFactId(card.getFactId())) {
			if (cardId != card.getId()) {
				int positionC = getPosition(mCards, cardId);
				int positionA = getPosition(mAllCards, cardId);
				Card c = mDeck.cardFromId(cardId);
				String question = Utils.stripHTML(c.getQuestion().replaceAll("<br(\\s*\\/*)>","\n"));
				String answer = Utils.stripHTML(c.getAnswer().replaceAll("<br(\\s*\\/*)>","\n"));
				if (positionC != -1) {
					mCards.get(positionC).put("question", question);
					mCards.get(positionC).put("answer", answer);
				}
				mAllCards.get(positionA).put("question", question);
				mAllCards.get(positionA).put("answer", answer);
			}
		}
	}

	private void markCards(long factId, boolean mark) {
		for (long cardId : mDeck.getCardsFromFactId(factId)) {
			int positionC = getPosition(mCards, cardId);
			int positionA = getPosition(mAllCards, cardId);
			String marSus = mAllCards.get(positionA).get("flags");
			if (mark) {
				marSus = "1" + marSus.substring(1, 2);
				if (positionC != -1) {
					mCards.get(positionC).put("flags", marSus);
				}
				mAllCards.get(positionA).put("flags", marSus);
			} else {
				marSus = "0" + marSus.substring(1, 2);
				if (positionC != -1) {
					mCards.get(positionC).put("flags", marSus);
				}
				mAllCards.get(positionA).put("flags", marSus);
			}
		}
		updateList();
	}

	private void suspendCard(Card card, int position, boolean suspend) {
		int posA = getPosition(mAllCards, card.getId());
		String marSus = mAllCards.get(posA).remove("flags");
		if (suspend) {
			marSus = marSus.substring(0, 1) + "1";
			if (position != -1) {
				mCards.get(position).put("flags", marSus);
			}
			mAllCards.get(posA).put("flags", marSus);
		} else {
			marSus = marSus.substring(0, 1) + "0";
			if (position != -1) {
				mCards.get(position).put("flags", marSus);
			}
			mAllCards.get(posA).put("flags", marSus);
		}
		updateList();
	}

	private void deleteCard(String cardId, int position) {
		if (mDeletedCards == null) {
			mDeletedCards = new ArrayList<HashMap<String, String>>();
		}
		HashMap<String, String> data = new HashMap<String, String>();
		for (int i = 0; i < mAllCards.size(); i++) {
			if (mAllCards.get(i).get("id").equals(cardId)) {
				data.put("id", mAllCards.get(i).get("id"));
				data.put("question", mAllCards.get(i).get("question"));
				data.put("answer", mAllCards.get(i).get("answer"));
				data.put("flags", mAllCards.get(i).get("flags"));
				data.put("allCardPos", Integer.toString(i));
				mDeletedCards.add(data);
				mAllCards.remove(i);
				// Log.i(AnkiDroidApp.TAG, "Remove card from list");
				break;
			}
		}
		mCards.remove(position);
		updateList();
	}

	private DeckTask.TaskListener mLoadCardsHandler = new DeckTask.TaskListener() {

		@Override
		public void onPreExecute() {
			if (!mUndoRedoDialogShowing) {
				mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
						getResources().getString(R.string.card_browser_load),
						true, true, new OnCancelListener() {

							@Override
							public void onCancel(DialogInterface arg0) {
								DeckTask.cancelTask();
							}
					
				});
			} else {
				mProgressDialog.setMessage(getResources().getString(
						R.string.card_browser_load));
				mUndoRedoDialogShowing = false;
			}
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			// This verification would not be necessary if
			// onConfigurationChanged it's executed correctly (which seems
			// that emulator does not do)
//			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SORT_CARDS, mSortCardsHandler, new DeckTask.TaskData(mAllCards, new HashMapCompare()));
			if (mProgressDialog.isShowing()) {
				try {
					mProgressDialog.dismiss();
				} catch (Exception e) {
					Log.e(AnkiDroidApp.TAG,
							"onPostExecute - Dialog dismiss Exception = "
									+ e.getMessage());
				}
			}
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			ArrayList<HashMap<String, String>> cards = values[0].getCards();
			if (cards == null) {
				Resources res = getResources();
				StyledDialog.Builder builder = new StyledDialog.Builder(
						CardBrowser.this);
				builder.setTitle(res.getString(R.string.error));
				builder.setIcon(android.R.drawable.ic_dialog_alert);
				builder.setMessage(res
						.getString(R.string.card_browser_cardloading_error));
				builder.setPositiveButton(res.getString(R.string.ok),
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								CardBrowser.this.finish();
							}
						});
				builder.setOnCancelListener(new OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						CardBrowser.this.finish();
					}
				});
				builder.create().show();
			} else {
				if (mPrefFixArabic) {
					for (HashMap<String, String> entry : cards) {
						entry.put("question", ArabicUtilities.reshapeSentence(entry.get("question")));
						entry.put("answer", ArabicUtilities.reshapeSentence(entry.get("answer")));
					}
				}
				try {
					mAllCards.addAll(cards);
					mCards.addAll(cards);					
				} catch (OutOfMemoryError e) {
			    	Log.e(AnkiDroidApp.TAG, "CardBrowser: mLoadCardsHandler: OutOfMemoryError: " + e);
					Themes.showThemedToast(CardBrowser.this, getResources().getString(R.string.error_insufficient_memory), false);
			    	finish();
				}
				updateList();
			}
		}
	};

	private DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
					.getString(R.string.saving_changes), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			mSelectedCard = values[0].getCard();
			markCards(mSelectedCard.getFactId(), !mIsMarked);
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			mProgressDialog.dismiss();

		}
	};

	private DeckTask.TaskListener mSuspendCardHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
					.getString(R.string.saving_changes), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			suspendCard(mSelectedCard, mPositionInCardsList, !mIsSuspended);
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			mProgressDialog.dismiss();
		}
	};

	private DeckTask.TaskListener mDeleteCardHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
					.getString(R.string.saving_changes), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			deleteCard(result.getString(), mPositionInCardsList);
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
				mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
						.getString(R.string.card_browser_sorting_cards), true);				
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


	private DeckTask.TaskListener mUndoRedoHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "", res
					.getString(R.string.saving_changes), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			mUndoRedoCardId = result.getLong();
			String undoType = result.getString();
			if (undoType.equals(Deck.UNDO_TYPE_DELETE_CARD)) {
				int position = getPosition(mDeletedCards, mUndoRedoCardId);
				if (position != -1) {
					HashMap<String, String> data = new HashMap<String, String>();
					data.put("id", mDeletedCards.get(position).get("id"));
					data.put("question", mDeletedCards.get(position).get(
							"question"));
					data.put("answer", mDeletedCards.get(position)
							.get("answer"));
					data.put("flags", mDeletedCards.get(position)
							.get("flags"));
					mAllCards.add(Integer.parseInt(mDeletedCards.get(position)
							.get("allCardPos")), data);
					mDeletedCards.remove(position);
					updateCardsList();
				} else {
					deleteCard(Long.toString(mUndoRedoCardId), getPosition(
							mCards, mUndoRedoCardId));
				}
				mProgressDialog.dismiss();
			} else {
				mUndoRedoCard = mDeck.cardFromId(mUndoRedoCardId);
				if (undoType.equals(Deck.UNDO_TYPE_EDIT_CARD)) {
					mUndoRedoCard.fromDB(mUndoRedoCardId);
					int pos = getPosition(mAllCards, mUndoRedoCardId);
					updateCard(mUndoRedoCard, mAllCards, pos);
					pos = getPosition(mCards, mUndoRedoCardId);
					if (pos != -1) {
						updateCard(mUndoRedoCard, mCards, pos);
					}
					updateList();
					mProgressDialog.dismiss();
				} else if (undoType.equals(Deck.UNDO_TYPE_MARK_CARD)) {
					markCards(mUndoRedoCard.getFactId(), mUndoRedoCard
							.isMarked());
					mProgressDialog.dismiss();
				} else if (undoType.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
					suspendCard(mUndoRedoCard, getPosition(mCards,
							mUndoRedoCardId), mUndoRedoCard.getSuspendedState());
					mProgressDialog.dismiss();
				} else {
					mUndoRedoDialogShowing = true;
					getCards();
				}
			}
		}
	};

	private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			mProgressDialog = StyledProgressDialog.show(CardBrowser.this, "",
					getResources().getString(R.string.saving_changes), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			mSelectedCard.fromDB(mSelectedCard.getId());
			int pos = getPosition(mAllCards, mSelectedCard.getId());
			updateCard(mSelectedCard, mAllCards, pos);
			pos = getPosition(mCards, mSelectedCard.getId());
			if (pos != -1) {
				updateCard(mSelectedCard, mCards, pos);
			}
			updateList();
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
			mProgressDialog.dismiss();
		}
	};

	public class SizeControlledListAdapter extends SimpleAdapter {

		private int fontSizeScalePcent;
		private float originalTextSize = -1.0f;

		public SizeControlledListAdapter(Context context,
				List<? extends Map<String, ?>> data, int resource,
				String[] from, int[] to, int fontSizeScalePcent) {
			super(context, data, resource, from, to);
			this.fontSizeScalePcent = fontSizeScalePcent;

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
							((TextView) child).setTextSize(
									TypedValue.COMPLEX_UNIT_SP,
									originalTextSize
											* (fontSizeScalePcent / 100.0f));
						}
					}

				}

			}

			return view;
		}
	}


	private class HashMapCompare implements
	Comparator<HashMap<String, String>> {
		@Override
		public int compare(HashMap<String, String> object1,
				HashMap<String, String> object2) {
		    try {
		    	int result;
		    	switch (mSelectedOrder) {
		    	case CARD_ORDER_ANSWER:
		    		result = object1.get("answer").compareToIgnoreCase(object2.get("answer"));
		    		if (result == 0) {
		    			result = object1.get("question").compareToIgnoreCase(object2.get("question"));
		    		}
		    		return result;
		    	case CARD_ORDER_QUESTION:
		    		result = object1.get("question").compareToIgnoreCase(object2.get("question"));
		    		if (result == 0) {
		    			result = object1.get("answer").compareToIgnoreCase(object2.get("answer"));
		    		}
		    		return result;
		    	case CARD_ORDER_DUE:
		    		result = Double.valueOf(object1.get("due")).compareTo(Double.valueOf(object2.get("due")));
		    		if (result == 0) {
		    			Long.valueOf(object1.get("id")).compareTo(Long.valueOf(object2.get("id")));
		    		}
		    		return result;
		    	case CARD_ORDER_INTERVAL:
		    		result = Double.valueOf(object1.get("interval")).compareTo(Double.valueOf(object2.get("interval")));
		    		if (result == 0) {
		    			Long.valueOf(object1.get("id")).compareTo(Long.valueOf(object2.get("id")));
		    		}
		    		return result;
		    	case CARD_ORDER_FACTOR:
		    		result = Double.valueOf(object1.get("factor")).compareTo(Double.valueOf(object2.get("factor")));
		    		if (result == 0) {
		    			Long.valueOf(object1.get("id")).compareTo(Long.valueOf(object2.get("id")));
		    		}
		    		return result;
		    	case CARD_ORDER_CREATED:
		    		result = Double.valueOf(object1.get("created")).compareTo(Double.valueOf(object2.get("created")));
		    		if (result == 0) {
		    			Long.valueOf(object1.get("id")).compareTo(Long.valueOf(object2.get("id")));
		    		}
		    		return result;
		    	}
		    	return 0;
		    }
		    catch (Exception e) {
		    	Log.e(AnkiDroidApp.TAG, "Error on sorting cards: " + e);
		        return 0;
		    }
		}
	}

}
