package com.spencehouse.logue.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class VehicleUpdateWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}
