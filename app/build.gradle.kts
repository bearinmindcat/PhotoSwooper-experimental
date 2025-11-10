plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("androidx.room")
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.example.photoswooper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.photoswooper"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "2025.11.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "PhotoSwooper")
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "PhotoSwooper (debug)")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    // For testing database migrations
    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.util.android)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose) // For loading images
    implementation(libs.coil.gif) // For animated GIFs
    implementation(libs.coil.svg)
    implementation(libs.coil.video) // For video thumbnails
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) // for database
    implementation(libs.koalaplot.core) // For plotting graphs for stats
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.room.testing.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.exoplayer) // For video playback
    implementation(libs.androidx.media3.ui.compose) // For video playback UI

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


}