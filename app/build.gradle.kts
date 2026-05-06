plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** Optional local override read from ~/.gradle/gradle.properties or ./gradle.properties:
 *  trialBypass=true  — Release でも試用期限を無視（開発者の端末のみに推奨）
 *  trialBypass=false — Debug でも試用期限を適用（期限切れUIの確認用）
 * Debug 既定: 制限オフ。false 指定時のみ Debug でも本番と同じ期限挙動。
 */
val trialBypassGradleProp: String? =
    project.findProperty("trialBypass") as? String

android {
    namespace = "jp.simplist.smmultiplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.simplist.smmultiplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val trialBypassRelease =
                trialBypassGradleProp?.equals("true", ignoreCase = true) == true
            buildConfigField("boolean", "TRIAL_BYPASS", trialBypassRelease.toString())
        }
        debug {
            isMinifyEnabled = false
            val trialBypassDebug = when (trialBypassGradleProp?.lowercase()) {
                "false", "no", "0" -> false
                else -> true
            }
            buildConfigField("boolean", "TRIAL_BYPASS", trialBypassDebug.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    // Material Components (provides the Theme.Material3.* XML themes used by the Activity)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // DataStore Preferences (app settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // DocumentFile (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Google Play Billing (¥250 buyout, no subscription)
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
