# Architecture

**Analysis Date:** 2026-02-26

## Pattern Overview

**Overall:** MVVM (Model-View-ViewModel) with layered data access and system service integration

**Key Characteristics:**
- Single-Activity Compose-based UI with tab navigation
- Repository pattern for data access (Room database and SharedPreferences)
- Worker pattern for background tasks (WorkManager)
- System service integration (CallScreeningService, NotificationManager)
- Reactive state management via StateFlow and coroutines

## Layers

**Presentation (UI Layer):**
- Purpose: Display UI and handle user interactions
- Location: `app/src/main/java/com/dodisturb/app/ui/`
- Contains: MainActivity, Composable screens, MainViewModel, theme configuration
- Depends on: MainViewModel, data models
- Used by: Android system (activity lifecycle)

**ViewModel Layer:**
- Purpose: Manage UI state and business logic for screens
- Location: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt`
- Contains: AppUiState data class, MainViewModel with StateFlow
- Depends on: PreferencesManager, TimeframeRepository, DndManager, AppDatabase, AnalyticsHelper
- Used by: Composable functions in UI layer

**Data Access Layer (Repository):**
- Purpose: Abstract data sources and provide clean interfaces
- Location: `app/src/main/java/com/dodisturb/app/data/repository/`
- Contains: TimeframeRepository (Room access), PreferencesManager (SharedPreferences)
- Depends on: TimeframeDao, BlockedCallDao, Android SharedPreferences, Android ContentResolver
- Used by: ViewModel, Service, Worker

**Database Layer:**
- Purpose: Persist and query local data via Room ORM
- Location: `app/src/main/java/com/dodisturb/app/data/db/`
- Contains: AppDatabase (Room database), TimeframeDao, BlockedCallDao
- Depends on: Room library, data models
- Used by: Repository layer

**Data Models:**
- Purpose: Define entities and data transfer objects
- Location: `app/src/main/java/com/dodisturb/app/data/model/`
- Contains: AllowedTimeframe (Room entity), BlockedCallInfo (Room entity)
- Depends on: Room annotations
- Used by: All layers

**System Service Layer:**
- Purpose: Integrate with Android system services for call screening and DND control
- Location: `app/src/main/java/com/dodisturb/app/service/`
- Contains: DoDisturbCallScreeningService (screens incoming calls)
- Depends on: Android Telecom API, Room database, PreferencesManager, ContactsHelper
- Used by: Android system (telecom framework)

**Background Work Layer:**
- Purpose: Periodically sync calendar and update DND state
- Location: `app/src/main/java/com/dodisturb/app/worker/`
- Contains: CalendarSyncWorker (WorkManager task)
- Depends on: WorkManager, Room database, Android CalendarContract, PreferencesManager, DndManager
- Used by: Android system (WorkManager)

**Utility Layer:**
- Purpose: Provide reusable helpers for specific concerns
- Location: `app/src/main/java/com/dodisturb/app/util/`
- Contains: DndManager (DND control), ContactsHelper (contacts lookup), NotificationHelper (notifications), BootReceiver (boot event), AnalyticsHelper (event logging)
- Depends on: Android system APIs, Firebase Crashlytics
- Used by: ViewModel, Service, Worker

**Application Entry Point:**
- Purpose: Initialize app, logging, and database
- Location: `app/src/main/java/com/dodisturb/app/DoDisturbApp.kt`
- Contains: Custom Application class with Timber initialization
- Depends on: Room database, NotificationHelper
- Used by: Android system

## Data Flow

**Call Screening Flow:**

1. Android system intercepts incoming call
2. `DoDisturbCallScreeningService.onScreenCall()` triggered with call details
3. Service extracts phone number from call details
4. Service checks in order:
   - Is blocking enabled in PreferencesManager?
   - Is current time in allowed timeframe (via TimeframeRepository)?
   - Is phone number in contacts (via ContactsHelper)?
5. If all checks pass: call is allowed, respond with allowCall()
6. If any check fails: call is blocked
7. If blocked: BlockedCallInfo persisted to database, notification posted via NotificationHelper
8. User can view blocked calls in CallLogScreen (from MainViewModel.blockedCalls)

**Calendar Sync Flow:**

1. CalendarSyncWorker executes (periodic every 15 minutes or manual trigger)
2. Worker queries all device calendars via CalendarContract.Calendars
3. Worker searches for target calendar by name
4. Worker queries events from target calendar for next 30 days via CalendarContract.Instances
5. Worker converts events to AllowedTimeframe objects
6. Worker calls TimeframeRepository.replaceAllTimeframes() to update database
7. Worker calls DndManager.updateDndState() to manage DND based on current timeframe
8. Worker updates PreferencesManager with sync timestamp and calendar list
9. MainViewModel observes TimeframeRepository flow and updates UI state

**DND Management Flow:**

1. CalendarSyncWorker (after syncing calendars) calls DndManager.updateDndState(isInTimeframe)
2. If isInTimeframe is true: DndManager saves current interruption filter, then sets to ALL
3. If isInTimeframe is false: DndManager restores previous interruption filter
4. PreferencesManager tracks previous filter and managed state for recovery

**State Management:**

- UI state is managed in MainViewModel._uiState (MutableStateFlow<AppUiState>)
- AppUiState is immutable data class with copy() for updates
- Flows observed via collectAsState() in Composables
- Database changes auto-propagate via DAO Flow emissions
- Background work updates via PreferencesManager (sync time, errors, calendars)

## Key Abstractions

**TimeframeRepository:**
- Purpose: Encapsulates all timeframe queries
- Examples: `app/src/main/java/com/dodisturb/app/data/repository/TimeframeRepository.kt`
- Pattern: Repository pattern over DAO; provides sync and async variants
- Key methods: getUpcomingTimeframes() (Flow), isInAllowedTimeframeSync(), isInAllowedTimeframe(), replaceAllTimeframes()

**PreferencesManager:**
- Purpose: Centralized SharedPreferences access with type-safe properties
- Examples: `app/src/main/java/com/dodisturb/app/data/repository/PreferencesManager.kt`
- Pattern: Property delegation with getter/setter pattern
- Key properties: calendarName, isBlockingEnabled, lastSyncTimestamp, isDndManagedByApp

**DndManager:**
- Purpose: Encapsulates Do Not Disturb state management logic
- Examples: `app/src/main/java/com/dodisturb/app/util/DndManager.kt`
- Pattern: Facade over NotificationManager with state recovery
- Key methods: disableDnd(), restoreDnd(), updateDndState()

**AppUiState:**
- Purpose: Single source of truth for all UI state
- Examples: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt`
- Pattern: Immutable data class with nested groups (setup state, home state, blocked calls, debug)
- Key fields: isSetupComplete, isBlockingEnabled, activeTimeframe, upcomingTimeframes, blockedCalls

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/dodisturb/app/ui/MainActivity.kt`
- Triggers: User launches app (MAIN intent filter in manifest)
- Responsibilities: Create Compose hierarchy, initialize MainViewModel, handle deep links via intent extras

**DoDisturbCallScreeningService:**
- Location: `app/src/main/java/com/dodisturb/app/service/DoDisturbCallScreeningService.kt`
- Triggers: Android system routes incoming call to screening service
- Responsibilities: Screen call based on contacts, timeframes, and settings; persist blocked calls; post notifications

**CalendarSyncWorker:**
- Location: `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt`
- Triggers: Periodic schedule (every 15 minutes) or manual sync request via syncNow()
- Responsibilities: Fetch calendar events, update database, manage DND state, handle errors

**BootReceiver:**
- Location: `app/src/main/java/com/dodisturb/app/util/BootReceiver.kt`
- Triggers: Device boot completion (BOOT_COMPLETED broadcast)
- Responsibilities: Re-enqueue CalendarSyncWorker for persistence across device restarts

**DoDisturbApp:**
- Location: `app/src/main/java/com/dodisturb/app/DoDisturbApp.kt`
- Triggers: Process startup (Android Application onCreate)
- Responsibilities: Initialize database singleton, create notification channels, initialize logging

## Error Handling

**Strategy:** Graceful degradation with user notifications and logging

**Patterns:**

- **Call Screening:** Wrapped in try-catch; blocks call and logs error if database insert fails
- **Calendar Sync:** Worker returns Result.retry() on exception; Result.failure() on missing calendar
- **Permission Checks:** ViewModel checks permissions on refresh; screens show setup prompts if missing
- **DND Control:** Checks hasNotificationPolicyAccess() before attempting changes; logs warnings if denied
- **Logging:** All paths instrumented with Timber; high-priority events sent to Firebase Crashlytics
- **Notifications:** Error messages persisted to PreferencesManager for display in UI (sync errors, calendar not found)

## Cross-Cutting Concerns

**Logging:**
- Framework: Timber
- Strategy: Timber.DebugTree() in debug builds, Timber.Tree customization in release via DoDisturbApp.CrashlyticsTree
- All logs forwarded to Firebase Crashlytics as breadcrumbs
- PII redacted before logging (calendar names, etc.)

**Validation:**
- Phone numbers extracted and validated in service layer
- Calendar names matched case-insensitive
- Timeframes validated via epoch millisecond timestamps
- Permissions checked in ViewModel and service layers

**Authentication:**
- No app-level authentication (uses device calendar/contacts)
- Calendar access via Android CalendarContract permissions
- Contacts access via READ_CONTACTS permission
- DND control via ACCESS_NOTIFICATION_POLICY permission

---

*Architecture analysis: 2026-02-26*
