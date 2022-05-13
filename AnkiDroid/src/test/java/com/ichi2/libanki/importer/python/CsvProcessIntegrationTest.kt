/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.libanki.importer.python

import android.os.Build
import androidx.annotation.RequiresApi
import com.ichi2.libanki.importer.python.CsvDialect.Quoting
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import java.util.*

/**
 * Inputs are similar to the following.
 * ... input = '"John ""Da Man""",Rep,120 Fake St.,Falsey, NJ,00000'
 * ... dialect = csv.Sniffer().sniff(input, None)
 * ... pp(dialect.doublequote)        # True
 * ... pp(dialect.skipinitialspace)   # False
 * ... pp(dialect.quoting)            # 0
 * ... pp(dialect.delimiter)          # ','
 * ... pp(dialect.quotechar)          # '"'
 * ... pp(dialect.escapechar)         # None
 * ... pp(dialect.lineterminator)     #'\r\n'
 * ... iter = csv.reader([input], dialect = dialect)
 * ... for r in iter:
 * ...     pp(r)
 * ['John "Da Man"', 'Rep', '120 Fake St.', 'Falsey', ' NJ', '00000']
 */
@KotlinCleanup("fix the dependency to work under any Java version")
@RequiresApi(api = Build.VERSION_CODES.O) // CsvSniffer & sniff
@KotlinCleanup("is->equalTo")
class CsvProcessIntegrationTest {
    @Test
    fun quotedDelimiterTest() {
        val input = "\"John \"\"Da Man\"\"\",Rep,120 Fake St.,Falsey, NJ,00000"
        val dialect = CsvSniffer().sniff(input, null)

        assertThat("doublequote", dialect.mDoublequote, `is`(true))
        assertThat("skipinitialspace", dialect.mSkipInitialSpace, `is`(false))
        assertThat("quoting", dialect.mQuoting, `is`(Quoting.QUOTE_MINIMAL))
        assertThat("delimiter", dialect.mDelimiter, `is`(','))
        assertThat("quotechar", dialect.mQuotechar, `is`('"'))
        assertThat("escapechar", dialect.mEscapechar, `is`('\u0000'))
        assertThat("lineterminator", dialect.mLineTerminator, `is`("\r\n"))

        assertThat(getFields(dialect, input), contains("John \"Da Man\"", "Rep", "120 Fake St.", "Falsey", " NJ", "00000"))
    }

    private fun getFields(dialect: CsvDialect, input: String): List<String>? {
        val list = Collections.singletonList(input)
        val reader = CsvReader.fromDialect(list.iterator(), dialect)
        return reader.iterator().next()
    }
}
