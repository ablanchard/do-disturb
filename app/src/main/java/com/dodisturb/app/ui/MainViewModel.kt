package com.dodisturb.app.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResult
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
import com.dodisturb.app.util.DndManager
import com.dodisturb.app.worker.CalendarSyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUiState(
    // Setup state
    val hasContactsPermission: Boolean = false,
    val hasDndPermission: Boolean = false,
    val hasCallScreeningRole: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isGoogleSignedIn: Boolean = false,
    val googleAccountEmail: String? = null,
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

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val prefs = PreferencesManager(context)
    private val dndManager = DndManager(context)
    private val db = AppDatabase.getInstance(context)
    private val repository = TimeframeRepository(db.timeframeDao())
    private val blockedCallDao = db.blockedCallDao()

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

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

        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        val isSignedIn = googleAccount != null

        val isSetup = hasContacts && hasDnd && hasRole && isSignedIn

        _uiState.value = _uiState.value.copy(
            hasContactsPermission = hasContacts,
            hasDndPermission = hasDnd,
            hasCallScreeningRole = hasRole,
            hasNotificationPermission = hasNotification,
            isGoogleSignedIn = isSignedIn,
            googleAccountEmail = googleAccount?.email ?: prefs.googleAccountEmail,
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

    fun handleGoogleSignInResult(result: ActivityResult) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            prefs.googleAccountEmail = account.email
            Log.d(TAG, "Google sign-in successful: ${account.email}")

            // Start the sync worker after sign-in
            CalendarSyncWorker.enqueue(context)

            refreshState()
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
        }
    }

    fun setCalendarName(name: String) {
        prefs.calendarName = name
        _uiState.value = _uiState.value.copy(calendarName = name)
    }

    fun setBlockingEnabled(enabled: Boolean) {
        prefs.isBlockingEnabled = enabled
        _uiState.value = _uiState.value.copy(isBlockingEnabled = enabled)
    }

    fun triggerManualSync() {
        Log.d(TAG, "Manual sync triggered by user")
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
            Log.e(TAG, "Error creating role request intent", e)
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
