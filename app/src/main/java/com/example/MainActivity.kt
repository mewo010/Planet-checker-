package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.CinemaDashboardScreen
import com.example.ui.theme.PlanetCheckerTheme
import com.example.ui.viewmodel.CinemaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CinemaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            PlanetCheckerTheme {
                val uiState by viewModel.uiState.collectAsState()
                CinemaDashboardScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
            }
        }
    }
}
