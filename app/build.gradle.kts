import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cloudDemoRequested = providers.gradleProperty("ritCloudDemo").orNull?.toBooleanStrictOrNull() ?: false
val configuredApiBaseUrl = providers.gradleProperty("ritApiBaseUrl").orNull?.trim()?.trimEnd('/')
if (cloudDemoRequested) {
    val url = configuredApiBaseUrl ?: error("Cloud demo requires -PritApiBaseUrl=https://<real-render-host>")
    val parsed = runCatching { URI(url) }.getOrElse {
        error("ritApiBaseUrl must be a valid HTTPS origin")
    }
    require(parsed.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null &&
        parsed.query == null && parsed.fragment == null && parsed.path.isNullOrEmpty()) {
        "Cloud demo ritApiBaseUrl must be an HTTPS origin with no credentials, path, query, or fragment"
    }
} else if (configuredApiBaseUrl != null) {
    val parsed = runCatching { URI(configuredApiBaseUrl) }.getOrNull()
    require(parsed?.scheme == "http" && parsed.host == "127.0.0.1" && parsed.port == 8080 &&
        parsed.userInfo == null && parsed.query == null && parsed.fragment == null && parsed.path.isNullOrEmpty()) {
        "Local debug API override is restricted to http://127.0.0.1:8080; enable ritCloudDemo for HTTPS"
    }
}
val apiBaseUrl = configuredApiBaseUrl ?: "http://127.0.0.1:8080"

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
        buildConfigField("String", "RIT_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("boolean", "RIT_CLOUD_DEMO", cloudDemoRequested.toString())
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
    debugImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.1")
}
