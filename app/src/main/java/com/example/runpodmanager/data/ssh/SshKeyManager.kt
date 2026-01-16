package com.example.runpodmanager.data.ssh

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.Security
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SshKeyManager"

@Singleton
class SshKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        // Registrar BouncyCastle como proveedor de seguridad
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ssh_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_COMMENT = "runpod-manager@android"
    }

    fun hasKeys(): Boolean {
        return prefs.contains(KEY_PRIVATE) && prefs.contains(KEY_PUBLIC)
    }

    fun generateKeys(): Pair<String, String> {
        Log.d(TAG, "Generando par de claves RSA 4096...")

        // Generar par de claves RSA usando BouncyCastle
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(4096)
        val keyPair = keyPairGenerator.generateKeyPair()

        // Convertir clave privada a formato PEM (PKCS8)
        val privateKeyWriter = StringWriter()
        JcaPEMWriter(privateKeyWriter).use { pemWriter ->
            pemWriter.writeObject(JcaPKCS8Generator(keyPair.private, null))
        }
        val privateKey = privateKeyWriter.toString()
        Log.d(TAG, "Clave privada generada, longitud: ${privateKey.length}")

        // Convertir clave pública a formato OpenSSH
        val rsaPublicKey = keyPair.public as RSAPublicKey
        val publicKeyOpenSSH = encodeRSAPublicKey(rsaPublicKey, KEY_COMMENT)
        Log.d(TAG, "Clave publica generada: ${publicKeyOpenSSH.take(50)}...")

        prefs.edit()
            .putString(KEY_PRIVATE, privateKey)
            .putString(KEY_PUBLIC, publicKeyOpenSSH)
            .apply()

        Log.d(TAG, "Claves guardadas en EncryptedSharedPreferences")
        return Pair(privateKey, publicKeyOpenSSH)
    }

    private fun encodeRSAPublicKey(publicKey: RSAPublicKey, comment: String): String {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()

        fun writeSSHString(data: ByteArray) {
            byteArrayOutputStream.write((data.size shr 24) and 0xFF)
            byteArrayOutputStream.write((data.size shr 16) and 0xFF)
            byteArrayOutputStream.write((data.size shr 8) and 0xFF)
            byteArrayOutputStream.write(data.size and 0xFF)
            byteArrayOutputStream.write(data)
        }

        writeSSHString("ssh-rsa".toByteArray(Charsets.US_ASCII))
        writeSSHString(publicKey.publicExponent.toByteArray())
        writeSSHString(publicKey.modulus.toByteArray())

        val encodedKey = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray())
        return "ssh-rsa $encodedKey $comment"
    }

    fun getPrivateKey(): String? {
        return prefs.getString(KEY_PRIVATE, null)
    }

    fun getPublicKey(): String? {
        return prefs.getString(KEY_PUBLIC, null)
    }

    fun deleteKeys() {
        prefs.edit()
            .remove(KEY_PRIVATE)
            .remove(KEY_PUBLIC)
            .apply()
        Log.d(TAG, "Claves eliminadas")
    }
}
