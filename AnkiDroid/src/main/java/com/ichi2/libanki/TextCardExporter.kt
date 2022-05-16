/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
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
package com.ichi2.libanki;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import androidx.annotation.NonNull;

public class TextCardExporter extends Exporter {

    public static final String EXT = ".txt";


    public TextCardExporter(@NonNull Collection col, boolean includeHTML) {
        super(col);
        mIncludeHTML = includeHTML;
    }


    public TextCardExporter(@NonNull Collection col, @NonNull Long did, boolean includeHTML) {
        super(col, did);
        mIncludeHTML = includeHTML;
    }


    /**
     * Exports into a csv(tsv) file
     *
     * @param path path of the file
     * @throws IOException encountered an error while writing the csv file
     */
    public void doExport(@NonNull String path) throws IOException {
        final Long[] ids = cardIds();
        Arrays.sort(ids);

        final StringBuilder out = new StringBuilder();
        for (Long cid : ids) {
            final Card c = mCol.getCard(cid);
            out.append(esc(c.q()));
            out.append("\t");
            out.append(esc(c.a()));
            out.append("\n");
        }

        try (BufferedWriter writer =
                     new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            writer.write(out.toString());
        }
    }


    /**
     * Strip off the repeated question in answer if exists
     *
     * @param s answer
     * @return stripped answer
     */
    @NonNull
    private String esc(@NonNull String s) {
        s = s.replaceAll("(?si)^.*<hr id=answer>\\n*", "");
        return processText(s);
    }
}
