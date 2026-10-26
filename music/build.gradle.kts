plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The Music module (Tempo inside Hive): download audio / video with yt-dlp, speed it up without changing pitch, play it.
android {
    namespace = "app.stride.music"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
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
    implementation("io.coil-kt:coil-compose:2.7.0")

    // yt-dlp + ffmpeg + aria2c bundled (Python runs from native libs; the app extracts them, see app/build.gradle.kts).
    val ydl = "0.17.3"
    implementation("io.github.junkfood02.youtubedl-android:library:$ydl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ydl")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$ydl")

    // Player: background playback with lock-screen controls.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("com.google.guava:guava:33.3.1-android")

    testImplementation("junit:junit:4.13.2")
}
