package com.ichi2.async;


import android.content.Context;

import com.ichi2.anki.CardBrowser;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;

import java.util.List;
import java.util.Map;
import static com.ichi2.anki.CardBrowser.CardCache;

public class TaskData {
    private Card mCard;
    private Note mNote;
    private int mInteger;
    private String mMsg;
    private boolean mBool = false;
    private List<CardCache> mCards;
    private long mLong;
    private Context mContext;
    private int mType;
    private Object[] mObjects;


    public TaskData(Object[] obj) {
        mObjects = obj;
    }


    public TaskData(int value, Object[] obj, boolean bool) {
        mObjects = obj;
        mInteger = value;
        mBool = bool;
    }

    public TaskData(Object[] obj, boolean bool) {
        mObjects = obj;
        mBool = bool;
    }


    public TaskData(int value, Card card) {
        this(value);
        mCard = card;
    }


    public TaskData(int value, long cardId, boolean bool) {
        this(value);
        mLong = cardId;
        mBool = bool;
    }


    public TaskData(Card card) {
        mCard = card;
    }


    public TaskData(Card card, String tags) {
        mCard = card;
        mMsg = tags;
    }


    public TaskData(Card card, int integer) {
        mCard = card;
        mInteger = integer;
    }


    public TaskData(Context context, int type, int period) {
        mContext = context;
        mType = type;
        mInteger = period;
    }


    public TaskData(List<CardCache> cards) {
        mCards = cards;
    }


    public TaskData(boolean bool) {
        mBool = bool;
    }

    public TaskData(boolean bool, Object[] obj) {
        mBool = bool;
        mObjects = obj;
    }

    public TaskData(String string, boolean bool) {
        mMsg = string;
        mBool = bool;
    }


    public TaskData(long value, boolean bool) {
        mLong = value;
        mBool = bool;
    }


    public TaskData(int value, boolean bool) {
        mInteger = value;
        mBool = bool;
    }


    public TaskData(Card card, boolean bool) {
        mBool = bool;
        mCard = card;
    }


    public TaskData(int value) {
        mInteger = value;
    }


    public TaskData(long l) {
        mLong = l;
    }


    public TaskData(String msg) {
        mMsg = msg;
    }


    public TaskData(Note note) {
        mNote = note;
    }


    public TaskData(int value, String msg) {
        mMsg = msg;
        mInteger = value;
    }


    public TaskData(String msg, long cardId, boolean bool) {
        mMsg = msg;
        mLong = cardId;
        mBool = bool;
    }


    public List<CardCache> getCards() {
        return mCards;
    }


    public void setCards(List<CardCache> cards) {
        mCards = cards;
    }


    public Card getCard() {
        return mCard;
    }


    public Note getNote() {
        return mNote;
    }


    public long getLong() {
        return mLong;
    }


    public int getInt() {
        return mInteger;
    }


    public String getString() {
        return mMsg;
    }


    public boolean getBoolean() {
        return mBool;
    }


    public Context getContext() {
        return mContext;
    }


    public int getType() {
        return mType;
    }


    public Object[] getObjArray() {
        return mObjects;
    }


    public <T> boolean objAtIndexIs(int i, Class<T> clazz) {
        if (getObjArray() == null) {
            return false;
        }
        if (getObjArray().length <= i) {
            return false;
        }
        Object val = getObjArray()[i];
        if (val == null) {
            return false;
        }

        return clazz.isAssignableFrom(val.getClass());
    }
}
