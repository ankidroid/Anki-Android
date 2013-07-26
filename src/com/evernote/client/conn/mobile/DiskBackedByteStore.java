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

import java.io.*;

/**
 * Implements an OutputStream that stores data to a temporary file on disk.
 * Used by TAndroidHttpClient to write Thrift messages to disk before
 * POSTing them to the Thrift server.
 * <p/>
 * You should not need to interact with this class directly.
 */
public class DiskBackedByteStore extends OutputStream {

  protected File file;

  /**
   * The maximum amount of memory to use before writing to disk
   */
  protected int maxMemory;

  protected FileOutputStream fileoutputStream = null;
  protected ByteArrayOutputStream byteArray = null;
  protected FileInputStream fileInputStream = null;
  protected OutputStream current = null;
  protected int size = 0;
  protected Exception exception;
  protected File tempPath;

  /**
   * Constructor that sets the exact name of the file to use
   * if we have to swap data out to secondary store.
   *
   * @param file      The full pathname where we will swap data.
   * @param maxMemory The size, in bytes, that we will buffer
   *                  until we swap.
   */
  public DiskBackedByteStore(File file, int maxMemory) {
    this.file = file;
    this.maxMemory = maxMemory;
  }

  public DiskBackedByteStore(File parentDir, String prefix, int maxMemory)
      throws IOException {
    parentDir.mkdirs();
    tempPath = parentDir;
    this.file = makeTempFile();
    this.maxMemory = maxMemory;
  }

  protected File makeTempFile() {
    return new File(tempPath, (Math.random() * Long.MAX_VALUE) + ".tft");
  }

  @Override
  public void write(byte[] buffer, int offset, int count) {
    initBuffers();
    try {
      if (isSwapRequired(count)) {
        swapToDisk();
      }
      size += count;
      current.write(buffer, offset, count);
    } catch (Exception e) {
      exception = e;
    }
  }

  private boolean isSwapRequired(int delta) {
    return size + delta > maxMemory && byteArray != null;
  }

  @Override
  public void write(int oneByte) {
    try {
      initBuffers();
      if (isSwapRequired(1)) {
        swapToDisk();
      }
      size++;
      current.write(oneByte);
    } catch (Exception e) {
      exception = e;
    }
  }

  private void initBuffers() {
    if (current == null) {
      current = byteArray = new ByteArrayOutputStream();
    }
  }

  protected void swapToDisk() throws FileNotFoundException, IOException {
    // Swap in disk
    fileoutputStream = new FileOutputStream(file);
    byteArray.writeTo(fileoutputStream);
    byteArray = null;
    current = fileoutputStream;
  }

  public void clear() {
    byteArray = null;
    current = null;
    if (fileInputStream != null) {
      try {
        fileInputStream.close();
      } catch (IOException e) {
      }
    }
    fileInputStream = null;
    size = 0;
  }

  public int getSize() {
    return size;
  }

  public InputStream getInputStream() throws IOException {
    current.close();
    if (byteArray != null) {
      return new ByteArrayInputStream(byteArray.toByteArray());
    } else {
      return fileInputStream = new FileInputStream(file);
    }
  }

  public Exception getException() {
    return exception;
  }

  public void reset() throws IOException {
    clear();
    if (file.isFile()) {
      file.delete();
    }
    file = makeTempFile();
    exception = null;
  }
}
