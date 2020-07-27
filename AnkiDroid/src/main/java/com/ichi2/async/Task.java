package com.ichi2.async;

public interface Task<Progress, Result> {
     Result background(CollectionTask<Progress, ?> task);
}
