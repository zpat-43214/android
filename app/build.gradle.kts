plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.nlrp.metradio"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.nlrp.metradio"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Your dispatch site (no trailing slash)
        buildConfigField("String", "BASE_URL", "\"https://met-nlrp.duckdns.org\"")
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("io.livekit:livekit-android:2.5.0")
    implementation("io.socket:socket.io-client:2.1.0") { exclude(group = "org.json", module = "json") }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
