The AnkiDroid is being migrated to Kotlin.

Steps to migrate a file from Java to Kotlin using Android Studio:
Right click on file name -> Convert Java File to Kotlin File

After this, Android Studio will handle the conversion of the Java code to Kotlin code. Note that the the Git changes might display the Java file as deleted and the corresponding Kotlin file as a new file.

The extension of the file would change from `.java` to `.kt` while the filename would remain the same.

After the conversion is complete, multiple changes might be required in the file. Look out for fields that are nullable. Since Kotlin is a null-safe language, function calls or operations on such fields would require a nullability check which can be added by using the `.?` operator. There would also be places which would require code of this sort: `someVariable?.let { it.performTask() }`.

Apart from this, look out for type casting issues and fix them.

Most of the required changes can be done by going to each error and using the project quick fix menu (Alt + Enter). If that menu doesn't have the required option or there is some other error then it will require manual checking and updating.