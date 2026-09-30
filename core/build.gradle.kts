plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.geckour.q.core"

    compileSdk = 37
    defaultConfig {
        minSdk = 34
    }
}

dependencies {
    api(libs.timber)
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)
}
