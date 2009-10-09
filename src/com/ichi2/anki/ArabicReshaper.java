package com.ichi2.anki;

import java.lang.String;
import java.util.StringTokenizer;

public class ArabicReshaper
{
	// private String _RetString;
	private static char[][] ArabicGlphies =
	{
	{ 1569, 65152, 65163, 65164, 65152, 3 },
	{ 1570, 65153, 65153, 65154, 65154, 2 },
	{ 1571, 65155, 65155, 65156, 65156, 2 },
	{ 1572, 65157, 65157, 65158, 65158, 2 },
	{ 1573, 65159, 65159, 65160, 65160, 2 },
	{ 1575, 65165, 65165, 65166, 65166, 2 },
	{ 1576, 65167, 65169, 65170, 65168, 4 },
	{ 1577, 65171, 65171, 65172, 65172, 2 },
	{ 1578, 65173, 65175, 65176, 65174, 4 },
	{ 1579, 65177, 65179, 65180, 65178, 4 },
	{ 1580, 65181, 65183, 65184, 65182, 4 },
	{ 1581, 65185, 65187, 65188, 65186, 4 },
	{ 1582, 65189, 65191, 65192, 65190, 4 },
	{ 1583, 65193, 65193, 65194, 65194, 2 },
	{ 1584, 65195, 65195, 65196, 65196, 2 },
	{ 1585, 65197, 65197, 65198, 65198, 2 },
	{ 1586, 65199, 65199, 65200, 65200, 2 },
	{ 1587, 65201, 65203, 65204, 65202, 4 },
	{ 1588, 65205, 65207, 65208, 65206, 4 },
	{ 1589, 65209, 65211, 65212, 65210, 4 },
	{ 1590, 65213, 65215, 65216, 65214, 4 },
	{ 1591, 65217, 65219, 65218, 65220, 4 },
	{ 1592, 65221, 65223, 65222, 65222, 4 },
	{ 1593, 65225, 65227, 65228, 65226, 4 },
	{ 1594, 65229, 65231, 65232, 65230, 4 },
	{ 1601, 65233, 65235, 65236, 65234, 4 },
	{ 1602, 65237, 65239, 65240, 65238, 4 },
	{ 1603, 65241, 65243, 65244, 65242, 4 },
	{ 1604, 65245, 65247, 65248, 65246, 4 },
	{ 1605, 65249, 65251, 65252, 65250, 4 },
	{ 1606, 65253, 65255, 65256, 65254, 4 },
	{ 1607, 65257, 65259, 65260, 65258, 4 },
	{ 1608, 65261, 65261, 65262, 65262, 2 },
	{ 1609, 65263, 65263, 65264, 65264, 2 },
	{ 1574, 65161, 65161, 65162, 65162, 2 },
	{ 1610, 65265, 65267, 65268, 65266, 4 } };

	private static char GetReshapedGlphy(char Target, int Location)
	{
		for (int n = 0; n < 36; n++)
		{
			if (ArabicGlphies[n][0] == Target)
			{
				return ArabicGlphies[n][Location];
			}
		}
		return Target;
	}

	private static int GetGlphyType(char Target)
	{
		for (int n = 0; n < 36; n++)
		{
			if (ArabicGlphies[n][0] == Target)
				return ArabicGlphies[n][5];
		}
		return 0;
	}

	public static String ReshaperA(String UnA)
	{
		String x = "";
		String y;
		StringTokenizer parser = new StringTokenizer(UnA, ",. ", true);
		while (parser.hasMoreTokens())
		{
			y = parser.nextToken();
			if (y.length() <= 1)
				x = x + y;
			else
				x = x + Reshaper(y);
		}
		return x;
	}

	public static String Reshaper(String UnshapedAra)
	{
		String _RetString = "";

		int nLen = UnshapedAra.length();
		char[] bString = new char[nLen];
		UnshapedAra.getChars(0, nLen, bString, 0);

		if (GetGlphyType(bString[0]) == 2)
		{
			_RetString += GetReshapedGlphy(bString[0], 1);
		} else
		{
			_RetString += GetReshapedGlphy(bString[0], 2);
		}

		for (int nStringLoop = 1; nStringLoop < nLen - 1; nStringLoop++)
		{
			if (GetGlphyType(bString[nStringLoop - 1]) == 2)
			{
				_RetString += GetReshapedGlphy(bString[nStringLoop], 2);
			} else
			{
				_RetString += GetReshapedGlphy(bString[nStringLoop], 3);
			}
		}

		if (GetGlphyType(bString[nLen - 2]) == 2)
		{
			_RetString += GetReshapedGlphy(bString[nLen - 1], 1);
		} else
		{
			_RetString += GetReshapedGlphy(bString[nLen - 1], 4);
		}

		return _RetString;

	}
}