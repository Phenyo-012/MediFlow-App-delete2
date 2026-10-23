// Android application module: SDK levels, build variants and library dependencies live here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Compile and target SDKs are separate from the minimum Android version supported on devices.
android {
    namespace = "com.medicationinventory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.medicationinventory"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            // The client calls this HTTPS endpoint; privileged Firebase credentials must stay on the server.
            "String",
            "TRUSTED_BACKEND_URL",
            "\"https://digital-medication-inventory-backen.vercel.app\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }

        create("staging") {
            // Staging stays installable alongside production/debug and uses a separate backend placeholder.
            initWith(getByName("debug"))

            isDebuggable = true

            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"

            matchingFallbacks += listOf("debug")

            buildConfigField(
                "String",
                "TRUSTED_BACKEND_URL",
                "\"https://staging-backend-not-configured.invalid\""
            )
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Room, Firebase, WorkManager and CameraX each back a different part of the app workflow.
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(libs.androidx.recyclerview)

    // Room / SQLite
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime)

    // CameraX preview and frame analysis for offline inventory scanning
    implementation("androidx.camera:camera-core:1.5.3")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")

    // Bundled ML Kit model: scanning works offline after installation.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Lifecycle / MVVM
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")


    // Offline QR-code encoding: the authenticator secret never leaves the device.
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.20.0")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
