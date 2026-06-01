package com.hackastic.decmed.patient

import com.hackastic.decmed.data.patient.DeterministicPatientCryptoBridge
import com.hackastic.decmed.domain.model.patient.PatientRegistrationDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientCompatibilityVectorTest {
    @Test
    fun generatedMnemonicMatchesTwelveWordContract() = runBlocking {
        val bridge = DeterministicPatientCryptoBridge()
        val words = bridge.generateMnemonic().split(" ")

        assertEquals(12, words.size)
        assertTrue(words.all { it.isNotBlank() })
    }

    @Test
    fun registrationProfileDerivationIsDeterministic() = runBlocking {
        val bridge = DeterministicPatientCryptoBridge()
        val draft = PatientRegistrationDraft(
            pin = "123456",
            seedWords = "abandon ability able about above absent absorb abstract absurd abuse access accident",
            nik = "1234567890123456"
        )

        val first = bridge.deriveRegistrationProfile(draft)
        val second = bridge.deriveRegistrationProfile(draft)

        assertEquals(first, second)
        assertEquals("1234567890123456", first.id)
        assertEquals(first.pghdPublicKey, second.pghdPublicKey)
        assertEquals(first.pghdSecretKey, second.pghdSecretKey)
        assertTrue(first.pghdPublicKey?.isNotBlank() == true)
        assertTrue(first.pghdSecretKey?.isNotBlank() == true)
    }
}
