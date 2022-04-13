/*
 Copyright (c) 2021 Mrudul Tora <mrudultora@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.analytics

import com.ichi2.utils.KotlinCleanup
import org.junit.Assert
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.lang.RuntimeException
import java.lang.reflect.Field
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.Throws

/**
 * This class contains two nested classes and is using the concept of Enclosed runner that internally works as a Suite.
 * The first inner class is a Parameterized class and will run according to the size of test case, while the second
 * inner class is non-parameterized and will run only once.
 */
@RunWith(Enclosed::class)
@KotlinCleanup("auto IDE lint")
@KotlinCleanup("Move away from Java streams")
@KotlinCleanup("Remove @Throws")
object AnalyticsConstantsTest {
    private val listOfConstantFields: MutableList<String> = ArrayList()

    init {
        listOfConstantFields.add("Opened HelpDialogBox")
        listOfConstantFields.add("Opened Using AnkiDroid")
        listOfConstantFields.add("Opened Get Help")
        listOfConstantFields.add("Opened Support AnkiDroid")
        listOfConstantFields.add("Opened Community")
        listOfConstantFields.add("Opened AnkiDroid Manual")
        listOfConstantFields.add("Opened Anki Manual")
        listOfConstantFields.add("Opened AnkiDroid FAQ")
        listOfConstantFields.add("Opened Mailing List")
        listOfConstantFields.add("Opened Report a Bug")
        listOfConstantFields.add("Opened Donate")
        listOfConstantFields.add("Opened Translate")
        listOfConstantFields.add("Opened Develop")
        listOfConstantFields.add("Opened Rate")
        listOfConstantFields.add("Opened Other")
        listOfConstantFields.add("Opened Send Feedback")
        listOfConstantFields.add("Opened Anki Forums")
        listOfConstantFields.add("Opened Reddit")
        listOfConstantFields.add("Opened Discord")
        listOfConstantFields.add("Opened Facebook")
        listOfConstantFields.add("Opened Twitter")
        listOfConstantFields.add("Opened Privacy")
        listOfConstantFields.add("Opened AnkiDroid Privacy Policy")
        listOfConstantFields.add("Opened AnkiWeb Privacy Policy")
        listOfConstantFields.add("Opened AnkiWeb Terms and Conditions")
        listOfConstantFields.add("Exception Report")
        listOfConstantFields.add("Import APKG")
        listOfConstantFields.add("Import COLPKG")
    }

    internal val analyticsConstantFields: Stream<Field>
        get() = Arrays.stream(UsageAnalytics.Actions::class.java.declaredFields)
            .filter { x: Field -> x.isAnnotationPresent(AnalyticsConstant::class.java) }

    @RunWith(Parameterized::class)
    class AnalyticsConstantsFieldValuesTest(private val analyticsString: String) {
        /**
         * The message here means that the string being checked cannot be found in Actions class.
         * If encountered with this message, re-check the list present here and constants in Actions class, to resolve
         * the test failure.
         */
        @Test
        @Throws(IllegalAccessException::class)
        fun checkAnalyticsString() {
            Assert.assertEquals(
                "Re-check if you renamed any string in the analytics string constants of Actions class or AnalyticsConstantsTest.listOfConstantFields. If so, revert them as those string constants must not change as they are compared in analytics.",
                analyticsString, getStringFromReflection(analyticsString)
            )
        }

        @Throws(IllegalAccessException::class)
        fun getStringFromReflection(analyticsStringToBeChecked: String): String? {
            for (value in analyticsConstantFields.collect(Collectors.toList())) {
                val reflectedValue = value[null]
                if (reflectedValue == analyticsStringToBeChecked) {
                    return reflectedValue as String
                }
            }
            return null
        }

        companion object {
            @JvmStatic
            @Parameterized.Parameters
            fun addAnalyticsConstants(): List<String> {
                return listOfConstantFields
            }
        }
    }

    class AnalyticsConstantsFieldLengthTest {
        @Test
        fun fieldSizeEqualsListOfConstantFields() {
            if (fieldSize > listOfConstantFields.size) {
                Assert.assertEquals(
                    "Add the newly added analytics constant to AnalyticsConstantsTest.listOfConstantFields. NOTE: Constants should not be renamed as we cannot compare these in analytics.",
                    listOfConstantFields.size.toLong(), fieldSize
                )
            } else if (fieldSize < listOfConstantFields.size) {
                Assert.assertEquals(
                    "If a constant is removed, it should be removed from AnalyticsConstantsTest.listOfConstantFields. NOTE: Constants should not be renamed as we cannot compare these in analytics.",
                    listOfConstantFields.size.toLong(), fieldSize
                )
            } else {
                Assert.assertEquals(listOfConstantFields.size.toLong(), fieldSize)
            }
        }

        /**
         * This test is used to check whether all the string constants of Actions are annotated with @AnalyticsConstant.
         * If not, then a runtime exception is thrown.
         */
        @Test
        fun fieldAnnotatedOrNot() {
            val actions = UsageAnalytics.Actions()
            val field: Array<Field>
            field = actions.javaClass.declaredFields
            for (value in field) {
                if (!value.isAnnotationPresent(AnalyticsConstant::class.java) && !value.isSynthetic) {
                    throw RuntimeException("All the fields in Actions class must be annotated with @AnalyticsConstant. It seems " + value.name + " is not annotated.")
                }
            }
        }

        companion object {
            /**
             * This method is used to get the size of fields in Actions Class.
             * Because whenever a new constant is added in Actions Class but not added to the list present in this
             * class (listOfConstantFields) the test must fail.
             */
            val fieldSize: Long
                get() = analyticsConstantFields.count()
        }
    }
}
