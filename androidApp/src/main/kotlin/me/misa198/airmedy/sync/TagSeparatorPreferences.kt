package me.misa198.airmedy.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** How the scanner splits multi-name artist, album artist and composer tags. */
internal data class TagSeparatorSettings(
    val enabled: Boolean = true,
    val delimiters: List<String> = ArtistSeparator.DEFAULT_TOKENS,
) {
    /** Identifies the settings a scan used, so a later change can prompt a rescan. */
    val signature: String get() = "$enabled|${ArtistSeparator.serializeDelimiters(delimiters)}"
}

private val Context.tagSeparatorDataStore by preferencesDataStore(name = "tag_separators")
private val EnabledKey = booleanPreferencesKey("enabled")
private val DelimitersKey = stringPreferencesKey("delimiters")
private val CustomDelimitersKey = stringPreferencesKey("custom_delimiters")
private val AppliedSignatureKey = stringPreferencesKey("applied_signature")

internal class TagSeparatorPreferences(private val context: Context) {
    val settings: Flow<TagSeparatorSettings> = context.tagSeparatorDataStore.data.map { preferences ->
        TagSeparatorSettings(
            enabled = preferences[EnabledKey] ?: true,
            delimiters = ArtistSeparator.parseDelimiters(preferences[DelimitersKey]),
        )
    }

    /** User-added tokens beyond the base symbols, kept even while not active. */
    val customDelimiters: Flow<List<String>> = context.tagSeparatorDataStore.data.map { preferences ->
        preferences[CustomDelimitersKey]?.let(ArtistSeparator::parseDelimiters).orEmpty()
    }

    /** The settings signature of the last completed scan, or null before the first one. */
    val appliedSignature: Flow<String?> = context.tagSeparatorDataStore.data.map { it[AppliedSignatureKey] }

    suspend fun current(): TagSeparatorSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.tagSeparatorDataStore.edit { it[EnabledKey] = enabled }
    }

    suspend fun setDelimiters(tokens: List<String>) {
        context.tagSeparatorDataStore.edit { it[DelimitersKey] = ArtistSeparator.serializeDelimiters(tokens) }
    }

    suspend fun setCustomDelimiters(tokens: List<String>) {
        context.tagSeparatorDataStore.edit { preferences ->
            val clean = tokens.map(String::trim).filter(String::isNotEmpty).distinct()
            if (clean.isEmpty()) preferences.remove(CustomDelimitersKey)
            else preferences[CustomDelimitersKey] = ArtistSeparator.serializeDelimiters(clean)
        }
    }

    suspend fun setAppliedSignature(signature: String) {
        context.tagSeparatorDataStore.edit { it[AppliedSignatureKey] = signature }
    }
}
