# Codebase Concerns

**Analysis Date:** 2026-02-26

## Tech Debt

**Database Thread Safety Configuration:**
- Issue: `allowMainThreadQueries()` is enabled in `AppDatabase.getInstance()` for Room database operations. This is a significant anti-pattern that bypasses Room's safety checks.
- Files: `app/src/main/java/com/dodisturb/app/data/db/AppDatabase.kt` (line 32)
- Impact: Can cause ANR (Application Not Responding) errors when database operations block the main thread, leading to app crashes or freezes. The comment justifies this for CallScreeningService which "runs on binder thread," but this enables main thread access across the entire database.
- Fix approach: Refactor synchronous database calls to use async patterns. The CallScreeningService in `DoDisturbCallScreeningService.kt` should use `insertSync()` and `getActiveTimeframeSync()` methods more defensively, or implement a separate dispatcher for binder-thread operations rather than allowing all main thread queries.

**Destructive Database Migration:**
- Issue: `fallbackToDestructiveMigration()` is enabled in `AppDatabase.getInstance()`. This automatically deletes all database tables when schema version changes.
- Files: `app/src/main/java/com/dodisturb/app/data/db/AppDatabase.kt` (line 31)
- Impact: Users lose all blocked call history and cached timeframe data when the app updates and database schema changes. No migration path exists for preserving data.
- Fix approach: Implement proper Room migrations using `Migration` classes when schema changes occur. Create explicit migration files for each schema version bump (currently at version 2).

**Missing Code Obfuscation:**
- Issue: `isMinifyEnabled = false` in release builds. No ProGuard/R8 configuration enabled.
- Files: `app/build.gradle.kts` (line 28)
- Impact: Compiled code is fully reverse-engineerable. Sensitive logic including Firebase API calls, DND management, and contact handling can be analyzed and exploited. App size is larger than necessary.
- Fix approach: Enable minification with R8 in release builds. Add appropriate ProGuard rules to retain public APIs while obfuscating internal logic.

**JSON Parsing without Validation:**
- Issue: CalendarInfo JSON serialization in `PreferencesManager.getAvailableCalendars()` silently returns empty list on parsing failure without logging details.
- Files: `app/src/main/java/com/dodisturb/app/data/repository/PreferencesManager.kt` (lines 101-103)
- Impact: If corrupted data exists in SharedPreferences, users won't know why their calendar list disappeared. Debugging becomes difficult.
- Fix approach: Log the exception details before returning empty list. Consider persisting data in a more robust format (Protocol Buffers, DataStore) or adding a version number to JSON payloads.

## Known Bugs

