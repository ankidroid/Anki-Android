/****************************************************************************************
 * Copyright (c) 2009 Daniel Sv�rd <daniel.svard@gmail.com>                             *
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

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * A card is a presentation of a fact, and has two sides: a question and an answer. Any number of fields can appear on
 * each side. When you add a fact to Anki, cards which show that fact are generated. Some models generate one card,
 * others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class Card {

    // TODO: Javadoc.

    /**
     * Tag for logging messages
     */
    private static String TAG = "AnkiDroid";

    /** Tags src constants */
    public static final int TAGS_FACT = 0;
    public static final int TAGS_MODEL = 1;
    public static final int TAGS_TEMPL = 2;

    // BEGIN SQL table entries
    long id; // Primary key
    long factId; // Foreign key facts.id
    long cardModelId; // Foreign key cardModels.id
    double created = System.currentTimeMillis() / 1000.0;
    double modified = System.currentTimeMillis() / 1000.0;
    String tags = "";
    int ordinal;
    // Cached - changed on fact update
    String question = "";
    String answer = "";
    // Default to 'normal' priority
    // This is indexed in deck.java as we need to create a reverse index
    int priority = 2;
    double interval = 0;
    double lastInterval = 0;
    double due = System.currentTimeMillis() / 1000.0;
    double lastDue = 0;
    double factor = 2.5;
    double lastFactor = 2.5;
    double firstAnswered = 0;
    // Stats
    int reps = 0;
    int successive = 0;
    double averageTime = 0;
    double reviewTime = 0;
    int youngEase0 = 0;
    int youngEase1 = 0;
    int youngEase2 = 0;
    int youngEase3 = 0;
    int youngEase4 = 0;
    int matureEase0 = 0;
    int matureEase1 = 0;
    int matureEase2 = 0;
    int matureEase3 = 0;
    int matureEase4 = 0;
    // This duplicates the above data, because there's no way to map imported
    // data to the above
    int yesCount = 0;
    int noCount = 0;
    double spaceUntil = 0;
    // relativeDelay is reused as type without scheduling (ie, it remains 0-2 even if card is suspended, etc)
    int relativeDelay = 0;
    int isDue = 0;              // obsolete in libanki 1.1
    int type = 2;
    double combinedDue = 0;
    // END SQL table entries

    Deck deck;

    // BEGIN JOINed variables
    @SuppressWarnings("unused")
    private CardModel cardModel;
    Fact fact;
    private String[] tagsBySrc;
    // END JOINed variables

    double timerStarted;
    double timerStopped;
    double fuzz;

    // Leech flags, not read from database, only set to true during the actual suspension
    private boolean isLeechMarked;
    private boolean isLeechSuspended;

    public Card(Deck deck, Fact fact, CardModel cardModel, double created) {
        tags = "";
        tagsBySrc = new String[3];
        tagsBySrc[TAGS_FACT] = "";
        tagsBySrc[TAGS_MODEL] = "";
        tagsBySrc[TAGS_TEMPL] = "";

        id = Utils.genID();
        // New cards start as new & due
        type = 2;
        relativeDelay = type;
        timerStarted = Double.NaN;
        timerStopped = Double.NaN;
        modified = System.currentTimeMillis() / 1000.0;
        if (created != Double.NaN) {
            this.created = created;
            due = created;
        } else {
            due = modified;
        }
        isLeechSuspended = false;
        combinedDue = due;
        this.deck = deck;
        this.fact = fact;
        this.cardModel = cardModel;
        if (cardModel != null) {
            cardModelId = cardModel.id;
            ordinal = cardModel.ordinal;
            /*
             * FIXME: what is the code below used for? It is never persisted Additionally, cardModel has no accessor.
             * HashMap<String, HashMap<Long, String>> d = new HashMap<String, HashMap<Long, String>>();
             * Iterator<FieldModel> iter = fact.model.fieldModels.iterator(); while (iter.hasNext()) { FieldModel fm =
             * iter.next(); HashMap<Long, String> field = new HashMap<Long, String>(); field.put(fm.id,
             * fact.getFieldValue(fm.name)); d.put(fm.name, field); }
             */
            // HashMap<String, String> qa = CardModel.formatQA(id, fact.modelId, d, splitTags(), cardModel);
            // question = qa.get("question");
            // answer = qa.get("answer");
        }
    }


    public Card(Deck deck) {
        this(deck, null, null, Double.NaN);
    }


    public Fact getFact() {
        if (fact != null) {
            return fact;
        } else {
            fact = new Fact(deck, factId);
            return fact;
        }
    }


    public void setModified() {
        modified = System.currentTimeMillis() / 1000.0;
    }


    public void startTimer() {
        timerStarted = System.currentTimeMillis() / 1000.0;
    }


    public void stopTimer() {
        timerStopped = System.currentTimeMillis() / 1000.0;
    }


    public double thinkingTime() {
        if (Double.isNaN(timerStopped)) {
            return (System.currentTimeMillis() / 1000.0) - timerStarted;
        } else {
            return timerStopped - timerStarted;
        }
    }


    public double totalTime() {
        return (System.currentTimeMillis() / 1000.0) - timerStarted;
    }


    public void genFuzz() {
        Random rand = new Random();
        fuzz = 0.95 + (0.1 * rand.nextDouble());
    }


    public String htmlQuestion(String type, boolean align) {
        return null;
    }


    public String htmlAnswer(boolean align) {
        return htmlQuestion("answer", align);
    }


    public void updateStats(int ease, String state) {
        reps += 1;
        if (ease > 1) {
            successive += 1;
        } else {
            successive = 0;
        }

        double delay = totalTime();
        // Ignore any times over 60 seconds
        if (delay < 60) {
            reviewTime += delay;
            if (averageTime != 0) {
                averageTime = (averageTime + delay) / 2.0;
            } else {
                averageTime = delay;
            }
        }
        // We don't track first answer for cards
        if ("new".equalsIgnoreCase(state)) {
            state = "young";
        }
        // Update ease and yes/no count
        String attr = state + String.format("Ease%d", ease);
        try {
            Field f = this.getClass().getDeclaredField(attr);
            f.setInt(this, f.getInt(this) + 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ease < 2) {
            noCount += 1;
        } else {
            yesCount += 1;
        }
        if (firstAnswered == 0) {
            firstAnswered = System.currentTimeMillis() / 1000.0;
        }
        setModified();
    }


    public String[] splitTags() {
        String[] tags = new String[]{
            getFact().tags,
            Model.getModel(deck, getFact().modelId, true).tags,
            getCardModel().name
        };
        return tags;
    }


    public String allTags() {
        // Non-Canonified string of fact and model tags
        if ((tagsBySrc[TAGS_FACT].length() > 0) && (tagsBySrc[TAGS_MODEL].length() > 0)) {
            return tagsBySrc[TAGS_FACT] + "," + tagsBySrc[TAGS_MODEL];
        } else if (tagsBySrc[TAGS_FACT].length() > 0) {
            return tagsBySrc[TAGS_FACT];
        } else {
            return tagsBySrc[TAGS_MODEL];
        }
    }


    public boolean hasTag(String tag) {
        return (allTags().indexOf(tag) != -1);
    }


    // FIXME: Should be removed. Calling code should directly interact with Model
    public CardModel getCardModel() {
        Model myModel = Model.getModel(deck, cardModelId, false);
        return myModel.getCardModel(cardModelId);
    }


    // Loading tags for this card. Needed when:
    // - we modify the card fields and need to update question and answer.
    // - we check is a card is marked
    public void loadTags() {
        Cursor cursor = null;

        int tagSrc = 0;

        // Flush tags
        for (int i = 0; i < tagsBySrc.length; i++) {
            tagsBySrc[i] = "";
        }

        try {
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery("SELECT tags.tag, cardTags.src "
                    + "FROM cardTags JOIN tags ON cardTags.tagId = tags.id " + "WHERE cardTags.cardId = " + id
                    + " AND cardTags.src in (" + TAGS_FACT + ", " + TAGS_MODEL + "," + TAGS_TEMPL + ") "
                    + "ORDER BY cardTags.id", null);
            while (cursor.moveToNext()) {
                tagSrc = cursor.getInt(1);
                if (tagsBySrc[tagSrc].length() > 0) {
                    tagsBySrc[tagSrc] += "," + cursor.getString(0);
                } else {
                    tagsBySrc[tagSrc] += cursor.getString(0);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }


    public boolean fromDB(long id) {
        Cursor cursor = null;

        try {
            cursor = AnkiDatabaseManager.getDatabase(deck.deckPath).database.rawQuery(
                    "SELECT id, factId, cardModelId, created, modified, tags, "
                            + "ordinal, question, answer, priority, interval, lastInterval, "
                            + "due, lastDue, factor, lastFactor, firstAnswered, reps, "
                            + "successive, averageTime, reviewTime, youngEase0, youngEase1, "
                            + "youngEase2, youngEase3, youngEase4, matureEase0, matureEase1, "
                            + "matureEase2, matureEase3, matureEase4, yesCount, noCount, "
                            + "spaceUntil, isDue, type, combinedDue " + "FROM cards " + "WHERE id = " + id, null);
            if (!cursor.moveToFirst()) {
                Log.w("anki", "Card.java (fromDB(id)): No result from query.");
                return false;
            }

            this.id = cursor.getLong(0);
            factId = cursor.getLong(1);
            cardModelId = cursor.getLong(2);
            created = cursor.getDouble(3);
            modified = cursor.getDouble(4);
            tags = cursor.getString(5);
            ordinal = cursor.getInt(6);
            question = cursor.getString(7);
            answer = cursor.getString(8);
            priority = cursor.getInt(9);
            interval = cursor.getDouble(10);
            lastInterval = cursor.getDouble(11);
            due = cursor.getDouble(12);
            lastDue = cursor.getDouble(13);
            factor = cursor.getDouble(14);
            lastFactor = cursor.getDouble(15);
            firstAnswered = cursor.getDouble(16);
            reps = cursor.getInt(17);
            successive = cursor.getInt(18);
            averageTime = cursor.getDouble(19);
            reviewTime = cursor.getDouble(20);
            youngEase0 = cursor.getInt(21);
            youngEase1 = cursor.getInt(22);
            youngEase2 = cursor.getInt(23);
            youngEase3 = cursor.getInt(24);
            youngEase4 = cursor.getInt(25);
            matureEase0 = cursor.getInt(26);
            matureEase1 = cursor.getInt(27);
            matureEase2 = cursor.getInt(28);
            matureEase3 = cursor.getInt(29);
            matureEase4 = cursor.getInt(30);
            yesCount = cursor.getInt(31);
            noCount = cursor.getInt(32);
            spaceUntil = cursor.getDouble(33);
            isDue = cursor.getInt(34);
            type = cursor.getInt(35);
            combinedDue = cursor.getDouble(36);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // TODO: Should also read JOINed entries CardModel and Fact.

        return true;
    }


    public void toDB() {

        ContentValues values = new ContentValues();
        values.put("factId", factId);
        values.put("cardModelId", cardModelId);
        values.put("created", created);
        values.put("modified", modified);
        values.put("tags", tags);
        values.put("ordinal", ordinal);
        values.put("question", question);
        values.put("answer", answer);
        values.put("priority", priority);
        values.put("interval", interval);
        values.put("lastInterval", lastInterval);
        values.put("due", due);
        values.put("lastDue", lastDue);
        values.put("factor", factor);
        values.put("lastFactor", lastFactor);
        values.put("firstAnswered", firstAnswered);
        values.put("reps", reps);
        values.put("successive", successive);
        values.put("averageTime", averageTime);
        values.put("reviewTime", reviewTime);
        values.put("youngEase0", youngEase0);
        values.put("youngEase1", youngEase1);
        values.put("youngEase2", youngEase2);
        values.put("youngEase3", youngEase3);
        values.put("youngEase4", youngEase4);
        values.put("matureEase0", matureEase0);
        values.put("matureEase1", matureEase1);
        values.put("matureEase2", matureEase2);
        values.put("matureEase3", matureEase3);
        values.put("matureEase4", matureEase4);
        values.put("yesCount", yesCount);
        values.put("noCount", noCount);
        values.put("spaceUntil", spaceUntil);
        values.put("isDue", 0);
        values.put("type", type);
        values.put("combinedDue", Math.max(spaceUntil, due));
        values.put("relativeDelay", 0.0);
        AnkiDatabaseManager.getDatabase(deck.deckPath).database.update("cards", values, "id = " + id, null);

        // TODO: Should also write JOINED entries: CardModel and Fact.
    }


    // Method used for building downloaded decks
    public void updateQAfields() {
        ContentValues values = new ContentValues();
        values.put("modified", modified);
        values.put("question", question);
        values.put("answer", answer);
        AnkiDatabaseManager.getDatabase(deck.deckPath).database.update("cards", values, "id = " + id, null);
    }

    // Leech flag
    public boolean getLeechFlag() {
        return isLeechMarked;
    }
    public void setLeechFlag(boolean flag) {
        isLeechMarked = flag;
    }
    // Suspended flag
    public boolean getSuspendedFlag() {
        return isLeechSuspended;
    }
    public void setSuspendedFlag(boolean flag) {
        isLeechSuspended = flag;
    }
}
