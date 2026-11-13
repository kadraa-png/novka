package com.kadraa.novka.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kadraa.novka.domain.PromptMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val mainCurrency: String = "BAM",
    val defaultAccountId: Long = 0,
    val lastAccountId: Long = 0,
    val walletEnabled: Boolean = true,
    val raiffeisenEnabled: Boolean = true,
    val promptMode: PromptMode = PromptMode.UNTIL_CONFIDENT,
    val lockEnabled: Boolean = false,
    val biometric: Boolean = true,
    val lockDelaySeconds: Int = 0,
    val hideAmountOnLockScreen: Boolean = false,
    val theme: String = "system",
    val ratesFetchedAt: Long = 0,
    /** "budgetId:yyyy-MM:level" for every budget alert already shown. */
    val budgetAlerts: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsKey = stringPreferencesKey("settings")
    private val pinKey = stringPreferencesKey("pin")

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val now = prefs[settingsKey]?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
            prefs[settingsKey] = json.encodeToString(Settings.serializer(), change(now))
        }
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        // Hashing takes a noticeable fraction of a second, so it stays off the main thread.
        val hash = withContext(Dispatchers.Default) { Pin.hash(pin, salt) }
        context.dataStore.edit { it[pinKey] = encoder.encodeToString(salt) + ":" + encoder.encodeToString(hash) }
    }

    suspend fun hasPin(): Boolean = context.dataStore.data.first()[pinKey] != null

    suspend fun checkPin(pin: String): Boolean {
        val stored = context.dataStore.data.first()[pinKey] ?: return false
        val (salt, hash) = stored.split(':').map { Base64.getDecoder().decode(it) }
        return MessageDigest.isEqual(hash, withContext(Dispatchers.Default) { Pin.hash(pin, salt) })
    }
}

object Pin {
    fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)).encoded
}
