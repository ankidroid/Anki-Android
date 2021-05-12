package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@JsonFormat(shape= JsonFormat.Shape.ARRAY)
public class Requirement {
    private long mTemplateOrdinal;
    private RequirementType mType;
    private List<Long> mFieldsOrdinal;


    @JsonCreator // same order as in the original array
    public Requirement(@JsonProperty("templateOrdinal") long templateOrdinal,
                       @JsonProperty("type") RequirementType type,
                       @JsonProperty("fieldsOrdinal") List<Long> fieldsOrdinal) {
        mTemplateOrdinal = templateOrdinal;
        mType = type;
        mFieldsOrdinal = fieldsOrdinal;
    }


    public long getTemplateOrdinal() {
        return mTemplateOrdinal;
    }


    public Requirement setTemplateOrdinal(long templateOrdinal) {
        mTemplateOrdinal = templateOrdinal;
        return this;
    }


    public RequirementType getType() {
        return mType;
    }


    public Requirement setType(RequirementType type) {
        mType = type;
        return this;
    }


    public List<Long> getFieldsOrdinal() {
        return mFieldsOrdinal;
    }


    public Requirement setFieldsOrdinal(List<Long> fieldsOrdinal) {
        mFieldsOrdinal = fieldsOrdinal;
        return this;
    }
}
