package com.littlepages.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted app-private storage for an already-authorized offline book payload.
 * The caller must verify the current account's access grant with the trusted service
 * before calling [put]. Expiry here is a local safety check, not proof of entitlement.
 */
internal class OfflineBookVault(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME).apply { mkdirs() }

    @Throws(IOException::class)
    fun put(bookId: String, expiresAtEpochMillis: Long, clearContent: InputStream) {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        require(expiresAtEpochMillis > System.currentTimeMillis()) { "Offline access grant has expired" }

        val target = fileFor(bookId)
        val temporary = File(directory, "${target.name}.pending")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            updateAAD(aad(bookId, expiresAtEpochMillis))
        }

        try {
            FileOutputStream(temporary).use { fileOutput ->
                DataOutputStream(fileOutput).apply {
                    writeInt(FILE_MAGIC)
                    writeLong(expiresAtEpochMillis)
                    writeByte(cipher.iv.size)
                    write(cipher.iv)
                    flush()
                }
                val nonClosingOutput = object : FilterOutputStream(fileOutput) {
                    override fun close() = flush()
                }
                CipherOutputStream(nonClosingOutput, cipher).use { encryptedOutput ->
                    clearContent.copyTo(encryptedOutput, BUFFER_SIZE)
                }
                fileOutput.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: Exception) {
            temporary.delete()
            if (failure is IOException) throw failure
            throw IOException("Could not store the encrypted book", failure)
        }
    }

    @Throws(IOException::class)
    fun open(bookId: String): InputStream {
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val file = fileFor(bookId)
        val headerInput = DataInputStream(FileInputStream(file))
        try {
            if (headerInput.readInt() != FILE_MAGIC) throw IOException("Unsupported offline book file")
            val expiresAt = headerInput.readLong()
            val ivLength = headerInput.readUnsignedByte()
            if (ivLength !in MIN_IV_LENGTH..MAX_IV_LENGTH) throw IOException("Invalid encrypted book header")
            val iv = ByteArray(ivLength)
            headerInput.readFully(iv)
            if (expiresAt <= System.currentTimeMillis()) {
                headerInput.close()
                file.delete()
                throw OfflineBookExpiredException()
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(aad(bookId, expiresAt))
            }
            return CipherInputStream(headerInput, cipher)
        } catch (failure: Exception) {
            runCatching { headerInput.close() }
            if (failure is IOException) throw failure
            throw IOException("Could not open the encrypted book", failure)
        }
    }

    fun hasUnexpiredOfflineCopy(bookId: String): Boolean {
        val file = fileFor(bookId)
        if (!file.isFile) return false
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                input.readInt() == FILE_MAGIC && input.readLong() > System.currentTimeMillis()
            }
        }.getOrDefault(false).also { valid -> if (!valid) file.delete() }
    }

    fun remove(bookId: String): Boolean = !fileFor(bookId).exists() || fileFor(bookId).delete()

    private fun fileFor(bookId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(bookId.toByteArray(StandardCharsets.UTF_8))
        val stableName = digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory, "$stableName.book")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun aad(bookId: String, expiresAt: Long): ByteArray =
        "$bookId:$expiresAt".toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val DIRECTORY_NAME = "offline-books"
        const val KEY_ALIAS = "littlepages.offline.books.aesgcm.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FILE_MAGIC = 0x4C504F46 // LPOF
        const val GCM_TAG_BITS = 128
        const val MIN_IV_LENGTH = 12
        const val MAX_IV_LENGTH = 16
        const val BUFFER_SIZE = 32 * 1024
    }
}

internal class OfflineBookExpiredException : IOException("Offline access has expired. Connect to refresh access.")
