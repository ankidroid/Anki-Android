///***************************************************************************************
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
//package com.ichi2.widget;
//
//import org.json.JSONException;
//
//import com.ichi2.anki.AnkiDroidApp;
//import com.ichi2.anki.DeckPicker;
//import com.ichi2.anki2.R;
//import com.ichi2.async.DeckTask;
//import com.ichi2.libanki.Card;
//import com.ichi2.libanki.Utils;
//
//import android.app.PendingIntent;
//import android.app.Service;
//import android.appwidget.AppWidgetManager;
//import android.appwidget.AppWidgetProvider;
//import android.content.ComponentName;
//import android.content.Context;
//import android.content.Intent;
//import android.content.ServiceConnection;
//import android.content.SharedPreferences;
//import android.content.res.Resources;
//import android.graphics.Color;
//import android.os.AsyncTask;
//import android.os.IBinder;
//import android.text.Html;
//import android.text.SpannableString;
//import android.text.SpannableStringBuilder;
//import android.text.Spanned;
//import android.text.style.ForegroundColorSpan;
//import android.text.style.UnderlineSpan;
//import android.util.Log;
//import android.view.View;
//import android.widget.RemoteViews;
//
//public class AnkiDroidWidgetBig extends AppWidgetProvider {
//
//    private static Context sContext;
//
//    private static WidgetContentService contentService = null;
//    private static WidgetContentService.WidgetContentBinder contentServiceBinder;
//    private static Intent tempIntent;
//
//    private static ServiceConnection localServiceConnection = new ServiceConnection() {
//
//        @Override
//        public void onServiceConnected(ComponentName className, IBinder binder) {
//            Log.i(AnkiDroidApp.TAG, "binding content service - success");
//            contentServiceBinder = (WidgetContentService.WidgetContentBinder) binder;
//            contentService = contentServiceBinder.getService();
//            // check, if card is still the same after reloading the deck. If not, show question instead of answering
//            if (tempIntent.getAction().startsWith(UpdateService.ACTION_ANSWER)
//                    && contentService.mCurrentCard != null
//                    && AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).getLong(
//                            "lastWidgetCard", 0) != contentService.mCurrentCard.getId()) {
//                tempIntent.setAction(UpdateService.ACTION_UPDATE);
//                tempIntent.putExtra(UpdateService.EXTRA_VIEW, UpdateService.VIEW_SHOW_QUESTION);
//            }
//            sContext.startService(tempIntent);
//            tempIntent = null;
//        }
//
//
//        @Override
//        public void onServiceDisconnected(ComponentName arg0) {
//            contentService = null;
//        }
//    };
//
//
//    @Override
//    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
//        Log.i(AnkiDroidApp.TAG, "BigWidget: onUpdate");
//        WidgetStatus.update(context);
//    }
//
//
//    @Override
//    public void onEnabled(Context context) {
//        super.onEnabled(context);
//        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget enabled");
//        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
//        if (!preferences.getBoolean("widgetBigEnabled", false)) {
//            // show info dialog
//            Intent intent;
//            intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
//            intent.setAction(UpdateService.ACTION_SHOW_RESTRICTIONS_DIALOG);
//            context.startService(intent);
//        }
//        preferences.edit().putBoolean("widgetBigEnabled", true).commit();
//        if (contentService != null) {
//            contentService.mBigCurrentView = UpdateService.VIEW_NOT_SPECIFIED;
//            contentService.mCol = null;
//            AnkiDroidApp.getSharedPrefs(AnkiDroidApp.getInstance().getBaseContext()).edit()
//                    .putString("lastWidgetDeck", "").commit();
//            contentService.setCard(null);
//            contentService.mBigCurrentMessage = null;
//            contentService.mBigShowProgressDialog = false;
//            contentService.mTomorrowDues = null;
//            contentService.mWaitForAsyncTask = false;
//        }
//    }
//
//
//    @Override
//    public void onDisabled(Context context) {
//        super.onDisabled(context);
//        Log.i(AnkiDroidApp.TAG, "BigWidget: Widget disabled");
//        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
//        preferences.edit().putBoolean("widgetBigEnabled", false).commit();
//    }
//
//
//    // public static void setDeck(Decks deck) {
//    // if (contentService != null) {
//    // contentService.mCol = deck;
//    // }
//    // updateWidget(UpdateService.VIEW_NOT_SPECIFIED);
//    // }
//
//    public static void setCard(Card card) {
//        if (contentService != null && contentService.mCol != null) {
//            contentService.setCard(card);
//        }
//    }
//
//
//    public static Card getCard() {
//        if (contentService != null) {
//            return contentService.mCurrentCard;
//        } else {
//            return null;
//        }
//    }
//
//
//    public static void updateWidget(int view) {
//        updateWidget(view, false);
//    }
//
//
//    public static void updateWidget(int view, boolean showProgressDialog) {
//        AnkiDroidApp
//                .getInstance()
//                .getApplicationContext()
//                .startService(
//                        getUpdateIntent(AnkiDroidApp.getInstance().getApplicationContext(), view, showProgressDialog));
//    }
//
//
//    public static Intent getUpdateIntent(Context context, int view, boolean showProgressDialog) {
//        Intent intent = new Intent(context, AnkiDroidWidgetBig.UpdateService.class);
//        intent.putExtra(UpdateService.EXTRA_VIEW, view);
//        intent.putExtra(UpdateService.EXTRA_PROGRESSDIALOG, showProgressDialog);
//        return intent.setAction(UpdateService.ACTION_UPDATE);
//    }
//
//    public static class UpdateService extends Service {
//        public static final String ACTION_NOTHING = "org.ichi2.anki.AnkiDroidWidgetBig.NOTHING";
//        public static final String ACTION_OPENDECK = "org.ichi2.anki.AnkiDroidWidgetBig.OPENDECK";
//        public static final String ACTION_OPENCOLLECTION = "org.ichi2.anki.AnkiDroidWidgetBig.OPENCOLLECTION";
//        public static final String ACTION_CLOSEDECK = "org.ichi2.anki.AnkiDroidWidgetBig.CLOSEDECK";
//        public static final String ACTION_ANSWER = "org.ichi2.anki.AnkiDroidWidgetBig.ANSWER";
//        public static final String ACTION_OPEN = "org.ichi2.anki.AnkiDroidWidgetBig.OPEN";
//        public static final String ACTION_UPDATE = "org.ichi2.anki.AnkiDroidWidgetBig.UPDATE";
//        public static final String ACTION_BURY_CARD = "org.ichi2.anki.AnkiDroidWidgetBig.BURYCARD";
//        public static final String ACTION_UNDO = "org.ichi2.anki.AnkiDroidWidgetBig.UNDO";
//        public static final String ACTION_CARDEDITOR = "org.ichi2.anki.AnkiDroidWidgetBig.CARDEDITOR";
//        public static final String ACTION_FACTADDER = "org.ichi2.anki.AnkiDroidWidgetBig.FACTADDER";
//        public static final String ACTION_HELP = "org.ichi2.anki.AnkiDroidWidgetBig.HELP";
//        public static final String ACTION_SHOW_RESTRICTIONS_DIALOG = "org.ichi2.anki.AnkiDroidWidgetBig.SHOWRESTRICTIONSDIALOG";
//        public static final String ACTION_LEARN_MORE = "org.ichi2.anki.AnkiDroidWidgetBig.LEARNMORE";
//        public static final String ACTION_REVIEW_EARLY = "org.ichi2.anki.AnkiDroidWidgetBig.REVIEWEARLY";
//        public static final String ACTION_SHOW_TOMORROW_DUES = "org.ichi2.anki.AnkiDroidWidgetBig.TOMORROWDUES";
//
//        public static final String EXTRA_EASE = "ease";
//        public static final String EXTRA_VIEW = "view";
//        public static final String EXTRA_PROGRESSDIALOG = "progressDialog";
//
//        public static final int VIEW_ACTION_DEFAULT = 0;
//        public static final int VIEW_ACTION_SHOW_PROGRESS_DIALOG = 1;
//        public static final int VIEW_ACTION_HIDE_BUTTONS = 2;
//
//        public static final int VIEW_NOT_SPECIFIED = 0;
//        public static final int VIEW_DECKS = 1;
//        public static final int VIEW_CONGRATS = 2;
//        public static final int VIEW_SHOW_QUESTION = 3;
//        public static final int VIEW_SHOW_ANSWER = 4;
//        public static final int VIEW_NOTHING_DUE = 5;
//        public static final int VIEW_SHOW_HELP = 6;
//
//        private static final int sColorBlue = Color.parseColor("#000099");
//        private static final int sColorRed = Color.parseColor("#990000");
//        private static final int sColorGreen = Color.parseColor("#007700");
//
//
//        private CharSequence getDeckStatusString(Card card) {
//            int[] counts = contentService.mCol.getSched().counts(card);
//            return getDeckStatusString(counts[0], counts[1], counts[2], card);
//        }
//
//
//        private CharSequence getDeckStatusString(int newCount, int lrnCount, int dueCount, Card card) {
//            SpannableStringBuilder sb = new SpannableStringBuilder();
//
//            SpannableString red = new SpannableString(Integer.toString(newCount));
//            red.setSpan(new ForegroundColorSpan(sColorBlue), 0, red.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            if (card != null && card.getType() == Card.TYPE_NEW) {
//                red.setSpan(new UnderlineSpan(), 0, red.length(), 0);
//            }
//
//            SpannableString black = new SpannableString(Integer.toString(lrnCount));
//            black.setSpan(new ForegroundColorSpan(sColorRed), 0, black.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            if (card != null && card.getType() == Card.TYPE_LRN) {
//                black.setSpan(new UnderlineSpan(), 0, black.length(), 0);
//            }
//
//            SpannableString blue = new SpannableString(Integer.toString(dueCount));
//            blue.setSpan(new ForegroundColorSpan(sColorGreen), 0, blue.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            if (card != null && card.getType() == Card.TYPE_REV) {
//                blue.setSpan(new UnderlineSpan(), 0, blue.length(), 0);
//            }
//
//            sb.append(red);
//            sb.append(" ");
//            sb.append(black);
//            sb.append(" ");
//            sb.append(blue);
//
//            return sb;
//        }
//
//
//        private CharSequence getNextTimeString(Card card) {
//            SpannableStringBuilder sb = new SpannableStringBuilder();
//
//            SpannableString again = new SpannableString(contentService.mCol.getSched().nextIvlStr(card, 1));
//            SpannableString hard = new SpannableString(contentService.mCol.getSched().nextIvlStr(card, 2));
//            SpannableString easy = new SpannableString(contentService.mCol.getSched().nextIvlStr(card, 3));
//            SpannableString veryEasy = null;
//
//            again.setSpan(new ForegroundColorSpan(sColorRed), 0, again.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//            if (contentService.mCol.getSched().answerButtons(card) == 3) {
//                hard.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color_green)), 0,
//                        hard.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                easy.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color)), 0,
//                        easy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            } else {
//                hard.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color)), 0,
//                        hard.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                easy.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color_green)), 0,
//                        easy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//                veryEasy = new SpannableString(contentService.mCol.getSched().nextIvlStr(card, 4));
//                veryEasy.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.widget_big_font_color)), 0,
//                        veryEasy.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            }
//
//            sb.append(again);
//            sb.append(" \u2027 ");
//            sb.append(hard);
//            sb.append(" \u2027 ");
//            sb.append(easy);
//            if (contentService.mCol.getSched().answerButtons(card) == 4) {
//                sb.append(" \u2027 ");
//                sb.append(veryEasy);
//            }
//
//            return sb;
//        }
//
//        private DeckTask.TaskListener mOpenCollectionHandler = new DeckTask.TaskListener() {
//            @Override
//            public void onPreExecute() {
//                showProgressDialog();
//            }
//
//
//            @Override
//            public void onProgressUpdate(DeckTask.TaskData... values) {
//                String message = values[0].getString();
//                if (message != null) {
//                    contentService.mBigCurrentMessage = message;
//                }
//                updateViews();
//            }
//
//
//            @Override
//            public void onPostExecute(DeckTask.TaskData result) {
//                contentService.mCol = result.getCollection();
//                if (contentService.mCol != null) {
//                    showDeckSelectionDialog();
//                } else {
//                    handleError();
//                }
//            }
//        };
//
//        private DeckTask.TaskListener mOpenDeckHandler = new DeckTask.TaskListener() {
//            @Override
//            public void onPreExecute() {
//                showProgressDialog();
//            }
//
//
//            @Override
//            public void onProgressUpdate(DeckTask.TaskData... values) {
//            }
//
//
//            @Override
//            public void onPostExecute(DeckTask.TaskData result) {
//                contentService.setCard();
//                contentService.mBigShowProgressDialog = false;
//                contentService.mBigCurrentMessage = null;
//                updateViews(VIEW_SHOW_QUESTION);
//            }
//        };
//
//        private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
//            @Override
//            public void onPreExecute() {
//                showProgressDialog();
//            }
//
//
//            @Override
//            public void onProgressUpdate(DeckTask.TaskData... values) {
//                contentService.setCard(values[0].getCard());
//                int leech = values[0].getInt();
//                if (leech > 0) {
//                    String leechMessage;
//                    if (leech == 2) {
//                        contentService.mBigCurrentMessage = getResources().getString(
//                                R.string.leech_suspend_notification);
//                    } else {
//                        contentService.mBigCurrentMessage = getResources().getString(R.string.leech_notification);
//                    }
//                } else {
//                    contentService.mBigCurrentMessage = null;
//                }
//                contentService.mBigShowProgressDialog = false;
//                updateViews(VIEW_SHOW_QUESTION);
//                // TODO: do not reload all counts
//                WidgetStatus.update(UpdateService.this);
//            }
//
//
//            @Override
//            public void onPostExecute(DeckTask.TaskData result) {
//                if (!result.getBoolean()) {
//                    handleError();
//                }
//            }
//        };
//
//        private DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
//            @Override
//            public void onPreExecute() {
//                showProgressDialog();
//            }
//
//
//            @Override
//            public void onProgressUpdate(DeckTask.TaskData... values) {
//                contentService.setCard(values[0].getCard());
//                contentService.mBigShowProgressDialog = false;
//                contentService.mBigCurrentMessage = null;
//                updateViews(VIEW_SHOW_QUESTION);
//            }
//
//
//            @Override
//            public void onPostExecute(DeckTask.TaskData result) {
//                if (!result.getBoolean()) {
//                    handleError();
//                    return;
//                }
//                String str = result.getString();
//                if (str != null) {
//                    // if (str.equals(Decks.UNDO_TYPE_SUSPEND_CARD)) {
//                    // contentService.mBigCurrentMessage = getResources().getString(R.string.card_unsuspended);
//                    // } else if (str.equals("redo suspend")) {
//                    // contentService.mBigCurrentMessage = getResources().getString(R.string.card_suspended);
//                    // }
//                }
//                updateViews();
//                // WidgetStatus.update(UpdateService.this, WidgetStatus.getDeckStatus(contentService.mCol), false);
//            }
//        };
//
//        private DeckTask.TaskListener mCloseDeckHandler = new DeckTask.TaskListener() {
//            @Override
//            public void onPreExecute() {
//                showProgressDialog();
//            }
//
//
//            @Override
//            public void onProgressUpdate(DeckTask.TaskData... values) {
//            }
//
//
//            @Override
//            public void onPostExecute(DeckTask.TaskData result) {
//                contentService.mCol = null;
//                AnkiDroidApp.getSharedPrefs(UpdateService.this.getBaseContext()).edit().putString("lastWidgetDeck", "")
//                        .commit();
//                contentService.setCard(null);
//                contentService.mBigShowProgressDialog = false;
//                contentService.mBigCurrentMessage = null;
//                updateViews(VIEW_DECKS);
//            }
//        };
//
//
//        @Override
//        public void onStart(Intent intent, int startId) {
//            Log.i(AnkiDroidApp.TAG, "BigWidget: OnStart");
//
//            if (intent == null) {
//                // do nothing
//            } else if (contentService == null) {
//                Log.i(AnkiDroidApp.TAG, "binding content service");
//                updateViews();
//                tempIntent = intent;
//                sContext = this;
//                Intent contentIntent = new Intent(this, WidgetContentService.class);
//                this.bindService(contentIntent, localServiceConnection, Context.BIND_AUTO_CREATE);
//            } else if (intent.getAction() != null) {
//                String action = intent.getAction();
//                if (ACTION_NOTHING.equals(action)) {
//                    // do nothing
//                } else if (ACTION_UPDATE.equals(action)) {
//                    if (!contentService.mWaitForAsyncTask) {
//                        if (!intent.getBooleanExtra(EXTRA_PROGRESSDIALOG, false)) {
//                            contentService.mBigShowProgressDialog = false;
//                            updateViews(intent.getIntExtra(EXTRA_VIEW, VIEW_NOT_SPECIFIED));
//                        } else if (!contentService.mBigShowProgressDialog) {
//                            showProgressDialog();
//                        }
//                    }
//                } else if (ACTION_OPENCOLLECTION.equals(action)) {
//                    showProgressDialog();
//                    if (contentService.mCol == null) {
//                        contentService.mBigCurrentMessage = getResources().getString(R.string.open_collection);
//                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_OPEN_COLLECTION, mOpenCollectionHandler,
//                                new DeckTask.TaskData(AnkiDroidApp.getCollectionPath()));
//                        updateViews();
//                    } else {
//                        showDeckSelectionDialog();
//                    }
//                } else if (ACTION_OPENDECK.equals(action)) {
//                    showProgressDialog();
//                    if (contentService != null) {
//                        contentService.mCol.getDecks().select(intent.getLongExtra(DeckPicker.EXTRA_DECK_ID, 1));
//                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_VALUES_FROM_DECK, mOpenDeckHandler,
//                                new DeckTask.TaskData(contentService.mCol.getSched(), true));
//                    }
//                } else if (ACTION_CLOSEDECK.equals(action)) {
//                    // if (contentService.mCol != null) {
//                    // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_CLOSE_DECK, mCloseDeckHandler, new
//                    // DeckTask.TaskData(contentService.mCol.getDeckPath()));
//                    // } else {
//                    updateViews(VIEW_DECKS);
//                    // }
//                } else if (ACTION_UNDO.equals(action)) {
//                    if (contentService.mCol != null) {
//                        if (contentService.mCol.undoAvailable()) {
//                            // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new
//                            // DeckTask.TaskData(0, contentService.mCol, contentService.mCurrentCard != null ?
//                            // contentService.mCurrentCard.getId() : 0, true));
//                        }
//                    } else {
//                        updateViews(VIEW_DECKS);
//                    }
//                } else if (ACTION_BURY_CARD.equals(action)) {
//                    if (contentService.mCol != null) {
//                        // DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mUpdateCardHandler, new
//                        // DeckTask.TaskData(0, contentService.mCol, contentService.mCurrentCard));
//                    } else {
//                        updateViews(VIEW_DECKS);
//                    }
//                } else if (action.startsWith(ACTION_ANSWER)) {
//                    int ease = intent.getIntExtra(EXTRA_EASE, 0);
//                    if (ease == 0) {
//                        updateViews(VIEW_SHOW_ANSWER);
//                    } else {
//                        if (contentService.mCol != null) {
//                            if (contentService.mCurrentCard.timeTaken() > 12) {
//                                // assume, user was not learning
//                                contentService.mCurrentCard.setTimer(Utils.now() - 6);
//                            }
//                            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler,
//                                    new DeckTask.TaskData(contentService.mCol.getSched(), contentService.mCurrentCard,
//                                            ease));
//                        } else {
//                            updateViews(VIEW_DECKS);
//                        }
//                    }
//                } else if (ACTION_SHOW_RESTRICTIONS_DIALOG.equals(action)) {
//                    Intent dialogIntent = new Intent(this, WidgetDialog.class);
//                    dialogIntent.setAction(WidgetDialog.ACTION_SHOW_RESTRICTIONS_DIALOG);
//                    dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    this.startActivity(dialogIntent);
//                } else if (ACTION_OPEN.equals(action)) {
//                    // String deckpath = intent.getStringExtra(EXTRA_DECK_PATH);
//                    // Intent newIntent = StudyOptions.getLoadDeckIntent(this, deckpath);
//                    // if (deckpath != null) {
//                    // DeckManager.getDeck(deckpath, true, DeckManager.REQUESTING_ACTIVITY_STUDYOPTIONS);
//                    // if (contentService.mBigCurrentView != VIEW_NOTHING_DUE) {
//                    // newIntent.putExtra(StudyOptions.EXTRA_START, StudyOptions.EXTRA_START_REVIEWER);
//                    // startActivity(newIntent);
//                    // showProgressDialog();
//                    // } else {
//                    // startActivity(newIntent);
//                    // }
//                    // } else {
//                    // newIntent.putExtra(StudyOptions.EXTRA_START, StudyOptions.EXTRA_START_DECKPICKER);
//                    // startActivity(newIntent);
//                    // }
//                } else if (ACTION_CARDEDITOR.equals(action)) {
//                    // Intent editIntent = new Intent(this, CardEditor.class);
//                    // editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    // editIntent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_BIGWIDGET_EDIT);
//                    // // editIntent.putExtra(CardEditor.EXTRA_DECKPATH, contentService.mCol.getDeckPath());
//                    // this.startActivity(editIntent);
//                } else if (ACTION_FACTADDER.equals(action)) {
//                    // Intent editIntent = new Intent(this, CardEditor.class);
//                    // editIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    // editIntent.putExtra(CardEditor.EXTRA_CALLER, CardEditor.CALLER_BIGWIDGET_ADD);
//                    // editIntent.putExtra(CardEditor.EXTRA_DECKPATH, contentService.mCol.getDeckPath());
//                    // this.startActivity(editIntent);
//                } else if (ACTION_HELP.equals(action)) {
//                    if (contentService.mBigShowProgressDialog) {
//                        contentService.mBigShowProgressDialog = false;
//                        updateViews(VIEW_SHOW_HELP);
//                        contentService.mBigShowProgressDialog = true;
//                    } else {
//                        updateViews(VIEW_SHOW_HELP);
//                    }
//                } else if (ACTION_LEARN_MORE.equals(action)) {
//                    // if (contentService.mCol != null) {
//                    // contentService.mCol.setupLearnMoreScheduler();
//                    // contentService.mCol.reset();
//                    // contentService.setCard();
//                    // contentService.mBigShowProgressDialog = false;
//                    // contentService.mBigCurrentMessage = null;
//                    // updateViews(VIEW_SHOW_QUESTION);
//                    // } else {
//                    // updateViews(VIEW_DECKS);
//                    // }
//                } else if (ACTION_REVIEW_EARLY.equals(action)) {
//                    // if (contentService.mCol != null) {
//                    // contentService.mCol.setupReviewEarlyScheduler();
//                    // contentService.mCol.reset();
//                    // contentService.setCard();
//                    // contentService.mBigShowProgressDialog = false;
//                    // contentService.mBigCurrentMessage = null;
//                    // updateViews(VIEW_SHOW_QUESTION);
//                    // } else {
//                    // updateViews(VIEW_DECKS);
//                    // }
//                } else if (ACTION_SHOW_TOMORROW_DUES.equals(action)) {
//                    if (contentService.mTomorrowDues == null) {
//                        showProgressDialog();
//                        contentService.mWaitForAsyncTask = true;
//                        AsyncTask<String, Void, DeckStatus[]> getTomorrowDuesAsyncTask = new GetTomorrowDueAsyncTask();
//                        getTomorrowDuesAsyncTask.execute(AnkiDroidApp.getSharedPrefs(
//                                AnkiDroidWidgetBig.UpdateService.this).getString("deckPath",
//                                AnkiDroidApp.getStorageDirectory() + "/AnkiDroid"));
//                    } else {
//                        updateViews(VIEW_CONGRATS);
//                    }
//                }
//            }
//        }
//
//
//        private void handleError() {
//            // TODO
//            // Intent newIntent = DeckPicker.getLoadDeckIntent(AnkiDroidWidgetBig.UpdateService.this, 0);
//            // newIntent.putExtra(StudyOptions.EXTRA_START, StudyOptions.EXTRA_DB_ERROR);
//            // startActivity(newIntent);
//            // contentService.mBigCurrentMessage = null;
//            // DeckManager.closeDeck(deckPath);
//            // contentService.mCol = null;
//            // contentService.setCard(null);
//            // contentService.mBigShowProgressDialog = false;
//            // updateViews(VIEW_DECKS);
//        }
//
//
//        private void showProgressDialog() {
//            Log.i(AnkiDroidApp.TAG, "BigWidget: show progress dialog");
//            contentService.mBigShowProgressDialog = true;
//            updateViews();
//        }
//
//
//        private void updateViews(int view) {
//            if (view != VIEW_NOT_SPECIFIED) {
//                contentService.mBigCurrentView = view;
//            }
//            updateViews();
//        }
//
//
//        private void updateViews() {
//            RemoteViews updateViews = buildUpdate(this);
//
//            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidgetBig.class);
//            AppWidgetManager manager = AppWidgetManager.getInstance(this);
//            manager.updateAppWidget(thisWidget, updateViews);
//        }
//
//
//        private synchronized RemoteViews buildUpdate(Context context) {
//            Resources res = getResources();
//            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_big);
//
//            if (contentService == null) {
//                updateViews.setViewVisibility(R.id.widget_big_progressbar, View.VISIBLE);
//                updateViews.setViewVisibility(R.id.widget_big_noclicks, View.VISIBLE);
//                return updateViews;
//            }
//
//            if (contentService.mBigCurrentView == VIEW_NOT_SPECIFIED) {
//                contentService.mBigCurrentView = VIEW_DECKS;
//            }
//
//            Log.i(AnkiDroidApp.TAG, "BigWidget: buildUpdate (" + contentService.mBigCurrentView + ")");
//
//            if (contentService.mBigCurrentView == VIEW_DECKS || contentService.mBigCurrentView == VIEW_SHOW_HELP
//                    || contentService.mBigCurrentView == VIEW_CONGRATS) {
//            } else if (contentService.mCol == null) {
//                contentService.mBigCurrentView = VIEW_DECKS;
//            } else if (contentService.mCurrentCard == null) {
//                contentService.mBigCurrentView = VIEW_NOTHING_DUE;
//            }
//
//            if (contentService.mBigCurrentMessage != null) {
//                updateViews.setTextViewText(R.id.widget_big_message, contentService.mBigCurrentMessage);
//            } else {
//                updateViews.setTextViewText(R.id.widget_big_message, "");
//            }
//
//            if (contentService.mBigShowProgressDialog) {
//                updateViews.setViewVisibility(R.id.widget_big_progressbar, View.VISIBLE);
//                updateViews.setViewVisibility(R.id.widget_big_noclicks, View.VISIBLE);
//                return updateViews;
//            } else {
//                updateViews.setViewVisibility(R.id.widget_big_progressbar, View.INVISIBLE);
//                updateViews.setViewVisibility(R.id.widget_big_noclicks, View.INVISIBLE);
//            }
//
//            switch (contentService.mBigCurrentView) {
//                case VIEW_CONGRATS:
//                case VIEW_DECKS:
//                    updateViews.setTextViewText(R.id.widget_big_deckname, "");
//                    updateCounts(updateViews, contentService.mBigCurrentView);
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_deckfield,
//                            contentService.mBigCurrentView == VIEW_DECKS ? getTomorrowDuePendingIntent(context)
//                                    : getUpdatePendingIntent(context, VIEW_DECKS));
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_openclose,
//                            getShowDeckSelectionPendingIntent(context));
//                    updateViews.setViewVisibility(R.id.widget_big_open, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_close, View.INVISIBLE);
//
//                    updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_help, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_empty, getHelpPendingIntent(this));
//
//                    updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.VISIBLE);
//                    updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
//                    updateViews.setViewVisibility(R.id.widget_big_card_areas, View.INVISIBLE);
//                    emptyCardAreaTexts(updateViews);
//                    removeAreaListeners(this, true);
//
//                    updateViews.setViewVisibility(R.id.widget_big_add, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_star, View.VISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottom_right, getOpenPendingIntent(this, 0));
//                    break;
//
//                case VIEW_SHOW_HELP:
//                    updateViews
//                            .setOnClickPendingIntent(R.id.widget_big_empty, getUpdatePendingIntent(this, VIEW_DECKS));
//
//                    updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
//
//                    updateViews.setViewVisibility(R.id.widget_big_card_areas, View.VISIBLE);
//                    String[] values = res.getStringArray(R.array.gestures_labels);
//                    updateViews.setTextViewText(R.id.widget_big_topleft, res.getString(R.string.open_in_reviewer));
//                    updateViews.setTextViewText(R.id.widget_big_middleleft, values[12]);
//                    updateViews.setTextViewText(R.id.widget_big_bottomleft, values[1]);
//                    updateViews.setTextViewText(R.id.widget_big_topright, values[9]);
//                    updateViews.setTextViewText(R.id.widget_big_middleright, values[7]);
//                    updateViews.setTextViewText(R.id.widget_big_bottomright, values[5]);
//                    break;
//
//                case VIEW_SHOW_QUESTION:
//                    try {
//                        updateViews.setTextViewText(R.id.widget_big_deckname, contentService.mCol.getDecks().current()
//                                .getString("name"));
//                    } catch (JSONException e1) {
//                        throw new RuntimeException(e1);
//                    }
//                    updateCounts(updateViews, contentService.mBigCurrentView);
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, getCloseDeckPendingIntent(context));
//                    updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_close, View.VISIBLE);
//
//                    updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_help, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_flipcard, View.VISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_empty, getAnswerPendingIntent(context, 0));
//
//                    updateViews.setViewVisibility(R.id.widget_big_card_areas, View.VISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_topleft,
//                            getOpenPendingIntent(this, contentService.mCol.getDecks().selected()));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_middleleft, getBuryCardPendingIntent(this));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, getAnswerPendingIntent(this, 0));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_topright, getCardEditorPendingIntent(this));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_middleright, getUndoPendingIntent(this));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright, getAnswerPendingIntent(this, 0));
//                    emptyCardAreaTexts(updateViews);
//
//                    updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.INVISIBLE);
//
//                    updateViews.setTextViewText(R.id.widget_big_cardcontent,
//                            Html.fromHtml(contentService.mCurrentCard.getQuestion(true)));
//
//                    updateViews.setViewVisibility(R.id.widget_big_add, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_star, View.INVISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottom_right,
//                            getFactAdderPendingIntent(context));
//                    break;
//
//                case VIEW_SHOW_ANSWER:
//                    updateViews.setViewVisibility(R.id.widget_big_empty, View.INVISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_ease1, getAnswerPendingIntent(context, 1));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_ease2, getAnswerPendingIntent(context, 2));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_ease3, getAnswerPendingIntent(context, 3));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_ease4, getAnswerPendingIntent(context, 4));
//
//                    updateViews
//                            .setTextViewText(R.id.widget_big_message, getNextTimeString(contentService.mCurrentCard));
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottomleft, getAnswerPendingIntent(this, 1));
//
//                    if (contentService.mCol.getSched().answerButtons(contentService.mCurrentCard) == 4) {
//                        updateViews.setViewVisibility(R.id.widget_big_ease2_normal, View.INVISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.VISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease3_normal, View.INVISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.VISIBLE);
//                        updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright,
//                                getAnswerPendingIntent(this, 3));
//                    } else {
//                        updateViews.setViewVisibility(R.id.widget_big_ease2_normal, View.VISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease2_rec, View.INVISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease3_normal, View.VISIBLE);
//                        updateViews.setViewVisibility(R.id.widget_big_ease3_rec, View.INVISIBLE);
//                        updateViews.setOnClickPendingIntent(R.id.widget_big_bottomright,
//                                getAnswerPendingIntent(this, 2));
//                    }
//
//                    updateViews.setTextViewText(R.id.widget_big_cardcontent,
//                            Html.fromHtml(contentService.mCurrentCard.getAnswer(true)));
//                    break;
//
//                case VIEW_NOTHING_DUE:
//                    try {
//                        updateViews.setTextViewText(R.id.widget_big_deckname, contentService.mCol.getDecks().current()
//                                .getString("name"));
//                    } catch (JSONException e) {
//                        throw new RuntimeException(e);
//                    }
//                    updateCounts(updateViews, contentService.mBigCurrentView);
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_openclose, getCloseDeckPendingIntent(context));
//                    updateViews.setViewVisibility(R.id.widget_big_open, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_close, View.VISIBLE);
//
//                    updateViews.setViewVisibility(R.id.widget_big_empty, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_flipcard, View.INVISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_help, View.INVISIBLE);
//                    getHelpPendingIntent(this).cancel();
//                    getUpdatePendingIntent(this, VIEW_DECKS).cancel();
//
//                    updateViews.setViewVisibility(R.id.widget_big_card_areas, View.VISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_topleft,
//                            getOpenPendingIntent(this, contentService.mCol.getDecks().selected()));
//                    removeAreaListeners(this, false);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_middleright, getUndoPendingIntent(this));
//                    emptyCardAreaTexts(updateViews);
//
//                    updateViews.setViewVisibility(R.id.widget_big_cardbackgroundstar, View.VISIBLE);
//                    updateViews.setTextViewText(R.id.widget_big_cardcontent, "");
//                    updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
//
//                    // TODO
//                    // int failedCards = contentService.mCol.getFailedDelayedCount();
//                    // int revCards = contentService.mCol.getNextDueCards(1);
//                    // int newCards = contentService.mCol.getNextNewCards();
//                    // int eta = contentService.mCol.getETA(failedCards, revCards, newCards, true);
//                    // updateViews.setTextViewText(R.id.widget_big_congrats, StudyOptions.getCongratsMessage(this,
//                    // failedCards, revCards, newCards, eta));
//
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_review_early,
//                            getReviewEarlyPendingIntent(context));
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_learn_more, getLearnMorePendingIntent(context));
//
//                    updateViews.setViewVisibility(R.id.widget_big_add, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_star, View.INVISIBLE);
//                    updateViews.setOnClickPendingIntent(R.id.widget_big_bottom_right,
//                            getFactAdderPendingIntent(context));
//                    break;
//            }
//            return updateViews;
//
//            // WebView webView = new WebView(this);
//            // webView.setDrawingCacheEnabled(true);
//            // webView.layout(0, 0, 500, 500);
//            // webView.loadDataWithBaseURL("", "asdf", "text/html", "utf-8", null);
//            // webView.buildDrawingCache(true);
//            // Bitmap b = Bitmap.createBitmap(webView.getDrawingCache());
//            // webView.setDrawingCacheEnabled(false); // clear drawing cache
//            //
//            // FileOutputStream out;
//            // try {
//            // out = new FileOutputStream("/emmc/test.png");
//            // b.compress(Bitmap.CompressFormat.PNG, 90, out);
//            // File f = new File("/emmc/test.png");
//            // Uri uri = Uri.fromFile(f);
//            // updateViews.setImageViewUri(R.id.widget_big_cardcontent, uri);
//            // } catch (FileNotFoundException e) {
//            // // TODO Auto-generated catch block
//            // e.printStackTrace();
//            // }
//        }
//
//
//        private void updateCounts(RemoteViews updateViews, int view) {
//            int[] counts = WidgetStatus.fetchSmall(this);
//            int progressTotal = counts[1];
//            int eta = counts[2];
//            updateViews.setProgressBar(R.id.widget_big_progress_total, 100, progressTotal, false);
//            if (eta == 0) {
//                updateViews.setViewVisibility(R.id.widget_big_eta, View.INVISIBLE);
//            } else {
//                updateViews.setViewVisibility(R.id.widget_big_eta, View.VISIBLE);
//                updateViews.setTextViewText(R.id.widget_big_eta,
//                        eta > 99 ? "\u2027\u2027\u2027" : Integer.toString(eta));
//            }
//
//            switch (view) {
//                case VIEW_SHOW_QUESTION:
//                case VIEW_NOTHING_DUE:
//                    updateViews.setTextViewText(R.id.widget_big_counts,
//                            getDeckStatusString(contentService.mCurrentCard));
//                    double sessionProgress = contentService.mCol.getSched().todaysProgress(contentService.mCurrentCard,
//                            false, false);
//                    if (sessionProgress == -1) {
//                        updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.INVISIBLE);
//                    } else {
//                        updateViews.setProgressBar(R.id.widget_big_progress_deck, 100, (int) (sessionProgress * 100),
//                                false);
//                        updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.VISIBLE);
//                    }
//                    contentService.mTomorrowDues = null;
//                    break;
//
//                case VIEW_CONGRATS:
//                    if (contentService.mTomorrowDues != null && contentService.mTomorrowDues.length > 0) {
//                        updateViews.setViewVisibility(R.id.widget_big_totalcongrats, View.VISIBLE);
//                        setDeckCounts(updateViews, contentService.mTomorrowDues);
//                    }
//                    break;
//
//                case VIEW_DECKS:
//                    updateViews.setTextViewText(R.id.widget_big_counts, "");
//                    updateViews.setViewVisibility(R.id.widget_big_progress_frame_deck, View.INVISIBLE);
//
//                    updateViews.setViewVisibility(R.id.widget_big_deckfield, View.VISIBLE);
//                    updateViews.setViewVisibility(R.id.widget_big_nothing_due, View.INVISIBLE);
//
//                    setDeckCounts(updateViews, WidgetStatus.fetch(this));
//
//                    updateViews.setViewVisibility(R.id.widget_big_totalcongrats, View.GONE);
//                    updateViews.setViewVisibility(R.id.widget_big_decketa, View.VISIBLE);
//                    updateViews.setTextViewText(R.id.widget_big_decketa,
//                            "─────\n" + getResources().getQuantityString(R.plurals.widget_big_eta, eta, eta));
//                    break;
//            }
//        }
//
//
//        private void emptyCardAreaTexts(RemoteViews updateViews) {
//            updateViews.setTextViewText(R.id.widget_big_topleft, "");
//            updateViews.setTextViewText(R.id.widget_big_middleleft, "");
//            updateViews.setTextViewText(R.id.widget_big_bottomleft, "");
//            updateViews.setTextViewText(R.id.widget_big_topright, "");
//            updateViews.setTextViewText(R.id.widget_big_middleright, "");
//            updateViews.setTextViewText(R.id.widget_big_bottomright, "");
//        }
//
//
//        private void removeAreaListeners(Context context, boolean all) {
//            getBuryCardPendingIntent(context).cancel();
//            getAnswerPendingIntent(context, 0).cancel();
//            getAnswerPendingIntent(context, 1).cancel();
//            getAnswerPendingIntent(context, 2).cancel();
//            getAnswerPendingIntent(context, 3).cancel();
//            getCardEditorPendingIntent(context).cancel();
//            if (all) {
//                getOpenPendingIntent(context, 0).cancel();
//                getUndoPendingIntent(context).cancel();
//            }
//        }
//
//
//        private void setDeckCounts(RemoteViews updateViews, DeckStatus[] decks) {
//            StringBuilder namesSb = new StringBuilder();
//            SpannableStringBuilder duesSb = new SpannableStringBuilder();
//            int eta = 0;
//            for (DeckStatus d : decks) {
//                namesSb.append(d.mDeckName).append("  \n");
//                duesSb.append(getDeckStatusString(d.mNewCards, d.mLrnCards, d.mDueCards, null)).append("\n");
//                eta += d.mEta;
//            }
//            int pos = namesSb.length() - 1;
//            if (pos != -1) {
//                namesSb.delete(pos, pos + 1);
//            }
//            pos = duesSb.length() - 1;
//            if (pos != -1) {
//                duesSb.delete(pos, pos + 1);
//            }
//            if (namesSb.length() == 0 || duesSb.length() == 0) {
//                updateViews.setViewVisibility(R.id.widget_big_deckfield, View.INVISIBLE);
//                // if (!sUpdateStarted) {
//                // sUpdateStarted = true;
//                // WidgetStatus.update(sContext);
//                // }
//            } else {
//                // sUpdateStarted = false;
//            }
//            updateViews.setTextViewText(R.id.widget_big_decknames, namesSb);
//            updateViews.setTextViewText(R.id.widget_big_deckdues, duesSb);
//            updateViews.setTextViewText(R.id.widget_big_decketa,
//                    "─────\n" + getResources().getQuantityString(R.plurals.widget_big_eta, eta, eta));
//        }
//
//
//        private PendingIntent getAnswerPendingIntent(Context context, int ease) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_ANSWER + Integer.toString(ease));
//            ankiDroidIntent.putExtra(EXTRA_EASE, ease);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getShowDeckSelectionPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_OPENCOLLECTION);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getCloseDeckPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_CLOSEDECK);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getUndoPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_UNDO);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getBuryCardPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_BURY_CARD);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getOpenPendingIntent(Context context, long deckId) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_OPEN);
//            ankiDroidIntent.putExtra(DeckPicker.EXTRA_DECK_ID, deckId);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, PendingIntent.FLAG_CANCEL_CURRENT);
//        }
//
//
//        private PendingIntent getLearnMorePendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_LEARN_MORE);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getReviewEarlyPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_REVIEW_EARLY);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getCardEditorPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_CARDEDITOR);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getFactAdderPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_FACTADDER);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getHelpPendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_HELP);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getTomorrowDuePendingIntent(Context context) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_SHOW_TOMORROW_DUES);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, 0);
//        }
//
//
//        private PendingIntent getUpdatePendingIntent(Context context, int view) {
//            Intent ankiDroidIntent = new Intent(context, UpdateService.class);
//            ankiDroidIntent.setAction(ACTION_UPDATE);
//            ankiDroidIntent.putExtra(EXTRA_VIEW, view);
//            return PendingIntent.getService(context, 0, ankiDroidIntent, PendingIntent.FLAG_CANCEL_CURRENT);
//        }
//
//
//        private void showDeckSelectionDialog() {
//            Intent ankiDroidIntent = new Intent(UpdateService.this, WidgetDialog.class);
//            ankiDroidIntent.setAction(WidgetDialog.ACTION_SHOW_DECK_SELECTION_DIALOG);
//            ankiDroidIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(ankiDroidIntent);
//        }
//
//
//        @Override
//        public IBinder onBind(Intent arg0) {
//            Log.i(AnkiDroidApp.TAG, "onBind");
//            return null;
//        }
//
//        private static class GetTomorrowDueAsyncTask extends AsyncTask<String, Void, DeckStatus[]> {
//
//            @Override
//            protected DeckStatus[] doInBackground(String... params) {
//                Log.i(AnkiDroidApp.TAG, "doInBackgroundGetTomorrowDue");
//                //
//                // File dir = new File(params[0]);
//                // File[] fileList = dir.listFiles(new WidgetStatus.AnkiFileFilter());
//                //
//                // DeckStatus[] todayDues = WidgetStatus.fetch(sContext);
//                //
//                // ArrayList<DeckStatus> decks = new ArrayList<DeckStatus>(fileList.length);
//                //
//                // for (DeckStatus s : todayDues) {
//                // try {
//                // Decks deck = DeckManager.getDeck(s.mDeckPath, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET, false);
//                // if (deck != null) {
//                // int failedCards = deck.getFailedDelayedCount() + s.mFailedCards;
//                // int dueCards = deck.getNextDueCards(1) + s.mDueCards;
//                // int newCards = deck.getNextNewCards() + s.mNewCards;
//                // decks.add(new DeckStatus(null, deck.getDeckName(), newCards, dueCards, failedCards,
//                // deck.getETA(failedCards, dueCards, newCards, true), 0));
//                // }
//                // DeckManager.closeDeck(s.mDeckPath, DeckManager.REQUESTING_ACTIVITY_BIGWIDGET);
//                // } catch (RuntimeException e) {
//                // Log.e(AnkiDroidApp.TAG, "doInBackgroundGetTomorrowDue: an error occurred: " + e);
//                // }
//                // }
//                // return decks.toArray(new DeckStatus[0]);
//                return null;
//            }
//
//
//            @Override
//            protected void onPostExecute(DeckStatus[] status) {
//                Log.d(AnkiDroidApp.TAG, "DeckManager.CloseDeckAsyncTask.onPostExecute()");
//                contentService.mCol = null;
//                contentService.mCurrentCard = null;
//                contentService.mBigCurrentMessage = null;
//                contentService.mWaitForAsyncTask = false;
//                contentService.mTomorrowDues = status;
//                if (contentService.mBigShowProgressDialog) {
//                    contentService.mBigShowProgressDialog = false;
//                    updateWidget(VIEW_CONGRATS);
//                }
//                contentService.mBigShowProgressDialog = false;
//            }
//        }
//    }
//}
