package com.example.data.seats

import com.example.data.model.Seat
import com.example.data.model.SeatAvailabilitySummary
import com.example.data.model.SeatLayoutMetadata
import com.example.data.model.SeatPosition
import com.example.data.model.summarizeSeats
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.math.BigDecimal

/**
 * Parses Yes Planet `seats-statusV2` JSON payloads cleanly with dynamic JSON keys.
 *
 * Expected hierarchy:
 * - root.rd -> layout metadata (seatRadius, width, height)
 * - root.S  -> map of screen/room objects (dynamic keys, e.g. "1")
 *   - room.n -> room name (e.g. "אולם 7")
 *   - room.G -> map of seat groups (dynamic keys, e.g. "0")
 *     - group.R -> map of rows (dynamic keys, e.g. "2")
 *       - row.n -> row number/label (e.g. "1")
 *       - row.S -> map of seats inside the row (dynamic keys, e.g. "4")
 *         - seat.n  -> seat number (e.g. "22")
 *         - seat.rd -> seat coordinates {"cx": ..., "cy": ...}
 *         - any additional fields (e.g. "tg", "hc", etc.) -> preserved verbatim in [Seat.rawFields]
 * - root.E  -> optional array of non-seat elements
 * - root.ST -> metadata object
 *
 * Note that property "S" appears at BOTH root (screens) and row (seats) levels; this parser
 * traverses each level explicitly so layout metadata and row containers are never counted as seats.
 */
class SeatStatusParser(
    private val statusMapper: SeatStatusMapper = SeatStatusMapper(),
    moshi: Moshi = Moshi.Builder().build()
) {
    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    fun parse(
        rawJson: String,
        presentationId: String? = null,
        fetchedAtEpochMs: Long = System.currentTimeMillis(),
        httpStatusCode: Int? = null
    ): Result<SeatAvailabilitySummary> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty seats-statusV2 response body"))
        }

        val root = try {
            mapAdapter.fromJson(trimmed)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Malformed JSON in seats-statusV2 response", e))
        } ?: return Result.failure(IllegalArgumentException("Null root object in seats-statusV2 response"))

        val layoutMetadata = parseLayoutMetadata(root["rd"])
        val seats = mutableListOf<Seat>()
        val seenSeatKeys = mutableSetOf<String>()

        // Level 1: root["S"] = screens/rooms map
        val screensMap = asStringMap(root["S"])
        if (screensMap != null) {
            for ((roomKey, roomNodeAny) in screensMap) {
                val roomNode = asStringMap(roomNodeAny) ?: continue
                val roomName = normalizeScalar(roomNode["n"])

                // Level 2: room["G"] = groups map
                val groupsMap = asStringMap(roomNode["G"]) ?: continue
                for ((groupKey, groupNodeAny) in groupsMap) {
                    val groupNode = asStringMap(groupNodeAny) ?: continue

                    // Level 3: group["R"] = rows map
                    val rowsMap = asStringMap(groupNode["R"]) ?: continue
                    for ((rowKey, rowNodeAny) in rowsMap) {
                        val rowNode = asStringMap(rowNodeAny) ?: continue
                        val rowNumber = normalizeScalar(rowNode["n"])?.takeIf { it.isNotBlank() } ?: rowKey

                        // Level 4: row["S"] = seats map inside this row
                        val rowSeatsMap = asStringMap(rowNode["S"]) ?: continue
                        for ((seatKey, seatNodeAny) in rowSeatsMap) {
                            val seatNode = asStringMap(seatNodeAny) ?: continue
                            if (!isActualSeatObject(seatNode)) continue

                            val dedupeId = "$roomKey|$groupKey|$rowKey|$seatKey"
                            if (!seenSeatKeys.add(dedupeId)) continue

                            val seatNumber = normalizeScalar(seatNode["n"])?.takeIf { it.isNotBlank() } ?: seatKey
                            val position = parseSeatPosition(seatNode["rd"])

                            val rawFields = linkedMapOf<String, String>()
                            for ((fieldKey, fieldVal) in seatNode) {
                                if (fieldKey == "n" || fieldKey == "rd") continue
                                val normalized = normalizeAnyValue(fieldVal) ?: continue
                                rawFields[fieldKey] = normalized
                            }

                            val classification = statusMapper.classify(rawFields)
                            seats.add(
                                Seat(
                                    seatKey = seatKey,
                                    roomKey = roomKey,
                                    roomName = roomName,
                                    groupKey = groupKey,
                                    rowKey = rowKey,
                                    rowNumber = rowNumber,
                                    seatNumber = seatNumber,
                                    position = position,
                                    rawFields = rawFields,
                                    status = classification
                                )
                            )
                        }
                    }
                }
            }
        }

        val summary = summarizeSeats(
            seats = seats,
            presentationId = presentationId,
            layoutMetadata = layoutMetadata,
            hasUnverifiedStatuses = !statusMapper.config.isVerifiedAgainstWebsite ||
                seats.any { it.status == com.example.data.model.SeatAvailabilityStatus.UNKNOWN },
            httpStatusCode = httpStatusCode,
            fetchedAtEpochMs = fetchedAtEpochMs
        )
        return Result.success(summary)
    }

    private fun isActualSeatObject(seatNode: Map<String, Any?>): Boolean {
        // Guard against nested containers accidentally treated as seats
        if (seatNode.containsKey("G") || seatNode.containsKey("R") || seatNode.containsKey("S")) {
            return false
        }
        val seatNumber = normalizeScalar(seatNode["n"])
        val pos = parseSeatPosition(seatNode["rd"])
        // Must have a seat identifier OR spatial seat coordinates
        return !seatNumber.isNullOrBlank() || pos != null
    }

    private fun parseLayoutMetadata(rawRd: Any?): SeatLayoutMetadata? {
        val rdMap = asStringMap(rawRd) ?: return null
        return SeatLayoutMetadata(
            seatRadius = (rdMap["seatRadius"] as? Number)?.toDouble(),
            width = (rdMap["width"] as? Number)?.toDouble(),
            height = (rdMap["height"] as? Number)?.toDouble()
        )
    }

    private fun parseSeatPosition(rawRd: Any?): SeatPosition? {
        val rdMap = asStringMap(rawRd) ?: return null
        val cx = (rdMap["cx"] as? Number)?.toDouble() ?: return null
        val cy = (rdMap["cy"] as? Number)?.toDouble() ?: return null
        return SeatPosition(cx = cx, cy = cy)
    }

    private fun asStringMap(value: Any?): Map<String, Any?>? {
        val rawMap = value as? Map<*, *> ?: return null
        val result = linkedMapOf<String, Any?>()
        for ((k, v) in rawMap) {
            if (k is String) {
                result[k] = v
            }
        }
        return result
    }

    private fun normalizeScalar(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Boolean -> value.toString()
            is Number -> formatNumberCleanly(value)
            else -> null
        }
    }

    private fun normalizeAnyValue(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Boolean -> value.toString()
            is Number -> formatNumberCleanly(value)
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { normalizeAnyValue(it) ?: "null" }
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}:${normalizeAnyValue(it.value)}" }
            else -> value.toString()
        }
    }

    private fun formatNumberCleanly(num: Number): String {
        val d = num.toDouble()
        if (d.isFinite() && d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
            return d.toLong().toString()
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
    }
}
