plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.geckour.q.worker"

    compileSdk = 37
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.database)

    api(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jaudiotagger)
    implementation(libs.commons.codec)
}
