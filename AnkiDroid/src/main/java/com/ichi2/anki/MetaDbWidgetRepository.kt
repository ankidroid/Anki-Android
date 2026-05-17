// SPDX-FileCopyrightText: 2026 Sumit Singh <sumitsinghkoranga7@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.app.Application
import android.content.Context
import com.ichi2.widget.SmallWidgetStatus
import com.ichi2.widget.WidgetRepository
import com.ichi2.widget.WidgetStorage

/**
 * Delegates [WidgetRepository] to [MetaDB]. Replace this class to migrate
 * widget storage to MMKV/DataStore without touching callers.
 *
 * TODO: replace MetaDB backing with MMKV or DataStore (#20737)
 */
internal class MetaDbWidgetRepository(
    private val context: Context,
) : WidgetRepository {
    override fun storeSmallWidgetStatus(status: SmallWidgetStatus) = MetaDB.storeSmallWidgetStatus(context, status)

    override fun getWidgetSmallStatus(): SmallWidgetStatus = MetaDB.getWidgetSmallStatus(context)

    override fun dueCardsCount(): Int = MetaDB.getNotificationStatus(context)
}

context(application: Application)
fun initializeWidgetRepository() {
    WidgetStorage.setRepository(MetaDbWidgetRepository(application))
}
