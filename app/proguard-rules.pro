# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# Media3 / ExoPlayer (reflection in extractors)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep BillingClient response classes (referenced by reflection internally)
-keep class com.android.billingclient.api.** { *; }
