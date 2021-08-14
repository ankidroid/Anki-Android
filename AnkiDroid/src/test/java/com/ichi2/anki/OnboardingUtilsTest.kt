package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.full.companionObject
import kotlin.reflect.typeOf

@RunWith(AndroidJUnit4::class)
class OnboardingUtilsTest : RobolectricTest() {

    enum class Feature : OnboardingFlag
    @Test
    @kotlin.ExperimentalStdlibApi
    fun resetResetAllElementsFromOnboarding() {
        // Creating an object is mandatory to execute Onboarding's init code.
        val ou = object : Onboarding<Feature>(targetContext, mutableListOf()) {
        }

        assertThat(
            "The elements resetted in featureConstants should be exactly the same as Onboarding companion's member",
            Onboarding::class.companionObject!!.members.filter { it.isFinal && it.returnType == typeOf<String>() }.size,
            equalTo(OnboardingUtils.Companion.featureConstants.size)
        )
    }
}
