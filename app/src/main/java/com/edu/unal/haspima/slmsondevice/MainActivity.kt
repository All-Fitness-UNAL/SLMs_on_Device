package com.edu.unal.haspima.slmsondevice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.unal.haspima.slmsondevice.ui.ChatScreen
import com.edu.unal.haspima.slmsondevice.ui.ChatViewModel
import com.edu.unal.haspima.slmsondevice.ui.ModelsScreen
import com.edu.unal.haspima.slmsondevice.ui.SettingsScreen
import com.edu.unal.haspima.slmsondevice.ui.theme.SLMsOnDeviceTheme

/** Destinos de pantalla de la app. El chat es la experiencia principal. */
enum class Screen {
    CHAT,
    MODELS,
    SETTINGS,
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SLMsOnDeviceTheme {
                App()
            }
        }
    }
}

@Composable
fun App(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var currentScreen by rememberSaveable { mutableStateOf(Screen.CHAT) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            vm.noticeShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (currentScreen) {
            Screen.CHAT -> ChatScreen(
                vm = vm,
                state = state,
                onNavigateToModels = { currentScreen = Screen.MODELS },
                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                modifier = Modifier.fillMaxSize(),
                paddingValues = padding,
            )
            Screen.MODELS -> ModelsScreen(
                vm = vm,
                state = state,
                onBack = { currentScreen = Screen.CHAT },
                modifier = Modifier.fillMaxSize(),
                paddingValues = padding,
            )
            Screen.SETTINGS -> SettingsScreen(
                current = state.settings,
                onSave = { newSettings ->
                    vm.updateSettings(newSettings)
                    currentScreen = Screen.CHAT
                },
                onBack = { currentScreen = Screen.CHAT },
                modifier = Modifier.fillMaxSize(),
                paddingValues = padding,
            )
        }
    }
}
