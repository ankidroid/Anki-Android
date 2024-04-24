/*
 *  Copyright (c) 2024 Abd-Elrahman Esam <abdelrahmanesam20000@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor


/**
 * Product Flavors are used for Amazon App Store and Google Play Store.
 * This is because we cannot use Camera Permissions in Amazon App Store (for FireTv etc...)
 * Therefore, different AndroidManifest for Camera Permissions is used in Amazon flavor.
 *
 * This flavor block must stay in sync with the same block in testlib/build.gradle.kts
 */

@Suppress("EnumEntryName")
enum class FlavorDimension {
    appStore
}

@Suppress("EnumEntryName")
enum class AnkiFlavor(val dimension: FlavorDimension) {
    play(FlavorDimension.appStore),
    amazon(FlavorDimension.appStore),
    full(FlavorDimension.appStore),
}

internal fun CommonExtension<*, *, *, *, *>.configureFlavors(
) {
    flavorDimensions += FlavorDimension.appStore.name
    productFlavors {
        AnkiFlavor.values().forEach {
            create(it.name) {
                dimension = FlavorDimension.appStore.name
            }
        }
    }
}
