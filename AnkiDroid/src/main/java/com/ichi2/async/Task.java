package com.ichi2.async;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;

public interface Task {
     TaskData background(CollectionTask task);
}
