/*
 * Copyright (c) 2025 Akshit Mandial <akshitmandial517@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * **Example Usage:**
 * ```kotlin
 * val intent = context.getIntentWithClearTop(MyActivity::class.java) {
 *     putExtra("key", "value")
 * }
 * startActivity(intent)
 * ```
 */
private fun <T : Activity> Context.getIntentWithFlags(
    activity: Class<T>,
    configIntent: Intent.() -> Unit = {},
): Intent =
    Intent(this, activity).apply {
        configIntent()
    }

/**
 * Creates an explicit intent with `FLAG_ACTIVITY_CLEAR_TOP`.
 */
fun <T : Activity> Context.getIntentWithClearTop(
    activity: Class<T>,
    configIntent: Intent.() -> Unit = {},
): Intent =
    getIntentWithFlags(activity) {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        configIntent()
    }

/**
 * Creates an explicit intent with `FLAG_ACTIVITY_CLEAR_TASK`.
 */
fun <T : Activity> Context.getIntentWithClearTask(
    activity: Class<T>,
    configIntent: Intent.() -> Unit = {},
): Intent =
    getIntentWithFlags(activity) {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        configIntent()
    }

/**
 * Creates an explicit intent with both `FLAG_ACTIVITY_CLEAR_TOP` and `FLAG_ACTIVITY_NEW_TASK`.
 */
fun <T : Activity> Context.getIntentWithClearTopAndNewTask(
    activity: Class<T>,
    configIntent: Intent.() -> Unit = {},
): Intent =
    getIntentWithFlags(activity) {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        configIntent()
    }

/**
 * Creates an explicit intent with `FLAG_ACTIVITY_NEW_TASK` and `FLAG_ACTIVITY_CLEAR_TASK`.
 */
fun <T : Activity> Context.getIntentWithNewTaskAndClearTask(
    activity: Class<T>,
    configIntent: Intent.() -> Unit = {},
): Intent =
    getIntentWithFlags(activity) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        configIntent()
    }

/**
 * **Example Usage:**
 * ```kotlin
 * val intent = context.getImplicitIntentWithClearTop(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
 * startActivity(intent)
 * ```
 */
private fun getImplicitIntent(
    action: String,
    data: Uri? = null,
    configIntent: Intent.() -> Unit = {},
): Intent =
    Intent(action).apply {
        data?.let { setData(it) }
        configIntent()
    }

/**
 * Creates an implicit intent with `FLAG_ACTIVITY_NEW_TASK`.
 */
fun getImplicitIntentWithNewTask(
    action: String,
    data: Uri? = null,
    configIntent: Intent.() -> Unit = {},
): Intent =
    getImplicitIntent(action, data) {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        configIntent()
    }
