package com.alfredang.sgcarpark

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class LtaCarparkAvailabilityResponse(
    @SerialName("value")
    val value: List<LtaCarparkAvailability>,
)

@Serializable
data class LtaCarparkAvailability(
    @SerialName("CarParkID")
    val carParkId: String,
    @SerialName("Area")
    val area: String = "",
    @SerialName("Development")
    val development: String = "",
    @SerialName("Location")
    val location: String = "",
    @SerialName("AvailableLots")
    val availableLots: Int = 0,
    @SerialName("LotType")
    val lotType: String = "",
    @SerialName("Agency")
    val agency: String = "",
)

data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)

data class Carpark(
    val id: String,
    val area: String,
    val development: String,
    val coordinate: Coordinate,
    val availableLots: Int,
    val lotType: String,
    val agency: String,
) {
    val title: String = development.ifBlank { id }
    val agencyName: String = when (agency.uppercase(Locale.ROOT)) {
        "HDB" -> "Housing & Development Board"
        "LTA" -> "Land Transport Authority"
        "URA" -> "Urban Redevelopment Authority"
        else -> agency
    }
    val subtitle: String = listOf(area, agencyName).filter { it.isNotBlank() }.joinToString(" • ")
    val lotTypeName: String = when (lotType.uppercase(Locale.ROOT)) {
        "C" -> "Cars"
        "Y" -> "Motorcycles"
        "H" -> "Heavy vehicles"
        else -> lotType.ifBlank { "Lots" }
    }

    fun distanceTo(other: Coordinate): Double = haversineMeters(coordinate, other)

    fun matches(query: String): Boolean {
        val normalized = query.lowercase(Locale.ROOT)
        return id.lowercase(Locale.ROOT).contains(normalized) ||
            development.lowercase(Locale.ROOT).contains(normalized) ||
            area.lowercase(Locale.ROOT).contains(normalized) ||
            agency.lowercase(Locale.ROOT).contains(normalized)
    }
}

data class PlaceSearchResult(
    val name: String,
    val address: String,
    val coordinate: Coordinate,
)

fun LtaCarparkAvailability.toCarpark(): Carpark? {
    val parts = location.split(" ", ",").mapNotNull { it.trim().toDoubleOrNull() }
    if (parts.size < 2) return null
    return Carpark(
        id = carParkId,
        area = area,
        development = development,
        coordinate = Coordinate(parts[0], parts[1]),
        availableLots = availableLots,
        lotType = lotType,
        agency = agency,
    )
}

fun List<Carpark>.nearestTo(coordinate: Coordinate): Carpark? =
    minByOrNull { it.distanceTo(coordinate) }

fun haversineMeters(start: Coordinate, end: Coordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val dLat = Math.toRadians(end.latitude - start.latitude)
    val dLon = Math.toRadians(end.longitude - start.longitude)
    val lat1 = Math.toRadians(start.latitude)
    val lat2 = Math.toRadians(end.latitude)
    val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

fun Double.formatDistance(): String =
    if (this < 1000) "${this.toInt()} m" else String.format(Locale.ROOT, "%.1f km", this / 1000.0)
