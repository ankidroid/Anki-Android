// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ext.indexOfNewlineAtOrAfter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tag identifying a [Timber] call which should be persisted by [ReminderLogTree].
 */
private const val REMINDER_TAG = "Reminders"

/**
 * A [Timber.Tree] which persists review reminder logs to a file.
 * Logs are routed here by tagging the [Timber] call with [reminderTag]; untagged logs are ignored.
 *
 * Many review reminder events like "notification fired" and "notification attempted" are difficult to test
 * (may need to wait a whole day to test again) or fire at non-deterministic times due to OS intervention.
 * Bugs such as missing notifications may be noticed by developers long after logs have been rotated out of memory.
 * Users may find describing issues on forums difficult beyond "I didn't get a notification".
 * This small set of logs is persisted to a file and is intended to aid in bug reporting and diagnosis.
 *
 * @param context Used to locate the log file.
 */
@SuppressLint("LogNotTimber")
class ReminderLogTree(
    context: Context,
) : Timber.Tree() {
    /**
     * We store the log file in [Context.getNoBackupFilesDir], which requires no permission on any build flavor, is
     * never reclaimed by the OS when storage is low, and is excluded from device backups: the log describes the
     * behavior of alarms on *this* device, so entries restored from another device would be misleading.
     */
    private val logFile = File(context.noBackupFilesDir, LOG_FILE_NAME)

    private val scope: CoroutineScope = applicationScope

    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = tag != null && tag.startsWith(REMINDER_TAG) && priority >= Log.INFO

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // Take timestamp immediately to avoid logging a time that is later than the actual event
        val timestamp = TimeManager.time.currentDate
        scope.launch(dispatcher) {
            val line = formatLine(timestamp, priority, tag, message)
            append(line)
        }
    }

    /**
     * The contents of the log file, oldest entry first, or an empty string if nothing has been logged yet.
     */
    suspend fun readLog(): String =
        withContext(dispatcher) {
            try {
                if (logFile.exists()) logFile.readText() else ""
            } catch (e: IOException) {
                Log.w(REMINDER_TAG, "Failed to read review reminder log", e)
                ""
            }
        }

    private fun formatLine(
        timestamp: Date,
        priority: Int,
        tag: String?,
        message: String,
    ): String {
        val escapedMessage = message.replace("\n", "\\n")
        val truncatedMessage =
            if (escapedMessage.length <= MAX_LINE_LENGTH) {
                escapedMessage
            } else {
                escapedMessage.take(MAX_LINE_LENGTH) + "..."
            }
        val time = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(timestamp)
        val id = tag?.substringAfter('/', "")?.takeIf { it.isNotEmpty() } ?: "--"
        return "$time ${levelChar(priority)} #$id $truncatedMessage\n"
    }

    private fun levelChar(priority: Int): Char =
        when (priority) {
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR, Log.ASSERT -> 'E'
            else -> 'X'
        }

    /**
     * Appends [line] to the log file, truncating the file first if it has grown too large.
     */
    private fun append(line: String) {
        try {
            trimIfNeeded()
            FileOutputStream(logFile, true).use { it.write(line.toByteArray()) }
        } catch (e: IOException) {
            Log.w(REMINDER_TAG, "Failed to write to review reminder log", e)
        }
    }

    /**
     * Discards the oldest entries once the file exceeds [TRIM_TRIGGER_BYTES], leaving [TRIM_TARGET_BYTES].
     */
    private fun trimIfNeeded() {
        if (logFile.length() < TRIM_TRIGGER_BYTES) return
        val bytes = logFile.readBytes()
        val newlineIndex = bytes.indexOfNewlineAtOrAfter(bytes.size - TRIM_TARGET_BYTES)
        val start = if (newlineIndex == -1) bytes.size else newlineIndex + 1
        val temporaryFile = File(logFile.parentFile, "$LOG_FILE_NAME.tmp")
        temporaryFile.writeBytes(bytes.copyOfRange(start, bytes.size))
        if (!temporaryFile.renameTo(logFile)) {
            temporaryFile.delete()
            Log.w(REMINDER_TAG, "Failed to truncate review reminder log")
        }
    }

    companion object {
        /**
         * Name of the log file written by [ReminderLogTree].
         */
        private const val LOG_FILE_NAME = "reminders.log"

        /**
         * The log file is truncated back down to this size once it exceeds [TRIM_TRIGGER_BYTES].
         */
        private const val TRIM_TARGET_BYTES = 24 * 1024

        /**
         * Size at which the log file is truncated back down to [TRIM_TARGET_BYTES].
         */
        private const val TRIM_TRIGGER_BYTES = TRIM_TARGET_BYTES * 3 / 2

        /**
         * Maximum length of a single line in the log file. Just in case really long exceptions are sent to the log file.
         */
        private const val MAX_LINE_LENGTH = 2 * 1024

        /**
         * Timestamp format for entries in the log file.
         */
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        /**
         * Gets the contents of the review reminder log from Timber.
         */
        suspend fun readReminderLog(): String =
            Timber
                .forest()
                .filterIsInstance<ReminderLogTree>()
                .firstOrNull()
                ?.readLog() ?: ""
    }
}

/**
 * The tag to pass to [Timber.tag] to have a log persisted by [ReminderLogTree].
 * Only `i`, `w` and `e` are persisted; `d` and `v` are dropped.
 *
 * Usage examples:
 * ```
 * Timber.tag(reminderTag(reminder.id)).i("Successfully scheduled notifications")
 * Timber.tag(reminderTag()).i("scheduleAllEnabledReviewReminderNotifications")
 * ```
 *
 * @param id The reminder this log is about, or `null` if it is not about a specific reminder.
 */
fun reminderTag(id: ReviewReminderId? = null): String = if (id == null) REMINDER_TAG else "$REMINDER_TAG/${id.value}"
