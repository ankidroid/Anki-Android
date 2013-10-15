/*
 * Copyright 2012 Evernote Corporation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.client.conn.mobile;

import com.evernote.edam.type.Data;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TField;
import com.evernote.thrift.protocol.TProtocol;
import com.evernote.thrift.protocol.TStruct;
import com.evernote.thrift.protocol.TType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Implements a replacement for com.evernote.edam.type.Data that retrieves
 * the actual binary data to be sent from a File instead of a byte array.
 * This allows large Thrift messages to be assembled without the entire message
 * being stored in memory.
 * <p/>
 * To use this class, simply replace all uses of com.evernote.edam.type.Data with
 * com.evernote.android.edam.FileData when creating new Data objects to send to
 * Evernote.
 */
public class FileData extends Data {
  private static final TStruct STRUCT_DESC = new TStruct("Data");
  private static final TField BODY_HASH_FIELD_DESC =
      new TField("bodyHash", TType.STRING, (short) 1);
  private static final TField SIZE_FIELD_DESC =
      new TField("size", TType.I32, (short) 2);
  private static final TField BODY_FIELD_DESC =
      new TField("body", TType.STRING, (short) 3);

  private static final long serialVersionUID = 1L;
  private File mBodyFile;

  /**
   * Create a new FileData.
   *
   * @param bodyHash An MD5 hash of the binary data contained in the file.
   * @param file     The file containing the binary data.
   */
  public FileData(byte[] bodyHash, File file) {
    mBodyFile = file;
    setBodyHash(bodyHash);
    setSize((int) file.length());
  }

  @Override
  public void write(TProtocol oprot) throws TException {
    validate();
    oprot.writeStructBegin(STRUCT_DESC);
    if (this.getBodyHash() != null) {
      if (isSetBodyHash()) {
        oprot.writeFieldBegin(BODY_HASH_FIELD_DESC);
        oprot.writeBinary(ByteBuffer.wrap(this.getBodyHash()));
        oprot.writeFieldEnd();
      }
    }
    oprot.writeFieldBegin(SIZE_FIELD_DESC);
    oprot.writeI32(this.getSize());
    oprot.writeFieldEnd();
    if (this.mBodyFile != null && this.mBodyFile.isFile()) {
      oprot.writeFieldBegin(BODY_FIELD_DESC);
      InputStream s = null;
      try {
        s = new FileInputStream(mBodyFile);
        oprot.writeStream(s, this.mBodyFile.length());
      } catch (FileNotFoundException e) {
        throw new TException("Failed to write binary body:" + mBodyFile, e);
      } finally {
        try {
          if (s != null) {
            s.close();
          }
        } catch (Exception e) {
        }
      }
      oprot.writeFieldEnd();
    }
    oprot.writeFieldStop();
    oprot.writeStructEnd();
  }
}
