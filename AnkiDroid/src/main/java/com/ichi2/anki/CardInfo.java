/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.Gravity;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Model;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Utils;
import com.ichi2.ui.FixedTextView;
import com.ichi2.utils.FunctionalInterfaces;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.utils.UiUtil;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import androidx.annotation.CheckResult;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

import static com.ichi2.libanki.stats.Stats.SECONDS_PER_DAY;

public class CardInfo extends AnkiActivity {
    
    private static final long INVALID_CARD_ID = -1;

    private static final DateFormat sDateFormat = DateFormat.getDateInstance();
    private static final DateFormat sDateTimeFormat = DateFormat.getDateTimeInstance();
    private CardInfoModel mModel;
    private long mCardId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return;
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.card_info);

        mCardId = getCardId(savedInstanceState);

        if (!hasValidCardId()) {
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), false);
            finishWithoutAnimation();
            return;
        }

        enableToolbar();

        startLoadingCollection();
    }


    @Override
    protected void onCollectionLoaded(Collection col) {
        super.onCollectionLoaded(col);

        Card c = getCard(col);

        if (c == null) {
            UIUtils.showThemedToast(this, getString(R.string.multimedia_editor_something_wrong), false);
            finishWithoutAnimation();
            return;
        }

        // Candidate to move to background thread - can get hundreds of rows for bad cards.
        CardInfoModel model = CardInfoModel.create(c, col);

        setText(R.id.card_info_added, formatDate(model.getAddedDate()));

        setIfNotNull(model.getFirstReviewDate(), R.id.card_info_first_review, R.id.card_info_first_review_label, this::formatDate);
        setIfNotNull(model.getLatestReviewDate(), R.id.card_info_latest_review, R.id.card_info_latest_review_label, this::formatDate);
        setIfNotNull(model.getDue(), R.id.card_info_due, R.id.card_info_due_label, s -> s);
        setIfNotNull(model.getInterval(), R.id.card_info_interval, R.id.card_info_interval_label, s -> getResources().getQuantityString(R.plurals.time_span_days, model.getInterval(), model.getInterval()));
        setIfNotNull(model.getEaseInPercent(), R.id.card_info_ease, R.id.card_info_ease_label, easePercent -> formatDouble("%.0f%%", easePercent * 100));
        setFormattedText(R.id.card_info_review_count, "%d", model.getReviews());
        setFormattedText(R.id.card_info_lapse_count, "%d", model.getLapses());
        setIfNotNull(model.getAverageTimeMs(), R.id.card_info_average_time, R.id.card_info_average_time_label, this::formatAsTimeSpan);
        setIfNotNull(model.getTotalTimeMs(), R.id.card_info_total_time, R.id.card_info_total_time_label, this::formatAsTimeSpan);
        setText(R.id.card_info_card_type, model.getCardType());
        setText(R.id.card_info_note_type, model.getNoteType());
        setText(R.id.card_info_deck_name, model.getDeckName());
        setFormattedText(R.id.card_info_card_id, "%d", model.getCardId());
        setFormattedText(R.id.card_info_note_id, "%d", model.getNoteId());

        TableLayout tl = findViewById(R.id.card_info_revlog_entries);

        for (CardInfoModel.RevLogEntry entry : model.getEntries()) {
            TableRow row = new TableRow(this);

            addWithText(row, formatDateTime(entry.dateTime)).setGravity(Gravity.START);
            addWithText(row, entry.spannableType(this)).setGravity(Gravity.CENTER_HORIZONTAL);
            addWithText(row, entry.getRating(this)).setGravity(Gravity.CENTER_HORIZONTAL);
            addWithText(row, Utils.timeQuantityNextIvl(this, entry.intervalAsTimeSeconds())).setGravity(Gravity.START);
            addWithText(row, entry.getEase(this)).setGravity(Gravity.CENTER_HORIZONTAL);
            addWithText(row, entry.getTimeTaken()).setGravity(Gravity.END);

            tl.addView(row);
        }

        this.mModel = model;
    }

    private FixedTextView addWithText(TableRow row, String value) {
        return addWithText(row, new SpannableString(value));
    }

    private FixedTextView addWithText(TableRow row, Spannable value) {
        FixedTextView text = new FixedTextView(this);
        text.setText(value);
        text.setTextSize(12f);
        row.addView(text);
        return text;
    }


    @NonNull
    private String formatAsTimeSpan(Long timeInMs) {
        // HACK: There is probably a bug here
        // It would be nice to use Utils.timeSpan, but the Android string formatting system does not support floats.
        // https://stackoverflow.com/questions/54882981/android-plurals-for-float-values
        // Mixing both float-based time processing and plural processing seems like a recipe for disaster until we have
        // a spec, so we ignore the problem for now

        // So, we use seconds
        return getString(R.string.time_span_decimal_seconds, String.format(getLocale(), "%.2f", timeInMs / 1000d));
    }


    private <T> void setIfNotNull(T nullableData, @IdRes int dataRes, @IdRes int labelRes, Function<T, String> asString) {
        if (nullableData == null) {
            findViewById(dataRes).setVisibility(View.GONE);
            findViewById(labelRes).setVisibility(View.GONE);
        } else {
            setText(dataRes, asString.apply(nullableData));
        }
    }


    private void setFormattedText(@IdRes int resource, String formatSpecifier, long number) {
        String text = formatLong(formatSpecifier, number);
        setText(resource, text);
    }


    @NonNull
    private String formatLong(String formatSpecifier, long number) {
        return String.format(getLocale(), formatSpecifier, number);
    }

    @NonNull
    private String formatDouble(String formatSpecifier, double number) {
        return String.format(getLocale(), formatSpecifier, number);
    }

    private Locale getLocale() {
        return LanguageUtil.getLocaleCompat(getResources());
    }


    private void setText(@IdRes int id, String text) {
        TextView view = findViewById(id);
        view.setText(text);
    }


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("cardId", mCardId);
    }


    @SuppressLint("DirectDateInstantiation")
    private String formatDate(Long date) {
        return sDateFormat.format(new Date(date));
    }

    @SuppressLint("DirectDateInstantiation")
    private String formatDateTime(long dateTime) {
        return sDateTimeFormat.format(new Date(dateTime));
    }

    @Nullable
    private Card getCard(Collection col) {
        return col.getCard(mCardId);
    }


    private boolean hasValidCardId() {
        return mCardId > 0;
    }


    private long getCardId(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return savedInstanceState.getLong("cardId");
        }
        try {
            return getIntent().getLongExtra("cardId", INVALID_CARD_ID);
        } catch (Exception e) {
            Timber.w(e, "Failed to get Card Id");
            return INVALID_CARD_ID;
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public CardInfoModel getModel() {
        return mModel;
    }

    public static class CardInfoModel {
        private final long mAddedDate;
        @Nullable
        private final Long mFirstReviewDate;
        @Nullable
        private final Long mLatestReviewDate;
        private final String mDue;
        @Nullable
        private final Integer mInterval;
        @Nullable
        private final Double mEaseInPercent;
        private final int mLapses;
        private final int mReviews;
        @Nullable
        private final Long mAverageTimeMs;
        @Nullable
        private final Long mTotalTimeMs;
        private final String mCardType;
        private final String mNoteType;
        private final String mDeckName;
        private final long mNoteId;
        private final List<RevLogEntry> mEntries;


        public CardInfoModel(long createdDate,
                             @Nullable Long firstReview,
                             @Nullable Long latestReview,
                             String due,
                             @Nullable Integer interval,
                             @Nullable Double easeInPercent,
                             int reviews,
                             int lapses,
                             @Nullable Long averageTime,
                             @Nullable Long totalTime,
                             String cardType,
                             String noteType,
                             String deckName,
                             long noteId,
                             List<RevLogEntry> entries) {
            this.mAddedDate = createdDate;
            this.mFirstReviewDate = firstReview;
            mLatestReviewDate = latestReview;
            mDue = due;
            mInterval = interval;
            mEaseInPercent = easeInPercent;
            mReviews = reviews;
            mLapses = lapses;
            mAverageTimeMs = averageTime;
            mTotalTimeMs = totalTime;
            mCardType = cardType;
            mNoteType = noteType;
            mDeckName = deckName;
            mNoteId = noteId;
            mEntries = entries;
        }


        @CheckResult
        public static CardInfoModel create(Card c, Collection collection) {
            long addedDate = c.getId();

            Long firstReview = collection.getDb().queryLongScalar("select min(id) from revlog where cid = ?", c.getId());
            if (firstReview == 0) {
                firstReview = null;
            }

            Long latestReview = collection.getDb().queryLongScalar("select max(id) from revlog where cid = ?", c.getId());
            if (latestReview == 0) {
                latestReview = null;
            }

            Long averageTime = collection.getDb().queryLongScalar("select avg(time) from revlog where cid = ?", c.getId());
            if (averageTime == 0) {
                averageTime = null;
            }

            Long totalTime = collection.getDb().queryLongScalar("select sum(time) from revlog where cid = ?", c.getId());
            if (totalTime == 0) {
                totalTime = null;
            }


            Double easeInPercent = c.getFactor() / 1000.0d;
            int lapses = c.getLapses();
            int reviews = c.getReps();
            Model model = collection.getModels().get(c.note().getMid());
            String cardType = getCardType(c, model);
            String noteType = model.getString("name");
            String deckName = collection.getDecks().get(c.getDid()).getString("name");
            long noteId = c.getNid();

            Integer interval = c.getIvl();
            if (interval <= 0) {
                interval = null;
            }


            if (c.getType() < Consts.CARD_TYPE_REV) {
                easeInPercent = null;
            }

            String due = c.getDueString();

            List<RevLogEntry> entries = new ArrayList<>(collection.getDb().queryScalar("select count() from revlog where cid = ?", c.getId()));

            try (Cursor cur = collection.getDb().query("select " +
                    "id as dateTime, " +
                    "ease as rating, " +
                    "ivl, " +
                    "factor as ease, " +
                    "time, " +
                    "type " +
                    "from revlog where cid = ?" +
                    "order by id desc", c.getId())) {
                while (cur.moveToNext()) {
                    RevLogEntry e = new RevLogEntry();
                    e.dateTime = cur.getLong(0);
                    e.rating = cur.getInt(1);
                    e.ivl = cur.getLong(2);
                    e.factor = cur.getLong(3);
                    e.timeTakenMs = cur.getLong(4);
                    e.type = cur.getInt(5);
                    entries.add(e);
                }


            }

            return new CardInfoModel(addedDate, firstReview, latestReview, due, interval, easeInPercent, reviews, lapses, averageTime, totalTime, cardType, noteType, deckName, noteId, entries);
        }


        @NonNull
        protected static String getCardType(Card c, Model model) {
            try {
                int ord = c.getOrd();
                if (c.model().isCloze()) {
                    ord = 0;
                }
                return model.getJSONArray("tmpls").getJSONObject(ord).getString("name");
            } catch (Exception e) {
                Timber.w(e);
                return null;
            }
        }


        public long getAddedDate() {
            return mAddedDate;
        }


        @Nullable
        public Long getFirstReviewDate() {
            return mFirstReviewDate;
        }

        @Nullable
        public Long getLatestReviewDate() {
            return mLatestReviewDate;
        }

        @Nullable
        public String getDue() {
            return mDue;
        }

        @Nullable
        public Integer getInterval() {
            return mInterval;
        }

        @Nullable
        public Double getEaseInPercent() {
            return mEaseInPercent;
        }

        public int getReviews() {
            return mReviews;
        }

        public int getLapses() {
            return mLapses;
        }

        @Nullable
        public Long getAverageTimeMs() {
            return mAverageTimeMs;
        }

        @Nullable
        public Long getTotalTimeMs() {
            return mTotalTimeMs;
        }

        public String getCardType() {
            return mCardType;
        }

        public String getNoteType() {
            return mNoteType;
        }

        public String getDeckName() {
            return mDeckName;
        }

        public long getCardId() {
            return mAddedDate;
        }

        public long getNoteId() {
            return mNoteId;
        }


        public List<RevLogEntry> getEntries() {
            return mEntries;
        }


        // date type rating interval ease time
        public static class RevLogEntry {
            public long dateTime;
            public int type;
            public int rating;
            public long ivl;
            public long factor;
            public long timeTakenMs;


            public Spannable spannableType(Context context) {

                int[] attrs = new int[] {
                        R.attr.newCountColor,
                        R.attr.learnCountColor,
                        R.attr.reviewCountColor};
                TypedArray ta = context.obtainStyledAttributes(attrs);
                int newCountColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black));
                int learnCountColor = ta.getColor(1, ContextCompat.getColor(context, R.color.black));
                int reviewCountColor = ta.getColor(2, ContextCompat.getColor(context, R.color.black));
                int filteredColor = ContextCompat.getColor(context, R.color.material_orange_A700);
                ta.recycle();

                switch (type) {
                    case Consts.REVLOG_LRN:
                        return UiUtil.makeColored(context.getString(R.string.card_info_revlog_learn), newCountColor);
                    case Consts.REVLOG_REV:
                        return UiUtil.makeColored(context.getString(R.string.card_info_revlog_review), reviewCountColor);
                    case Consts.REVLOG_RELRN:
                        return UiUtil.makeColored(context.getString(R.string.card_info_revlog_relearn), learnCountColor);
                    case Consts.REVLOG_CRAM:
                        return UiUtil.makeColored(context.getString(R.string.card_info_revlog_filtered), filteredColor);
                    default:
                        return new SpannableString(Integer.toString(type));
                }
            }

            public Spannable getEase(Context context) {
                if (factor == 0) {
                    return new SpannableString(context.getString(R.string.card_info_ease_not_applicable));
                } else {
                    return new SpannableString(Long.toString(factor / 10));
                }
            }

            public long intervalAsTimeSeconds() {
                if (ivl < 0) {
                    return -ivl;
                }

                return ivl * SECONDS_PER_DAY;
            }

            public String getTimeTaken() {
                // saves space if we just use seconds rather than a "s" suffix
                //return Utils.timeQuantityNextIvl(context, timeTakenMs / 1000);
                return Long.toString(timeTakenMs / 1000);
            }


            public Spannable getRating(Context context) {
                String source = Long.toString(rating);

                if (rating == 1) {
                    int[] attrs = new int[] { R.attr.learnCountColor };
                    TypedArray ta = context.obtainStyledAttributes(attrs);
                    int failColor = ta.getColor(0, ContextCompat.getColor(context, R.color.black));
                    ta.recycle();
                    return UiUtil.makeColored(source, failColor);
                } else {
                    return new SpannableString(source);
                }
            }
        }
    }
}
