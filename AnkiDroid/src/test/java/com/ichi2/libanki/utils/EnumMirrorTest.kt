/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.utils

//
// @RunWith(Parameterized::class)
// class EnumMirrorTest(val clazz: TestData) {
//
//    @Test
//    fun ensureEnumsHaveSameConstants() {
//        assertEquals(clazz.targetNames, clazz.mirrorNames, "A class marked with @EnumMirror should have all the enum constants of the class that it mirrors")
//    }
//
//    companion object {
//        @Suppress("deprecation")
//        @RustCleanup("remove suppress on BuiltinSortKind")
//        @Parameterized.Parameters(name = "{0}")
//        fun data(): Iterable<Array<Any>> = sequence<Array<Any>> {
//            // HACK: We list the classes manually as "Reflections" doesn't work on Android out the box
//            // and it would be better to code a gradle plugin to streamline the current hacks
//            // (use gradle to serialize the list of possible classes, and load that at runtime).
// //            yield(arrayOf(getClass(SortOrder.BuiltinSortKind.BuiltIn::class)))
//        }.asIterable()
//
//        @Suppress("unchecked_cast")
//        fun getClass(clazz: KClass<*>): TestData {
//            assertTrue(clazz.java.isEnum, "target class should be an enum")
//            val annotation = clazz.findAnnotation<EnumMirror>()
//            assertNotNull(annotation, "target class should have @EnumMirror")
//            val annotatedClass = annotation!!.value
//            assertTrue(annotatedClass.java.isEnum, "mirror target should be an enum")
//
//            return TestData(clazz as KClass<out Enum<*>>, annotatedClass as KClass<out Enum<*>>)
//        }
//
//        data class TestData(val clazz: KClass<out Enum<*>>, val shouldMirror: KClass<out Enum<*>>) {
//            private fun getEnumNames(enumClass: KClass<out Enum<*>>) = enumClass.java.enumConstants.map { it.name }
//            val targetNames; get() = getEnumNames(clazz)
//            val mirrorNames; get() = getEnumNames(shouldMirror)
//            override fun toString() = "${clazz.simpleName} -> ${shouldMirror.simpleName}"
//        }
//    }
// }
