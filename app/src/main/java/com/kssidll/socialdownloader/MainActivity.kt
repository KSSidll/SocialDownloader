package com.kssidll.socialdownloader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kssidll.socialdownloader.media.Media
import com.kssidll.socialdownloader.ui.theme.Theme
import com.kssidll.socialdownloader.util.SocialMediaUrl
import com.kssidll.socialdownloader.util.parseSocialMediaUrl
import com.kssidll.socialdownloader.xhs.downloadXhs
import com.kssidll.socialdownloader.xhs.filterXhs

private const val TAG = "SocialDownloader"

const val REDNOTE_URL = """
example http://xhslink.cn/o/example
Copy and open rednote to view the note
"""

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScreenContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ScreenContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier)

    LaunchedEffect(Unit) {
        Log.d(TAG, "ScreenContent: starting parse of REDNOTE_URL")
        val parsed = parseSocialMediaUrl(REDNOTE_URL)
        Log.d(TAG, "ScreenContent: detected $parsed")

        if (parsed == null) {
            Log.d(TAG, "ScreenContent: no URL found in text, stopping")
            return@LaunchedEffect
        }

        Log.d(TAG, "ScreenContent: dispatching to downloader for $parsed")
        val media: Media? = when (parsed) {
            is SocialMediaUrl.Xhs -> filterXhs(downloadXhs(parsed.url))
            is SocialMediaUrl.Instagram -> TODO("Instagram downloading not supported yet")
            is SocialMediaUrl.Threads -> TODO("Threads downloading not supported yet")
            is SocialMediaUrl.Unrecognized -> TODO("Unrecognized link, nothing to download")
        }

        if (media == null) {
            Log.d(TAG, "ScreenContent: no media found")
        } else {
            Log.d(TAG, "ScreenContent: got media $media")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Theme {
        ScreenContent()
    }
}
