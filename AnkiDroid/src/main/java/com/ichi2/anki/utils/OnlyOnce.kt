// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Prevent multiple instances of a method being executed simultaneously
 */
object OnlyOnce {
    private val blockedFunctions = mutableSetOf<Any>()

    enum class Method {
        ANSWER_CARD,
        UNIT_TEST,
    }

    /**
     * Prevents multiple instances of a method being executed simultaneously
     *
     * If the provided method is not running, run it
     * If the provided method is running, do nothing more
     */
    fun preventSimultaneousExecutions(
        name: Method,
        function: () -> Job,
    ) {
        if (!blockedFunctions.add(name)) {
            Timber.w("simultaneously executions of $name blocked")
            return
        }
        Timber.v("executing $name")
        function().invokeOnCompletion {
            Timber.v("completed $name")
            blockedFunctions.remove(name)
        }
    }
}

/**
 * Enforces single execution of a coroutine task
 * if a task is running, ignore any other requests till it finishes
 * @param scope The coroutine scope where the task is launched
 */
class RunOnlyOnce(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        if (job?.isActive == true) {
            Timber.d("skipped multiple executions of job")
            return
        }
        job = scope.launch(block = block)
    }
}
