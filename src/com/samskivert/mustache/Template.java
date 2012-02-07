//
// JMustache - A Java implementation of the Mustache templating language
// http://github.com/samskivert/jmustache/blob/master/LICENSE

package com.samskivert.mustache;

import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

/**
 * Represents a compiled template. Templates are executed with a <em>context</em> to generate
 * output. The context can be any tree of objects. Variables are resolved against the context.
 * For anki, we only support the case where context is a Map interface
 */
public class Template
{
    /**
     * Executes this template with the given context, writing the results to the supplied writer.
     * @throws MustacheException if an error occurs while executing or writing the template.
     */
    public void execute (Object context, Writer out) throws MustacheException
    {
        Context ctx = new Context(context, null, 0, Mode.OTHER);
        for (Segment seg : _segs) {
            seg.execute(this, ctx, out);
        }
    }

    /**
     * Executes this template with the given context, returning the results as a string.
     * @throws MustacheException if an error occurs while executing or writing the template.
     */
    public String execute (Object context) throws MustacheException
    {
        StringWriter out = new StringWriter();
        execute(context, out);
        return out.toString();
    }

    protected Template (Segment[] segs)
    {
        _segs = segs;
    }

    /**
     * Called by executing segments to obtain the value of the specified variable in the supplied
     * context.
     *
     * @param ctx the context in which to look up the variable.
     * @param name the name of the variable to be resolved
     */
    protected Object getValue (Context ctx, String name, int line)
    {
        // anki - we don't use any compound keys
        // if (name.indexOf(".") != -1) { ... }

        while (ctx != null) {
            Object value = getValueIn(ctx.data, name, line);
            if (value != null) {
                return value;
            }
            ctx = ctx.parent;
        }
        // Graceful failing, no need to throw exception
        Log.e(AnkiDroidApp.TAG, "No key, method or field with name '" + name + "' on line " + line);
        return new String("{unknown field " + name + "}");
    }

    protected Object getValueIn (Object data, String name, int line)
    {
        if (data == null) {
            throw new NullPointerException(
                "Null context for variable '" + name + "' on line " + line);
        }

        VariableFetcher fetcher = createFetcher(data.getClass(), name);

        // if we were unable to create a fetcher, just return null and our caller can either try
        // the parent context, or do le freak out
        if (fetcher == null) {
            return null;
        }

        try {
            Object value = fetcher.get(data, name);
            return value;
        } catch (Exception e) {
            throw new MustacheException(
                "Failure fetching variable '" + name + "' on line " + line, e);
        }
    }

    protected final Segment[] _segs;

    protected static VariableFetcher createFetcher (Class<?> cclass, String name)
    {

        if (Map.class.isAssignableFrom(cclass)) {
            return MAP_FETCHER;
        }

        return null;
    }

    protected static enum Mode { FIRST, OTHER, LAST };

    protected static class Context
    {
        public final Object data;
        public final Context parent;
        public final int index;
        public final Mode mode;

        public Context (Object data, Context parent, int index, Mode mode) {
            this.data = data;
            this.parent = parent;
            this.index = index;
            this.mode = mode;
        }

        public Context nest (Object data, int index, Mode mode) {
            return new Context(data, this, index, mode);
        }
    }

    /** A template is broken into segments. */
    protected static abstract class Segment
    {
        abstract void execute (Template tmpl, Context ctx, Writer out);

        protected static void write (Writer out, String data) {
            try {
                out.write(data);
            } catch (IOException ioe) {
                throw new MustacheException(ioe);
            }
        }
    }

    protected static abstract class VariableFetcher {
        abstract Object get (Object ctx, String name) throws Exception;
    }

    protected static final VariableFetcher MAP_FETCHER = new VariableFetcher() {
        public Object get (Object ctx, String name) throws Exception {
            return ((Map<?,?>)ctx).get(name);
        }
    };
}
