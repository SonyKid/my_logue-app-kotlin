package com.spencehouse.logue

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LogueApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                "vehicle_status",
                "Vehicle Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for vehicle charging and other statuses"
            }

            val climateChannel = NotificationChannel(
                "climate_control",
                "Climate Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while climate control is running"
            }

            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(climateChannel)
        }
    }
}
