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

import com.ichi2.anki.WidgetStatus.WidgetDeckTaskData;
import com.ichi2.widget.WidgetDialog;
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
import android.graphics.Color;
import android.net.Uri;
import android.os.IBinder;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class AnkiDroidWidgetBig extends AppWidgetProvider {

    private static BroadcastReceiver mMountReceiver = null;
    private static boolean remounted = false;
    private static Deck sLoadedDeck;
    private static Card sCard;
    private static boolean sShowAnswer = false;

    private static Context sContext;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(AnkiDroidApp.TAG, "BigWidget: onUpdate");
//        WidgetStatus.update(context);
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
        if (preferences.getBoolean("widgetBigEnabled", false)) {
            // show info dialog
            Intent intent;
            intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
            intent.setAction(UpdateService.ACTION_SHOW_RESTRICTIONS_DIALOG);
            context.startService(intent);
        }
        preferences.edit().putBoolean("widgetBigEnabled", true).commit();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget disabled");
        SharedPreferences preferences = PrefSettings.getSharedPrefs(context);
        preferences.edit().putBoolean("widgetBigEnabled", false).commit();
    }


    public static void setDeckAndLoadCard(Deck deck) {
    	sLoadedDeck = deck;
    	if (deck != null) {
        	sCard = sLoadedDeck.getCard();
    	}
    	updateWidget();
    }


    public static void setCard(Card card) {
    	if (sLoadedDeck != null) {
        	sCard = card;
    	}
    	updateWidget();
    }

    public static Card getCard() {
    	return sCard;
    }

    public static void updateWidget() {
        Intent intent;
        if (sContext != null) {
            intent = new Intent(sContext, AnkiDroidWidgetBig.UpdateService.class);
            intent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_UPDATE);
            sContext.startService(intent);        	
        }
    }


    public static class UpdateService extends Service {
    	public static final String ACTION_OPENDECK = "org.ichi2.anki.AnkiDroidWidgetBig.OPENDECK";
        public static final String ACTION_CLOSEDECK = "org.ichi2.anki.AnkiDroidWidgetBig.CLOSEDECK";
        public static final String ACTION_ANSWER = "org.ichi2.anki.AnkiDroidWidgetBig.ANSWER";
        public static final String ACTION_OPEN = "org.ichi2.anki.AnkiDroidWidgetBig.OPEN";
        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidgetBig.UPDATE";
        public static final String ACTION_BURY_CARD = "org.ichi2.anki.AnkiDroidWidgetBig.BURYCARD";
        public static final String ACTION_UNDO = "org.ichi2.anki.AnkiDroidWidgetBig.UNDO";
        public static final String ACTION_CARDEDITOR = "org.ichi2.anki.AnkiDroidWidgetBig.CARDEDITOR";
        public static final String ACTION_SHOW_RESTRICTIONS_DIALOG = "org.ichi2.anki.AnkiDroidWidgetBig.SHOWRESTRICTIONSDIALOG";
        public static final String EXTRA_ANSWER_EASE = "answerEase";
        public static final String EXTRA_DECK_PATH = "deckPath";


        public static final int VIEW_ACTION_DEFAULT = 0;
        public static final int VIEW_ACTION_SHOW_PROGRESS_DIALOG = 1;
        public static final int VIEW_ACTION_HIDE_BUTTONS = 2;

        private CharSequence getDeckStatusString(Deck deck, Card card) {
            SpannableStringBuilder sb = new SpannableStringBuilder();

            SpannableString red = new SpannableString(Integer.toString(deck.getFailedSoonCount()));
            red.setSpan(new ForegroundColorSpan(Color.RED), 0, red.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (card != null && card.getType() == Card.TYPE_FAILED) {
            	red.setSpan(new UnderlineSpan(), 0, red.length(), 0);
            }

            SpannableString black = new SpannableString(Integer.toString(deck.getRevCount()));
            black.setSpan(new ForegroundColorSpan(Color.BLACK), 0, black.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (card != null && card.getType() == Card.TYPE_REV) {
            	black.setSpan(new UnderlineSpan(), 0, black.length(), 0);
            }

            SpannableString blue = new SpannableString(Integer.toString(deck.getNewCountToday()));
            blue.setSpan(new ForegroundColorSpan(Color.BLUE), 0, blue.length(),
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


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: OnStart");
    		
            if (intent != null && intent.getAction() != null) {
            	String action = intent.getAction();
            	if (ACTION_UPDATE.equals(action)) {
            		updateViews();
            	} else if (ACTION_OPENDECK.equals(action)) {
                	updateViews(VIEW_ACTION_SHOW_PROGRESS_DIALOG);
                	WidgetStatus.deckOperation(WidgetStatus.TASK_OPEN_DECK, new WidgetDeckTaskData(AnkiDroidWidgetBig.UpdateService.this, intent.getStringExtra(EXTRA_DECK_PATH)));
                } else if (ACTION_CLOSEDECK.equals(action)) {
                	updateViews(VIEW_ACTION_SHOW_PROGRESS_DIALOG);
                	if (sLoadedDeck != null) {
                    	WidgetStatus.deckOperation(WidgetStatus.TASK_CLOSE_DECK, new WidgetDeckTaskData(this, sLoadedDeck.getDeckPath()));
                	}
                } else if (ACTION_UNDO.equals(action)) {
                	sShowAnswer = false;
                	updateViews(VIEW_ACTION_HIDE_BUTTONS);
                	WidgetStatus.deckOperation(WidgetStatus.TASK_UNDO, new WidgetDeckTaskData(this, sLoadedDeck));
                } else if (ACTION_BURY_CARD.equals(action)) {
                	sShowAnswer = false;
                	updateViews(VIEW_ACTION_HIDE_BUTTONS);
                	WidgetStatus.deckOperation(WidgetStatus.TASK_BURY_CARD, new WidgetDeckTaskData(this, sLoadedDeck, sCard));
                } else if (action.startsWith(ACTION_ANSWER)) {
                	int ease = Integer.parseInt(action.substring(action.length() - 1));
                	if (ease == 0) {
                		sShowAnswer = true;
                		updateViews();
                	} else {
                    	sShowAnswer = false;
                    	updateViews(VIEW_ACTION_HIDE_BUTTONS);
                    	WidgetStatus.deckOperation(WidgetStatus.TASK_ANSWER_CARD, new WidgetDeckTaskData(this, sLoadedDeck, sCard, ease));                		
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
                	newIntent.putExtra(StudyOptions.EXTRA_START_REVIEWER, true);
                	startActivity(newIntent);
                } else if (ACTION_CARDEDITOR.equals(action)) {
                    Intent editIntent = new Intent(this, CardEditor.class);
                    editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    editIntent.putExtra(CardEditor.CARD_EDITOR_ACTION, CardEditor.EDIT_BIGWIDGET_CARD);
                    editIntent.putExtra(CardEditor.DECKPATH, sLoadedDeck.getDeckPath());
                    this.startActivity(editIntent);
                }
            }
        }


        private void updateViews() {
        	updateViews(VIEW_ACTION_DEFAULT);
        }
    	private void updateViews(int action) {
            RemoteViews updateViews = buildUpdate(this, action);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetBig.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);        	
        }


    	private RemoteViews buildUpdate(Context context, int action) {
            Log.i(AnkiDroidApp.TAG, "BigWidget: buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);

            if (action == VIEW_ACTION_SHOW_PROGRESS_DIALOG) {
            	updateViews.setViewVisibility(R.id.widget_big_progressbar, View.VISIBLE);
            } else {
            	updateViews.setViewVisibility(R.id.widget_big_progressbar, View.INVISIBLE);            	
            }
            if (action == VIEW_ACTION_HIDE_BUTTONS) {
//            	updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);
//            	updateViews.setViewVisibility(R.id.widget_big_close, View.INVISIBLE);
            	updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
            } else if (sLoadedDeck != null) {
        		updateViews.setTextViewText(R.id.widget_big_deckname, sLoadedDeck.getDeckName());
        		PendingIntent openADIntent = getOpenPendingIntent(this, sLoadedDeck.getDeckPath());
        		updateViews.setOnClickPendingIntent(R.id.widget_big_deckname, openADIntent);
        		updateViews.setOnClickPendingIntent(R.id.widget_big_topleft, openADIntent);

        		updateViews.setTextViewText(R.id.widget_big_counts, getDeckStatusString(sLoadedDeck, sCard));
        		
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

        		PendingIntent closeDeckPendingIntent = getCloseDeckPendingIntent(context);
                updateViews.setOnClickPendingIntent(R.id.widget_big_close, closeDeckPendingIntent);
                updateViews.setViewVisibility(R.id.widget_big_close, View.VISIBLE);
                updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);

                updateViews.setTextViewText(R.id.widget_big_topleft, "");
                updateViews.setTextViewText(R.id.widget_big_topright, "");
                updateViews.setTextViewText(R.id.widget_big_middleleft, "");
                updateViews.setTextViewText(R.id.widget_big_middleright, "");
                updateViews.setTextViewText(R.id.widget_big_bottomleft, "");
                updateViews.setTextViewText(R.id.widget_big_bottomright, "");

                // set the flashcard content
                if (sCard != null) {
                    PendingIntent undoPendindIntent = getUndoPendingIntent(context);
                    updateViews.setOnClickPendingIntent(R.id.widget_big_middleright, undoPendindIntent);
                    PendingIntent buryPendindIntent = getBuryCardPendingIntent(context);
                    updateViews.setOnClickPendingIntent(R.id.widget_big_middleleft, buryPendindIntent);
                	PendingIntent cardEditorIntent = getCardEditorPendingIntent(context);
                    updateViews.setOnClickPendingIntent(R.id.widget_big_topright, cardEditorIntent);
                	updateViews.setViewVisibility(R.id.widget_big_empty, View.INVISIBLE);
                    // answer buttons
                    if (sShowAnswer) {
                    	updateViews.setTextViewText(R.id.widget_big_cardcontent,  Html.fromHtml(sCard.getQuestion() 
                    			+ "<br>─────<br>" 
                    			+ sCard.getAnswer()));
                		PendingIntent ease1PendindIntent = getAnswerPendingIntent(context, 1);
                        updateViews.setOnClickPendingIntent(R.id.widget_big_ease1, ease1PendindIntent);
                        updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, ease1PendindIntent);
                        if (sCard.isRev()) {
                    		PendingIntent ease2PendindIntent = getAnswerPendingIntent(context, 2);
                            updateViews.setOnClickPendingIntent(R.id.widget_big_ease2, ease2PendindIntent);
                    		PendingIntent ease3PendindIntent = getAnswerPendingIntent(context, 3);
                    		updateViews.setOnClickPendingIntent(R.id.widget_big_ease3_rec, ease3PendindIntent);
                    		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, ease3PendindIntent);
                        	updateViews.setViewVisibility(R.id.widget_big_ease2, View.VISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.INVISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease3, View.INVISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.VISIBLE);
                        } else {
                    		PendingIntent ease2PendindIntent = getAnswerPendingIntent(context, 2);
                            updateViews.setOnClickPendingIntent(R.id.widget_big_ease2_rec, ease2PendindIntent);
                    		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, ease2PendindIntent);
                    		PendingIntent ease3PendindIntent = getAnswerPendingIntent(context, 3);
                            updateViews.setOnClickPendingIntent(R.id.widget_big_ease3, ease3PendindIntent);	                    	
                        	updateViews.setViewVisibility(R.id.widget_big_ease2, View.INVISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.VISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease3, View.VISIBLE);
                        	updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.INVISIBLE);
                        }
                		PendingIntent ease4PendindIntent = getAnswerPendingIntent(context, 4);
                        updateViews.setOnClickPendingIntent(R.id.widget_big_ease4, ease4PendindIntent);                	

                        updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
                    } else {
                		PendingIntent ease0PendindIntent = getAnswerPendingIntent(context, 0);
                        updateViews.setOnClickPendingIntent(R.id.widget_big_flipcard, ease0PendindIntent);
                		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, ease0PendindIntent);
                		updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, ease0PendindIntent);
                    	updateViews.setViewVisibility(R.id.widget_big_flipcard, View.VISIBLE);
                    	updateViews.setTextViewText(R.id.widget_big_cardcontent, Html.fromHtml(sCard.getQuestion()));
                    }
                } else {
                	updateViews.setViewVisibility(R.id.widget_big_empty, View.INVISIBLE);
                }
        	} else {
        		sShowAnswer = false;
        		PendingIntent openPendingIntent = getShowDeckSelectionPendingIntent(context);
                updateViews.setOnClickPendingIntent(R.id.widget_big_open, openPendingIntent);
                updateViews.setTextViewText(R.id.widget_big_counts, "");
                updateViews.setTextViewText(R.id.widget_big_deckname, "");
                updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
            	updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
                updateViews.setViewVisibility(R.id.widget_big_close, View.INVISIBLE);
                updateViews.setViewVisibility(R.id.widget_big_open, View.VISIBLE);
                updateViews.setTextViewText(R.id.widget_big_topleft, "reviewer");
                updateViews.setTextViewText(R.id.widget_big_topright, "cardeditor");
                updateViews.setTextViewText(R.id.widget_big_middleleft, "bury");
                updateViews.setTextViewText(R.id.widget_big_middleright, "undo");
                updateViews.setTextViewText(R.id.widget_big_bottomleft, "again");
                updateViews.setTextViewText(R.id.widget_big_bottomright, "recommended");
        	}
            return updateViews;
        }


        private PendingIntent getAnswerPendingIntent(Context context, int ease) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_ANSWER + Integer.toString(ease));
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getOpenDeckPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_OPENDECK);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        private PendingIntent getShowDeckSelectionPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, WidgetDialog.class);
            ankiDroidIntent.setAction(WidgetDialog.ACTION_SHOW_DECK_SELECTION_DIALOG);
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

        private PendingIntent getCardEditorPendingIntent(Context context) {
            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
            ankiDroidIntent.setAction(ACTION_CARDEDITOR);
            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
        }

        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(AnkiDroidApp.TAG, "onBind");
            return null;
        }
    }
}
