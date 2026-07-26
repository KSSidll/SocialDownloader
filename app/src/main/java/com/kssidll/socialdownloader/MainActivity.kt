package com.kssidll.socialdownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.kssidll.socialdownloader.ui.screen.DownloaderScreen
import com.kssidll.socialdownloader.ui.theme.Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DownloaderScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
