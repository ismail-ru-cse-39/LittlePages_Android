package com.littlepages.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object FirebaseAppCheckProviderFactoryProvider {
    fun get(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
