# Codebase Structure

**Analysis Date:** 2026-02-26

## Directory Layout

```
DoDisturb/
├── app/                                        # Main Android application module
│   ├── build.gradle.kts                       # App-level build configuration
│   ├── src/main/
│   │   ├── java/com/dodisturb/app/           # Source code root
│   │   │   ├── DoDisturbApp.kt                # Application class (entry point)
│   │   │   ├── data/                          # Data layer (models, DB, repositories)
│   │   │   │   ├── db/                        # Room database and DAOs
│   │   │   │   │   ├── AppDatabase.kt         # Database singleton
│   │   │   │   │   ├── TimeframeDao.kt        # DAO for allowed timeframes
│   │   │   │   │   └── BlockedCallDao.kt      # DAO for blocked calls
│   │   │   │   ├── model/                     # Entity and data classes
│   │   │   │   │   ├── AllowedTimeframe.kt    # Calendar event timeframe entity
│   │   │   │   │   └── BlockedCallInfo.kt     # Blocked call log entity
│   │   │   │   └── repository/                # Data abstraction layer
│   │   │   │       ├── TimeframeRepository.kt # Timeframe access logic
│   │   │   │       └── PreferencesManager.kt  # SharedPreferences wrapper
│   │   │   ├── service/                       # Android system services
│   │   │   │   └── DoDisturbCallScreeningService.kt  # Call screening implementation
│   │   │   ├── ui/                            # UI layer (Compose, ViewModel)
│   │   │   │   ├── MainActivity.kt            # Single activity entry point
│   │   │   │   ├── MainViewModel.kt           # Main screen state & logic
│   │   │   │   ├── screens/                   # Composable screens
│   │   │   │   │   ├── HomeScreen.kt          # Main dashboard screen
│   │   │   │   │   ├── CallLogScreen.kt       # Blocked calls history
│   │   │   │   │   ├── SetupScreen.kt         # Permissions & setup wizard
│   │   │   │   │   └── DebugScreen.kt         # Debug info and controls
│   │   │   │   └── theme/                     # Compose theme config
│   │   │   │       ├── Color.kt               # Color palette definitions
│   │   │   │       └── Theme.kt               # Material 3 theme composition
│   │   │   ├── util/                          # Utility and helper classes
│   │   │   │   ├── AnalyticsHelper.kt         # Firebase event logging
│   │   │   │   ├── BootReceiver.kt            # Device boot event handler
│   │   │   │   ├── ContactsHelper.kt          # Contacts lookups
│   │   │   │   ├── DndManager.kt              # Do Not Disturb control
│   │   │   │   └── NotificationHelper.kt      # Notification creation/management
│   │   │   └── worker/                        # Background work (WorkManager)
│   │   │       └── CalendarSyncWorker.kt      # Periodic calendar sync task
│   │   ├── res/                               # Android resources
│   │   │   ├── drawable/                      # Vector drawable assets
│   │   │   ├── mipmap-*/                      # App icon variants (hdpi, mdpi, etc.)
│   │   │   ├── values/                        # String/color/dimen resources
│   │   │   └── xml/                           # XML resource configs
│   │   └── AndroidManifest.xml                # App manifest (permissions, components)
│   └── proguard-rules.pro                     # ProGuard obfuscation rules
├── build.gradle.kts                           # Root build configuration
├── settings.gradle.kts                        # Project settings
├── gradle.properties                          # Gradle global properties
├── local.properties                           # Local SDK paths (gitignored)
├── .planning/                                 # GSD planning documents
│   └── codebase/                              # Architecture & quality docs
└── README.md                                  # Project documentation
```

## Directory Purposes

**app/:**
- Purpose: Single-module Android application containing all source code, resources, and config
- Contains: Kotlin source files, Android resources, manifests, build configuration
- Key files: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

**app/src/main/java/com/dodisturb/app/:**
- Purpose: Root package for all application Kotlin code
- Contains: All feature packages organized by layer (data, service, ui, util, worker)
- Key files: `DoDisturbApp.kt` (entry point)

**app/src/main/java/com/dodisturb/app/data/:**
- Purpose: Data layer containing models, database, repositories
- Contains: Room entities, DAOs, repository abstractions, SharedPreferences managers
- Key files: `db/AppDatabase.kt` (singleton), `repository/TimeframeRepository.kt`, `repository/PreferencesManager.kt`

