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
import kotlinx.coroutines.flow.first
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
            profile.iotaKeyPair?.let { prefs[Keys.iotaKeyPair] = secureStorage.encrypt(it).orEmpty() }
            profile.medicalPrePublicKey?.let { prefs[Keys.medicalPrePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.medicalPreSecretKey?.let { prefs[Keys.medicalPreSecretKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPrePublicKey?.let { prefs[Keys.pghdPrePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPreSecretKey?.let { prefs[Keys.pghdPreSecretKey] = secureStorage.encrypt(it).orEmpty() }
            profile.prePublicKey?.let { prefs[Keys.prePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.preSecretKey?.let { prefs[Keys.preSecretKey] = secureStorage.encrypt(it).orEmpty() }
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
            profile.iotaKeyPair?.let { prefs[Keys.iotaKeyPair] = secureStorage.encrypt(it).orEmpty() }
            profile.medicalPrePublicKey?.let { prefs[Keys.medicalPrePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.medicalPreSecretKey?.let { prefs[Keys.medicalPreSecretKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPrePublicKey?.let { prefs[Keys.pghdPrePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.pghdPreSecretKey?.let { prefs[Keys.pghdPreSecretKey] = secureStorage.encrypt(it).orEmpty() }
            profile.prePublicKey?.let { prefs[Keys.prePublicKey] = secureStorage.encrypt(it).orEmpty() }
            profile.preSecretKey?.let { prefs[Keys.preSecretKey] = secureStorage.encrypt(it).orEmpty() }
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

    override suspend fun getUnlockedProfile(): PatientProfile {
        val prefs = dataStore.data.first()
        require(prefs[Keys.sessionState] == SESSION_UNLOCKED) { "Patient session is locked." }

        val patientId = secureStorage.decrypt(prefs[Keys.patientId])
        require(!patientId.isNullOrBlank()) { "Patient identity is not available." }

        return PatientProfile(
            id = patientId,
            idHash = secureStorage.decrypt(prefs[Keys.patientIdHash]),
            iotaAddress = secureStorage.decrypt(prefs[Keys.iotaAddress]),
            iotaKeyPair = secureStorage.decrypt(prefs[Keys.iotaKeyPair]),
            medicalPrePublicKey = secureStorage.decrypt(prefs[Keys.medicalPrePublicKey])
                ?: secureStorage.decrypt(prefs[Keys.prePublicKey]),
            medicalPreSecretKey = secureStorage.decrypt(prefs[Keys.medicalPreSecretKey])
                ?: secureStorage.decrypt(prefs[Keys.preSecretKey]),
            pghdPrePublicKey = secureStorage.decrypt(prefs[Keys.pghdPrePublicKey]),
            pghdPreSecretKey = secureStorage.decrypt(prefs[Keys.pghdPreSecretKey]),
            prePublicKey = secureStorage.decrypt(prefs[Keys.prePublicKey]),
            preSecretKey = secureStorage.decrypt(prefs[Keys.preSecretKey]),
            pghdPublicKey = secureStorage.decrypt(prefs[Keys.pghdPublicKey]),
            pghdSecretKey = secureStorage.decrypt(prefs[Keys.pghdSecretKey]),
            name = secureStorage.decrypt(prefs[Keys.profileName]),
            birthPlace = secureStorage.decrypt(prefs[Keys.birthPlace]),
            dateOfBirth = secureStorage.decrypt(prefs[Keys.dateOfBirth]),
            gender = secureStorage.decrypt(prefs[Keys.gender]),
            religion = secureStorage.decrypt(prefs[Keys.religion]),
            education = secureStorage.decrypt(prefs[Keys.education]),
            occupation = secureStorage.decrypt(prefs[Keys.occupation]),
            maritalStatus = secureStorage.decrypt(prefs[Keys.maritalStatus])
        )
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
        val iotaKeyPair = stringPreferencesKey("patient_iota_key_pair")
        val medicalPrePublicKey = stringPreferencesKey("patient_medical_pre_public_key")
        val medicalPreSecretKey = stringPreferencesKey("patient_medical_pre_secret_key")
        val pghdPrePublicKey = stringPreferencesKey("patient_pghd_pre_public_key")
        val pghdPreSecretKey = stringPreferencesKey("patient_pghd_pre_secret_key")
        val prePublicKey = stringPreferencesKey("patient_pre_public_key")
        val preSecretKey = stringPreferencesKey("patient_pre_secret_key")
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
