/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.testutil;

public class ThreadUtils {
    public static void sleep(int timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public static void runAndJoin(Runnable runnable, int timeout) {
        Thread t = new Thread(runnable);
        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
