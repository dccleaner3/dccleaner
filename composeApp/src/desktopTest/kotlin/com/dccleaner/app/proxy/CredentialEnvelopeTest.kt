package com.dccleaner.app.proxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialEnvelopeTest {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()

    @Test
    fun envelopeOpensWithServerFormat() {
        val envelope = CredentialEnvelope.seal("tester", "p@ss \"word\" 한글", keyPair.public)
        val opened = Json.parseToJsonElement(open(envelope, keyPair.private)).jsonObject

        assertEquals("tester", opened["id"]?.jsonPrimitive?.content)
        assertEquals("p@ss \"word\" 한글", opened["pw"]?.jsonPrimitive?.content)
    }

    @Test
    fun envelopeHasVersionAndStandardBase64Fields() {
        val fields = Json.parseToJsonElement(CredentialEnvelope.seal("a", "b", keyPair.public)).jsonObject

        assertEquals(setOf("v", "k", "iv", "c"), fields.keys)
        assertEquals(1, fields["v"]?.jsonPrimitive?.content?.toInt())
        val decoder = Base64.getDecoder()
        assertEquals(12, decoder.decode(fields["iv"]!!.jsonPrimitive.content).size)
        assertEquals(384, decoder.decode(fields["k"]!!.jsonPrimitive.content).size)
    }

    @Test
    fun defaultOaepParametersCannotOpenEnvelope() {
        // Guards the explicit MGF1-SHA256 spec: the platform default uses MGF1-SHA1.
        val fields = Json.parseToJsonElement(CredentialEnvelope.seal("a", "b", keyPair.public)).jsonObject
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        assertFailsWith<Exception> {
            cipher.doFinal(Base64.getDecoder().decode(fields["k"]!!.jsonPrimitive.content))
        }
    }

    @Test
    fun bundledServerKeyIsUsable() {
        val fields = Json.parseToJsonElement(CredentialEnvelope.seal("a", "b")).jsonObject
        assertEquals(setOf("v", "k", "iv", "c"), fields.keys)
    }

    private fun open(envelope: String, privateKey: PrivateKey): String {
        val fields = Json.parseToJsonElement(envelope).jsonObject
        val decoder = Base64.getDecoder()
        val aesKey = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
            init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256)
            doFinal(decoder.decode(fields["k"]!!.jsonPrimitive.content))
        }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(128, decoder.decode(fields["iv"]!!.jsonPrimitive.content))
            )
            String(doFinal(decoder.decode(fields["c"]!!.jsonPrimitive.content)), Charsets.UTF_8)
        }
    }
}
