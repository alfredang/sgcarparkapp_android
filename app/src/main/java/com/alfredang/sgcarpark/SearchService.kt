package com.alfredang.sgcarpark

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class SearchService(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun search(query: String): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        @Suppress("DEPRECATION")
        Geocoder(appContext, Locale("en", "SG"))
            .getFromLocationName("$trimmed, Singapore", 8, 1.16, 103.55, 1.48, 104.12)
            .orEmpty()
            .mapNotNull { address ->
                val latitude = address.latitude
                val longitude = address.longitude
                if (latitude == 0.0 && longitude == 0.0) {
                    null
                } else {
                    PlaceSearchResult(
                        name = address.featureName ?: trimmed,
                        address = address.getAddressLine(0).orEmpty(),
                        coordinate = Coordinate(latitude, longitude),
                    )
                }
            }
    }
}