**Manual Sync Timing Issue:**
- Bug: Hard-coded 5-second delay for sync UI state refresh may be insufficient or excessive depending on network conditions.
- Files: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt` (line 175)
- Trigger: User taps "Refresh" button, WorkManager enqueues sync, UI waits 5 seconds then checks if sync completed.
- Impact: If sync takes longer than 5 seconds, UI shows stale `isSyncing=false` before actual completion. If sync finishes in <5s, the delay is unnecessary.
- Workaround: User can manually refresh again if they suspect data is stale.
- Fix approach: Replace hard-coded delay with WorkInfo state observation from WorkManager or implement a callback mechanism from CalendarSyncWorker to notify MainViewModel of completion.

**Phone Number Lookup Fails Open:**
- Bug: `ContactsHelper.isNumberInContacts()` returns `true` on exception, allowing unknown callers through.
- Files: `app/src/main/java/com/dodisturb/app/util/ContactsHelper.kt` (lines 41-44)
- Trigger: Any exception during contacts lookup (permissions changed, database locked, etc.)
- Impact: Critical security issue - unknown callers bypass the filter when contacts database is unavailable. The fail-safe defaults to allow calls rather than block them.
- Workaround: None. Users must hope the contacts provider is accessible.
- Fix approach: Implement defensive checks before calling ContentProvider. Cache contacts list during setup so failures don't break the system. Or fail safely by blocking the call instead of allowing it.

**No Timezone Handling:**
- Bug: Calendar event times are treated as epoch milliseconds without timezone awareness. If user crosses timezones or changes device timezone, timeframe logic breaks.
- Files: `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt` (lines 208-258), `app/src/main/java/com/dodisturb/app/data/model/AllowedTimeframe.kt`
- Trigger: User has calendar event scheduled for "9 AM" in timezone A, then travels to timezone B or changes device timezone.
- Impact: Timeframes will activate/deactivate at wrong wall-clock times. User expects "9 AM" to be respected in their current timezone, not epoch time.
- Fix approach: Store timezone information with each timeframe. Compare against current device timezone rather than epoch comparison alone.

## Security Considerations

**Insufficient Input Validation:**
- Risk: Phone numbers in `DoDisturbCallScreeningService.onScreenCall()` are extracted from Call.Details.handle without validation.
- Files: `app/src/main/java/com/dodisturb/app/service/DoDisturbCallScreeningService.kt` (line 27)
- Current mitigation: Stored to database with no validation. Malformed numbers could cause unexpected behavior.
- Recommendations: Validate phone number format before storage and contact lookup. Sanitize before logging to Crashlytics breadcrumbs.

**Calendar Name Stored in Plain SharedPreferences:**
- Risk: Calendar name is persisted in SharedPreferences which is stored unencrypted. Could reveal calendar structure to attackers with device access.
- Files: `app/src/main/java/com/dodisturb/app/data/repository/PreferencesManager.kt` (line 21)
- Current mitigation: None. Calendar name is readable from shared_prefs XML.
- Recommendations: Migrate to EncryptedSharedPreferences from androidx.security library. Consider whether calendar name needs to be stored at all.

**Firebase Crashlytics Logging Includes Timestamps:**
- Risk: Log messages in CrashlyticsTree include raw message content. While PII redaction was added (see recent commits), breadcrumb logging may still leak sensitive data indirectly.
- Files: `app/src/main/java/com/dodisturb/app/DoDisturbApp.kt` (line 44)
- Current mitigation: Recent commits show effort to redact calendar names and contact data. But no centralized PII filtering.
- Recommendations: Add a custom Timber.Tree that intercepts all log messages and applies consistent PII redaction rules before forwarding to Crashlytics. Never log phone numbers or contact names even in debug logs.

**Notification Posting Without Explicit Check:**
- Risk: `NotificationHelper.notifyBlockedCall()` checks `canPostNotifications()` but silently fails if permission missing. Users may not know blocked calls are not being notified.
- Files: `app/src/main/java/com/dodisturb/app/util/NotificationHelper.kt` (line 63)
- Current mitigation: Checks permission, returns early if denied.
- Recommendations: Log when notifications cannot be posted so users understand why they're not seeing alerts. Consider a fallback (toast, status indicator).

## Performance Bottlenecks

**ContentProvider Query During Call Screening:**
- Problem: ContactsHelper.isNumberInContacts() performs a blocking ContentProvider query during call screening, which must complete quickly.
- Files: `app/src/main/java/com/dodisturb/app/util/ContactsHelper.kt` (line 33)
- Cause: PhoneLookup ContentProvider can be slow if contacts database is large or on slow storage. CallScreeningService.onScreenCall() runs synchronously.
- Improvement path: Pre-load contacts into memory during setup and refresh periodically. Use in-memory lookup instead of ContentProvider query for hot path. Implement timeout for contact lookup with fallback behavior.

**Calendar Sync Fetches 30 Days Every 15 Minutes:**
- Problem: CalendarSyncWorker queries 30 days of events every 15 minutes regardless of actual change frequency.
- Files: `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt` (lines 42-44, 128-135)
- Cause: Fixed 15-minute interval with no change detection or incremental sync.
- Improvement path: Implement exponential backoff - reduce sync frequency if last sync found no changes. Use CalendarContract.Calendars.LAST_MODIFIED to detect changes before querying events. Implement incremental sync with lastSyncTimestamp.

**Database Deletion and Reinsertion on Every Sync:**
- Problem: `replaceAllTimeframes()` deletes all timeframes and inserts new ones every sync, even if no changes.
- Files: `app/src/main/java/com/dodisturb/app/data/repository/TimeframeRepository.kt` (lines 51-53)
- Cause: No change detection between sync results and existing data.
- Improvement path: Compare synced timeframes with existing data. Only delete/insert items that actually changed. Keep unchanged events in place.

**No Query Indexing on Time-Based Lookups:**
- Problem: `getActiveTimeframe()` and `getUpcomingTimeframes()` queries run without explicit database indexes on startTime/endTime columns.
- Files: `app/src/main/java/com/dodisturb/app/data/db/TimeframeDao.kt` (lines 13-24)
- Cause: As timeframe count grows over months, queries may become O(n) table scans.
- Improvement path: Add `@Index` annotations to AllowedTimeframe entity on startTime and endTime columns. Add composite index on (startTime, endTime) for range queries.

## Fragile Areas

**CalendarSyncWorker Single Responsibility Violation:**
- Files: `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt`
- Why fragile: Worker handles calendar querying, error handling, notification posting, database updates, DND state management, and analytics in one class (259 lines). Changes to any component require careful analysis of side effects.
- Safe modification: Extract calendar querying logic to separate CalendarProvider class. Move DND updates to separate DndStateManager. Create SyncResultHandler to manage notifications and analytics.
- Test coverage: No dedicated unit tests for sync logic visible. Worker doWork() method has many branches without test coverage.

**TimeframeRepository Synchronous Variant:**
- Files: `app/src/main/java/com/dodisturb/app/data/repository/TimeframeRepository.kt` (lines 30-32)
- Why fragile: Maintains both async (`isInAllowedTimeframe()`) and sync (`isInAllowedTimeframeSync()`) variants. Any schema change or logic update requires updating both paths. Risk of inconsistency.
- Safe modification: Use single canonical async implementation. For CallScreeningService, use Room's `blockingQuery()` or implement proper threading model rather than exposing sync variants.
- Test coverage: Sync variant not tested separately from async variant.

**MainViewModel State Refresh Timing:**
- Files: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt` (lines 155-183)
- Why fragile: `triggerManualSync()` updates UI state with hard-coded 5-second delay. If sync timing changes, UI becomes inconsistent. Observable flows are not integrated with WorkManager state.
- Safe modification: Observe WorkInfo from WorkManager instead of estimating completion. Implement proper state machine for sync states (pending, active, completed, failed).
- Test coverage: ViewModel logic not unit tested. Sync timing behavior is hard-coded and untestable.

