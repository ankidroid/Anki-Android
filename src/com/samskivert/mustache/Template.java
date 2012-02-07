//
// JMustache - A Java implementation of the Mustache templating language
// http://github.com/samskivert/jmustache/blob/master/LICENSE

package com.samskivert.mustache;

import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a compiled template. Templates are executed with a <em>context</em> to generate
 * output. The context can be any tree of objects. Variables are resolved against the context.
 * Given a name {@code foo}, the following mechanisms are supported for resolving its value
 * (and are sought in this order):
 * <ul>
 * <li>If the variable has the special name {@code this} the context object itself will be
 * returned. This is useful when iterating over lists.
 * <li>If the object is a {@link Map}, {@link Map#get} will be called with the string {@code foo}
 * as the key.
 * <li>A method named {@code foo} in the supplied object (with non-void return value).
 * <li>A method named {@code getFoo} in the supplied object (with non-void return value).
 * <li>A field named {@code foo} in the supplied object.
 * </ul>
 * <p> The field type, method return type, or map value type should correspond to the desired
 * behavior if the resolved name corresponds to a section. {@link Boolean} is used for showing or
 * hiding sections without binding a sub-context. Arrays, {@link Iterator} and {@link Iterable}
 * implementations are used for sections that repeat, with the context bound to the elements of the
 * array, iterator or iterable. Lambdas are current unsupported, though they would be easy enough
 * to add if desire exists. See the <a href="http://mustache.github.com/mustache.5.html">Mustache
 * documentation</a> for more details on section behavior. </p>
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
     * @param name the name of the variable to be resolved, which must be an interned string.
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

        Key key = new Key(data.getClass(), name);
        VariableFetcher fetcher = _fcache.get(key);
        if (fetcher != null) {
            try {
                return fetcher.get(data, name);
            } catch (Exception e) {
                // zoiks! non-monomorphic call site, update the cache and try again
                fetcher = createFetcher(key);
            }
        } else {
            fetcher = createFetcher(key);
        }

        // if we were unable to create a fetcher, just return null and our caller can either try
        // the parent context, or do le freak out
        if (fetcher == null) {
            return null;
        }

        try {
            Object value = fetcher.get(data, name);
            _fcache.put(key, fetcher);
            return value;
        } catch (Exception e) {
            throw new MustacheException(
                "Failure fetching variable '" + name + "' on line " + line, e);
        }
    }

    protected final Segment[] _segs;
    protected final Map<Key, VariableFetcher> _fcache =
        new ConcurrentHashMap<Key, VariableFetcher>();

    protected static VariableFetcher createFetcher (Key key)
    {

        if (Map.class.isAssignableFrom(key.cclass)) {
            return MAP_FETCHER;
        }

        /* anki - we don't need this:
        final Method m = getMethod(key.cclass, key.name);
        if (m != null) {
            return new VariableFetcher() {
                public Object get (Object ctx, String name) throws Exception {
                    return m.invoke(ctx);
                }
            };
        }

        final Field f = getField(key.cclass, key.name);
        if (f != null) {
            return new VariableFetcher() {
                public Object get (Object ctx, String name) throws Exception {
                    return f.get(ctx);
                }
            };
        }
        */

        return null;
    }

    protected static Method getMethod (Class<?> clazz, String name)
    {
        Method m;
        try {
            m = clazz.getDeclaredMethod(name);
            if (!m.getReturnType().equals(void.class)) {
                if (!m.isAccessible()) {
                    m.setAccessible(true);
                }
                return m;
            }
        } catch (Exception e) {
            // fall through
        }
        try {
            m = clazz.getDeclaredMethod(
                "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            if (!m.getReturnType().equals(void.class)) {
                if (!m.isAccessible()) {
                    m.setAccessible(true);
                }
                return m;
            }
        } catch (Exception e) {
            // fall through
        }

        Class<?> sclass = clazz.getSuperclass();
        if (sclass != Object.class && sclass != null) {
            return getMethod(clazz.getSuperclass(), name);
        }
        return null;
    }

    protected static Field getField (Class<?> clazz, String name)
    {
        Field f;
        try {
            f = clazz.getDeclaredField(name);
            if (!f.isAccessible()) {
                f.setAccessible(true);
            }
            return f;
        } catch (Exception e) {
            // fall through
        }

        Class<?> sclass = clazz.getSuperclass();
        if (sclass != Object.class && sclass != null) {
            return getField(clazz.getSuperclass(), name);
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

    /** Used to cache variable fetchers for a given context class, name combination. */
    protected static class Key
    {
        public final Class<?> cclass;
        public final String name;

        public Key (Class<?> cclass, String name) {
            this.cclass = cclass;
            this.name = name;
        }

        @Override public int hashCode () {
            return cclass.hashCode() * 31 + name.hashCode();
        }

        @Override public boolean equals (Object other) {
            Key okey = (Key)other;
            return okey.cclass == cclass && okey.name == name;
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

    protected static final VariableFetcher THIS_FETCHER = new VariableFetcher() {
        public Object get (Object ctx, String name) throws Exception {
            return ctx;
        }
    };
}
