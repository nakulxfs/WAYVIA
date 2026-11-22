package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class PhotonResponse(
    @Json(name = "features") val features: List<PhotonFeature> = emptyList()
)

data class PhotonFeature(
    @Json(name = "geometry") val geometry: PhotonGeometry? = null,
    @Json(name = "properties") val properties: PhotonProperties? = null
)

data class PhotonGeometry(
    @Json(name = "coordinates") val coordinates: List<Double> = emptyList() // [lon, lat]
)

data class PhotonProperties(
    @Json(name = "name") val name: String? = null,
    @Json(name = "city") val city: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "street") val street: String? = null,
    @Json(name = "osm_value") val osmValue: String? = null
)

interface PhotonGeocodingApi {
    @GET("api/")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("lat") biasLat: Double = 28.6139,
        @Query("lon") biasLon: Double = 77.2090,
        @Query("limit") limit: Int = 6
    ): PhotonResponse
}

object NetworkClientFactory {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "CommuteBuddy-Android/1.0 (Delhi NCR Open Transit)")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    val photonApi: PhotonGeocodingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://photon.komoot.io/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PhotonGeocodingApi::class.java)
    }

    /**
     * Attempts to download a GTFS ZIP archive from a configured URL (e.g. Delhi OTD or self-hosted FastAPI)
     * and cache it locally. Returns null cleanly on network failure or non-ZIP payload so the app
     * falls back reliably to cached or bundled Delhi NCR GTFS data.
     */
    fun downloadGtfsZipToCache(url: String, targetFile: File): File? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("zip") && !contentType.contains("octet-stream")) {
                    return null
                }
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
                targetFile
            }
        }.getOrNull()
    }
}
