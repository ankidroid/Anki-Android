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

package com.ichi2.widget;


import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.BackupManager;
import com.ichi2.anki.Card;
import com.ichi2.anki.CardEditor;
import com.ichi2.anki.Deck;
import com.ichi2.anki.DeckManager;
import com.ichi2.anki.DeckStatus;
import com.ichi2.anki.DeckTask;
import com.ichi2.anki.R;
import com.ichi2.anki.Reviewer;
import com.ichi2.anki.StudyOptions;
import com.ichi2.anki.Utils;
import com.ichi2.anki.WidgetStatus;
import com.ichi2.themes.Themes;
import com.tomgibara.android.veecheck.util.PrefSettings;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.IBinder;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class AnkiDroidWidgetBig extends AppWidgetProvider {

    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;
    private static Deck sLoadedDeck;
    private static Card sCard;
    private static boolean mShowProgressDialog = false;
    private static int sCurrentView;
    private static String mCurrentMessage;

    private static Context sContext;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "BigWidget: onUpdate");
        WidgetStatus.update(context);
        sContext = context;
        Intent intent;
        intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);            	
        intent.setAction(UpdateService.ACTION_UPDATE);
        context.startService(intent);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget enabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        if (!preferences.getBoolean("widgetBigEnabled", false)) {
            // show info dialog
            Intent intent;
            intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
            intent.setAction(UpdateService.ACTION_SHOW_RESTRICTIONS_DIALOG);
            context.startService(intent);
        }
        preferences.edit().putBoolean("widgetBigEnabled", true).commit();
        sContext = context;
        sCurrentView = UpdateService.VIEW_NOT_SPECIFIED;
        sLoadedDeck = null;
        sCard = null;
        mCurrentMessage = null;
        mShowProgressDialog = false;
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget disabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetBigEnabled", false).commit();
    }


    public static void setDeck(Deck deck) {
    	sLoadedDeck = deck;
    	updateWidget(UpdateService.VIEW_NOT_SPECIFIED);
    }


    public static void setCard(Card card) {
    	if (sLoadedDeck != null) {
        	sCard = card;
    	}
    	updateWidget(UpdateService.VIEW_SHOW_QUESTION);
    }

    public static Card getCard() {
    	return sCard;
    }

    public static void updateWidget(int view) {
        Intent intent;
        if (sContext != null) {
            intent = new Intent(sContext, AnkiDroidWidgetBig.UpdateService.class);
            intent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_UPDATE + Integer.toString(view));
            sContext.startService(intent);        	
        }
    }


    public static class UpdateService extends Service {
    	public static final String ACTION_NOTHING = "org.ichi2.anki.AnkiDroidWidgetBig.NOTHING";
    	public static final String ACTION_OPENDECK = "org.ichi2.anki.AnkiDroidWidgetBig.OPENDECK";
        public static final String ACTION_CLOSEDECK = "org.ichi2.anki.AnkiDroidWidgetBig.CLOSEDECK";
        public static final String ACTION_ANSWER = "org.ichi2.anki.AnkiDroidWidgetBig.ANSWER";
        public static final String ACTION_OPEN = "org.ichi2.anki.AnkiDroidWidgetBig.OPEN";
        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidgetBig.UPDATE";
        public static final String ACTION_BURY_CARD = "org.ichi2.anki.AnkiDroidWidgetBig.BURYCARD";
        public static final String ACTION_UNDO = "org.ichi2.anki.AnkiDroidWidgetBig.UNDO";
        public static final String ACTION_CARDEDITOR = "org.ichi2.anki.AnkiDroidWidgetBig.CARDEDITOR";
        public static final String ACTION_HELP = "org.ichi2.anki.AnkiDroidWidgetBig.HELP";
        public static final String ACTION_SHOW_RESTRICTIONS_DIALOG = "org.ichi2.anki.AnkiDroidWidgetBig.SHOWRESTRICTIONSDIALOG";
        public static final String ACTION_LEARN_MORE = "org.ichi2.anki.AnkiDroidWidgetBig.LEARNMORE";
        public static final String ACTION_REVIEW_EARLY = "org.ichi2.anki.AnkiDroidWidgetBig.REVIEWEARLY";


        public static final String EXTRA_DECK_PATH = "deckPath";


        public static final int VIEW_ACTION_DEFAULT = 0;
        public static final int VIEW_ACTION_SHOW_PROGRESS_DIALOG = 1;
        public static final int VIEW_ACTION_HIDE_BUTTONS = 2;

        public static final int VIEW_NOT_SPECIFIED = 0;
        public static final int VIEW_DECKS = 1;
        public static final int VIEW_SHOW_QUESTION = 2;
        public static final int VIEW_SHOW_ANSWER = 3;
        public static final int VIEW_NOTHING_DUE = 4;
        public static final int VIEW_SHOW_HELP = 5;
        

        private CharSequence getDeckStatusString(Deck deck, Card card) {
        	return getDeckStatusString(deck.getFailedSoonCount(), deck.getRevCount(), deck.getNewCountToday(), card);
        }
    	private CharSequence getDeckStatusString(int failed, int rev, int newCount, Card card) {
            SpannableStringBuilder sb = new SpannableStringBuilder();

            SpannableString red = new SpannableString(Integer.toString(failed));
            red.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color_red)), 0, red.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (card != null && card.getType() == Card.TYPE_FAILED) {
            	red.setSpan(new UnderlineSpan(), 0, red.length(), 0);
            }

            SpannableString black = new SpannableString(Integer.toString(rev));
            black.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color)), 0, black.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (card != null && card.getType() == Card.TYPE_REV) {
            	black.setSpan(new UnderlineSpan(), 0, black.length(), 0);
            }

            SpannableString blue = new SpannableString(Integer.toString(newCount));
            blue.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color_blue)), 0, blue.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (card != null && card.getType() == Card.TYPE_NEW) {
            	blue.setSpan(new UnderlineSpan(), 0, blue.length(), 0);
            }

            sb.append(red);
            sb.append(" ");
            sb.append(black);
            sb.append(" ");
            sb.append(blue);

            return sb;
        }


    	private CharSequence getNextTimeString(Card card) {
            SpannableStringBuilder sb = new SpannableStringBuilder();

            SpannableString hard = new SpannableString(Utils.fmtTimeSpan(card.nextInterval(card, 2) * 86400, Utils.TIME_FORMAT_DEFAULT));
            hard.setSpan(new ForegroundColorSpan(getResources().getColor(card.isRev() ? R.color.widget_big_font_color : R.color.widget_big_font_color_green)), 0, hard.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString easy = new SpannableString(Utils.fmtTimeSpan(card.nextInterval(card, 3) * 86400, Utils.TIME_FORMAT_DEFAULT));
            easy.setSpan(new ForegroundColorSpan(getResources().getColor(card.isRev() ? R.color.widget_big_font_color_green : R.color.widget_big_font_color)), 0, easy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableString veryEasy = new SpannableString(Utils.fmtTimeSpan(card.nextInterval(card, 4) * 86400, Utils.TIME_FORMAT_DEFAULT));
            veryEasy.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color)), 0, veryEasy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            sb.append(hard);
            sb.append(" \u2027 ");
            sb.append(easy);
            sb.append(" \u2027 ");
            sb.append(veryEasy);

            return sb;
        }


    	private void updateCounts(RemoteViews updateViews) {
			DeckStatus[] decks = WidgetStatus.fetch(this);
        	int eta = 0;
        	int reps = 0;
        	int due = 0;
        	for (DeckStatus d : decks) {
        		eta += d.mEta;
        		reps += d.mTime;
        		due += d.mFailedCards + d.mNewCards + d.mDueCards;
        	}
        	int totalreps = reps + due;
        	int progressTotal = 0;
        	if (totalreps != 0) {
        		progressTotal = (int) Math.round((100.0d * reps) / totalreps);
        	}

    		switch (sCurrentView) {
    		case VIEW_SHOW_QUESTION:
    		case VIEW_NOTHING_DUE:
        		updateViews.setTextViewText(R.id.widget_big_counts, getDeckStatusString(sLoadedDeck, sCard));
			int sessionProgress = sLoadedDeck.getSessionProgress(true);
			if (sessionProgress == -1) {
	        		updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.INVISIBLE);
			} else {
	        		updateViews.setProgressBar(R.id.widget_big_progress_deck, 100, (int) (sLoadedDeck.getSessionProgress() * 100), false);
	        		updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.VISIBLE);
			}
        		break;
    		case VIEW_DECKS:
        		updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.INVISIBLE);
        		updateViews.setTextViewText(R.id.widget_big_counts, "");
            	StringBuilder namesSb = new StringBuilder();
            	SpannableStringBuilder duesSb = new SpannableStringBuilder();
            	for (DeckStatus d : decks) {
            		namesSb.append(d.mDeckName).append("  \n");
            		duesSb.append(getDeckStatusString(d.mFailedCards, d.mDueCards, d.mNewCards, null)).append("\n");
            	}
            	int pos = namesSb.length() - 1;
            	if (pos != -1) {
            		namesSb.delete(pos, pos + 1);
            	}
            	pos = duesSb.length() - 1;
            	if (pos != -1) {
            		duesSb.delete(pos, pos + 1);
            	}
        		updateViews.setTextViewText(R.id.widget_big_decknames, namesSb);
        		updateViews.setTextViewText(R.id.widget_big_deckdues, duesSb);
        		updateViews.setTextViewText(R.id.widget_big_decketa, "─────\n" + getResources().getQuantityString(R.plurals.widget_big_eta, eta, eta));
        		break;
    		}
    		
    		updateViews.setProgressBar(R.id.widget_big_progress_total, 100, progressTotal, false);
    		updateViews.setTextViewText(R.id.widget_big_eta, eta > 99 ? "\u2027\u2027\u2027" : Integer.toString(eta));
    	}


        private DeckTask.TaskListener mOpenDeckHandler = new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
            	showProgressDialog();
            }
            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            	Resources res = getResources();
            	String message = values[0].getString();
            	if (message == null) {
    				switch (values[0].getInt()) {
            		case BackupManager.RETURN_BACKUP_CREATED:
                		mCurrentMessage = res.getString(R.string.backup_deck_success);
                		break;
            		case BackupManager.RETURN_TODAY_ALREADY_BACKUP_DONE:
            		case BackupManager.RETURN_DECK_NOT_CHANGED:
            			mCurrentMessage = res.getString(R.string.loading_deck);
            			break;
            		case BackupManager.RETURN_ERROR:
            			mCurrentMessage = "backup error";
            			break;
            		case BackupManager.RETURN_NOT_ENOUGH_SPACE:
            			mCurrentMessage = "not enough space";
            			break;
            		case BackupManager.RETURN_LOW_SYSTEM_SPACE:
            			mCurrentMessage = "low system space";
            			break;
            		}
            	} else {
            		mCurrentMessage = message;
            	}
            	updateViews();
            }

            @Override
            public void onPostExecute(DeckTask.TaskData result) {
            	if (result.getInt() == DeckTask.DECK_LOADED) {
                	sLoadedDeck = result.getDeck();
                	if (sLoadedDeck != null) {
                    	sCard = sLoadedDeck.getCard();
                	}
                	mShowProgressDialog = false;
                	mCurrentMessage = null;
                	updateViews(VIEW_SHOW_QUESTION);            		
            	} else {
                	mShowProgressDialog = false;
                	mCurrentMessage = "deck could not be loaded";
                	updateViews(VIEW_DECKS);
            	}
            }
        };


        private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
            	showProgressDialog();
            }
            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            	sCard = values[0].getCard();
                if (values[0].isPreviousCardLeech()) {
                    if (values[0].isPreviousCardSuspended()) {
                    	mCurrentMessage = getResources().getString(R.string.leech_suspend_notification);
                    } else {
                    	mCurrentMessage = getResources().getString(R.string.leech_notification);
                    }
                } else {
                	mCurrentMessage = null;
                }
            	mShowProgressDialog = false;
            	updateViews(VIEW_SHOW_QUESTION);
            }
            @Override
            public void onPostExecute(DeckTask.TaskData result) {
            	if (!result.getBoolean()) {
            		// TODO: db error handling
            	}
                WidgetStatus.update(sContext, WidgetStatus.getDeckStatus(sLoadedDeck));
            }
        };


        private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
            	showProgressDialog();
            }
            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            	sCard = values[0].getCard();
            	mShowProgressDialog = false;
            	mCurrentMessage = null;
            	updateViews(VIEW_SHOW_QUESTION);
            }
            @Override
            public void onPostExecute(DeckTask.TaskData result) {
            	if (!result.getBoolean()) {
            		// TODO: db error handling
            	}
                String str = result.getString();
                if (str != null) {
                    if (str.equals(Deck.UNDO_TYPE_SUSPEND_CARD)) {
                    	mCurrentMessage = getResources().getString(R.string.card_unsuspended);
                    } else if (str.equals("redo suspend")) {
                    	mCurrentMessage = getResources().getString(R.string.card_suspended);
                    }
                }
                updateViews();
                WidgetStatus.update(sContext, WidgetStatus.getDeckStatus(sLoadedDeck));
            }
        };


        private DeckTask.TaskListener mCloseDeckHandler = new DeckTask.TaskListener() {
            @Override
            public void onPreExecute() {
            	showProgressDialog();
            }
            @Override
            public void onProgressUpdate(DeckTask.TaskData... values) {
            }
            @Override
            public void onPostExecute(DeckTask.TaskData result) {
            	sLoadedDeck = null;
            	sCard = null;
            	mShowProgressDialog = false;
            	mCurrentMessage = null;
            	updateViews(VIEW_DECKS);
            }
        };

        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: OnStart");
            sContext = this;

            if (intent != null && intent.getAction() != null) {
            	String action = intent.getAction();
            	if (ACTION_NOTHING.equals(action)) {
			// do nothing
            	} else if (action.startsWith(ACTION_UPDATE)) {
            		mShowProgressDialog = false;
            		int view;
            		try {
            			view = Integer.parseInt(action.substring(action.length() - 1));
            		} catch (NumberFormatException e) {
            			view = VIEW_NOT_SPECIFIED;
            		}
            		updateViews(view);
            	} else if (ACTION_OPENDECK.equals(action)) {
            		showProgressDialog();
            		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_LOAD_DECK, mOpenDeckHandler, new DeckTask.TaskData(intent.getStringExtra(EXTRA_DECK_PATH)));
                } else if (ACTION_CLOSEDECK.equals(action)) {
            		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CLOSE_DECK, mCloseDeckHandler, new DeckTask.TaskData(sLoadedDeck.getDeckPath()));
                } else if (ACTION_UNDO.equals(action)) {
                	if (sLoadedDeck.undoAvailable()) {
                		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new DeckTask.TaskData(0, sLoadedDeck, sCard.getId(), true));                		
                	}
                } else if (ACTION_BURY_CARD.equals(action)) {
            		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mUpdateCardHandler, new DeckTask.TaskData(0, sLoadedDeck, sCard));
                } else if (action.startsWith(ACTION_ANSWER)) {
                	int ease = Integer.parseInt(action.substring(action.length() - 1));
                	if (ease == 0) {
                		updateViews(VIEW_SHOW_ANSWER);
                	} else {
                		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(ease, sLoadedDeck, sCard));
                	}
                } else if (ACTION_SHOW_RESTRICTIONS_DIALOG.equals(action)) {
                	Intent dialogIntent = new Intent(this, WidgetDialog.class);
                    dialogIntent.setAction(WidgetDialog.ACTION_SHOW_RESTRICTIONS_DIALOG);
                    dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(dialogIntent);
                } else if (ACTION_OPEN.equals(action)) {
                	DeckManager.getDeck(intent.getData().getPath(), true, DeckManager.REQUESTING_ACTIVITY_STUDYOPTIONS);
                	Intent newIntent = StudyOptions.getLoadDeckIntent(this, "");
                	newIntent.removeExtra(StudyOptions.EXTRA_DECK);
                	newIntent.putExtra(StudyOptions.EXTRA_START_REVIEWER, (sCurrentView != VIEW_NOTHING_DUE));
                	startActivity(newIntent);
                } else if (action.startsWith(ACTION_CARDEDITOR)) {
                    Intent editIntent = new Intent(this, CardEditor.class);
                    editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (action.substring(action.length() - 1).equals("1")) {
                        editIntent.putExtra(CardEditor.CARD_EDITOR_ACTION, CardEditor.EDIT_BIGWIDGET_CARD);
                        editIntent.putExtra(CardEditor.DECKPATH, sLoadedDeck.getDeckPath());
                    } else {
                        editIntent.putExtra(CardEditor.CARD_EDITOR_ACTION, CardEditor.ADD_CARD);
                        editIntent.putExtra(CardEditor.DECKPATH, sLoadedDeck.getDeckPath());
                    }
                    this.startActivity(editIntent);
                } else if (ACTION_HELP.equals(action)) {
                	updateViews(VIEW_SHOW_HELP);
                } else if (ACTION_LEARN_MORE.equals(action)) {
                	if (sLoadedDeck != null) {
                		sLoadedDeck.setupLearnMoreScheduler();
                		sLoadedDeck.reset();
	                    sCard = sLoadedDeck.getCard();
	                	mShowProgressDialog = false;
	                	mCurrentMessage = null;
        	        	updateViews(VIEW_SHOW_QUESTION); 
                	}			
                } else if (ACTION_REVIEW_EARLY.equals(action)) {
                	if (sLoadedDeck != null) {
                		sLoadedDeck.setupReviewEarlyScheduler();
                		sLoadedDeck.reset();
	                    sCard = sLoadedDeck.getCard();
	                	mShowProgressDialog = false;
	                	mCurrentMessage = null;
        	        	updateViews(VIEW_SHOW_QUESTION); 
                	}			
                }
            }
        }


        private void showProgressDialog() {
        	mShowProgressDialog = true;
        	updateViews();
        }
        private void updateViews(int view) {
        	if (view != VIEW_NOT_SPECIFIED) {
        		sCurrentView = view;
        	}
        	updateViews();
        }
    	private void updateViews() {
            RemoteViews updateViews = buildUpdate(this);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetBig.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


    	private RemoteViews buildUpdate(Context context) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: buildUpdate (" + sCurrentView + ")");
            Resources res = getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);

            if (sCurrentView == VIEW_SHOW_HELP) {
            } else if (sLoadedDeck == null) {
            	sCurrentView = VIEW_DECKS;
            } else if (sCard == null) {
            	sCurrentView = VIEW_NOTHING_DUE;
            }

            PendingIntent doNothingIntent = getDoNothingPendingIntent(context);

    		if (mCurrentMessage != null) {
    			updateViews.setTextViewText(R.id.widget_big_message, mCurrentMessage);				
    		} else {
    			updateViews.setTextViewText(R.id.widget_big_message, "");				
    		}

            if (mShowProgressDialog) {
            	updateViews.setViewVisibility(R.id.widget_big_progressbar, View.VISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_help, View.INVISIBLE);

            		updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_empty, doNothingIntent);

            		disableCardAreaListeners(updateViews, false, doNothingIntent);
            } else {
            	updateViews.setViewVisibility(R.id.widget_big_progressbar, View.INVISIBLE);            	
            }

            switch (sCurrentView) {
            case VIEW_SHOW_HELP:
            case VIEW_DECKS:
        		updateViews.setTextViewText(R.id.widget_big_deckname, "");
        		updateCounts(updateViews);

        		updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
        		updateViews.setOnClickPendingIntent(R.id.widget_big_empty, sCurrentView == VIEW_SHOW_HELP ? getUpdatePendingIntent(this, VIEW_DECKS) : getHelpPendingIntent(this));
        		updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_help, View.VISIBLE);

        		updateViews.setViewVisibility(R.id.widget_big_open, View.VISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_close, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);
        		if (!mShowProgressDialog) {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, getShowDeckSelectionPendingIntent(context));
            		disableCardAreaListeners(updateViews, false, doNothingIntent);
        		}

        		updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.VISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_deckfield, View.VISIBLE);
        		updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
        		updateViews.setViewVisibility(R.id.widget_big_add, View.INVISIBLE);
        		break;

            case VIEW_SHOW_ANSWER:
            	if (!mShowProgressDialog) {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease1, getAnswerPendingIntent(context, 1));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease2, getAnswerPendingIntent(context, 2));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease3, getAnswerPendingIntent(context, 3));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease4, getAnswerPendingIntent(context, 4));            		

            		if (sCard.isRev()) {
            			updateViews.setViewVisibility(R.id.widget_big_ease2_normal, View.INVISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.VISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease3_normal, View.INVISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.VISIBLE);
            		} else {
            			updateViews.setViewVisibility(R.id.widget_big_ease2_normal, View.VISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.INVISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease3_normal, View.VISIBLE);
            			updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.INVISIBLE);            			
            		}
        		updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);
            		updateViews.setViewVisibility(R.id.widget_big_empty, View.INVISIBLE);
        			enableCardAreaListeners(updateViews, false);
        			updateViews.setTextViewText(R.id.widget_big_message, getNextTimeString(sCard));
            	} else {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease1, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease2, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease3, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_ease4, doNothingIntent);
		}

            	updateViews.setTextViewText(R.id.widget_big_cardcontent,  Html.fromHtml(sCard.getQuestion() 
            			+ "<br>─────<br>" 
            			+ sCard.getAnswer()));

            case VIEW_SHOW_QUESTION:
            	if (sCurrentView == VIEW_SHOW_QUESTION) {
            		if (!mShowProgressDialog) {
                		updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
                		updateViews.setOnClickPendingIntent(R.id.widget_big_empty, getAnswerPendingIntent(context, 0));            			
                		updateViews.setViewVisibility(R.id.widget_big_flipcard, View.VISIBLE);            		
                		updateViews.setViewVisibility(R.id.widget_big_help, View.INVISIBLE);            		
                		enableCardAreaListeners(updateViews, true);
            		}

            		updateViews.setTextViewText(R.id.widget_big_cardcontent,  Html.fromHtml(sCard.getQuestion())); 
            	}

        		updateViews.setTextViewText(R.id.widget_big_deckname, sLoadedDeck.getDeckName());
        		updateCounts(updateViews);
        		updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);
        		if (!mShowProgressDialog) {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, getCloseDeckPendingIntent(context));
        		}
        		updateViews.setViewVisibility(R.id.widget_big_close, View.VISIBLE);

        		updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);

        		updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_add, View.VISIBLE);
        		break;

            case VIEW_NOTHING_DUE:
        		updateViews.setTextViewText(R.id.widget_big_deckname, sLoadedDeck.getDeckName());
        		updateCounts(updateViews);

        		if (!mShowProgressDialog) {
            		updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_empty, doNothingIntent);
            		updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
            		updateViews.setViewVisibility(R.id.widget_big_help, View.INVISIBLE);
            		updateViews.setViewVisibility(R.id.widget_big_open, View.VISIBLE);
            		updateViews.setViewVisibility(R.id.widget_big_close, View.INVISIBLE);

            		disableCardAreaListeners(updateViews, true, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, getCloseDeckPendingIntent(context));

        		updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.VISIBLE);
        		updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
        		}

        		updateViews.setViewVisibility(R.id.widget_big_add, View.VISIBLE);

        		updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.VISIBLE);
        		updateViews.setTextViewText(R.id.widget_big_congrats, StudyOptions.getCongratsMessage(this, sLoadedDeck));

        		updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_close, View.VISIBLE);

            		updateViews.setOnClickPendingIntent(R.id.widget_big_review_early, getReviewEarlyPendingIntent(context));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_learn_more, getLearnMorePendingIntent(context));
        		break;
            }

            if (sCurrentView == VIEW_SHOW_HELP) {
            	String[] values = res.getStringArray(R.array.gestures_labels);
        		updateViews.setTextViewText(R.id.widget_big_topleft, res.getString(R.string.open_in_reviewer));
        		updateViews.setTextViewText(R.id.widget_big_middleleft, values[12]);
        		updateViews.setTextViewText(R.id.widget_big_bottomleft, values[1]);
        		updateViews.setTextViewText(R.id.widget_big_topright, values[9]);
        		updateViews.setTextViewText(R.id.widget_big_middleright, values[7]);
        		updateViews.setTextViewText(R.id.widget_big_bottomright, values[5]);
        		updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_help, View.VISIBLE);
        		updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
        		updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.INVISIBLE);
        		updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);

        		if (!mShowProgressDialog) {
        			disableCardAreaListeners(updateViews, false, doNothingIntent);
        		}
            } else {
        		updateViews.setTextViewText(R.id.widget_big_topleft, "");
        		updateViews.setTextViewText(R.id.widget_big_middleleft, "");
        		updateViews.setTextViewText(R.id.widget_big_bottomleft, "");
        		updateViews.setTextViewText(R.id.widget_big_topright, "");
        		updateViews.setTextViewText(R.id.widget_big_middleright, "");
        		updateViews.setTextViewText(R.id.widget_big_bottomright, "");
            }
