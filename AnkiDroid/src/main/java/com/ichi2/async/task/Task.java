package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;

public abstract class Task {
    public abstract TaskData background(CollectionTask task);
}
