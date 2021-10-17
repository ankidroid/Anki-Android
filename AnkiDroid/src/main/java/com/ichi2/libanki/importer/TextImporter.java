//noinspection MissingCopyrightHeader #8659
package com.ichi2.libanki.importer;

import android.os.Build;
import android.text.TextUtils;

import com.ichi2.anki.R;
import com.ichi2.libanki.Collection;
import com.ichi2.libanki.importer.python.CsvDialect;
import com.ichi2.libanki.importer.python.CsvReader;
import com.ichi2.libanki.importer.python.CsvSniffer;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import timber.log.Timber;

@RequiresApi(api = Build.VERSION_CODES.O)
public class TextImporter extends NoteImporter {

    private boolean mNeedDelimiter = true;
    final String mPatterns = "\t|,;:";

    private FileObj mFileobj;
    private char mDelimiter;
    private String[] mTagstoadd;

    private CsvDialect mDialect;
    private int mNumFields;


    private boolean mFirstLineWasTags;


    public TextImporter(Collection col, String file) {
        super(col, file);
        mFileobj = null;
        mDelimiter = '\0';
        mTagstoadd = new String[0];
    }


    @NonNull
    @Override
    protected List<ForeignNote> foreignNotes() {
        open();
        // process all lines
        List<String> log = new ArrayList<>(); //Number of element is reader's size
        List<ForeignNote> notes = new ArrayList<>(); //Number of element is reader's size
        int lineNum = 0;
        int ignored = 0;
        // Note: This differs from libAnki as we don't have csv.reader
        Iterator<String> data = getDataStream().iterator();
        CsvReader reader;
        if (mDelimiter != '\0') {
            reader = CsvReader.fromDelimiter(data, mDelimiter);
        } else {
            reader = CsvReader.fromDialect(data, mDialect);
        }
        try {
            for (List<? extends String> row : reader) {
                if (row == null) {
                    continue;
                }
                List<String> rowAsString = new ArrayList<>(row);
                if (rowAsString.size() != mNumFields) {
                    if (!rowAsString.isEmpty()) {
                        String formatted = getString(R.string.csv_importer_error_invalid_field_count,
                                TextUtils.join(" ", rowAsString),
                                rowAsString.size(),
                                mNumFields);
                        log.add(formatted);
                        ignored += 1;

                    }
                    continue;
                }
                ForeignNote note = noteFromFields(rowAsString);
                notes.add(note);
            }
        } catch (CsvException e) {
            log.add(getString(R.string.csv_importer_error_exception, e));
        }
        mLog = log;
        mFileobj.close();
        return notes;
    }

    /**
     * Number of fields.
     * @throws UnknownDelimiterException Could not determine delimiter (example: empty file)
     * @throws EncodingException Non-UTF file (for example: an image)
     */
    @Override
    public int fields() {
        open();
        return mNumFields;
    }


    private ForeignNote noteFromFields(List<String> fields) {
        ForeignNote note = new ForeignNote();
        note.mFields.addAll(fields);
        note.mTags.addAll(Arrays.asList(mTagstoadd));
        return note;
    }


    /** Parse the top line and determine the pattern and number of fields. */
    @Override
    protected void open() {
        // load & look for the right pattern
        cacheFile();
    }

    /** Read file into self.lines if not already there. */
    private void cacheFile() {
        if (mFileobj == null) {
            openFile();
        }
    }


    private void openFile() {
        mDialect = null;
        mFileobj = FileObj.open(mFile);

        String firstLine = getFirstFileLine().orElse(null);
        if (firstLine != null) {
            if (firstLine.startsWith("tags:")) {
                String tags = firstLine.substring("tags:".length()).trim();
                mTagstoadd = tags.split(" ");
                this.mFirstLineWasTags = true;
            }
            updateDelimiter();
        }

        if (mDialect == null && mDelimiter == '\0') {
            throw new UnknownDelimiterException();
        }
    }

