// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LanguageUtilRobolectricTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "zn")
    fun `Language without region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn"))
    }

    @Test
    @Config(qualifiers = "zn-rTW")
    fun `Language with region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn-TW"))
    }

    @Test
    fun `regional preferences do not change the backend language`() {
        for (tag in listOf("pl-PL-u-fw-mon", "pl-PL-u-mu-celsius", "pl-PL-x-private", "pl-PL-x-lvariant-WIN")) {
            LanguageUtil.setDefaultBackendLanguages(tag)

            assertEquals(listOf("pl-PL"), BackendFactory.defaultLanguages, tag)
        }
    }

    @Test
    fun `backend language preserves script region and variants`() {
        LanguageUtil.setDefaultBackendLanguages("ca-Latn-ES-valencia-u-fw-mon")

        assertEquals(listOf("ca-Latn-ES-valencia"), BackendFactory.defaultLanguages)
    }

    @Test
    fun `backend language aliases remain supported`() {
        for ((tag, expected) in mapOf("heb" to "he", "ind" to "id", "tgl" to "tl", "hi" to "hi-IN")) {
            LanguageUtil.setDefaultBackendLanguages(tag)

            assertEquals(listOf(expected), BackendFactory.defaultLanguages, tag)
        }
    }

    @Test
    fun `failed conversion preserves the original tag for rslib`() {
        for (tag in listOf("abcd", "pl-PL-!", "en-US-Latn", "pl-PL-abcd", "pl-PL-1!!!", "pt-PT-abcd")) {
            LanguageUtil.setDefaultBackendLanguages("pl")

            LanguageUtil.setDefaultBackendLanguages(tag)

            assertEquals(listOf(tag), BackendFactory.defaultLanguages, tag)
        }
    }
}
