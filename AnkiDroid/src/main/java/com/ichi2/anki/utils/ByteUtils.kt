/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

/**
 * Converts a size in bytes (represented as a [Double]) to megabytes.
 *
 * @return The size in megabytes as a [Double].
 */
fun Double.toMB(): Double = this / (1024 * 1024)

/**
 * Converts a size in bytes (represented as a [Long]) to megabytes by truncating any remainder.
 *
 * @return The size in megabytes as a [Long], rounded down to the nearest whole number.
 */
fun Long.toMB(): Long = this / (1024 * 1024)
