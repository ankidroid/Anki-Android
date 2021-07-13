
package com.ichi2.libanki.json.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


public class Model {

    @JsonProperty("id")
    private Long mId;

    @JsonProperty("name")
    private String mName;

    @JsonProperty("type")
    private ModelType mType;

    @JsonProperty("mod")
    private Long mMod;

    @JsonProperty("usn")
    private Long mUsn;

    @JsonProperty("sortf")
    private Long mSortingField;

    @JsonProperty("did")
    private Long mDeckId;

    @JsonProperty("tmpls")
    private List<Template> mTemplates;

    @JsonProperty("flds")
    private List<Field> mFields;

    @JsonProperty("css")
    private String mCss;

    @JsonProperty("latexPre")
    private String mLatexPrefix;

    @JsonProperty("latexPost")
    private String mLatexPostfix;

    @JsonProperty("latexsvg")
    private Boolean mLatexSvg;

    @JsonProperty("req")
    private List<Requirement> mRequirements;

    @JsonIgnore
    private final Map<String, Object> mAdditionalProperties = new HashMap<>();

    /**
     * No args constructor for use in serialization
     *
     */
    public Model() {
    }


    public Model(Long id, String name, ModelType type, Long mod, Long usn, Long sortf, Long did, List<Template> tmpls, List<Field> flds, String css, String latexPre, String latexPost, Boolean latexsvg, List<Requirement> req) {
        super();
        this.mId = id;
        this.mName = name;
        this.mType = type;
        this.mMod = mod;
        this.mUsn = usn;
        this.mSortingField = sortf;
        this.mDeckId = did;
        this.mTemplates = tmpls;
        this.mFields = flds;
        this.mCss = css;
        this.mLatexPrefix = latexPre;
        this.mLatexPostfix = latexPost;
        this.mLatexSvg = latexsvg;
        this.mRequirements = req;
    }

    public Long getId() {
        return mId;
    }

    public void setId(Long id) {
        this.mId = id;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public ModelType getType() {
        return mType;
    }

    public void setType(ModelType type) {
        this.mType = type;
    }

    public Long getMod() {
        return mMod;
    }

    public void setMod(Long mod) {
        this.mMod = mod;
    }

    public Long getUsn() {
        return mUsn;
    }

    public void setUsn(Long usn) {
        this.mUsn = usn;
    }

    public Long getSortingField() {
        return mSortingField;
    }

    public void setSortingField(Long sortingField) {
        this.mSortingField = sortingField;
    }

    public Long getDeckId() {
        return mDeckId;
    }

    public void setDeckId(Long deckId) {
        this.mDeckId = deckId;
    }

    public List<Template> getTemplates() {
        return mTemplates;
    }

    public void setTemplates(List<Template> templates) {
        this.mTemplates = templates;
    }

    public List<Field> getFields() {
        return mFields;
    }

    public void setFields(List<Field> fields) {
        this.mFields = fields;
    }

    public String getCss() {
        return mCss;
    }

    public void setCss(String css) {
        this.mCss = css;
    }

    public String getLatexPrefix() {
        return mLatexPrefix;
    }

    public void setLatexPrefix(String latexPrefix) {
        this.mLatexPrefix = latexPrefix;
    }

    public String getLatexPostfix() {
        return mLatexPostfix;
    }

    public void setLatexPostfix(String latexPostfix) {
        this.mLatexPostfix = latexPostfix;
    }

    public Boolean isLatexsvg() {
        return mLatexSvg;
    }

    public void setLatexSvg(Boolean latexSvg) {
        this.mLatexSvg = latexSvg;
    }

    public List<Requirement> getRequirements() {
        return mRequirements;
    }

    public void setRequirements(List<Requirement> requirements) {
        this.mRequirements = requirements;
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
