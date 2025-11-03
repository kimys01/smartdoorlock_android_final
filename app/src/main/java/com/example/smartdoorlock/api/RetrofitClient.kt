package com.example.smartdoorlock.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ✅ 수정된 서버 주소
    private const val BASE_URL = "http://10.0.2.2/smartdoorlock/"
    // 또는 에뮬레이터일 경우 → "http://10.0.2.2/smartdoorlock/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
