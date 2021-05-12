package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.jetbrains.annotations.Contract;

import java.util.Objects;

import androidx.annotation.Nullable;

public class ModelType {

    public static ModelType STANDARD = new ModelType(0);
    public static ModelType CLOZE = new ModelType(1);

    public static ModelType[] values = new ModelType[] {STANDARD, CLOZE};

    @JsonCreator
    @Contract("null -> null; !null -> !null")
    public static ModelType createFromValue(@Nullable Integer value) {
        if (value == null) {
            return null;
        }
        for (ModelType type : values) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return new ModelType(value, true);
    }


    private final int mValue;
    private final boolean mUnknown;

    private ModelType(int value) {
        this(value, false);
    }

    private ModelType(int value, boolean unknown) {
        this.mValue = value;
        this.mUnknown = unknown;
    }

    @JsonValue
    public int getValue() {
        return mValue;
    }


    public boolean isUnknown() {
        return mUnknown;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ModelType that = (ModelType) o;
        return mUnknown == that.mUnknown && mValue == that.mValue;
    }


    @Override
    public int hashCode() {
        return Objects.hash(mValue, mUnknown);
    }
}
