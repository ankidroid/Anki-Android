package com.ichi2.libanki.template;

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class EmptyNode extends ParsedNode {
    @Override
    public boolean template_is_empty(@NonNull Set<String> nonempty_fields) {
        return true;
    }


    @Override
    public void render_into(Map<String, String> fields, Set<String> nonempty_fields, StringBuilder builder) {
    }


    @NonNull
    @Override
    public String toString() {
        return "new EmptyNode()";
    }


    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof EmptyNode;
    }
}
