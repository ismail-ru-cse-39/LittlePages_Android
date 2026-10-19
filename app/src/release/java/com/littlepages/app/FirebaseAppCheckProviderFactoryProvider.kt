package com.littlepages.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object FirebaseAppCheckProviderFactoryProvider {
    fun get(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
