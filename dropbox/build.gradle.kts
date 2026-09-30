plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geckour.q.dropbox"

    compileSdk = 37
    defaultConfig {
        minSdk = 34
    }
}

dependencies {
    api(projects.database)
    implementation(projects.worker)

    api(libs.dropbox.core.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
