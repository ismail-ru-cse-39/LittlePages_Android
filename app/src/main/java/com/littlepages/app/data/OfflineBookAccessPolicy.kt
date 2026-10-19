package com.littlepages.app.data

/** Provisional offline policy from firebase/config/provisional-defaults.v1.json. */
internal data class OfflineBookAccessPolicy(
    val maximumBooksPerFamily: Int = 10,
    val maximumBookBytes: Long = 100L * 1024L * 1024L,
    val maximumLeaseMillis: Long = DAYS_30,
    val onlineRefreshAfterMillis: Long = DAYS_7,
    val removeWhenEntitlementExpires: Boolean = true
) {
    init {
        require(maximumBooksPerFamily > 0)
        require(maximumBookBytes > 0)
        require(maximumLeaseMillis > 0)
        require(onlineRefreshAfterMillis > 0 && onlineRefreshAfterMillis <= maximumLeaseMillis)
    }

    fun requiresOnlineRefresh(lastVerifiedAtEpochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        lastVerifiedAtEpochMillis <= 0 || nowEpochMillis < lastVerifiedAtEpochMillis || nowEpochMillis - lastVerifiedAtEpochMillis >= onlineRefreshAfterMillis

    fun leaseExpired(expiresAtEpochMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis >= expiresAtEpochMillis

    fun entitlementExpired(entitlementExpiresAtEpochMillis: Long?, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        removeWhenEntitlementExpires && entitlementExpiresAtEpochMillis != null && nowEpochMillis >= entitlementExpiresAtEpochMillis

    fun canAddBook(currentBookCount: Int, alreadyStored: Boolean): Boolean {
        require(currentBookCount >= 0)
        return alreadyStored || currentBookCount < maximumBooksPerFamily
    }

    private companion object {
        const val DAYS_7 = 7L * 24 * 60 * 60 * 1000
        const val DAYS_30 = 30L * 24 * 60 * 60 * 1000
    }
}
