# Testing Patterns

**Analysis Date:** 2026-02-26

## Test Framework

**Runner:**
- JUnit 4 - configured in `app/build.gradle.kts`
- Android Test Framework (AndroidX Test) for instrumentation tests
- Espresso 3.5.1 for UI testing
- No unit test framework explicitly configured

**Test Configuration:**
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `app/build.gradle.kts`
- Compose testing support: `androidx.compose.ui:ui-test-junit4` for Compose tests

**Assertion Library:**
- JUnit assertions (implicit via junit:junit:4.13.2)
- No explicit assertion library (Mockito, AssertJ, etc.) configured

**Run Commands:**
```bash
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumentation tests on connected device
./gradlew test --watch         # Watch mode (not available in Gradle, use IDE)
```

**Current Test Coverage:**
- No test directory exists at `app/src/test/` or `app/src/androidTest/`
- Zero unit tests or instrumentation tests present in codebase

## Test File Organization

**Location:**
- Test files SHOULD be placed in `app/src/test/java/com/dodisturb/app/` for unit tests
- Instrumentation tests SHOULD be placed in `app/src/androidTest/java/com/dodisturb/app/` for Android-specific tests
- Currently, NO test directories exist in the project

**Naming Convention (to follow):**
- Test files should use pattern: `{ClassName}Test.kt`
- Examples: `MainViewModelTest.kt`, `PreferencesManagerTest.kt`, `CalendarSyncWorkerTest.kt`

**Test Structure (recommended pattern):**
```
app/src/test/java/com/dodisturb/app/
├── data/
│   └── repository/
│       └── PreferencesManagerTest.kt
├── ui/
│   └── MainViewModelTest.kt
└── worker/
    └── CalendarSyncWorkerTest.kt

app/src/androidTest/java/com/dodisturb/app/
├── service/
│   └── DoDisturbCallScreeningServiceTest.kt
└── ui/screens/
    └── HomeScreenTest.kt
```

## Test Structure

**Suite Organization (recommended):**

Based on Android/JUnit 4 conventions observed in gradle configuration:

```kotlin
class MainViewModelTest {
    private lateinit var viewModel: MainViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        // Initialize test fixtures
    }

    @After
    fun teardown() {
        // Clean up
    }

    @Test
    fun testRefreshStateWithoutPermissions() {
        // Arrange
        // Act
        // Assert
    }
}
```

**Patterns to implement:**

- **Setup pattern:** Use `@Before` to initialize ViewModel, database, preferences
- **Teardown pattern:** Use `@After` to clean resources and reset state
- **Assertion pattern:** Simple JUnit assertions or extend with custom matchers

## Mocking

**Framework:**
- Mockito is NOT explicitly configured
- No mocking library found in dependencies
- Manual mocking via test doubles or fake implementations recommended

**Patterns to implement:**

Since no mocking framework is configured, use:
1. **Fake implementations** of interfaces:
```kotlin
class FakeTimeframeRepository(private val timeframes: List<AllowedTimeframe>) : TimeframeRepository {
    override fun getUpcomingTimeframes(): Flow<List<AllowedTimeframe>> {
        return flowOf(timeframes)
    }
}
```

2. **Test contexts** for Android components:
```kotlin
val testContext = ApplicationProvider.getApplicationContext<Context>()
```

3. **In-memory database** for Room tests:
```kotlin
@get:Rule
val instantExecutorRule = InstantTaskExecutorRule()

private lateinit var db: AppDatabase

@Before
fun setup() {
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
```

**What to Mock/Stub:**
- Android system services (NotificationManager, Context, RoleManager)
- External dependencies (Firebase Analytics, Timber)
- Database via in-memory Room database
- ContentResolver queries (CalendarContract)

**What NOT to Mock:**
- Core business logic (TimeframeRepository, PreferencesManager)
- Data classes (AllowedTimeframe, BlockedCallInfo)
- Kotlin Flow/coroutines - test actual async behavior

## Fixtures and Factories

**Test Data (recommended pattern):**

```kotlin
object TestData {
    fun createAllowedTimeframe(
        id: Long = 1,
        title: String = "Meeting",
        startTime: Long = System.currentTimeMillis(),
        endTime: Long = startTime + 3600000
    ) = AllowedTimeframe(
        id = id,
        calendarEventId = "event-$id",
        title = title,
        startTime = startTime,
        endTime = endTime
    )

    fun createBlockedCallInfo(
        phoneNumber: String = "+33612345678",
        timestamp: Long = System.currentTimeMillis()
    ) = BlockedCallInfo(
        phoneNumber = phoneNumber,
        timestamp = timestamp,
        reason = "not_in_contacts"
    )
}
```

