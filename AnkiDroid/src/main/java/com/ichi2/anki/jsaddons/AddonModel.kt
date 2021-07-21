/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
 *                                                                                      *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.jsaddons

import android.annotation.SuppressLint

/**
 * This class used in NpmPackageDownloader by ObjectMapper to map npm package.json to AddonModel
 * When package.json fetched from https://registry.npmjs.org/some-addon/latest,
 * ObjectMapper (jackson) readvalue map package.json to this class
 * All the required fields in package.json mapped to variables in this class.
 * The most important fields in package.json are
 * ankiDroidJsApi, addonType and keywords --> this distinguish from other npm packages
 */

@SuppressLint("NonPublicNonStaticFieldName")
class AddonModel {
    val name: String? = null // name of npm package, it unique for each package listed on npm
    val addonTitle: String? = null // for showing in AnkiDroid
    val version: String? = null
    val description: String? = null
    val main: String? = null
    val ankidroidJsApi: String? = null
    val addonType: String? = null
    val keywords: Array<String>? = null
    val author: Map<String, String>? = null
    val license: String? = null
    val homepage: String? = null
    val dist: Map<String, String>? = null
}
