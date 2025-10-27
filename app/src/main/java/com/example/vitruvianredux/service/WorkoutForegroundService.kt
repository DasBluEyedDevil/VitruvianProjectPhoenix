package com.example.vitruvianredux.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.vitruvianredux.MainActivity
import timber.log.Timber

/**
 * Foreground service to keep the app alive during workouts
 * Prevents Android from killing the app and losing BLE connection
 */
class WorkoutForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "vitruvian_workout_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_WORKOUT = "com.example.vitruvianredux.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.example.vitruvianredux.STOP_WORKOUT"
        const val EXTRA_WORKOUT_MODE = "workout_mode"
        const val EXTRA_TARGET_REPS = "target_reps"

        fun startWorkoutService(context: Context, workoutMode: String, targetReps: Int) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = ACTION_START_WORKOUT
                putExtra(EXTRA_WORKOUT_MODE, workoutMode)
                putExtra(EXTRA_TARGET_REPS, targetReps)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopWorkoutService(context: Context) {
            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                action = ACTION_STOP_WORKOUT
            }
            context.startService(intent)
        }
    }

    private var workoutMode: String = "Old School"
    private var targetReps: Int = 10
    private var currentReps: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("Workout foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WORKOUT -> {
                workoutMode = intent.getStringExtra(EXTRA_WORKOUT_MODE) ?: "Old School"
                targetReps = intent.getIntExtra(EXTRA_TARGET_REPS, 10)
                currentReps = 0
                startForeground(NOTIFICATION_ID, createNotification())
                Timber.d("Workout service started: $workoutMode, $targetReps reps")
            }
            ACTION_STOP_WORKOUT -> {
                Timber.d("Workout service stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't need binding
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vitruvian Workout",
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound/vibration
            ).apply {
                description = "Shows ongoing workout status"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Vitruvian Workout Active")
        .setContentText("$workoutMode - $currentReps/$targetReps reps")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true) // Cannot be dismissed
        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(createPendingIntent())
        .build()

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("Workout foreground service destroyed")
    }
}
