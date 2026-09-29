plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.oliverpekel.karfunkel"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.oliverpekel.karfunkel"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-Schlüssel, damit das Release-APK ohne eigenen Keystore installierbar ist.
            signingConfig = signingConfigs.getByName("debug")
            // Nur ARM (Handys/Tablets); die x86-Bibliotheken von MapLibre braucht nur der Emulator.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") + (project.findProperty("extraAbi") as String?).orEmpty().split(',').filter { it.isNotBlank() } }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Native Bibliotheken komprimiert ablegen: APK halb so groß (wichtig zum Verschicken).
        jniLibs { useLegacyPackaging = true }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Karte: MapLibre (BSD) mit OpenFreeMap-Vektorkacheln (OpenStreetMap-Daten, ohne API-Schlüssel)
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    // HTML-Parser für die Kalenderseite
    implementation("org.jsoup:jsoup:1.18.1")
}
