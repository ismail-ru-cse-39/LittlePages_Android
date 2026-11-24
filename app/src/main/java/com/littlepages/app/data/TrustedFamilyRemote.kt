package com.littlepages.app.data

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TrustedFamilyRemote(app: FirebaseApp) {
    private val functions = FirebaseFunctions.getInstance(app, "asia-south1")
    private val firestore = FirebaseFirestore.getInstance(app)

    suspend fun saveChild(nickname: String, ageBand: String, childId: String?): OfflineBookChildProfile {
        val payload = mutableMapOf<String, Any>("nickname" to nickname, "ageBand" to ageBand)
        if (!childId.isNullOrBlank()) payload["childId"] = childId
        val data = call("saveChildProfile", payload)
        return OfflineBookChildProfile(data.requiredString("childId"), data.requiredString("nickname"), data.requiredString("ageBand"))
    }

    suspend fun deleteChild(childId: String) {
        call("deleteChildProfile", mapOf("childId" to childId))
    }

    suspend fun saveProgress(childId: String, bookId: String, page: Int, bookmarks: List<Int>, updatedAt: Long) {
        call("saveReadingProgress", mapOf(
            "childId" to childId, "bookId" to bookId, "page" to page, "bookmarks" to bookmarks, "updatedAt" to updatedAt
        ))
    }

    suspend fun fetchProgress(accountId: String, childId: String): List<RemoteReadingProgress> = withContext(Dispatchers.IO) {
        val snapshot = firestore.collection("users").document(accountId).collection("children").document(childId)
            .collection("progress").get().awaitFamily()
        snapshot.documents.mapNotNull { document ->
            val page = (document.getLong("page") ?: return@mapNotNull null).toInt()
            val bookmarks = document.get("bookmarks") as? List<*> ?: emptyList<Any>()
            RemoteReadingProgress(
                bookId = document.id,
                page = page,
                bookmarks = bookmarks.mapNotNull { (it as? Number)?.toInt() },
                updatedAt = document.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
            )
        }
    }

    suspend fun requestDeletion(reason: String): String = call("requestAccountDeletion", mapOf("reason" to reason)).requiredString("requestId")

    suspend fun exportSummary(): String {
        val data = call("exportFamilyData", emptyMap())
        val profiles = data["profiles"] as? List<*> ?: emptyList<Any>()
        if (profiles.isEmpty()) return "No server family profiles were found."
        return profiles.mapNotNull { it as? Map<*, *> }.joinToString("\n") { profile ->
            val nickname = profile["nickname"] as? String ?: "Child"
            val progress = profile["progress"] as? List<*> ?: emptyList<Any>()
            "$nickname · ${progress.size} saved book progress records"
        }
    }

    suspend fun createComment(postId: String, body: String): RemoteCommunityPost {
        val data = call("createCommunityComment", mapOf("postId" to postId, "body" to body))
        return RemoteCommunityPost(data.requiredString("id"), body, postId, null, data.requiredString("moderationState"))
    }

    suspend fun listComments(postId: String): List<RemoteCommunityPost> {
        val comments = call("listCommunityComments", mapOf("postId" to postId))["comments"] as? List<*> ?: emptyList<Any>()
        return comments.mapNotNull { item ->
            val record = item as? Map<*, *> ?: return@mapNotNull null
            RemoteCommunityPost(
                id = record["id"] as? String ?: return@mapNotNull null,
                body = record["body"] as? String ?: "",
                categoryId = record["postId"] as? String ?: postId,
                authorUid = record["authorUid"] as? String,
                moderationState = record["moderationState"] as? String ?: "pending"
            )
        }
    }

    suspend fun createPost(body: String, categoryId: String, imageId: String?): RemoteCommunityPost {
        val payload = mutableMapOf<String, Any>("body" to body, "categoryId" to categoryId)
        if (!imageId.isNullOrBlank()) payload["imageId"] = imageId
        val data = call("createCommunityPost", payload)
        return RemoteCommunityPost(data.requiredString("id"), body, categoryId, null, data.requiredString("moderationState"))
    }

    suspend fun listPosts(): List<RemoteCommunityPost> {
        val posts = call("listCommunityPosts", emptyMap())["posts"] as? List<*> ?: emptyList<Any>()
        return posts.mapNotNull { item ->
            val record = item as? Map<*, *> ?: return@mapNotNull null
            RemoteCommunityPost(
                id = record["id"] as? String ?: return@mapNotNull null,
                body = record["body"] as? String ?: "",
                categoryId = record["categoryId"] as? String ?: "",
                authorUid = record["authorUid"] as? String,
                moderationState = record["moderationState"] as? String ?: "pending"
            )
        }
    }

    suspend fun report(targetType: String, targetId: String, reason: String) {
        call("createCommunityReport", mapOf("targetType" to targetType, "targetId" to targetId, "reason" to reason))
    }

    suspend fun createReview(bookId: String, rating: Int, body: String): String =
        call("createReview", mapOf("bookId" to bookId, "rating" to rating, "body" to body)).requiredString("id")

    suspend fun uploadImage(bytes: ByteArray, contentType: String): String = withContext(Dispatchers.IO) {
        val prepared = call("createCommunityImageUploadUrl", mapOf("contentType" to contentType, "byteLength" to bytes.size))
        val url = URI(prepared.requiredString("url"))
        if (url.scheme != "https") error("The image upload URL is invalid")
        val connection = (url.toURL().openConnection() as? HttpURLConnection) ?: error("The image upload connection is invalid")
        try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", prepared.requiredString("contentType"))
            connection.outputStream.use { output: OutputStream -> output.write(bytes) }
            if (connection.responseCode !in 200..299) error("Image upload failed with HTTP ${connection.responseCode}")
        } finally {
            connection.disconnect()
        }
        call("finalizeCommunityImage", mapOf("imageId" to prepared.requiredString("imageId"))).requiredString("imageId")
    }

    private suspend fun call(name: String, payload: Map<String, Any>): Map<String, Any?> {
        val result = functions.getHttpsCallable(name).call(payload).awaitFamily()
        return result.data.asFamilyMap()
    }
}

internal data class RemoteReadingProgress(val bookId: String, val page: Int, val bookmarks: List<Int>, val updatedAt: Long)
internal data class RemoteCommunityPost(val id: String, val body: String, val categoryId: String, val authorUid: String?, val moderationState: String)

private fun Any?.asFamilyMap(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.associate { (key, value) ->
    (key as? String ?: error("Firebase returned an invalid field name")) to value
} ?: error("Firebase returned an invalid response")

private fun Map<String, Any?>.requiredString(key: String): String = this[key] as? String ?: error("Firebase response is missing $key")

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitFamily(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            task.result?.let(continuation::resume) ?: continuation.resumeWithException(IllegalStateException("Firebase returned an empty response"))
        } else {
            continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed"))
        }
    }
}
