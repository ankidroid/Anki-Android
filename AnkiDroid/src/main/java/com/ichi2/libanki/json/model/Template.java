
package com.ichi2.libanki.json.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;


public class Template {

    @JsonProperty("name")
    private String mName;
    
    @JsonProperty("ord")
    private Long mOrdinal;
    
    @JsonProperty("qfmt")
    private String mQuestionFormat;
    
    @JsonProperty("afmt")
    private String mAnswerFormat;
    
    @JsonProperty("bqfmt")
    private String mBrowserQuestionFormat;
    
    @JsonProperty("bafmt")
    private String mBrowserAnswerFormat;
    
    @JsonProperty("did")
    private Object mDeckId;
    
    @JsonProperty("bfont")
    private String mBrowserFont;
    
    @JsonProperty("bsize")
    private Long mBrowserFontSize;

    @JsonIgnore
    private final Map<String, Object> mAdditionalProperties = new HashMap<>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public Template() {
    }


    public Template(String name, Long ord, String qfmt, String afmt, String bqfmt, String bafmt, Object did, String bfont, Long bsize) {
        super();
        this.mName = name;
        this.mOrdinal = ord;
        this.mQuestionFormat = qfmt;
        this.mAnswerFormat = afmt;
        this.mBrowserQuestionFormat = bqfmt;
        this.mBrowserAnswerFormat = bafmt;
        this.mDeckId = did;
        this.mBrowserFont = bfont;
        this.mBrowserFontSize = bsize;
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

    public String getQuestionFormat() {
        return mQuestionFormat;
    }

    public void setQuestionFormat(String questionFormat) {
        this.mQuestionFormat = questionFormat;
    }

    public String getAnswerFormat() {
        return mAnswerFormat;
    }

    public void setAnswerFormat(String answerFormat) {
        this.mAnswerFormat = answerFormat;
    }

    public String getBrowserQuestionFormat() {
        return mBrowserQuestionFormat;
    }

    public void setBrowserQuestionFormat(String browserQuestionFormat) {
        this.mBrowserQuestionFormat = browserQuestionFormat;
    }

    public String getBrowserAnswerFormat() {
        return mBrowserAnswerFormat;
    }

    public void setBrowserAnswerFormat(String browserAnswerFormat) {
        this.mBrowserAnswerFormat = browserAnswerFormat;
    }

    public Object getDeckId() {
        return mDeckId;
    }

    public void setDeckId(Object deckId) {
        this.mDeckId = deckId;
    }

    public String getBrowserFont() {
        return mBrowserFont;
    }

    public void setBrowserFont(String browserFont) {
        this.mBrowserFont = browserFont;
    }

    public Long getBrowserFontSize() {
        return mBrowserFontSize;
    }

    public void setBrowserFontSize(Long browserFontSize) {
        this.mBrowserFontSize = browserFontSize;
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
