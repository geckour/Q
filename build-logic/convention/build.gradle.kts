plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.q.android.application.get().pluginId
            implementationClass = "com.geckour.q.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.q.android.library.get().pluginId
            implementationClass = "com.geckour.q.buildlogic.AndroidLibraryConventionPlugin"
        }
    }
}
