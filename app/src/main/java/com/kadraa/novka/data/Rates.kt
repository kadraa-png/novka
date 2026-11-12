package com.kadraa.novka.data

import com.kadraa.novka.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Returns units of each currency per one euro, or null when the service cannot be reached. */
interface RateProvider {
    suspend fun fetch(): Map<String, Double>?
}

private suspend fun getJson(url: String): JsonObject? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) return@runCatching null
            Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private fun JsonObject.toRates(): Map<String, Double> = entries.mapNotNull { (code, value) ->
    val upper = code.uppercase(Locale.ROOT)
    val rate = runCatching { value.jsonPrimitive.doubleOrNull }.getOrNull()
    if (rate != null && rate > 0 && upper.length == 3 && Money.isCurrency(upper)) upper to rate else null
}.toMap()

/** Only the request for euro rates is sent. No user data leaves the phone. */
class ErApiProvider : RateProvider {
    override suspend fun fetch(): Map<String, Double>? =
        getJson("https://open.er-api.com/v6/latest/EUR")?.get("rates")?.let { runCatching { it.jsonObject.toRates() }.getOrNull() }
            ?.takeIf { it.size > 20 }
}

class CurrencyApiProvider : RateProvider {
    override suspend fun fetch(): Map<String, Double>? =
        getJson("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.min.json")
            ?.get("eur")?.let { runCatching { it.jsonObject.toRates() }.getOrNull() }
            ?.takeIf { it.size > 20 }
}
