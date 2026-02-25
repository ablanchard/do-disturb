# Do Disturb

A smart Android call-blocking app that integrates with Google Calendar to create time-based call filtering rules.

## Concept

**Do Disturb** flips the "Do Not Disturb" concept:

- **By default**: all incoming calls from numbers **not in your contacts** are blocked.
- **During calendar events**: calls from **all numbers** (including unknown) are temporarily allowed through.

If you have a calendar event like "Expecting delivery call," unknown callers will be let through for that time window. The app also manages the system-level Do Not Disturb mode automatically.

## How It Works

1. Complete the setup wizard (contacts, DND, call screening, Google Sign-In, notifications)
2. The app syncs events from a designated Google Calendar (default: `DoDisturb`) every 15 minutes
3. Calendar events become "allowed timeframes" stored locally
4. When a call arrives, the screening service checks:
   - Is blocking enabled?
   - Are we in an allowed timeframe?
   - Is the caller in contacts?
   - If none apply, the call is blocked and logged

## Screenshots

_Coming soon_

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Local Storage | Room Database |
| Background Sync | WorkManager (periodic 15-min) |
| Calendar API | Google Calendar API v3 |
| Authentication | Google Sign-In (OAuth2) |
| Call Blocking | Android CallScreeningService |
| Min SDK | 29 (Android 10) |
| Target SDK | 34 (Android 14) |

## Project Structure

```
app/src/main/java/com/dodisturb/app/
├── DoDisturbApp.kt                  # Application class
├── data/
│   ├── db/                          # Room database, DAOs
│   ├── model/                       # Entities (AllowedTimeframe, BlockedCallInfo)
│   └── repository/                  # SharedPreferences, Timeframe repository
├── service/
│   └── DoDisturbCallScreeningService.kt   # Core call screening logic
├── ui/
│   ├── MainActivity.kt              # Single-activity entry point
│   ├── MainViewModel.kt             # Central ViewModel + UI state
│   ├── screens/
│   │   ├── HomeScreen.kt            # Status, toggle, calendar, timeframes
│   │   ├── SetupScreen.kt           # Permission wizard
│   │   ├── CallLogScreen.kt         # Blocked calls history
│   │   └── DebugScreen.kt           # Calendar diagnostics
│   └── theme/                       # Material 3 theme + colors
├── util/
│   ├── BootReceiver.kt              # Re-register worker on boot
│   ├── ContactsHelper.kt            # Phone number lookup
│   ├── DndManager.kt                # System DND control
│   └── NotificationHelper.kt        # Notification channels
└── worker/
    └── CalendarSyncWorker.kt        # Periodic Google Calendar sync
```

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- A Google account with Google Calendar

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/ablanchard/do-disturb.git
   ```

2. Open the project in Android Studio.

3. Sync Gradle and build the project.

4. Create a Google Calendar named `DoDisturb` (or configure a different name in the app).

5. Run the app on a device or emulator (API 29+).

6. Follow the setup wizard to grant required permissions.

## Permissions

The app requires:

- **Contacts** -- to identify known callers
- **Do Not Disturb access** -- to manage system DND mode
- **Call screening role** -- to intercept and filter incoming calls
- **Google Sign-In** -- to read your Google Calendar (read-only)
- **Notifications** (Android 13+) -- to notify about blocked calls and sync errors

## Building

```bash
./gradlew assembleDebug
```

## License

_TBD_
