package com.dodisturb.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dodisturb.app.ui.screens.CallLogScreen
import com.dodisturb.app.ui.screens.DebugScreen
import com.dodisturb.app.ui.screens.HomeScreen
import com.dodisturb.app.ui.screens.SetupScreen
import com.dodisturb.app.ui.theme.DoDisturbTheme
import com.dodisturb.app.worker.CalendarSyncWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            DoDisturbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DoDisturbApp(initialTab = when (navigateTo) {
                        "call_log" -> 1
                        "debug" -> 2
                        else -> 0
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when returning from system settings
        // ViewModel will be refreshed via the composable
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoDisturbApp(initialTab: Int = 0) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(context)
    )
    val uiState by viewModel.uiState.collectAsState()

    // Track which screen to show
    var showSetup by remember { mutableStateOf(!uiState.isSetupComplete) }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    // Always show setup if permissions are missing
    if (!uiState.isSetupComplete && !showSetup) {
        showSetup = true
    }

    if (showSetup) {
        SetupScreen(
            uiState = uiState,
            viewModel = viewModel,
            onSetupComplete = {
                showSetup = false
                // Start the calendar sync worker once setup is complete
                CalendarSyncWorker.enqueue(context.applicationContext)
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Do Disturb") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.triggerManualSync() }) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Sync")
                            }
                        }
                        IconButton(onClick = { showSetup = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = {
                            if (uiState.blockedCallCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${uiState.blockedCallCount}") }
                                    }
                                ) {
                                    Icon(Icons.Filled.PhoneMissed, contentDescription = "Call Log")
                                }
                            } else {
                                Icon(Icons.Filled.PhoneMissed, contentDescription = "Call Log")
                            }
                        },
                        label = { Text("Blocked") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(Icons.Filled.Info, contentDescription = "Debug")
                        },
                        label = { Text("Debug") }
                    )
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> HomeScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToSetup = { showSetup = true },
                    modifier = Modifier.padding(padding)
                )
                1 -> CallLogScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
                2 -> DebugScreen(
                    uiState = uiState,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
