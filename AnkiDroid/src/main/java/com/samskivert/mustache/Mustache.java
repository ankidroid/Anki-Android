//
// JMustache - A Java implementation of the Mustache templating language
// http://github.com/samskivert/jmustache/blob/master/LICENSE

package com.samskivert.mustache;



import com.ichi2.anki.AnkiDroidApp;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Provides <a href="http://mustache.github.com/">Mustache</a> templating services.
 * <p> Basic usage: <pre>{@code
 * String source = "Hello {{arg}}!";
 * Template tmpl = Mustache.compiler().compile(source);
 * Map<String, Object> context = new HashMap<String, Object>();
 * context.put("arg", "world");
 * tmpl.execute(context); // returns "Hello world!" }</pre>
 * <p> Limitations:
 * <ul><li> Only one or two character delimiters are supported when using {{=ab cd=}} to change
 * delimiters.
 * <li> {{< include}} is not supported. We specifically do not want the complexity of handling the
 * automatic loading of dependent templates. </ul>
 */
public class Mustache
{
    /** An interface to the Mustache compilation process. See {@link Mustache}. */
    public static class Compiler {
        /** Whether or not HTML entities are escaped by default. */
        public final boolean stripSpan;

        /** Compiles the supplied template into a repeatedly executable intermediate form. */
        public Template compile (String template)
        {
            return compile(new StringReader(template));
        }

        /** Compiles the supplied template into a repeatedly executable intermediate form. */
        public Template compile (Reader source)
        {
            return Mustache.compile(source, this);
        }

        /** Returns a compiler that either does or does not escape HTML by default. */
        public Compiler stripSpan (boolean stripSpan) {
            return new Compiler(stripSpan);
        }

        protected Compiler (boolean stripSpan) {
            this.stripSpan = stripSpan;
        }
    }

    /** Used to read variables from values. */
    public interface VariableFetcher
    {
        /** Reads the so-named variable from the supplied context object. */
        Object get (Object ctx, String name) throws Exception;
    }

    /**
     * Returns a compiler that escapes HTML by default.
     */
    public static Compiler compiler ()
    {
        return new Compiler(true);
    }

    /**
     * Compiles the supplied template into a repeatedly executable intermediate form.
     */
    protected static Template compile (Reader source, Compiler compiler)
    {
        // a hand-rolled parser; whee!
        Accumulator accum = new Accumulator(compiler);
        char start1 = '{', start2 = '{', end1 = '}', end2 = '}';
        int state = TEXT;
        StringBuilder text = new StringBuilder();
        int line = 1;
        boolean skipNewline = false;
        boolean skippedExtraBracket = false;

        while (true) {
            char c;
            try {
                int v = source.read();
                if (v == -1) {
                    break;
                }
                c = (char)v;
            } catch (IOException e) {
                throw new MustacheException(e);
            }

            if (c == '\n') {
                line++;
                // if we just parsed an open section or close section task, we'll skip the first
                // newline character following it, if desired; TODO: handle CR, sigh
                if (skipNewline) {
                    skipNewline = false;
                    continue;
                }
            } else {
                skipNewline = false;
            }

            switch (state) {
            case TEXT:
                if (c == start1) {
                    if (start2 == -1) {
                        accum.addTextSegment(text);
                        state = TAG;
                    } else {
                        state = MATCHING_START;
                    }
                } else {
                    text.append(c);
                }
                break;

            case MATCHING_START:
                if (c == start2) {
                    accum.addTextSegment(text);
                    state = TAG;
                } else {
                    text.append(start1);
                    if (c != start1) {
                        text.append(c);
                        state = TEXT;
                    }
                }
                break;

            case TAG:
                if (c == end1) {
                    if (!skippedExtraBracket && text.charAt(0) == '{') {
                        // This tag requires an extra closing '}', we need to skip it
                        skippedExtraBracket = true;
                    } else if (end2 == -1) {
                        if (text.charAt(0) == '=') {
                            // TODO: change delimiters
                        } else {
                            if (sanityCheckTag(text, line, start1, start2)) {
                                accum = accum.addTagSegment(text, line);
                            } else {
                                text.setLength(0);
                            }
                            skipNewline = accum.skipNewline();
                            skippedExtraBracket = false;
                        }
                        state = TEXT;
                    } else {
                        state = MATCHING_END;
                    }
                } else {
                    text.append(c);
                }
                break;

            case MATCHING_END:
                if (c == end2) {
                    if (text.charAt(0) == '=') {
                        // TODO: change delimiters
                    } else {
                        if (sanityCheckTag(text, line, start1, start2)) {
                            accum = accum.addTagSegment(text, line);
                        } else {
                            text.setLength(0);
                        }
                        skipNewline = accum.skipNewline();
                        skippedExtraBracket = false;
                    }
                    state = TEXT;
                } else {
                    text.append(end1);
                    if (c != end1) {
                        text.append(c);
                        state = TAG;
                    }
                }
                break;
            }
        }

        // accumulate any trailing text
        switch (state) {
        case TEXT:
            accum.addTextSegment(text);
            break;
        case MATCHING_START:
            text.append(start1);
            accum.addTextSegment(text);
            break;
        case MATCHING_END:
            text.append(end1);
            accum.addTextSegment(text);
            break;
        case TAG:
            Timber.e("Template ended while parsing a tag [line=" + line + ", tag=" + text + "]");
            text.append(end1);
            accum.addTextSegment(text);
            break;
        }

        return new Template(accum.finish());
    }

    private Mustache () {} // no instantiateski

    protected static boolean sanityCheckTag (StringBuilder accum, int line, char start1, char start2)
    {
        for (int ii = 0, ll = accum.length(); ii < ll; ii++) {
            if (accum.charAt(ii) == start1) {
                if (start2 == -1 || (ii < ll-1 && accum.charAt(ii+1) == start2)) {
                    Timber.e("Tag contains start tag delimiter, probably missing close delimiter " +
                            "[line=" + line + ", tag=" + accum + "]");
                    return false;
                }
            }
        }
        return true;
    }

    protected static final Pattern spanPattern = Pattern.compile("^<span.+?>(.*)</span>");

    protected static String stripSpan (String text)
    {
        // Changed for anki, stripping the wrapped field span when using {{{
        Matcher m = spanPattern.matcher(text);
        if (m.find()) {
            text = m.group(1);
        }
        return text;
    }

    protected static final int TEXT = 0;
    protected static final int MATCHING_START = 1;
    protected static final int MATCHING_END = 2;
    protected static final int TAG = 3;

    protected static class Accumulator {
        public Accumulator (Compiler compiler) {
            _compiler = compiler;
        }

        public boolean skipNewline () {
            // return true if we just added a compound segment which means we're immediately
            // following the close section tag
            return (_segs.size() > 0 && _segs.get(_segs.size()-1) instanceof CompoundSegment);
        }

        public void addTextSegment (StringBuilder text) {
            if (text.length() > 0) {
                _segs.add(new StringSegment(text.toString()));
                text.setLength(0);
            }
        }

        public Accumulator addTagSegment (StringBuilder accum, final int tagLine) {
            final Accumulator outer = this;
            String tag = accum.toString().trim();
            final String tag1 = tag.substring(1).trim();
            accum.setLength(0);

            switch (tag.charAt(0)) {
            case '#':
                requireNoNewlines(tag, tagLine);
                return new Accumulator(_compiler) {
                    @Override public boolean skipNewline () {
                        // if we just opened this section, we want to skip a newline
                        return (_segs.size() == 0) || super.skipNewline();
                    }
                    @Override public Template.Segment[] finish () {
                        throw new MustacheException("Section missing close tag " +
                                                    "[line=" + tagLine + ", tag=" + tag1 + "]");
                    }
                    @Override protected Accumulator addCloseSectionSegment (String itag, int line) {
                        requireSameName(tag1, itag, line);
                        outer._segs.add(new SectionSegment(itag, super.finish(), tagLine));
                        return outer;
                    }
                };

            case '^':
                requireNoNewlines(tag, tagLine);
                return new Accumulator(_compiler) {
                    @Override public boolean skipNewline () {
                        // if we just opened this section, we want to skip a newline
                        return (_segs.size() == 0) || super.skipNewline();
                    }
                    @Override public Template.Segment[] finish () {
                        throw new MustacheException("Inverted section missing close tag " +
                                                    "[line=" + tagLine + ", tag=" + tag1 + "]");
                    }
                    @Override protected Accumulator addCloseSectionSegment (String itag, int line) {
                        requireSameName(tag1, itag, line);
                        outer._segs.add(new InvertedSectionSegment(itag, super.finish(), tagLine));
                        return outer;
                    }
                };

            case '/':
                requireNoNewlines(tag, tagLine);
                return addCloseSectionSegment(tag1, tagLine);

            case '!':
                // comment!, ignore
                return this;

            case '{':
                requireNoNewlines(tag1, tagLine);
                _segs.add(new VariableSegment(tag1, _compiler.stripSpan, tagLine));
                return this;

            default:
                requireNoNewlines(tag, tagLine);
                _segs.add(new VariableSegment(tag, false, tagLine));
                return this;
            }
        }

        public Template.Segment[] finish () {
            return _segs.toArray(new Template.Segment[_segs.size()]);
        }

        protected Accumulator addCloseSectionSegment (String tag, int line) {
            throw new MustacheException("Section close tag with no open tag " +
                                        "[line=" + line + ", tag=" + tag + "]");
        }

        protected static void requireNoNewlines (String tag, int line) {
            if (tag.contains("\n") || tag.contains("\r")) {
                throw new MustacheException("Invalid tag name: contains newline " +
                                            "[line=" + line + ", tag=" + tag + "]");
            }
        }

        protected static void requireSameName (String name1, String name2, int line)
        {
            if (!name1.equals(name2)) {
                throw new MustacheException(
                    "Section close tag with mismatched open tag " +
                    "[line=" + line + ", expected=" + name1 + ", got=" + name2 + "]");
            }
        }

        protected Compiler _compiler;
        protected final List<Template.Segment> _segs = new ArrayList<Template.Segment>();
    }

    /** A simple segment that reproduces a string. */
    protected static class StringSegment extends Template.Segment {
        public StringSegment (String text) {
            _text = text;
        }
        @Override public void execute (Template tmpl, Template.Context ctx, Writer out) {
            write(out, _text);
        }
        protected final String _text;
    }

    /** A helper class for named segments. */
    protected static abstract class NamedSegment extends Template.Segment {
        protected NamedSegment (String name, int line) {
            _name = name;
            _line = line;
        }
        protected final String _name;
        protected final int _line;
    }

    /** A segment that substitutes the contents of a variable. */
    protected static class VariableSegment extends NamedSegment {
        public VariableSegment (String name, boolean stripSpan, int line) {
            super(name, line);
            _stripSpan = stripSpan;
        }
        @Override public void execute (Template tmpl, Template.Context ctx, Writer out)  {
            Object value = tmpl.getValue(ctx, _name, _line);
            if (value != null) {
                String text = String.valueOf(value);
                write(out, _stripSpan ? stripSpan(text) : text);
            }
        }
        protected boolean _stripSpan;
    }

    /** A helper class for compound segments. */
    protected static abstract class CompoundSegment extends NamedSegment {
        protected CompoundSegment (String name, Template.Segment[] segs, int line) {
            super(name, line);
            _segs = segs;
        }
        protected void executeSegs (Template tmpl, Template.Context ctx, Writer out)  {
            for (Template.Segment seg : _segs) {
                seg.execute(tmpl, ctx, out);
            }
        }
        protected final Template.Segment[] _segs;
    }

    /** A segment that represents a section. */
    protected static class SectionSegment extends CompoundSegment {
        public SectionSegment (String name, Template.Segment[] segs, int line) {
            super(name, segs, line);
        }
        @Override public void execute (Template tmpl, Template.Context ctx, Writer out)  {
            Object value = tmpl.getValue(ctx, _name, _line);
            if (value == null) {
                return; // TODO: configurable behavior on missing values
            }
            if (value instanceof Iterable<?>) {
                value = ((Iterable<?>)value).iterator();
            }
            if (value instanceof Iterator<?>) {
                Template.Mode mode = null;
                int index = 0;
                for (Iterator<?> iter = (Iterator<?>)value; iter.hasNext(); ) {
                    Object elem = iter.next();
                    mode = (mode == null) ? Template.Mode.FIRST :
                        (iter.hasNext() ? Template.Mode.OTHER : Template.Mode.LAST);
                    executeSegs(tmpl, ctx.nest(elem, ++index, mode), out);
                }
            } else if (value instanceof Boolean) {
                if ((Boolean)value) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value instanceof String) {
                if (((String) value).length() > 0) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value.getClass().isArray()) {
                for (int ii = 0, ll = Array.getLength(value); ii < ll; ii++) {
                    Template.Mode mode = (ii == 0) ? Template.Mode.FIRST :
                        ((ii == ll-1) ? Template.Mode.LAST : Template.Mode.OTHER);
                    executeSegs(tmpl, ctx.nest(Array.get(value, ii), ii+1, mode), out);
                }
            } else {
                executeSegs(tmpl, ctx.nest(value, 0, Template.Mode.OTHER), out);
            }
        }
    }

    /** A segment that represents an inverted section. */
    protected static class InvertedSectionSegment extends CompoundSegment {
        public InvertedSectionSegment (String name, Template.Segment[] segs, int line) {
            super(name, segs, line);
        }
        @Override public void execute (Template tmpl, Template.Context ctx, Writer out)  {
            Object value = tmpl.getValue(ctx, _name, _line);
            if (value == null) {
                executeSegs(tmpl, ctx, out); // TODO: configurable behavior on missing values
            }
            if (value instanceof Iterable<?>) {
                Iterable<?> iable = (Iterable<?>)value;
                if (!iable.iterator().hasNext()) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value instanceof Boolean) {
                if (!(Boolean)value) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value instanceof String) {
                if (((String) value).length() == 0) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value.getClass().isArray()) {
                if (Array.getLength(value) == 0) {
                    executeSegs(tmpl, ctx, out);
                }
            } else if (value instanceof Iterator<?>) {
                Iterator<?> iter = (Iterator<?>)value;
                if (!iter.hasNext()) {
                    executeSegs(tmpl, ctx, out);
                }
            }
        }
    }

    /** Map of strings that must be replaced inside html attributes and their replacements. (They
     * need to be applied in order so amps are not double escaped.) */
    protected static final String[][] ATTR_ESCAPES = {
        { "&", "&amp;" },
        { "'", "&apos;" },
        { "\"", "&quot;" },
        { "<", "&lt;" },
        { ">", "&gt;" },
    };
}
