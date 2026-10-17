package com.littlepages.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.littlepages.app.ui.LittlePagesApp
import com.littlepages.app.ui.theme.LittlePagesTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseEnvironment.initializeIfConfigured(this)
        setContent {
            LittlePagesTheme {
                LittlePagesApp()
            }
        }
    }
}
