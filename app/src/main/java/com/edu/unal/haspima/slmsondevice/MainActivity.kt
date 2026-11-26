package com.edu.unal.haspima.slmsondevice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edu.unal.haspima.slmsondevice.ui.ChatScreen
import com.edu.unal.haspima.slmsondevice.ui.ChatViewModel
import com.edu.unal.haspima.slmsondevice.ui.ModelsScreen
import com.edu.unal.haspima.slmsondevice.ui.SettingsScreen
import com.edu.unal.haspima.slmsondevice.ui.theme.SLMsOnDeviceTheme

/** Pestañas de la app (navegación simple basada en estado, sin librería de navegación). */
enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Filled.Email),
    MODELS("Modelos", Icons.Filled.Build),
    SETTINGS("Ajustes", Icons.Filled.Settings),
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
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }
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
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding).consumeWindowInsets(padding)
        when (tab) {
            Tab.CHAT -> ChatScreen(vm, state, onGoToModels = { tab = Tab.MODELS }, modifier = mod)
            Tab.MODELS -> ModelsScreen(vm, state, modifier = mod)
            Tab.SETTINGS -> SettingsScreen(state.settings, onSave = vm::updateSettings, modifier = mod)
        }
    }
}
