package no.jene.weightcalendar.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import no.jene.weightcalendar.data.Settings
import no.jene.weightcalendar.data.WeightDatabase
import no.jene.weightcalendar.data.WeightRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

// Daily backup: once a day, write the same CSV as "Export CSV" into a folder chosen by the
// user (for instance one that the NAS app syncs), and keep only the most recent ones.
//
// Files are named weightcal-backup-2026-10-19.csv: a different name from manual exports
// (weights-....csv), so the clean-up never deletes a file the user saved by hand.
object Backup {

    private const val WORK_NAME = "daily-backup"
    private const val PREFIX = "weightcal-backup-"
    const val KEEP = 14

    // Plan the daily backup from the settings (or cancel it).
    // A periodic work runs about once every 24 h, at a moment Android chooses (often while
    // the phone is idle or charging). KEEP: if it is already planned, leave it as it is.
    fun schedule(context: Context) {
        val settings = Settings(context)
        val workManager = WorkManager.getInstance(context)
        if (!settings.backupEnabled || settings.backupFolder == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // Name of the chosen folder, for display ("WeightCalendar"), or null.
    fun folderName(context: Context, folder: String): String? =
        DocumentFile.fromTreeUri(context, folder.toUri())?.name

    // Write today's backup now (replacing today's file if there is one), delete the old
    // ones, and return the file name. Throws if the folder can't be written.
    suspend fun run(context: Context): String {
        val settings = Settings(context)
        val folder = settings.backupFolder?.let { DocumentFile.fromTreeUri(context, it.toUri()) }
            ?: error("No backup folder chosen")
        if (!folder.canWrite()) error("Backup folder not accessible")

        val name = "$PREFIX${LocalDate.now()}.csv"
        val file = folder.findFile(name) ?: folder.createFile("text/csv", name)
            ?: error("Cannot create $name")
        val output = context.contentResolver.openOutputStream(file.uri, "wt") ?: error("Cannot write $name")
        output.use { WeightRepository(WeightDatabase.get(context)).exportCsv(it, settings) }

        // Clean-up: the names contain the date (year-month-day), so sorting them by name
        // sorts them by date. Everything except the KEEP most recent is deleted.
        folder.listFiles()
            .filter { it.name.orEmpty().startsWith(PREFIX) && it.name.orEmpty().endsWith(".csv") }
            .sortedBy { it.name }
            .dropLast(KEEP)
            .forEach { it.delete() }

        settings.lastBackup = LocalDateTime.now()
        return name
    }
}

// The background task. If the backup fails (folder deleted, storage full...), it is simply
// tried again the next day; "Last backup" in the dialog shows when it last worked.
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { Backup.run(applicationContext) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.failure() })
}
