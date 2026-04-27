package com.moviecat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.moviecat.app.ui.screens.MovieCatApp
import com.moviecat.app.ui.theme.MovieCatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieCatTheme {
                Surface {
                    MovieCatApp()
                }
            }
        }
    }
}
