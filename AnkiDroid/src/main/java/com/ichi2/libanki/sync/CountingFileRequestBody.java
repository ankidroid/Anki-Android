/****************************************************************************************
 * Copyright (c) 2019 Mike Hardy <github@mikehardy.net>                                 *
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

package com.ichi2.libanki.sync;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;


public class CountingFileRequestBody extends RequestBody {

    private static final int SEGMENT_SIZE = 8092; // okio.Segment.SIZE (internal, copy required)

    private final File mFile;
    private final ProgressListener mListener;
    private final String mContentType;

    public CountingFileRequestBody(File file, String contentType, ProgressListener listener) {
        this.mFile = file;
        this.mContentType = contentType;
        this.mListener = listener;
    }

    @Override
    public long contentLength() {
        return mFile.length();
    }

    @Override
    public MediaType contentType() {
        return MediaType.parse(mContentType);
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        Source source = null;
        try {
            source = Okio.source(mFile);
            long read;

            while ((read = source.read(sink.getBuffer(), SEGMENT_SIZE)) != -1) {
                sink.flush();
                this.mListener.transferred(read);
            }
        } finally {
            Util.closeQuietly(source);
        }
    }

    public interface ProgressListener {
        void transferred(long num);
    }
}