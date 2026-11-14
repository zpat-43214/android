plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.yrp.sypapps"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yrp.sypapps"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Your dispatch site (no trailing slash)
        buildConfigField("String", "BASE_URL", "\"https://cad.yorkshire-rpc.co.uk\"")

        // Native Auth0 login (see LoginActivity/Auth0Session) — Client ID is public and safe
        // to ship in the app; there's deliberately no client secret anywhere here, since a
        // secret baked into an APK can just be pulled back out of it.
        buildConfigField("String", "AUTH0_DOMAIN", "\"yorkshire-rp.uk.auth0.com\"")
        buildConfigField("String", "AUTH0_CLIENT_ID", "\"t6nU2gGxkU0ps6YJl2aONCoiubSKQ4nD\"")
        buildConfigField("String", "AUTH0_SCHEME", "\"app.yrp.sypapps\"")
        // Matches AUTH0_API_AUDIENCE on the server — the custom Auth0 API registered for
        // the dispatch backend (not the built-in Auth0 Management API).
        buildConfigField("String", "AUTH0_AUDIENCE", "\"https://cad.yorkshire-rpc.co.uk/api\"")

        manifestPlaceholders["auth0Domain"] = "yorkshire-rp.uk.auth0.com"
        manifestPlaceholders["auth0Scheme"] = "app.yrp.sypapps"
    }

    // Debug builds normally sign with a throwaway keystore that Gradle auto-generates per
    // machine — fine locally, but on GitHub Actions (a fresh VM every run) that means a brand
    // new SHA256 fingerprint on every single build, which breaks anything (Auth0's Device
    // Settings, etc.) that's registered against a specific fingerprint.
    // If KEYSTORE_FILE is set (CI passes it, pointing at the keystore decoded from the
    // KEYSTORE_BASE64 secret) we sign debug builds with that real, stable keystore instead.
    // Nothing here changes the APK's actual debuggable/debug behaviour — only who signs it.
    val ciKeystorePath = System.getenv("KEYSTORE_FILE")
    if (ciKeystorePath != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
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
    implementation("com.auth0.android:auth0:4.1.0")
    implementation("io.livekit:livekit-android:2.5.0")
    implementation("io.socket:socket.io-client:2.1.0") { exclude(group = "org.json", module = "json") }
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
