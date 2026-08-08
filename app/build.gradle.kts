import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = localProps.getProperty("RELEASE_STORE_FILE") != null

// THIS REPO IS PUBLIC. The bridge's LAN address is a per-install value, not a
// constant to ship — it used to be hard-coded in Settings.kt and was readable by
// anyone without a login. It comes from local.properties (gitignored) now, and
// defaults to empty so a fresh clone builds and prompts on the Settings screen
// rather than dialling someone else's network. Set DEFAULT_BRIDGE_URL there.
val defaultBridgeUrl = localProps.getProperty("DEFAULT_BRIDGE_URL") ?: ""

android {
    namespace = "com.darney.bubblewatch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darney.bubblewatch"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"

        buildConfigField("String", "DEFAULT_BRIDGE_URL", "\"$defaultBridgeUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(localProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Wear Compose UI
    implementation("androidx.wear.compose:compose-material:1.3.0")
    implementation("androidx.wear.compose:compose-foundation:1.3.0")
    implementation("androidx.wear.compose:compose-navigation:1.3.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-input:1.1.0") // RemoteInput voice/keyboard helper
    implementation("androidx.fragment:fragment-ktx:1.6.2") // pin >=1.3.0: registerForActivityResult requires it

    // Lifecycle / ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Settings persistence
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Periodic re-post of the persistent shortcut notification (survives
    // process death + reboot; battery-safe, no foreground service)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Networking to the clawatch-bridge
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
