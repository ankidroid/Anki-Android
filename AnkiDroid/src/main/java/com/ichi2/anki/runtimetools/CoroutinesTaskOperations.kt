package com.ichi2.anki.runtimetools

import com.ichi2.async.BaseCoroutinesTask

object CoroutinesTaskOperations {
    /**
     * Gently killing ongoing Coroutines task
     */

    fun stopTaskGracefully(t: BaseCoroutinesTask<*, *, *>?) {
        if (t != null) {
            if (t.getStatus() == BaseCoroutinesTask.Status.RUNNING) {
                t.cancel()
            }
        }
    }
}
