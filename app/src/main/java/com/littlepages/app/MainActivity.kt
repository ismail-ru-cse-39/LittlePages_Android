package com.littlepages.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.littlepages.app.ui.LittlePagesApp
import com.littlepages.app.ui.theme.LittlePagesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LittlePagesTheme {
                LittlePagesApp()
            }
        }
    }
}
