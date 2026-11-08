// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import com.ichi2.anki.common.utils.android.showThemedToast
import java.util.UUID

/** Owns an import from preparation until its confirmation or import screen is closed. */
class ImportSession private constructor(
    val id: ImportSessionId,
) {
    private var finished = false
    private var operations = 0

    fun finish() {
        synchronized(Companion) {
            finished = true
            releaseIfFinished()
        }
    }

    private fun releaseIfFinished() {
        if (finished && operations == 0 && active === this) active = null
    }

    private suspend fun <T> withOperation(block: suspend () -> T): T {
        synchronized(Companion) {
            check(!finished)
            operations++
        }
        try {
            return block()
        } finally {
            synchronized(Companion) {
                operations--
                releaseIfFinished()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "importSessionId"
        private var active: ImportSession? = null

        @VisibleForTesting
        fun resetForTests() {
            synchronized(this) {
                check((active?.operations ?: 0) == 0)
                active = null
            }
        }

        /** An ID transfers an existing import, or restores it after process death. */
        fun acquire(
            context: Context,
            id: ImportSessionId? = null,
        ): ImportSession? {
            val session =
                synchronized(this) {
                    val current = active
                    when {
                        current == null -> ImportSession(id ?: ImportSessionId(UUID.randomUUID().toString())).also { active = it }
                        current.id == id && !current.finished -> current
                        else -> null
                    }
                }
            if (session == null) {
                // TODO: Reconsider queuing concurrent imports instead of rejecting them.
                showThemedToast(context, CommonString.import_already_in_progress, false)
            }
            return session
        }

        fun finish(id: ImportSessionId?) {
            synchronized(this) {
                active?.takeIf { it.id == id }?.finish()
            }
        }

        /** Closing the UI must not release the source file while the backend is still reading it. */
        suspend fun <T> withOperation(block: suspend () -> T): T {
            val session = synchronized(this) { active }
            return session?.withOperation(block) ?: block()
        }
    }
}

/** Keeps ownership across recreation, but releases it when the owning screen is removed. */
class ImportSessionViewModel : ViewModel() {
    private var session: ImportSession? = null

    fun attach(
        context: Context,
        id: ImportSessionId?,
    ): Boolean {
        if (session == null) session = ImportSession.acquire(context, id)
        return session != null
    }

    fun transfer(): ImportSessionId {
        val id = checkNotNull(session).id
        session = null
        return id
    }

    override fun onCleared() {
        session?.finish()
    }
}
