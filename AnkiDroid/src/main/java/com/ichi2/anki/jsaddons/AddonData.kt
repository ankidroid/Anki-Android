/****************************************************************************************
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>                                       *
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

import com.ichi2.anki.AnkiDroidJsAPIConstants.sCurrentJsApiVersion
import com.ichi2.anki.AnkiSerialization
import com.ichi2.anki.jsaddons.AddonsConst.ANKIDROID_JS_ADDON_KEYWORDS
import com.ichi2.anki.jsaddons.AddonsConst.NOTE_EDITOR_ADDON
import com.ichi2.anki.jsaddons.AddonsConst.REVIEWER_ADDON
import com.ichi2.anki.jsaddons.NpmUtils.validateName
import org.acra.collections.ImmutableMap
import java.io.File
import java.io.IOException
import kotlin.jvm.Throws

/**
 * When package.json fetched from https://registry.npmjs.org/some-addon/latest,
 * all the required fields in package.json mapped to AddonModel in this class.
 * The most important fields in package.json are
 * ankiDroidJsApi, addonType and keywords, these fields distinguish other npm packages
 */

class AddonData(
    val name: String? = null, // name of npm package, it unique for each package listed on npm
    val addonTitle: String? = null, // for showing in AnkiDroid
    val icon: String? = null, // only required for note editor (single character recommended)
    val version: String? = null,
    val description: String? = null,
    val main: String? = null,
    val ankidroidJsApi: String? = null,
    val addonType: String? = null,
    val keywords: List<String>? = null,
    val author: Map<String, String>? = null,
    val license: String? = null,
    val homepage: String? = null,
    val dist: Map<String, String>? = null
)

/**
 * Check if npm package is valid or not by fields ankidroidJsApi, keywords (ankidroid-js-addon) and
 * addon_type (reviewer or note editor) in addonData
 *
 * For valid addon the error list will be empty,
 * for not valid addon the error list will contain the error related to the checks
 *
 * @param packageJsonPath package.json file path
 * @return Pair with addonModel and error list
 */
@Throws(IOException::class)
fun getAddonModelFromJson(packageJsonPath: String): Pair<AddonModel?, List<String>> {
    val mapper = AnkiSerialization.objectMapper
    val addonData: AddonData = mapper.readValue(File(packageJsonPath), AddonData::class.java)

    var errorStr: String
    val errorList: MutableList<String> = ArrayList()

    // either fields not present in package.json or failed to parse the fields
    if (addonData.name.isNullOrBlank() || addonData.addonTitle.isNullOrBlank() || addonData.main.isNullOrBlank() ||
        addonData.ankidroidJsApi.isNullOrBlank() || addonData.addonType.isNullOrBlank() || addonData.homepage.isNullOrBlank() ||
        addonData.keywords.isNullOrEmpty()
    ) {
        errorStr = "Invalid addon package: fields in package.json are empty or null"
        errorList.add(errorStr)
    }

    // check if name is safe and valid
    if (!validateName(addonData.name!!)) {
        errorStr = "Invalid addon package: package name failed validation"
        errorList.add(errorStr)
    }

    if (addonData.addonType != REVIEWER_ADDON && addonData.addonType != NOTE_EDITOR_ADDON) {
        errorStr = "Invalid addon package: ${addonData.addonType} is not valid addon type, package.json must have 'addonType' fields of 'reviewer' or 'note-editor'"
        errorList.add(errorStr)
    }

    // if addon type is note editor then it must have icon
    if (addonData.addonType == NOTE_EDITOR_ADDON && addonData.icon.isNullOrBlank()) {
        errorStr = "Invalid addon package: note editor addon must have 'icon' fields in package.json"
        errorList.add(errorStr)
    }

    // check if ankidroid-js-addon present or not in mapped addonData
    val jsAddonKeywordsPresent = addonData.keywords?.any { it == ANKIDROID_JS_ADDON_KEYWORDS }
    if (!jsAddonKeywordsPresent!!) {
        errorStr = "Invalid addon package: package.json does not have 'ankidroid-js-addon' in ${addonData.keywords} keywords"
        errorList.add(errorStr)
    }

    // Check supplied api and current api
    if (addonData.ankidroidJsApi != sCurrentJsApiVersion) {
        errorStr = "Invalid addon package: supplied js api version ${addonData.ankidroidJsApi} must be equal to current js api version $sCurrentJsApiVersion"
        errorList.add(errorStr)
    }

    val immutableList: List<String> = ArrayList(errorList)

    // there are errors in package.json so return null and errors list
    if (errorList.isNotEmpty()) {
        return Pair(null, immutableList)
    }

    val icon = if (addonData.addonType == NOTE_EDITOR_ADDON) addonData.icon!! else ""

    // return addon model, because it is validated
    val addonModel = AddonModel(
        name = addonData.name,
        addonTitle = addonData.addonTitle!!,
        icon = icon,
        version = addonData.version!!,
        description = addonData.description!!,
        main = addonData.main!!,
        ankidroidJsApi = addonData.ankidroidJsApi!!,
        addonType = addonData.addonType!!,
        keywords = addonData.keywords,
        author = ImmutableMap<String, String>(addonData.author!!),
        license = addonData.license!!,
        homepage = addonData.homepage!!,
        dist = ImmutableMap<String, String>(addonData.dist!!)
    )

    return Pair(addonModel, immutableList)
}
