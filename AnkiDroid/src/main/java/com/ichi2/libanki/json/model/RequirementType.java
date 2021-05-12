package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import org.jetbrains.annotations.Contract;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RequirementType {

    public static RequirementType NONE = new RequirementType("none");
    public static RequirementType ALL = new RequirementType("all");
    public static RequirementType ANY = new RequirementType("any");

    public static RequirementType[] values = new RequirementType[] {NONE, ALL, ANY};

    @JsonCreator
    @Contract("null -> null; !null -> !null")
    public static RequirementType createFromValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (RequirementType type : values) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return new RequirementType(value, true);
    }


    @NonNull
    private final String mValue;
    private final boolean mUnknown;

    private RequirementType(String value) {
        this(value, false);
    }

    private RequirementType(@NonNull String value, boolean unknown) {
        this.mValue = value;
        this.mUnknown = unknown;
    }

    @JsonValue
    @NonNull
    public String getValue() {
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
        RequirementType that = (RequirementType) o;
        return mUnknown == that.mUnknown && mValue.equals(that.mValue);
    }


    @Override
    public int hashCode() {
        return Objects.hash(mValue, mUnknown);
    }
}
