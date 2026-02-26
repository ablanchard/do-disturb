# External Integrations

**Analysis Date:** 2026-02-26

## APIs & External Services

**Google Calendar:**
- Service: Local device calendar access via Android CalendarContract Content Provider
- What it's used for: Reading calendar events to determine allowed call-screening timeframes
- SDK/Client: CalendarContract (Android platform API)
- Access: Via ContentResolver with READ_CALENDAR permission
- Data flow: `CalendarSyncWorker` queries `CalendarContract.Calendars` and `CalendarContract.Instances` Content URIs
- Sync frequency: Every 15 minutes via periodic `WorkManager` job, plus manual sync on-demand

**Google Contacts:**
- Service: Local device contacts access via Android Contacts Content Provider
- What it's used for: Checking if incoming call source is in user's contact list
- SDK/Client: ContactsContract (Android platform API)
- Access: Via ContentResolver with READ_CONTACTS permission
- Implementation: `ContactsHelper` utility class

## Data Storage

**Databases:**
- SQLite (Room ORM)
  - Database name: `dodisturb_db`
  - Location: Device-local encrypted storage
  - Schema version: 2
  - Migration policy: Fallback to destructive migration
  - Tables:
    - `AllowedTimeframe` - Calendar events converted to blocked-call timeframes (title, start/end times, calendar event ID)
    - `BlockedCallInfo` - Log of blocked calls (caller, timestamp, reason)
  - Access: Via Room DAOs (`TimeframeDao`, `BlockedCallDao`)
  - Thread access: Main thread queries allowed (used by `CallScreeningService` on binder thread)

**Preferences Storage:**
- SharedPreferences (Android platform)
  - Name: `dodisturb_prefs`
  - Managed by: `PreferencesManager` class at `app/src/main/java/com/dodisturb/app/data/repository/PreferencesManager.kt`
  - Stored data:
    - `calendar_name` - Target calendar display name
    - `calendar_id` - Resolved calendar ID
    - `blocking_enabled` - Whether call screening is active
    - `last_sync` - Timestamp of last calendar sync
    - `prev_interruption_filter` - Previous Do Not Disturb state for restoration
    - `dnd_managed` - Whether app is managing DND state
    - `last_sync_error` - Most recent sync error message
    - `available_calendars` - JSON array of available calendars from last sync

**File Storage:**
- Local filesystem only - No cloud storage integration

**Caching:**
- In-memory Room database caching
- SharedPreferences for preference caching

## Authentication & Identity

**Auth Provider:**
- None for app-specific authentication
- Uses device-level Google Account for calendar access (inherited from Android system)
- No explicit API key or OAuth token management in app code

## Monitoring & Observability

**Error Tracking:**
- Firebase Crashlytics
  - SDK: `com.google.firebase:firebase-crashlytics-ktx`
  - Implementation: `CrashlyticsTree` custom Timber logging tree in `DoDisturbApp` class
  - Captures: All log messages as breadcrumbs, exceptions as non-fatal events
  - Configuration: Automatically initialized via Firebase BOM (version 32.7.1)
  - Non-fatal error recording: Enabled for WARN and ERROR level logs with exceptions
  - Redaction: Calendar names and PII redacted from logs before sending to Crashlytics

**Logs:**
- Timber logging framework (version 5.0.1)
  - `DoDisturbApp` plants two trees:
    1. `CrashlyticsTree` - Sends logs to Firebase Crashlytics as breadcrumbs
    2. `Timber.DebugTree()` - Logs to Android logcat in debug builds only
  - All log statements tagged with class name, PII-safe messages
  - Log locations:
    - `CalendarSyncWorker` - Sync operation details
    - `DndManager` - Do Not Disturb state changes
    - `DoDisturbCallScreeningService` - Call screening decisions
    - `AnalyticsHelper` - Event tracking details

## Analytics

**Firebase Analytics:**
- SDK: `com.google.firebase:firebase-analytics-ktx`
- Implementation: `AnalyticsHelper` centralized event logger at `app/src/main/java/com/dodisturb/app/util/AnalyticsHelper.kt`
- Events tracked:
  - `call_allowed` - When a call passes screening (includes reason: "contact", "timeframe", "manual")
  - `call_blocked` - When a call is blocked
  - `sync_started` - Calendar sync begins
  - `sync_completed` - Calendar sync completes (includes timeframe count, in-timeframe boolean)
  - `sync_failed` - Calendar sync fails (includes error type)
  - `calendar_not_found` - Target calendar missing from device
  - `dnd_disabled` - Do Not Disturb disabled
  - `dnd_restored` - Do Not Disturb restored to previous state
  - `blocking_toggled` - User enables/disables blocking (includes enabled boolean)
  - `manual_sync_triggered` - User manually triggers sync
- Data policy: No PII (phone numbers, calendar IDs, calendar names) included in event parameters

## CI/CD & Deployment

**Hosting:**
- Google Play Store (intended deployment target)
- Local APK builds for development/testing

**CI Pipeline:**
- None detected - Manual build and deployment assumed

**Build Output:**
- APK generation via Gradle
- Release builds: ProGuard rules applied, minification disabled, shrinking and obfuscation enabled by default

## Environment Configuration

**Required env vars:**
- None explicit in code - Firebase configuration embedded in `google-services.json`

**Secrets location:**
- `app/google-services.json` - Firebase configuration file (present in repo)
- Contains: Firebase project ID, API keys, app ID (standard Firebase configuration)

## System Services & Permissions

**Android System Services:**
- CalendarContract - Calendar event querying (READ_CALENDAR permission)
- ContactsContract - Contact lookup (READ_CONTACTS permission)
- NotificationManager - Blocked call notifications and sync error alerts (POST_NOTIFICATIONS permission)
- TelecomManager/CallScreeningService - Call screening API (BIND_SCREENING_SERVICE permission)
- NotificationPolicyAccessService - Do Not Disturb control (ACCESS_NOTIFICATION_POLICY permission)
- BroadcastReceiver - Boot completion handling (RECEIVE_BOOT_COMPLETED permission)

**Permissions Declared:**
- `android.permission.INTERNET` - Firebase connectivity
- `android.permission.READ_CALENDAR` - Calendar event access
- `android.permission.READ_CONTACTS` - Contact list access
- `android.permission.ACCESS_NOTIFICATION_POLICY` - Do Not Disturb control
- `android.permission.RECEIVE_BOOT_COMPLETED` - Re-enqueue workers on boot
- `android.permission.READ_PHONE_STATE` - Call state monitoring
- `android.permission.POST_NOTIFICATIONS` - Blocked call alerts (Android 13+)

## Webhooks & Callbacks

**Incoming:**
- None detected - App does not expose web service endpoints

**Outgoing:**
- None - Calendar and contacts are queried locally, no external API calls made
- Firebase events sent via Firebase backend (automatic, SDK-managed)

---

*Integration audit: 2026-02-26*
