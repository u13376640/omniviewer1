package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.OmniViewerTheme
import com.example.ui.viewmodel.OmniViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: OmniViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure global Coil ImageLoader to automatically decode video frames for thumbnails
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            OmniViewerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}
