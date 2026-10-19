package com.littlepages.app.data

import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Calls the trusted service; the returned grant is authoritative over local cache state. */
internal class FirebaseOfflineBookAccessRemote(
    app: FirebaseApp,
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(app, "asia-south1")
) {
    suspend fun refreshGrant(bookId: String, childId: String, locale: String): OfflineBookGrant {
        val result = functions.getHttpsCallable("refreshOfflineBookGrant")
            .call(mapOf("bookId" to bookId, "childId" to childId, "locale" to locale))
            .awaitCallable()
        val data = result.data.asStringMap()
        return data.requiredMap("grant").let { grant ->
            OfflineBookGrant(
                bookId = grant.requiredString("bookId"),
                childId = grant.requiredString("childId"),
                locale = grant.requiredString("locale"),
                contentVersion = grant.requiredLong("contentVersion").toInt(),
                source = grant.requiredString("source"),
                refreshAfter = Instant.parse(grant.requiredString("refreshAfter")),
                expiresAt = Instant.parse(grant.requiredString("expiresAt")),
                entitlementExpiresAt = grant["entitlementExpiresAt"] as? String
                    ?.let(Instant::parse)
            )
        }
    }

    suspend fun createDownloadUrl(bookId: String, childId: String, locale: String): OfflineBookDownload {
        val result = functions.getHttpsCallable("createOfflineBookDownloadUrl")
            .call(mapOf("bookId" to bookId, "childId" to childId, "locale" to locale))
            .awaitCallable()
        val data = result.data.asStringMap()
        return OfflineBookDownload(
            url = URI(data.requiredString("url")),
            expiresAt = Instant.parse(data.requiredString("expiresAt")),
            contentVersion = data.requiredLong("contentVersion").toInt(),
            sha256 = data.requiredString("sha256"),
            byteLength = data.requiredLong("byteLength")
        )
    }

    suspend fun revokeGrant(bookId: String) {
        functions.getHttpsCallable("revokeOfflineBookGrant")
            .call(mapOf("bookId" to bookId))
            .awaitCallable()
    }
}

internal data class OfflineBookGrant(
    val bookId: String,
    val childId: String,
    val locale: String,
    val contentVersion: Int,
    val source: String,
    val refreshAfter: Instant,
    val expiresAt: Instant,
    val entitlementExpiresAt: Instant?
)

internal data class OfflineBookDownload(
    val url: URI,
    val expiresAt: Instant,
    val contentVersion: Int,
    val sha256: String,
    val byteLength: Long
)

private fun Any?.asStringMap(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { (key, value) ->
    (key as? String ?: error("Firebase returned an invalid field name")) to value
} ?: error("Firebase returned an invalid response")

private fun Map<String, Any?>.requiredMap(key: String): Map<String, Any?> = this[key].asStringMap()
private fun Map<String, Any?>.requiredString(key: String): String = this[key] as? String ?: error("Firebase response is missing $key")
private fun Map<String, Any?>.requiredLong(key: String): Long = (this[key] as? Number)?.toLong() ?: error("Firebase response is missing $key")

private suspend fun com.google.android.gms.tasks.Task<HttpsCallableResult>.awaitCallable(): HttpsCallableResult =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                task.result?.let(continuation::resume)
                    ?: continuation.resumeWithException(IllegalStateException("Firebase returned an empty response"))
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed"))
            }
        }
    }
