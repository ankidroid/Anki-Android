/****************************************************************************************
* Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
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

package com.ichi2.anki;

import java.util.Random;
import java.util.TreeSet;

/**
 * TODO comments
 */
public class Util {
	private static TreeSet<Integer> idTree;
	private static long idTime;

	public static long genID() {
		long time = System.currentTimeMillis();
		long id;
		int rand;
		Random random = new Random();

		if (idTree == null) {
			idTree = new TreeSet<Integer>();
			idTime = time;
		}
		else if (idTime != time) {
			idTime = time;
			idTree.clear();
		}

		while (true) {
			rand = random.nextInt(2^23);
			if (!idTree.contains(new Integer(rand))) {
				idTree.add(new Integer(rand));
				break;
			}
		}
		id = rand << 41 | time;
		return id;
	}
}
