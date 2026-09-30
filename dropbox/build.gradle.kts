plugins {
    alias(libs.plugins.q.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geckour.q.dropbox"
}

dependencies {
    api(projects.database)
    implementation(projects.worker)

    api(libs.dropbox.core.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
}
