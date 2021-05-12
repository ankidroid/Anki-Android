
package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Field {

    @JsonProperty("name")
    private String mName;

    @JsonProperty("ord")
    private Long mOrdinal;

    @JsonProperty("sticky")
    private Boolean mSticky;

    @JsonProperty("rtl")
    private Boolean mRtl;

    @JsonProperty("font")
    private String mFont;

    @JsonProperty("size")
    private Long mSize;

    @JsonProperty("media")
    private List<Object> mMedia;

    @JsonProperty("single line")
    private Boolean mSingleLine;

    @JsonIgnore
    private final Map<String, Object> mAdditionalProperties = new HashMap<>();


    /**
     * No args constructor for use in serialization
     */
    public Field() {
    }

    public Field(String name, Long ord, Boolean sticky, Boolean rtl, String font, Long size, List<Object> media, Boolean singleLine) {
        super();
        this.mName = name;
        this.mOrdinal = ord;
        this.mSticky = sticky;
        this.mRtl = rtl;
        this.mFont = font;
        this.mSize = size;
        this.mMedia = media;
        this.mSingleLine = singleLine;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public Long getOrdinal() {
        return mOrdinal;
    }

    public void setOrdinal(Long ordinal) {
        this.mOrdinal = ordinal;
    }

    public Boolean isSticky() {
        return mSticky;
    }

    public void setSticky(Boolean sticky) {
        this.mSticky = sticky;
    }

    public Boolean isRtl() {
        return mRtl;
    }

    public void setRtl(Boolean rtl) {
        this.mRtl = rtl;
    }

    public String getFont() {
        return mFont;
    }

    public void setFont(String font) {
        this.mFont = font;
    }

    public Long getSize() {
        return mSize;
    }

    public void setSize(Long size) {
        this.mSize = size;
    }

    public List<Object> getMedia() {
        return mMedia;
    }

    public void setMedia(List<Object> media) {
        this.mMedia = media;
    }

    public Boolean isSingleLine() {
        return mSingleLine;
    }

    public void setSingleLine(Boolean singleLine) {
        this.mSingleLine = singleLine;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.mAdditionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.mAdditionalProperties.put(name, value);
    }

}
