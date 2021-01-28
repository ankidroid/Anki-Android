package com.ichi2.libanki.template;

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Text extends ParsedNode {
    private final String mText;


    public Text(String mText) {
        this.mText = mText;
    }


    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return true;
    }


    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) {
        builder.append(mText);
    }


    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof Text)) {
            return false;
        }
        Text other = (Text) obj;
        return other.mText.equals(mText);
    }


    @NonNull
    @Override
    public String toString() {
        return "new Text(\"" + mText.replace("\\", "\\\\").replace("\"", "\\\"")+ "\")";
    }
}