**Location:**
- Test fixtures should live in `app/src/test/java/com/dodisturb/app/utils/TestData.kt`
- Shared test utilities in `app/src/test/java/com/dodisturb/app/utils/`

## Coverage

**Requirements:**
- No explicit coverage requirements configured
- No coverage report tools (JaCoCo) in build configuration

**Recommended approach:**
- Aim for >70% coverage on critical paths: call screening logic, calendar sync, DND management
- Lower priority for UI layer (screens, theme)

**To enable coverage (future):**
```bash
./gradlew test jacocoTestReport    # After adding JaCoCo plugin
./gradlew connectedAndroidTest --coverage
```

## Test Types

**Unit Tests (app/src/test/):**
- **Scope:** Pure Kotlin logic without Android dependencies
- **Examples to write:**
  - `PreferencesManagerTest` - SharedPreferences mock behavior
  - `TimeframeRepositoryTest` - Query logic and filtering
  - `AnalyticsHelperTest` - Event logging calls (mock Firebase Analytics)
  - `CalendarSyncWorkerTest` - Calendar query parsing logic

**Instrumentation Tests (app/src/androidTest/):**
- **Scope:** Android-specific functionality requiring context, permissions, system services
- **Examples to write:**
  - `DoDisturbCallScreeningServiceTest` - Call screening decisions
  - `DndManagerTest` - NotificationManager interaction
  - `MainViewModelTest` - Permission state and UI state updates
  - `HomeScreenTest` - Compose UI rendering and interactions

**E2E Tests:**
- **Status:** Not currently in use
- **Recommendation:** Consider for critical user flows (setup wizard → blocking → call handling)

## Common Patterns

**Async Testing (CoroutineWorker, Flow):**

```kotlin
@Test
fun testSyncCompletes() = runTest {
    val worker = CalendarSyncWorker(context, WorkerParameters())
    val result = worker.doWork()

    assertEquals(Result.success(), result)
}
```

Using TestDispatchers and `runTest` from `kotlinx-coroutines-test`:
```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()

@Test
fun testStateUpdates() = runTest {
    viewModel.refreshState()
    advanceUntilIdle()

    assertEquals(true, viewModel.uiState.value.isSetupComplete)
}
```

**Flow Collection Testing:**

```kotlin
@Test
fun testUpcomingTimeframes() = runTest {
    val timeframes = listOf(
        TestData.createAllowedTimeframe(),
        TestData.createAllowedTimeframe(id = 2)
    )
    val repository = FakeTimeframeRepository(timeframes)

    repository.getUpcomingTimeframes().test {
        assertEquals(2, awaitItem().size)
        awaitComplete()
    }
}
```

**Error Testing:**

```kotlin
@Test(expected = IllegalStateException::class)
fun testInvalidPermissionThrows() {
    val manager = DndManager(context)
    // Mock: notificationManager.isNotificationPolicyAccessGranted = false
    manager.disableDnd()
    // Should handle gracefully with Timber logging instead of throwing
}
```

Better pattern (no exceptions expected):

```kotlin
@Test
fun testDndDisableWithoutPermission() {
    val mockNotificationManager = mock<NotificationManager>()
    // mockNotificationManager.isNotificationPolicyAccessGranted = false

    manager.disableDnd()

    // Should log warning but not crash
    // Verify Timber.w() was called
}
```

## Missing Test Infrastructure

**Currently absent:**
- No test source directories (`app/src/test/` or `app/src/androidTest/`)
- No test data builders or factories
- No mock framework configured (Mockito)
- No coroutine testing library (`kotlinx-coroutines-test`)
- No Room testing utilities
- No UI testing infrastructure beyond Espresso
- No CI/CD test execution pipeline

**Recommended additions to build.gradle.kts:**
```kotlin
dependencies {
    // Testing - Unit
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Testing - Database
    testImplementation("androidx.room:room-testing:2.6.1")

    // Testing - Android Instrumentation
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:rules:1.5.0")
}
```

---

*Testing analysis: 2026-02-26*

**Note:** This codebase currently has NO tests implemented. These patterns describe recommended practices based on gradle configuration and Android/Kotlin best practices for this project type.
