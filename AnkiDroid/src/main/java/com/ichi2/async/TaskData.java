package com.ichi2.async;


import android.content.Context;

import com.ichi2.libanki.Card;
import com.ichi2.libanki.Note;

import java.util.List;

import androidx.annotation.CheckResult;

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


    public List<CardCache> getCards() {
        return mCards;
    }


    public void setCards(List<CardCache> cards) {
        mCards = cards;
    }


    @CheckResult
    public Card getCard() {
        return mCard;
    }


    @CheckResult
    public Note getNote() {
        return mNote;
    }


    @CheckResult
    public long getLong() {
        return mLong;
    }


    @CheckResult
    public int getInt() {
        return mInteger;
    }


    @CheckResult
    public String getString() {
        return mMsg;
    }


    @CheckResult
    public boolean getBoolean() {
        return mBool;
    }


    @CheckResult
    public Context getContext() {
        return mContext;
    }


    @CheckResult
    public int getType() {
        return mType;
    }


    @CheckResult
    public Object[] getObjArray() {
        return mObjects;
    }


    @CheckResult
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
