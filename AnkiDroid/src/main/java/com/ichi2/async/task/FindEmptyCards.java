package com.ichi2.async.task;

import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskData;
import com.ichi2.libanki.Collection;

import java.util.List;

public class FindEmptyCards extends Task {
    public TaskData background(CollectionTask task) {
        Collection col = task.getCol();
        List<Long> cids = col.emptyCids();
        return new TaskData(new Object[] { cids});
    }
}
