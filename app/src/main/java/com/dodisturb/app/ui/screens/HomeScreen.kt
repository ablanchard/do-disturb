package com.dodisturb.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PhoneEnabled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dodisturb.app.data.model.AllowedTimeframe
import com.dodisturb.app.ui.AppUiState
import com.dodisturb.app.ui.MainViewModel
import com.dodisturb.app.ui.theme.StatusGreen
import com.dodisturb.app.ui.theme.StatusOrange
import com.dodisturb.app.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: AppUiState,
    viewModel: MainViewModel,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Status Card
        StatusCard(uiState = uiState)

        Spacer(modifier = Modifier.height(16.dp))

        // Blocking Toggle
        BlockingToggleCard(
            isEnabled = uiState.isBlockingEnabled,
            onToggle = { viewModel.setBlockingEnabled(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Calendar Settings
        CalendarSettingsCard(
            calendarName = uiState.calendarName,
            lastSyncTime = uiState.lastSyncTime,
            onCalendarNameChanged = { viewModel.setCalendarName(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Upcoming Timeframes
        UpcomingTimeframesCard(
            timeframes = uiState.upcomingTimeframes,
            activeTimeframe = uiState.activeTimeframe
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(uiState: AppUiState) {
    val (statusColor, statusIcon, statusText, statusDescription) = when {
        !uiState.isBlockingEnabled -> StatusInfo(
            StatusOrange, Icons.Filled.Shield,
            "Blocking Disabled",
            "All incoming calls are allowed."
        )
        uiState.isInAllowedTimeframe -> StatusInfo(
            StatusGreen, Icons.Filled.PhoneEnabled,
            "All Calls Allowed",
            "Active timeframe: ${uiState.activeTimeframe?.title ?: "Unknown"}"
        )
        else -> StatusInfo(
            StatusRed, Icons.Filled.Block,
            "Blocking Active",
            "Only calls from contacts are allowed."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = statusDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class StatusInfo(
    val color: androidx.compose.ui.graphics.Color,
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun BlockingToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Call Blocking",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Block calls from numbers not in your contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = StatusGreen
                )
            )
        }
    }
}

@Composable
private fun CalendarSettingsCard(
    calendarName: String,
    lastSyncTime: Long,
    onCalendarNameChanged: (String) -> Unit
) {
    var editingName by remember { mutableStateOf(calendarName) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Calendar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = editingName,
                onValueChange = {
                    editingName = it
                    onCalendarNameChanged(it)
                },
                label = { Text("Calendar Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Events in this calendar define allowed timeframes")
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (lastSyncTime > 0) {
                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                Text(
                    text = "Last synced: ${dateFormat.format(Date(lastSyncTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Not synced yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusOrange
                )
            }
        }
    }
}

@Composable
private fun UpcomingTimeframesCard(
    timeframes: List<AllowedTimeframe>,
    activeTimeframe: AllowedTimeframe?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Upcoming Timeframes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (timeframes.isEmpty()) {
                Text(
                    text = "No upcoming events found in calendar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                timeframes.take(5).forEachIndexed { index, timeframe ->
                    val isActive = activeTimeframe?.id == timeframe.id
                    TimeframeItem(timeframe = timeframe, isActive = isActive)
                    if (index < timeframes.size - 1 && index < 4) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                if (timeframes.size > 5) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+${timeframes.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeframeItem(timeframe: AllowedTimeframe, isActive: Boolean) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEE, MMM dd", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeframe.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) StatusGreen else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${dayFormat.format(Date(timeframe.startTime))}, " +
                        "${dateFormat.format(Date(timeframe.startTime))} - " +
                        dateFormat.format(Date(timeframe.endTime)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isActive) {
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = StatusGreen
            )
        }
    }
}
