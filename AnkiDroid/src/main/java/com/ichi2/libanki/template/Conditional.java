package com.ichi2.libanki.template;

import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Conditional extends ParsedNode {
    private final String mKey;
    private final ParsedNode mChild;


    public Conditional(String key, ParsedNode child) {
        this.mKey = key;
        this.mChild = child;
    }




    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return !nonempty_fields.contains(mKey) || mChild.template_is_empty(nonempty_fields);
    }


    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) throws TemplateError {
        if (nonempty_fields.contains(mKey)) {
            mChild.render_into(fields, nonempty_fields, builder);
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (! (obj instanceof Conditional)) {
            return false;
        }
        Conditional other = (Conditional) obj;
        return other.mKey.equals(mKey) && other.mChild.equals(mChild);
    }

    @NonNull
    @Override
    public String toString() {
        return "new Conditional(\"" + mKey.replace("\\", "\\\\") + "\"," + mChild + ")";
    }
}
