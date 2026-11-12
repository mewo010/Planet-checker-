package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.PlanetDatabase
import com.example.data.repository.AttendanceHistoryRepository
import com.example.data.repository.CinemaRepository
import com.example.data.repository.SeatAvailabilityRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Background periodic monitor scheduled via WorkManager (every 15 minutes when network is available).
 * Captures attendance snapshots around screening completion and records monitoring gaps honestly
 * if Android pauses execution or the device is offline.
 */
class CinemaMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val cinemaRepository = CinemaRepository()
        val seatRepository = SeatAvailabilityRepository()
        val dao = PlanetDatabase.getInstance(applicationContext).attendanceDao()
        val historyRepository = AttendanceHistoryRepository(dao)

        val now = ZonedDateTime.now(ZoneId.of("Asia/Jerusalem"))
        val rawMinutes = now.hour * 60 + now.minute
        val cinemaMinutes = cinemaRepository.normalizeCinemaMinutes(rawMinutes)

        val result = cinemaRepository.loadSchedule(cinemaMinutes)
        if (!result.isLive) {
            historyRepository.recordOfflineAttempt(result.cinemaDate)
            return Result.retry()
        }

        val enriched = cinemaRepository.enrichWithLiveSeatAvailability(
            screenings = result.screenings,
            seatRepository = seatRepository,
            forceRefreshActive = false
        )

        historyRepository.recordLiveObservation(
            screenings = enriched,
            cinemaDate = result.cinemaDate,
            now = now,
            cinemaNowMin = cinemaMinutes
        )

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "planet_checker_periodic_monitor"

        fun schedulePeriodicMonitoring(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<CinemaMonitorWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }
}
