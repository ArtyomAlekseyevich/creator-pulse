package dev.creatorpulse.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Secrets (session cookie, Open Cloud keys) live in EncryptedSharedPreferences and never in the snapshot. */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        try {
            EncryptedSharedPreferences.create(
                context, "secure_store", master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore got wiped/corrupted (happens after some restores). Start fresh rather than crash.
            context.deleteSharedPreferences("secure_store")
            EncryptedSharedPreferences.create(
                context, "secure_store", master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val _cookie = MutableStateFlow(prefs.getString(KEY_COOKIE, null))
    val cookieFlow: StateFlow<String?> = _cookie
    val cookie: String? get() = _cookie.value

    private val _sessionExpired = MutableStateFlow(prefs.getBoolean(KEY_EXPIRED, false))
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    private val _apiKeys = MutableStateFlow(loadKeys())
    val apiKeysFlow: StateFlow<List<ApiKeyEntry>> = _apiKeys
    val apiKeys: List<ApiKeyEntry> get() = _apiKeys.value

    fun setCookie(value: String?) {
        val clean = value?.trim()?.removePrefix(".ROBLOSECURITY=")?.takeIf { it.isNotBlank() }
        prefs.edit().putString(KEY_COOKIE, clean).putBoolean(KEY_EXPIRED, false).apply()
        _cookie.value = clean
        _sessionExpired.value = false
    }

    fun markSessionExpired() {
        prefs.edit().putBoolean(KEY_EXPIRED, true).apply()
        _sessionExpired.value = true
    }

    fun addApiKey(label: String, key: String) {
        val entry = ApiKeyEntry(UUID.randomUUID().toString(), label.ifBlank { "Key ${apiKeys.size + 1}" }, key.trim())
        saveKeys(apiKeys + entry)
    }

    fun removeApiKey(id: String) = saveKeys(apiKeys.filterNot { it.id == id })

    private fun loadKeys(): List<ApiKeyEntry> = runCatching {
        prefs.getString(KEY_API_KEYS, null)?.let { json.decodeFromString(ListSerializer(ApiKeyEntry.serializer()), it) }
    }.getOrNull() ?: emptyList()

    private fun saveKeys(list: List<ApiKeyEntry>) {
        prefs.edit().putString(KEY_API_KEYS, json.encodeToString(ListSerializer(ApiKeyEntry.serializer()), list)).apply()
        _apiKeys.value = list
    }

    companion object {
        private const val KEY_COOKIE = "roblosecurity"
        private const val KEY_EXPIRED = "session_expired"
        private const val KEY_API_KEYS = "api_keys"
    }
}
