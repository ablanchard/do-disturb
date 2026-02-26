# Technology Stack

**Analysis Date:** 2026-02-26

## Languages

**Primary:**
- Kotlin 1.9.22 - All application code and build configuration

## Runtime

**Environment:**
- Android Runtime (ART) - Target SDK 34 (Android 14)
- Min SDK: 29 (Android 10)

**Build System:**
- Gradle 8.5 - Build orchestration via Gradle Wrapper
- Kotlin Gradle Plugin 1.9.22

## Frameworks

**Core UI:**
- Jetpack Compose 2024.02.00 - Modern declarative UI framework
- Material 3 - Material Design 3 component library
- Material Icons Extended - Extended icon set for Material Design

**Android Architecture:**
- Jetpack Lifecycle 2.7.0 - Lifecycle-aware components (ViewModel, LiveData)
- Jetpack Activity 1.8.2 - Activity integration with Compose

**Data & Storage:**
- Room 2.6.1 - SQLite ORM and database abstraction
- SQLite - Local relational database (name: `dodisturb_db`)
- SharedPreferences - Key-value preference storage

**Background Processing:**
- WorkManager 2.9.0 - Reliable background job scheduling
- Kotlin Coroutines 1.7.3 - Asynchronous programming

**Testing:**
- JUnit 4.13.2 - Unit testing framework
- Espresso 3.5.1 - UI testing framework
- Compose Testing - Compose UI testing utilities

## Key Dependencies

**Critical:**
- androidx.core:core-ktx:1.12.0 - Core Kotlin extensions for Android
- androidx.room:room-runtime:2.6.1 - Essential for local data persistence
- androidx.work:work-runtime-ktx:2.9.0 - Required for periodic calendar sync operations
- kotlinx-coroutines-android:1.7.3 - Enables non-blocking coroutine-based operations

**Infrastructure:**
- com.google.firebase:firebase-crashlytics-ktx - Remote crash reporting
- com.google.firebase:firebase-analytics-ktx - Analytics event tracking
- com.google.devtools.ksp:1.9.22-1.0.17 - Kotlin Symbol Processing for Room annotation compilation
- com.jakewharton.timber:timber:5.0.1 - Structured logging with redaction support

**Build Plugins:**
- com.android.application:8.2.2 - Android application plugin
- com.google.gms.google-services:4.4.0 - Google Services plugin for Firebase
- com.google.firebase.crashlytics:2.9.9 - Crashlytics build plugin

## Configuration

**Environment:**
- Application namespace: `com.dodisturb.app`
- Debug and Release build types
- ProGuard enabled for release builds (minification: disabled, shrinking/obfuscation: default)

**Build Optimization:**
- Compose compiler extension version: 1.5.8
- Java/Kotlin compilation target: JVM 17
- Vector drawable support library enabled
- Build config generation enabled

**Dependency Resolution:**
- Repository mode: FAIL_ON_PROJECT_REPOS (strict dependency management)
- Primary repositories: Google Maven, Maven Central, Gradle Plugin Portal

## Platform Requirements

**Development:**
- Android SDK 34 (for compilation)
- Android SDK 29+ (runtime minimum)
- JDK 17 or later (for Gradle and Kotlin compilation)

**Production:**
- Target: Android 14 (API 34) with fallback support to Android 10 (API 29)
- Installation requires: Google Play Store or manual APK installation
- Device permissions required by app defined in `app/src/main/AndroidManifest.xml`

---

*Stack analysis: 2026-02-26*
