plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ona.miciclo"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.ona.miciclo"
        minSdk = 26
        targetSdk = 34
        versionCode = 41
        versionName = "1.0.1-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration testing
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // #region debug-session: app-update-crash
        // Telemetría temporal para la sesión de debug. Desactivada para producción.
        // Para reactivar durante debug, poner en "true" y ajustar la URL al host LAN.
        buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
        buildConfigField("String", "TELEMETRY_URL", "\"http://192.168.10.120:7777/event\"")
        // #endregion

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // llama.cpp/MNN native JNI requires it disabled
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            // x86_64 SOLO en debug para QA en emulador. Release queda arm64-only.
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            // TDD: reporte de cobertura JaCoCo para testDebugUnitTest.
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        // Required for java.time APIs on API 26+
        isCoreLibraryDesugaringEnabled = false // minSdk 26 supports java.time natively
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Fase 2 GGUF: módulo nativo llama.cpp (llama.cpp v0.5.0 vía FetchContent).
    // El .so resultante se llama "ona_llama" (ver LlamaCppBridge).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "lib/arm64-v8a/libc++_shared.so"
        }
    }
}

dependencies {
    // ── AndroidX Core ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // ── Compose (via BOM — no individual versions needed) ──
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Navigation Compose (type-safe) ──
    implementation(libs.androidx.navigation.compose)

    // ── Room (encrypted with SQLCipher) ──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── SQLCipher — encrypts ALL Room data at rest ──
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // ── Hilt (dependency injection) ──
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── WorkManager (sync periódico en segundo plano) ──
    implementation(libs.androidx.work.runtime.ktx)

    // ── Firebase Auth ONLY (zero analytics, zero Firestore) ──
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // ── Credential Manager (modern Google Sign-In) ──
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.googleid)

    // ── Serialization ──
    implementation(libs.kotlinx.serialization.json)

    // ── Coroutines ──
    implementation(libs.kotlinx.coroutines.android)

    // ── Security (Keystore helpers for encryption key management) ──
    implementation(libs.androidx.security.crypto)

    // ── JSON (export/import of encrypted backups) ──
    implementation(libs.gson)

    // ── CameraX (OCR for ovulation strips) ──
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation("com.google.guava:guava:31.1-android")

    // ── Testing ──
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

































