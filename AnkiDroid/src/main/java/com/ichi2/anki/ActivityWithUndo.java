package com.ichi2.anki;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskListener;
import com.ichi2.async.TaskListenerWithContext;
import com.ichi2.async.TaskManager;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.CollectionGetter;
import com.ichi2.utils.BooleanGetter;


import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

public interface ActivityWithUndo extends CollectionGetter {
    TaskListener<Card, BooleanGetter> getUndoListener();


    @VisibleForTesting
    default boolean isUndoAvailable() {
        return getCol().undoAvailable();
    }

    default @Nullable
    CollectionTask<Card, Card, BooleanGetter, BooleanGetter> undo() {
        if (isUndoAvailable()) {
            return TaskManager.launchCollectionTask(new CollectionTask.Undo(), getUndoListener());
        }
        return null;
    }
}
