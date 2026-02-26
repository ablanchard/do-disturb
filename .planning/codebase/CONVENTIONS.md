# Coding Conventions

**Analysis Date:** 2026-02-26

## Naming Patterns

**Files:**
- PascalCase for Kotlin classes: `MainViewModel.kt`, `DndManager.kt`, `PreferencesManager.kt`
- camelCase for utility/helper files: `ContactsHelper.kt`, `AnalyticsHelper.kt`, `NotificationHelper.kt`
- Screen files in PascalCase with "Screen" suffix: `HomeScreen.kt`, `SetupScreen.kt`, `CallLogScreen.kt`, `DebugScreen.kt`
- Service files with "Service" suffix: `DoDisturbCallScreeningService.kt`
- Worker files with "Worker" suffix: `CalendarSyncWorker.kt`
- DAO interfaces in PascalCase with "Dao" suffix: `TimeframeDao.kt`, `BlockedCallDao.kt`
- Database class: `AppDatabase.kt`
- Model files in PascalCase: `AllowedTimeframe.kt`, `BlockedCallInfo.kt`

**Functions:**
- camelCase for all function names: `refreshState()`, `disableDnd()`, `queryAllCalendars()`, `logCallBlocked()`
- Utility functions as object members: `ContactsHelper.isNumberInContacts()`, `AnalyticsHelper.logCallBlocked()`
- Private helper functions with descriptive names: `queryAllCalendars()`, `queryEvents()`, `allowCall()`, `blockCall()`

**Variables:**
- camelCase for all variables and properties: `uiState`, `phoneNumber`, `blockedCall`, `calendarName`, `lastSyncTime`
- Backing fields with underscore prefix: `_uiState: MutableStateFlow`, exposed as `uiState: StateFlow`
- CONSTANT_CASE for companion object constants: `PREFS_NAME`, `KEY_CALENDAR_NAME`, `WORK_NAME`, `DEFAULT_CALENDAR_NAME`

**Types:**
- PascalCase for all data classes and interfaces: `AppUiState`, `AllowedTimeframe`, `BlockedCallInfo`, `CalendarInfo`
- DAO interfaces inherit from Room patterns: `@Dao interface TimeframeDao`

## Code Style

**Formatting:**
- No explicit linter/formatter configured (likely using default Android Studio settings)
- Imports are organized: Android framework → androidx → third-party (Firebase, Timber) → local packages
- Line continuations follow standard Kotlin style with operators at line starts
- Spacing: 4-space indentation throughout

**Linting:**
- No detectKt, ktlint, or ESLint configuration found in project
- Code appears to follow Kotlin standard library conventions and Android best practices
- No explicit rule enforcement configured

## Import Organization

**Order:**
1. `package` declaration
2. Android framework imports (`android.*`)
3. AndroidX imports (`androidx.*`)
4. Third-party library imports (Firebase, Timber, etc.)
5. Java standard library imports (`java.*`)
6. Local package imports (`com.dodisturb.app.*`)

**Path Aliases:**
- No path aliases configured
- Imports use full package paths: `com.dodisturb.app.data.model.*`, `com.dodisturb.app.util.*`

**Example (from `CalendarSyncWorker.kt`):**
```kotlin
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
// ... more androidx imports
import com.dodisturb.app.data.db.AppDatabase
import com.dodisturb.app.data.model.AllowedTimeframe
// ... local imports
import timber.log.Timber
import java.util.concurrent.TimeUnit
```

## Error Handling

**Patterns:**
- try-catch blocks with specific exception handling (not catching all exceptions generically)
- Fail-open pattern for external data access: `ContactsHelper.isNumberInContacts()` returns `true` on lookup error to allow calls
- Database operations use try-catch with error logging: `DoDisturbCallScreeningService` wraps blocked call persistence
- Async worker operations use `Result.retry()` for transient failures, `Result.failure()` for permanent failures
- Null coalescing with elvis operator: `handle?.schemeSpecificPart ?: ""`

**Example (from `ContactsHelper.kt`):**
```kotlin
return try {
    val lookupUri: Uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber)
    )
    val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
    cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
    val found = cursor != null && cursor.count > 0
    found
} catch (e: Exception) {
    Timber.e(e, "Error looking up phone number in contacts")
    // On error, fail open (allow the call) rather than blocking a potentially valid contact
    true
} finally {
    cursor?.close()
}
```

## Logging

