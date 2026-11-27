plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.dreadnought0.feedclean"
    compileSdk {
        // Compose BOM 2026.11 requires compiling against 37.1+. targetSdk stays 37 (no runtime behaviour change).
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.dreadnought0.feedclean"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing reads the key from USER-level Gradle properties (~/.gradle/gradle.properties), never from the
    // repo. Without them (e.g. a fresh clone) the release build is simply unsigned; debug builds are unaffected.
    val releaseStoreFile = providers.gradleProperty("FEEDCLEAN_RELEASE_STORE_FILE").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("FEEDCLEAN_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("FEEDCLEAN_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("FEEDCLEAN_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // R8 is OFF for releases for now. The template's package-scoped optimisation (androidx/kotlin/kotlinx)
            // produced an APK that crashed at launch on Android 17 (IllegalAccessError: a rewritten
            // kotlin.sequences class became inaccessible). Releases ship the same unoptimised code as the debug
            // builds that have been tested on device; re-enable only together with an emulator/device launch test.
            optimization {
                enable = false
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Scrubbed uiautomator fixtures live in src/test/resources/fixtures.
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
