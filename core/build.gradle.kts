plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Shared by every module: theme and components, in-app updates, the Claude client, data events,
// and the host contract (AppModule, Host, AppInfo). Libraries every module uses are exposed with api().
android {
    namespace = "app.stride.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    api(composeBom)
    api("androidx.core:core-ktx:1.15.0")
    api("androidx.activity:activity-compose:1.9.3")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-extended")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    // Home-screen widgets and background work, used by every module.
    api("androidx.glance:glance-appwidget:1.1.1")
    api("androidx.work:work-runtime-ktx:2.9.1")
    // AI coach (Claude API, the user's own key).
    api("com.anthropic:anthropic-java:2.34.0")

    testImplementation("junit:junit:4.13.2")
}
