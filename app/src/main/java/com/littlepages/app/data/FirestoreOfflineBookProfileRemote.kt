package com.littlepages.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads parent-owned child IDs for offline access; preview-only profiles are never sent to Firebase. */
internal class FirestoreOfflineBookProfileRemote(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchOwnedChildren(accountId: String): List<OfflineBookChildProfile> {
        require(accountId.isNotBlank())
        val snapshot = firestore.collection("users").document(accountId)
            .collection("children").get().awaitResult()
        return snapshot.documents.mapNotNull { document ->
            val nickname = document.getString("nickname")?.trim().orEmpty()
            val ageBand = document.getString("ageBand")?.trim().orEmpty()
            if (document.id.matches(CHILD_ID_PATTERN) && nickname.isNotBlank()) {
                OfflineBookChildProfile(document.id, nickname, ageBand)
            } else null
        }
    }

    private companion object {
        val CHILD_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,80}$")
    }
}

internal data class OfflineBookChildProfile(
    val id: String,
    val nickname: String,
    val ageBand: String
)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                task.result?.let(continuation::resume)
                    ?: continuation.resumeWithException(IllegalStateException("Firebase returned an empty result"))
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed"))
            }
        }
    }
