package com.dawood.orbit.tools.cloudbrowser.real

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * On-device encrypted storage for per-server SSH passwords.
 *
 * Why this exists: the RAM-only [EphemeralCredentials] vault is wiped on
 * disconnect and process death, forcing the user to retype the VPS password
 * on every launch. Users asked the app to remember the login instead.
 *
 * How: one AES-256/GCM key held by the hardware-backed Android Keystore
 * (non-exportable; on supported devices it lives in the TEE/StrongBox), and
 * a JSON file in the app-private files directory mapping serverId to
 * Base64(IV || ciphertext+tag). No third-party dependency is needed and the
 * plaintext never touches logs. All methods do small file I/O and must be
 * called off the main thread.
 *
 * Website logins are a separate matter: the remote Chrome profile under
 * `~/.config/orbit-chrome` on the VPS is already persistent, so cookies and
 * passwords saved IN THE BROWSER survive target and Chrome restarts.
 */
class SavedCredentialStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val file: File = File(appContext.filesDir, "cloud_saved_credentials.json")
    private val lock = Any()

    /** Persists [password] encrypted for [serverId]; blank password deletes. */
    fun save(serverId: String, password: String) {
        synchronized(lock) {
            val map = readMap()
            if (password.isBlank()) {
                map.remove(serverId)
            } else {
                map.put(serverId, encrypt(password.toCharArray()))
            }
            writeMap(map)
        }
    }

    /** Returns the decrypted password for [serverId], or null when absent. */
    fun load(serverId: String): String? {
        synchronized(lock) {
            val packed = readMap().optString(serverId, "")
            if (packed.isEmpty()) return null
            return try {
                String(decrypt(packed))
            } catch (_: Exception) {
                // A failed keystore unlock / corrupt blob behaves as "not
                // saved" rather than crashing the connect flow.
                null
            }
        }
    }

    /** True when a password is saved for [serverId]. Never returns secrets. */
    fun hasPassword(serverId: String): Boolean {
        synchronized(lock) { return readMap().has(serverId) }
    }

    /** Drops the saved password for a deleted server, if any. */
    fun delete(serverId: String) {
        synchronized(lock) {
            val map = readMap()
            if (map.has(serverId)) {
                map.remove(serverId)
                writeMap(map)
            }
        }
    }

    private fun readMap(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }

    private fun writeMap(map: JSONObject) {
        try {
            file.writeText(map.toString())
        } catch (_: Exception) {
            // Best effort: failing to persist the secret must not block the
            // in-memory connection from completing.
        }
    }

    private fun encrypt(secret: CharArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(String(secret).toByteArray(Charsets.UTF_8))
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(packed: String): CharArray {
        val raw = Base64.decode(packed, Base64.NO_WRAP)
        require(raw.size > IV_LENGTH) { "credential blob too short" }
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val ct = raw.copyOfRange(IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8).toCharArray()
    }

    private fun secretKey(): SecretKey {
        val keystore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "orbit_cloud_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
