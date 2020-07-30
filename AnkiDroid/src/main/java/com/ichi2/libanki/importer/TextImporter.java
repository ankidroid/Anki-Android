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
    String mPatterns = "\t|,;:";

    private final Object lines;
    private FileObj fileobj;
    private char delimiter;
    private String[] tagsToAdd;

    private CsvDialect dialect;
    private int numFields;
    // appears unused
    private int mIgnored;


    private boolean mFirstLineWasTags;


    public TextImporter(Collection col, String file) {
        super(col, file);
        lines = null;
        fileobj = null;
        delimiter = '\0';
        tagsToAdd = new String[0];
    }


    @NonNull
    @Override
    protected List<ForeignNote> foreignNotes() {
        open();
        // process all lines
        List<String> log = new ArrayList<>();
        List<ForeignNote> notes = new ArrayList<>();
        int lineNum = 0;
        int ignored = 0;
        // Note: This differs from libAnki as we don't have csv.reader
        Iterator<String> data = getDataStream().iterator();
        CsvReader reader;
        if (delimiter != '\0') {
            reader = CsvReader.fromDelimiter(data, delimiter);
        } else {
            reader = CsvReader.fromDialect(data, dialect);
        }
        try {
            for (List<String> row : reader) {
                if (row == null) {
                    continue;
                }
                List<String> rowAsString = new ArrayList<>(row);
                if (rowAsString.size() != numFields) {
                    if (rowAsString.size() > 0) {
                        String formatted = getString(R.string.csv_importer_error_invalid_field_count,
                                TextUtils.join(" ", rowAsString),
                                rowAsString.size(),
                                numFields);
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
        mIgnored = ignored;
        fileobj.close();
        return notes;
    }

    /** Number of fields. */
    @Override
    protected int fields() {
        open();
        return numFields;
    }


    private ForeignNote noteFromFields(List<String> fields) {
        ForeignNote note = new ForeignNote();
        note.mFields.addAll(fields);
        note.mTags.addAll(Arrays.asList(tagsToAdd));
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
        if (fileobj == null) {
            openFile();
        }
    }


    private void openFile() {
        dialect = null;
        fileobj = FileObj.open(mFile);

        String firstLine = getFirstFileLine().orElse(null);
        if (firstLine != null) {
            if (firstLine.startsWith("tags:")) {
                String tags = firstLine.substring("tags:".length()).trim();
                tagsToAdd = tags.split(" ");
                this.mFirstLineWasTags = true;
            }
            updateDelimiter();
        }

        if (dialect == null && delimiter == '\0') {
            throw new RuntimeException("unknownFormat");
        }
    }

    @Contract(" -> fail")
    private void err() {
        throw new RuntimeException("unknownFormat");
    }

    private void updateDelimiter() {
        dialect = null;
        CsvSniffer sniffer = new CsvSniffer();
        if (delimiter == '\0') {
            try {
                String join = getLinesFromFile(10);
                dialect = sniffer.sniff(join, mPatterns.toCharArray());
            } catch (Exception e) {
                try {
                    dialect = sniffer.sniff(getFirstFileLine().orElse(""), mPatterns.toCharArray());
                } catch (Exception ex) {
                    // pass
                }
            }
        }

        Iterator<String> data = getDataStream().iterator();

        CsvReader reader = null;
        if (dialect != null) {
            try {
                reader = CsvReader.fromDialect(data, dialect);
            } catch (Exception e) {
                err();
            }
        } else {
            // PERF: This starts the file read twice - whereas we only need the first line
            String firstLine = getFirstFileLine().orElse("");
            if (delimiter == '\0') {
                if (firstLine.contains("\t")) {
                    delimiter = '\t';
                } else if(firstLine.contains(";")) {
                    delimiter = ';';
                } else if(firstLine.contains(",")) {
                    delimiter = ',';
                } else {
                    delimiter = ' ';
                }
            }
            reader = CsvReader.fromDelimiter(data, delimiter);
        }

        try {
            while (true) {
                List<String> row = reader.next();
                if (row.size() > 0) {
                    numFields = row.size();
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
            data = fileobj.readAsUtf8WithoutBOM();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Stream<String> withoutComments = data.filter(x -> !"__comment".equals(sub(x))).map(s -> s + "\n");
        if (this.mFirstLineWasTags) {
            withoutComments = withoutComments.skip(1);
        }
        return withoutComments;
    }


    private Optional<String> getFirstFileLine() {
        return getDataStream().findFirst();
    }


    private String getLinesFromFile(int numberOfLines) {
        return TextUtils.join("\n", getDataStream().limit(numberOfLines).collect(Collectors.toList()));
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private static class FileObj {

        private final File mFile;


        public FileObj(@NonNull File file) {
            this.mFile = file;
        }


        @NonNull
        public static FileObj open(@NonNull String mFile) {
            return new FileObj(new File(mFile));
        }


        public void close() {
            // No need for anything - closed in the read
        }


        @NonNull
        public Stream<String> readAsUtf8WithoutBOM() throws IOException {
            return Files.lines(Paths.get(mFile.getAbsolutePath()), StandardCharsets.UTF_8);
        }
    }
}
