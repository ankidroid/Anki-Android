/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package androidx.test.internal.runner;

import android.content.Context;
import android.os.Build;

import com.ichi2.libanki.Utils;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import androidx.annotation.NonNull;
import timber.log.Timber;

/**
 * This class exists as I could get MultiDex working, but ClassPathScanner uses new DexFile(),
 * which only loads the classes from classes.dex in Android < 21. MultiDex.install() fixes the class loader,
 * but since we don't return all the class names from the dex, we don't see these as potential test classes to filter.
 **/
class MultiDexClassPathScanner extends ClassPathScanner {
    private final List<String> classPathEntries;
    private final Context targetContext;

    public MultiDexClassPathScanner(List<String> classPath, Context targetContext) {
        super(classPath);
        //There's no accessor in the base class.
        this.classPathEntries = classPath;
        //We need context to get a temporary folder to store our extracted dex files.
        this.targetContext = targetContext;
    }

    @Override
    public Set<String> getClassPathEntries(ClassNameFilter filter) {
        Set<String> ret = new LinkedHashSet<>();
        String absolutePath = targetContext.getCacheDir().getAbsolutePath();

        for (String classPath : classPathEntries) {
            for (String dexPath: extractDexFilesFromApk(classPath, absolutePath)) {
                List<String> classes = extractClassesFromDexPath(dexPath);
                for (String name: classes) {
                    if (filter.accept(name)) {
                        ret.add(name);
                    }
                }
            }
        }

        return ret;
    }

    private List<String> extractClassesFromDexPath(String dexPath) {
        List<String> ret = new ArrayList<>();
        try {
            DexBackedDexFile dex = DexFileFactory.loadDexFile(new File(dexPath), Opcodes.forApi(Build.VERSION.SDK_INT));
            for (ClassDef classDef: dex.getClasses()) {
                String typeName = extractTypeNameFromDef(classDef);
                ret.add(typeName);
            }

        } catch (IOException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }
        return ret;
    }


    /**
     * @param classDef The class definition to extract the name from
     * @return A classname in the form: java.util.scanner
     */
    @NonNull
    private String extractTypeNameFromDef(ClassDef classDef) {
        String type = classDef.getType();
        //classes are inconsistently named: https://stackoverflow.com/a/11350724
        //Assumed structure: Ljava/lang/String;
        if (!type.startsWith("L") || !type.endsWith(";")) {
            throw new IllegalArgumentException("Unhandled class type: " + type);
        }
        type = type.substring(1, type.length() - 1); //trim suffix and prefix
        type = type.replace('/', '.');
        return type;
    }


    private List<String> extractDexFilesFromApk(String path, String targetDirectory) {
        //Modified from Utils.unzipFiles
        ZipFile zip;
        try {
            zip = new ZipFile(new File(path), ZipFile.OPEN_READ);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> fileNames = new ArrayList<>();

        try {
            File tempDir = new File(targetDirectory);
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new IOException("Failed to create target directory: " + targetDirectory);
            }

            for (ZipEntry ze : Collections.list(zip.entries())) {
                String name = ze.getName();
                if (!name.endsWith(".dex") || ze.isDirectory()) {
                    continue;
                }

                File destFile = new File(tempDir, name);
                if (!Utils.isInside(destFile, tempDir)) {
                    Timber.e("Refusing to decompress invalid path: %s", destFile.getCanonicalPath());
                    String message = String.format(Locale.US, "File is outside extraction target directory. %s %s", tempDir, destFile);
                    throw new IOException(message);
                }

                try (InputStream zis = zip.getInputStream(ze)) {
                    Utils.writeToFile(zis, destFile.getAbsolutePath());
                }

                fileNames.add(destFile.getAbsolutePath());

            }
        } catch (IOException e) {
            throw new RuntimeException(e.getLocalizedMessage(), e);
        }

        return fileNames;
    }
}
