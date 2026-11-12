package com.finance.splitup.data

import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ExchangeRatesResponse(
    val amount: Double = 1.0,
    val base: String = "EUR",
    val date: String = "",
    val rates: Map<String, Double> = emptyMap()
)

interface CurrencyApi {
    @GET("latest")
    suspend fun getLatestRates(@Query("from") from: String = "EUR"): ExchangeRatesResponse
}

object CurrencyApiService {
    private const val TAG = "CurrencyApiService"

    private val _lastUpdatedTime = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastUpdatedTime: StateFlow<Long> = _lastUpdatedTime.asStateFlow()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.frankfurter.app/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: CurrencyApi = retrofit.create(CurrencyApi::class.java)

    suspend fun updateLiveRates() {
        try {
            val response = api.getLatestRates("EUR")
            if (response.rates.isNotEmpty()) {
                applyRates(response.rates)
                Log.d(TAG, "Successfully updated live rates from Frankfurter API (Date: ${response.date})")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frankfurter API failed, trying fallback live exchange rate API", e)
        }

        // Secondary fallback to open.er-api.com if primary API failed
        try {
            fetchFallbackRates()
        } catch (e: Exception) {
            Log.e(TAG, "Fallback exchange rate API also failed", e)
        }
    }

    private fun applyRates(rates: Map<String, Double>) {
        rates["USD"]?.let { if (it > 0) Currencies.liveUsdRate = it }
        rates["GBP"]?.let { if (it > 0) Currencies.liveGbpRate = it }
        rates["INR"]?.let { if (it > 0) Currencies.liveInrRate = it }
        rates["CAD"]?.let { if (it > 0) Currencies.liveCadRate = it }
        rates["AUD"]?.let { if (it > 0) Currencies.liveAudRate = it }
        rates["JPY"]?.let { if (it > 0) Currencies.liveJpyRate = it }
        rates["CHF"]?.let { if (it > 0) Currencies.liveChfRate = it }
        rates["SGD"]?.let { if (it > 0) Currencies.liveSgdRate = it }
        rates["CNY"]?.let { if (it > 0) Currencies.liveCnyRate = it }
        if (Currencies.liveUsdRate > 0) {
            Currencies.liveAedRate = Currencies.liveUsdRate * 3.6725
        }
        _lastUpdatedTime.value = System.currentTimeMillis()
    }

    private suspend fun fetchFallbackRates() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = java.net.URL("https://open.er-api.com/v6/latest/EUR")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (conn.responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(jsonText)
            if (json.has("rates")) {
                val ratesObj = json.getJSONObject("rates")
                val ratesMap = mutableMapOf<String, Double>()
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    ratesMap[key] = ratesObj.optDouble(key, 0.0)
                }
                applyRates(ratesMap)
                Log.d(TAG, "Successfully updated live rates from fallback open.er-api")
            }
        }
    }
}
