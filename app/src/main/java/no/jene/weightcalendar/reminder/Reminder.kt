package no.jene.weightcalendar.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import no.jene.weightcalendar.MainActivity
import no.jene.weightcalendar.R
import no.jene.weightcalendar.data.Settings
import no.jene.weightcalendar.data.WeightDatabase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

// Daily reminder: at the chosen time, if today has no weight yet, show a notification.
//
// How it works: WorkManager (an Android library) runs a ReminderWorker once at the next
// reminder time. The worker checks the day, then plans the next run for the day after.
// WorkManager keeps planned work across reboots, so nothing else is needed for that.
// The time is approximate: Android may delay background work by a few minutes to save battery.
object Reminder {

    private const val WORK_NAME = "daily-reminder"
    private const val CHANNEL_ID = "reminder"
    private const val NOTIFICATION_ID = 1

    // Plan the next reminder from the settings (or cancel it if the reminder is off).
    // REPLACE: forget any reminder already planned (used when the settings change).
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val settings = Settings(context)
        val workManager = WorkManager.getInstance(context)
        if (!settings.reminderEnabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(settings.reminderTime)
        if (!next.isAfter(now)) next = next.plusDays(1)   // today's time has passed: tomorrow

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next))
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun showNotification(context: Context) {
        // Without the permission (refused by the user), Android would drop it anyway.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // A "channel" groups notifications; the user can tune it in the phone's settings.
        // Creating it again when it already exists does nothing.
        val channel = NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // What happens on tap: open the app. A PendingIntent is an "intent to open something
        // later", handed to Android so it can do it on our behalf.
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Weight Calendar")
            .setContentText("You haven't logged your weight today.")
            .setContentIntent(openApp)
            .setAutoCancel(true)            // disappears when tapped
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    // Remove the notification (once today's weight is logged).
    fun dismissNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

// The background task itself. CoroutineWorker: doWork() can call suspend functions
// (here the database), and runs off the main thread.
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Settings(context).reminderEnabled) return Result.success()

        val logged = WeightDatabase.get(context).weightDao().get(LocalDate.now()) != null
        if (!logged) Reminder.showNotification(context)

        // Plan tomorrow's run. APPEND_OR_REPLACE: it starts after this one has finished
        // (REPLACE would cancel the worker that is running right now).
        Reminder.schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
