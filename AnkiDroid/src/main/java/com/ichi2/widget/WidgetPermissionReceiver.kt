// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.storage.grantedStoragePermissions

/**
 * BroadcastReceiver to handle the scenario where storage permissions are granted,
 * triggering an update for widgets using the AddNoteWidget class.
 */
class WidgetPermissionReceiver : AnkiBroadcastReceiver() {
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (grantedStoragePermissions(context)) {
            val appWidgetManager = getAppWidgetManager(context) ?: return
            val widgetIds = appWidgetManager.getAppWidgetIdsEx(ComponentName(context, AddNoteWidget::class.java))
            AddNoteWidget.updateWidgets(context, appWidgetManager, widgetIds)
        }
    }
}
