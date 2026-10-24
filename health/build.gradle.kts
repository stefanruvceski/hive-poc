plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The Health module: training plan, workouts, recovery, widgets, notifications, sharing, Garmin, Health Connect.
android {
    namespace = "app.stride.health"
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
    api(project(":core"))
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Steps, sleep, weight, HRV and resting HR from Health Connect; strength workouts written back.
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
