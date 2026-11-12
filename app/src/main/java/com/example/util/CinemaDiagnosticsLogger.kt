package com.example.util

import android.util.Log
import com.example.data.model.OccupancyResult

/**
 * Structured diagnostic logger for cinema occupancy calculation.
 * Never logs HTTP cookies, tokens, Authorization headers, or secrets.
 */
object CinemaDiagnosticsLogger {
    private const val TAG = "CinemaOccupancyDiag"

    /**
     * Hook for JVM unit tests or custom telemetry sinks.
     */
    @Volatile
    var customPrinter: ((String) -> Unit)? = null

    fun logOccupancyCalculation(context: String, result: OccupancyResult) {
        val header = buildString {
            append("[Occupancy:$context] ")
            append("occupiedSeats=${result.occupiedSeats} ")
            append("(verified=${result.verifiedOccupiedSeats}, estimated=${result.estimatedOccupiedSeats}), ")
            append("active=${result.activeScreenings}, ")
            append("counted=${result.countedScreenings}, ")
            append("missingPid=${result.missingPresentationIdScreenings}, ")
            append("unverified=${result.unverifiedSeatStatusScreenings}, ")
            append("missing=${result.missingScreenings}, ")
            append("failed=${result.failedScreenings}, ")
            append("completeness=${result.completenessPercentage}%, ")
            append("isComplete=${result.isComplete}, ")
            append("unknownOccupancy=${result.unknownOccupancy}")
        }
        emitLog(header)

        for (diag in result.screeningDiagnostics) {
            val line = buildString {
                append("  [ActiveScreening] ")
                append("room=${diag.roomNumber ?: diag.room}, ")
                append("title=\"${diag.title}\", ")
                append("pid=${diag.presentationId ?: "none"}, ")
                append("pcode=${diag.presentationCode ?: "none"}, ")
                append("src=${diag.availabilitySource.name}, ")
                append("http=${diag.seatApiHttpCode ?: "?"}, ")
                append("state=${diag.seatFetchState.name}, ")
                append("time=${diag.startTime}-${diag.endTime}, ")
                append("rawSeats=${diag.totalRawSeatObjects ?: "?"}, ")
                append("valid=${diag.totalValidSeats ?: "?"}, ")
                append("avail=${diag.availableSeats ?: "?"}, ")
                append("occ=${diag.occupiedSeats ?: "?"}, ")
                append("res=${diag.reservedSeats ?: "?"}, ")
                append("blk=${diag.blockedSeats ?: "?"}, ")
                append("unk=${diag.unknownSeats ?: "?"}, ")
                append("cap=${diag.roomCapacity ?: "?"}, ")
                append("counted=${diag.countedOccupiedSeats ?: "null"}, ")
                append("kind=${diag.occupancyKind.name}, ")
                if (diag.observedPropertyKeys.isNotEmpty()) {
                    append("keys=${diag.observedPropertyKeys.joinToString("/")}, ")
                }
                append("included=${diag.includedInOccupancyTotal}")
                if (diag.exclusionReason != null) {
                    append(", excludedReason=${diag.exclusionReason}")
                }
            }
            emitLog(line)
        }
    }

    private fun emitLog(message: String) {
        val printer = customPrinter
        if (printer != null) {
            printer(message)
            return
        }
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // In plain JVM tests where android.util.Log is stubbed, ignore silently unless customPrinter is set
        }
    }
}
