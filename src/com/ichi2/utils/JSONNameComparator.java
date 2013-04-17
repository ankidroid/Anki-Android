package com.ichi2.utils;

import java.util.Comparator;

import org.json.JSONException;
import org.json.JSONObject;

public class JSONNameComparator implements Comparator<JSONObject>
{
	@Override
	public int compare(JSONObject lhs, JSONObject rhs)
	{
		String[] o1;
		String[] o2;
		try
		{
			o1 = lhs.getString("name").split("::");
			o2 = rhs.getString("name").split("::");
		}
		catch (JSONException e)
		{
			throw new RuntimeException(e);
		}
		for (int i = 0; i < Math.min(o1.length, o2.length); i++)
		{
			int result = o1[i].compareToIgnoreCase(o2[i]);
			if (result != 0)
			{
				return result;
			}
		}
		if (o1.length < o2.length)
		{
			return -1;
		}
		else if (o1.length > o2.length)
		{
			return 1;
		}
		else
		{
			return 0;
		}
	}
}
