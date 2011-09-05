/***************************************************************************************
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
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.method.KeyListener;
import android.text.style.StrikethroughSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anim.ActivityTransitionAnimation;
import com.ichi2.anim.ViewAnimation;
import com.ichi2.anki.DeckPicker.AnkiFilter;
import com.ichi2.anki.Fact.Field;
import com.ichi2.themes.StyledDialog;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.amr.arabic.ArabicUtilities;

/**
 * Allows the user to edit a fact, for instance if there is a typo.
 * 
 * A card is a presentation of a fact, and has two sides: a question and an
 * answer. Any number of fields can appear on each side. When you add a fact to
 * Anki, cards which show that fact are generated. Some models generate one
 * card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class CardEditor extends Activity {

	public static final String CARD_EDITOR_ACTION = "cea";
	public static final int EDIT_REVIEWER_CARD = 0;
	public static final int EDIT_BROWSER_CARD = 1;
	public static final int ADD_CARD = 2;

	private static final int DIALOG_MODEL_SELECT = 0;
	private static final int DIALOG_CARD_MODEL_SELECT = 1;
	private static final int DIALOG_TAGS = 2;
	private static final int DIALOG_DECK_SELECT = 3;

	private static final String INTENT_CREATE_FLASHCARD = "org.openintents.indiclash.CREATE_FLASHCARD";
	private static final String INTENT_CREATE_FLASHCARD_SEND = "android.intent.action.SEND";

	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;

	private LinearLayout mFieldsLayoutContainer;
	private HashMap<Long, Model> mModels;

	private Button mSave;
	private Button mCancel;
	private Button mTags;
	private LinearLayout mModelButtons;
	private Button mModelButton;
	private Button mSwapButton;
	private Button mCardModelButton;

	private StyledDialog mCardModelDialog;
	private StyledDialog mDeckSelectDialog;

	private Fact mEditorFact;
	private boolean mAddFact = false;

	private Deck mDeck;
	private Long mCurrentSelectedModelId;

	private LinkedList<FieldEditText> mEditFields;
	private LinkedHashMap<Long, CardModel> mCardModels;

	private LinkedHashMap<Long, CardModel> mSelectedCardModels;
	private LinkedHashMap<Long, CardModel> mNewSelectedCardModels;
	private ArrayList<Long> cardModelIds = new ArrayList<Long>();

	private boolean mModified;

	private String[] allTags;
	private HashSet<String> mSelectedTags;
	private String mFactTags = "";
	private EditText mNewTagEditText;
	private ImageView mAddTextButton;
	private StyledDialog mTagsDialog;

	private ProgressDialog mProgressDialog;

	private HashMap<String, String> mFullDeckPaths;
	private String[] mDeckNames;
	private String mSourceLanguage;
	private String mTargetLanguage;
	private String mSourceText;
	private String mTargetText;
	private int mSourcePosition = 0;
	private int mTargetPosition = 1;
	private boolean mCancelled = false;

	private boolean mPrefFixArabic;

	private int mFilledFields = 0;

	private DeckTask.TaskListener mSaveFactHandler = new DeckTask.TaskListener() {
		@Override
		public void onPreExecute() {
			Resources res = getResources();
			mProgressDialog = ProgressDialog.show(CardEditor.this, "", res
					.getString(R.string.saving_facts), true);
		}

		@Override
		public void onProgressUpdate(DeckTask.TaskData... values) {
			if (values[0].getBoolean()) {
				setResult(RESULT_OK);
				mEditorFact = mDeck.newFact(mCurrentSelectedModelId);
				populateEditFields();
				mSave.setEnabled(false);
				mSourceText = null;
				mTargetText = null;
				mSwapButton.setVisibility(View.GONE);
			} else {
				Themes.showThemedToast(CardEditor.this, getResources()
						.getString(R.string.factadder_saving_error), true);
			}
			if (mProgressDialog != null && mProgressDialog.isShowing()) {
				mProgressDialog.dismiss();
			}
		}

		@Override
		public void onPostExecute(DeckTask.TaskData result) {
		}
	};

	// ----------------------------------------------------------------------------
	// ANDROID METHODS
	// ----------------------------------------------------------------------------

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Themes.applyTheme(this);
		super.onCreate(savedInstanceState);

		registerExternalStorageListener();

		View mainView = getLayoutInflater().inflate(R.layout.card_editor, null);
		setContentView(mainView);
		Themes.setWallpaper(mainView);

		mFieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);
		Themes.setTextViewStyle(mFieldsLayoutContainer);

		setTitle(R.string.cardeditor_title);
		mSave = (Button) findViewById(R.id.CardEditorSaveButton);
		mCancel = (Button) findViewById(R.id.CardEditorCancelButton);
		mSwapButton = (Button) findViewById(R.id.CardEditorSwapButton);
		mModelButtons = (LinearLayout) findViewById(R.id.CardEditorSelectModelLayout);
		mModelButton = (Button) findViewById(R.id.CardEditorModelButton);
		mCardModelButton = (Button) findViewById(R.id.CardEditorCardModelButton);
		mTags = (Button) findViewById(R.id.CardEditorTagButton);
		mTags.setText(getResources().getString(R.string.CardEditorTags, mFactTags));

		mNewSelectedCardModels = new LinkedHashMap<Long, CardModel>();
		cardModelIds = new ArrayList<Long>();

		Intent intent = getIntent();
		String action = intent.getAction();
		if (action != null && action.equals(INTENT_CREATE_FLASHCARD)) {
			prepareForIntentAddition();
			Bundle extras = intent.getExtras();
			mSourceLanguage = extras.getString("SOURCE_LANGUAGE");
			mTargetLanguage = extras.getString("TARGET_LANGUAGE");
			mSourceText = extras.getString("SOURCE_TEXT");
			mTargetText = extras.getString("TARGET_TEXT");
			mAddFact = true;
		} else if (action != null
				&& action.equals(INTENT_CREATE_FLASHCARD_SEND)) {
			prepareForIntentAddition();
			Bundle extras = intent.getExtras();
			mSourceText = extras.getString(Intent.EXTRA_SUBJECT);
			mTargetText = extras.getString(Intent.EXTRA_TEXT);
			mAddFact = true;
		} else {
			switch (intent.getIntExtra(CARD_EDITOR_ACTION, ADD_CARD)) {
			case EDIT_REVIEWER_CARD:
				mEditorFact = Reviewer.getEditorCard().getFact();
				break;
			case EDIT_BROWSER_CARD:
				mEditorFact = CardBrowser.getEditorCard().getFact();
				break;
			case ADD_CARD:
				mAddFact = true;
				mDeck = AnkiDroidApp.deck();
				loadContents();
				modelChanged();
				mSave.setEnabled(false);
				break;
			}
		}
		if (mAddFact) {
			mModelButtons.setVisibility(View.VISIBLE);
			mModelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					showDialog(DIALOG_MODEL_SELECT);
				}
			});
			mCardModelButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					showDialog(DIALOG_CARD_MODEL_SELECT);
				}
			});
			mSave.setText(getResources().getString(R.string.add));
			mCancel.setText(getResources().getString(R.string.close));
		} else {
			mFactTags = mEditorFact.getTags();
		}

		mModified = false;

		SharedPreferences preferences = PrefSettings
				.getSharedPrefs(getBaseContext());
		mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
		// if Arabic reshaping is enabled, disable the Save button to avoid
		// saving the reshaped string to the deck
		if (mPrefFixArabic && !mAddFact) {
			mSave.setEnabled(false);
		}

		mTags.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				showDialog(DIALOG_TAGS);
			}

		});
		allTags = null;
		mSelectedTags = new HashSet<String>();

		mSave.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (mAddFact) {
					boolean empty = true;
					for (FieldEditText current : mEditFields) {
						current.updateField();
						if (current.getText().length() != 0) {
							empty = false;
						}
					}
					if (!empty) {
						mEditorFact.setTags(mFactTags);
						DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ADD_FACT,
								mSaveFactHandler, new DeckTask.TaskData(mDeck,
										mEditorFact, mSelectedCardModels));
						setResult(RESULT_OK);
					} else {
						setResult(RESULT_CANCELED);
					}
				} else {
					Iterator<FieldEditText> iter = mEditFields.iterator();
					while (iter.hasNext()) {
						FieldEditText current = iter.next();
						mModified |= current.updateField();
					}
					if (!mEditorFact.getTags().equals(mFactTags)) {
						mEditorFact.setTags(mFactTags);
						mModified = true;
					}
					// Only send result to save if something was actually
					// changed
					if (mModified) {
						setResult(RESULT_OK);
					} else {
						setResult(RESULT_CANCELED);
					}
					closeCardEditor();
				}
			}

		});

		mCancel.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				closeCardEditor();
			}

		});

		if (!mAddFact) {
			populateEditFields();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			Log.i(AnkiDroidApp.TAG, "CardEditor - onBackPressed()");
			closeCardEditor();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mUnmountReceiver != null) {
			unregisterReceiver(mUnmountReceiver);
		}
	}

	// ----------------------------------------------------------------------------
	// CUSTOM METHODS
	// ----------------------------------------------------------------------------

	/**
	 * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
	 */
	public void registerExternalStorageListener() {
		if (mUnmountReceiver == null) {
			mUnmountReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					String action = intent.getAction();
					if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
						finishNoStorageAvailable();
					}
				}
			};
			IntentFilter iFilter = new IntentFilter();
			iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
			iFilter.addDataScheme("file");
			registerReceiver(mUnmountReceiver, iFilter);
		}
	}

	private void loadContents() {
		mModels = Model.getModels(mDeck);
		mCurrentSelectedModelId = mDeck.getCurrentModelId();
		modelChanged();
	}

	private void prepareForIntentAddition() {
		mSwapButton.setVisibility(View.VISIBLE);
		mSwapButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				swapText(false);
			}
		});
		showDialog(DIALOG_DECK_SELECT);
	}

	private void finishNoStorageAvailable() {
		setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
	}

	private void closeCardEditor() {
		finish();
		if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
			ActivityTransitionAnimation.slide(CardEditor.this,
					ActivityTransitionAnimation.RIGHT);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		StyledDialog dialog = null;
		Resources res = getResources();
		StyledDialog.Builder builder = new StyledDialog.Builder(this);

		switch (id) {
		case DIALOG_TAGS:
			builder.setTitle(R.string.studyoptions_limit_select_tags);
			builder.setPositiveButton(res.getString(R.string.select),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							String tags = mSelectedTags.toString();
							mFactTags = tags.substring(1, tags.length() - 1);
							mTags.setText(getResources().getString(
									R.string.CardEditorTags, mFactTags));
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel), null);

			mNewTagEditText = (EditText) new EditText(this);
			mNewTagEditText.setHint(R.string.add_new_tag);

			InputFilter filter = new InputFilter() {
				public CharSequence filter(CharSequence source, int start,
						int end, Spanned dest, int dstart, int dend) {
					for (int i = start; i < end; i++) {
						if (!(Character.isLetterOrDigit(source.charAt(i)))) {
							return "";
						}
					}
					return null;
				}
			};
			mNewTagEditText.setFilters(new InputFilter[] { filter });

			ImageView mAddTextButton = new ImageView(this);
			mAddTextButton.setImageResource(R.drawable.ic_addtag);
			mAddTextButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					String tag = mNewTagEditText.getText().toString();
					if (tag.length() != 0) {
						for (int i = 0; i < allTags.length; i++) {
							if (allTags[i].equalsIgnoreCase(tag)) {
								mNewTagEditText.setText("");
								return;
							}
						}
						mSelectedTags.add(tag);
						String[] oldTags = allTags;
						allTags = new String[oldTags.length + 1];
						allTags[0] = tag;
						for (int j = 1; j < allTags.length; j++) {
							allTags[j] = oldTags[j - 1];
						}
						mTagsDialog.addMultiChoiceItems(tag, true);
						mNewTagEditText.setText("");
					}
				}
			});

			FrameLayout frame = new FrameLayout(this);
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT
							| Gravity.CENTER_VERTICAL);
			params.rightMargin = 10;
			mAddTextButton.setLayoutParams(params);
			frame.addView(mNewTagEditText);
			frame.addView(mAddTextButton);

			builder.setView(frame, true);
			dialog = builder.create();
			mTagsDialog = dialog;
			break;

		case DIALOG_DECK_SELECT:
			int len = 0;
			File[] fileList;

			File dir = new File(PrefSettings.getSharedPrefs(getBaseContext())
					.getString("deckPath", AnkiDroidApp.getStorageDirectory()));
			fileList = dir.listFiles(new AnkiFilter());

			if (dir.exists() && dir.isDirectory() && fileList != null) {
				len = fileList.length;
			}

			TreeSet<String> tree = new TreeSet<String>();
			mFullDeckPaths = new HashMap<String, String>();

			if (len > 0 && fileList != null) {
				Log.i(AnkiDroidApp.TAG,
						"CardEditor - populateDeckDialog, number of anki files = "
								+ len);
				for (File file : fileList) {
					String name = file.getName().replaceAll(".anki", "");
					tree.add(name);
					mFullDeckPaths.put(name, file.getAbsolutePath());
				}
			}

			builder.setTitle(R.string.fact_adder_select_deck);
			// Convert to Array
			mDeckNames = new String[tree.size()];
			tree.toArray(mDeckNames);

			builder.setItems(mDeckNames, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int item) {
					loadDeck(item);
				}
			});
			builder.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface arg0) {
					mCancelled = true;
				}

			});
			builder.setOnDismissListener(new OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface arg0) {
					if (mCancelled == true) {
						finish();
					} else if (mDeck == null) {
						showDialog(DIALOG_DECK_SELECT);
					}
				}
			});
			dialog = builder.create();
			break;
		case DIALOG_MODEL_SELECT:
			ArrayList<CharSequence> dialogItems = new ArrayList<CharSequence>();
			// Use this array to know which ID is associated with each
			// Item(name)
			final ArrayList<Long> dialogIds = new ArrayList<Long>();

			Model mModel;
			builder.setTitle(R.string.select_model);
			for (Long i : mModels.keySet()) {
				mModel = mModels.get(i);
				dialogItems.add(mModel.getName());
				dialogIds.add(i);
			}
			// Convert to Array
			String[] items = new String[dialogItems.size()];
			dialogItems.toArray(items);

			builder.setItems(items, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					long oldModelId = mCurrentSelectedModelId;
					mCurrentSelectedModelId = dialogIds.get(item);
					if (oldModelId != mCurrentSelectedModelId) {
						int size = mEditFields.size();
						String[] oldValues = new String[size];
						for (int i = 0; i < size; i++) {
							oldValues[i] = mEditFields.get(i).getText()
									.toString();
						}
						modelChanged();
						if ((mSourceText == null || mSourceText.isEmpty())
								&& (mTargetText == null || mTargetText
										.isEmpty())) {
							for (int i = 0; i < Math.min(size, mEditFields
									.size()); i++) {
								mEditFields.get(i).setText(oldValues[i]);
							}
						}
					}
				}
			});
			dialog = builder.create();
			break;
		case DIALOG_CARD_MODEL_SELECT:
			builder.setTitle(res.getString(R.string.select_card_model));
			builder.setPositiveButton(res.getString(R.string.select),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							mSelectedCardModels.clear();
							mSelectedCardModels.putAll(mNewSelectedCardModels);
							cardModelsChanged();
						}
					});
			builder.setNegativeButton(res.getString(R.string.cancel),
					new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					});
			mCardModelDialog = builder.create();
			dialog = mCardModelDialog;
			break;
		}
		return dialog;
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		StyledDialog ad = (StyledDialog) dialog;
		switch (id) {
		case DIALOG_TAGS:
			if (allTags == null) {
				String[] oldTags = AnkiDroidApp.deck().allUserTags();
				Log.i(AnkiDroidApp.TAG, "all tags: "
								+ Arrays.toString(oldTags));
				allTags = new String[oldTags.length];
				for (int i = 0; i < oldTags.length; i++) {
					allTags[i] = oldTags[i];
				}
			}
			mSelectedTags.clear();
			List<String> selectedList = Arrays.asList(Utils
					.parseTags(mFactTags));
			int length = allTags.length;
			boolean[] checked = new boolean[length];
			for (int i = 0; i < length; i++) {
				String tag = allTags[i];
				if (selectedList.contains(tag)) {
					checked[i] = true;
					mSelectedTags.add(tag);
				}
			}
			ad.setMultiChoiceItems(allTags, checked,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int which) {
							String tag = allTags[which];
							if (mSelectedTags.contains(tag)) {
								Log
										.i(AnkiDroidApp.TAG, "unchecked tag: "
												+ tag);
								mSelectedTags.remove(tag);
							} else {
								Log.i(AnkiDroidApp.TAG, "checked tag: " + tag);
								mSelectedTags.add(tag);
							}
						}
					});
			break;
		case DIALOG_CARD_MODEL_SELECT:
			mCardModels = mDeck.cardModels(mEditorFact);
			int size = mCardModels.size();
			String dialogItems[] = new String[size];
			cardModelIds.clear();
			int i = 0;
			for (Long id2 : mCardModels.keySet()) {
				dialogItems[i] = mCardModels.get(id2).getName();
				cardModelIds.add(id2);
				i++;
			}
			boolean[] checkedItems = new boolean[size];
			for (int j = 0; j < size; j++) {
				;
				checkedItems[j] = mSelectedCardModels.containsKey(cardModelIds
						.get(j));
			}
			mNewSelectedCardModels.clear();
			mNewSelectedCardModels.putAll(mSelectedCardModels);
			ad.setMultiChoiceItems(dialogItems, checkedItems,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int which) {
							long m = cardModelIds.get(which);
							if (mNewSelectedCardModels.containsKey(m)) {
								mNewSelectedCardModels.remove(m);
							} else {
								mNewSelectedCardModels.put(m, mCardModels
										.get(m));
							}
							mCardModelDialog.getButton(
									StyledDialog.BUTTON_POSITIVE).setEnabled(
									!mNewSelectedCardModels.isEmpty());
						}
					});
			ad.getButton(StyledDialog.BUTTON_POSITIVE).setEnabled(
					!mNewSelectedCardModels.isEmpty());
			break;
		}
	}

	private void modelChanged() {
		mEditorFact = mDeck.newFact(mCurrentSelectedModelId);
		mSelectedCardModels = mDeck.activeCardModels(mEditorFact);

		mModelButton.setText(getResources().getString(R.string.model) + " "
				+ mModels.get(mCurrentSelectedModelId).getName());
		cardModelsChanged();
		populateEditFields();
		swapText(true);
	}

	private void loadDeck(int item) {
		mDeck = Deck.openDeck(mFullDeckPaths.get(mDeckNames[item]), false);
		if (mDeck == null) {
			Themes.showThemedToast(CardEditor.this, getResources().getString(
					R.string.fact_adder_deck_not_loaded), true);
		} else {
			setTitle(mDeckNames[item]);
			loadContents();
		}
	}

	private void swapText(boolean reset) {
		if (mEditFields.size() > mSourcePosition) {
			mEditFields.get(mSourcePosition).setText("");
		}
		if (mEditFields.size() > mTargetPosition) {
			mEditFields.get(mTargetPosition).setText("");
		}
		if (reset) {
			mSourcePosition = 0;
			mTargetPosition = 1;
		} else {
			mTargetPosition++;
			while (mTargetPosition == mSourcePosition
					|| mTargetPosition >= mEditFields.size()) {
				mTargetPosition++;
				if (mTargetPosition >= mEditFields.size()) {
					mTargetPosition = 0;
					mSourcePosition++;
				}
				if (mSourcePosition >= mEditFields.size()) {
					mSourcePosition = 0;
				}
			}
		}
		if (mSourceText != null) {
			mEditFields.get(mSourcePosition).setText(mSourceText);
		}
		if (mSourceText != null) {
			mEditFields.get(mTargetPosition).setText(mTargetText);
		}
	}

	private void cardModelsChanged() {
		String cardModelNames = "";
		for (Map.Entry<Long, CardModel> entry : mSelectedCardModels.entrySet()) {
			cardModelNames = cardModelNames + entry.getValue().getName() + ", ";
		}
		cardModelNames = cardModelNames.substring(0,
				cardModelNames.length() - 2);

		if (mSelectedCardModels.size() == 1) {
			mCardModelButton.setText(getResources().getString(R.string.card)
					+ " " + cardModelNames);
		} else {
			mCardModelButton.setText(getResources().getString(R.string.cards)
					+ " " + cardModelNames);
		}
	}

	private void populateEditFields() {
		mFieldsLayoutContainer.removeAllViews();
		mEditFields = new LinkedList<FieldEditText>();
		TreeSet<Field> fields = mEditorFact.getFields();
		for (Field f : fields) {
			FieldEditText newTextbox = new FieldEditText(this, f);
			TextView label = newTextbox.getLabel();
			ImageView circle = newTextbox.getCircle();
			mEditFields.add(newTextbox);
			FrameLayout frame = new FrameLayout(this);
			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
					ViewGroup.LayoutParams.WRAP_CONTENT,
					ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT
							| Gravity.CENTER_VERTICAL);
			params.rightMargin = 10;
			circle.setLayoutParams(params);
			frame.addView(newTextbox);
			frame.addView(circle);
			mFieldsLayoutContainer.addView(label);
			mFieldsLayoutContainer.addView(frame);
		}
	}

	// ----------------------------------------------------------------------------
	// INNER CLASSES
	// ----------------------------------------------------------------------------

	public class FieldEditText extends EditText {

	    public final String NEW_LINE = System.getProperty("line.separator");
	    public final String NL_MARK = "newLineMark";

		private Field mPairField;
		private WordRow mCutString[];
		private boolean mCutMode = false;
		private ImageView mCircle;
		private KeyListener mKeyListener;
		private Context mContext;

		public FieldEditText(Context context, Field pairField) {
			super(context);
			mContext = context;
			mPairField = pairField;
			if (mPrefFixArabic) {
				this.setText(ArabicUtilities.reshapeSentence(pairField
						.getValue().replaceAll("<br(\\s*\\/*)>", NEW_LINE)));
			} else {
				this.setText(pairField.getValue().replaceAll("<br(\\s*\\/*)>",
						NEW_LINE));
			}
			this.setMinimumWidth(400);
			this.setOnClickListener(new View.OnClickListener() {

				@Override
				public void onClick(View v) {
					if (mCutMode) {
						updateSpannables();
					}
				}
			});
		}

		@Override
		public void onTextChanged(CharSequence text, int start, int before,
				int after) {
			super.onTextChanged(text, start, before, after);
			if (mCircle != null) {
				int visibility = mCircle.getVisibility();
				if (text.length() == 0) {
					if (visibility == View.VISIBLE) {
						mFilledFields--;
						mCircle.setVisibility(View.GONE);
						mCircle.setAnimation(ViewAnimation.fade(
								ViewAnimation.FADE_OUT, 300, 0));
					}
				} else if (visibility == View.GONE) {
					mFilledFields++;
					mCircle.setVisibility(View.VISIBLE);
					mCircle.setAnimation(ViewAnimation.fade(
							ViewAnimation.FADE_IN, 300, 0));
				}
				mSave.setEnabled(mFilledFields != 0 && (!mPrefFixArabic || mAddFact));
			}
		}

		private void splitText(String text) {
			text = text.replace(NEW_LINE, " " + NL_MARK + " ");
			String[] cut = text.split("\\s");
			mCutString = new WordRow[cut.length];
			for (int i = 0; i < cut.length; i++) {
				mCutString[i] = new WordRow(cut[i]);
				if (mCutString[i].mWord.equals(NL_MARK)) {
					mCutString[i].mEnabled = true;
				}
			}
		}

		public TextView getLabel() {
			TextView label = new TextView(this.getContext());
			label.setText(mPairField.getFieldModel().getName());
			return label;
		}

		public ImageView getCircle() {
			mCircle = new ImageView(this.getContext());
			mCircle.setImageResource(R.drawable.ic_circle_normal);
			mCircle.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ImageView view = ((ImageView) v);
					Editable editText = FieldEditText.this.getText();
					if (mCutMode) {
						view.setImageResource(R.drawable.ic_circle_normal);
						FieldEditText.this.setKeyListener(mKeyListener);
						FieldEditText.this.setCursorVisible(true);
						for (WordRow row : mCutString) {
							if (row.mEnabled && !row.mWord.equals(NL_MARK)) {
								removeDeleted();
								break;
							}
						}
						StrikethroughSpan[] ss = editText.getSpans(0, editText
								.length(), StrikethroughSpan.class);
						for (StrikethroughSpan s : ss) {
							editText.removeSpan(s);
						}
						mCutMode = false;
					} else {
						view.setImageResource(R.drawable.ic_circle_pressed);
						InputMethodManager imm = (InputMethodManager) mContext
								.getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(FieldEditText.this
								.getWindowToken(), 0);
						mKeyListener = FieldEditText.this.getKeyListener();
						FieldEditText.this.setKeyListener(null);
						FieldEditText.this.setCursorVisible(false);
						mCutMode = true;
						String text = editText.toString();
						splitText(text);
						int pos = 0;
						for (WordRow row : mCutString) {
							if (row.mWord.length() == 0 || row.mWord.equals(NL_MARK)) {
								continue;
							}
							row.mBegin = text.indexOf(row.mWord, pos);
							row.mEnd = row.mBegin + row.mWord.length();
							if (!row.mEnabled) {
								editText.setSpan(new StrikethroughSpan(),
										row.mBegin, row.mEnd, 0);
							}
							pos = row.mEnd;
						}
					}
				}
			});
			if (this.getText().toString().length() > 0) {
				mFilledFields++;
				mCircle.setVisibility(View.VISIBLE);
			} else {
				mCircle.setVisibility(View.GONE);
			}
			return mCircle;
		}

		public boolean updateField() {
			String newValue = this.getText().toString().replace(NEW_LINE, "<br />");
			if (!mPairField.getValue().equals(newValue)) {
				mPairField.setValue(newValue);
				return true;
			}
			return false;
		}

		public void updateSpannables() {
			int cursorPosition = this.getSelectionStart();
			Editable editText = this.getText();
			for (WordRow row : mCutString) {
				if (row.mBegin <= cursorPosition && row.mEnd > cursorPosition) {
					if (!row.mEnabled) {
						StrikethroughSpan[] ss = this.getText().getSpans(
								cursorPosition, cursorPosition,
								StrikethroughSpan.class);
						if (ss.length != 0) {
							editText.removeSpan(ss[0]);
						}
						row.mEnabled = true;
					} else {
						editText.setSpan(new StrikethroughSpan(), row.mBegin,
								row.mEnd, 0);
						row.mEnabled = false;
						break;
					}
				}
			}
			this.setText(editText);
			this.setSelection(cursorPosition);
		}

		public String cleanText(String text) {
			text = text.replaceAll("\\s*(" + NL_MARK + "\\s*)+", NEW_LINE);
			text = text.replaceAll("^[,;:\\s\\)\\]" + NEW_LINE + "]*", "");
			text = text.replaceAll("[,;:\\s\\(\\[" + NEW_LINE + "]*$", "");
			return text;
		}

		public void removeDeleted() {
			if (this.getText().length() > 0) {
				StringBuilder sb = new StringBuilder();
				for (WordRow row : mCutString) {
					if (row.mEnabled) {
						sb.append(row.mWord);
						sb.append(" ");
					}
				}
				this.setText(cleanText(sb.toString()));
			}
		}

		private class WordRow {
			public String mWord;
			public int mBegin;
			public int mEnd;
			public boolean mEnabled = false;

			WordRow(String word) {
				mWord = word;
			}
		}
	}
}
