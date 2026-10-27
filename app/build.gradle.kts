plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "edu.msrit.canteen"
    compileSdk = 36

    defaultConfig {
        applicationId = "edu.msrit.canteen"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation-core:1.11.1")
    implementation("androidx.compose.foundation:foundation:1.11.1")
    implementation("androidx.compose.ui:ui:1.11.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.1")
    debugImplementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.1")
}
