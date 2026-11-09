plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

// Override with -Psplit.serverUrl=https://split.example.org (or in ~/.gradle/gradle.properties).
val serverUrlOverride = providers.gradleProperty("split.serverUrl").orNull
// Emulator -> the API on the host machine.
val debugServerUrl = serverUrlOverride ?: "http://10.0.2.2:8787"
// Release builds have no built-in server unless one is given; the login screen then asks for it.
val releaseServerUrl = serverUrlOverride ?: ""

android {
    namespace = "online.aireport.split"
    compileSdk = 36

    defaultConfig {
        applicationId = "online.aireport.split"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"$debugServerUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "DEFAULT_SERVER_URL", "\"$releaseServerUrl\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += "nl"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // The shared split vectors live in the monorepo; tests read them in place.
                test.systemProperty(
                    "split.vectors",
                    rootProject.layout.projectDirectory.file("../../spec/split-vectors.json").asFile.absolutePath,
                )
                // Set SPLIT_API_URL to run the integration test against a live server.
                test.environment("SPLIT_API_URL", providers.environmentVariable("SPLIT_API_URL").orNull ?: "")
                test.maxHeapSize = "2g"
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
}
