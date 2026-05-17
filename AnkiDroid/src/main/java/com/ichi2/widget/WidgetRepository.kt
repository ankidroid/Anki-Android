// SPDX-FileCopyrightText: 2026 Sumit Singh <sumitsinghkoranga7@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.widget

/**
 * Storage abstraction for widget status, decoupling widget code from [com.ichi2.anki.MetaDB].
 * When `:widgets` becomes a separate module, this interface moves with it.
 */
interface WidgetRepository {
    fun storeSmallWidgetStatus(status: SmallWidgetStatus)

    fun getWidgetSmallStatus(): SmallWidgetStatus

    fun dueCardsCount(): Int
}

object WidgetStorage {
    lateinit var instance: WidgetRepository
        private set

    fun setRepository(repository: WidgetRepository) {
        instance = repository
    }

    fun storeSmallWidgetStatus(status: SmallWidgetStatus) = instance.storeSmallWidgetStatus(status)

    fun getWidgetSmallStatus(): SmallWidgetStatus = instance.getWidgetSmallStatus()

    fun dueCardsCount(): Int = instance.dueCardsCount()
}
