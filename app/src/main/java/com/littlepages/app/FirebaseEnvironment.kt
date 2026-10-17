package com.littlepages.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/** Initializes the selected public Firebase client configuration when one is supplied. */
object FirebaseEnvironment {
    fun initializeIfConfigured(context: Context): FirebaseApp? {
        if (!BuildConfig.FIREBASE_CONFIGURED) return null

        val applicationContext = context.applicationContext
        FirebaseApp.getApps(applicationContext)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let { app ->
                require(app.options.projectId == BuildConfig.FIREBASE_PROJECT_ID) {
                    "The default Firebase app is already initialized for a different project."
                }
                return app
            }

        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID)
            .build()
        return FirebaseApp.initializeApp(applicationContext, options)
    }
}
