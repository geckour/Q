plugins {
    alias(libs.plugins.q.android.library)
}

android {
    namespace = "com.geckour.q.core"
}

dependencies {
    api(libs.timber)
    api(platform(libs.firebase.bom))
    api(libs.firebase.crashlytics)
}
