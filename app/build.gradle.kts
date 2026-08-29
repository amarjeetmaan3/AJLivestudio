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
        versionName = "0.12.1"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("permanentDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("permanentDebug") }
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
        freeCompilerArgs += listOf("-Xskip-metadata-version-check", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { pickFirsts += "**/*.so" }
    }
}

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
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    val streamPackVersion = "3.2.0"
    implementation("io.github.thibaultbee.streampack:streampack-core:$streamPackVersion")
    implementation("io.github.thibaultbee.streampack:streampack-ui:$streamPackVersion")
    implementation("io.github.thibaultbee.streampack:streampack-rtmp:$streamPackVersion")

    // NEW: Android CameraX (For showing camera behind overlays)
    val cameraxVersion = "1.3.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.guava:guava:32.1.3-android")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
