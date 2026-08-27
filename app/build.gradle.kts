plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.amarjeetmaan.ajlivestudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.amarjeetmaan.ajlivestudio"
        minSdk = 24
        targetSdk = 34
        versionCode = 13
        versionName = "0.12.1" // StreamPack compile fixes

        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xskip-metadata-version-check",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/*.so"
        }
    }
}

// Some transitive dependencies (Google Play Services, StreamPack) declare
// loose/dynamic version ranges for androidx.core and androidx.activity that
// Gradle resolves to whatever is newest on Maven Central at build time —
// which can require a compileSdk higher than AGP 8.2.0 supports (max 34).
// Forcing these to known-good, compileSdk-34-safe versions keeps the build
// reproducible regardless of what's newly published upstream.
configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
        force("androidx.activity:activity:1.9.1")
        force("androidx.activity:activity-ktx:1.9.1")
        force("androidx.activity:activity-compose:1.9.1")
        force("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
        force("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    }
}

dependencies {
    val composeBom = enforcedPlatform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Image loading for overlay logos picked via the photo picker
    implementation("io.coil-kt:coil-compose:2.6.0")

    // YouTube Direct API (Phase 11) — Google Sign-In + raw REST calls to
    // YouTube Data API v3 (no heavy google-api-client dependency needed).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    // StreamPack — capture, encode, mux and send (RTMP) in one pipeline.
    // Version 3.2.0 confirmed against official KDoc (thibaultbee.github.io/StreamPack) —
    // setConfig()/startStream(descriptor) do not exist in 3.1.2, only 3.2.0+.
    val streamPackVersion = "3.2.0"
    implementation("io.github.thibaultbee.streampack:streampack-core:$streamPackVersion")
    implementation("io.github.thibaultbee.streampack:streampack-ui:$streamPackVersion")
    implementation("io.github.thibaultbee.streampack:streampack-rtmp:$streamPackVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
