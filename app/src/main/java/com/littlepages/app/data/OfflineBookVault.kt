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
internal class OfflineBookVault(
    context: Context,
    private val policy: OfflineBookAccessPolicy = OfflineBookAccessPolicy()
) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME).apply { mkdirs() }

    @Throws(IOException::class)
    @Synchronized
    fun put(
        accountId: String,
        bookId: String,
        expiresAtEpochMillis: Long,
        clearContent: InputStream,
        entitlementExpiresAtEpochMillis: Long? = null
    ) {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val now = System.currentTimeMillis()
        require(expiresAtEpochMillis > now) { "Offline access grant has expired" }
        require(expiresAtEpochMillis - now <= policy.maximumLeaseMillis) { "Offline access grant exceeds the allowed lease" }
        val effectiveExpiry = if (policy.removeWhenEntitlementExpires && entitlementExpiresAtEpochMillis != null) {
            minOf(expiresAtEpochMillis, entitlementExpiresAtEpochMillis)
        } else expiresAtEpochMillis
        require(effectiveExpiry > now) { "Book entitlement has expired" }

        val target = fileFor(accountId, bookId)
        removeExpiredCopies(accountId)
        require(policy.canAddBook(storedBookCount(accountId), target.exists())) { "Offline book limit reached" }
        val temporary = File(target.parentFile, "${target.name}.pending")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            updateAAD(aad(accountId, bookId, effectiveExpiry))
        }

        try {
            FileOutputStream(temporary).use { fileOutput ->
                DataOutputStream(fileOutput).apply {
                    writeInt(FILE_MAGIC)
                    writeLong(effectiveExpiry)
                    writeByte(cipher.iv.size)
                    write(cipher.iv)
                    flush()
                }
                val nonClosingOutput = object : FilterOutputStream(fileOutput) {
                    override fun close() = flush()
                }
                CipherOutputStream(nonClosingOutput, cipher).use { encryptedOutput ->
                    copyWithLimit(clearContent, encryptedOutput, policy.maximumBookBytes)
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
    fun open(accountId: String, bookId: String): InputStream {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val file = fileFor(accountId, bookId)
        val headerInput = DataInputStream(FileInputStream(file))
        try {
            if (headerInput.readInt() != FILE_MAGIC) throw IOException("Unsupported offline book file")
            val expiresAt = headerInput.readLong()
            val ivLength = headerInput.readUnsignedByte()
            if (ivLength !in MIN_IV_LENGTH..MAX_IV_LENGTH) throw IOException("Invalid encrypted book header")
            val iv = ByteArray(ivLength)
            headerInput.readFully(iv)
            val now = System.currentTimeMillis()
            if (expiresAt <= now || expiresAt - now > policy.maximumLeaseMillis) {
                headerInput.close()
                file.delete()
                throw OfflineBookExpiredException()
            }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(aad(accountId, bookId, expiresAt))
            }
            return CipherInputStream(headerInput, cipher)
        } catch (failure: Exception) {
            runCatching { headerInput.close() }
            if (failure is IOException) throw failure
            throw IOException("Could not open the encrypted book", failure)
        }
    }

    fun hasUnexpiredOfflineCopy(accountId: String, bookId: String): Boolean {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val file = fileFor(accountId, bookId)
        if (!file.isFile) return false
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input -> readValidExpiry(input) != null }
        }.getOrDefault(false).also { valid -> if (!valid) file.delete() }
    }

    @Synchronized
    fun remove(accountId: String, bookId: String): Boolean {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        require(bookId.isNotBlank()) { "bookId must not be blank" }
        val file = fileFor(accountId, bookId)
        return !file.exists() || file.delete()
    }

    @Synchronized
    fun storedBookCount(accountId: String): Int {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        removeExpiredCopies(accountId)
        return accountDirectory(accountId).listFiles { file -> file.isFile && file.extension == "book" }?.size ?: 0
    }

    @Synchronized
    fun storedBytes(accountId: String): Long {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        removeExpiredCopies(accountId)
        return accountDirectory(accountId).listFiles { file -> file.isFile && file.extension == "book" }?.sumOf { it.length() } ?: 0L
    }

    /** Remove expired or unreadable entries for the signed-in account. */
    @Synchronized
    fun removeExpiredCopies(accountId: String): Int {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        val accountFiles = accountDirectory(accountId).listFiles() ?: return 0
        val books = accountFiles.filter { it.isFile && it.extension == "book" }
        val expiredRemoved = books.count { file ->
            val isValid = runCatching {
                DataInputStream(FileInputStream(file)).use { input -> readValidExpiry(input) != null }
            }.getOrDefault(false)
            !isValid && file.delete()
        }
        val abandonedWritesRemoved = accountFiles.filter { it.isFile && it.name.endsWith(".pending") }.count { it.delete() }
        return expiredRemoved + abandonedWritesRemoved
    }

    /** Explicit account removal also removes all cached offline books for that account. */
    @Synchronized
    fun removeAccount(accountId: String): Boolean {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
        val accountDirectory = accountDirectory(accountId)
        return !accountDirectory.exists() || accountDirectory.deleteRecursively()
    }

    private fun fileFor(accountId: String, bookId: String): File {
        return File(accountDirectory(accountId), "${stableHash(bookId)}.book")
    }

    private fun accountDirectory(accountId: String): File = File(directory, stableHash(accountId)).apply { mkdirs() }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maximumBytes: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > maximumBytes) throw IOException("Offline book exceeds the permitted file size")
            output.write(buffer, 0, read)
        }
    }

    private fun readValidExpiry(input: DataInputStream): Long? {
        if (input.readInt() != FILE_MAGIC) return null
        val expiresAt = input.readLong()
        val ivLength = input.readUnsignedByte()
        if (ivLength !in MIN_IV_LENGTH..MAX_IV_LENGTH) return null
        input.readFully(ByteArray(ivLength))
        val now = System.currentTimeMillis()
        return expiresAt.takeIf { it > now && it - now <= policy.maximumLeaseMillis }
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

    private fun aad(accountId: String, bookId: String, expiresAt: Long): ByteArray =
        "$accountId:$bookId:$expiresAt".toByteArray(StandardCharsets.UTF_8)

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

internal class OfflineBookExpiredException : IOException("Offline access is invalid or expired. Connect to refresh access.")
