// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.ichi2.anki.gradle.UnitTestModules.All
import com.ichi2.anki.gradle.UnitTestModules.Selected
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TestModuleSelectionTest {
    private val moduleDirectories =
        listOf(
            ModuleDirectory(projectPath = ":AnkiDroid", relativeDirectory = "AnkiDroid"),
            ModuleDirectory(projectPath = ":common", relativeDirectory = "common"),
            ModuleDirectory(projectPath = ":common:android", relativeDirectory = "common/android"),
            ModuleDirectory(projectPath = ":feature:browse", relativeDirectory = "feature/browse"),
            ModuleDirectory(projectPath = ":widgets", relativeDirectory = "components/widgets"),
        )
    private val allModulePaths = moduleDirectories.map { it.projectPath }.toSet()
    private val dependencies =
        mapOf(
            ":AnkiDroid" to listOf(UnitTestProjectDependency(":feature:browse"), UnitTestProjectDependency(":widgets")),
            ":feature:browse" to listOf(UnitTestProjectDependency(":common:android")),
            ":common:android" to listOf(UnitTestProjectDependency(":common")),
            ":widgets" to listOf(UnitTestProjectDependency(":common")),
        )

    @Test
    fun `app changes do not select its dependencies`() {
        assertEquals(Selected(setOf(":AnkiDroid")), select("AnkiDroid/src/main/Example.kt"))
    }

    @Test
    fun `dependency changes select transitive dependents`() {
        assertEquals(Selected(allModulePaths), select("common/src/main/Example.kt"))
    }

    @Test
    fun `private unit test changes select only their own module`() {
        assertEquals(Selected(setOf(":widgets")), select("components/widgets/src/test/Example.kt"))
        assertEquals(Selected(setOf(":common")), select("common/src/test/resources/data.json"))
        assertEquals(
            Selected(setOf(":common", ":widgets", ":AnkiDroid")),
            select("common/src/test/Example.kt", "components/widgets/src/main/Example.kt"),
        )
        assertEquals(Selected(allModulePaths), select("common/src/testFixtures/Fixture.kt"))
    }

    @Test
    fun `nested modules use their actual directories and longest path match`() {
        assertEquals(
            Selected(setOf(":common:android", ":feature:browse", ":AnkiDroid")),
            select("common/android/src/main/Example.kt"),
        )
        assertEquals(Selected(setOf(":widgets", ":AnkiDroid")), select("components/widgets/src/main/Example.kt"))
    }

    @Test
    fun `multiple changed modules are combined`() {
        assertEquals(
            Selected(setOf(":widgets", ":feature:browse", ":AnkiDroid")),
            select("components/widgets/src/main/Example.kt", "feature/browse/src/main/Example.kt"),
        )
    }

    @Test
    fun `cycles terminate`() {
        val cycle = dependencies + (":common" to listOf(UnitTestProjectDependency(":widgets")))
        assertEquals(
            Selected(allModulePaths),
            affectedTestModules(listOf("components/widgets/src/main/Example.kt"), moduleDirectories, cycle),
        )
    }

    @Test
    fun `shared build configuration and unknown paths select everything`() {
        for (path in listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "common/build.gradle.kts",
            "common/build.gradle",
            "gradle/libs.versions.toml",
            "buildSrc/src/main/Example.kt",
            ".github/workflows/tests_unit.yml",
            "unknown/src/main/Example.kt",
        )) {
            assertEquals(All, select(path), path)
        }
    }

    @Test
    fun `documentation only and empty comparisons select nothing`() {
        assertEquals(Selected(emptySet()), select("README.md", "docs/example.md"))
        assertEquals(Selected(emptySet()), select())
        assertEquals(Selected(setOf(":AnkiDroid")), select("README.md", "AnkiDroid/src/main/Example.kt"))
    }

    @Test
    fun `output distinguishes full empty and specific selections`() {
        assertEquals("modules=*\n", testSelectionOutput(All))
        assertEquals("modules=\n", testSelectionOutput(Selected(emptySet())))
        assertEquals("modules=:common\n", testSelectionOutput(Selected(setOf(":common"))))
        assertEquals(
            "modules=:AnkiDroid,:widgets\n",
            testSelectionOutput(Selected(setOf(":widgets", ":AnkiDroid"))),
        )
        assertEquals("modules=\n", testSelectionOutput(Selected(setOf(":baselineprofile"))))
        assertEquals("modules=\n", testSelectionOutput(Selected(setOf(":lint-rules"))))
        assertEquals("modules=:common\n", testSelectionOutput(Selected(setOf(":common", ":baselineprofile", ":lint-rules"))))
    }

    private fun select(vararg paths: String) = affectedTestModules(paths.toList(), moduleDirectories, dependencies)
}
