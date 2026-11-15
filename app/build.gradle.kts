import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, default: String = ""): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: default

// Push notifications need your own Firebase project's google-services.json in app/
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.freepakistancall.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.freepakistancall.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("release") {
            val ks = cfg("KEYSTORE_PATH")
            if (ks.isNotEmpty()) {
                storeFile = file(ks)
                storePassword = cfg("KEYSTORE_PASSWORD")
                keyAlias = cfg("KEY_ALIAS")
                keyPassword = cfg("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "SERVER_URL", "\"${cfg("DEBUG_SERVER_URL", "http://10.0.2.2:3000")}\"")
            manifestPlaceholders["cleartext"] = "true"
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"${cfg("RELEASE_SERVER_URL", "https://CONFIGURE-RELEASE-SERVER-URL.invalid")}\"")
            manifestPlaceholders["cleartext"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (cfg("KEYSTORE_PATH").isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // WebRTC (maintained prebuilt of Google's libwebrtc; package org.webrtc)
    implementation("io.getstream:stream-webrtc-android:1.1.1")
    // Push (needs google-services.json)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    // PSTN: Twilio Voice (authorized provider). Verify latest 6.x version in Twilio docs.
    implementation("com.twilio:voice-android:6.6.2")
}
