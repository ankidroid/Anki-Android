/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

/*
 Convention plugin: applies `com.android.application` plus Kotlin Parcelize,
 and pins the settings shared with every other Android module
 (compileSdk, minSdk, Java 17). Mirrors `ankidroid.android.library`.
 */

import com.android.build.api.dsl.ApplicationExtension
import com.ichi2.anki.gradle.libsVersionFor

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

extensions.configure<ApplicationExtension> {
    compileSdk = libsVersionFor("compileSdk").toInt()

    defaultConfig {
        minSdk = libsVersionFor("minSdk").toInt()
        // After #13695: change .tests_emulator.yml
        targetSdk = libsVersionFor("targetSdk").toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Shared project-wide lint configuration.
apply(from = "${rootDir}/lint.gradle")
