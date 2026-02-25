package com.dodisturb.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodisturb.app.ui.AppUiState
import com.dodisturb.app.ui.MainViewModel
import com.dodisturb.app.ui.theme.StatusGreen

@Composable
fun SetupScreen(
    uiState: AppUiState,
    viewModel: MainViewModel,
    onSetupComplete: () -> Unit
) {
    // Permission launchers
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshState()
    }

    val dndSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Do Disturb",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Block unknown callers. Allow calls during calendar events.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Setup Required",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1: Contacts Permission
        SetupStepCard(
            stepNumber = 1,
            title = "Contacts Access",
            description = "Read your contacts to identify known callers.",
            isCompleted = uiState.hasContactsPermission,
            onAction = {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 2: DND Permission
        SetupStepCard(
            stepNumber = 2,
            title = "Do Not Disturb Control",
            description = "Manage DND mode during allowed timeframes.",
            isCompleted = uiState.hasDndPermission,
            onAction = {
                dndSettingsLauncher.launch(viewModel.getDndAccessIntent())
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 3: Call Screening Role
        SetupStepCard(
            stepNumber = 3,
            title = "Call Screening",
            description = "Set as default call screening app to filter calls.",
            isCompleted = uiState.hasCallScreeningRole,
            onAction = {
                viewModel.getCallScreeningRoleIntent()?.let { intent ->
                    callScreeningRoleLauncher.launch(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 4: Google Sign-In
        SetupStepCard(
            stepNumber = 4,
            title = "Google Calendar",
            description = if (uiState.isGoogleSignedIn) {
                "Signed in as ${uiState.googleAccountEmail}"
            } else {
                "Sign in to sync your calendar events."
            },
            isCompleted = uiState.isGoogleSignedIn,
            onAction = {
                googleSignInLauncher.launch(viewModel.googleSignInClient.signInIntent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 5: Notifications (Android 13+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SetupStepCard(
                stepNumber = 5,
                title = "Notifications",
                description = "Get notified when a call is blocked.",
                isCompleted = uiState.hasNotificationPermission,
                onAction = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Continue button
        if (uiState.isSetupComplete) {
            Button(
                onClick = onSetupComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue to App")
            }
        } else {
            Text(
                text = "Complete all steps above to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SetupStepCard(
    stepNumber: Int,
    title: String,
    description: String,
    isCompleted: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = if (isCompleted) "Completed" else "Pending",
                tint = if (isCompleted) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step $stepNumber: $title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isCompleted) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onAction) {
                    Text("Grant")
                }
            }
        }
    }
}
