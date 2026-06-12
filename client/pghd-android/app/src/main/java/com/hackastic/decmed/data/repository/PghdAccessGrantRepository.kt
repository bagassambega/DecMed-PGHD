package com.hackastic.decmed.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hackastic.decmed.viewmodel.PatientGrantAccessKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StoredPghdAccessGrant(
    val id: String,
    val hospitalPersonnelIotaAddress: String,
    val accessKind: PatientGrantAccessKind,
    val accessLogIndexes: List<Long>,
    val grantedAt: String,
    val revokedAt: String? = null
) {
    val isActive: Boolean get() = revokedAt == null
}

class PghdAccessGrantRepository(
    private val dataStore: DataStore<Preferences>
) {
    val grants: Flow<List<StoredPghdAccessGrant>> = dataStore.data.map { prefs ->
        decodeGrants(prefs[Keys.grantsJson].orEmpty())
    }

    suspend fun activeGrants(): List<StoredPghdAccessGrant> =
        grants.first().filter { it.isActive }

    suspend fun nextAccessLogIndex(): Long {
        val allIndexes = grants.first().flatMap { it.accessLogIndexes }
        return (allIndexes.maxOrNull() ?: -1L) + 1L
    }

    suspend fun saveGrant(
        hospitalPersonnelIotaAddress: String,
        accessKind: PatientGrantAccessKind,
        accessLogIndexes: List<Long>,
        grantedAt: String
    ): StoredPghdAccessGrant {
        val grant = StoredPghdAccessGrant(
            id = UUID.randomUUID().toString(),
            hospitalPersonnelIotaAddress = hospitalPersonnelIotaAddress,
            accessKind = accessKind,
            accessLogIndexes = accessLogIndexes,
            grantedAt = grantedAt
        )
        dataStore.edit { prefs ->
            val existing = decodeGrants(prefs[Keys.grantsJson].orEmpty())
            prefs[Keys.grantsJson] = encodeGrants(existing + grant)
        }
        return grant
    }

    suspend fun markRevoked(id: String, revokedAt: String) {
        dataStore.edit { prefs ->
            val updated = decodeGrants(prefs[Keys.grantsJson].orEmpty()).map { grant ->
                if (grant.id == id) grant.copy(revokedAt = revokedAt) else grant
            }
            prefs[Keys.grantsJson] = encodeGrants(updated)
        }
    }

    private fun decodeGrants(raw: String): List<StoredPghdAccessGrant> = runCatching {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            StoredPghdAccessGrant(
                id = item.getString("id"),
                hospitalPersonnelIotaAddress = item.getString("hospital_personnel_iota_address"),
                accessKind = PatientGrantAccessKind.valueOf(item.getString("access_kind")),
                accessLogIndexes = item.getJSONArray("access_log_indexes").let { indexes ->
                    List(indexes.length()) { indexes.getLong(it) }
                },
                grantedAt = item.getString("granted_at"),
                revokedAt = item.optString("revoked_at").ifBlank { null }
            )
        }
    }.getOrElse { emptyList() }

    private fun encodeGrants(grants: List<StoredPghdAccessGrant>): String =
        JSONArray().apply {
            grants.forEach { grant ->
                put(
                    JSONObject()
                        .put("id", grant.id)
                        .put("hospital_personnel_iota_address", grant.hospitalPersonnelIotaAddress)
                        .put("access_kind", grant.accessKind.name)
                        .put("access_log_indexes", JSONArray(grant.accessLogIndexes))
                        .put("granted_at", grant.grantedAt)
                        .put("revoked_at", grant.revokedAt ?: "")
                )
            }
        }.toString()

    private object Keys {
        val grantsJson = stringPreferencesKey("pghd_access_grants_json")
    }
}
