/****************************************************************************************
 * Copyright (c) 2021 mikunimaru <com.mikuni0@gmail.com>                          *
 *
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
 * this program. If not, see <http://www.gnu.org/licenses/>.                            *
 ****************************************************************************************/

package com.ichi2.anki;

import java.util.Locale;

public class LanguageUtils {

    /**
     * Convert a string representation of a locale, in the format returned by Locale.toString(),
     * into a Locale object, disregarding any script and extensions fields (i.e. using solely the
     * language, country and variant fields).
     * <p>
     * Returns a Locale object constructed from an empty string if the input string is null, empty
     * or contains more than 3 fields separated by underscores.
     */
    public static Locale localeFromStringIgnoringScriptAndExtensions(String localeCode) {
      if (localeCode == null) {
          return new Locale("");
      }

      localeCode = stripScriptAndExtensions(localeCode);

      String[] fields = localeCode.split("_");
      switch (fields.length) {
          case 1:
              return new Locale(fields[0]);
          case 2:
              return new Locale(fields[0], fields[1]);
          case 3:
              return new Locale(fields[0], fields[1], fields[2]);
          default:
              return new Locale("");
      }
  }

  private static String stripScriptAndExtensions(String localeCode) {
      int hashPos = localeCode.indexOf('#');
      if (hashPos >= 0) {
          localeCode = localeCode.substring(0, hashPos);
      }
      return localeCode;
  }
}