**app/src/main/java/com/dodisturb/app/data/db/:**
- Purpose: Room database configuration and data access objects
- Contains: AppDatabase singleton, TimeframeDao, BlockedCallDao
- Key files: `AppDatabase.kt` (Room setup with fallbackToDestructiveMigration), `TimeframeDao.kt` (flow-based queries)

**app/src/main/java/com/dodisturb/app/data/model/:**
- Purpose: Room entity definitions and domain models
- Contains: AllowedTimeframe (calendar events), BlockedCallInfo (call history)
- Key files: All use @Entity annotation for Room tables

**app/src/main/java/com/dodisturb/app/data/repository/:**
- Purpose: Abstract data sources behind clean interfaces
- Contains: TimeframeRepository (Room queries), PreferencesManager (SharedPreferences), CalendarInfo (data class)
- Key files: `TimeframeRepository.kt` (repository pattern), `PreferencesManager.kt` (type-safe prefs)

**app/src/main/java/com/dodisturb/app/service/:**
- Purpose: Android system service implementations
- Contains: DoDisturbCallScreeningService (telecom integration)
- Key files: `DoDisturbCallScreeningService.kt` (screens calls and blocks non-contacts)

**app/src/main/java/com/dodisturb/app/ui/:**
- Purpose: UI presentation layer with Compose and ViewModel
- Contains: MainActivity, MainViewModel, Composable screens, Material 3 theme
- Key files: `MainActivity.kt` (activity entry), `MainViewModel.kt` (state management)

**app/src/main/java/com/dodisturb/app/ui/screens/:**
- Purpose: Individual Compose screen implementations
- Contains: HomeScreen (dashboard), CallLogScreen (blocked calls), SetupScreen (permissions), DebugScreen (debug tools)
- Key files: Each screen file handles one tab of the bottom navigation

**app/src/main/java/com/dodisturb/app/ui/theme/:**
- Purpose: Material 3 theme configuration and color definitions
- Contains: Color.kt (palette), Theme.kt (theme composition)
- Key files: `Theme.kt` (applies Material 3 theme to app)

**app/src/main/java/com/dodisturb/app/util/:**
- Purpose: Reusable utility and helper classes
- Contains: AnalyticsHelper (Firebase events), BootReceiver (boot broadcast), ContactsHelper (contacts API), DndManager (DND control), NotificationHelper (notification creation)
- Key files: `DndManager.kt` (manages DND state), `ContactsHelper.kt` (contact lookups), `AnalyticsHelper.kt` (event logging)

**app/src/main/java/com/dodisturb/app/worker/:**
- Purpose: Background work tasks via WorkManager
- Contains: CalendarSyncWorker (periodic calendar sync)
- Key files: `CalendarSyncWorker.kt` (syncs events and manages DND)

