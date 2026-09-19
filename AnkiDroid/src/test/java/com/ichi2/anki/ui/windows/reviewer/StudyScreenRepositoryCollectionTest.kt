// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.utils.CollectionPreferences
import com.ichi2.anki.utils.ext.cardStateCustomizer
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class StudyScreenRepositoryCollectionTest : JvmTest() {
    private val repository: StudyScreenRepository

    init {
        val mockResources = mockk<Resources>()
        every { mockResources.getString(any()) } answers { invocation.args[0].toString() }
        val prefs = PrefsRepository(SPMockBuilder().createSharedPreferences(), mockResources)
        repository = StudyScreenRepository(prefs)
    }

    @Test
    fun `custom scheduling js is correctly retrieved`() =
        runTest {
            val js = CollectionManager.withCol { cardStateCustomizer }
            assertEquals(js, repository.getCustomSchedulingJs())

            val newJs = "console.log('Anki is awesome!');"
            CollectionManager.withCol { cardStateCustomizer = newJs }
            assertEquals(newJs, repository.getCustomSchedulingJs())
        }

    @Test
    fun `shouldShowNextTimes is correctly retrieved`() =
        runTest {
            val defaultValue = CollectionPreferences.getShowIntervalOnButtons()
            assertEquals(defaultValue, repository.getShouldShowNextTimes())

            suspend fun assertNewValue(newValue: Boolean) {
                CollectionPreferences.setShowIntervalsOnButtons(newValue)
                assertEquals(newValue, repository.getShouldShowNextTimes())
            }

            assertNewValue(true)
            assertNewValue(false)
        }
}
