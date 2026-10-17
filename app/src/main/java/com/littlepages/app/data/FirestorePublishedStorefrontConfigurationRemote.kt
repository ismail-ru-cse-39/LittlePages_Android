package com.littlepages.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read-only Firestore adapter for immutable published storefront revisions. */
class FirestorePublishedStorefrontConfigurationRemote(
    private val firestore: FirebaseFirestore,
    private val decoder: SharedPreferencesStorefrontConfigurationCache
) : PublishedStorefrontConfigurationRemote {
    override suspend fun fetchCurrent(): VersionedStorefrontSnapshot {
        val manifest = firestore.collection("publicConfiguration").document("current")
            .get().awaitResult().data ?: error("Published content manifest is missing")
        val configurationRevision = manifest.requiredLong("configurationRevision")
        val catalogRevision = manifest.requiredLong("catalogRevision")

        val configurationDocument = firestore.collection("configurationRevisions")
            .document(configurationRevision.toString()).get().awaitResult().data
            ?: error("Configuration revision $configurationRevision is missing")
        val configuration = configurationDocument.requiredMap("configuration")

        val productDocuments = firestore.collection("catalogRevisions")
            .document(catalogRevision.toString()).collection("products")
            .get().awaitResult().documents
        val products = productDocuments.map { document ->
            val data = document.data ?: error("Product ${document.id} has no data")
            if ("id" in data) data else data + ("id" to document.id)
        }

        return decoder.decodeFirestoreSnapshot(manifest, configuration, products)
    }

    private fun Map<String, Any?>.requiredLong(name: String): Long = when (val value = this[name]) {
        is Number -> value.toLong()
        else -> error("Manifest field '$name' is missing or invalid")
    }

    private fun Map<String, Any?>.requiredMap(name: String): Map<String, Any?> {
        val value = this[name] as? Map<*, *> ?: error("Configuration field '$name' is missing or invalid")
        return value.entries.associate { (key, item) ->
            (key as? String ?: error("Configuration field '$name' contains a non-string key")) to item
        }
    }
}

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