**Permission Check Pattern:**
- Files: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt` (lines 73-114)
- Why fragile: Permission checks are scattered across multiple files (DndManager, MainViewModel, SetupScreen). No centralized permission state machine. Easy to miss checking a permission in a new code path.
- Safe modification: Create PermissionManager utility that centralizes all permission checks and handles state consistently.
- Test coverage: Permission checking logic is intertwined with ViewModel initialization.

## Scaling Limits

**SharedPreferences for Large Data:**
- Current capacity: PreferencesManager stores JSON array of available calendars. Effectively tested at <20 calendars.
- Limit: SharedPreferences file size grows with calendar count. JSON serialization/deserialization becomes slow with >100 calendars. File corruption risk increases.
- Scaling path: Migrate calendar list to Room database. Implement pagination for calendar selection UI instead of loading all at once.

**Database Not Sharded by Calendar:**
- Current capacity: AllowedTimeframe table grows with calendar event count. Typically <1000 events in 30-day window.
- Limit: If user syncs multiple calendars or increases lookback window, table grows linearly. No cleanup strategy for very old events visible (only auto-delete expired).
- Scaling path: Implement automatic cleanup of events older than N days (currently only cleanup happens at sync time). Add cleanup job separate from sync.

**BlockedCallInfo Unbounded Growth:**
- Current capacity: Blocked calls accumulate indefinitely with no automatic cleanup.
- Limit: Over years, blocked_calls table could grow to millions of rows. Queries and storage will degrade.
- Scaling path: Implement automatic cleanup of calls older than 90 days. Add database optimization (VACUUM) periodically. Consider moving old records to archive.

## Dependencies at Risk

**Old Compose BOM Version:**
- Risk: Compose BOM pinned at 2024.02.00 (released Feb 2024). New security patches and bug fixes not included.
- Impact: Missing performance improvements and stability fixes in later Compose versions.
- Migration plan: Update to latest stable Compose BOM version. Test UI rendering and animations.

**FirebaseFirebaseFirebaseBOM at 32.7.1:**
- Risk: Firebase BOM pinned at 32.7.1 (Dec 2023). Newer versions available with additional features and security patches.
- Impact: Missing Firebase Analytics and Crashlytics improvements.
- Migration plan: Update to latest Firebase BOM. Verify Crashlytics event tracking still works correctly.

**Kotlin Compiler Extension Version 1.5.8:**
- Risk: Paired with Kotlin 1.9.22. May have compatibility issues with newer Kotlin versions.
- Impact: Prevents upgrading Kotlin for bug fixes and new features.
- Migration plan: Test upgrading to latest Kotlin compiler extension compatible with target Kotlin version.

## Missing Critical Features

**No Data Backup/Restore:**
- Problem: App has no backup of user configuration, blocked call log, or timeframe cache. If app is uninstalled or data is cleared, all settings are lost.
- Blocks: Users cannot easily migrate to new device or recover from accidental data deletion.
- Recommended implementation: Use Android's BackupManager framework or cloud sync via Firebase Realtime Database.

**No Network Change Resilience:**
- Problem: CalendarSyncWorker may fail if network becomes unavailable mid-sync. Errors are logged but no retry strategy with backoff is implemented beyond WorkManager's default retry.
- Blocks: Users with intermittent connectivity may experience sync failures and stale calendar data.
- Recommended implementation: Implement exponential backoff strategy in CalendarSyncWorker. Add offline caching with eventual consistency.

**No Manual Contact Import/Export:**
- Problem: App relies entirely on system contacts. No way to add phone numbers directly or backup contact list within app.
- Blocks: If user loses contacts (factory reset, account delete), they cannot restore contact-based blocking rules.
- Recommended implementation: Allow users to create local contact lists within app or import CSV files.

## Test Coverage Gaps

**No Unit Tests for CalendarSyncWorker:**
- What's not tested: Calendar querying, event filtering, timeframe creation, error handling, retry logic
- Files: `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt`
- Risk: Sync logic is core functionality. Changes could silently break event filtering or error recovery without test protection.
- Priority: High

**No Unit Tests for MainViewModel:**
- What's not tested: Permission state tracking, UI state updates, sync triggering, blocking toggle logic
- Files: `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt`
- Risk: UI logic controls critical features. Bugs in state management could leave users without blocking functionality unknowingly.
- Priority: High

**No Tests for CallScreeningService:**
- What's not tested: Call blocking decision logic, contact lookup, timeframe checking, database persistence, notification sending
- Files: `app/src/main/java/com/dodisturb/app/service/DoDisturbCallScreeningService.kt`
- Risk: This is the core security feature. No test protection means blocking logic could silently fail.
- Priority: Critical

**No Integration Tests:**
- What's not tested: Sync → UI update → DND control → Call blocking flow end-to-end
- Impact: Cannot verify that changes to one component don't break dependent features.
- Priority: High

**No Tests for ContactsHelper:**
- What's not tested: Phone number lookup, ContentProvider interaction, fallback behavior on error
- Files: `app/src/main/java/com/dodisturb/app/util/ContactsHelper.kt`
- Risk: The "fail open" behavior (return true on error) is untested, so regression is undetected.
- Priority: Medium

**No Tests for DndManager:**
- What's not tested: DND enable/disable, state restoration, permission checks
- Files: `app/src/main/java/com/dodisturb/app/util/DndManager.kt`
- Risk: DND state management is critical. Errors could leave DND permanently enabled or disabled.
- Priority: High

---

*Concerns audit: 2026-02-26*
