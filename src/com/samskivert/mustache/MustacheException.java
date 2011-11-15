//
// JMustache - A Java implementation of the Mustache templating language
// http://github.com/samskivert/jmustache/blob/master/LICENSE

package com.samskivert.mustache;

/**
 * An exception thrown when an error occurs parsing or executing a Mustache template.
 */
@SuppressWarnings("serial")
public class MustacheException extends RuntimeException
{
    public MustacheException (String message) {
        super(message);
    }

    public MustacheException (Throwable cause) {
        super(cause);
    }

    public MustacheException (String message, Throwable cause) {
        super(message, cause);
    }
}
