plugins {
    alias(libs.plugins.q.android.library)
}

android {
    namespace = "com.geckour.q.worker"

    defaultConfig {
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
