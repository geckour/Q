import com.geckour.q.buildlogic.secretProperty

plugins {
    alias(libs.plugins.q.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.geckour.q.spotify"

    buildFeatures.buildConfig = true

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${secretProperty("SPOTIFY_CLIENT_ID") ?: ""}\""
        )
    }
}

dependencies {
    api(projects.database)

    api(libs.spotify.auth)
    api(variantOf(libs.spotify.app.remote) { artifactType("aar") })
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.exoplayer)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.timber)
}
