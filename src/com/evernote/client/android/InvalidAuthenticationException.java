package com.evernote.client.android;

/**
 * Exception used when an operation is called that requires a valid session without one.
 */
public class InvalidAuthenticationException extends Exception{
  /**
   * Constructor that takes a message
   * @param message
   */
  public InvalidAuthenticationException(String message) {
    super(message);
  }

  /**
   * Constructor that takes message and throwable
   * @param message
   * @param throwable
   */
  public InvalidAuthenticationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
