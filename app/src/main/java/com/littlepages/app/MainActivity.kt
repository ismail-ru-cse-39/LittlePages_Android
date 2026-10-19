package com.littlepages.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.littlepages.app.ui.LittlePagesApp
import com.littlepages.app.ui.theme.LittlePagesTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseEnvironment.initializeIfConfigured(this)?.let(::initializeAppCheck)
        setContent {
            LittlePagesTheme {
                LittlePagesApp()
            }
        }
    }

    private fun initializeAppCheck(app: FirebaseApp) {
        synchronized(installedAppCheckNames) {
            if (app.name in installedAppCheckNames) return
            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(FirebaseAppCheckProviderFactoryProvider.get())
            installedAppCheckNames += app.name
        }
    }

    private companion object {
        val installedAppCheckNames = mutableSetOf<String>()
    }
}
