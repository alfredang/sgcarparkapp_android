package com.alfredang.sgcarpark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LTADataMallClient(
    private val accountKey: String = BuildConfig.LTA_ACCOUNT_KEY,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val baseUrl = "https://datamall2.mytransport.sg/ltaodataservice/CarParkAvailabilityv2".toHttpUrl()

    suspend fun fetchCarparks(): List<Carpark> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl)
            .header("AccountKey", accountKey)
            .header("accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("LTA DataMall returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("LTA DataMall returned an empty body")
            json.decodeFromString<LtaCarparkAvailabilityResponse>(body)
                .value
                .mapNotNull { it.toCarpark() }
        }
    }
}
