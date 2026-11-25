// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.os.Bundle
import android.os.Parcel
import androidx.activity.result.ActivityResult
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import com.ichi2.testutils.rules.MainDispatcherRule
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class ExternalImageEditorViewModelTest : RobolectricTest(loadBackend = false) {
    private val editedImage = editorResultImageBytes()
    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher(scheduler))

    @Before
    fun resetFileProviderRoots() {
        clearFileProviderRoots()
    }

    @Test
    fun `a delivered result is imported after process state restoration`() {
        val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
        val working = File(targetContext.cacheDir, "working.png").apply { writeBytes(editedImage) }
        val edit = ExternalImageEditorViewModel.Edit.Importing(original.path, working.path, ActivityResult(Activity.RESULT_OK, null))
        withRestoredEditor(edit) { viewModel ->
            val finished = assertIs<ExternalImageEditorViewModel.Edit.Finished>(viewModel.state.value)
            val accepted = File(requireNotNull(finished.acceptedPath))
            assertContentEquals(editedImage, accepted.readBytes())
            viewModel.consumed()
            scheduler.runCurrent()
            assertFalse(working.exists())
            assertThat(original.readText(), equalTo("original"))
            assertContentEquals(editedImage, accepted.readBytes())
        }
    }

    @Test
    fun `preparation resumes after process state restoration`() {
        val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
        withRestoredEditor(ExternalImageEditorViewModel.Edit.Preparing(original.path)) { viewModel ->
            val ready = assertIs<ExternalImageEditorViewModel.Edit.Ready>(viewModel.state.value)
            val working = File(ready.workingPath)
            assertThat(working.readText(), equalTo("original"))
            viewModel.launched()
            viewModel.readResult(ActivityResult(Activity.RESULT_CANCELED, null))
            scheduler.runCurrent()
            assertIs<ExternalImageEditorViewModel.Edit.Finished>(viewModel.state.value)
            viewModel.consumed()
            scheduler.runCurrent()
            assertFalse(working.exists())
            assertThat(original.readText(), equalTo("original"))
        }
    }

    @Test
    fun `clearing a stopped editor preserves its working copy for restoration`() {
        val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
        val working = File(targetContext.cacheDir, "working.png").apply { writeBytes(editedImage) }
        withRestoredEditor(ExternalImageEditorViewModel.Edit.Editing(original.path, working.path)) { }
        assertContentEquals(editedImage, working.readBytes())
        assertThat(original.readText(), equalTo("original"))
    }

    @Test
    fun `closing with an unconsumed result removes both copies`() {
        val original = File(targetContext.cacheDir, "original.png").apply { writeText("original") }
        val working = File(targetContext.cacheDir, "working.png").apply { writeBytes(editedImage) }
        lateinit var accepted: File
        val viewModel =
            withRestoredEditor(
                ExternalImageEditorViewModel.Edit.Importing(original.path, working.path, ActivityResult(Activity.RESULT_OK, null)),
            ) { model ->
                val finished = assertIs<ExternalImageEditorViewModel.Edit.Finished>(model.state.value)
                accepted = File(requireNotNull(finished.acceptedPath))
                model
            }
        assertContentEquals(editedImage, working.readBytes())
        assertContentEquals(editedImage, accepted.readBytes())
        viewModel.discard()
        scheduler.runCurrent()
        assertFalse(working.exists())
        assertFalse(accepted.exists())
        assertThat(original.readText(), equalTo("original"))
    }

    private fun <T> withRestoredEditor(
        edit: ExternalImageEditorViewModel.Edit,
        block: (ExternalImageEditorViewModel) -> T,
    ): T {
        val parcel = Parcel.obtain()
        val restored =
            try {
                parcel.writeBundle(Bundle().apply { putParcelable("edit", edit) })
                parcel.setDataPosition(0)
                BundleCompat.getParcelable(
                    parcel.readBundle(edit.javaClass.classLoader)!!,
                    "edit",
                    ExternalImageEditorViewModel.Edit::class.java,
                )!!
            } finally {
                parcel.recycle()
            }
        val store = ViewModelStore()
        val model =
            ExternalImageEditorViewModel(
                SavedStateHandle(mapOf("externalEdit" to restored)),
                ExternalImageEditor(targetContext.applicationContext, targetContext.cacheDir, StandardTestDispatcher(scheduler)),
            )
        store.put("editor", model)
        return try {
            scheduler.runCurrent()
            block(model)
        } finally {
            store.clear()
            scheduler.runCurrent()
        }
    }
}
