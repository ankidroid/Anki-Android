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

package com.ichi2.libanki.backend.exception;

import net.ankiweb.rsdroid.RustCleanup;

@RustCleanup("All of these should be removed when JavaBackend is removed")
public class BackendNotSupportedException extends Exception {
    public RuntimeException alreadyUsingRustBackend() {
        return new RuntimeException("A BackendNotSupportedException should not occur when using Rust backend", this);
    }
}