**app/src/main/res/:**
- Purpose: Android resource files (strings, colors, drawables, icons)
- Contains: drawable/, mipmap-*/, values/, xml/
- Key files: `values/strings.xml` (string resources), `values/colors.xml` (color definitions)

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/dodisturb/app/DoDisturbApp.kt`: Application class; initializes database, notification channels, logging
- `app/src/main/java/com/dodisturb/app/ui/MainActivity.kt`: Single activity; sets up Compose UI and navigation
- `app/src/main/java/com/dodisturb/app/service/DoDisturbCallScreeningService.kt`: System service; handles call screening
- `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt`: Background worker; syncs calendar events

**Configuration:**
- `app/build.gradle.kts`: Dependencies, SDK versions, Compose configuration
- `app/src/main/AndroidManifest.xml`: Permissions, activities, services, broadcast receivers
- `app/src/main/java/com/dodisturb/app/data/db/AppDatabase.kt`: Room database singleton setup
- `build.gradle.kts`: Root project configuration

**Core Logic:**
- `app/src/main/java/com/dodisturb/app/ui/MainViewModel.kt`: All UI state and business logic
- `app/src/main/java/com/dodisturb/app/data/repository/TimeframeRepository.kt`: Timeframe queries
- `app/src/main/java/com/dodisturb/app/util/DndManager.kt`: DND state management
- `app/src/main/java/com/dodisturb/app/worker/CalendarSyncWorker.kt`: Calendar sync and DND updates

**Database:**
- `app/src/main/java/com/dodisturb/app/data/db/AppDatabase.kt`: Room database config
- `app/src/main/java/com/dodisturb/app/data/db/TimeframeDao.kt`: Timeframe queries
- `app/src/main/java/com/dodisturb/app/data/db/BlockedCallDao.kt`: Blocked call queries

## Naming Conventions

**Files:**
- Kotlin files: PascalCase matching class name (e.g., `MainViewModel.kt`)
- Package structure: lowercase with dots (e.g., `com.dodisturb.app.ui`)
- Resource files: snake_case (e.g., `strings.xml`, `colors.xml`)

**Directories:**
- Package dirs: lowercase (e.g., `data/`, `ui/`, `service/`)
- Resource dirs: Android naming convention (e.g., `mipmap-hdpi`, `values`)

**Kotlin Code:**
- Classes: PascalCase (e.g., `MainViewModel`, `TimeframeRepository`)
- Functions: camelCase (e.g., `getAllBlockedCalls()`, `isInAllowedTimeframe()`)
- Variables: camelCase (e.g., `phoneNumber`, `isBlockingEnabled`)
- Constants: UPPER_SNAKE_CASE in companion objects (e.g., `WORK_NAME`, `PREFS_NAME`)
- Interfaces: PascalCase prefixed with I (Room DAOs exception: just PascalCase with Dao suffix)

## Where to Add New Code

**New Feature:**
- Primary code: `app/src/main/java/com/dodisturb/app/ui/screens/` (if UI-focused)
- Or: `app/src/main/java/com/dodisturb/app/util/` (if utility helper)
- ViewModel logic: Add method to `MainViewModel.kt` if it affects shared UI state
- Database access: Create DAO method in existing `*Dao.kt` or extend model in `data/model/`

**New Screen:**
- Implementation: `app/src/main/java/com/dodisturb/app/ui/screens/NewScreen.kt` (Composable function)
- State: Add state fields to `AppUiState` data class in `MainViewModel.kt`
- Navigation: Add tab to bottom navigation in `MainActivity.kt` DoDisturbApp() composable
- Theme: Use colors/styles from `app/src/main/java/com/dodisturb/app/ui/theme/`

**New Data Model:**
- Entity: `app/src/main/java/com/dodisturb/app/data/model/NewEntity.kt` (with @Entity annotation)
- DAO: `app/src/main/java/com/dodisturb/app/data/db/NewDao.kt` (as interface with @Dao)
- Database: Register in `AppDatabase.kt` entities list and add abstract fun method
- Repository: Create `app/src/main/java/com/dodisturb/app/data/repository/NewRepository.kt` if complex queries

**Preference/Setting:**
- Storage: Add getter/setter to `PreferencesManager.kt` using pattern: `var key: Type get() = prefs.get(...) set(value) = prefs.edit { put(...) }`
- Key constant: Add to `PreferencesManager.companion object`
- UI binding: Use in `MainViewModel.kt` and expose via `AppUiState`

**New Background Task:**
- Worker: Create `app/src/main/java/com/dodisturb/app/worker/NewWorker.kt` extending `CoroutineWorker`
- Manifest: Add to `app/src/main/AndroidManifest.xml` (usually no entry needed for WorkManager)
- Enqueuing: Add static methods to companion object for `enqueue()` and `cancel()`

**New Utility Helper:**
- File: `app/src/main/java/com/dodisturb/app/util/NewHelper.kt` (class or object)
- Usage: Call from ViewModel, Service, or Worker as needed

**New Permission:**
- Manifest: Add `<uses-permission>` to `app/src/main/AndroidManifest.xml`
- Check: Add to `MainViewModel.refreshState()` method
- UI Prompt: Add field to `AppUiState` and UI in `SetupScreen`

## Special Directories

**.planning/:**
- Purpose: GSD planning documents and architectural analysis
- Generated: Yes (by GSD commands)
- Committed: Yes

**.gradle/ and .idea/:**
- Purpose: Gradle and Android Studio IDE metadata
- Generated: Yes (auto-generated on build/IDE operations)
- Committed: No

**app/build/:**
- Purpose: Build output directory
- Generated: Yes (compilation artifacts)
- Committed: No (in .gitignore)

**gradle/:**
- Purpose: Gradle wrapper scripts
- Generated: No
- Committed: Yes (for consistent build tool versions)

---

*Structure analysis: 2026-02-26*
