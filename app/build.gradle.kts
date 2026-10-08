import com.geckour.q.buildlogic.secretProperty

plugins {
    alias(libs.plugins.q.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

apply(from = "signing/release.gradle", to = android)

android {
    namespace = "com.geckour.q"

    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "com.geckour.q"
        versionCode = 56
        versionName = "3.5.1"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        val dropboxAppKey = secretProperty("DROPBOX_APP_KEY")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
        buildConfigField(
            "String",
            "DROPBOX_APP_SECRET",
            "\"${secretProperty("DROPBOX_APP_SECRET")}\""
        )

        manifestPlaceholders["dropboxAppKey"] = "db-$dropboxAppKey"
    }
    signingConfigs {
        getByName("debug") {
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
            storeFile = file("signing/debug.keystore")
            storePassword = "android"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.database)
    implementation(projects.spotify)
    implementation(projects.worker)
    implementation(projects.dropbox)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.concurrent.futures.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // DI
    implementation(libs.koin.android)

    // Logging
    implementation(libs.timber)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Networking
    implementation(platform(libs.retrofit.bom))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // AAC ViewModel
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    testImplementation(libs.androidx.core.testing)

    // AndroidX
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.window)
    implementation(libs.androidx.mediarouter)

    // KTX
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.core.ktx)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Permission
    implementation(libs.permissions.dispatcher.ktx)

    // Image processing
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)

    // Player
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.decoder.ffmpeg)
    implementation(libs.androidx.media3.ui)
    compileOnly(libs.checker.qual)
    compileOnly(libs.checker.compat.qual)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.reorderable)
    implementation(libs.androidx.paging.compose)

    // RemoteCompose (App Widget)
    implementation(libs.androidx.compose.remote.creation.compose)
    implementation(libs.androidx.compose.remote.creation)
    implementation(libs.androidx.compose.remote.creation.core)

    // Dropbox
    implementation(libs.dropbox.android.sdk)

    // Billing
    implementation(libs.billing.ktx)

    // File Utils
    implementation(libs.commons.io)

    // Animation
    implementation(libs.lottie.compose)
}
