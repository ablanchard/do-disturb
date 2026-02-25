# Add project specific ProGuard rules here.
-keep class com.google.api.services.calendar.** { *; }
-keep class com.google.api.client.** { *; }
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**
