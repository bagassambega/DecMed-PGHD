package com.hackastic.decmed.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hackastic.decmed.data.local.security.PatientSecureStorage
import com.hackastic.decmed.data.remote.IotaPatientGateway
import com.hackastic.decmed.data.remote.PrePghdClient
import com.hackastic.decmed.domain.model.patient.PatientAuthState
import com.hackastic.decmed.domain.model.patient.PatientProfile
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import com.hackastic.decmed.domain.repository.PatientAuthRepository
import com.hackastic.decmed.domain.repository.PatientCryptoBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PatientAuthRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val cryptoBridge: PatientCryptoBridge,
    private val secureStorage: PatientSecureStorage,
    private val prePghdClient: PrePghdClient,
    private val iotaPatientGateway: IotaPatientGateway
) : PatientAuthRepository {

    override val authState: Flow<PatientAuthState> = dataStore.data.map { prefs ->
        val patientId = secureStorage.decrypt(prefs[Keys.patientId])
        val profileName = secureStorage.decrypt(prefs[Keys.profileName])
        val sessionState = prefs[Keys.sessionState]

        when {
            patientId.isNullOrBlank() -> PatientAuthState.NeedsSignupOrSignin
            profileName.isNullOrBlank() -> PatientAuthState.NeedsProfile(patientId)
            sessionState != SESSION_UNLOCKED -> PatientAuthState.NeedsPin(patientId)
            else -> PatientAuthState.Authenticated(
                patientId = patientId,
                iotaAddress = secureStorage.decrypt(prefs[Keys.iotaAddress]),
                displayName = profileName
            )
        }
    }

    override suspend fun generateMnemonic(): String = cryptoBridge.generateMnemonic()

    override suspend fun signUp(draft: PatientRegistrationDraft) {
        require(draft.pin.matches(Regex("^\\d{6}$"))) { "PIN must contain exactly 6 digits." }
        require(draft.nik.matches(Regex("^\\d{16}$"))) { "NIK must contain exactly 16 digits." }
        require(draft.seedWords.trim().split(Regex("\\s+")).size == 12) {
            "Seed words must contain exactly 12 words."
        }

        val profile = cryptoBridge.deriveRegistrationProfile(draft)
        iotaPatientGateway.registerPatient(profile)
        prePghdClient.pushRegistration(profile)
        dataStore.edit { prefs ->
            prefs[Keys.patientId] = secureStorage.encrypt(profile.id).orEmpty()
            profile.idHash?.let { prefs[Keys.patientIdHash] = secureStorage.encrypt(it).orEmpty() }
            profile.iotaAddress?.let { prefs[Keys.iotaAddress] = secureStorage.encrypt(it).orEmpty() }
            profile.prePublicKey?.let { prefs[Keys.prePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPublicKey?.let { prefs[Keys.pghdPublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdSecretKey?.let { prefs[Keys.pghdSecretKey] = secureStorage.encrypt(it).orEmpty() }
            prefs[Keys.sessionState] = SESSION_UNLOCKED
        }
    }

    override suspend fun signIn(seedWords: String, nik: String, pin: String) {
        val draft = PatientRegistrationDraft(pin = pin, seedWords = seedWords, nik = nik)
        require(draft.pin.matches(Regex("^\\d{6}$"))) { "PIN must contain exactly 6 digits." }
        require(draft.nik.matches(Regex("^\\d{16}$"))) { "NIK must contain exactly 16 digits." }
        require(draft.seedWords.trim().split(Regex("\\s+")).size == 12) {
            "Seed words must contain exactly 12 words."
        }

        val profile = cryptoBridge.deriveRegistrationProfile(draft)
        iotaPatientGateway.ensureRegistered(profile)
        prePghdClient.pushRegistration(profile)
        dataStore.edit { prefs ->
            prefs[Keys.patientId] = secureStorage.encrypt(profile.id).orEmpty()
            profile.idHash?.let { prefs[Keys.patientIdHash] = secureStorage.encrypt(it).orEmpty() }
            profile.iotaAddress?.let { prefs[Keys.iotaAddress] = secureStorage.encrypt(it).orEmpty() }
            profile.prePublicKey?.let { prefs[Keys.prePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPublicKey?.let { prefs[Keys.pghdPublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdSecretKey?.let { prefs[Keys.pghdSecretKey] = secureStorage.encrypt(it).orEmpty() }
            prefs[Keys.sessionState] = SESSION_UNLOCKED
        }
    }

    override suspend fun saveProfile(profile: PatientProfile) {
        require(!profile.name.isNullOrBlank()) { "Name is required." }
        dataStore.edit { prefs ->
            prefs[Keys.patientId] = secureStorage.encrypt(profile.id).orEmpty()
            prefs[Keys.profileName] = secureStorage.encrypt(profile.name.orEmpty()).orEmpty()
            profile.birthPlace?.let { prefs[Keys.birthPlace] = secureStorage.encrypt(it).orEmpty() }
            profile.dateOfBirth?.let { prefs[Keys.dateOfBirth] = secureStorage.encrypt(it).orEmpty() }
            profile.gender?.let { prefs[Keys.gender] = secureStorage.encrypt(it).orEmpty() }
            profile.religion?.let { prefs[Keys.religion] = secureStorage.encrypt(it).orEmpty() }
            profile.education?.let { prefs[Keys.education] = secureStorage.encrypt(it).orEmpty() }
            profile.occupation?.let { prefs[Keys.occupation] = secureStorage.encrypt(it).orEmpty() }
            profile.maritalStatus?.let { prefs[Keys.maritalStatus] = secureStorage.encrypt(it).orEmpty() }
            prefs[Keys.sessionState] = SESSION_UNLOCKED
        }
    }

    override suspend fun unlock(pin: String) {
        require(pin.matches(Regex("^\\d{6}$"))) { "PIN must contain exactly 6 digits." }
        dataStore.edit { prefs ->
            prefs[Keys.sessionState] = SESSION_UNLOCKED
        }
    }

    override suspend fun signOut() {
        dataStore.edit { prefs ->
            prefs[Keys.sessionState] = SESSION_LOCKED
        }
    }

    private object Keys {
        val patientId = stringPreferencesKey("patient_id")
        val patientIdHash = stringPreferencesKey("patient_id_hash")
        val iotaAddress = stringPreferencesKey("patient_iota_address")
        val prePublicKey = stringPreferencesKey("patient_pre_public_key")
        val pghdPublicKey = stringPreferencesKey("patient_pghd_public_key")
        val pghdSecretKey = stringPreferencesKey("patient_pghd_secret_key")
        val profileName = stringPreferencesKey("patient_profile_name")
        val birthPlace = stringPreferencesKey("patient_birth_place")
        val dateOfBirth = stringPreferencesKey("patient_date_of_birth")
        val gender = stringPreferencesKey("patient_gender")
        val religion = stringPreferencesKey("patient_religion")
        val education = stringPreferencesKey("patient_education")
        val occupation = stringPreferencesKey("patient_occupation")
        val maritalStatus = stringPreferencesKey("patient_marital_status")
        val sessionState = stringPreferencesKey("patient_session_state")
    }

    private companion object {
        const val SESSION_LOCKED = "locked"
        const val SESSION_UNLOCKED = "unlocked"
    }
}
