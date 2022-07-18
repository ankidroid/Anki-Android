/***************************************************************************************
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>                       *
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

package com.ichi2.libanki.importer

import com.ichi2.libanki.CollectionV16

// These take and return bytes that the frontend TypeScript code will encode/decode.

fun CollectionV16.getCsvMetadataRaw(input: ByteArray): ByteArray {
    return backend.getCsvMetadataRaw(input)
}

fun CollectionV16.importCsvRaw(input: ByteArray): ByteArray {
    return backend.importCsvRaw(input)
}
