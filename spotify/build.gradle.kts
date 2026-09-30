import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

val secretProperties = Properties().apply {
    rootProject.file("secret.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.geckour.q.spotify"

    buildFeatures.buildConfig = true

    compileSdk = 37
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${secretProperties.getProperty("SPOTIFY_CLIENT_ID") ?: ""}\""
        )
    }
}

dependencies {
    api(projects.database)

    api(libs.spotify.auth)
    api(variantOf(libs.spotify.app.remote) { artifactType("aar") })
    implementation(libs.gson)

    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.timber)
}
