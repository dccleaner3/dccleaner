package com.dccleaner.app.proxy

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

const val SERVER_PUBLIC_KEY =
    "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAqh4tUDPO5bLSaUI5PM1UWova8rJzPQv8qsD9WAIZhQdxLEV/CP8QVWB87ziojghhvPtqVbDswKtmSP2WGixqZsJ5GVlO2rfi1GL9O0ZpqT+nqonNL1dx1C6c7sbETR9R/BDzHbtJvEzXcCDecpJm8herM78CNXqGDTBcyW6KJErV2x90JymNk8v3SUX3camfo5Xsose/4vjmC3ZU6G2EEGetrmhcSL2I9qZJNXFbkntMeGN5yfX5e1fM4FW8+SqZWDnu27rffHsVEdXELaX3LlOdhf9Dzf+bj1/FxOJ6wLP9cXUKKi3FBpXWeOQ95DISSovAdRl5vU9QnIpEwcKIvhyMAiS7b6UnoovMrblIAZatp66pJDK5pnL2ZvZkzKEQlwT/qsfLPuQRN0476TGML01egJsY07OytvjhMAld9YbZKXrZT0zxN8aI2AdftjNT7oz9GCjO/IGoVflfrijiaVwgAKdEq/DKtR0aAAIFkx8lx56ftIVhcl8dWlQzJja1AgMBAAE="

// Without an explicit spec, JDK and Android default MGF1 to SHA-1 and the server can't decrypt.
internal val OAEP_SHA256 = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

/** Hybrid RSA-OAEP(SHA-256) + AES-256-GCM envelope that only the server's private key can open. */
object CredentialEnvelope {
    fun seal(userId: String, password: String, publicKey: PublicKey = serverPublicKey()): String {
        val plaintext = buildJsonObject {
            put("id", userId)
            put("pw", password)
        }.toString().toByteArray(Charsets.UTF_8)
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val ciphertext = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            doFinal(plaintext)
        }
        plaintext.fill(0)
        val encryptedKey = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding").run {
            init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256)
            doFinal(aesKey.encoded)
        }
        val base64 = Base64.getEncoder()
        return buildJsonObject {
            put("v", JsonPrimitive(1))
            put("k", base64.encodeToString(encryptedKey))
            put("iv", base64.encodeToString(iv))
            put("c", base64.encodeToString(ciphertext))
        }.toString()
    }

    fun serverPublicKey(encoded: String = SERVER_PUBLIC_KEY): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
}
