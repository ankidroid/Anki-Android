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

import android.database.Cursor;
import android.text.TextUtils;

import com.ichi2.utils.StringUtil;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

public class TextNoteExporter extends Exporter {

    private static final String EXT = ".txt";
    private final boolean mIncludedTags;
    private final boolean mIncludeID;


    public TextNoteExporter(@NonNull Collection col, boolean includeID, boolean includedTags, boolean includeHTML) {
        super(col);
        mIncludedTags = includedTags;
        mIncludeHTML = includeHTML;
        mIncludeID = includeID;
    }


    public TextNoteExporter(@NonNull Collection col, @NonNull Long did, boolean includeID, boolean includedTags, boolean includeHTML) {
        super(col, did);
        mIncludedTags = includedTags;
        mIncludeHTML = includeHTML;
        mIncludeID = includeID;
    }


    public void doExport(String path) throws IOException {
        final Long[] ids = cardIds();

        final String queryStr = String.format(
                "SELECT guid, flds, tags from notes " +
                        "WHERE id in " +
                        "(SELECT nid from cards WHERE cards.id in %s)", Utils.ids2str(cardIds()));

        final List<String> data = new ArrayList<>();

        try (final Cursor cursor = mCol.getDb().query(queryStr)) {
            while (cursor.moveToNext()) {
                final String id = cursor.getString(0);
                final String flds = cursor.getString(1);
                final String tags = cursor.getString(2);

                final List<String> row = new ArrayList<>();

                if (mIncludeID) {
                    row.add(id);
                }

                for (String field : Utils.splitFields(flds)) {
                    row.add(processText(field));
                }

                if (mIncludedTags) {
                    row.add(StringUtil.strip(tags));
                }

                data.add(TextUtils.join("\t", row));
            }
        }

        mCount = data.size();
        final String out = TextUtils.join("\n", data);

        try (BufferedWriter writer =
                     new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            writer.write(out.toString());
        }
    }
}