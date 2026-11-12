package com.example.data.seats

import com.example.data.model.SeatAvailabilityStatus

/**
 * Single declarative rule mapping verified raw seat JSON fields to a canonical [SeatAvailabilityStatus].
 * Rules are evaluated in order; the first matching rule wins.
 */
data class SeatStatusMappingRule(
    val id: String,
    val description: String,
    val predicate: (Map<String, String>) -> Boolean,
    val status: SeatAvailabilityStatus
)

/**
 * Centralized configuration for interpreting raw status fields returned by
 * `GET https://tickets5.planetcinema.co.il/api/seats/seats-statusV2`.
 *
 * IMPORTANT VERIFICATION NOTE:
 * In Yes Planet's `seats-statusV2` JSON payloads, seat objects can include fields such as:
 * - `tg` (observed e.g. `tg=1`)
 * - `hc` (e.g. `hc=1` / accessibility/handicap or status flag — unverified without live UI match)
 * - `hsn` (hidden/seat-number or status attribute — unverified without live UI match)
 * - `svga` (vector/seat group attribute — unverified without live UI match)
 *
 * Without side-by-side comparison of live booking page colors/labels against the raw JSON
 * for the same `presentationId`, these fields MUST NOT be guessed to mean AVAILABLE or OCCUPIED.
 * Unverified combinations intentionally map to [SeatAvailabilityStatus.UNKNOWN] while preserving
 * all raw keys & combinations in [SeatAvailabilitySummary.rawStatusHistogram] for inspection.
 */
data class SeatStatusMappingConfig(
    val rules: List<SeatStatusMappingRule>,
    val isVerifiedAgainstWebsite: Boolean,
    val notes: String = ""
) {
    companion object {
        /**
         * Safe default configuration: preserves raw status fields without guessing unverified codes.
         * Every seat whose status fields have not been explicitly verified maps to [SeatAvailabilityStatus.UNKNOWN].
         */
        val UNVERIFIED_DEFAULT = SeatStatusMappingConfig(
            rules = emptyList(),
            isVerifiedAgainstWebsite = false,
            notes = "Unverified seat fields (tg, hc, hsn, svga, etc.) safely default to UNKNOWN."
        )

        /**
         * Active application mapping config. Update here once each field value (e.g. tg, hc, st)
         * has been verified against the live Yes Planet seat selection colors.
         */
        val ACTIVE_CONFIG: SeatStatusMappingConfig = UNVERIFIED_DEFAULT

        fun fromRules(
            vararg rules: SeatStatusMappingRule,
            isVerifiedAgainstWebsite: Boolean = true,
            notes: String = ""
        ): SeatStatusMappingConfig = SeatStatusMappingConfig(
            rules = rules.toList(),
            isVerifiedAgainstWebsite = isVerifiedAgainstWebsite,
            notes = notes
        )

        fun exactFieldRule(
            id: String,
            fieldName: String,
            expectedValue: String,
            status: SeatAvailabilityStatus,
            description: String = "$fieldName == $expectedValue -> $status"
        ): SeatStatusMappingRule = SeatStatusMappingRule(
            id = id,
            description = description,
            predicate = { fields -> fields[fieldName] == expectedValue },
            status = status
        )
    }
}

class SeatStatusMapper(
    val config: SeatStatusMappingConfig = SeatStatusMappingConfig.ACTIVE_CONFIG
) {
    fun classify(rawFields: Map<String, String>): SeatAvailabilityStatus {
        for (rule in config.rules) {
            if (rule.predicate(rawFields)) {
                return rule.status
            }
        }
        return SeatAvailabilityStatus.UNKNOWN
    }
}