    @Contract(" -> fail")
    private void err() {
        throw new UnknownDelimiterException();
    }

    private void updateDelimiter() {
        mDialect = null;
        CsvSniffer sniffer = new CsvSniffer();
        if (mDelimiter == '\0') {
            try {
                String join = getLinesFromFile(10);
                mDialect = sniffer.sniff(join, mPatterns.toCharArray());
            } catch (Exception e) {
                // expected: do not log the exception
                try {
                    mDialect = sniffer.sniff(getFirstFileLine().orElse(""), mPatterns.toCharArray());
                } catch (Exception ex) {
                    // expected and ignored: do not log the exception
                }
            }
        }

        Iterator<String> data = getDataStream().iterator();

        CsvReader reader = null;
        if (mDialect != null) {
            try {
                reader = CsvReader.fromDialect(data, mDialect);
            } catch (Exception e) {
                // expected and ignored: do not log the exception
                err();
            }
        } else {
            // PERF: This starts the file read twice - whereas we only need the first line
            String firstLine = getFirstFileLine().orElse("");
            if (mDelimiter == '\0') {
                if (firstLine.contains("\t")) {
                    mDelimiter = '\t';
                } else if(firstLine.contains(";")) {
                    mDelimiter = ';';
                } else if(firstLine.contains(",")) {
                    mDelimiter = ',';
                } else {
                    mDelimiter = ' ';
                }
            }
            reader = CsvReader.fromDelimiter(data, mDelimiter);
        }

        try {
            while (true) {
                List<String> row = reader.next();
                if (!row.isEmpty()) {
                    mNumFields = row.size();
                    break;
                }
            }
        } catch (Exception e) {
            Timber.e(e);
            err();
        }
        initMapping();
    }


    /*
    In python:
    >>> pp(re.sub("^\#.*$", "__comment", "#\r\n"))
    '__comment\n'
    In Java:
    COMMENT_PATTERN.matcher("#\r\n").replaceAll("__comment") -> "__comment\r\n"
    So we use .DOTALL to ensure we get the \r
    */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("^#.*$", Pattern.DOTALL);

    private String sub(String s) {
        return COMMENT_PATTERN.matcher(s).replaceAll("__comment");
    }


    private Stream<String> getDataStream() {
        Stream<String> data;
        try {
            data = mFileobj.readAsUtf8WithoutBOM();
        } catch (IOException e) {
            throw new EncodingException(e);
        }

        Stream<String> withoutComments = data.filter(x -> !"__comment".equals(sub(x))).map(s -> s + "\n");
        if (this.mFirstLineWasTags) {
            withoutComments = withoutComments.skip(1);
        }
        return withoutComments;
    }


    private Optional<String> getFirstFileLine() {
        try {
            return getDataStream().findFirst();
        } catch (UncheckedIOException e) {
            throw new EncodingException(e);
        }
    }


    private String getLinesFromFile(int numberOfLines) {
        try {
            return TextUtils.join("\n", getDataStream().limit(numberOfLines).collect(Collectors.toList()));
        } catch (UncheckedIOException e) {
            throw new EncodingException(e);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private static class FileObj {

        private final File mFile;


        public FileObj(@NonNull File file) {
            this.mFile = file;
        }


        @NonNull
        public static FileObj open(@NonNull String file) {
            return new FileObj(new File(file));
        }


        public void close() {
            // No need for anything - closed in the read
        }


        @NonNull
        public Stream<String> readAsUtf8WithoutBOM() throws IOException {
            return Files.lines(Paths.get(mFile.getAbsolutePath()), StandardCharsets.UTF_8);
        }
    }

    public static class UnknownDelimiterException extends RuntimeException {
        public UnknownDelimiterException() {
            super("unknownFormat");
        }
    }

    /** Non-UTF file was provided (for example: an image) */
    public static class EncodingException extends RuntimeException {
        public EncodingException(Exception e) {
            super(e);
        }
    }
}
