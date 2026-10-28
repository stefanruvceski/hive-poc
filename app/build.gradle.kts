plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "app.stride"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.stride"
        // 29: Music saves into the shared Music / Movies / Download folders through MediaStore.
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
        // Music bundles Python, yt-dlp and ffmpeg as native libraries; the Pixel needs only arm64.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Release signing comes from CI secrets (see .github/workflows/build.yml); the keystore is never committed.
    val keystorePath = System.getenv("STRIDE_KEYSTORE_PATH")
    val keystorePassword = System.getenv("STRIDE_KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "stride"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        // BuildConfig.DEBUG / VERSION_CODE / VERSION_NAME are handed to the modules via AppInfo (StrideApplication).
        buildConfig = true
    }
    packaging {
        // youtubedl-android runs its binaries from the extracted lib folder, so they must not stay compressed in the APK.
        jniLibs { useLegacyPackaging = true }
        // Duplicate license/metadata files from the Anthropic SDK's dependencies (Jackson, Kotlin).
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    // The modules; :health brings :core (shared UI, updates, coach client, host contract) with it.
    implementation(project(":health"))
    implementation(project(":habits"))
    implementation(project(":money"))
    implementation(project(":books"))
    implementation(project(":draw"))
    implementation(project(":wallet"))
    implementation(project(":music"))
    implementation(project(":invest"))
    implementation(project(":core"))
}
