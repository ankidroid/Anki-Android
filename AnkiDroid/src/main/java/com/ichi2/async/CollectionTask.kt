/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.async

import android.content.Context
import com.fasterxml.jackson.core.JsonToken
import com.ichi2.anki.*
import com.ichi2.anki.AnkiSerialization.factory
import com.ichi2.libanki.*
import com.ichi2.libanki.Collection
import com.ichi2.libanki.Collection.CheckDatabaseResult
import com.ichi2.utils.Computation
import com.ichi2.utils.KotlinCleanup
import org.apache.commons.compress.archivers.zip.ZipFile
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

/**
 * This is essentially an AsyncTask with some more logging. It delegates to TaskDelegate the actual business logic.
 * It adds some extra check.
 * TODO: explain the goal of those extra checks. They seems redundant with AsyncTask specification.
 *
 * The CollectionTask should be created by the TaskManager. All creation of background tasks (except for Connection and Widget) should be done by sending a TaskDelegate to the ThreadManager.launchTask.
 *
 * @param <Progress> The type of progress that is sent by the TaskDelegate. E.g. a Card, a pairWithBoolean.
 * @param <Result>   The type of result that the TaskDelegate sends. E.g. a tree of decks, counts of a deck.
 */
@KotlinCleanup("IDE Lint")
@KotlinCleanup("Lots to do")
open class CollectionTask<Progress, Result>(val task: TaskDelegateBase<Progress, Result>, private val listener: TaskListener<in Progress, in Result>?, private var previousTask: CollectionTask<*, *>?) : BaseAsyncTask<Void, Progress, Result>(), Cancellable {
    /**
     * A reference to the application context to use to fetch the current Collection object.
     */
    protected var context: Context? = null
        private set

    /** Cancel the current task.
     * @return whether cancelling did occur.
     */
    @Suppress("deprecation") // #7108: AsyncTask
    override fun safeCancel(): Boolean {
        try {
            if (status != Status.FINISHED) {
                return cancel(true)
            }
        } catch (e: Exception) {
            // Potentially catching SecurityException, from
            // Thread.interrupt from FutureTask.cancel from
            // AsyncTask.cancel
            Timber.w(e, "Exception cancelling task")
        } finally {
            TaskManager.removeTask(this)
        }
        return false
    }

    private val col: Collection
        get() = CollectionHelper.instance.getCol(context)!!

    protected override fun doInBackground(vararg arg0: Void): Result? {
        return try {
            actualDoInBackground()
        } finally {
            TaskManager.removeTask(this)
        }
    }

    // This method and those that are called here are executed in a new thread
    @Suppress("deprecation") // #7108: AsyncTask
    protected fun actualDoInBackground(): Result? {
        super.doInBackground()
        // Wait for previous thread (if any) to finish before continuing
        if (previousTask != null && previousTask!!.status != Status.FINISHED) {
            Timber.d("Waiting for %s to finish before starting %s", previousTask!!.task, task.javaClass)
            try {
                previousTask!!.get()
                Timber.d("Finished waiting for %s to finish. Status= %s", previousTask!!.task, previousTask!!.status)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                // We have been interrupted, return immediately.
                Timber.d(e, "interrupted while waiting for previous task: %s", previousTask!!.task.javaClass)
                return null
            } catch (e: ExecutionException) {
                // Ignore failures in the previous task.
                Timber.e(e, "previously running task failed with exception: %s", previousTask!!.task.javaClass)
            } catch (e: CancellationException) {
                // Ignore cancellation of previous task
                Timber.d(e, "previously running task was cancelled: %s", previousTask!!.task.javaClass)
            }
        }
        TaskManager.setLatestInstance(this)
        context = AnkiDroidApp.instance.applicationContext

        // Skip the task if the collection cannot be opened
        if (task.requiresOpenCollection() && CollectionHelper.instance.getColSafe(context) == null) {
            Timber.e("CollectionTask CollectionTask %s as Collection could not be opened", task.javaClass)
            return null
        }
        // Actually execute the task now that we are at the front of the queue.
        return task.execTask(col, this)
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onPreExecute() {
        super.onPreExecute()
        listener?.onPreExecute()
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onProgressUpdate(vararg values: Progress) {
        super.onProgressUpdate(*values)
        listener?.onProgressUpdate(values[0])
    }

    /** Delegates to the [TaskListener] for this task.  */
    override fun onPostExecute(result: Result) {
        super.onPostExecute(result)
        listener?.onPostExecute(result)
        Timber.d("enabling garbage collection of mPreviousTask...")
        previousTask = null
    }

    override fun onCancelled() {
        TaskManager.removeTask(this)
        listener?.onCancelled()
    }

    class CheckDatabase : TaskDelegate<String, Pair<Boolean, CheckDatabaseResult?>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<String>): Pair<Boolean, CheckDatabaseResult?> {
            Timber.d("doInBackgroundCheckDatabase")
            // Don't proceed if collection closed
            val result = col.fixIntegrity(TaskManager.ProgressCallback(collectionTask, AnkiDroidApp.appResources))
            return if (result.failed) {
                // we can fail due to a locked database, which requires knowledge of the failure.
                Pair(false, result)
            } else {
                // Close the collection and we restart the app to reload
                CollectionHelper.instance.closeCollection(true, "Check Database Completed")
                Pair(true, result)
            }
        }
    }

