package com.hackastic.decmed.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class HospitalPersonnelQrPayloadTest {
    @Test
    fun decodesLegacyAddressAtPublicKeyPayload() {
        assertEquals(
            "0xabc" to "public-key-value",
            decodeHospitalPersonnelQrPayload("0xabc@public-key-value")
        )
    }

    @Test
    fun removesLineBreaksInsertedAroundLongQrContent() {
        assertEquals(
            "0xabc" to "publickeyvalue",
            decodeHospitalPersonnelQrPayload(" 0xabc \n @ publickey\nvalue ")
        )
    }

    @Test
    fun decodesUrlEncodedPayload() {
        val payload = URLEncoder.encode(
            "0xabc@public-key-value",
            StandardCharsets.UTF_8.name()
        )
        assertEquals(
            "0xabc" to "public-key-value",
            decodeHospitalPersonnelQrPayload("decmed://personnel?payload=$payload")
        )
    }
}
