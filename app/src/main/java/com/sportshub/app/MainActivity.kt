package com.sportshub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sportshub.app.ui.SportsHubRoot
import com.sportshub.app.ui.theme.SportsHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SportsHubApp).container
        setContent {
            SportsHubTheme {
                SportsHubRoot(container)
            }
        }
    }
}
