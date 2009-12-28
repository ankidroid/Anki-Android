package com.ichi2.anki;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;


public class StudyOptions extends Activity
{
	
	private Button mButtonStart;
	private Button mButtonMore;
	private TextView mTextReviewsDue;
	private TextView mTextNewToday;
	private TextView mTextNewTotal;
	private EditText mEditNewPerDay;
	private EditText mEditSessionTime;
	private EditText mEditSessionQuestions;

	private AlertDialog mDialogMoreOptions;
	private Spinner mSpinnerNewCardOrder;
	private Spinner mSpinnerNewCardSchedule;
	private Spinner mSpinnerRevCardOrder;
	private Spinner mSpinnerFailCardOption;
	
	private View.OnClickListener mButtonClickListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			switch (v.getId())
			{
			case R.id.studyoptions_start:
				finish();
				return;
			case R.id.studyoptions_more:
				showDialog();
				return;
			default:
				return;
			}
		}
	};
	
	private View.OnFocusChangeListener mEditFocusListener = new View.OnFocusChangeListener()
	{
		@Override
		public void onFocusChange(View v, boolean hasFocus)
		{
			Deck deck = AnkidroidApp.deck();
			if (!hasFocus)
				switch (v.getId())
				{
				case R.id.studyoptions_new_cards_per_day:
					deck.setNewCardsPerDay(Integer.parseInt(((EditText)v).getText().toString()));
					updateValuesFromDeck();
					return;
				case R.id.studyoptions_session_minutes:
					deck.setSessionTimeLimit(Long.parseLong(((EditText)v).getText().toString()) * 60);
					return;
				case R.id.studyoptions_session_questions:
					deck.setSessionRepLimit(Long.parseLong(((EditText)v).getText().toString()));
					return;
				default:
					return;
				}
		}
	};
	
	private DialogInterface.OnClickListener mDialogSaveListener = new DialogInterface.OnClickListener()
	{
		@Override
		public void onClick(DialogInterface dialog, int which)
		{
			Deck deck = AnkidroidApp.deck();
			deck.setNewCardOrder(mSpinnerNewCardOrder.getSelectedItemPosition());
			deck.setNewCardSpacing(mSpinnerNewCardSchedule.getSelectedItemPosition());
			deck.setRevCardOrder(mSpinnerRevCardOrder.getSelectedItemPosition());
			
			dialog.dismiss();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.studyoptions);
		mButtonStart = (Button) findViewById(R.id.studyoptions_start);
		mButtonMore = (Button) findViewById(R.id.studyoptions_more);
		
		mTextReviewsDue = (TextView) findViewById(R.id.studyoptions_reviews_due);
		mTextNewToday = (TextView) findViewById(R.id.studyoptions_new_today);
		mTextNewTotal = (TextView) findViewById(R.id.studyoptions_new_total);
		
		mEditNewPerDay = (EditText) findViewById(R.id.studyoptions_new_cards_per_day);
		mEditSessionTime = (EditText) findViewById(R.id.studyoptions_session_minutes);
		mEditSessionQuestions = (EditText) findViewById(R.id.studyoptions_session_questions);
		
		mButtonStart.setOnClickListener(mButtonClickListener);
		mButtonMore.setOnClickListener(mButtonClickListener);
		
		mEditNewPerDay.setOnFocusChangeListener(mEditFocusListener);
		
		mDialogMoreOptions = createDialog();
		
		updateValuesFromDeck();
	}
	
	private void updateValuesFromDeck()
	{
		Deck deck = AnkidroidApp.deck();
		String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
		setTitle(String.format(unformattedTitle, deck.deckName, deck.revCount, deck.cardCount));
		
		mTextReviewsDue.setText(String.valueOf(deck.revCount));
		mTextNewToday.setText(String.valueOf(deck.newCountToday));
		mTextNewTotal.setText(String.valueOf(deck.newCount));
		
		mEditNewPerDay.setText(String.valueOf(deck.getNewCardsPerDay()));
		mEditSessionTime.setText(String.valueOf(deck.getSessionTimeLimit()/60));
		mEditSessionQuestions.setText(String.valueOf(deck.getSessionRepLimit()));
	}
	
	private AlertDialog createDialog()
	{
		View contentView = getLayoutInflater().inflate(R.layout.studyoptions_more_dialog_contents, null);
		mSpinnerNewCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_order);
		mSpinnerNewCardSchedule = (Spinner) contentView.findViewById(R.id.studyoptions_new_card_schedule);
		mSpinnerRevCardOrder = (Spinner) contentView.findViewById(R.id.studyoptions_rev_card_order);
		mSpinnerFailCardOption = (Spinner) contentView.findViewById(R.id.studyoptions_fail_card_option);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.studyoptions_more_dialog_title);
		builder.setPositiveButton(R.string.studyoptions_more_save, mDialogSaveListener);
		builder.setView(contentView);
		
		return builder.create();
	}
	
	private void showDialog()
	{
		Deck deck = AnkidroidApp.deck();
		
		mSpinnerNewCardOrder.setSelection(deck.getNewCardOrder());
		mSpinnerNewCardSchedule.setSelection(deck.getNewCardSpacing());
		mSpinnerRevCardOrder.setSelection(deck.getRevCardOrder());
		mSpinnerFailCardOption.setVisibility(View.GONE);
		
		mDialogMoreOptions.show();
	}

}
