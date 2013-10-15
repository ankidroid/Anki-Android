/*
 * Copyright 2012 Evernote Corporation.
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
package com.evernote.client.android;

import com.evernote.edam.type.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EvernoteUtil {

  /**
   * The ENML preamble to every Evernote note.
   * Note content goes between <en-note> and </en-note>
   */
  public static final String NOTE_PREFIX =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
          "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" +
          "<en-note>";

  /**
   * The ENML postamble to every Evernote note
   */
  public static final String NOTE_SUFFIX = "</en-note>";

  /**
   * One-way hashing function used for providing a checksum of EDAM data
   */
  private static final String EDAM_HASH_ALGORITHM = "MD5";

  /**
   * Create an ENML &lt;en-media&gt; tag for the specified Resource object.
   */
  public static String createEnMediaTag(Resource resource) {
    return "<en-media hash=\"" + bytesToHex(resource.getData().getBodyHash()) +
        "\" type=\"" + resource.getMime() + "\"/>";
  }

  /**
   * Returns an MD5 checksum of the provided array of bytes.
   */
  public static byte[] hash(byte[] body) {
    try {
      return MessageDigest.getInstance(EDAM_HASH_ALGORITHM).digest(body);
    } catch (NoSuchAlgorithmException e) {
      throw new EvernoteUtilException(EDAM_HASH_ALGORITHM + " not supported", e);
    }
  }

  /**
   * Returns an MD5 checksum of the contents of the provided InputStream.
   */
  public static byte[] hash(InputStream in) throws IOException {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance(EDAM_HASH_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new EvernoteUtilException(EDAM_HASH_ALGORITHM + " not supported", e);
    }
    byte[] buf = new byte[1024];
    int n;
    while ((n = in.read(buf)) != -1) {
      digest.update(buf, 0, n);
    }
    return digest.digest();
  }

  /**
   * Converts the provided byte array into a hexadecimal string
   * with two characters per byte.
   */
  public static String bytesToHex(byte[] bytes) {
    return bytesToHex(bytes, false);
  }

  /**
   * Takes the provided byte array and converts it into a hexadecimal string
   * with two characters per byte.
   *
   * @param withSpaces if true, include a space character between each hex-rendered
   *                   byte for readability.
   */
  public static String bytesToHex(byte[] bytes, boolean withSpaces) {
    StringBuilder sb = new StringBuilder();
    for (byte hashByte : bytes) {
      int intVal = 0xff & hashByte;
      if (intVal < 0x10) {
        sb.append('0');
      }
      sb.append(Integer.toHexString(intVal));
      if (withSpaces) {
        sb.append(' ');
      }
    }
    return sb.toString();
  }

  /**
   * Takes a string in hexadecimal format and converts it to a binary byte
   * array. This does no checking of the format of the input, so this should
   * only be used after confirming the format or origin of the string. The input
   * string should only contain the hex data, two characters per byte.
   */
  public static byte[] hexToBytes(String hexString) {
    byte[] result = new byte[hexString.length() / 2];
    for (int i = 0; i < result.length; ++i) {
      int offset = i * 2;
      result[i] = (byte) Integer.parseInt(hexString.substring(offset,
          offset + 2), 16);
    }
    return result;
  }

  /**
   * A runtime exception that will be thrown when we hit an error that should
   * "never" occur ... e.g. if the JVM doesn't know about UTF-8 or MD5.
   */
  @SuppressWarnings("serial")
  private static final class EvernoteUtilException extends RuntimeException {
    public EvernoteUtilException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