**Framework:** Timber (Jake Wharton's logging library)

**Patterns:**
- `Timber.d()` for debug messages (development information)
- `Timber.i()` for info messages (important operational events like sync start/completion)
- `Timber.w()` for warnings (expected error conditions, e.g., calendar not found)
- `Timber.e(e, "message")` for exceptions with stack traces

**Log redaction:**
- PII (phone numbers, contact names, calendar IDs) is NOT logged
- Calendar names are redacted with descriptive messages like "Looking for target calendar" instead of logging the actual name
- Phone numbers in blocked calls are logged as generic descriptions

**Example (from `CalendarSyncWorker.kt`):**
```kotlin
Timber.i("Looking for target calendar among %d calendars", allCalendars.size)
Timber.w("Target calendar NOT FOUND among %d calendars", allCalendars.size)
Timber.i("Fetched %d events / timeframes", timeframes.size)
```

**Example (from `DoDisturbCallScreeningService.kt`):**
```kotlin
Timber.d("Screening incoming call")
Timber.d("Blocking call (not in contacts, not in allowed timeframe)")
```

## Comments

**When to Comment:**
- Class-level documentation for public classes with detailed purpose and behavior
- Method-level documentation for public/protected methods explaining what they do and why
- Inline comments for non-obvious logic or business rules
- Comments explaining Android system constraints (e.g., "Needed for CallScreeningService (runs on binder thread)")

**JSDoc/KDoc:**
- KDoc comments (Kotlin documentation) used for classes and public functions
- Format: `/** ... */` for multi-line documentation
- Parameter and return type documentation where useful

**Example (from `AppDatabase.kt`):**
```kotlin
/**
 * Application class for Do Disturb.
 * Initializes the database, notification channels, and logging/crash reporting.
 */
```

**Example (from `CalendarSyncWorker.kt`):**
```kotlin
/**
 * Periodic worker that syncs events from the configured calendar
 * using Android's CalendarContract Content Provider and updates
 * the allowed timeframes in the local database.
 * Also manages DND state based on whether we're in an allowed timeframe.
 */
```

## Function Design

**Size:**
- Functions are generally 10-50 lines
- Worker's `doWork()` is ~78 lines but represents a complete workflow
- Preference property getters/setters are 1-3 lines using Kotlin property syntax

**Parameters:**
- Constructor parameters injected directly: `MainViewModel(private val context: Context)`
- Single responsibility per function: helper functions like `queryAllCalendars()` and `queryEvents()` separated
- No excessive parameter passing; use data classes when multiple values needed: `AppUiState` bundles UI state

**Return Values:**
- Explicit return types for all functions
- Flow<T> for observable streams: `fun getUpcomingTimeframes(): Flow<List<AllowedTimeframe>>`
- Sealed result types for operations: `Result.success()`, `Result.failure()`, `Result.retry()`
- Null for optional values: `getAllowedTimeframes(): AllowedTimeframe?`

**Example (from `MainViewModel.kt`):**
```kotlin
fun setBlockingEnabled(enabled: Boolean) {
    prefs.isBlockingEnabled = enabled
    _uiState.value = _uiState.value.copy(isBlockingEnabled = enabled)
    AnalyticsHelper.logBlockingToggled(enabled)
}
```

## Module Design

**Exports:**
- DAO interfaces expose specific query methods, not generic CRUD
- Repositories wrap database access: `TimeframeRepository` provides business logic around timeframe queries
- Helper objects (ContactsHelper, AnalyticsHelper) expose static/singleton methods
- ViewModels expose `StateFlow<AppUiState>` for UI binding

**Barrel Files:**
- No barrel files (index.kt) used in this codebase
- Imports use direct file references: `import com.dodisturb.app.data.db.AppDatabase`

**Example (from `TimeframeRepository.kt` pattern):**
```kotlin
class TimeframeRepository(private val timeframeDao: TimeframeDao) {
    fun getUpcomingTimeframes(): Flow<List<AllowedTimeframe>> {
        // Business logic wrapping DAO calls
    }
}
```

## Architecture Patterns

**State Management:**
- ViewModel with `MutableStateFlow` backing field `_uiState` and public read-only `StateFlow<AppUiState>`
- State updates via `.copy()` method on data classes for immutability
- `StateFlow.asStateFlow()` to expose read-only view

**Dependency Injection:**
- Manual injection through constructors (no DI framework like Hilt)
- Context passed explicitly through parameters
- Singleton pattern with lazy initialization: `Firebase.analytics` via `by lazy`
- Database singleton pattern with synchronized block: `AppDatabase.getInstance(context)`

**Async Operations:**
- `CoroutineWorker` for background tasks (CalendarSyncWorker)
- `viewModelScope.launch` for scoped coroutines in ViewModel
- `Flow.collect()` to observe state changes

---

*Convention analysis: 2026-02-26*
