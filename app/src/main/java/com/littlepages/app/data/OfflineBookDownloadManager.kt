package com.littlepages.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coordinates trusted grant checks, private download, integrity validation, and encrypted storage. */
internal class OfflineBookDownloadManager(
    context: Context,
    private val remote: FirebaseOfflineBookAccessRemote,
    private val vault: OfflineBookVault = OfflineBookVault(context)
) {
    private val appContext = context.applicationContext
    private val grantIndex = appContext.getSharedPreferences(GRANT_INDEX, Context.MODE_PRIVATE)
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
        fetchVerifiedPdf(download) { verified ->
            vault.put(
                accountId = accountId,
                bookId = bookId,
                expiresAtEpochMillis = download.expiresAt.toEpochMilli(),
                clearContent = verified,
                entitlementExpiresAtEpochMillis = grant.entitlementExpiresAt?.toEpochMilli()
            )
        }
        rememberGrant(accountId, grant)
        grant
    }

    suspend fun maintain(accountId: String) = withContext(Dispatchers.IO) {
        require(accountId.isNotBlank())
        vault.removeExpiredCopies(accountId)
        val now = System.currentTimeMillis()
        val prefix = "$accountId|"
        for (key in grantIndex.all.keys.filter { it.startsWith(prefix) }) {
            val record = runCatching { JSONObject(grantIndex.getString(key, null) ?: continue) }.getOrNull() ?: continue
            val bookId = record.optString("bookId")
            val expiresAt = record.optLong("expiresAt")
            val entitlementExpiresAt = record.optLong("entitlementExpiresAt").takeIf { record.has("entitlementExpiresAt") }
            val refreshAfter = record.optLong("refreshAfter")
            val expired = expiresAt <= now || (entitlementExpiresAt != null && entitlementExpiresAt <= now) ||
                !vault.hasUnexpiredOfflineCopy(accountId, bookId)
            if (expired) {
                runCatching { remove(accountId, bookId) }
                grantIndex.edit().remove(key).apply()
            } else if (refreshAfter in 1..now) {
                runCatching { download(accountId, bookId, record.getString("childId"), record.getString("locale")) }
            }
        }
    }

    suspend fun prepareReadingFile(accountId: String, bookId: String, childId: String, locale: String): PreparedReadingFile =
        withContext(Dispatchers.IO) {
            val directory = File(appContext.cacheDir, READER_CACHE).apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, "current.pdf")
            if (vault.hasUnexpiredOfflineCopy(accountId, bookId)) {
                vault.open(accountId, bookId).use { input -> file.outputStream().use(input::copyTo) }
                return@withContext prepared(file, "offline")
            }
            val download = remote.resolveBookAccess(bookId, childId, locale)
            if (download.expiresAt <= java.time.Instant.now() || download.byteLength !in 1..OfflineBookAccessPolicy().maximumBookBytes ||
                !download.sha256.matches(Regex("^[a-f0-9]{64}$"))
            ) {
                throw IOException("The trusted service returned invalid book access")
            }
            fetchVerifiedPdf(download) { verified -> file.outputStream().use { output -> verified.copyTo(output) } }
            prepared(file, "online")
        }

    fun renderPreparedPage(file: File, pageNumber: Int): Bitmap {
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val index = (pageNumber - 1).coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(index).use { page ->
                    val width = 720
                    val height = (width * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    fun releaseReadingFile() {
        File(appContext.cacheDir, READER_CACHE).deleteRecursively()
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

    private fun rememberGrant(accountId: String, grant: OfflineBookGrant) {
        val record = JSONObject()
            .put("bookId", grant.bookId)
            .put("childId", grant.childId)
            .put("locale", grant.locale)
            .put("refreshAfter", grant.refreshAfter.toEpochMilli())
            .put("expiresAt", grant.expiresAt.toEpochMilli())
        grant.entitlementExpiresAt?.let { record.put("entitlementExpiresAt", it.toEpochMilli()) }
        grantIndex.edit().putString("$accountId|${grant.bookId}", record.toString()).apply()
    }

    private fun prepared(file: File, source: String): PreparedReadingFile {
        val pageCount = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { it.pageCount }
        }
        if (pageCount < 1) throw IOException("The book PDF has no pages")
        return PreparedReadingFile(file, pageCount, source)
    }

    private fun fetchVerifiedPdf(download: OfflineBookDownload, consumer: (InputStream) -> Unit) {
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
                consumer(VerifiedPdfInputStream(response, download.byteLength, download.sha256))
            }
        } finally {
            connection.disconnect()
        }
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
        const val GRANT_INDEX = "offline_grant_index_v1"
        const val READER_CACHE = "authorized-reader"
    }
}

internal data class PreparedReadingFile(val file: File, val pageCount: Int, val source: String)
