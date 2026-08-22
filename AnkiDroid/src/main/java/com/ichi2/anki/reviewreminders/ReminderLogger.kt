// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.content.Context
import com.ichi2.anki.R
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.settings.PrefsStore
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Profile ID for the reminder log SharedPreferences file key, mirroring [ReviewRemindersDatabase]'s.
 * Currently hard-coded as 0; see [ReviewRemindersDatabase] for multi-profile caveats, which apply here too.
 */
private const val PROFILE_ID: Int = 0

/**
 * SharedPreferences file name key for the reminder log.
 */
private const val SHARED_PREFS_FILE_KEY = "com.ichi2.anki.REVIEW_REMINDERS_LOG_SHARED_PREFS_$PROFILE_ID"

/**
 * The maximum number of log entries to retain. Once exceeded, the least recent entry is evicted.
 */
private const val MAX_ENTRIES = 100

/**
 * Small persistent logger service for review reminders.
 *
 * Many review reminder events like "notification fired" and "notification attempted" are difficult to test
 * (may need to wait a whole day to test again) or fire at non-deterministic times due to OS intervention.
 * Bugs such as missing notifications may be noticed by developers long after logs have been rotated out of memory.
 * Users may find describing issues on forums difficult beyond "I didn't get a notification."
 * This small set of logs is persisted to shared preferences and is intended to aid in bug reporting and diagnosis.
 */
object ReminderLogger : PrefsStore<ReminderLogEntry>(
    keyResId = R.string.review_reminder_log_key,
    entrySerializer = ReminderLogEntry.serializer(),
    maxEntries = MAX_ENTRIES,
    prefs = PrefsRepository(appContext.getSharedPreferences(SHARED_PREFS_FILE_KEY, Context.MODE_PRIVATE), appContext.resources),
) {
    /** Indicates a successful event occurred. */
    fun log(
        loggedMessage: String,
        persistedMessage: String = loggedMessage,
        id: ReviewReminderId? = null,
    ) {
        Timber.i(loggedMessage)
        addRecent(ReminderLogEntry.newEntry(ReminderLogLevel.LOG, id, persistedMessage))
    }

    /** Indicates an event (ex. a notification) was aborted. */
    fun skip(
        loggedMessage: String,
        persistedMessage: String = loggedMessage,
        id: ReviewReminderId? = null,
    ) {
        Timber.i(loggedMessage)
        addRecent(ReminderLogEntry.newEntry(ReminderLogLevel.SKIP, id, persistedMessage))
    }

    /** Indicates something went wrong. */
    fun warn(
        loggedMessage: String,
        persistedMessage: String = loggedMessage,
        id: ReviewReminderId? = null,
        error: Throwable? = null,
    ) {
        Timber.w(error, loggedMessage)
        addRecent(ReminderLogEntry.newEntry(ReminderLogLevel.WARN, id, persistedMessage))
    }

    /** Indicates that something which should never happen, happened. */
    fun error(
        loggedMessage: String,
        persistedMessage: String = loggedMessage,
        id: ReviewReminderId? = null,
        error: Throwable? = null,
    ) {
        Timber.e(error, loggedMessage)
        addRecent(ReminderLogEntry.newEntry(ReminderLogLevel.ERROR, id, persistedMessage))
    }
}

/**
 * The type of [ReminderLogEntry].
 */
@Serializable
enum class ReminderLogLevel(
    private val displayName: String,
) {
    @SerialName("l")
    LOG("log"),

    @SerialName("s")
    SKIP("skip"),

    @SerialName("w")
    WARN("warn"),

    @SerialName("e")
    ERROR("error"),
    ;

    override fun toString(): String = displayName
}

/**
 * An individual log entry created by [ReminderLogger].
 */
@Serializable
@ConsistentCopyVisibility
data class ReminderLogEntry private constructor(
    val time: String,
    val lvl: ReminderLogLevel,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: Int? = null,
    val msg: String,
) {
    override fun toString(): String = "$time:: $lvl: ${id ?: "-"}: $msg"

    companion object {
        /**
         * Creates a new [ReminderLogEntry] with the current time and provided parameters.
         */
        fun newEntry(
            level: ReminderLogLevel,
            id: ReviewReminderId?,
            message: String,
        ) = ReminderLogEntry(
            time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(TimeManager.time.currentDate),
            lvl = level,
            id = id?.value,
            msg = message,
        )
    }
}
