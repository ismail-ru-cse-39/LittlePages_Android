package com.littlepages.app.data

import android.content.Context
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates trusted grant checks, private download, integrity validation, and encrypted storage. */
internal class OfflineBookDownloadManager(
    context: Context,
    private val remote: FirebaseOfflineBookAccessRemote,
    private val vault: OfflineBookVault = OfflineBookVault(context)
) {
    suspend fun download(
        accountId: String,
        bookId: String,
        childId: String,
        locale: String
    ): OfflineBookGrant = withContext(Dispatchers.IO) {
        require(accountId.isNotBlank())
        val grant = remote.refreshGrant(bookId, childId, locale)
        if (grant.bookId != bookId || grant.childId != childId || grant.locale != locale ||
            grant.refreshAfter >= grant.expiresAt || grant.refreshAfter <= java.time.Instant.now() ||
            grant.expiresAt.toEpochMilli() - System.currentTimeMillis() > OfflineBookAccessPolicy().maximumLeaseMillis
        ) {
            throw IOException("The trusted service returned an invalid offline grant")
        }

        val download = remote.createDownloadUrl(bookId, childId, locale)
        if (download.contentVersion != grant.contentVersion || download.expiresAt > grant.expiresAt ||
            download.expiresAt <= java.time.Instant.now() || download.byteLength !in 1..OfflineBookAccessPolicy().maximumBookBytes ||
            !download.sha256.matches(Regex("^[a-f0-9]{64}$"))
        ) {
            throw IOException("The trusted service returned invalid book download metadata")
        }
        val url = download.url
        val downloadHost = url.host?.lowercase()
        if (url.scheme != "https" || downloadHost !in setOf("storage.googleapis.com", "firebasestorage.googleapis.com") ||
            url.userInfo != null || url.fragment != null
        ) {
            throw IOException("The trusted service returned an invalid download URL")
        }

        val connection = (url.toURL().openConnection() as? HttpURLConnection)
            ?: throw IOException("Unsupported book download connection")
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = "GET"
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Book download failed with HTTP $status")
            val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (contentType != "application/pdf") throw IOException("The approved book asset is not a PDF")
            if (connection.contentLengthLong >= 0 && connection.contentLengthLong != download.byteLength) {
                throw IOException("The downloaded book size does not match the approved asset")
            }
            connection.inputStream.use { response ->
                vault.put(
                    accountId = accountId,
                    bookId = bookId,
                    expiresAtEpochMillis = download.expiresAt.toEpochMilli(),
                    clearContent = VerifiedPdfInputStream(response, download.byteLength, download.sha256),
                    entitlementExpiresAtEpochMillis = grant.entitlementExpiresAt?.toEpochMilli()
                )
            }
            grant
        } finally {
            connection.disconnect()
        }
    }

    suspend fun remove(accountId: String, bookId: String) = withContext(Dispatchers.IO) {
        vault.remove(accountId, bookId)
        remote.revokeGrant(bookId)
    }

    suspend fun removeAccount(accountId: String): Boolean = withContext(Dispatchers.IO) {
        vault.removeAccount(accountId)
    }

    suspend fun open(accountId: String, bookId: String): InputStream = withContext(Dispatchers.IO) {
        vault.open(accountId, bookId)
    }

    suspend fun hasOfflineCopy(accountId: String, bookId: String): Boolean = withContext(Dispatchers.IO) {
        vault.hasUnexpiredOfflineCopy(accountId, bookId)
    }

    private class VerifiedPdfInputStream(
        input: InputStream,
        private val expectedBytes: Long,
        expectedSha256: String
    ) : FilterInputStream(input) {
        private val digest = MessageDigest.getInstance("SHA-256")
        private val expectedDigest = expectedSha256
        private val header = ByteArray(PDF_HEADER.size)
        private var size = 0L
        private var headerSize = 0
        private var verified = false

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) accept(byteArrayOf(value.toByte()), 0, 1) else verifyEnd()
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) accept(buffer, offset, count) else if (count < 0) verifyEnd()
            return count
        }

        private fun accept(bytes: ByteArray, offset: Int, count: Int) {
            size += count
            if (size > expectedBytes) throw IOException("The downloaded book exceeds its approved size")
            val headerCount = minOf(count, header.size - headerSize)
            if (headerCount > 0) {
                bytes.copyInto(header, headerSize, offset, offset + headerCount)
                headerSize += headerCount
            }
            digest.update(bytes, offset, count)
        }

        private fun verifyEnd() {
            if (verified) return
            verified = true
            if (size != expectedBytes || !header.contentEquals(PDF_HEADER)) {
                throw IOException("The downloaded book is incomplete or is not a PDF")
            }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            if (!MessageDigest.isEqual(actualDigest.toByteArray(), expectedDigest.toByteArray())) {
                throw IOException("The downloaded book failed its integrity check")
            }
        }

        companion object {
            private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 45_000
    }
}
