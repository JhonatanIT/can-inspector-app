import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Dependency versions (avoid hardcoded literals for SonarQube compliance)
val composeBomVersion = "2026.06.01"
val lifecycleVersion = "2.11.0"
val navigationComposeVersion = "2.9.8"
val coreKtxVersion = "1.19.0"
val activityComposeVersion = "1.13.0"

// Configure the Android application extension (AGP 9+ new DSL)
configure<ApplicationExtension> {
    namespace = "com.example.caninspector"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.caninspector"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Configure Kotlin JVM toolchain for module (recommended)
kotlin {
    jvmToolchain(17)
}

dependencies {
    // Compose (BOM)
    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.navigation:navigation-compose:$navigationComposeVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:$coreKtxVersion")

    // Note: CSV export uses only java.io + org.json (both built into the
    // Android platform) — no extra dependency needed for it.
}