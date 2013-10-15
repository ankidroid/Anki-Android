/*
The MIT License

Copyright (c) 2010 Pablo Fernandez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

Modified by Evernote for use with the Evernote API.
*/

package com.evernote.client.oauth;

import org.scribe.exceptions.OAuthException;
import org.scribe.model.Token;
import org.scribe.utils.OAuthEncoder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Scribe AccessToken that contains Evernote-specific items from the OAuth response.
 */
public class EvernoteAuthToken extends Token {

  private static final long serialVersionUID = -6892516333656106315L;

  private static final Pattern NOTESTORE_REGEX = Pattern.compile("edam_noteStoreUrl=([^&]+)");
  private static final Pattern WEBAPI_REGEX = Pattern.compile("edam_webApiUrlPrefix=([^&]+)");
  private static final Pattern USERID_REGEX = Pattern.compile("edam_userId=([^&]+)");

  private String mNoteStoreUrl;
  private String mWebApiUrlPrefix;
  private int mUserId;

  public EvernoteAuthToken(Token token) {
    super(token.getToken(), token.getSecret(), token.getRawResponse());
    this.mNoteStoreUrl = extract(getRawResponse(), NOTESTORE_REGEX);
    this.mWebApiUrlPrefix = extract(getRawResponse(), WEBAPI_REGEX);
    this.mUserId = Integer.parseInt(extract(getRawResponse(), USERID_REGEX));
  }

  private String extract(String response, Pattern p) {
    Matcher matcher = p.matcher(response);
    if (matcher.find() && matcher.groupCount() >= 1) {
      return OAuthEncoder.decode(matcher.group(1));
    } else {
      throw new OAuthException("Response body is incorrect. " +
          "Can't extract token and secret from this: '" + response + "'", null);
    }
  }

  /**
   * Get the Evernote web service NoteStore URL from the OAuth access token response.
   */
  public String getNoteStoreUrl() {
    return mNoteStoreUrl;
  }

  /**
   * Get the Evernote web API URL prefix from the OAuth access token response.
   */
  public String getWebApiUrlPrefix() {
    return mWebApiUrlPrefix;
  }

  /**
   * Get the numeric Evernote user ID from the OAuth access token response.
   */
  public int getUserId() {
    return mUserId;
  }

}
