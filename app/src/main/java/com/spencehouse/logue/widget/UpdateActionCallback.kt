package com.spencehouse.logue.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.spencehouse.logue.service.VehicleUpdateWorker

class UpdateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<VehicleUpdateWorker>().build()
        workManager.enqueue(request)

        // Optionally force an immediate partial update with cached data
        // For a more complete update, the Worker should call updateAll
        BatteryWidget().updateAll(context)
    }
}