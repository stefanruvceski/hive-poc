plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The Wallet module: fiscal receipts read from their QR code (suf.purs.gov.rs) and loyalty cards shown as barcodes.
android {
    namespace = "app.stride.wallet"
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
    // Live scanning with Google's scanner UI (no camera permission) and barcodes in a picked photo or screenshot.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    // Reads the printed fields of a receipt whose QR code won't scan.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    // Draws the loyalty card's barcode (pure Java).
    implementation("com.google.zxing:core:3.5.3")
    // Shop and card logos, card photos.
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