//        		WebView webView = new WebView(this);
//        		webView.setDrawingCacheEnabled(true);
//        		webView.layout(0, 0, 500, 500); 
//        		webView.loadDataWithBaseURL("", "asdf", "text/html", "utf-8", null);
//        		webView.buildDrawingCache(true);
//        		Bitmap b = Bitmap.createBitmap(webView.getDrawingCache());
//        		webView.setDrawingCacheEnabled(false); // clear drawing cache
//
//        		FileOutputStream out;
//				try {
//					out = new FileOutputStream("/emmc/test.png");
//	        	       b.compress(Bitmap.CompressFormat.PNG, 90, out);
//	        	       File f = new File("/emmc/test.png");
//	        	       Uri uri = Uri.fromFile(f);
//	        	       updateViews.setImageViewUri(R.id.widget_big_cardcontent, uri);
//				} catch (FileNotFoundException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}

            return updateViews;
        }

	private void disableCardAreaListeners(RemoteViews updateViews, boolean keepUndoOpening, PendingIntent doNothingIntent) {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_topleft, keepUndoOpening ? getOpenPendingIntent(this, sLoadedDeck.getDeckPath()) : doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_topright, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_middleleft, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_middleright, keepUndoOpening ? getUndoPendingIntent(this) : doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottom_right, keepUndoOpening ? getCardEditorPendingIntent(this, false) : doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, doNothingIntent);
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, doNothingIntent);
	}

	private void enableCardAreaListeners(RemoteViews updateViews, boolean questionsShown) {
            		updateViews.setOnClickPendingIntent(R.id.widget_big_topleft, getOpenPendingIntent(this, sLoadedDeck.getDeckPath()));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_middleleft, getBuryCardPendingIntent(this));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, getAnswerPendingIntent(this, questionsShown ? 0 : 1));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_topright, getCardEditorPendingIntent(this, true));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottom_right, getCardEditorPendingIntent(this, false));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_middleright, getUndoPendingIntent(this));
            		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, getAnswerPendingIntent(this, questionsShown ? 0 : (sCard.isRev() ? 3 : 2)));
	}

        private PendingIntent getAnswerPendingIntent(Context context, int ease) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_ANSWER + Integer.toString(ease));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getShowDeckSelectionPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, WidgetDialog.class);
            ankiDroidIntent.setAction(WidgetDialog.ACTION_SHOW_DECK_SELECTION_DIALOG);
//            ankiDroidIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            return PendingIntent.getActivity(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getCloseDeckPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_CLOSEDECK);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getUndoPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_UNDO);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getBuryCardPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_BURY_CARD);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getOpenPendingIntent(Context context, String deckPath) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_OPEN);
            ankiDroidIntent.setData(Uri.parse("file://" + deckPath));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getLearnMorePendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_LEARN_MORE);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getReviewEarlyPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_REVIEW_EARLY);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getCardEditorPendingIntent(Context context, boolean editCurrentCard) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_CARDEDITOR + (editCurrentCard ? "1" : "0"));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getHelpPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_HELP);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getUpdatePendingIntent(Context context, int view) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_UPDATE + Integer.toString(view));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getDoNothingPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_NOTHING);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
