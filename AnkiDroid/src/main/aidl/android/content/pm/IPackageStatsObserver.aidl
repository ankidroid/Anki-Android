/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

// This file was adapted from
// https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/pm/IPackageStatsObserver.aidl

package android.content.pm;

import android.content.pm.PackageStats;
/**
 * API for package data change related callbacks from the Package Manager.
 * Some usage scenarios include deletion of cache directory, generate
 * statistics related to code, data, cache usage(TODO)
 * {@hide}
 *
 * This generates a deprecation warning during builds, and it would be great to remove it.
 * There is only one usage: FileUtils::getUserDataAndCacheSizeUsingGetPackageSizeInfo
 * The code that references this will no longer be needed after Build.VERSION_CODES >= 0
 * or minSdk >= 26 - at that API level we should be able to remove all related code / deprecation
 */
oneway interface IPackageStatsObserver {
    void onGetStatsCompleted(in PackageStats pStats, boolean succeeded);
}