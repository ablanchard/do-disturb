package com.dodisturb.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.data.model.AllowedTimeframe
import com.dodisturb.app.data.model.BlockedCallInfo
import com.dodisturb.app.data.repository.CalendarInfo
import com.dodisturb.app.data.repository.PreferencesManager
import com.dodisturb.app.data.repository.TimeframeRepository
import com.dodisturb.app.util.AnalyticsHelper
import com.dodisturb.app.util.DndManager
import com.dodisturb.app.worker.CalendarSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class AppUiState(
    // Setup state
    val hasContactsPermission: Boolean = false,
    val hasDndPermission: Boolean = false,
    val hasCallScreeningRole: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val isSetupComplete: Boolean = false,

    // Home state
    val isBlockingEnabled: Boolean = true,
    val calendarName: String = PreferencesManager.DEFAULT_CALENDAR_NAME,
    val isInAllowedTimeframe: Boolean = false,
    val activeTimeframe: AllowedTimeframe? = null,
    val nextTimeframe: AllowedTimeframe? = null,
    val upcomingTimeframes: List<AllowedTimeframe> = emptyList(),
    val lastSyncTime: Long = 0,
    val isSyncing: Boolean = false,
    val syncError: String? = null,

    // Blocked calls state
    val blockedCalls: List<BlockedCallInfo> = emptyList(),
    val blockedCallCount: Int = 0,

    // Debug state
    val availableCalendars: List<CalendarInfo> = emptyList()
)

class MainViewModel(private val context: Context) : ViewModel() {

    private val prefs = PreferencesManager(context)
    private val dndManager = DndManager(context)
    private val db = AppDatabase.getInstance(context)
    private val repository = TimeframeRepository(db.timeframeDao())
    private val blockedCallDao = db.blockedCallDao()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshState()
        observeTimeframes()
        observeBlockedCalls()
    }

    fun refreshState() {
        val hasContacts = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val hasDnd = dndManager.hasNotificationPolicyAccess()

        val hasRole = try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (e: Exception) {
            false
        }

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed before Android 13
        }

        val hasCalendar = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val isSetup = hasContacts && hasDnd && hasRole && hasCalendar

        _uiState.value = _uiState.value.copy(
            hasContactsPermission = hasContacts,
            hasDndPermission = hasDnd,
            hasCallScreeningRole = hasRole,
            hasNotificationPermission = hasNotification,
            hasCalendarPermission = hasCalendar,
            isSetupComplete = isSetup,
            isBlockingEnabled = prefs.isBlockingEnabled,
            calendarName = prefs.calendarName,
            lastSyncTime = prefs.lastSyncTimestamp,
            syncError = prefs.lastSyncError,
            availableCalendars = prefs.getAvailableCalendars()
        )
    }

    private fun observeTimeframes() {
        viewModelScope.launch {
            repository.getUpcomingTimeframes().collect { timeframes ->
                val active = timeframes.firstOrNull {
                    val now = System.currentTimeMillis()
                    it.startTime <= now && it.endTime > now
                }
                val next = if (active != null) {
                    timeframes.firstOrNull { it.startTime > System.currentTimeMillis() }
                } else {
                    timeframes.firstOrNull()
                }

                _uiState.value = _uiState.value.copy(
                    isInAllowedTimeframe = active != null,
                    activeTimeframe = active,
                    nextTimeframe = next,
                    upcomingTimeframes = timeframes
                )
            }
        }
    }

    private fun observeBlockedCalls() {
        viewModelScope.launch {
            blockedCallDao.getAllBlockedCalls().collect { calls ->
                _uiState.value = _uiState.value.copy(
                    blockedCalls = calls,
                    blockedCallCount = calls.size
                )
            }
        }
    }

    fun clearBlockedCalls() {
        viewModelScope.launch {
            blockedCallDao.deleteAll()
        }
    }

    fun setCalendarName(name: String) {
        prefs.calendarName = name
        _uiState.value = _uiState.value.copy(calendarName = name)
    }

    fun setBlockingEnabled(enabled: Boolean) {
        prefs.isBlockingEnabled = enabled
        _uiState.value = _uiState.value.copy(isBlockingEnabled = enabled)
        AnalyticsHelper.logBlockingToggled(enabled)
    }

    fun triggerManualSync() {
        Timber.d("Manual sync triggered by user")
        AnalyticsHelper.logManualSyncTriggered()
        _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
        CalendarSyncWorker.syncNow(context)

        // Refresh state after a short delay to show updated sync time
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                lastSyncTime = prefs.lastSyncTimestamp,
                syncError = prefs.lastSyncError,
                availableCalendars = prefs.getAvailableCalendars()
            )
        }
    }

    fun getDndAccessIntent(): Intent = dndManager.getNotificationPolicyAccessIntent()

    fun getCallScreeningRoleIntent(): Intent? {
        return try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        } catch (e: Exception) {
            Timber.e(e, "Error creating role request intent")
            null
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(context.applicationContext) as T
        }
    }
}
