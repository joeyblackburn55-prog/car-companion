plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.carcompanion.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.carcompanion.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "1.0"
    }
    signingConfigs {
        // Fixed debug key committed on purpose so every CI build can update the previous install.
        getByName("debug") { storeFile = file("debug.keystore") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
