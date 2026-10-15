package com.example.indoorpositioning

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// =============================================================================
// 데이터 모델
// =============================================================================

data class MapInfo(
    val id: Int,
    val name: String,
    val floor_image_url: String?,
    val ap_list: List<String>,
    val created_at: String,
    val image_width_px: Int?,
    val image_height_px: Int?,
    val real_width_m: Float?,
    val real_height_m: Float?,
    val origin_x_px: Float?,
    val origin_y_px: Float?
)

data class MapsResponse(
    val maps: List<MapInfo>
)

data class LocateRequest(
    val map_id: Int,
    val rssi_vector: Map<String, Int>,
    val k: Int = 3
)

data class LocateResponse(
    val x: Float,
    val y: Float,
    val map_id: Int,
    val matched_aps: Int
)

// =============================================================================
// Retrofit API 인터페이스
// =============================================================================

interface ApiService {

    @GET("maps")
    suspend fun getMaps(): Response<MapsResponse>

    @POST("locate")
    suspend fun locate(@Body request: LocateRequest): Response<LocateResponse>
}

// =============================================================================
// Retrofit 싱글톤
// =============================================================================

object RetrofitClient {
    private const val BASE_URL = "https://wifi-collector.onrender.com/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