    @KotlinCleanup("needs to handle null collection")
    class ImportReplace(private val pathList: List<String>) : TaskDelegate<String, Computation<*>>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<String>): Computation<*> {
            Timber.d("doInBackgroundImportReplace")
            val res = AnkiDroidApp.instance.baseContext.resources
            val context = col.context
            val colPath = col.path
            // extract the deck from the zip file
            val dir = File(File(colPath).parentFile, "tmpzip")
            if (dir.exists()) {
                BackupManager.removeDir(dir)
            }
            // from anki2.py
            var colname = "collection.anki21"

            for (path in pathList) {
                val zip: ZipFile = try {
                    ZipFile(File(path))
                } catch (e: IOException) {
                    Timber.e(e, "doInBackgroundImportReplace - Error while unzipping")
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace0")
                    return Computation.ERR
                }
                try {
                    // v2 scheduler?
                    if (zip.getEntry(colname) == null) {
                        colname = CollectionHelper.COLLECTION_FILENAME
                    }
                    Utils.unzipFiles(zip, dir.absolutePath, arrayOf(colname, "media"), null)
                } catch (e: IOException) {
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace - unzip")
                    return Computation.ERR
                }
                val colFile = File(dir, colname).absolutePath
                if (!File(colFile).exists()) {
                    return Computation.ERR
                }
                var tmpCol: Collection? = null
                try {
                    tmpCol = Storage.collection(context, colFile)
                    if (!tmpCol.validCollection()) {
                        tmpCol.close()
                        return Computation.ERR
                    }
                } catch (e: Exception) {
                    Timber.e("Error opening new collection file... probably it's invalid")
                    try {
                        tmpCol!!.close()
                    } catch (e2: Exception) {
                        Timber.w(e2)
                        // do nothing
                    }
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace - open col")
                    return Computation.ERR
                } finally {
                    tmpCol?.close()
                }
                collectionTask.doProgress(res.getString(R.string.importing_collection))
                try {
                    CollectionHelper.instance.getCol(context)
                    // unload collection
                    CollectionHelper.instance.closeCollection(true, "Importing new collection")
                    CollectionHelper.instance.lockCollection()
                } catch (e: Exception) {
                    Timber.w(e)
                }
                // overwrite collection
                val f = File(colFile)
                if (!f.renameTo(File(colPath))) {
                    // Exit early if this didn't work
                    return Computation.ERR
                }
                return try {
                    CollectionHelper.instance.unlockCollection()

                    // because users don't have a backup of media, it's safer to import new
                    // data and rely on them running a media db check to get rid of any
                    // unwanted media. in the future we might also want to duplicate this step
                    // import media
                    val nameToNum = HashMap<String, String>()
                    val numToName = HashMap<String, String>()
                    val mediaMapFile = File(dir.absolutePath, "media")
                    if (mediaMapFile.exists()) {
                        factory.createParser(mediaMapFile).use { jp ->
                            var name: String
                            var num: String
                            check(jp.nextToken() == JsonToken.START_OBJECT) { "Expected content to be an object" }
                            while (jp.nextToken() != JsonToken.END_OBJECT) {
                                num = jp.currentName()
                                name = jp.nextTextValue()
                                nameToNum[name] = num
                                numToName[num] = name
                            }
                        }
                    }
                    val mediaDir = Media.getCollectionMediaPath(colPath)
                    val total = nameToNum.size
                    var i = 0
                    for ((file, c) in nameToNum) {
                        val of = File(mediaDir, file)
                        if (!of.exists()) {
                            Utils.unzipFiles(zip, mediaDir, arrayOf(c), numToName)
                        }
                        ++i
                        collectionTask.doProgress(res.getString(R.string.import_media_count, (i + 1) * 100 / total))
                    }
                    zip.close()
                    // delete tmp dir
                    BackupManager.removeDir(dir)
                    Computation.OK
                } catch (e: RuntimeException) {
                    Timber.e(e, "doInBackgroundImportReplace - RuntimeException")
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace1")
                    Computation.ERR
                } catch (e: FileNotFoundException) {
                    Timber.e(e, "doInBackgroundImportReplace - FileNotFoundException")
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace2")
                    Computation.ERR
                } catch (e: IOException) {
                    Timber.e(e, "doInBackgroundImportReplace - IOException")
                    CrashReportService.sendExceptionReport(e, "doInBackgroundImportReplace3")
                    Computation.ERR
                }
            }
            return Computation.OK
        }
    }

    class Reset : TaskDelegate<Void, Void?>() {
        override fun task(col: Collection, collectionTask: ProgressSenderAndCancelListener<Void>): Void? {
            col.sched.reset()
            return null
        }
    }
}
