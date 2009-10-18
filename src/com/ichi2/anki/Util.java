package com.ichi2.anki;

import java.util.Random;
import java.util.TreeSet;

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
