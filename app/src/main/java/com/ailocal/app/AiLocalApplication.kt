package com.ailocal.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ailocal.app.storage.AppDatabase

/**
 * Application entry point.
 *
 * This app is fully offline: no network SDK, no analytics, no ads, no
 * cloud AI service of any kind. Everything (models, speech, chats) stays
 * on the device.
 */
class AiLocalApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val timers = NotificationChannel(
                CHANNEL_TIMERS,
                getString(R.string.actions_time_category),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(timers)

            val media = NotificationChannel(
                CHANNEL_MEDIA,
                getString(R.string.actions_media_category),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(media)
        }
    }

    companion object {
        const val CHANNEL_TIMERS = "channel_timers"
        const val CHANNEL_MEDIA = "channel_media"
    }
}
